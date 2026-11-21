package org.example.sublearn.admin.dto;

import jakarta.validation.constraints.NotNull;
import org.example.sublearn.domain.UserStatus;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
