package com.threem.api.application.usecase;

import com.threem.api.domain.model.User;

public interface UserUseCase {

    User getMyInfo(Long userId);
}
