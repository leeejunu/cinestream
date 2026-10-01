package com.threem.api.global.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.threem.api.global.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Refresh Token 쿠키. 스크립트가 읽지 못하게 HttpOnly이고, 인증 API로만 전송되도록 경로를 좁힌다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/v1/auth";

    private final AuthProperties authProperties;

    public ResponseCookie create(String refreshToken, Duration ttl) {
        return base(refreshToken).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite("Lax")
                .path(PATH);
    }
}
