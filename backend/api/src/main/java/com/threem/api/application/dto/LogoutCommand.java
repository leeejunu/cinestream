package com.threem.api.application.dto;

/**
 * @param refreshToken 없으면 null. 로그아웃은 토큰이 없거나 잘못돼도 실패하지 않는다.
 */
public record LogoutCommand(String refreshToken) {

    @Override
    public String toString() {
        return "LogoutCommand[***]";
    }
}
