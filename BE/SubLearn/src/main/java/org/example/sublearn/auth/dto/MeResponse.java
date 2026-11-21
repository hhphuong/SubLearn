package org.example.sublearn.auth.dto;

import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;

public record MeResponse(Long userId, String email, String displayName, UserRole role, UserStatus status) {
}
