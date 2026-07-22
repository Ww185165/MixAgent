package com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity;

import lombok.Data;

/** MyBatis（持久层框架）批量写入参数，向量和 JSON 已转换为 PostgreSQL 可解析文本。 */
@Data
public class KnowledgeChunkPersistenceRow {

    private long documentId;
    private int chunkIndex;
    private String content;
    private int characterCount;
    private String metadataJson;
    private String embeddingLiteral;
}
