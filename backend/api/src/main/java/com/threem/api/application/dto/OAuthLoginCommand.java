package com.threem.api.application.dto;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.global.error.BusinessException;

/**
 * OAuth 제공자가 알려준 사용자 정보.
 *
 * @param nickname 제공자가 주지 않았으면 null
 */
public record OAuthLoginCommand(
        AuthProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String nickname
) {

    public OAuthLoginCommand {
        if (provider == null || provider == AuthProvider.LOCAL || providerUserId == null || providerUserId.isBlank()
                || email == null || email.isBlank()) {
            throw new BusinessException(UserErrorCode.OAUTH_FAILED);
        }
    }
}
