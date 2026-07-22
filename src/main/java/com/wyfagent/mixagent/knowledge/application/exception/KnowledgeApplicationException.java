package com.wyfagent.mixagent.knowledge.application.exception;

/**
 * 可安全映射到接口错误协议的知识库异常，message 不得包含文件绝对路径、密钥或模型原始响应。
 */
public abstract class KnowledgeApplicationException extends RuntimeException {

    private final String code;

    protected KnowledgeApplicationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
