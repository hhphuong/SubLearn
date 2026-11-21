package org.example.sublearn.admin.dto;

import java.time.Instant;

public record SendPasswordResetResponse(Long userId, String email, Instant expiresAt) {
}
