package com.wyfagent.mixagent.knowledge.infrastructure.embedding;

import com.wyfagent.mixagent.knowledge.domain.model.EmbeddingBatch;
import com.wyfagent.mixagent.knowledge.domain.port.TextEmbeddingPort;
import com.wyfagent.mixagent.knowledge.infrastructure.config.EmbeddingProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通过百炼 OpenAI-compatible（OpenAI 兼容）接口生成向量。只记录次数和耗时，
 * 不记录原文、模型原始响应或 API Key（应用程序编程接口密钥）。
 */
@Component
public class BailianTextEmbeddingAdapter implements TextEmbeddingPort {

    private final EmbeddingProperties properties;
    private final Timer requestTimer;
    private final Counter failureCounter;
    private volatile EmbeddingModel embeddingModel;

    public BailianTextEmbeddingAdapter(EmbeddingProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.requestTimer = Timer.builder("mixagent.ai.embedding.duration")
                .description("百炼向量模型调用耗时")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("mixagent.ai.embedding.failures")
                .description("百炼向量模型调用失败次数")
                .register(meterRegistry);
    }

    @Override
    public EmbeddingBatch embedAll(List<String> texts) {
        validateRequest(texts);
        try {
            return requestTimer.record(() -> invoke(texts));
        } catch (RuntimeException exception) {
            failureCounter.increment();
            throw exception;
        }
    }

    private EmbeddingBatch invoke(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        Response<List<Embedding>> response = model().embedAll(segments);
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.size() != texts.size()) {
            throw new IllegalStateException("百炼向量接口返回数量与请求不一致");
        }

        List<List<Float>> vectors = new ArrayList<>(embeddings.size());
        for (Embedding embedding : embeddings) {
            float[] values = embedding.vector();
            if (values.length != properties.dimensions()) {
                throw new IllegalStateException("百炼向量维度与数据库索引配置不一致");
            }
            List<Float> vector = new ArrayList<>(values.length);
            for (float value : values) {
                if (!Float.isFinite(value)) {
                    throw new IllegalStateException("百炼向量包含非有限数值");
                }
                vector.add(value);
            }
            vectors.add(List.copyOf(vector));
        }
        return new EmbeddingBatch(properties.modelName(), properties.dimensions(), vectors);
    }

    private EmbeddingModel model() {
        EmbeddingModel local = embeddingModel;
        if (local == null) {
            synchronized (this) {
                local = embeddingModel;
                if (local == null) {
                    validateConfiguration();
                    local = OpenAiEmbeddingModel.builder()
                            .baseUrl(properties.baseUrl())
                            .apiKey(properties.apiKey())
                            .modelName(properties.modelName())
                            .dimensions(properties.dimensions())
                            .timeout(properties.timeout())
                            .maxRetries(properties.maxRetries())
                            .build();
                    embeddingModel = local;
                }
            }
        }
        return local;
    }

    private void validateRequest(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.size() > properties.batchSize()) {
            throw new IllegalArgumentException("向量请求批量大小必须在配置范围内");
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("向量请求文本不能为空");
        }
    }

    private void validateConfiguration() {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("未配置 MIXAGENT_AI_BASE_URL");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY");
        }
        if (properties.modelName() == null || properties.modelName().isBlank()) {
            throw new IllegalStateException("未配置向量模型名称");
        }
        if (properties.dimensions() != 1024) {
            throw new IllegalStateException("当前数据库向量索引固定为 1024 维");
        }
    }
}
