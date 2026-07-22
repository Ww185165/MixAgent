package com.wyfagent.mixagent.knowledge.infrastructure.processing;

import com.wyfagent.mixagent.knowledge.application.KnowledgeDocumentProcessor;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeProcessingScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** 有界线程池调度器，队列过载时把任务转为可重试失败，而不是静默丢失。 */
@Component
public class ThreadPoolKnowledgeProcessingScheduler implements KnowledgeProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolKnowledgeProcessingScheduler.class);

    private final ThreadPoolTaskExecutor executor;
    private final KnowledgeDocumentProcessor processor;
    private final KnowledgeDocumentRepository documentRepository;

    public ThreadPoolKnowledgeProcessingScheduler(
            @Qualifier("knowledgeProcessingExecutor") ThreadPoolTaskExecutor executor,
            KnowledgeDocumentProcessor processor,
            KnowledgeDocumentRepository documentRepository
    ) {
        this.executor = executor;
        this.processor = processor;
        this.documentRepository = documentRepository;
    }

    @Override
    public void schedule(long documentId) {
        try {
            executor.execute(() -> processor.process(documentId));
        } catch (TaskRejectedException exception) {
            log.warn("知识处理队列已满，documentId={}", documentId);
            documentRepository.markFailed(documentId, "QUEUE_FULL", "知识处理任务繁忙，请稍后重试");
        }
    }
}
