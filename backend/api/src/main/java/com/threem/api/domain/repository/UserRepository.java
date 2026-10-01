package com.threem.api.domain.repository;

import java.util.Optional;

import com.threem.api.domain.model.AuthProvider;
import com.threem.api.domain.model.User;

public interface UserRepository {

    Optional<User> findById(Long userId);

    /**
     * @param email 소문자로 정규화된 이메일
     */
    Optional<User> findByEmail(String email);

    Optional<User> findByProvider(AuthProvider provider, String providerId);

    /**
     * @throws com.threem.api.global.error.BusinessException 이메일이 이미 있으면 {@code EMAIL_ALREADY_EXISTS}
     */
    User save(User user);
}
