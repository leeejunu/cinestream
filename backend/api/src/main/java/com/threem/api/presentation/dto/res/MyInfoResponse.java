package com.threem.api.presentation.dto.res;

import com.threem.api.domain.model.User;

/**
 * @param provider 가입한 로그인 수단. {@code LOCAL}(이메일·비밀번호), {@code GOOGLE}
 */
public record MyInfoResponse(Long id, String email, String nickname, String role, String provider) {

    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole().name(),
                user.getProvider().name());
    }
}
