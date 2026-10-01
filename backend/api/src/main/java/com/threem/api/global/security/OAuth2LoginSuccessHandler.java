package com.threem.api.global.security;

import java.io.IOException;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.OAuthLoginCommand;
import com.threem.api.application.usecase.AuthUseCase;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.global.config.AuthProperties;
import com.threem.api.global.error.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Google 인증이 끝나면 회원을 로그인시키고(처음이면 가입) Refresh Token 쿠키를 심은 뒤
 * 프론트엔드 {@code {frontendUrl}/oauth/callback}으로 돌려보낸다. 실패하면 {@code ?error={에러 코드}}를 붙인다.
 * 프론트엔드는 돌아오면 {@code POST /api/v1/auth/refresh}로 Access Token을 받는다.
 */
@Component
@RequiredArgsConstructor
class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String FRONTEND_CALLBACK_PATH = "/oauth/callback";

    private final AuthUseCase authUseCase;
    private final RefreshTokenCookies refreshTokenCookies;
    private final AuthProperties authProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = token.getPrincipal();
        String errorCode = null;
        try {
            LoginResult result = authUseCase.loginWithOAuth(new OAuthLoginCommand(
                    AuthProvider.valueOf(token.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT)),
                    oauthUser.getName(),
                    oauthUser.getAttribute("email"),
                    Boolean.TRUE.equals(oauthUser.getAttribute("email_verified")),
                    oauthUser.getAttribute("name")));
            response.addHeader(HttpHeaders.SET_COOKIE,
                    refreshTokenCookies.create(result.refreshToken(), result.refreshTokenTtl()).toString());
        } catch (BusinessException e) {
            errorCode = e.getErrorCode().code();
        }
        redirectToFrontend(request, response, authProperties.frontendUrl(), errorCode);
    }

    /**
     * OAuth 인가 요청(state)을 담아 두던 세션은 로그인이 끝나면 필요 없으므로 지운다.
     *
     * @param errorCode 성공이면 null
     */
    static void redirectToFrontend(HttpServletRequest request, HttpServletResponse response,
            String frontendUrl, String errorCode) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(frontendUrl).path(FRONTEND_CALLBACK_PATH);
        if (errorCode != null) {
            url.queryParam("error", errorCode);
        }
        response.sendRedirect(url.encode().toUriString());
    }
}
