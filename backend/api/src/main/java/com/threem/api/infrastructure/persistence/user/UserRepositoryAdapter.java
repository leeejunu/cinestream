package com.threem.api.infrastructure.persistence.user;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.AuthProvider;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.UserRepository;
import com.threem.api.global.error.BusinessException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByProvider(AuthProvider provider, String providerId) {
        return userJpaRepository.findByProviderAndProviderId(provider, providerId);
    }

    /**
     * 동시에 같은 이메일로 가입하면 조회 검사를 통과해도 유니크 제약에 걸린다. 바로 flush해 여기서 잡는다.
     */
    @Override
    public User save(User user) {
        try {
            return userJpaRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }
}
