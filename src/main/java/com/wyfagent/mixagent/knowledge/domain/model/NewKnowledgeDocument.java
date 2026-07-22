package com.wyfagent.mixagent.knowledge.domain.model;

/**
 * 创建知识文档记录所需的最小数据。文件必须先安全落盘，数据库只保存受控存储键。
 */
public record NewKnowledgeDocument(
        String title,
        String originalFilename,
        String mediaType,
        String storageKey,
        String contentHash,
        long fileSizeBytes,
        KnowledgeSourceType sourceType
) {
}
