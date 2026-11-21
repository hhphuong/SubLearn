package org.example.sublearn.admin;

import org.example.sublearn.admin.dto.AdminSystemHealthResponse;
import org.example.sublearn.admin.dto.RabbitQueueStatsResponse;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.job.RabbitMqConfig;
import org.example.sublearn.repository.ProcessingJobRepository;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Service
public class AdminHealthService {

    private final DataSource dataSource;
    private final ObjectProvider<AmqpAdmin> amqpAdminProvider;
    private final ProcessingJobRepository processingJobRepository;
    private final String ytDlpPath;
    private final String cookiesPath;
    private final String geminiApiKey;
    private final String geminiModel;

    public AdminHealthService(DataSource dataSource,
                              ObjectProvider<AmqpAdmin> amqpAdminProvider,
                              ProcessingJobRepository processingJobRepository,
                              @Value("${sublearn.ytdlp.path:yt-dlp}") String ytDlpPath,
                              @Value("${sublearn.ytdlp.cookies-path:}") String cookiesPath,
                              @Value("${sublearn.gemini.api-key:}") String geminiApiKey,
                              @Value("${sublearn.gemini.model:gemini-flash-latest}") String geminiModel) {
        this.dataSource = dataSource;
        this.amqpAdminProvider = amqpAdminProvider;
        this.processingJobRepository = processingJobRepository;
        this.ytDlpPath = ytDlpPath;
        this.cookiesPath = cookiesPath;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
    }

    public AdminSystemHealthResponse getSystemHealth() {
        AdminSystemHealthResponse.DependencyStatus mysql = checkMysql();
        RabbitQueueStatsResponse queueStats = getQueueStats(0);
        AdminSystemHealthResponse.RabbitMqHealth rabbitmq = new AdminSystemHealthResponse.RabbitMqHealth(
                queueStats.reachable() ? "UP" : "DOWN",
                queueStats.reachable() ? "Kết nối RabbitMQ ổn định" : "Không thể kết nối hoặc đọc queue RabbitMQ",
                queueStats.videoJobs(),
                queueStats.retryJobs(),
                queueStats.dlqJobs()
        );
        AdminSystemHealthResponse.YtDlpHealth ytDlp = checkYtDlp();
        AdminSystemHealthResponse.GeminiHealth gemini = checkGemini();
        return new AdminSystemHealthResponse(mysql, rabbitmq, ytDlp, gemini);
    }

    public RabbitQueueStatsResponse getQueueStats(int bulkRetryMaxBatch) {
        AmqpAdmin amqpAdmin = amqpAdminProvider.getIfAvailable();
        if (amqpAdmin == null) {
            return new RabbitQueueStatsResponse(false, 0, 0, 0, bulkRetryMaxBatch);
        }
        try {
            Long videoJobs = readQueueCount(amqpAdmin, RabbitMqConfig.QUEUE_VIDEO_JOBS);
            Long retryJobs = readQueueCount(amqpAdmin, RabbitMqConfig.QUEUE_VIDEO_JOBS_RETRY);
            Long dlqJobs = readQueueCount(amqpAdmin, RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ);
            boolean reachable = videoJobs != null && retryJobs != null && dlqJobs != null;
            return new RabbitQueueStatsResponse(
                    reachable,
                    videoJobs != null ? videoJobs : 0,
                    retryJobs != null ? retryJobs : 0,
                    dlqJobs != null ? dlqJobs : 0,
                    bulkRetryMaxBatch
            );
        } catch (Exception ex) {
            return new RabbitQueueStatsResponse(false, 0, 0, 0, bulkRetryMaxBatch);
        }
    }

    private Long readQueueCount(AmqpAdmin amqpAdmin, String queueName) {
        Properties props = amqpAdmin.getQueueProperties(queueName);
        if (props == null) {
            return null;
        }
        Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        if (count instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private AdminSystemHealthResponse.DependencyStatus checkMysql() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new AdminSystemHealthResponse.DependencyStatus("UP", "Kết nối Database hoạt động bình thường");
            }
            return new AdminSystemHealthResponse.DependencyStatus("DOWN", "Truy vấn kiểm tra Database không trả kết quả");
        } catch (Exception ex) {
            return new AdminSystemHealthResponse.DependencyStatus("DOWN", "Lỗi kết nối Database: " + ex.getMessage());
        }
    }

    private AdminSystemHealthResponse.YtDlpHealth checkYtDlp() {
        boolean cookiesConfigured = isCookiesConfigured();
        Process process = null;
        try {
            process = new ProcessBuilder(ytDlpPath, "--version").start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new AdminSystemHealthResponse.YtDlpHealth(
                        "DOWN", null, cookiesConfigured, "yt-dlp --version vượt quá thời gian chờ");
            }
            String version;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                version = reader.readLine();
            }
            if (process.exitValue() == 0 && version != null && !version.isBlank()) {
                return new AdminSystemHealthResponse.YtDlpHealth(
                        "UP",
                        version.trim(),
                        cookiesConfigured,
                        cookiesConfigured ? "Sẵn sàng (đã nạp cookies)" : "Sẵn sàng (chạy không cookies)"
                );
            }
            return new AdminSystemHealthResponse.YtDlpHealth(
                    "DOWN", null, cookiesConfigured, "yt-dlp thoát với mã " + process.exitValue());
        } catch (Exception ex) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new AdminSystemHealthResponse.YtDlpHealth(
                    "DOWN", null, cookiesConfigured, "Không tìm thấy hoặc không thể chạy yt-dlp");
        }
    }

    private boolean isCookiesConfigured() {
        if (cookiesPath == null || cookiesPath.isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(cookiesPath);
            return Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private AdminSystemHealthResponse.GeminiHealth checkGemini() {
        boolean apiKeyConfigured = geminiApiKey != null && !geminiApiKey.isBlank();
        long failures24h = processingJobRepository.countByFailureReasonAndStatusAndCreatedAtAfter(
                FailureReason.TRANSLATION_FAILED,
                JobStatus.FAILED,
                Instant.now().minus(Duration.ofHours(24))
        );
        if (!apiKeyConfigured) {
            return new AdminSystemHealthResponse.GeminiHealth(
                    "DOWN", false, geminiModel, failures24h, "Chưa cấu hình GEMINI_API_KEY");
        }
        if (failures24h > 0) {
            return new AdminSystemHealthResponse.GeminiHealth(
                    "DEGRADED", true, geminiModel, failures24h,
                    "Đã cấu hình; có " + failures24h + " job lỗi dịch thuật trong 24h qua");
        }
        return new AdminSystemHealthResponse.GeminiHealth(
                "UP", true, geminiModel, 0, "Đã cấu hình GEMINI_API_KEY và sẵn sàng");
    }
}
