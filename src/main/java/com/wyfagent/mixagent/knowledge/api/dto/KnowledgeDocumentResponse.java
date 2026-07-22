package com.wyfagent.mixagent.knowledge.api.dto;

import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentSnapshot;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;

import java.time.Instant;

/** 状态接口不暴露服务器存储路径，只返回管理员排障和页面展示所需信息。 */
public record KnowledgeDocumentResponse(
        long documentId,
        String title,
        String originalFilename,
        long fileSizeBytes,
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
    public static KnowledgeDocumentResponse from(KnowledgeDocumentSnapshot document) {
        return new KnowledgeDocumentResponse(
                document.id(), document.title(), document.originalFilename(), document.fileSizeBytes(),
                document.status(), document.chunkStrategy(), document.embeddingModel(),
                document.embeddingDimensions(), document.retryCount(), document.failureCode(),
                document.errorMessage(), document.processedAt(), document.createdAt(), document.updatedAt());
    }
}
