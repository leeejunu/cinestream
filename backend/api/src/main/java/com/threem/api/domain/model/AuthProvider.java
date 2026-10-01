package com.threem.api.domain.model;

import lombok.Getter;

/**
 * 회원이 가입한 로그인 수단. 회원 한 명은 수단 하나만 갖는다.
 */
@Getter
public enum AuthProvider {
    /** 이메일·비밀번호 */
    LOCAL("이메일"),
    GOOGLE("Google");

    private final String displayName;

    AuthProvider(String displayName) {
        this.displayName = displayName;
    }
}
