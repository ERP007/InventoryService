package com.fallguys.inventoryservice.shared.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

import com.fallguys.inventoryservice.shared.exception.ForbiddenException;
import com.fallguys.inventoryservice.shared.model.UserRole;

/**
 * 리소스 서버가 검증한 JWT에서 사용자 Role을 추출하고 인가를 강제하는 컨트롤러 보조 유틸.
 *
 * <p>Role은 Keycloak 클레임 {@code resource_access.erp-client.roles}(클라이언트 롤)에서 읽는다.
 * 미인증(토큰 없음)은 리소스 서버 필터가 401로 처리하므로 여기서는 다루지 않고,
 * 인증은 됐으나 Role을 판별할 수 없거나 허용 목록 밖이면 {@link ForbiddenException}(403)으로 통일한다.
 */
public final class JwtClaimExtractor {

    /** ERP 전 서비스가 공유하는 Keycloak 클라이언트 id. 클라이언트 롤이 이 키 아래에 담긴다. */
    private static final String CLIENT_NAME = "erp-client";

    private JwtClaimExtractor() {
    }

    /**
     * JWT의 클라이언트 롤 중 {@link UserRole}로 해석 가능한 첫 Role을 반환한다.
     * Keycloak이 롤 순서를 보장하지 않으므로 getFirst가 아니라 매핑 가능한 첫 값을 찾는다.
     * 클레임 누락·형식 오류·매핑 불가 시 {@link ForbiddenException}.
     */
    public static UserRole extractRole(Jwt jwt) {
        return extractClientRoles(jwt).stream()
                .map(JwtClaimExtractor::toUserRole)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(ForbiddenException::new);
    }

    /**
     * 사용자의 Role이 허용 목록에 포함되는지 검증한다. 포함되지 않으면 {@link ForbiddenException}(403).
     * 쓰기·제한 조회 엔드포인트의 첫 줄에서 호출한다.
     */
    public static void requireAnyOf(Jwt jwt, UserRole... allowedRoles) {
        UserRole role = extractRole(jwt);
        if (!Set.of(allowedRoles).contains(role)) {
            throw new ForbiddenException();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractClientRoles(Jwt jwt) {
        try {
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            Map<String, Object> erpClient = (Map<String, Object>) resourceAccess.get(CLIENT_NAME);
            return (List<String>) erpClient.get("roles");
        } catch (RuntimeException e) {
            // 클레임 누락·구조 불일치·타입 불일치 등 모든 파싱 실패는 권한 판별 불가 → 403
            throw new ForbiddenException();
        }
    }

    private static UserRole toUserRole(String role) {
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException notAUserRole) {
            return null;
        }
    }
}
