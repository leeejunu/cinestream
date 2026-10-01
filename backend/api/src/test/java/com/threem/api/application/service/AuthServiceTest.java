package com.threem.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.threem.api.application.dto.LoginCommand;
import com.threem.api.application.dto.LoginResult;
import com.threem.api.application.dto.LogoutCommand;
import com.threem.api.application.dto.RefreshTokenCommand;
import com.threem.api.application.dto.SignUpCommand;
import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.domain.model.RefreshToken;
import com.threem.api.domain.model.Role;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.RefreshTokenRepository;
import com.threem.api.domain.repository.UserRepository;
import com.threem.api.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    @DisplayName("가입하면 비밀번호를 해시로 저장하고 VIEWER로 로그인시킨다")
    void signUp() {
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("password1")).willReturn("hashed");
        given(userRepository.save(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        given(loginTokenIssuer.issue(any(), any())).willAnswer(invocation -> loginResult(invocation.getArgument(0)));

        LoginResult result = authService.signUp(new SignUpCommand("Viewer@Example.com", "password1", "관람객"));

        assertThat(result.user().getEmail()).isEqualTo("viewer@example.com");
        assertThat(result.user().getPasswordHash()).isEqualTo("hashed");
        assertThat(result.user().getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 가입할 수 없다")
    void signUpDuplicateEmail() {
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.of(localUser()));

        assertThatThrownBy(() -> authService.signUp(new SignUpCommand("viewer@example.com", "password1", "관람객")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 가입된 이메일입니다")
                .extracting("errorCode").isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Google로 가입된 이메일로 일반 가입하면 Google로 로그인하라고 알려준다")
    void signUpWithGoogleEmail() {
        User googleUser = User.signUpWithOAuth(AuthProvider.GOOGLE, "sub", "viewer@example.com", "관람객", NOW);
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.of(googleUser));

        assertThatThrownBy(() -> authService.signUp(new SignUpCommand("viewer@example.com", "password1", "관람객")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Google로 가입된 이메일입니다. Google로 로그인해 주세요");
    }

    @Test
    @DisplayName("비밀번호가 규칙에 맞지 않으면 가입할 수 없다")
    void invalidPassword() {
        assertThatThrownBy(() -> new SignUpCommand("a@b.com", "short1", "관람객"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new SignUpCommand("a@b.com", "onlyletters", "관람객"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new SignUpCommand("a@b.com", "12345678", "관람객"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new SignUpCommand("a@b.com", "비밀번호abc123", "관람객"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 로그인되고 실패 기록이 지워진다")
    void login() {
        User user = localUser();
        user.recordLoginFailure(NOW.minusSeconds(60));
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1", "hashed")).willReturn(true);
        given(loginTokenIssuer.issue(user, NOW)).willReturn(loginResult(user));

        LoginResult result = authService.login(new LoginCommand("VIEWER@example.com", "password1"));

        assertThat(result.user()).isSameAs(user);
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 에러로 응답한다")
    void loginFailuresLookTheSame() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.of(localUser()));
        given(passwordEncoder.matches("wrong1234", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("nobody@example.com", "password1")))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> authService.login(new LoginCommand("viewer@example.com", "wrong1234")))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("5번 틀리면 잠기고, 잠긴 동안은 맞는 비밀번호로도 로그인할 수 없다")
    void lockAfterFailures() {
        User user = localUser();
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong1234", "hashed")).willReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginCommand("viewer@example.com", "wrong1234")))
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        }

        assertThatThrownBy(() -> authService.login(new LoginCommand("viewer@example.com", "password1")))
                .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
        then(passwordEncoder).should(never()).matches("password1", "hashed");
    }

    @Test
    @DisplayName("Google 회원이 비밀번호로 로그인하면 일반 실패와 같은 에러로 응답한다")
    void googleUserCannotUsePassword() {
        User googleUser = User.signUpWithOAuth(AuthProvider.GOOGLE, "sub", "viewer@example.com", "관람객", NOW);
        given(userRepository.findByEmail("viewer@example.com")).willReturn(Optional.of(googleUser));

        assertThatThrownBy(() -> authService.login(new LoginCommand("viewer@example.com", "password1")))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
        then(passwordEncoder).should(never()).matches(anyString(), any());
    }

    @Test
    @DisplayName("Refresh Token으로 재발급하면 이전 토큰은 사용 처리된다")
    void refresh() {
        User user = localUser();
        RefreshToken token = RefreshToken.issue(user, OpaqueTokens.hash("raw-token"), NOW.minusSeconds(60));
        given(refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokens.hash("raw-token")))
                .willReturn(Optional.of(token));
        given(loginTokenIssuer.issue(user, NOW)).willReturn(loginResult(user));

        authService.refresh(new RefreshTokenCommand("raw-token"));

        assertThat(token.isUsed()).isTrue();
    }

    @Test
    @DisplayName("이미 쓴 Refresh Token이 다시 오면 그 회원의 Refresh Token을 모두 폐기한다")
    void refreshReuseRevokesAll() {
        User user = localUser();
        RefreshToken token = RefreshToken.issue(user, OpaqueTokens.hash("raw-token"), NOW.minusSeconds(60));
        token.use(NOW.minusSeconds(30));
        given(refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokens.hash("raw-token")))
                .willReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenCommand("raw-token")))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
        then(refreshTokenRepository).should().revokeAllByUserId(1L, NOW);
        then(loginTokenIssuer).should(never()).issue(any(), any());
    }

    @Test
    @DisplayName("없는 Refresh Token으로는 재발급할 수 없다")
    void refreshUnknownToken() {
        given(refreshTokenRepository.findByTokenHashForUpdate(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenCommand("unknown")))
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("로그아웃하면 Refresh Token을 폐기한다")
    void logout() {
        RefreshToken token = RefreshToken.issue(localUser(), OpaqueTokens.hash("raw-token"), NOW.minusSeconds(60));
        given(refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokens.hash("raw-token")))
                .willReturn(Optional.of(token));

        authService.logout(new LogoutCommand("raw-token"));

        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Refresh Token 없이 로그아웃해도 실패하지 않는다")
    void logoutWithoutToken() {
        authService.logout(new LogoutCommand(null));

        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    private static User localUser() {
        return withId(User.signUp("viewer@example.com", "hashed", "관람객", NOW.minus(Duration.ofDays(1))), 1L);
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static LoginResult loginResult(User user) {
        return new LoginResult("access", Duration.ofMinutes(30), "refresh", RefreshToken.TTL, user);
    }
}
