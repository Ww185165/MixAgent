package com.wyfagent.mixagent.knowledge.infrastructure.config;

import com.wyfagent.mixagent.knowledge.application.HybridKnowledgeSearchService;
import com.wyfagent.mixagent.knowledge.application.KnowledgeDocumentApplicationService;
import com.wyfagent.mixagent.knowledge.application.KnowledgeDocumentProcessor;
import com.wyfagent.mixagent.knowledge.application.model.KnowledgeProcessingPolicy;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentStorage;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeIndexRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeProcessingScheduler;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeTextExtractor;
import com.wyfagent.mixagent.knowledge.domain.port.TextEmbeddingPort;
import com.wyfagent.mixagent.knowledge.domain.service.ParagraphAwareTextChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 只在基础设施层装配应用用例，领域对象不感知 Spring（Java 应用框架）。 */
@Configuration
public class KnowledgeConfiguration {

    @Bean
    public KnowledgeProcessingPolicy knowledgeProcessingPolicy(
            KnowledgeProperties properties,
            EmbeddingProperties embeddingProperties
    ) {
        return new KnowledgeProcessingPolicy(
                properties.maxFileSizeBytes(),
                properties.chunkMaxCharacters(),
                properties.chunkOverlapCharacters(),
                embeddingProperties.batchSize()
        );
    }

    @Bean
    public ParagraphAwareTextChunker paragraphAwareTextChunker() {
        return new ParagraphAwareTextChunker();
    }

    @Bean(name = "knowledgeProcessingExecutor")
    public ThreadPoolTaskExecutor knowledgeProcessingExecutor(KnowledgeProperties properties) {
        KnowledgeProperties.Processing processing = properties.processing();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(processing.corePoolSize());
        executor.setMaxPoolSize(processing.maxPoolSize());
        executor.setQueueCapacity(processing.queueCapacity());
        executor.setThreadNamePrefix("knowledge-processing-");
        // 队列满时明确拒绝，由调度器把任务标记为失败，防止请求线程被模型调用拖住。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public KnowledgeDocumentProcessor knowledgeDocumentProcessor(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeIndexRepository indexRepository,
            KnowledgeDocumentStorage storage,
            KnowledgeTextExtractor textExtractor,
            TextEmbeddingPort embeddingPort,
            ParagraphAwareTextChunker chunker,
            KnowledgeProcessingPolicy policy
    ) {
        return new KnowledgeDocumentProcessor(
                documentRepository, indexRepository, storage, textExtractor, embeddingPort, chunker, policy);
    }

    @Bean
    public KnowledgeDocumentApplicationService knowledgeDocumentApplicationService(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentStorage storage,
            KnowledgeProcessingScheduler scheduler,
            KnowledgeProcessingPolicy policy
    ) {
        return new KnowledgeDocumentApplicationService(documentRepository, storage, scheduler, policy);
    }

    @Bean
    public HybridKnowledgeSearchService hybridKnowledgeSearchService(
            KnowledgeIndexRepository indexRepository,
            TextEmbeddingPort embeddingPort
    ) {
        return new HybridKnowledgeSearchService(indexRepository, embeddingPort);
    }
}
