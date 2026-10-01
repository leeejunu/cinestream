package com.threem.api.presentation.dto.res;

import com.threem.api.global.error.ErrorCode;

public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message());
    }
}
