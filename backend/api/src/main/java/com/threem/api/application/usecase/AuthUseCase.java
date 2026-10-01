package com.threem.api.application.usecase;

import com.threem.api.application.dto.LoginCommand;
import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.LogoutCommand;
import com.threem.api.application.dto.OAuthLoginCommand;
import com.threem.api.application.dto.RefreshTokenCommand;
import com.threem.api.application.dto.SignUpCommand;

public interface AuthUseCase {

    /**
     * 이메일·비밀번호로 가입하고 바로 로그인시킨다.
     */
    LoginResult signUp(SignUpCommand command);

    LoginResult login(LoginCommand command);

    /**
     * OAuth 제공자 인증을 마친 사용자를 로그인시킨다. 처음이면 가입시킨다.
     */
    LoginResult loginWithOAuth(OAuthLoginCommand command);

    /**
     * Refresh Token으로 토큰을 새로 발급한다. 쓴 Refresh Token은 다시 쓸 수 없다.
     */
    LoginResult refresh(RefreshTokenCommand command);

    void logout(LogoutCommand command);
}
