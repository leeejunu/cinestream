package com.threem.api.domain.repository;

import java.time.Instant;
import java.util.Optional;

import com.threem.api.domain.model.RefreshToken;

public interface RefreshTokenRepository {

    /**
     * 같은 토큰으로 동시에 재발급하지 못하도록 트랜잭션이 끝날 때까지 행을 잠근다.
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    /**
     * 회원의 폐기되지 않은 Refresh Token을 모두 폐기한다.
     */
    void revokeAllByUserId(Long userId, Instant now);
}
