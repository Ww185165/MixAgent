package com.wyfagent.mixagent.knowledge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 百炼 OpenAI-compatible（OpenAI 兼容）向量接口配置，不允许在代码中硬编码密钥。 */
@ConfigurationProperties("mixagent.ai.embedding")
public record EmbeddingProperties(
        String baseUrl,
        String apiKey,
        String modelName,
        int dimensions,
        int batchSize,
        Duration timeout,
        int maxRetries
) {
}
