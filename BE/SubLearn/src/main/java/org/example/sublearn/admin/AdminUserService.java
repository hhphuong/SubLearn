package org.example.sublearn.admin;

import org.example.sublearn.admin.dto.AdminUserDetailResponse;
import org.example.sublearn.admin.dto.AdminUserPageResponse;
import org.example.sublearn.admin.dto.AdminUserRecentVideoDto;
import org.example.sublearn.admin.dto.AdminUserSummaryDto;
import org.example.sublearn.admin.dto.RevokeSessionsResponse;
import org.example.sublearn.admin.dto.SendPasswordResetResponse;
import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.example.sublearn.domain.Video;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.DictionaryFolderRepository;
import org.example.sublearn.repository.RefreshTokenRepository;
import org.example.sublearn.repository.UserVideoRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.repository.VideoWatchHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserVideoRepository userVideoRepository;
    private final VideoRepository videoRepository;
    private final DictionaryEntryRepository dictionaryEntryRepository;
    private final DictionaryFolderRepository dictionaryFolderRepository;
    private final VideoWatchHistoryRepository videoWatchHistoryRepository;
    private final AdminAuditService auditService;
    private final PasswordResetService passwordResetService;

    public AdminUserService(AppUserRepository appUserRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            UserVideoRepository userVideoRepository,
                            VideoRepository videoRepository,
                            DictionaryEntryRepository dictionaryEntryRepository,
                            DictionaryFolderRepository dictionaryFolderRepository,
                            VideoWatchHistoryRepository videoWatchHistoryRepository,
                            AdminAuditService auditService,
                            PasswordResetService passwordResetService) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userVideoRepository = userVideoRepository;
        this.videoRepository = videoRepository;
        this.dictionaryEntryRepository = dictionaryEntryRepository;
        this.dictionaryFolderRepository = dictionaryFolderRepository;
        this.videoWatchHistoryRepository = videoWatchHistoryRepository;
        this.auditService = auditService;
        this.passwordResetService = passwordResetService;
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(String search, UserRole role, UserStatus status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();

        Page<AppUser> result = appUserRepository.searchAdminUsers(
                cleanSearch,
                role,
                status,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Instant now = Instant.now();
        List<AdminUserSummaryDto> users = result.getContent().stream()
                .map(user -> new AdminUserSummaryDto(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRole(),
                        user.getStatus(),
                        user.getCreatedAt(),
                        user.getUpdatedAt(),
                        refreshTokenRepository.countByUser_IdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), now),
                        userVideoRepository.countByUser_Id(user.getId()),
                        dictionaryEntryRepository.countByUser_Id(user.getId())
                ))
                .toList();

        return new AdminUserPageResponse(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                users
        );
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng: " + userId));

        Instant now = Instant.now();
        long activeSessions = refreshTokenRepository.countByUser_IdAndRevokedAtIsNullAndExpiresAtAfter(userId, now);
        long libraryVideos = userVideoRepository.countByUser_Id(userId);
        long submittedVideos = videoRepository.countByRequestedByUser_Id(userId);
        long dictionaryEntries = dictionaryEntryRepository.countByUser_Id(userId);
        long dictionaryFolders = dictionaryFolderRepository.countByUser_Id(userId);

        List<AdminUserRecentVideoDto> recentVideos = videoWatchHistoryRepository
                .findTop10ByUser_IdOrderByLastWatchedAtDesc(userId)
                .stream()
                .map(h -> {
                    Video v = h.getVideo();
                    return new AdminUserRecentVideoDto(
                            v.getId(),
                            v.getYoutubeVideoId(),
                            v.getTitle(),
                            v.getSourceLanguage(),
                            h.getLastWatchedAt()
                    );
                })
                .toList();

        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                activeSessions,
                libraryVideos,
                submittedVideos,
                dictionaryEntries,
                dictionaryFolders,
                recentVideos
        );
    }

    @Transactional
    public AdminUserSummaryDto updateStatus(Long targetUserId, UserStatus newStatus,
                                            Long adminUserId, String ipAddress) {
        AppUser target = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng: " + targetUserId));

        if (newStatus == UserStatus.LOCKED) {
            if (targetUserId.equals(adminUserId)) {
                throw ApiException.badRequest("CANNOT_LOCK_SELF", "Không thể tự khóa tài khoản của chính bạn.");
            }
            if (target.getRole() == UserRole.ADMIN
                    && target.getStatus() == UserStatus.ACTIVE
                    && appUserRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
                throw ApiException.conflict("CANNOT_LOCK_LAST_ADMIN",
                        "Không thể khóa tài khoản ADMIN đang hoạt động cuối cùng của hệ thống.");
            }
        }

        UserStatus previousStatus = target.getStatus();
        int revokedCount = 0;
        if (previousStatus != newStatus) {
            target.setStatus(newStatus);
            appUserRepository.save(target);
            if (newStatus == UserStatus.LOCKED) {
                revokedCount = refreshTokenRepository.revokeAllActiveByUserId(targetUserId, Instant.now());
            }
            auditService.record(
                    adminUserId,
                    newStatus == UserStatus.LOCKED ? AdminAuditAction.LOCK_USER : AdminAuditAction.UNLOCK_USER,
                    "USER",
                    String.valueOf(targetUserId),
                    (newStatus == UserStatus.LOCKED ? "Khóa" : "Mở khóa") + " tài khoản " + target.getEmail(),
                    Map.of(
                            "email", target.getEmail(),
                            "previousStatus", previousStatus.name(),
                            "newStatus", newStatus.name(),
                            "revokedSessions", revokedCount
                    ),
                    ipAddress
            );
        }

        return toSummary(target);
    }

    @Transactional
    public AdminUserSummaryDto updateRole(Long targetUserId, UserRole newRole,
                                          Long adminUserId, String ipAddress) {
        AppUser target = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng: " + targetUserId));

        UserRole previousRole = target.getRole();
        if (previousRole == UserRole.ADMIN && newRole == UserRole.USER) {
            if (targetUserId.equals(adminUserId)) {
                throw ApiException.badRequest("CANNOT_DEMOTE_SELF", "Không thể tự hạ quyền tài khoản của chính bạn.");
            }
            boolean isLastAdmin = appUserRepository.countByRole(UserRole.ADMIN) <= 1
                    || (target.getStatus() == UserStatus.ACTIVE
                    && appUserRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1);
            if (isLastAdmin) {
                throw ApiException.conflict("CANNOT_DEMOTE_LAST_ADMIN",
                        "Không thể hạ quyền tài khoản ADMIN cuối cùng của hệ thống.");
            }
        }

        int revokedCount = 0;
        if (previousRole != newRole) {
            target.setRole(newRole);
            appUserRepository.save(target);
            if (newRole == UserRole.USER) {
                revokedCount = refreshTokenRepository.revokeAllActiveByUserId(targetUserId, Instant.now());
            }
            auditService.record(
                    adminUserId,
                    AdminAuditAction.CHANGE_USER_ROLE,
                    "USER",
                    String.valueOf(targetUserId),
                    "Đổi vai trò tài khoản " + target.getEmail() + " từ " + previousRole + " thành " + newRole,
                    Map.of(
                            "email", target.getEmail(),
                            "previousRole", previousRole.name(),
                            "newRole", newRole.name(),
                            "revokedSessions", revokedCount
                    ),
                    ipAddress
            );
        }

        return toSummary(target);
    }

    @Transactional
    public RevokeSessionsResponse revokeSessions(Long targetUserId, Long adminUserId, String ipAddress) {
        AppUser target = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng: " + targetUserId));

        int revokedCount = refreshTokenRepository.revokeAllActiveByUserId(targetUserId, Instant.now());
        auditService.record(
                adminUserId,
                AdminAuditAction.REVOKE_USER_SESSIONS,
                "USER",
                String.valueOf(targetUserId),
                "Thu hồi " + revokedCount + " phiên đăng nhập của " + target.getEmail(),
                Map.of("email", target.getEmail(), "revokedCount", revokedCount),
                ipAddress
        );
        return new RevokeSessionsResponse(targetUserId, revokedCount);
    }

    @Transactional
    public SendPasswordResetResponse sendPasswordReset(Long targetUserId, Long adminUserId, String ipAddress) {
        AppUser target = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng: " + targetUserId));
        Instant expiresAt = passwordResetService.sendResetEmailByAdmin(targetUserId, adminUserId, ipAddress);
        return new SendPasswordResetResponse(target.getId(), target.getEmail(), expiresAt);
    }

    private AdminUserSummaryDto toSummary(AppUser user) {
        Instant now = Instant.now();
        return new AdminUserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                refreshTokenRepository.countByUser_IdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), now),
                userVideoRepository.countByUser_Id(user.getId()),
                dictionaryEntryRepository.countByUser_Id(user.getId())
        );
    }
}
