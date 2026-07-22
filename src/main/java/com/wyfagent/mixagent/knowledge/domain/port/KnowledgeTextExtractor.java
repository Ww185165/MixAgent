package com.wyfagent.mixagent.knowledge.domain.port;

/**
 * 文档文本提取端口。第一版只允许严格 UTF-8 编码的 TXT 和 Markdown 文档。
 */
public interface KnowledgeTextExtractor {

    String extract(String originalFilename, String mediaType, byte[] content);
}
