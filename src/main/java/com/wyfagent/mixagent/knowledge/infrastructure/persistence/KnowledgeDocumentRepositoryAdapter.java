package com.wyfagent.mixagent.knowledge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentSnapshot;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeSourceType;
import com.wyfagent.mixagent.knowledge.domain.model.NewKnowledgeDocument;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentRepository;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity.KnowledgeDocumentEntity;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** 将数据库实体转换为稳定领域快照，并封装所有条件状态更新。 */
@Repository
public class KnowledgeDocumentRepositoryAdapter implements KnowledgeDocumentRepository {

    private final KnowledgeDocumentMapper mapper;

    public KnowledgeDocumentRepositoryAdapter(KnowledgeDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<KnowledgeDocumentSnapshot> findById(long documentId) {
        return Optional.ofNullable(mapper.selectById(documentId)).map(this::toSnapshot);
    }

    @Override
    public Optional<KnowledgeDocumentSnapshot> findByContentHash(String contentHash) {
        LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<>();
        query.eq(KnowledgeDocumentEntity::getContentHash, contentHash).last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(query)).map(this::toSnapshot);
    }

    @Override
    public long create(NewKnowledgeDocument document) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setTitle(document.title());
        entity.setOriginalFilename(document.originalFilename());
        entity.setMediaType(document.mediaType());
        entity.setStorageKey(document.storageKey());
        entity.setContentHash(document.contentHash());
        entity.setFileSizeBytes(document.fileSizeBytes());
        entity.setSourceType(document.sourceType().name());
        entity.setStatus(KnowledgeDocumentStatus.UPLOADED.name());
        entity.setVersionNo(1);
        entity.setRetryCount(0);
        entity.setLockVersion(0);
        if (mapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("知识文档登记失败");
        }
        return entity.getId();
    }

    @Override
    public boolean transition(long documentId, KnowledgeDocumentStatus expected, KnowledgeDocumentStatus target) {
        return mapper.transition(documentId, expected.name(), target.name()) == 1;
    }

    @Override
    public boolean resetFailedForRetry(long documentId) {
        return mapper.resetFailedForRetry(documentId) == 1;
    }

    @Override
    public void markFailed(long documentId, String failureCode, String safeMessage) {
        mapper.markFailed(documentId, truncate(failureCode, 64), truncate(safeMessage, 1000));
    }

    @Override
    public int markStaleProcessingAsFailed(Duration staleAfter) {
        if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("知识处理超时时间必须大于 0");
        }
        return mapper.markStaleProcessingAsFailed(staleAfter.toSeconds());
    }

    @Override
    public List<Long> findUploadedDocumentIds(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("待恢复任务查询数量必须在 1-1000 之间");
        }
        return List.copyOf(mapper.findUploadedDocumentIds(limit));
    }

    private KnowledgeDocumentSnapshot toSnapshot(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocumentSnapshot(
                entity.getId(), entity.getTitle(), entity.getOriginalFilename(), entity.getMediaType(),
                entity.getStorageKey(), entity.getContentHash(), entity.getFileSizeBytes(),
                KnowledgeSourceType.valueOf(entity.getSourceType()), KnowledgeDocumentStatus.valueOf(entity.getStatus()),
                entity.getChunkStrategy(), entity.getEmbeddingModel(), entity.getEmbeddingDimensions(),
                entity.getRetryCount(), entity.getFailureCode(), entity.getErrorMessage(), entity.getProcessedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
