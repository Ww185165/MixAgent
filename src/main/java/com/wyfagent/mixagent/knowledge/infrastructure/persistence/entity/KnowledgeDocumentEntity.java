package com.wyfagent.mixagent.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 数据库实体只存在于基础设施层，不跨模块作为接口对象传递。 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String originalFilename;
    private String mediaType;
    private String storageKey;
    private String contentHash;
    private Long fileSizeBytes;
    private String sourceType;
    private String status;
    private String chunkStrategy;
    private String embeddingModel;
    private Integer embeddingDimensions;
    private Integer versionNo;
    private Integer retryCount;
    private String failureCode;
    private String errorMessage;
    private Instant processingStartedAt;
    private Instant processedAt;
    private Integer lockVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
