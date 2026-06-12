package com.fallguys.inventoryservice.shared.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 리소스 서버(JWT) 보안 설정. 모든 요청은 유효한 JWT를 요구하며(401), Role 기반 인가(403)는
 * 컨트롤러에서 {@link JwtClaimExtractor#requireAnyOf}로 강제한다(엔드포인트별 권한이 달라 선언적 매칭 대신 명시 호출).
 *
 * <p>세션은 STATELESS, CSRF는 토큰 기반 무상태 API라 비활성화한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 인증 없이 접근 가능한 경로: 헬스 체크와 API 문서. */
    private static final String[] PUBLIC_PATHS = {
            "/inventory/health",
            "/actuator/health",
            "/inventory/swagger-ui/**",
            "/inventory/swagger-ui.html",
            "/inventory/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Keycloak 공개키(JWK)로 JWT 서명을 검증하는 디코더(운영 기본).
     * withJwkSetUri는 지연 로딩이라 기동 시 네트워크 호출이 없고, 첫 토큰 검증 시 JWKS를 가져온다.
     * (자동 구성에 의존하지 않고 명시적으로 등록해 컨텍스트 구성을 결정적으로 만든다.)
     */
    /*@Bean
    @Profile("!local")
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8080/realms/erp/protocol/openid-connect/certs}")
            String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    *//**
     * 로컬 전용 디코더(Keycloak 없이 Swagger로 인가 테스트). spring.profiles.active=local 일 때만 활성화된다.
     * 서명을 검증하지 않고 Bearer 토큰 문자열을 클레임으로 펼친다.
     * 형식: {@code ROLE} 또는 {@code ROLE~TENANCY_CODE}. (구분자는 '~' — '@'는 Bearer 토큰 허용 문자가 아님)
     * 예) {@code ADMIN} → 전사 / {@code BRANCH_STAFF~WH-SE-001} → BRANCH·창고 WH-SE-001 / 미입력 → 401.
     * employee_no·name은 'local-{ROLE}'로 합성한다(조정 이력의 수행자 사번·이름 스냅샷용).
     *
     * 주의: 서명 검증이 없으므로 절대 운영(local 외 프로파일)에서 활성화하지 않는다.
     *//*
    @Bean
    @Profile("local")
    public JwtDecoder localRoleJwtDecoder() {
        return token -> {
            String[] parts = token.split("~", 2);
            String role = parts[0];
            // tenancy_code 미지정 시 role을 그대로 사용(ADMIN/HQ 전사는 어차피 코드 비교를 안 함).
            String tenancyCode = parts.length > 1 ? parts[1] : role;
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("employee_no", "local-" + role)
                    .claim("name", "local-" + role)
                    .claim("user_role", role)
                    .claim("tenancy_type", localTenancyType(role))
                    .claim("tenancy_code", tenancyCode)
                    .issuedAt(now)
                    .expiresAt(now.plus(1, ChronoUnit.HOURS))
                    .build();
        };
    }

    *//** 로컬 디코더용: user_role 접두어로 tenancy_type을 유추한다(HQ_* → HQ, BRANCH_* → BRANCH, 그 외 → ADMIN). *//*
    private static String localTenancyType(String role) {
        if (role.startsWith("HQ")) {
            return "HQ";
        }
        if (role.startsWith("BRANCH")) {
            return "BRANCH";
        }
        return "ADMIN";
    }*/
}
