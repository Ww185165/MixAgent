package com.wyfagent.mixagent.knowledge.application.model;

/**
 * 知识入库策略快照。参数外部化后仍需在启动时校验，避免错误配置造成无限分块或超大请求。
 */
public record KnowledgeProcessingPolicy(
        long maxFileSizeBytes,
        int chunkMaxCharacters,
        int chunkOverlapCharacters,
        int embeddingBatchSize
) {
    public KnowledgeProcessingPolicy {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("知识文件大小上限必须大于 0");
        }
        if (chunkMaxCharacters < 200) {
            throw new IllegalArgumentException("分块最大字符数不能小于 200");
        }
        if (chunkOverlapCharacters < 0 || chunkOverlapCharacters >= chunkMaxCharacters) {
            throw new IllegalArgumentException("分块重叠字符数必须大于等于 0 且小于分块大小");
        }
        if (embeddingBatchSize <= 0 || embeddingBatchSize > 10) {
            throw new IllegalArgumentException("百炼 text-embedding-v4 单批数量必须在 1-10 之间");
        }
    }

    public String chunkStrategyName() {
        return "paragraph-aware-char-" + chunkMaxCharacters + "-overlap-" + chunkOverlapCharacters;
    }
}
