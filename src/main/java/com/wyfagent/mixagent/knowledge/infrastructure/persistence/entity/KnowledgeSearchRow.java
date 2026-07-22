package com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity;

import lombok.Data;

/** 两路检索 SQL（结构化查询语言）的统一投影。 */
@Data
public class KnowledgeSearchRow {

    private long chunkId;
    private long documentId;
    private String documentTitle;
    private String content;
    private int rank;
    private double rawScore;
}
