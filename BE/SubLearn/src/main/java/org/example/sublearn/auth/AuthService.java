package org.example.sublearn.auth;

import org.example.sublearn.auth.dto.AuthResponse;
import org.example.sublearn.auth.dto.LoginRequest;
import org.example.sublearn.auth.dto.MeResponse;
import org.example.sublearn.auth.dto.RegisterRequest;
import org.example.sublearn.auth.dto.RegisterResponse;
import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.RefreshToken;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.RefreshTokenRepository;
import org.example.sublearn.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        AppUser user = new AppUser(request.email(), passwordEncoder.encode(request.password()), request.displayName());
        appUserRepository.save(user);
        return new RegisterResponse(user.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired"));
        if (!existing.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
        }
        existing.revoke();
        return issueTokens(existing.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private AuthResponse issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();
        String refreshTokenHash = jwtService.hashToken(rawRefreshToken);
        Instant expiresAt = Instant.now().plus(jwtService.getRefreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, refreshTokenHash, expiresAt));
        return new AuthResponse(accessToken, rawRefreshToken, jwtService.getAccessTokenTtl().toSeconds());
    }
}
