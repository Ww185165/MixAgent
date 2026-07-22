package com.wyfagent.mixagent.knowledge.api.dto;

import com.wyfagent.mixagent.knowledge.application.result.UploadKnowledgeDocumentResult;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;

public record UploadKnowledgeDocumentResponse(
        long documentId,
        KnowledgeDocumentStatus status,
        String contentHash
) {
    public static UploadKnowledgeDocumentResponse from(UploadKnowledgeDocumentResult result) {
        return new UploadKnowledgeDocumentResponse(result.documentId(), result.status(), result.contentHash());
    }
}
