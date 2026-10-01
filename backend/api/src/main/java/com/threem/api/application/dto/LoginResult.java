package com.threem.api.application.dto;

import java.time.Duration;

import com.threem.api.domain.model.User;

/**
 * 로그인에 성공해 발급한 토큰. Refresh Token은 원문이며 응답으로만 내보내고 저장하지 않는다.
 */
public record LoginResult(
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl,
        User user
) {
}
