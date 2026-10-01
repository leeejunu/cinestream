package com.threem.api.application.dto;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.global.error.BusinessException;

public record RefreshTokenCommand(String refreshToken) {

    public RefreshTokenCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Override
    public String toString() {
        return "RefreshTokenCommand[***]";
    }
}
