package org.example.sublearn.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {
}
