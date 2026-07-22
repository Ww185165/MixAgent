package com.wyfagent.mixagent.knowledge.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 知识文档处理状态。状态迁移显式受控，避免重复任务同时覆盖同一文档的向量结果。
 */
public enum KnowledgeDocumentStatus {
    UPLOADED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED,
    DISABLED;

    private static final Set<KnowledgeDocumentStatus> PROCESSING_STATUSES =
            EnumSet.of(PARSING, CHUNKING, EMBEDDING);

    public boolean isProcessing() {
        return PROCESSING_STATUSES.contains(this);
    }
}
