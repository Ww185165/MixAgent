package com.wyfagent.mixagent.knowledge.application.command;

/**
 * 上传知识文档命令。接口层负责协议转换，应用层负责业务限制和幂等校验。
 */
public record UploadKnowledgeDocumentCommand(
        String title,
        String originalFilename,
        String mediaType,
        byte[] content
) {
    public UploadKnowledgeDocumentCommand {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
