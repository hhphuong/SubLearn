package org.example.sublearn.admin;

import org.example.sublearn.admin.dto.AdminDashboardOverviewResponse;
import org.example.sublearn.admin.dto.DailyCountDto;
import org.example.sublearn.admin.dto.FailureReasonCountDto;
import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.example.sublearn.domain.VideoStatus;
import org.example.sublearn.domain.WordMeaningStatus;
import org.example.sublearn.repository.AppUserRepository;
import org.example.sublearn.repository.DictionaryEntryRepository;
import org.example.sublearn.repository.ProcessingJobRepository;
import org.example.sublearn.repository.VideoRepository;
import org.example.sublearn.repository.VideoWatchHistoryRepository;
import org.example.sublearn.repository.WordMeaningCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminDashboardService {

    private final AppUserRepository appUserRepository;
    private final VideoRepository videoRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final WordMeaningCacheRepository wordMeaningCacheRepository;
    private final DictionaryEntryRepository dictionaryEntryRepository;
    private final VideoWatchHistoryRepository videoWatchHistoryRepository;

    public AdminDashboardService(AppUserRepository appUserRepository,
                                 VideoRepository videoRepository,
                                 ProcessingJobRepository processingJobRepository,
                                 WordMeaningCacheRepository wordMeaningCacheRepository,
                                 DictionaryEntryRepository dictionaryEntryRepository,
                                 VideoWatchHistoryRepository videoWatchHistoryRepository) {
        this.appUserRepository = appUserRepository;
        this.videoRepository = videoRepository;
        this.processingJobRepository = processingJobRepository;
        this.wordMeaningCacheRepository = wordMeaningCacheRepository;
        this.dictionaryEntryRepository = dictionaryEntryRepository;
        this.videoWatchHistoryRepository = videoWatchHistoryRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardOverviewResponse getOverview(String rawRange) {
        String normalizedRange = normalizeRange(rawRange);
        Instant now = Instant.now();
        Instant since = resolveSince(normalizedRange, now);

        long totalUsers = appUserRepository.count();
        long activeUsers = appUserRepository.countByStatus(UserStatus.ACTIVE);
        long lockedUsers = appUserRepository.countByStatus(UserStatus.LOCKED);
        long adminUsers = appUserRepository.countByRole(UserRole.ADMIN);
        long newUsersInRange = since == null ? totalUsers : appUserRepository.countByCreatedAtAfter(since);
        long activeInRange = videoWatchHistoryRepository.countDistinctActiveUsersSince(since);

        AdminDashboardOverviewResponse.UserKpis userKpis = new AdminDashboardOverviewResponse.UserKpis(
                totalUsers, activeUsers, lockedUsers, adminUsers, newUsersInRange, activeInRange
        );

        long totalVideos = videoRepository.count();
        long readyVideos = videoRepository.countByStatus(VideoStatus.READY);
        long processingVideos = videoRepository.countByStatus(VideoStatus.PROCESSING);
        long pendingVideos = videoRepository.countByStatus(VideoStatus.PENDING);
        long failedVideos = videoRepository.countByStatus(VideoStatus.FAILED);
        long createdVideosInRange = since == null ? totalVideos : videoRepository.countByCreatedAtAfter(since);

        AdminDashboardOverviewResponse.VideoKpis videoKpis = new AdminDashboardOverviewResponse.VideoKpis(
                totalVideos, readyVideos, processingVideos, pendingVideos, failedVideos, createdVideosInRange
        );

        long totalJobsInRange = since == null
                ? processingJobRepository.count()
                : processingJobRepository.countByCreatedAtAfter(since);
        long pendingJobs = processingJobRepository.countByStatus(JobStatus.PENDING);
        long processingJobs = processingJobRepository.countByStatus(JobStatus.PROCESSING);
        long completedJobsInRange = since == null
                ? processingJobRepository.countByStatus(JobStatus.COMPLETED)
                : processingJobRepository.countByStatusAndCreatedAtAfter(JobStatus.COMPLETED, since);
        long failedJobsInRange = since == null
                ? processingJobRepository.countByStatus(JobStatus.FAILED)
                : processingJobRepository.countByStatusAndCreatedAtAfter(JobStatus.FAILED, since);

        long terminalJobs = completedJobsInRange + failedJobsInRange;
        double successRate = terminalJobs == 0
                ? 100.0
                : Math.round((completedJobsInRange * 1000.0) / terminalJobs) / 10.0;

        Map<String, Long> jobsByType = new LinkedHashMap<>();
        for (JobType type : JobType.values()) {
            long typeCount = 0;
            for (JobStatus status : JobStatus.values()) {
                typeCount += since == null
                        ? processingJobRepository.countByJobTypeAndStatus(type, status)
                        : processingJobRepository.countByJobTypeAndStatusAndCreatedAtAfter(type, status, since);
            }
            jobsByType.put(type.name(), typeCount);
        }

        AdminDashboardOverviewResponse.JobKpis jobKpis = new AdminDashboardOverviewResponse.JobKpis(
                totalJobsInRange, pendingJobs, processingJobs, completedJobsInRange, failedJobsInRange,
                successRate, jobsByType
        );

        long wordCacheReady = wordMeaningCacheRepository.countByStatus(WordMeaningStatus.READY);
        long wordCacheFailed = wordMeaningCacheRepository.countByStatus(WordMeaningStatus.FAILED);
        long totalDictionaryEntries = dictionaryEntryRepository.count();

        AdminDashboardOverviewResponse.CacheAndDictionaryKpis cacheKpis =
                new AdminDashboardOverviewResponse.CacheAndDictionaryKpis(
                        wordCacheReady, wordCacheFailed, totalDictionaryEntries
                );

        int chartDays = switch (normalizedRange) {
            case "24h" -> 2;
            case "7d" -> 7;
            default -> 30;
        };
        LocalDate endDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate startDate = endDate.minusDays(chartDays - 1L);
        Instant chartSince = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<DailyCountDto> dailyVideos = bucketByDay(
                videoRepository.findCreatedAtSince(chartSince), startDate, endDate);
        List<DailyCountDto> dailyFailedJobs = bucketByDay(
                processingJobRepository.findFailedCreatedAtSince(chartSince), startDate, endDate);

        List<FailureReasonCountDto> failureReasons = processingJobRepository.countFailedByReasonSince(since).stream()
                .map(row -> new FailureReasonCountDto((FailureReason) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new AdminDashboardOverviewResponse(
                normalizedRange, userKpis, videoKpis, jobKpis, cacheKpis,
                dailyVideos, dailyFailedJobs, failureReasons
        );
    }

    private static String normalizeRange(String rawRange) {
        if (rawRange == null || rawRange.isBlank()) {
            return "7d";
        }
        return switch (rawRange.trim().toLowerCase(Locale.ROOT)) {
            case "24h", "7d", "30d", "all" -> rawRange.trim().toLowerCase(Locale.ROOT);
            default -> "7d";
        };
    }

    private static Instant resolveSince(String range, Instant now) {
        return switch (range) {
            case "24h" -> now.minus(Duration.ofHours(24));
            case "7d" -> now.minus(Duration.ofDays(7));
            case "30d" -> now.minus(Duration.ofDays(30));
            default -> null;
        };
    }

    private static List<DailyCountDto> bucketByDay(List<Instant> timestamps, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            counts.put(d, 0L);
        }
        for (Instant ts : timestamps) {
            if (ts == null) {
                continue;
            }
            LocalDate day = ts.atZone(ZoneOffset.UTC).toLocalDate();
            if (counts.containsKey(day)) {
                counts.put(day, counts.get(day) + 1L);
            }
        }
        List<DailyCountDto> result = new ArrayList<>(counts.size());
        for (Map.Entry<LocalDate, Long> entry : counts.entrySet()) {
            result.add(new DailyCountDto(entry.getKey().toString(), entry.getValue()));
        }
        return result;
    }
}
