package org.example.sublearn.admin;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.example.sublearn.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(AppUserRepository appUserRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${sublearn.admin.bootstrap-email:}") String bootstrapEmail,
                                @Value("${sublearn.admin.bootstrap-password:}") String bootstrapPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrapIfConfigured();
    }

    public void bootstrapIfConfigured() {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            return;
        }
        String normalizedEmail = bootstrapEmail.trim().toLowerCase(Locale.ROOT);
        var existingOpt = appUserRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existingOpt.isPresent()) {
            AppUser existing = existingOpt.get();
            boolean changed = false;
            if (existing.getRole() != UserRole.ADMIN) {
                existing.setRole(UserRole.ADMIN);
                changed = true;
            }
            if (existing.getStatus() != UserStatus.ACTIVE) {
                existing.setStatus(UserStatus.ACTIVE);
                changed = true;
            }
            if (changed) {
                appUserRepository.save(existing);
                log.info("[ADMIN BOOTSTRAP] Promoted existing user {} to ACTIVE ADMIN", normalizedEmail);
            }
            return;
        }

        if (bootstrapPassword == null || bootstrapPassword.isBlank() || bootstrapPassword.length() < 8) {
            log.warn("[ADMIN BOOTSTRAP] Skipped creating admin {} because ADMIN_BOOTSTRAP_PASSWORD is missing or shorter than 8 characters",
                    normalizedEmail);
            return;
        }
        AppUser admin = new AppUser(normalizedEmail, passwordEncoder.encode(bootstrapPassword), "Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        appUserRepository.save(admin);
        log.info("[ADMIN BOOTSTRAP] Created initial ADMIN account {}", normalizedEmail);
    }
}
