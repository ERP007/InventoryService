package com.fallguys.inventoryservice.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fallguys.inventoryservice.shared.exception.ForbiddenException;
import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.shared.model.UserRole;

class JwtClaimExtractorTest {

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("token").header("alg", "none");
    }

    /** 실토큰에 준하는 클레임을 모두 담은 JWT. */
    private static Jwt fullJwt(String userRole, String tenancyType, String tenancyCode, String employeeNo) {
        return base()
                .claim("user_role", userRole)
                .claim("tenancy_type", tenancyType)
                .claim("tenancy_code", tenancyCode)
                .claim("employee_no", employeeNo)
                .build();
    }

    // ---- extractRole ----

    @Test
    void user_role_클레임을_UserRole로_매핑한다() {
        assertThat(JwtClaimExtractor.extractRole(fullJwt("HQ_MANAGER", "HQ", "WH-HQ-001", "emp1")))
                .isEqualTo(UserRole.HQ_MANAGER);
    }

    @Test
    void user_role이_UserRole이_아니면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractRole(
                base().claim("user_role", "offline_access").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void user_role_클레임이_없으면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractRole(base().claim("employee_no", "emp1").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- extractTenancyType ----

    @Test
    void tenancy_type_클레임을_TenancyType으로_매핑한다() {
        assertThat(JwtClaimExtractor.extractTenancyType(fullJwt("BRANCH_STAFF", "BRANCH", "WH-BR-001", "emp1")))
                .isEqualTo(TenancyType.BRANCH);
    }

    @Test
    void tenancy_type이_매핑불가거나_없으면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractTenancyType(
                base().claim("tenancy_type", "UNKNOWN").build()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> JwtClaimExtractor.extractTenancyType(
                base().claim("user_role", "ADMIN").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- extractEmployeeNo ----

    @Test
    void employee_no_클레임을_반환한다() {
        assertThat(JwtClaimExtractor.extractEmployeeNo(fullJwt("ADMIN", "ADMIN", "ADMIN", "admin002")))
                .isEqualTo("admin002");
    }

    @Test
    void employee_no가_없으면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractEmployeeNo(base().claim("user_role", "ADMIN").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- extractTenancyCode ----

    @Test
    void tenancy_code_클레임을_반환한다() {
        assertThat(JwtClaimExtractor.extractTenancyCode(fullJwt("BRANCH_STAFF", "BRANCH", "WH-SE-001", "emp1")))
                .isEqualTo("WH-SE-001");
    }

    @Test
    void tenancy_code가_없으면_ForbiddenException() {
        assertThatThrownBy(() -> JwtClaimExtractor.extractTenancyCode(base().claim("user_role", "ADMIN").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- requireAnyOf ----

    @Test
    void 허용_롤이면_통과한다() {
        Jwt jwt = fullJwt("HQ_MANAGER", "HQ", "WH-HQ-001", "emp1");
        assertThatCode(() -> JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER))
                .doesNotThrowAnyException();
    }

    @Test
    void 허용목록_밖_롤이면_ForbiddenException() {
        Jwt jwt = fullJwt("BRANCH_STAFF", "BRANCH", "WH-BR-001", "emp1");
        assertThatThrownBy(() -> JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER))
                .isInstanceOf(ForbiddenException.class);
    }
}
