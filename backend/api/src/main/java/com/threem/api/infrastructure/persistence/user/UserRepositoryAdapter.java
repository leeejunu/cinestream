package com.threem.api.infrastructure.persistence.user;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
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

    /** V1__create_user_tables.sql의 이메일 유니크 제약 이름 */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

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
     * 같은 이메일로 동시에 가입하면 서비스의 중복 검사를 둘 다 통과하고 DB 유니크 제약에 걸린다.
     * 이메일 제약에 걸린 경우만 이메일 중복으로 바꾸고, 다른 제약 위반은 그대로 던진다.
     */
    @Override
    public User save(User user) {
        try {
            return userJpaRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (isEmailDuplicate(e)) {
                throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    private static boolean isEmailDuplicate(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
    }
}
