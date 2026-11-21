package org.example.sublearn.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.sublearn.admin.dto.AdminJobItemDto;
import org.example.sublearn.admin.dto.AdminJobPageResponse;
import org.example.sublearn.admin.dto.BulkRetryRequest;
import org.example.sublearn.admin.dto.BulkRetryResponse;
import org.example.sublearn.admin.dto.ForceFailJobRequest;
import org.example.sublearn.admin.dto.PurgeDlqResponse;
import org.example.sublearn.admin.dto.RabbitQueueStatsResponse;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.job.ProcessingJobResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
public class AdminJobController {

    private final AdminJobService adminJobService;

    public AdminJobController(AdminJobService adminJobService) {
        this.adminJobService = adminJobService;
    }

    @GetMapping("/jobs")
    public AdminJobPageResponse listJobs(@RequestParam(required = false) JobStatus status,
                                         @RequestParam(required = false) JobType jobType,
                                         @RequestParam(required = false) FailureReason failureReason,
                                         @RequestParam(required = false) Long videoId,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return adminJobService.listJobs(status, jobType, failureReason, videoId, search, from, to, page, size);
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ProcessingJobResponse retryJob(@PathVariable Long jobId,
                                          @AuthenticationPrincipal Long adminUserId,
                                          HttpServletRequest httpRequest) {
        return adminJobService.retryJob(jobId, adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }

    @PostMapping("/jobs/bulk-retry")
    public BulkRetryResponse bulkRetry(@Valid @RequestBody BulkRetryRequest request,
                                       @AuthenticationPrincipal Long adminUserId,
                                       HttpServletRequest httpRequest) {
        return adminJobService.bulkRetry(
                request.jobIds(), adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }

    @PostMapping("/jobs/{jobId}/force-fail")
    public AdminJobItemDto forceFailJob(@PathVariable Long jobId,
                                        @RequestBody(required = false) ForceFailJobRequest request,
                                        @AuthenticationPrincipal Long adminUserId,
                                        HttpServletRequest httpRequest) {
        String reason = request != null ? request.reason() : null;
        return adminJobService.forceFailJob(
                jobId, reason, adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }

    @GetMapping("/queues")
    public RabbitQueueStatsResponse queueStats() {
        return adminJobService.getQueueStats();
    }

    @PostMapping("/queues/dlq/purge")
    public PurgeDlqResponse purgeDlq(@AuthenticationPrincipal Long adminUserId,
                                     HttpServletRequest httpRequest) {
        return adminJobService.purgeDlq(adminUserId, AdminAuditService.extractClientIp(httpRequest));
    }
}
