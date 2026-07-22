package com.wyfagent.mixagent.knowledge.application.exception;

/**
 * 指定知识文档不存在或已不可访问。
 */
public class KnowledgeDocumentNotFoundException extends KnowledgeApplicationException {

    public KnowledgeDocumentNotFoundException(long documentId) {
        super("KNOWLEDGE_DOCUMENT_NOT_FOUND", "知识文档不存在：" + documentId);
    }
}
