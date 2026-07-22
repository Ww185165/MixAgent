package com.wyfagent.mixagent.knowledge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 知识入库边界配置，集中限制文件大小、分块参数和后台任务容量。 */
@ConfigurationProperties("mixagent.knowledge")
public record KnowledgeProperties(
        String storageRoot,
        long maxFileSizeBytes,
        int chunkMaxCharacters,
        int chunkOverlapCharacters,
        Processing processing
) {
    public record Processing(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            Duration staleAfter
    ) {
    }
}
