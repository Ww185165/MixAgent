package com.wyfagent.mixagent.knowledge.infrastructure.storage;

/** 本地知识存储异常，消息不得包含机器绝对路径。 */
public class KnowledgeStorageException extends RuntimeException {

    public KnowledgeStorageException(String message) {
        super(message);
    }

    public KnowledgeStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
