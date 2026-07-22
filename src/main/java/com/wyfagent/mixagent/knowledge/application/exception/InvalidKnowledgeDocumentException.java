package com.wyfagent.mixagent.knowledge.application.exception;

/**
 * 上传文档不满足文件类型、大小、编码或内容要求。
 */
public class InvalidKnowledgeDocumentException extends KnowledgeApplicationException {

    public InvalidKnowledgeDocumentException(String message) {
        super("INVALID_KNOWLEDGE_DOCUMENT", message);
    }
}
