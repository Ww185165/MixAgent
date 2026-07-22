package com.wyfagent.mixagent.knowledge.infrastructure.processing;

import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeProcessingScheduler;
import com.wyfagent.mixagent.knowledge.infrastructure.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时回收因进程退出而卡在处理中状态的任务，使管理员可以显式重试。 */
@Component
public class KnowledgeProcessingRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeProcessingRecovery.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeProcessingScheduler processingScheduler;
    private final KnowledgeProperties properties;

    public KnowledgeProcessingRecovery(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeProcessingScheduler processingScheduler,
            KnowledgeProperties properties
    ) {
        this.documentRepository = documentRepository;
        this.processingScheduler = processingScheduler;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recovered = documentRepository.markStaleProcessingAsFailed(properties.processing().staleAfter());
        if (recovered > 0) {
            log.warn("已将 {} 个超时知识处理任务标记为失败，等待管理员重试", recovered);
        }
        // 修复“文档已提交，但进程在任务入队前退出”的宕机窗口。状态条件更新会阻止重复执行。
        var pendingDocumentIds = documentRepository.findUploadedDocumentIds(1000);
        pendingDocumentIds.forEach(processingScheduler::schedule);
        if (!pendingDocumentIds.isEmpty()) {
            log.info("已重新调度 {} 个待处理知识文档", pendingDocumentIds.size());
        }
    }
}
