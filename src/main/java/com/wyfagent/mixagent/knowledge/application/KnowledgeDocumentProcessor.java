package com.wyfagent.mixagent.knowledge.application;

import com.wyfagent.mixagent.knowledge.application.model.KnowledgeProcessingPolicy;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeApplicationException;
import com.wyfagent.mixagent.knowledge.domain.model.EmbeddingBatch;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeChunkData;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentSnapshot;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;
import com.wyfagent.mixagent.knowledge.domain.model.TextChunk;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentStorage;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeIndexRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeTextExtractor;
import com.wyfagent.mixagent.knowledge.domain.port.TextEmbeddingPort;
import com.wyfagent.mixagent.knowledge.domain.service.ParagraphAwareTextChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档异步入库流程。远程向量调用在事务外完成，只有“替换片段并标记 READY”使用短数据库事务，
 * 这样模型延迟或超时不会长期占用数据库连接。
 */
public class KnowledgeDocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentProcessor.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeIndexRepository indexRepository;
    private final KnowledgeDocumentStorage storage;
    private final KnowledgeTextExtractor textExtractor;
    private final TextEmbeddingPort embeddingPort;
    private final ParagraphAwareTextChunker chunker;
    private final KnowledgeProcessingPolicy policy;

    public KnowledgeDocumentProcessor(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeIndexRepository indexRepository,
            KnowledgeDocumentStorage storage,
            KnowledgeTextExtractor textExtractor,
            TextEmbeddingPort embeddingPort,
            ParagraphAwareTextChunker chunker,
            KnowledgeProcessingPolicy policy
    ) {
        this.documentRepository = documentRepository;
        this.indexRepository = indexRepository;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.embeddingPort = embeddingPort;
        this.chunker = chunker;
        this.policy = policy;
    }

    public void process(long documentId) {
        if (!documentRepository.transition(documentId, KnowledgeDocumentStatus.UPLOADED, KnowledgeDocumentStatus.PARSING)) {
            log.debug("知识文档未获得处理权，documentId={}", documentId);
            return;
        }

        try {
            KnowledgeDocumentSnapshot document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalStateException("已登记的知识文档不存在"));
            String text = textExtractor.extract(
                    document.originalFilename(), document.mediaType(), storage.read(document.storageKey()));

            requireTransition(documentId, KnowledgeDocumentStatus.PARSING, KnowledgeDocumentStatus.CHUNKING);
            List<TextChunk> chunks = chunker.split(
                    text, policy.chunkMaxCharacters(), policy.chunkOverlapCharacters());
            if (chunks.isEmpty()) {
                throw new DocumentProcessingException("EMPTY_DOCUMENT", "文档中没有可入库的文本内容");
            }

            requireTransition(documentId, KnowledgeDocumentStatus.CHUNKING, KnowledgeDocumentStatus.EMBEDDING);
            EmbeddedChunks embedded = embed(chunks, document);

            // 片段替换与 READY 状态更新由持久化适配器放入同一事务，避免可检索到半套索引。
            indexRepository.replaceAndMarkReady(
                    documentId,
                    embedded.chunks(),
                    policy.chunkStrategyName(),
                    embedded.modelName(),
                    embedded.dimensions()
            );
        } catch (DocumentProcessingException exception) {
            markFailed(documentId, exception.code(), exception.getMessage(), exception);
        } catch (KnowledgeApplicationException exception) {
            markFailed(documentId, exception.code(), exception.getMessage(), exception);
        } catch (Exception exception) {
            markFailed(documentId, "PROCESSING_ERROR", "知识文档处理失败，请稍后重试", exception);
        }
    }

    private EmbeddedChunks embed(List<TextChunk> chunks, KnowledgeDocumentSnapshot document) {
        List<KnowledgeChunkData> result = new ArrayList<>(chunks.size());
        String expectedModel = null;
        Integer expectedDimensions = null;

        for (int start = 0; start < chunks.size(); start += policy.embeddingBatchSize()) {
            int end = Math.min(start + policy.embeddingBatchSize(), chunks.size());
            List<TextChunk> batchChunks = chunks.subList(start, end);
            EmbeddingBatch batch = embeddingPort.embedAll(batchChunks.stream().map(TextChunk::content).toList());
            validateEmbeddingBatch(batch, batchChunks.size());

            if (expectedModel == null) {
                expectedModel = batch.modelName();
                expectedDimensions = batch.dimensions();
            } else if (!expectedModel.equals(batch.modelName()) || expectedDimensions != batch.dimensions()) {
                throw new DocumentProcessingException("EMBEDDING_MODEL_CHANGED", "单次处理过程中向量模型或维度发生变化");
            }

            for (int index = 0; index < batchChunks.size(); index++) {
                TextChunk chunk = batchChunks.get(index);
                result.add(new KnowledgeChunkData(
                        chunk.index(),
                        chunk.content(),
                        chunk.characterCount(),
                        Map.of("documentTitle", document.title(), "originalFilename", document.originalFilename()),
                        batch.vectors().get(index)
                ));
            }
        }
        return new EmbeddedChunks(List.copyOf(result), expectedModel, expectedDimensions);
    }

    private void validateEmbeddingBatch(EmbeddingBatch batch, int expectedCount) {
        if (batch == null || batch.modelName() == null || batch.modelName().isBlank()) {
            throw new DocumentProcessingException("INVALID_EMBEDDING_RESPONSE", "向量模型返回缺少模型标识");
        }
        if (batch.dimensions() <= 0 || batch.vectors().size() != expectedCount) {
            throw new DocumentProcessingException("INVALID_EMBEDDING_RESPONSE", "向量模型返回数量或维度不正确");
        }
        boolean invalid = batch.vectors().stream().anyMatch(vector -> vector.size() != batch.dimensions());
        if (invalid) {
            throw new DocumentProcessingException("INVALID_EMBEDDING_RESPONSE", "向量长度与声明维度不一致");
        }
    }

    private void requireTransition(long documentId, KnowledgeDocumentStatus expected, KnowledgeDocumentStatus target) {
        if (!documentRepository.transition(documentId, expected, target)) {
            throw new DocumentProcessingException("STATUS_CONFLICT", "文档处理状态发生冲突");
        }
    }

    private void markFailed(long documentId, String code, String safeMessage, Exception exception) {
        log.error("知识文档处理失败，documentId={}, failureCode={}", documentId, code, exception);
        documentRepository.markFailed(documentId, code, safeMessage);
    }

    private static final class DocumentProcessingException extends RuntimeException {
        private final String code;

        private DocumentProcessingException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    private record EmbeddedChunks(List<KnowledgeChunkData> chunks, String modelName, int dimensions) {
    }
}
