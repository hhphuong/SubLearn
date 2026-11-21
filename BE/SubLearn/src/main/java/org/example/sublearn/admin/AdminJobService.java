package org.example.sublearn.admin;

import org.example.sublearn.admin.dto.AdminJobItemDto;
import org.example.sublearn.admin.dto.AdminJobPageResponse;
import org.example.sublearn.admin.dto.BulkRetryResponse;
import org.example.sublearn.admin.dto.PurgeDlqResponse;
import org.example.sublearn.admin.dto.RabbitQueueStatsResponse;
import org.example.sublearn.common.ApiException;
import org.example.sublearn.domain.AdminAuditAction;
import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.ProcessingJob;
import org.example.sublearn.domain.Video;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.job.ProcessingJobResponse;
import org.example.sublearn.job.ProcessingJobService;
import org.example.sublearn.job.RabbitMqConfig;
import org.example.sublearn.repository.ProcessingJobRepository;
import org.example.sublearn.repository.VideoRepository;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final VideoRepository videoRepository;
    private final ProcessingJobService processingJobService;
    private final AdminHealthService adminHealthService;
    private final AdminAuditService auditService;
    private final ObjectProvider<AmqpAdmin> amqpAdminProvider;
    private final int bulkRetryMaxBatch;

    public AdminJobService(ProcessingJobRepository processingJobRepository,
                           VideoRepository videoRepository,
                           ProcessingJobService processingJobService,
                           AdminHealthService adminHealthService,
                           AdminAuditService auditService,
                           ObjectProvider<AmqpAdmin> amqpAdminProvider,
                           @Value("${sublearn.admin.bulk-retry-max-batch:20}") int bulkRetryMaxBatch) {
        this.processingJobRepository = processingJobRepository;
        this.videoRepository = videoRepository;
        this.processingJobService = processingJobService;
        this.adminHealthService = adminHealthService;
        this.auditService = auditService;
        this.amqpAdminProvider = amqpAdminProvider;
        this.bulkRetryMaxBatch = bulkRetryMaxBatch;
    }

    @Transactional(readOnly = true)
    public AdminJobPageResponse listJobs(JobStatus status, JobType jobType, FailureReason failureReason,
                                         Long videoId, String search, Instant from, Instant to,
                                         int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();

        Page<ProcessingJob> result = processingJobRepository.searchAdminJobs(
                status,
                jobType,
                failureReason,
                videoId,
                cleanSearch,
                from,
                to,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<AdminJobItemDto> items = result.getContent().stream()
                .map(this::toItemDto)
                .toList();

        return new AdminJobPageResponse(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                items
        );
    }

    @Transactional
    public ProcessingJobResponse retryJob(Long jobId, Long adminUserId, String ipAddress) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Không tìm thấy job: " + jobId));
        if (job.getStatus() != JobStatus.FAILED) {
            throw ApiException.conflict("JOB_NOT_RETRYABLE",
                    "Chỉ có thể thử lại các job đang ở trạng thái FAILED.");
        }
        ProcessingJobResponse created = processingJobService.retryJob(jobId, adminUserId);
        auditService.record(
                adminUserId,
                AdminAuditAction.RETRY_JOB,
                "JOB",
                String.valueOf(jobId),
                "Thử lại job #" + jobId + " (tạo job mới #" + created.jobId() + ")",
                Map.of(
                        "originalJobId", jobId,
                        "newJobId", created.jobId(),
                        "videoId", job.getVideo().getId(),
                        "jobType", job.getJobType().name()
                ),
                ipAddress
        );
        return created;
    }

    @Transactional
    public BulkRetryResponse bulkRetry(List<Long> jobIds, Long adminUserId, String ipAddress) {
        if (jobIds == null || jobIds.isEmpty()) {
            throw ApiException.badRequest("EMPTY_JOB_LIST", "Vui lòng chọn ít nhất một job để thử lại.");
        }
        List<Long> distinctIds = jobIds.stream().distinct().toList();
        if (distinctIds.size() > bulkRetryMaxBatch) {
            throw ApiException.badRequest("BULK_RETRY_LIMIT_EXCEEDED",
                    "Mỗi lượt chỉ được thử lại tối đa " + bulkRetryMaxBatch + " job.");
        }

        List<Long> skippedIds = new ArrayList<>();
        List<Long> createdJobIds = new ArrayList<>();

        for (Long id : distinctIds) {
            ProcessingJob job = processingJobRepository.findById(id).orElse(null);
            if (job == null || job.getStatus() != JobStatus.FAILED) {
                skippedIds.add(id);
                continue;
            }
            ProcessingJobResponse created = processingJobService.retryJob(id, adminUserId);
            createdJobIds.add(created.jobId());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requestedJobIds", distinctIds);
        details.put("createdJobIds", createdJobIds);
        details.put("skippedIds", skippedIds);

        auditService.record(
                adminUserId,
                AdminAuditAction.BULK_RETRY_JOBS,
                "JOB",
                null,
                "Thử lại hàng loạt " + createdJobIds.size() + "/" + distinctIds.size() + " job",
                details,
                ipAddress
        );

        return new BulkRetryResponse(distinctIds.size(), createdJobIds.size(), skippedIds, createdJobIds);
    }

    @Transactional
    public AdminJobItemDto forceFailJob(Long jobId, String reasonMessage, Long adminUserId, String ipAddress) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Không tìm thấy job: " + jobId));
        if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.PROCESSING) {
            throw ApiException.conflict("JOB_NOT_ACTIVE",
                    "Chỉ có thể hủy các job đang ở trạng thái PENDING hoặc PROCESSING.");
        }

        String message = (reasonMessage != null && !reasonMessage.isBlank())
                ? reasonMessage.trim()
                : "Đã hủy thủ công bởi quản trị viên";
        job.markFailed(FailureReason.UNKNOWN_ERROR, message);
        processingJobRepository.save(job);

        Video video = job.getVideo();
        if (job.getJobType() == JobType.INGEST_VIDEO
                && (video.getStatus() == VideoStatus.PENDING || video.getStatus() == VideoStatus.PROCESSING)) {
            video.markFailed(FailureReason.UNKNOWN_ERROR, message);
            videoRepository.save(video);
        }

        auditService.record(
                adminUserId,
                AdminAuditAction.FORCE_FAIL_JOB,
                "JOB",
                String.valueOf(jobId),
                "Đánh dấu FAILED thủ công cho job #" + jobId,
                Map.of("jobId", jobId, "videoId", video.getId(), "reason", message),
                ipAddress
        );

        return toItemDto(job);
    }

    public RabbitQueueStatsResponse getQueueStats() {
        return adminHealthService.getQueueStats(bulkRetryMaxBatch);
    }

    @Transactional
    public PurgeDlqResponse purgeDlq(Long adminUserId, String ipAddress) {
        AmqpAdmin amqpAdmin = amqpAdminProvider.getIfAvailable();
        if (amqpAdmin == null) {
            throw ApiException.serviceUnavailable("RABBITMQ_UNAVAILABLE", "Không thể kết nối tới RabbitMQ.");
        }
        int purgedCount;
        try {
            purgedCount = amqpAdmin.purgeQueue(RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ);
        } catch (Exception ex) {
            throw ApiException.serviceUnavailable("RABBITMQ_UNAVAILABLE",
                    "Không thể dọn Dead Letter Queue trên RabbitMQ: " + ex.getMessage());
        }

        auditService.record(
                adminUserId,
                AdminAuditAction.PURGE_DLQ,
                "QUEUE",
                RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ,
                "Dọn sạch " + purgedCount + " message trong DLQ",
                Map.of("queue", RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ, "purgedCount", purgedCount),
                ipAddress
        );
        return new PurgeDlqResponse(RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ, purgedCount);
    }

    private AdminJobItemDto toItemDto(ProcessingJob job) {
        Video v = job.getVideo();
        AppUser u = job.getRequestedByUser();
        return new AdminJobItemDto(
                job.getId(),
                v.getId(),
                v.getYoutubeVideoId(),
                v.getTitle(),
                u != null ? u.getId() : null,
                u != null ? u.getEmail() : null,
                job.getJobType(),
                job.getStatus(),
                job.getSourceLanguage(),
                job.getTargetLanguage(),
                job.getRetryCount(),
                job.getMaxRetries(),
                job.getProgressPercent(),
                job.getFailureReason(),
                job.getFailureMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
