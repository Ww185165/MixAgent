package com.wyfagent.mixagent.knowledge.domain.model;

import java.time.Instant;

/**
 * 知识文档稳定快照，不向应用层暴露 MyBatis-Plus 实体。
 */
public record KnowledgeDocumentSnapshot(
        long id,
        String title,
        String originalFilename,
        String mediaType,
        String storageKey,
        String contentHash,
        long fileSizeBytes,
        KnowledgeSourceType sourceType,
        KnowledgeDocumentStatus status,
        String chunkStrategy,
        String embeddingModel,
        Integer embeddingDimensions,
        int retryCount,
        String failureCode,
        String errorMessage,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
