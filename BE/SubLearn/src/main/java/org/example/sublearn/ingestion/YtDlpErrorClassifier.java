package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Maps yt-dlp's own stderr error text (there is no stable machine-readable error code)
 * to a {@link FailureReason}. Substrings are taken from yt-dlp's actual error messages
 * as of the version this was written against; yt-dlp may change wording over time, in
 * which case unmatched errors safely fall back to {@link FailureReason#UNKNOWN_ERROR}.
 */
@Component
public class YtDlpErrorClassifier {

    public FailureReason classify(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return FailureReason.UNKNOWN_ERROR;
        }
        String lower = stderr.toLowerCase(Locale.ROOT);
        if (lower.contains("private video")) {
            return FailureReason.VIDEO_PRIVATE;
        }
        if (lower.contains("sign in to confirm your age") || lower.contains("age-restricted")) {
            return FailureReason.VIDEO_AGE_RESTRICTED;
        }
        if (lower.contains("available in your country") || lower.contains("blocked it in your country")
                || lower.contains("blocked it on copyright grounds")) {
            return FailureReason.VIDEO_REGION_LOCKED;
        }
        if (lower.contains("video unavailable") || lower.contains("this video is unavailable")
                || lower.contains("this video is no longer available") || lower.contains("this video is not available")
                || lower.contains("has been removed")) {
            return FailureReason.VIDEO_UNAVAILABLE;
        }
        if (lower.contains("unsupported url") || lower.contains("is not a valid url")) {
            return FailureReason.INVALID_URL;
        }
        return FailureReason.UNKNOWN_ERROR;
    }
}
