package com.threem.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.global.error.BusinessException;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final User USER = User.signUp("a@b.com", "hash", "관람객", NOW);

    @Test
    @DisplayName("발급하면 14일 뒤에 만료된다")
    void issue() {
        RefreshToken token = RefreshToken.issue(USER, "hash", NOW);

        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(RefreshToken.TTL));
        assertThat(token.isExpired(NOW.plus(RefreshToken.TTL).minusSeconds(1))).isFalse();
        assertThat(token.isExpired(NOW.plus(RefreshToken.TTL))).isTrue();
    }

    @Test
    @DisplayName("한 번 쓴 토큰은 다시 쓸 수 없다")
    void useOnce() {
        RefreshToken token = RefreshToken.issue(USER, "hash", NOW);

        token.use(NOW);

        assertThat(token.isUsed()).isTrue();
        assertThatThrownBy(() -> token.use(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("폐기되거나 만료된 토큰은 쓸 수 없다")
    void revokedOrExpired() {
        RefreshToken revoked = RefreshToken.issue(USER, "hash", NOW);
        revoked.revoke(NOW);
        RefreshToken expired = RefreshToken.issue(USER, "hash", NOW);

        assertThatThrownBy(() -> revoked.use(NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> expired.use(NOW.plus(RefreshToken.TTL))).isInstanceOf(BusinessException.class);
    }
}
