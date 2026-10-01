package com.threem.api.application.dto;

import java.nio.charset.StandardCharsets;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.User;
import com.threem.api.global.error.BusinessException;

public record LoginCommand(String email, String password) {

    /** BCrypt가 비교할 수 있는 최대 길이. 이보다 길면 가입할 수 없는 비밀번호다. */
    private static final int PASSWORD_MAX_BYTES = 72;

    public LoginCommand {
        if (email == null || email.isBlank() || password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        email = User.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginCommand[email=" + email + "]";
    }
}
