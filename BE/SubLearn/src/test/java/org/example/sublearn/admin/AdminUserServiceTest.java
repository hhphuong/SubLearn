package org.example.sublearn.admin;

import org.example.sublearn.auth.AuthService;
import org.example.sublearn.auth.dto.LoginRequest;
import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.RefreshToken;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.DictionaryFolderRepository;
import org.example.sublearn.repository.RefreshTokenRepository;
import org.example.sublearn.repository.UserVideoRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.repository.VideoWatchHistoryRepository;
import org.example.sublearn.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private AppUserRepository userRepo;
    private RefreshTokenRepository refreshRepo;
    private AdminAuditService auditService;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        userRepo = mock(AppUserRepository.class);
        refreshRepo = mock(RefreshTokenRepository.class);
        UserVideoRepository userVideoRepo = mock(UserVideoRepository.class);
        VideoRepository videoRepo = mock(VideoRepository.class);
        DictionaryEntryRepository dictRepo = mock(DictionaryEntryRepository.class);
        DictionaryFolderRepository folderRepo = mock(DictionaryFolderRepository.class);
        VideoWatchHistoryRepository historyRepo = mock(VideoWatchHistoryRepository.class);
        auditService = mock(AdminAuditService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);

        adminUserService = new AdminUserService(
                userRepo, refreshRepo, userVideoRepo, videoRepo,
                dictRepo, folderRepo, historyRepo, auditService, passwordResetService
        );
    }

    @Test
    void lockingUserRevokesSessionsWritesAuditAndBlocksAuth() {
        AppUser target = new AppUser("learner@example.com", "hashed-pw", "Learner");
        ReflectionTestUtils.setField(target, "id", 20L);
        when(userRepo.findById(20L)).thenReturn(Optional.of(target));
        when(refreshRepo.revokeAllActiveByUserId(eq(20L), any(Instant.class))).thenReturn(3);

        var summary = adminUserService.updateStatus(20L, UserStatus.LOCKED, 1L, "127.0.0.1");

        assertEquals(UserStatus.LOCKED, summary.status());
        assertEquals(UserStatus.LOCKED, target.getStatus());
        verify(refreshRepo).revokeAllActiveByUserId(eq(20L), any(Instant.class));
        verify(auditService).record(
                eq(1L), eq(AdminAuditAction.LOCK_USER), eq("USER"), eq("20"),
                any(), any(), eq("127.0.0.1"));

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("secret123", "hashed-pw")).thenReturn(true);
        when(userRepo.findByEmail("learner@example.com")).thenReturn(Optional.of(target));
        RefreshToken activeToken = new RefreshToken(target, "hash", Instant.now().plusSeconds(3600));
        when(refreshRepo.findByTokenHash(any())).thenReturn(Optional.of(activeToken));

        JwtService jwtService = new JwtService("test-only-secret-key-not-for-production-use-1234567890", 15, 7);
        AuthService authService = new AuthService(userRepo, refreshRepo, encoder, jwtService);

        ApiException loginEx = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("learner@example.com", "secret123")));
        assertEquals(HttpStatus.FORBIDDEN, loginEx.getStatus());
        assertEquals("ACCOUNT_LOCKED", loginEx.getCode());

        ApiException refreshEx = assertThrows(ApiException.class,
                () -> authService.refresh("raw-refresh-token"));
        assertEquals(HttpStatus.FORBIDDEN, refreshEx.getStatus());
        assertEquals("ACCOUNT_LOCKED", refreshEx.getCode());
    }

    @Test
    void preventsSelfLockSelfDemoteAndLastAdminLockOrDemote() {
        AppUser admin = new AppUser("admin@example.com", "hash", "Admin");
        admin.setRole(UserRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(admin));

        ApiException selfLockEx = assertThrows(ApiException.class,
                () -> adminUserService.updateStatus(1L, UserStatus.LOCKED, 1L, null));
        assertEquals("CANNOT_LOCK_SELF", selfLockEx.getCode());

        ApiException selfDemoteEx = assertThrows(ApiException.class,
                () -> adminUserService.updateRole(1L, UserRole.USER, 1L, null));
        assertEquals("CANNOT_DEMOTE_SELF", selfDemoteEx.getCode());

        when(userRepo.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);
        when(userRepo.countByRole(UserRole.ADMIN)).thenReturn(1L);

        ApiException lastAdminLockEx = assertThrows(ApiException.class,
                () -> adminUserService.updateStatus(1L, UserStatus.LOCKED, 99L, null));
        assertEquals("CANNOT_LOCK_LAST_ADMIN", lastAdminLockEx.getCode());

        ApiException lastAdminDemoteEx = assertThrows(ApiException.class,
                () -> adminUserService.updateRole(1L, UserRole.USER, 99L, null));
        assertEquals("CANNOT_DEMOTE_LAST_ADMIN", lastAdminDemoteEx.getCode());
    }
}
