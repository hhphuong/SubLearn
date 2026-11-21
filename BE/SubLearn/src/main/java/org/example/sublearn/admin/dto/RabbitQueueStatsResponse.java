package org.example.sublearn.admin.dto;

public record RabbitQueueStatsResponse(
        boolean reachable,
        long videoJobs,
        long retryJobs,
        long dlqJobs,
        int bulkRetryMaxBatch
) {
}
