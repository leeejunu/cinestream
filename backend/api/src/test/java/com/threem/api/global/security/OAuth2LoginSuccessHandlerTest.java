package com.threem.api.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.OAuthLoginCommand;
import com.threem.api.application.usecase.AuthUseCase;
import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.domain.model.RefreshToken;
import com.threem.api.domain.model.User;
import com.threem.api.global.config.AuthProperties;
import com.threem.api.global.error.BusinessException;

class OAuth2LoginSuccessHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:3000";

    private final AuthUseCase authUseCase = mock(AuthUseCase.class);
    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties(
                "test-jwt-secret-that-is-at-least-32-bytes", Duration.ofMinutes(30), FRONTEND_URL, true);
        handler = new OAuth2LoginSuccessHandler(
                authUseCase, new RefreshTokenCookies(authProperties), authProperties);
    }

    @Test
    @DisplayName("Google 로그인에 성공하면 Refresh Token 쿠키를 심고 프론트엔드 콜백으로 보낸다")
    void success() throws Exception {
        User user = User.signUpWithOAuth(AuthProvider.GOOGLE, "sub-1", "viewer@gmail.com", "관람객", Instant.now());
        given(authUseCase.loginWithOAuth(any()))
                .willReturn(new LoginResult("access", Duration.ofMinutes(30), "refresh-raw", RefreshToken.TTL, user));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_URL + "/oauth/callback");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=refresh-raw")
                .contains("HttpOnly", "Path=/api/v1/auth");
        assertThat(request.getSession(false)).isNull();

        ArgumentCaptor<OAuthLoginCommand> command = ArgumentCaptor.forClass(OAuthLoginCommand.class);
        org.mockito.BDDMockito.then(authUseCase).should().loginWithOAuth(command.capture());
        assertThat(command.getValue()).isEqualTo(
                new OAuthLoginCommand(AuthProvider.GOOGLE, "sub-1", "viewer@gmail.com", true, "관람객"));
    }

    @Test
    @DisplayName("이메일이 기존 일반 계정과 겹치면 쿠키 없이 에러 코드를 붙여 프론트엔드로 보낸다")
    void emailConflict() throws Exception {
        given(authUseCase.loginWithOAuth(any()))
                .willThrow(new BusinessException(UserErrorCode.OAUTH_EMAIL_CONFLICT));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_URL + "/oauth/callback?error=OAUTH_EMAIL_CONFLICT");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private static OAuth2AuthenticationToken googleAuthentication() {
        DefaultOAuth2User oauthUser = new DefaultOAuth2User(List.of(),
                Map.of("sub", "sub-1", "email", "viewer@gmail.com", "email_verified", true, "name", "관람객"),
                "sub");
        return new OAuth2AuthenticationToken(oauthUser, List.of(), "google");
    }
}
