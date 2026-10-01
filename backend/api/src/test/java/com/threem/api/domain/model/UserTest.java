package com.threem.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.threem.api.global.error.BusinessException;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("가입하면 이메일은 소문자로 저장되고 역할은 VIEWER다")
    void signUp() {
        User user = User.signUp(" Foo@Example.COM ", "hash", " 관람객 ", NOW);

        assertThat(user.getEmail()).isEqualTo("foo@example.com");
        assertThat(user.getNickname()).isEqualTo("관람객");
        assertThat(user.getRole()).isEqualTo(Role.VIEWER);
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.hasPassword()).isTrue();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("OAuth로 가입하면 비밀번호가 없고 역할은 VIEWER다")
    void signUpWithOAuth() {
        User user = User.signUpWithOAuth(AuthProvider.GOOGLE, "google-sub", "foo@gmail.com", "구글유저", NOW);

        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("google-sub");
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    @DisplayName("닉네임이 2자 미만이거나 20자를 넘으면 가입할 수 없다")
    void invalidNickname() {
        assertThatThrownBy(() -> User.signUp("a@b.com", "hash", "가", NOW))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> User.signUp("a@b.com", "hash", "가".repeat(21), NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("15분 안에 5번 실패하면 15분 동안 잠긴다")
    void lockAfterFiveFailures() {
        User user = User.signUp("a@b.com", "hash", "관람객", NOW);

        for (int i = 0; i < 4; i++) {
            user.recordLoginFailure(NOW.plus(Duration.ofMinutes(i)));
        }
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(4)))).isFalse();

        Instant fifthFailure = NOW.plus(Duration.ofMinutes(10));
        user.recordLoginFailure(fifthFailure);

        assertThat(user.isLocked(fifthFailure)).isTrue();
        assertThat(user.isLocked(fifthFailure.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(fifthFailure.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    @DisplayName("첫 실패 후 15분이 지나면 실패 횟수를 새로 센다")
    void failureWindowResets() {
        User user = User.signUp("a@b.com", "hash", "관람객", NOW);
        for (int i = 0; i < 4; i++) {
            user.recordLoginFailure(NOW);
        }

        Instant later = NOW.plus(Duration.ofMinutes(15));
        user.recordLoginFailure(later);

        assertThat(user.isLocked(later)).isFalse();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 횟수와 잠금을 초기화한다")
    void successResetsFailures() {
        User user = User.signUp("a@b.com", "hash", "관람객", NOW);
        user.recordLoginFailure(NOW);
        user.recordLoginFailure(NOW);

        user.recordLoginSuccess(NOW);

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getFirstFailedLoginAt()).isNull();
        assertThat(user.getLockedUntil()).isNull();
    }
}
