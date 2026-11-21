package org.example.sublearn.admin.dto;

public record AdminSystemHealthResponse(
        DependencyStatus mysql,
        RabbitMqHealth rabbitmq,
        YtDlpHealth ytDlp,
        GeminiHealth gemini
) {
    public record DependencyStatus(String status, String message) {
    }

    public record RabbitMqHealth(
            String status,
            String message,
            long videoJobs,
            long retryJobs,
            long dlqJobs
    ) {
    }

    public record YtDlpHealth(
            String status,
            String version,
            boolean cookiesConfigured,
            String message
    ) {
    }

    public record GeminiHealth(
            String status,
            boolean apiKeyConfigured,
            String model,
            long translationFailuresLast24h,
            String message
    ) {
    }
}
