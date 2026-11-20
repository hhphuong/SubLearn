package org.example.sublearn.job;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class ProcessingJobController {

    private final ProcessingJobService processingJobService;

    public ProcessingJobController(ProcessingJobService processingJobService) {
        this.processingJobService = processingJobService;
    }

    @GetMapping("/{jobId}")
    public ProcessingJobResponse getJob(@PathVariable Long jobId) {
        return processingJobService.getJob(jobId);
    }

    @PostMapping("/{jobId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProcessingJobResponse retryJob(@PathVariable Long jobId, @AuthenticationPrincipal Long userId) {
        return processingJobService.retryJob(jobId, userId);
    }
}
