package com.wyfagent.mixagent.knowledge.domain.model;

import java.util.List;

/**
 * 一次向量模型调用的结果，包含模型身份和维度，防止错误向量写入固定维度索引。
 */
public record EmbeddingBatch(String modelName, int dimensions, List<List<Float>> vectors) {
    public EmbeddingBatch {
        vectors = vectors.stream().map(List::copyOf).toList();
    }
}
