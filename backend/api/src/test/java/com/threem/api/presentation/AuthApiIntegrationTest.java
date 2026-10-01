package com.threem.api.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.threem.api.TestcontainersConfiguration;
import com.threem.api.global.security.RefreshTokenCookies;

import jakarta.servlet.http.Cookie;

/**
 * 웹 → 서비스 → PostgreSQL까지 실제로 거치는 인증 API 시나리오.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiIntegrationTest {

    private static final String PASSWORD = "password1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("가입하면 Access Token과 HttpOnly Refresh Token 쿠키를 받고, 그 토큰으로 내 정보를 조회한다")
    void signUpAndGetMe() throws Exception {
        String email = uniqueEmail();

        MvcResult result = signUp(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.role").value("VIEWER"))
                .andExpect(cookie().httpOnly(RefreshTokenCookies.NAME, true))
                .andExpect(cookie().path(RefreshTokenCookies.NAME, RefreshTokenCookies.PATH))
                .andReturn();

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    @DisplayName("대소문자만 다른 이메일로 가입하면 409 EMAIL_ALREADY_EXISTS")
    void duplicateEmail() throws Exception {
        String email = uniqueEmail();
        signUp(email).andExpect(status().isCreated());

        signUp(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("비밀번호 규칙을 어기면 400 INVALID_INPUT")
    void invalidPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(uniqueEmail(), "onlyletters")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("5번 틀리면 잠겨서 맞는 비밀번호로도 429 LOGIN_LOCKED")
    void loginLock() throws Exception {
        String email = uniqueEmail();
        signUp(email).andExpect(status().isCreated());

        login(email, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            login(email, "wrong1234")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    @DisplayName("없는 이메일은 틀린 비밀번호와 같은 401 INVALID_CREDENTIALS")
    void unknownEmail() throws Exception {
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("재발급하면 새 Refresh Token을 주고, 이전 토큰을 다시 쓰면 그 회원의 토큰이 모두 폐기된다")
    void refreshRotationAndReuse() throws Exception {
        String first = refreshTokenOf(signUp(uniqueEmail()).andReturn());

        MvcResult refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String second = refreshTokenOf(refreshed);
        assertThat(second).isNotEqualTo(first);

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃하면 쿠키가 지워지고 그 Refresh Token으로 재발급할 수 없다")
    void logout() throws Exception {
        String refreshToken = refreshTokenOf(signUp(uniqueEmail()).andReturn());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshTokenCookies.NAME, refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(RefreshTokenCookies.NAME, 0));

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("Refresh Token 쿠키 없이 재발급하면 401 INVALID_REFRESH_TOKEN")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("Access Token이 없거나 위조됐으면 401 UNAUTHORIZED")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Google 로그인을 시작하면 Google 동의 화면으로 리다이렉트한다")
    void googleAuthorize() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/authorize/google"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                                org.hamcrest.Matchers.containsString(
                                        "redirect_uri=http://localhost/api/v1/auth/oauth/callback/google"))));
    }

    private org.springframework.test.web.servlet.ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, PASSWORD)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(RefreshTokenCookies.NAME, refreshToken)));
    }

    private static String signUpBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s", "nickname": "관람객"}
                """.formatted(email, password);
    }

    private static String bearer(MvcResult result) throws Exception {
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshTokenOf(MvcResult result) {
        return result.getResponse().getCookie(RefreshTokenCookies.NAME).getValue();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
