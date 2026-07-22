package com.wyfagent.mixagent.knowledge.application.exception;

/**
 * 相同内容已存在，调用方应查询原文档状态而不是重复创建处理任务。
 */
public class DuplicateKnowledgeDocumentException extends KnowledgeApplicationException {

    private final long existingDocumentId;

    public DuplicateKnowledgeDocumentException(long existingDocumentId) {
        super("DUPLICATE_KNOWLEDGE_DOCUMENT", "相同内容的知识文档已经存在");
        this.existingDocumentId = existingDocumentId;
    }

    public long existingDocumentId() {
        return existingDocumentId;
    }
}
