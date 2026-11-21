package org.example.sublearn.admin;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.common.Sha256;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.PasswordResetToken;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.PasswordResetTokenRepository;
import org.example.sublearn.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String mailFrom;
    private final String frontendBaseUrl;
    private final long ttlMinutes;

    public PasswordResetService(AppUserRepository appUserRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                AdminAuditService auditService,
                                ObjectProvider<JavaMailSender> mailSenderProvider,
                                @Value("${spring.mail.host:}") String mailHost,
                                @Value("${sublearn.mail.from:no-reply@sublearn.local}") String mailFrom,
                                @Value("${sublearn.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
                                @Value("${sublearn.admin.password-reset-ttl-minutes:30}") long ttlMinutes) {
        this.appUserRepository = appUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public Instant sendResetEmailByAdmin(Long targetUserId, Long adminUserId, String ipAddress) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailHost == null || mailHost.isBlank() || mailSender == null) {
            throw ApiException.serviceUnavailable("SMTP_NOT_CONFIGURED",
                    "Chưa cấu hình máy chủ email SMTP (MAIL_HOST). Vui lòng cấu hình biến môi trường SMTP trước khi gửi.");
        }

        AppUser targetUser = appUserRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy người dùng: " + targetUserId));
        AppUser adminUser = adminUserId != null ? appUserRepository.getReferenceById(adminUserId) : null;

        Instant now = Instant.now();
        passwordResetTokenRepository.invalidateActiveTokensForUser(targetUser.getId(), now);

        String rawToken = generateRawToken();
        String tokenHash = Sha256.hash(rawToken);
        Instant expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));

        passwordResetTokenRepository.save(new PasswordResetToken(targetUser, adminUser, tokenHash, expiresAt));

        String resetUrl = frontendBaseUrl + "/reset-password?token=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(targetUser.getEmail());
        message.setSubject("[SubLearn] Đặt lại mật khẩu tài khoản");
        message.setText("Xin chào " + displayLabel(targetUser) + ",\n\n"
                + "Quản trị viên đã gửi yêu cầu đặt lại mật khẩu cho tài khoản SubLearn của bạn.\n"
                + "Vui lòng mở đường dẫn dưới đây trong vòng " + ttlMinutes + " phút (chỉ sử dụng 1 lần):\n\n"
                + resetUrl + "\n\n"
                + "Nếu bạn không yêu cầu thao tác này, vui lòng bỏ qua email này.");

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("[ADMIN] Failed to send password reset email to userId={}: {}", targetUserId, ex.getMessage());
            throw ApiException.serviceUnavailable("SMTP_SEND_FAILED",
                    "Không thể gửi email đặt lại mật khẩu. Vui lòng kiểm tra lại cấu hình SMTP.");
        }

        auditService.record(
                adminUserId,
                AdminAuditAction.SEND_PASSWORD_RESET,
                "USER",
                String.valueOf(targetUser.getId()),
                "Gửi link đặt lại mật khẩu tới " + targetUser.getEmail(),
                Map.of("targetEmail", targetUser.getEmail(), "expiresAt", expiresAt.toString()),
                ipAddress
        );
        return expiresAt;
    }

    @Transactional
    public void completePasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("INVALID_OR_EXPIRED_RESET_TOKEN", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }
        String tokenHash = Sha256.hash(rawToken.trim());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.badRequest(
                        "INVALID_OR_EXPIRED_RESET_TOKEN",
                        "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));

        Instant now = Instant.now();
        if (!resetToken.isUsable(now)) {
            throw ApiException.badRequest(
                    "INVALID_OR_EXPIRED_RESET_TOKEN",
                    "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        AppUser user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.markUsed(now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String displayLabel(AppUser user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getEmail();
    }
}
