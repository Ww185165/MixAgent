package com.wyfagent.mixagent.knowledge.domain.model;

/**
 * 知识来源类型，用于区分用户上传、内置资料和后续外部同步的数据。
 */
public enum KnowledgeSourceType {
    UPLOAD,
    BUILT_IN,
    MANUAL,
    EXTERNAL
}
