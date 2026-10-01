package com.threem.api.global.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 설정. 비밀값은 환경 변수로 받는다.
 *
 * @param jwtSecret      Access Token 서명 키 (HS256, 32바이트 이상)
 * @param accessTokenTtl Access Token 수명
 * @param frontendUrl    CORS 허용 origin이자 OAuth 로그인 후 돌아갈 주소
 * @param cookieSecure   Refresh Token 쿠키의 Secure 속성
 */
@ConfigurationProperties("threem.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("30m") Duration accessTokenTtl,
        String frontendUrl,
        @DefaultValue("true") boolean cookieSecure
) {

    private static final int MIN_JWT_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "threem.auth.jwt-secret(JWT_SECRET)은 %d바이트 이상이어야 합니다".formatted(MIN_JWT_SECRET_BYTES));
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalStateException("threem.auth.frontend-url(FRONTEND_URL)이 필요합니다");
        }
    }
}
