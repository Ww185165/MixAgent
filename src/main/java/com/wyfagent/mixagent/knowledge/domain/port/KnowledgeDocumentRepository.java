package com.wyfagent.mixagent.knowledge.domain.port;

import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentSnapshot;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;
import com.wyfagent.mixagent.knowledge.domain.model.NewKnowledgeDocument;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 知识文档持久化端口。所有状态更新都使用期望状态作为条件，避免重复任务并发执行。
 */
public interface KnowledgeDocumentRepository {

    Optional<KnowledgeDocumentSnapshot> findById(long documentId);

    Optional<KnowledgeDocumentSnapshot> findByContentHash(String contentHash);

    long create(NewKnowledgeDocument document);

    boolean transition(long documentId, KnowledgeDocumentStatus expected, KnowledgeDocumentStatus target);

    boolean resetFailedForRetry(long documentId);

    void markFailed(long documentId, String failureCode, String safeMessage);

    int markStaleProcessingAsFailed(Duration staleAfter);

    List<Long> findUploadedDocumentIds(int limit);
}
