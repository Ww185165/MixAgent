package com.wyfagent.mixagent.knowledge.application;

import com.wyfagent.mixagent.knowledge.domain.model.EmbeddingBatch;
import com.wyfagent.mixagent.knowledge.domain.model.HybridKnowledgeSearchResult;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeChunkData;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeSearchCandidate;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeIndexRepository;
import com.wyfagent.mixagent.knowledge.domain.port.TextEmbeddingPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HybridKnowledgeSearchServiceTest {

    @Test
    void overlappingCandidateWinsReciprocalRankFusion() {
        KnowledgeIndexRepository repository = new StubIndexRepository();
        TextEmbeddingPort embeddingPort = texts ->
                new EmbeddingBatch("fake-embedding", 3, List.of(List.of(0.1f, 0.2f, 0.3f)));
        HybridKnowledgeSearchService service = new HybridKnowledgeSearchService(repository, embeddingPort);

        List<HybridKnowledgeSearchResult> results = service.search("清爽酸甜", 3);

        assertEquals(3, results.size());
        assertEquals(2L, results.get(0).chunkId());
        assertNotNull(results.get(0).vectorRank());
        assertNotNull(results.get(0).keywordRank());
    }

    @Test
    void fallsBackToKeywordSearchWhenEmbeddingModelIsUnavailable() {
        KnowledgeIndexRepository repository = new StubIndexRepository();
        TextEmbeddingPort unavailableEmbedding = texts -> {
            throw new IllegalStateException("模拟模型超时");
        };
        HybridKnowledgeSearchService service = new HybridKnowledgeSearchService(repository, unavailableEmbedding);

        List<HybridKnowledgeSearchResult> results = service.search("金酒", 2);

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).chunkId());
        assertNull(results.get(0).vectorRank());
        assertNotNull(results.get(0).keywordRank());
    }

    private static final class StubIndexRepository implements KnowledgeIndexRepository {

        @Override
        public void replaceAndMarkReady(
                long documentId,
                List<KnowledgeChunkData> chunks,
                String chunkStrategy,
                String embeddingModel,
                int embeddingDimensions
        ) {
            throw new UnsupportedOperationException("本测试不执行写入");
        }

        @Override
        public List<KnowledgeSearchCandidate> vectorSearch(List<Float> queryVector, int limit) {
            return List.of(
                    candidate(1, 1, 0.92),
                    candidate(2, 2, 0.88)
            );
        }

        @Override
        public List<KnowledgeSearchCandidate> keywordSearch(String query, int limit) {
            return List.of(
                    candidate(2, 1, 1.80),
                    candidate(3, 2, 1.20)
            );
        }

        private KnowledgeSearchCandidate candidate(long chunkId, int rank, double score) {
            return new KnowledgeSearchCandidate(chunkId, 10, "基础调酒知识", "片段 " + chunkId, rank, score);
        }
    }
}
