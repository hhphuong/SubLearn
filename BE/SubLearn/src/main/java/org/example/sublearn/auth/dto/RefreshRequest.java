package org.example.sublearn.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
