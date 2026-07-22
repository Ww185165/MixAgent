package com.wyfagent.mixagent.knowledge.domain.port;

/**
 * 知识处理调度端口。调度失败必须转为可观察的文档失败状态，不能静默丢弃任务。
 */
public interface KnowledgeProcessingScheduler {

    void schedule(long documentId);
}
