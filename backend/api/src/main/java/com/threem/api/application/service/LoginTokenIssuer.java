package com.threem.api.application.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.threem.api.application.dto.LoginResult;
import com.threem.api.domain.model.RefreshToken;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.RefreshTokenRepository;
import com.threem.api.global.security.JwtProvider;

import lombok.RequiredArgsConstructor;

/**
 * 로그인에 성공한 회원에게 Access Token과 Refresh Token을 발급한다.
 */
@Component
@RequiredArgsConstructor
class LoginTokenIssuer {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    LoginResult issue(User user, Instant now) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole().name(), now);
        String refreshToken = OpaqueTokens.generate();
        refreshTokenRepository.save(RefreshToken.issue(user, OpaqueTokens.hash(refreshToken), now));
        return new LoginResult(accessToken, jwtProvider.accessTokenTtl(), refreshToken, RefreshToken.TTL, user);
    }
}
