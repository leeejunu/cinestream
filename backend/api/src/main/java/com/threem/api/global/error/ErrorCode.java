package com.threem.api.global.error;

/**
 * 응답의 {@code code}, 기본 메시지, 에러 종류. feature마다 enum으로 구현한다.
 */
public interface ErrorCode {

    String code();

    String message();

    ErrorType type();
}
