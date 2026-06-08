package com.fallguys.inventoryservice.shared.security;

import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

import com.fallguys.inventoryservice.shared.exception.ForbiddenException;
import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.shared.model.UserRole;

/**
 * 리소스 서버가 검증한 JWT에서 사용자 식별·권한 클레임을 추출하고 인가를 강제하는 컨트롤러 보조 유틸.
 *
 * <p>Keycloak(erp-client)이 발급하는 최상위 클레임을 읽는다:
 * {@code user_role}(권한), {@code employee_no}(사번), {@code tenancy_type}(소속 타입),
 * {@code tenancy_code}(소속/할당 창고 코드).
 * 미인증(토큰 없음)은 리소스 서버 필터가 401로 처리하므로 여기서는 다루지 않고,
 * 인증은 됐으나 필요한 클레임이 없거나 매핑 불가하면 {@link ForbiddenException}(403)으로 통일한다.
 */
public final class JwtClaimExtractor {

    private static final String ROLE_CLAIM = "user_role";
    private static final String EMPLOYEE_NO_CLAIM = "employee_no";
    private static final String NAME_CLAIM = "name";
    private static final String TENANCY_TYPE_CLAIM = "tenancy_type";
    private static final String TENANCY_CODE_CLAIM = "tenancy_code";

    private JwtClaimExtractor() {
    }

    /** 권한(user_role)을 {@link UserRole}로 반환한다. 클레임 누락·매핑 불가 시 {@link ForbiddenException}. */
    public static UserRole extractRole(Jwt jwt) {
        String role = requireClaim(jwt.getClaimAsString(ROLE_CLAIM));
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException notAUserRole) {
            throw new ForbiddenException();
        }
    }

    /** 소속 타입(tenancy_type)을 {@link TenancyType}로 반환한다(ADMIN/HQ/BRANCH). 누락·매핑 불가 시 {@link ForbiddenException}. */
    public static TenancyType extractTenancyType(Jwt jwt) {
        String type = requireClaim(jwt.getClaimAsString(TENANCY_TYPE_CLAIM));
        try {
            return TenancyType.valueOf(type);
        } catch (IllegalArgumentException notATenancyType) {
            throw new ForbiddenException();
        }
    }

    /** 사번(employee_no)을 반환한다. 누락 시 {@link ForbiddenException}. 생성·수정자 사번 snapshot 등에 사용한다. */
    public static String extractEmployeeNo(Jwt jwt) {
        return requireClaim(jwt.getClaimAsString(EMPLOYEE_NO_CLAIM));
    }

    /** 사원 이름(name)을 반환한다. 누락 시 {@link ForbiddenException}. 수행자 이름 snapshot 등에 사용한다. */
    public static String extractName(Jwt jwt) {
        return requireClaim(jwt.getClaimAsString(NAME_CLAIM));
    }

    /** 사용자에게 할당된 창고 코드(tenancy_code)를 반환한다. 누락 시 {@link ForbiddenException}. BRANCH 조회 범위 검증에 사용한다. */
    public static String extractTenancyCode(Jwt jwt) {
        return requireClaim(jwt.getClaimAsString(TENANCY_CODE_CLAIM));
    }

    /** 사용자 권한이 허용 목록에 포함되는지 검증한다. 포함되지 않으면 {@link ForbiddenException}(403). */
    public static void requireAnyOf(Jwt jwt, UserRole... allowedRoles) {
        UserRole role = extractRole(jwt);
        if (!Set.of(allowedRoles).contains(role)) {
            throw new ForbiddenException();
        }
    }

    private static String requireClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new ForbiddenException();
        }
        return value;
    }
}
