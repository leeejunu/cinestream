package com.threem.api.infrastructure.persistence.refreshtoken;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.threem.api.domain.model.RefreshToken;
import com.threem.api.domain.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return refreshTokenJpaRepository.findByTokenHashForUpdate(tokenHash);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public void revokeAllByUserId(Long userId, Instant now) {
        refreshTokenJpaRepository.revokeAllByUserId(userId, now);
    }
}
