package com.fallguys.inventoryservice.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fallguys.inventoryservice.shared.exception.ForbiddenException;
import com.fallguys.inventoryservice.shared.model.UserRole;

class JwtClaimExtractorTest {

    private static Jwt jwtWithRoles(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "tester")
                .claim("resource_access", Map.of("erp-client", Map.of("roles", roles)))
                .build();
    }

    private static Jwt jwtWithoutResourceAccess() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "tester")
                .build();
    }

    // ---- extractRole ----

    @Test
    void 클라이언트_롤을_UserRole로_매핑한다() {
        assertThat(JwtClaimExtractor.extractRole(jwtWithRoles(List.of("ADMIN"))))
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void UserRole이_아닌_롤은_건너뛰고_매핑가능한_첫_롤을_반환한다() {
        assertThat(JwtClaimExtractor.extractRole(jwtWithRoles(List.of("offline_access", "HQ_MANAGER"))))
                .isEqualTo(UserRole.HQ_MANAGER);
    }

    @Test
    void 매핑가능한_롤이_없으면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractRole(jwtWithRoles(List.of("offline_access"))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void resource_access_클레임이_없으면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractRole(jwtWithoutResourceAccess()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- requireAnyOf ----

    @Test
    void 허용_롤이면_통과한다() {
        Jwt jwt = jwtWithRoles(List.of("HQ_MANAGER"));
        assertThatCode(() -> JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER))
                .doesNotThrowAnyException();
    }

    @Test
    void 허용목록_밖_롤이면_ForbiddenException() {
        Jwt jwt = jwtWithRoles(List.of("BRANCH_STAFF"));
        assertThatThrownBy(() -> JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER))
                .isInstanceOf(ForbiddenException.class);
    }
}
