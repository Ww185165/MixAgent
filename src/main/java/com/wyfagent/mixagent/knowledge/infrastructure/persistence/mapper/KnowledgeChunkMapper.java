package com.wyfagent.mixagent.knowledge.infrastructure.persistence.mapper;

import com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity.KnowledgeChunkPersistenceRow;
import com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity.KnowledgeSearchRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {

    @Delete("DELETE FROM knowledge_chunk WHERE document_id = #{documentId}")
    int deleteByDocumentId(@Param("documentId") long documentId);

    int batchInsert(@Param("rows") List<KnowledgeChunkPersistenceRow> rows);

    List<KnowledgeSearchRow> vectorSearch(
            @Param("embeddingLiteral") String embeddingLiteral,
            @Param("limit") int limit
    );

    List<KnowledgeSearchRow> keywordSearch(@Param("query") String query, @Param("limit") int limit);
}
