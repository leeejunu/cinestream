package com.threem.api.domain.error;

import com.threem.api.global.error.ErrorCode;
import com.threem.api.global.error.ErrorType;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("회원을 찾을 수 없습니다", ErrorType.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("이미 가입된 이메일입니다", ErrorType.CONFLICT),
    INVALID_CREDENTIALS("이메일 또는 비밀번호가 올바르지 않습니다", ErrorType.UNAUTHORIZED),
    LOGIN_LOCKED("로그인에 여러 번 실패했습니다. 15분 뒤 다시 시도해 주세요", ErrorType.TOO_MANY_REQUESTS),
    INVALID_REFRESH_TOKEN("다시 로그인해 주세요", ErrorType.UNAUTHORIZED),
    OAUTH_FAILED("소셜 로그인에 실패했습니다", ErrorType.UNAUTHORIZED),
    OAUTH_EMAIL_CONFLICT("이미 이메일로 가입된 계정입니다. 이메일로 로그인해 주세요", ErrorType.CONFLICT);

    private final String message;
    private final ErrorType type;

    UserErrorCode(String message, ErrorType type) {
        this.message = message;
        this.type = type;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public ErrorType type() {
        return type;
    }
}
