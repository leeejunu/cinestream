package com.threem.api.global.error;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT("입력값이 올바르지 않습니다", ErrorType.INVALID),
    UNAUTHORIZED("로그인이 필요합니다", ErrorType.UNAUTHORIZED),
    FORBIDDEN("권한이 없습니다", ErrorType.FORBIDDEN),
    NOT_FOUND("요청한 리소스를 찾을 수 없습니다", ErrorType.NOT_FOUND),
    INTERNAL_ERROR("서버 오류가 발생했습니다", ErrorType.INTERNAL);

    private final String message;
    private final ErrorType type;

    CommonErrorCode(String message, ErrorType type) {
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
