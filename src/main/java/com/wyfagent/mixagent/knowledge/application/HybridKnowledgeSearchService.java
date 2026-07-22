package com.wyfagent.mixagent.knowledge.application;

import com.wyfagent.mixagent.knowledge.application.exception.InvalidKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeSearchUnavailableException;
import com.wyfagent.mixagent.knowledge.domain.model.EmbeddingBatch;
import com.wyfagent.mixagent.knowledge.domain.model.HybridKnowledgeSearchResult;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeSearchCandidate;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeIndexRepository;
import com.wyfagent.mixagent.knowledge.domain.port.TextEmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键词与向量混合检索。RRF 只依赖各路排序名次，不要求余弦分数与文本相关度处在同一量纲。
 */
public class HybridKnowledgeSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridKnowledgeSearchService.class);
    private static final int RRF_CONSTANT = 60;
    private static final int MAX_TOP_K = 20;
    private static final int MAX_CANDIDATES = 60;

    private final KnowledgeIndexRepository indexRepository;
    private final TextEmbeddingPort embeddingPort;

    public HybridKnowledgeSearchService(KnowledgeIndexRepository indexRepository, TextEmbeddingPort embeddingPort) {
        this.indexRepository = indexRepository;
        this.embeddingPort = embeddingPort;
    }

    public List<HybridKnowledgeSearchResult> search(String rawQuery, int topK) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty() || query.length() > 1000) {
            throw new InvalidKnowledgeDocumentException("检索文本长度必须在 1-1000 个字符之间");
        }
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new InvalidKnowledgeDocumentException("topK 必须在 1-20 之间");
        }

        int candidateLimit = Math.min(topK * 3, MAX_CANDIDATES);
        List<KnowledgeSearchCandidate> vectorCandidates = List.of();
        List<KnowledgeSearchCandidate> keywordCandidates = List.of();
        boolean vectorSucceeded = false;
        boolean keywordSucceeded = false;

        try {
            EmbeddingBatch queryEmbedding = embeddingPort.embedAll(List.of(query));
            if (queryEmbedding.vectors().size() != 1
                    || queryEmbedding.vectors().get(0).size() != queryEmbedding.dimensions()) {
                throw new IllegalStateException("查询向量返回格式不正确");
            }
            vectorCandidates = indexRepository.vectorSearch(queryEmbedding.vectors().get(0), candidateLimit);
            vectorSucceeded = true;
        } catch (RuntimeException exception) {
            // 不记录原始查询文本，避免知识检索内容进入普通错误日志。
            log.warn("向量检索失败，已尝试降级到关键词检索", exception);
        }

        try {
            keywordCandidates = indexRepository.keywordSearch(query, candidateLimit);
            keywordSucceeded = true;
        } catch (RuntimeException exception) {
            log.warn("关键词检索失败，已尝试降级到向量检索", exception);
        }

        if (!vectorSucceeded && !keywordSucceeded) {
            throw new KnowledgeSearchUnavailableException();
        }
        return fuse(vectorCandidates, keywordCandidates, topK);
    }

    List<HybridKnowledgeSearchResult> fuse(
            List<KnowledgeSearchCandidate> vectorCandidates,
            List<KnowledgeSearchCandidate> keywordCandidates,
            int topK
    ) {
        Map<Long, MutableFusionResult> merged = new LinkedHashMap<>();
        mergeCandidates(merged, vectorCandidates, true);
        mergeCandidates(merged, keywordCandidates, false);

        return merged.values().stream()
                .map(MutableFusionResult::toResult)
                .sorted(Comparator.comparingDouble(HybridKnowledgeSearchResult::fusionScore).reversed()
                        .thenComparingLong(HybridKnowledgeSearchResult::chunkId))
                .limit(topK)
                .toList();
    }

    private void mergeCandidates(
            Map<Long, MutableFusionResult> merged,
            List<KnowledgeSearchCandidate> candidates,
            boolean vector
    ) {
        for (KnowledgeSearchCandidate candidate : candidates) {
            MutableFusionResult result = merged.computeIfAbsent(
                    candidate.chunkId(), ignored -> new MutableFusionResult(candidate));
            result.fusionScore += 1.0d / (RRF_CONSTANT + candidate.rank());
            if (vector) {
                result.vectorRank = candidate.rank();
                result.vectorScore = candidate.rawScore();
            } else {
                result.keywordRank = candidate.rank();
                result.keywordScore = candidate.rawScore();
            }
        }
    }

    private static final class MutableFusionResult {
        private final KnowledgeSearchCandidate candidate;
        private Integer vectorRank;
        private Double vectorScore;
        private Integer keywordRank;
        private Double keywordScore;
        private double fusionScore;

        private MutableFusionResult(KnowledgeSearchCandidate candidate) {
            this.candidate = candidate;
        }

        private HybridKnowledgeSearchResult toResult() {
            return new HybridKnowledgeSearchResult(
                    candidate.chunkId(), candidate.documentId(), candidate.documentTitle(), candidate.content(),
                    vectorRank, vectorScore, keywordRank, keywordScore, fusionScore);
        }
    }
}
