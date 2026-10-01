package com.threem.api.global.error;

/**
 * 에러의 종류. HTTP 상태로의 변환은 {@code GlobalExceptionHandler}가 한다.
 */
public enum ErrorType {
    INVALID,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    TOO_MANY_REQUESTS,
    INTERNAL
}
