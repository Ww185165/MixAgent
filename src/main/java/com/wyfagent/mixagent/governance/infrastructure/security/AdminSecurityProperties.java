package com.wyfagent.mixagent.governance.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本地管理接口账号配置。生产环境应由密钥管理服务注入密码。 */
@ConfigurationProperties("mixagent.security.admin")
public record AdminSecurityProperties(String username, String password) {
}
