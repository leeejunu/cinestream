package com.threem.api.presentation.dto.req;

import com.threem.api.application.dto.SignUpCommand;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(min = 2, max = 20) String nickname
) {

    public SignUpCommand toCommand() {
        return new SignUpCommand(email, password, nickname);
    }

    @Override
    public String toString() {
        return "SignUpRequest[email=" + email + ", nickname=" + nickname + "]";
    }
}
