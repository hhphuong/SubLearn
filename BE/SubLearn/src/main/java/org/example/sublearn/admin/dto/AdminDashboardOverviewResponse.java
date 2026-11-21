package org.example.sublearn.admin.dto;

import java.util.List;
import java.util.Map;

public record AdminDashboardOverviewResponse(
        String range,
        UserKpis users,
        VideoKpis videos,
        JobKpis jobs,
        CacheAndDictionaryKpis cacheAndDictionary,
        List<DailyCountDto> dailyVideos,
        List<DailyCountDto> dailyFailedJobs,
        List<FailureReasonCountDto> failureReasons
) {
    public record UserKpis(
            long total,
            long active,
            long locked,
            long admins,
            long newInRange,
            long activeInRange
    ) {
    }

    public record VideoKpis(
            long total,
            long ready,
            long processing,
            long pending,
            long failed,
            long createdInRange
    ) {
    }

    public record JobKpis(
            long totalInRange,
            long pending,
            long processing,
            long completedInRange,
            long failedInRange,
            double successRatePercent,
            Map<String, Long> byTypeInRange
    ) {
    }

    public record CacheAndDictionaryKpis(
            long wordCacheReady,
            long wordCacheFailed,
            long totalDictionaryEntries
    ) {
    }
}
