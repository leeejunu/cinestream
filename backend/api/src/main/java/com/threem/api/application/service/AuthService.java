package com.threem.api.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.threem.api.application.dto.LoginCommand;
import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.LogoutCommand;
import com.threem.api.application.dto.OAuthLoginCommand;
import com.threem.api.application.dto.RefreshTokenCommand;
import com.threem.api.application.dto.SignUpCommand;
import com.threem.api.application.usecase.AuthUseCase;
import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.RefreshToken;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.RefreshTokenRepository;
import com.threem.api.domain.repository.UserRepository;
import com.threem.api.global.error.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

    private static final String DEFAULT_NICKNAME_PREFIX = "사용자";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginTokenIssuer loginTokenIssuer;
    private final Clock clock;

    @Override
    @Transactional
    public LoginResult signUp(SignUpCommand command) {
        Instant now = Instant.now(clock);
        userRepository.findByEmail(command.email()).ifPresent(existing -> {
            throw emailAlreadyExists(existing);
        });
        User user = userRepository.save(User.signUp(
                command.email(), passwordEncoder.encode(command.password()), command.nickname(), now));
        return loginTokenIssuer.issue(user, now);
    }

    /**
     * OAuth로 가입한 이메일이면 어떤 제공자로 로그인해야 하는지 알려준다.
     */
    private static BusinessException emailAlreadyExists(User existing) {
        if (existing.hasPassword()) {
            return new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String provider = existing.getProvider().getDisplayName();
        return new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS,
                "%s로 가입된 이메일입니다. %s로 로그인해 주세요".formatted(provider, provider));
    }

    /**
     * 실패해도 실패 횟수·잠금은 저장해야 하므로 {@link BusinessException}으로는 롤백하지 않는다.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginCommand command) {
        Instant now = Instant.now(clock);
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_CREDENTIALS));
        if (user.isLocked(now)) {
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }
        if (!user.hasPassword()) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            user.recordLoginFailure(now);
            userRepository.save(user);
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        user.recordLoginSuccess(now);
        userRepository.save(user);
        return loginTokenIssuer.issue(user, now);
    }

    @Override
    @Transactional
    public LoginResult loginWithOAuth(OAuthLoginCommand command) {
        if (!command.emailVerified()) {
            throw new BusinessException(UserErrorCode.OAUTH_FAILED);
        }
        Instant now = Instant.now(clock);
        User user = userRepository.findByProvider(command.provider(), command.providerUserId())
                .orElseGet(() -> signUpWithOAuth(command, now));
        return loginTokenIssuer.issue(user, now);
    }

    /**
     * 같은 이메일의 계정이 이미 있으면 합치지 않고 거부한다. 이메일 인증이 없어
     * 자동으로 합치면 남의 이메일로 먼저 가입해 둔 사람이 계정을 가로챌 수 있다.
     */
    private User signUpWithOAuth(OAuthLoginCommand command, Instant now) {
        String email = User.normalizeEmail(command.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(UserErrorCode.OAUTH_EMAIL_CONFLICT);
        }
        return userRepository.save(User.signUpWithOAuth(
                command.provider(), command.providerUserId(), email, oauthNickname(command), now));
    }

    private static String oauthNickname(OAuthLoginCommand command) {
        if (User.isValidNickname(command.nickname())) {
            return command.nickname();
        }
        return DEFAULT_NICKNAME_PREFIX + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
    }

    /**
     * 이미 쓴 토큰이 다시 오면 탈취로 보고 그 회원의 Refresh Token을 모두 폐기한다.
     * 폐기는 실패 응답과 함께 저장돼야 하므로 {@link BusinessException}으로는 롤백하지 않는다.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult refresh(RefreshTokenCommand command) {
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokenRepository
                .findByTokenHashForUpdate(RefreshToken.hash(command.refreshToken()))
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));
        if (token.isUsed()) {
            refreshTokenRepository.revokeAllByUserId(token.getUser().getId(), now);
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
        token.use(now);
        refreshTokenRepository.save(token);
        return loginTokenIssuer.issue(token.getUser(), now);
    }

    @Override
    @Transactional
    public void logout(LogoutCommand command) {
        if (command.refreshToken() == null || command.refreshToken().isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        refreshTokenRepository.findByTokenHashForUpdate(RefreshToken.hash(command.refreshToken()))
                .ifPresent(token -> {
                    token.revoke(now);
                    refreshTokenRepository.save(token);
                });
    }
}
