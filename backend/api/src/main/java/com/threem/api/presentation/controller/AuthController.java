package com.threem.api.presentation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.LogoutCommand;
import com.threem.api.application.dto.RefreshTokenCommand;
import com.threem.api.application.usecase.AuthUseCase;
import com.threem.api.global.security.RefreshTokenCookies;
import com.threem.api.presentation.dto.req.LoginRequest;
import com.threem.api.presentation.dto.req.SignUpRequest;
import com.threem.api.presentation.dto.res.LoginResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입·로그인·토큰 재발급·로그아웃.
 * Google 로그인({@code GET /api/v1/auth/oauth/authorize/google})은 Spring Security가 처리하고,
 * 끝나면 Refresh Token 쿠키만 심어 주므로 프론트엔드는 {@code /refresh}로 Access Token을 받는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthUseCase authUseCase;
    private final RefreshTokenCookies refreshTokenCookies;

    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return loggedIn(HttpStatus.CREATED, authUseCase.signUp(request.toCommand()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return loggedIn(HttpStatus.OK, authUseCase.login(request.toCommand()));
    }

    @PostMapping("/refresh")
    ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        return loggedIn(HttpStatus.OK, authUseCase.refresh(new RefreshTokenCommand(refreshToken)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        authUseCase.logout(new LogoutCommand(refreshToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> loggedIn(HttpStatus status, LoginResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookies.create(result.refreshToken(), result.refreshTokenTtl()).toString())
                .body(LoginResponse.from(result));
    }
}
