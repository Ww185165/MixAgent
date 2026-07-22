package com.wyfagent.mixagent.knowledge.application.exception;

/**
 * 当前状态不允许执行请求，例如对非失败文档发起重试。
 */
public class KnowledgeOperationConflictException extends KnowledgeApplicationException {

    public KnowledgeOperationConflictException(String message) {
        super("KNOWLEDGE_OPERATION_CONFLICT", message);
    }
}
