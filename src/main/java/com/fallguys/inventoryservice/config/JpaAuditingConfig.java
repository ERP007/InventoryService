package com.fallguys.inventoryservice.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * JPA Auditing 설정. 엔티티의 @CreatedBy/@LastModifiedBy를 현재 인증된 사용자의 사번으로 채운다.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * 생성·수정자(사번)를 현재 인증된 JWT의 employee_no 클레임에서 가져온다.
     * 인증 컨텍스트가 없으면(배치·테스트 등) 비운다 → created_by/updated_by는 null.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                return Optional.ofNullable(jwtAuth.getToken().getClaimAsString("employee_no"));
            }
            return Optional.empty();
        };
    }
}
