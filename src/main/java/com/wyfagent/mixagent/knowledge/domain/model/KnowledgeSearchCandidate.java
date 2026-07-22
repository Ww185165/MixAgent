package com.wyfagent.mixagent.knowledge.domain.model;

/**
 * 单路检索候选。rank 从 1 开始，rawScore 只在同一种检索方式内部具有可比性。
 */
public record KnowledgeSearchCandidate(
        long chunkId,
        long documentId,
        String documentTitle,
        String content,
        int rank,
        double rawScore
) {
}
