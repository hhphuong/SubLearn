package org.example.sublearn.admin;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.domain.WordMeaningStatus;
import org.example.sublearn.job.RabbitMqConfig;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.ProcessingJobRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.repository.VideoWatchHistoryRepository;
import org.example.sublearn.repository.WordMeaningCacheRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {

    @Test
    void buildsDashboardOverviewWithDailySeriesAndFailureDistribution() {
        AppUserRepository userRepo = mock(AppUserRepository.class);
        VideoRepository videoRepo = mock(VideoRepository.class);
        ProcessingJobRepository jobRepo = mock(ProcessingJobRepository.class);
        WordMeaningCacheRepository cacheRepo = mock(WordMeaningCacheRepository.class);
        DictionaryEntryRepository dictRepo = mock(DictionaryEntryRepository.class);
        VideoWatchHistoryRepository historyRepo = mock(VideoWatchHistoryRepository.class);

        when(userRepo.count()).thenReturn(10L);
        when(userRepo.countByStatus(UserStatus.ACTIVE)).thenReturn(9L);
        when(userRepo.countByStatus(UserStatus.LOCKED)).thenReturn(1L);
        when(userRepo.countByRole(UserRole.ADMIN)).thenReturn(2L);
        when(userRepo.countByCreatedAtAfter(any())).thenReturn(3L);
        when(historyRepo.countDistinctActiveUsersSince(any())).thenReturn(6L);

        when(videoRepo.count()).thenReturn(20L);
        when(videoRepo.countByStatus(VideoStatus.READY)).thenReturn(18L);
        when(videoRepo.countByStatus(VideoStatus.FAILED)).thenReturn(2L);
        when(videoRepo.countByCreatedAtAfter(any())).thenReturn(4L);
        when(videoRepo.findCreatedAtSince(any())).thenReturn(List.of(Instant.now()));

        when(jobRepo.countByCreatedAtAfter(any())).thenReturn(10L);
        when(jobRepo.countByStatusAndCreatedAtAfter(eq(JobStatus.COMPLETED), any())).thenReturn(8L);
        when(jobRepo.countByStatusAndCreatedAtAfter(eq(JobStatus.FAILED), any())).thenReturn(2L);
        when(jobRepo.countByJobTypeAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);
        when(jobRepo.findFailedCreatedAtSince(any())).thenReturn(List.of(Instant.now()));
        when(jobRepo.countFailedByReasonSince(any()))
                .thenReturn(List.<Object[]>of(new Object[]{FailureReason.TRANSLATION_FAILED, 2L}));

        when(cacheRepo.countByStatus(WordMeaningStatus.READY)).thenReturn(100L);
        when(cacheRepo.countByStatus(WordMeaningStatus.FAILED)).thenReturn(5L);
        when(dictRepo.count()).thenReturn(45L);

        AdminDashboardService service = new AdminDashboardService(
                userRepo, videoRepo, jobRepo, cacheRepo, dictRepo, historyRepo);

        var overview = service.getOverview("7d");
        assertEquals("7d", overview.range());
        assertEquals(10L, overview.users().total());
        assertEquals(20L, overview.videos().total());
        assertEquals(80.0, overview.jobs().successRatePercent());
        assertEquals(7, overview.dailyVideos().size());
        assertEquals(1, overview.failureReasons().size());
        assertEquals(FailureReason.TRANSLATION_FAILED, overview.failureReasons().get(0).reason());
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthServiceHandlesQueueCountsAndMissingDependenciesGracefully() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);

        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_VIDEO_JOBS)).thenReturn(queueProps(3));
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_VIDEO_JOBS_RETRY)).thenReturn(queueProps(1));
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_VIDEO_JOBS_DLQ)).thenReturn(queueProps(2));

        ObjectProvider<AmqpAdmin> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(amqpAdmin);

        ProcessingJobRepository jobRepo = mock(ProcessingJobRepository.class);
        when(jobRepo.countByFailureReasonAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);

        AdminHealthService healthService = new AdminHealthService(
                dataSource, provider, jobRepo, "non-existent-yt-dlp-binary", "", "", "gemini-flash-latest");

        var health = healthService.getSystemHealth();
        assertEquals("UP", health.mysql().status());
        assertEquals("UP", health.rabbitmq().status());
        assertEquals(3L, health.rabbitmq().videoJobs());
        assertEquals(1L, health.rabbitmq().retryJobs());
        assertEquals(2L, health.rabbitmq().dlqJobs());
        assertEquals("DOWN", health.ytDlp().status());
        assertFalse(health.ytDlp().cookiesConfigured());
        assertEquals("DOWN", health.gemini().status());
        assertTrue(!health.gemini().apiKeyConfigured());
    }

    private static Properties queueProps(int count) {
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, count);
        return props;
    }
}
