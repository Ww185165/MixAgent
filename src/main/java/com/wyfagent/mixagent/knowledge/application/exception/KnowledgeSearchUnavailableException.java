package com.wyfagent.mixagent.knowledge.application.exception;

/**
 * 向量模型或检索基础设施暂时不可用，调用方可在退避后重试。
 */
public class KnowledgeSearchUnavailableException extends KnowledgeApplicationException {

    public KnowledgeSearchUnavailableException() {
        super("KNOWLEDGE_SEARCH_UNAVAILABLE", "知识检索服务暂时不可用，请稍后重试");
    }
}
