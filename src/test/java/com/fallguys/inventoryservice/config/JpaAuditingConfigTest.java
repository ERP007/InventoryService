package com.fallguys.inventoryservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JpaAuditingConfigTest {

    private final AuditorAware<String> auditorAware = new JpaAuditingConfig().auditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_JWT의_employee_no를_현재_감사자로_반환한다() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("employee_no", "admin002")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(auditorAware.getCurrentAuditor()).contains("admin002");
    }

    @Test
    void 인증_컨텍스트가_없으면_빈값을_반환한다() {
        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }
}
