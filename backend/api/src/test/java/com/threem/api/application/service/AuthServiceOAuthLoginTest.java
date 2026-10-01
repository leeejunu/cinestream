package com.threem.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.OAuthLoginCommand;
import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.domain.model.RefreshToken;
import com.threem.api.domain.model.Role;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.RefreshTokenRepository;
import com.threem.api.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceOAuthLoginTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginTokenIssuer loginTokenIssuer;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, loginTokenIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("처음 Google로 로그인하면 VIEWER로 가입된다")
    void firstLoginSignsUp() {
        given(userRepository.findByProvider(AuthProvider.GOOGLE, "sub-1")).willReturn(Optional.empty());
        given(userRepository.findByEmail("viewer@gmail.com")).willReturn(Optional.empty());
        given(userRepository.save(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        given(loginTokenIssuer.issue(any(), any())).willAnswer(invocation -> loginResult(invocation.getArgument(0)));

        LoginResult result = authService.loginWithOAuth(command("Viewer@gmail.com", true, "구글 관람객"));

        User user = result.user();
        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("sub-1");
        assertThat(user.getEmail()).isEqualTo("viewer@gmail.com");
        assertThat(user.getNickname()).isEqualTo("구글 관람객");
        assertThat(user.getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    @DisplayName("이미 가입한 Google 계정이면 그 회원으로 로그인된다")
    void existingUserLogsIn() {
        User user = withId(User.signUpWithOAuth(AuthProvider.GOOGLE, "sub-1", "viewer@gmail.com", "관람객", NOW), 1L);
        given(userRepository.findByProvider(AuthProvider.GOOGLE, "sub-1")).willReturn(Optional.of(user));
        given(loginTokenIssuer.issue(user, NOW)).willReturn(loginResult(user));

        LoginResult result = authService.loginWithOAuth(command("viewer@gmail.com", true, "관람객"));

        assertThat(result.user()).isSameAs(user);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("같은 이메일의 일반 계정이 있으면 합치지 않고 거부한다")
    void emailConflict() {
        given(userRepository.findByProvider(AuthProvider.GOOGLE, "sub-1")).willReturn(Optional.empty());
        given(userRepository.findByEmail("viewer@gmail.com"))
                .willReturn(Optional.of(User.signUp("viewer@gmail.com", "hashed", "관람객", NOW)));

        assertThatThrownBy(() -> authService.loginWithOAuth(command("viewer@gmail.com", true, "관람객")))
                .extracting("errorCode").isEqualTo(UserErrorCode.OAUTH_EMAIL_CONFLICT);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Google이 이메일을 인증하지 않았으면 로그인할 수 없다")
    void unverifiedEmail() {
        assertThatThrownBy(() -> authService.loginWithOAuth(command("viewer@gmail.com", false, "관람객")))
                .extracting("errorCode").isEqualTo(UserErrorCode.OAUTH_FAILED);
    }

    @Test
    @DisplayName("Google 닉네임이 규칙에 맞지 않으면 '사용자+숫자'로 만든다")
    void defaultNickname() {
        given(userRepository.findByProvider(AuthProvider.GOOGLE, "sub-1")).willReturn(Optional.empty());
        given(userRepository.findByEmail("viewer@gmail.com")).willReturn(Optional.empty());
        given(userRepository.save(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        given(loginTokenIssuer.issue(any(), any())).willAnswer(invocation -> loginResult(invocation.getArgument(0)));

        LoginResult result = authService.loginWithOAuth(command("viewer@gmail.com", true, null));

        assertThat(result.user().getNickname()).matches("사용자\\d{6}");
    }

    private static OAuthLoginCommand command(String email, boolean emailVerified, String nickname) {
        return new OAuthLoginCommand(AuthProvider.GOOGLE, "sub-1", email, emailVerified, nickname);
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static LoginResult loginResult(User user) {
        return new LoginResult("access", Duration.ofMinutes(30), "refresh", RefreshToken.TTL, user);
    }
}
