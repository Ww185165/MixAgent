package com.wyfagent.mixagent.knowledge.application.result;

import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;

/**
 * 上传受理结果。处理在后台执行，接口只承诺任务已经可靠记录而非向量已经生成。
 */
public record UploadKnowledgeDocumentResult(
        long documentId,
        KnowledgeDocumentStatus status,
        String contentHash
) {
}
