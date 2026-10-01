package com.threem.api.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.threem.api.global.config.AuthProperties;

/**
 * Access Token(JWT, HS256) 발급. 검증은 {@link SecurityConfig}의 resource server가 한다.
 * {@code sub}는 회원 ID, {@code role} 클레임은 역할이다.
 */
@Component
public class JwtProvider {

    static final String ISSUER = "threem-api";
    static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;

    public JwtProvider(AuthProperties authProperties) {
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(authProperties)));
        this.accessTokenTtl = authProperties.accessTokenTtl();
    }

    public String createAccessToken(Long userId, String role, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .claim(ROLE_CLAIM, role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    static SecretKey secretKey(AuthProperties authProperties) {
        return new SecretKeySpec(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
