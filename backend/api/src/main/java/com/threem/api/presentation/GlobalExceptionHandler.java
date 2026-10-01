package com.threem.api.presentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.threem.api.global.error.BusinessException;
import com.threem.api.global.error.CommonErrorCode;
import com.threem.api.global.error.ErrorCode;
import com.threem.api.global.error.ErrorType;
import com.threem.api.presentation.dto.res.ErrorResponse;

/**
 * 모든 에러를 {@code { code, message }}로 응답한다. {@link ErrorType} → HTTP 상태 변환은 여기서만 한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(toStatus(errorCode.type()))
                .body(new ErrorResponse(errorCode.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidArgument(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.INVALID_INPUT.message());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(CommonErrorCode.INVALID_INPUT.code(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return error(CommonErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return error(CommonErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return error(CommonErrorCode.FORBIDDEN);
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return error(CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return error(CommonErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ErrorResponse> error(ErrorCode errorCode) {
        return ResponseEntity.status(toStatus(errorCode.type())).body(ErrorResponse.of(errorCode));
    }

    private static HttpStatus toStatus(ErrorType type) {
        return switch (type) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
