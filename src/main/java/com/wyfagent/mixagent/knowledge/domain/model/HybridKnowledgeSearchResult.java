package com.wyfagent.mixagent.knowledge.domain.model;

/**
 * 混合检索结果，同时保留关键词与向量两路证据，便于解释和离线评估。
 */
public record HybridKnowledgeSearchResult(
        long chunkId,
        long documentId,
        String documentTitle,
        String content,
        Integer vectorRank,
        Double vectorScore,
        Integer keywordRank,
        Double keywordScore,
        double fusionScore
) {
}
