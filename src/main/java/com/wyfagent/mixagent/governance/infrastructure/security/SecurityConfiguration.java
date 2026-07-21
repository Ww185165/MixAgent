package com.wyfagent.mixagent.governance.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 第一阶段安全基线：仅公开健康检查和基础信息，其余接口默认拒绝，后续接入身份认证后再按权限显式开放。
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 当前服务按无状态 REST 接口设计，不使用浏览器 Cookie 会话，因此关闭 CSRF 防护。
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // 采用默认拒绝策略，避免新增上传或管理接口时因遗漏配置而意外公开。
                        .anyRequest().denyAll())
                .build();
    }
}
