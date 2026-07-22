package com.wyfagent.mixagent.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {

    @Update("""
            UPDATE knowledge_document
            SET status = #{target},
                processing_started_at = CASE WHEN #{target} = 'PARSING' THEN CURRENT_TIMESTAMP
                                             ELSE processing_started_at END,
                updated_at = CURRENT_TIMESTAMP,
                lock_version = lock_version + 1
            WHERE id = #{documentId} AND status = #{expected}
            """)
    int transition(
            @Param("documentId") long documentId,
            @Param("expected") String expected,
            @Param("target") String target
    );

    @Update("""
            UPDATE knowledge_document
            SET status = 'UPLOADED', retry_count = retry_count + 1,
                failure_code = NULL, error_message = NULL,
                processing_started_at = NULL, processed_at = NULL,
                updated_at = CURRENT_TIMESTAMP, lock_version = lock_version + 1
            WHERE id = #{documentId} AND status = 'FAILED'
            """)
    int resetFailedForRetry(@Param("documentId") long documentId);

    @Update("""
            UPDATE knowledge_document
            SET status = 'FAILED', failure_code = #{failureCode}, error_message = #{safeMessage},
                updated_at = CURRENT_TIMESTAMP, lock_version = lock_version + 1
            WHERE id = #{documentId}
              AND status IN ('UPLOADED', 'PARSING', 'CHUNKING', 'EMBEDDING')
            """)
    int markFailed(
            @Param("documentId") long documentId,
            @Param("failureCode") String failureCode,
            @Param("safeMessage") String safeMessage
    );

    @Update("""
            UPDATE knowledge_document
            SET status = 'FAILED', failure_code = 'PROCESSING_TIMEOUT',
                error_message = '上次处理因服务中断或超时未完成，请重试',
                updated_at = CURRENT_TIMESTAMP, lock_version = lock_version + 1
            WHERE status IN ('PARSING', 'CHUNKING', 'EMBEDDING')
              AND processing_started_at < CURRENT_TIMESTAMP - (#{staleSeconds} * INTERVAL '1 second')
            """)
    int markStaleProcessingAsFailed(@Param("staleSeconds") long staleSeconds);

    @Update("""
            UPDATE knowledge_document
            SET status = 'READY', chunk_strategy = #{chunkStrategy}, embedding_model = #{embeddingModel},
                embedding_dimensions = #{embeddingDimensions}, failure_code = NULL, error_message = NULL,
                processed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP,
                lock_version = lock_version + 1
            WHERE id = #{documentId} AND status = 'EMBEDDING'
            """)
    int markReady(
            @Param("documentId") long documentId,
            @Param("chunkStrategy") String chunkStrategy,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimensions") int embeddingDimensions
    );

    @Select("""
            SELECT id
            FROM knowledge_document
            WHERE status = 'UPLOADED'
            ORDER BY created_at, id
            LIMIT #{limit}
            """)
    List<Long> findUploadedDocumentIds(@Param("limit") int limit);
}
