package com.threem.api.infrastructure.persistence.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.threem.api.TestcontainersConfiguration;
import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.UserRepository;
import com.threem.api.global.error.BusinessException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("중복 검사를 거치지 않고 같은 이메일을 저장하면 DB 제약에 걸려 EMAIL_ALREADY_EXISTS")
    void duplicateEmail() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.signUp(email, "hashed", "관람객", NOW));

        assertThatThrownBy(() -> userRepository.save(
                User.signUpWithOAuth(AuthProvider.GOOGLE, UUID.randomUUID().toString(), email, "관람객", NOW)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
