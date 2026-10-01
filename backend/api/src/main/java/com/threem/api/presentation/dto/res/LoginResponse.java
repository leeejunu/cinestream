package com.threem.api.presentation.dto.res;

import com.threem.api.application.dto.LoginResult;
import com.threem.api.domain.model.User;

/**
 * Refresh Token은 본문에 싣지 않고 HttpOnly 쿠키로만 내려준다.
 *
 * @param expiresIn Access Token 수명(초)
 */
public record LoginResponse(String accessToken, long expiresIn, UserSummary user) {

    public record UserSummary(Long id, String nickname, String role) {

        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getNickname(), user.getRole().name());
        }
    }

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), result.accessTokenTtl().toSeconds(),
                UserSummary.from(result.user()));
    }
}
