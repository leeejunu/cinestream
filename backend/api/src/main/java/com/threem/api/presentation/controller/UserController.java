package com.threem.api.presentation.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.threem.api.application.usecase.UserUseCase;
import com.threem.api.presentation.dto.res.MyInfoResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
class UserController {

    private final UserUseCase userUseCase;

    /**
     * Access Token의 {@code sub}가 회원 ID다.
     */
    @GetMapping("/me")
    public MyInfoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MyInfoResponse.from(userUseCase.getMyInfo(Long.valueOf(jwt.getSubject())));
    }
}
