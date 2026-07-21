package com.wyfagent.mixagent.governance.api;

import java.time.Instant;
import java.util.Map;

/**
 * 对外统一错误响应，避免将异常堆栈、数据库结构或内部实现细节暴露给调用方。
 *
 * @param code      稳定的业务错误码，前端不应依赖易变化的异常文本
 * @param message   面向用户的简洁错误说明
 * @param requestId 请求标识，用于关联服务端日志和后续问题排查
 * @param timestamp 错误发生时间，统一使用 UTC 时间戳
 * @param details   可安全公开的字段级错误，不包含密钥或内部提示词
 */
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        Map<String, String> details
) {
}
