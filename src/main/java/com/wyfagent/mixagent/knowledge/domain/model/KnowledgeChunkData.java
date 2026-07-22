package com.wyfagent.mixagent.knowledge.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 已完成向量化的知识片段。向量长度必须与当前索引维度完全一致。
 */
public record KnowledgeChunkData(
        int chunkIndex,
        String content,
        int characterCount,
        Map<String, Object> metadata,
        List<Float> embedding
) {
    public KnowledgeChunkData {
        metadata = Map.copyOf(metadata);
        embedding = List.copyOf(embedding);
    }
}
