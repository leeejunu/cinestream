package com.threem.api.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.threem.api.global.error.BusinessException;
import com.threem.api.global.error.CommonErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 20;

    static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(15);
    static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소문자로 정규화해 저장한다. */
    @Column(nullable = false, unique = true)
    private String email;

    /** {@link AuthProvider#LOCAL}만 갖는다. */
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuthProvider provider;

    /** OAuth 제공자의 사용자 ID. OAuth 회원은 이메일이 아니라 {@code (provider, providerId)}로 찾는다. */
    @Column(updatable = false)
    private String providerId;

    @Column(nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private int failedLoginCount;

    /** 연속 실패가 시작된 시각. 이 시각부터 {@link #FAILED_LOGIN_WINDOW} 안의 실패만 센다. */
    private Instant firstFailedLoginAt;

    private Instant lockedUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private User(String email, String passwordHash, AuthProvider provider, String providerId, String nickname,
            Instant now) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = validateNickname(nickname);
        this.role = Role.VIEWER;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 이메일·비밀번호로 가입한다. 가입하면 항상 {@link Role#VIEWER}다.
     */
    public static User signUp(String email, String passwordHash, String nickname, Instant now) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "비밀번호가 필요합니다");
        }
        return new User(email, passwordHash, AuthProvider.LOCAL, null, nickname, now);
    }

    /**
     * OAuth 첫 로그인 시 비밀번호 없이 가입한다. 가입하면 항상 {@link Role#VIEWER}다.
     */
    public static User signUpWithOAuth(AuthProvider provider, String providerId, String email, String nickname,
            Instant now) {
        if (provider == null || provider == AuthProvider.LOCAL || providerId == null || providerId.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "OAuth 제공자 정보가 필요합니다");
        }
        return new User(email, null, provider, providerId, nickname, now);
    }

    /**
     * 이메일은 대소문자를 구분하지 않으므로 소문자로 저장하고 조회한다.
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "이메일이 필요합니다");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidNickname(String nickname) {
        if (nickname == null) {
            return false;
        }
        int length = nickname.strip().length();
        return length >= NICKNAME_MIN_LENGTH && length <= NICKNAME_MAX_LENGTH;
    }

    private static String validateNickname(String nickname) {
        if (!isValidNickname(nickname)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "닉네임은 %d~%d자여야 합니다".formatted(NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH));
        }
        return nickname.strip();
    }

    public boolean hasPassword() {
        return provider == AuthProvider.LOCAL;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * 비밀번호가 틀린 로그인을 기록한다. {@link #FAILED_LOGIN_WINDOW} 안에
     * {@link #MAX_FAILED_LOGIN_ATTEMPTS}번 실패하면 {@link #LOGIN_LOCK_DURATION} 동안 잠근다.
     */
    public void recordLoginFailure(Instant now) {
        if (firstFailedLoginAt == null || !now.isBefore(firstFailedLoginAt.plus(FAILED_LOGIN_WINDOW))) {
            failedLoginCount = 0;
            firstFailedLoginAt = now;
        }
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGIN_ATTEMPTS) {
            lockedUntil = now.plus(LOGIN_LOCK_DURATION);
            failedLoginCount = 0;
            firstFailedLoginAt = null;
        }
        updatedAt = now;
    }

    public void recordLoginSuccess(Instant now) {
        if (failedLoginCount == 0 && firstFailedLoginAt == null && lockedUntil == null) {
            return;
        }
        failedLoginCount = 0;
        firstFailedLoginAt = null;
        lockedUntil = null;
        updatedAt = now;
    }
}
