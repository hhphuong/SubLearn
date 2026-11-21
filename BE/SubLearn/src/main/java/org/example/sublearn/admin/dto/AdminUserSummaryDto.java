package org.example.sublearn.admin.dto;

import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;

import java.time.Instant;

public record AdminUserSummaryDto(
        Long id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt,
        long activeSessionCount,
        long libraryVideoCount,
        long dictionaryEntryCount
) {
}
