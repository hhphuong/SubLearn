package org.example.sublearn.admin.dto;

import jakarta.validation.constraints.NotNull;
import org.example.sublearn.domain.UserRole;

public record UpdateUserRoleRequest(@NotNull UserRole role) {
}
