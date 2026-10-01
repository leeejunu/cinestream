package com.threem.api.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.global.error.BusinessException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 유지용 토큰. 원문은 클라이언트만 갖고 서버는 해시만 저장한다.
 * 재발급할 때마다 한 번 쓰고 버린다(rotation).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    public static final Duration TTL = Duration.ofDays(14);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private RefreshToken(User user, String tokenHash, Instant now) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = now.plus(TTL);
        this.createdAt = now;
    }

    public static RefreshToken issue(User user, String tokenHash, Instant now) {
        return new RefreshToken(user, tokenHash, now);
    }

    /**
     * 클라이언트에 줄 원문 토큰. 32바이트 난수다.
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * DB에 저장하고 조회할 때 쓰는 SHA-256 해시. 원문이 충분히 긴 난수라 솔트 없이 빠른 해시로 충분하다.
     */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 이미 재발급에 쓰인 토큰이면 true. 이런 토큰이 다시 들어오면 탈취를 의심한다.
     */
    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 재발급에 쓴다. 이미 썼거나, 폐기됐거나, 만료된 토큰이면 거부한다.
     */
    public void use(Instant now) {
        if (isUsed() || isRevoked() || isExpired(now)) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
        usedAt = now;
    }

    public void revoke(Instant now) {
        if (!isRevoked()) {
            revokedAt = now;
        }
    }
}
