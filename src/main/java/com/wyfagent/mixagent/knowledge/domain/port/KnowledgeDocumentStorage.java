package com.wyfagent.mixagent.knowledge.domain.port;

/**
 * 原始知识文件存储端口。storageKey 必须是受控相对键，不能接受调用方提供的任意绝对路径。
 */
public interface KnowledgeDocumentStorage {

    String store(String contentHash, String extension, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
