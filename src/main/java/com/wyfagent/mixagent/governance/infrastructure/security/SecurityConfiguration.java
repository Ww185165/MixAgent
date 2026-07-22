package com.wyfagent.mixagent.governance.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

/**
 * 管理接口使用 HTTP Basic（HTTP 基本认证）保护。当前适合本地开发；公开部署时必须置于 HTTPS 后，
 * 并升级为完整用户体系或网关鉴权。
 */
@Configuration
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            AdminSecurityProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        String username = properties.username() == null || properties.username().isBlank()
                ? "admin"
                : properties.username().trim();
        String rawPassword = properties.password();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = UUID.randomUUID().toString();
            log.warn("未配置 MIXAGENT_ADMIN_PASSWORD，知识库管理接口本次启动不可登录");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(rawPassword))
                .roles("KNOWLEDGE_ADMIN")
                .build());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 当前服务按无状态 REST 接口设计，不使用浏览器 Cookie 会话，因此关闭 CSRF 防护。
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/admin/knowledge/**").hasRole("KNOWLEDGE_ADMIN")
                        // 默认拒绝可避免未来新增管理接口时因遗漏权限配置而意外公开。
                        .anyRequest().denyAll())
                .httpBasic(basic -> {
                })
                .build();
    }
}
