package com.threem.api.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.threem.api.application.usecase.UserUseCase;
import com.threem.api.domain.error.UserErrorCode;
import com.threem.api.domain.model.User;
import com.threem.api.domain.repository.UserRepository;
import com.threem.api.global.error.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserUseCase {

    private final UserRepository userRepository;

    @Override
    public User getMyInfo(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
