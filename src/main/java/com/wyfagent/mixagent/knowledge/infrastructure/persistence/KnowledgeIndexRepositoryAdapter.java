package com.wyfagent.mixagent.knowledge.infrastructure.persistence;

import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeChunkData;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeSearchCandidate;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeIndexRepository;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity.KnowledgeChunkPersistenceRow;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity.KnowledgeSearchRow;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.mapper.KnowledgeChunkMapper;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** PostgreSQL（关系型数据库）向量与关键词索引适配器。 */
@Repository
public class KnowledgeIndexRepositoryAdapter implements KnowledgeIndexRepository {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final JsonMapper jsonMapper;

    public KnowledgeIndexRepositoryAdapter(
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            JsonMapper jsonMapper
    ) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Transactional
    public void replaceAndMarkReady(
            long documentId,
            List<KnowledgeChunkData> chunks,
            String chunkStrategy,
            String embeddingModel,
            int embeddingDimensions
    ) {
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("知识片段不能为空");
        }
        List<KnowledgeChunkPersistenceRow> rows = chunks.stream()
                .map(chunk -> toPersistenceRow(documentId, chunk, embeddingDimensions))
                .toList();
        chunkMapper.deleteByDocumentId(documentId);
        if (chunkMapper.batchInsert(rows) != rows.size()) {
            throw new IllegalStateException("知识片段未完整写入");
        }
        if (documentMapper.markReady(
                documentId, chunkStrategy, embeddingModel, embeddingDimensions) != 1) {
            throw new IllegalStateException("知识文档状态无法切换为 READY");
        }
    }

    @Override
    public List<KnowledgeSearchCandidate> vectorSearch(List<Float> queryVector, int limit) {
        return chunkMapper.vectorSearch(toVectorLiteral(queryVector), limit).stream()
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public List<KnowledgeSearchCandidate> keywordSearch(String query, int limit) {
        return chunkMapper.keywordSearch(query, limit).stream().map(this::toCandidate).toList();
    }

    private KnowledgeChunkPersistenceRow toPersistenceRow(
            long documentId,
            KnowledgeChunkData chunk,
            int embeddingDimensions
    ) {
        if (chunk.embedding().size() != embeddingDimensions) {
            throw new IllegalArgumentException("知识片段向量维度不一致");
        }
        KnowledgeChunkPersistenceRow row = new KnowledgeChunkPersistenceRow();
        row.setDocumentId(documentId);
        row.setChunkIndex(chunk.chunkIndex());
        row.setContent(chunk.content());
        row.setCharacterCount(chunk.characterCount());
        row.setMetadataJson(toJson(chunk));
        row.setEmbeddingLiteral(toVectorLiteral(chunk.embedding()));
        return row;
    }

    private String toJson(KnowledgeChunkData chunk) {
        try {
            return jsonMapper.writeValueAsString(chunk.metadata());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("知识片段元数据无法序列化", exception);
        }
    }

    private String toVectorLiteral(List<Float> vector) {
        StringBuilder literal = new StringBuilder(vector.size() * 10).append('[');
        for (int index = 0; index < vector.size(); index++) {
            Float value = vector.get(index);
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("向量包含空值或非有限数值");
            }
            if (index > 0) {
                literal.append(',');
            }
            literal.append(value);
        }
        return literal.append(']').toString();
    }

    private KnowledgeSearchCandidate toCandidate(KnowledgeSearchRow row) {
        return new KnowledgeSearchCandidate(
                row.getChunkId(), row.getDocumentId(), row.getDocumentTitle(), row.getContent(),
                row.getRank(), row.getRawScore());
    }
}
