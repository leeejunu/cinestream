package com.threem.api.application.dto;

import java.util.regex.Pattern;

import com.threem.api.domain.model.User;
import com.threem.api.global.error.BusinessException;
import com.threem.api.global.error.CommonErrorCode;

public record SignUpCommand(String email, String password, String nickname) {

    public static final int EMAIL_MAX_LENGTH = 255;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 64;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** BCrypt는 72바이트까지만 쓰므로 1바이트 문자(ASCII 인쇄 가능 문자)만 허용한다. */
    private static final Pattern PASSWORD_CHARS = Pattern.compile("^[\\x21-\\x7E]+$");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    public SignUpCommand {
        email = User.normalizeEmail(email);
        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다");
        }
        if (password == null
                || password.length() < PASSWORD_MIN_LENGTH
                || password.length() > PASSWORD_MAX_LENGTH
                || !PASSWORD_CHARS.matcher(password).matches()
                || !LETTER.matcher(password).find()
                || !DIGIT.matcher(password).find()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "비밀번호는 %d~%d자의 영문, 숫자, 특수문자이고 영문과 숫자를 포함해야 합니다".formatted(PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH));
        }
    }

    @Override
    public String toString() {
        return "SignUpCommand[email=" + email + ", nickname=" + nickname + "]";
    }
}
