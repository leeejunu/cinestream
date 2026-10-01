package com.threem.api.presentation.dto.req;

import com.threem.api.application.dto.LoginCommand;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
