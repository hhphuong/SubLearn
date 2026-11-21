package org.example.sublearn.admin;

import org.example.sublearn.common.ApiException;
import org.example.sublearn.common.Sha256;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.PasswordResetToken;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.PasswordResetTokenRepository;
import org.example.sublearn.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void sendResetEmailCreatesHashedTokenSendsMailAndAuditsWithoutLeakingRawToken() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
        RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);

        AppUser target = new AppUser("user@example.com", "old-hash", "Learner");
        ReflectionTestUtils.setField(target, "id", 5L);
        AppUser admin = new AppUser("admin@example.com", "admin-hash", "Admin");
        ReflectionTestUtils.setField(admin, "id", 1L);

        when(userRepo.findById(5L)).thenReturn(Optional.of(target));
        when(userRepo.getReferenceById(1L)).thenReturn(admin);

        PasswordResetService service = new PasswordResetService(
                userRepo, tokenRepo, refreshRepo, encoder, auditService,
                providerOf(mailSender), "smtp.example.com", "no-reply@sublearn.local",
                "http://localhost:5173", 30);

        Instant expiresAt = service.sendResetEmailByAdmin(5L, 1L, "127.0.0.1");
        assertNotNull(expiresAt);

        verify(tokenRepo).invalidateActiveTokensForUser(eq(5L), any(Instant.class));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo).save(tokenCaptor.capture());

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        String mailText = mailCaptor.getValue().getText();
        assertNotNull(mailText);
        assertTrue(mailText.contains("http://localhost:5173/reset-password?token="));

        String rawToken = mailText.split("token=")[1].split("\\s")[0].trim();
        assertEquals(Sha256.hash(rawToken), tokenCaptor.getValue().getTokenHash());

        verify(auditService).record(
                eq(1L), eq(AdminAuditAction.SEND_PASSWORD_RESET), eq("USER"), eq("5"),
                any(), any(), eq("127.0.0.1"));
    }

    @Test
    void completePasswordResetUpdatesPasswordMarksTokenUsedAndRevokesRefreshTokens() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
        RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AdminAuditService auditService = mock(AdminAuditService.class);

        AppUser user = new AppUser("user@example.com", "old-hash", "Learner");
        ReflectionTestUtils.setField(user, "id", 5L);

        String rawToken = "my-secret-reset-token";
        PasswordResetToken tokenEntity = new PasswordResetToken(
                user, null, Sha256.hash(rawToken), Instant.now().plusSeconds(1800));
        when(tokenRepo.findByTokenHash(Sha256.hash(rawToken))).thenReturn(Optional.of(tokenEntity));
        when(encoder.encode("NewStrongPass123")).thenReturn("new-encoded-hash");

        PasswordResetService service = new PasswordResetService(
                userRepo, tokenRepo, refreshRepo, encoder, auditService,
                providerOf(null), "", "no-reply@sublearn.local", "http://localhost:5173", 30);

        service.completePasswordReset(rawToken, "NewStrongPass123");

        assertEquals("new-encoded-hash", user.getPasswordHash());
        assertNotNull(tokenEntity.getUsedAt());
        verify(refreshRepo).revokeAllActiveByUserId(eq(5L), any(Instant.class));

        ApiException replayEx = assertThrows(ApiException.class,
                () -> service.completePasswordReset(rawToken, "AnotherPass123"));
        assertEquals("INVALID_OR_EXPIRED_RESET_TOKEN", replayEx.getCode());
    }

    @Test
    void throwsServiceUnavailableWhenSmtpIsNotConfigured() {
        PasswordResetService service = new PasswordResetService(
                mock(AppUserRepository.class),
                mock(PasswordResetTokenRepository.class),
                mock(RefreshTokenRepository.class),
                mock(PasswordEncoder.class),
                mock(AdminAuditService.class),
                providerOf(null),
                "",
                "no-reply@sublearn.local",
                "http://localhost:5173",
                30);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.sendResetEmailByAdmin(5L, 1L, "127.0.0.1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("SMTP_NOT_CONFIGURED", ex.getCode());
    }
}
