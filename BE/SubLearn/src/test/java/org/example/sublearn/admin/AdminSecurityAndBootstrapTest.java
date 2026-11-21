package org.example.sublearn.admin;

import org.example.sublearn.admin.dto.AdminDashboardOverviewResponse;
import org.example.sublearn.auth.AuthService;
import org.example.sublearn.auth.dto.RegisterRequest;
import org.example.sublearn.config.CorsConfig;
import org.example.sublearn.config.SecurityConfig;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.RefreshTokenRepository;
import org.example.sublearn.security.JwtAuthenticationFilter;
import org.example.sublearn.security.JwtService;
import org.example.sublearn.security.RestAccessDeniedHandler;
import org.example.sublearn.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import({
        SecurityConfig.class,
        CorsConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "sublearn.jwt.secret=test-only-secret-key-not-for-production-use-1234567890",
        "sublearn.jwt.access-token-ttl-minutes=15",
        "sublearn.jwt.refresh-token-ttl-days=7",
        "sublearn.cors.allowed-origin=http://localhost:5173"
})
class AdminSecurityAndBootstrapTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdminDashboardService dashboardService;

    @MockitoBean
    private AdminHealthService healthService;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void adminEndpointsRejectUnauthenticatedAndRegularUsersAndAllowAdmin() throws Exception {
        AdminDashboardOverviewResponse stubOverview = new AdminDashboardOverviewResponse(
                "7d",
                new AdminDashboardOverviewResponse.UserKpis(1, 1, 0, 1, 1, 0),
                new AdminDashboardOverviewResponse.VideoKpis(0, 0, 0, 0, 0, 0),
                new AdminDashboardOverviewResponse.JobKpis(0, 0, 0, 0, 0, 100.0, Map.of()),
                new AdminDashboardOverviewResponse.CacheAndDictionaryKpis(0, 0, 0),
                List.of(),
                List.of(),
                List.of()
        );
        given(dashboardService.getOverview("7d")).willReturn(stubOverview);

        mockMvc.perform(get("/api/admin/dashboard/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        String userToken = jwtService.generateAccessToken(10L, "user@example.com", UserRole.USER);
        mockMvc.perform(get("/api/admin/dashboard/overview")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String adminToken = jwtService.generateAccessToken(1L, "admin@example.com", UserRole.ADMIN);
        mockMvc.perform(get("/api/admin/dashboard/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("7d"));
    }

    @Test
    void bootstrapRunnerCreatesNewAdminOrPromotesExistingUser() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("StrongPass123")).thenReturn("encoded-pass");
        when(userRepo.findByEmailIgnoreCase("admin@sublearn.local")).thenReturn(Optional.empty());

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepo, encoder, "  ADMIN@sublearn.local ", "StrongPass123");
        runner.bootstrapIfConfigured();

        var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(userRepo).save(captor.capture());
        assertEquals("admin@sublearn.local", captor.getValue().getEmail());
        assertEquals(UserRole.ADMIN, captor.getValue().getRole());
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());

        AppUser existingUser = new AppUser("admin@sublearn.local", "old-hash", "Old");
        existingUser.setStatus(UserStatus.LOCKED);
        when(userRepo.findByEmailIgnoreCase("admin@sublearn.local")).thenReturn(Optional.of(existingUser));

        runner.bootstrapIfConfigured();
        assertEquals(UserRole.ADMIN, existingUser.getRole());
        assertEquals(UserStatus.ACTIVE, existingUser.getStatus());
    }

    @Test
    void publicRegisterAlwaysAssignsUserRoleAndActiveStatus() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtService localJwt = new JwtService("test-only-secret-key-not-for-production-use-1234567890", 15, 7);

        when(userRepo.existsByEmail("newuser@example.com")).thenReturn(false);
        when(encoder.encode("Password123")).thenReturn("hashed");
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 99L);
            return u;
        });

        AuthService authService = new AuthService(userRepo, refreshRepo, encoder, localJwt);
        authService.register(new RegisterRequest("newuser@example.com", "Password123", "New User"));

        var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(userRepo).save(captor.capture());
        assertEquals(UserRole.USER, captor.getValue().getRole());
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());
    }
}
