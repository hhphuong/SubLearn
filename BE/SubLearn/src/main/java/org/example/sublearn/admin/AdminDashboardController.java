package org.example.sublearn.admin;

import org.example.sublearn.admin.dto.AdminDashboardOverviewResponse;
import org.example.sublearn.admin.dto.AdminSystemHealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final AdminHealthService healthService;

    public AdminDashboardController(AdminDashboardService dashboardService,
                                    AdminHealthService healthService) {
        this.dashboardService = dashboardService;
        this.healthService = healthService;
    }

    @GetMapping("/overview")
    public AdminDashboardOverviewResponse overview(@RequestParam(defaultValue = "7d") String range) {
        return dashboardService.getOverview(range);
    }

    @GetMapping("/health")
    public AdminSystemHealthResponse health() {
        return healthService.getSystemHealth();
    }
}
