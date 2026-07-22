package com.wyfagent.mixagent.knowledge.domain.model;

/**
 * 文档分块结果。当前使用字符数而非供应商专用 Tokenizer，保证中文分块可复现。
 */
public record TextChunk(int index, String content, int characterCount) {
}
