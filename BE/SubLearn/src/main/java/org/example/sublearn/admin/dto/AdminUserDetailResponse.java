package org.example.sublearn.admin.dto;

import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;

import java.time.Instant;
import java.util.List;

public record AdminUserDetailResponse(
        Long id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt,
        long activeSessionCount,
        long libraryVideoCount,
        long submittedVideoCount,
        long dictionaryEntryCount,
        long dictionaryFolderCount,
        List<AdminUserRecentVideoDto> recentVideos
) {
}
