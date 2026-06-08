package com.fallguys.inventoryservice.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class SecurityConfigTest {

    private final JwtDecoder localDecoder = new SecurityConfig().localRoleJwtDecoder();

    @Test
    void 로컬디코더는_ROLE만이면_tenancy_code를_role로_채운다() {
        Jwt jwt = localDecoder.decode("ADMIN");

        assertThat(jwt.getClaimAsString("user_role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("tenancy_type")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("tenancy_code")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("employee_no")).isEqualTo("local-ADMIN");
    }

    @Test
    void 로컬디코더는_틸드로_role과_창고코드를_분리하고_tenancy_type을_유추한다() {
        Jwt jwt = localDecoder.decode("BRANCH_MANAGER~WH-SE-001");

        assertThat(jwt.getClaimAsString("user_role")).isEqualTo("BRANCH_MANAGER");
        assertThat(jwt.getClaimAsString("tenancy_type")).isEqualTo("BRANCH");
        assertThat(jwt.getClaimAsString("tenancy_code")).isEqualTo("WH-SE-001");
    }
}
