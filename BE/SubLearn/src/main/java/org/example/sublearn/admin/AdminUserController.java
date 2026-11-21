package org.example.sublearn.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.sublearn.admin.dto.AdminUserDetailResponse;
import org.example.sublearn.admin.dto.AdminUserPageResponse;
import org.example.sublearn.admin.dto.AdminUserSummaryDto;
import org.example.sublearn.admin.dto.RevokeSessionsResponse;
import org.example.sublearn.admin.dto.SendPasswordResetResponse;
import org.example.sublearn.admin.dto.UpdateUserRoleRequest;
import org.example.sublearn.admin.dto.UpdateUserStatusRequest;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserPageResponse list(@RequestParam(required = false) String search,
                                      @RequestParam(required = false) UserRole role,
                                      @RequestParam(required = false) UserStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return adminUserService.listUsers(search, role, status, page, size);
    }

    @GetMapping("/{userId}")
    public AdminUserDetailResponse detail(@PathVariable Long userId) {
        return adminUserService.getUserDetail(userId);
    }

    @PatchMapping("/{userId}/status")
    public AdminUserSummaryDto updateStatus(@PathVariable Long userId,
                                            @Valid @RequestBody UpdateUserStatusRequest request,
                                            @AuthenticationPrincipal Long adminUserId,
                                            HttpServletRequest httpRequest) {
        return adminUserService.updateStatus(
                userId, request.status(), adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }

    @PatchMapping("/{userId}/role")
    public AdminUserSummaryDto updateRole(@PathVariable Long userId,
                                          @Valid @RequestBody UpdateUserRoleRequest request,
                                          @AuthenticationPrincipal Long adminUserId,
                                          HttpServletRequest httpRequest) {
        return adminUserService.updateRole(
                userId, request.role(), adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }

    @PostMapping("/{userId}/revoke-sessions")
    public RevokeSessionsResponse revokeSessions(@PathVariable Long userId,
                                                 @AuthenticationPrincipal Long adminUserId,
                                                 HttpServletRequest httpRequest) {
        return adminUserService.revokeSessions(
                userId, adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }

    @PostMapping("/{userId}/send-password-reset")
    public SendPasswordResetResponse sendPasswordReset(@PathVariable Long userId,
                                                       @AuthenticationPrincipal Long adminUserId,
                                                       HttpServletRequest httpRequest) {
        return adminUserService.sendPasswordReset(
                userId, adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }
}
