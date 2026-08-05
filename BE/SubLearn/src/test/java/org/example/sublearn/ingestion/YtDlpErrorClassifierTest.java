package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YtDlpErrorClassifierTest {

    private final YtDlpErrorClassifier classifier = new YtDlpErrorClassifier();

    @Test
    void classifiesPrivateVideoError() {
        assertEquals(FailureReason.VIDEO_PRIVATE, classifier.classify("ERROR: [youtube] abc123: Private video. Sign in if you've been granted access"));
    }

    @Test
    void classifiesAgeRestrictedError() {
        assertEquals(FailureReason.VIDEO_AGE_RESTRICTED,
                classifier.classify("ERROR: [youtube] abc123: Sign in to confirm your age"));
    }

    @Test
    void classifiesRegionLockedError() {
        assertEquals(FailureReason.VIDEO_REGION_LOCKED,
                classifier.classify("ERROR: [youtube] abc123: The uploader has not made this video available in your country"));
    }

    @Test
    void classifiesUnavailableVideoError() {
        assertEquals(FailureReason.VIDEO_UNAVAILABLE, classifier.classify("ERROR: [youtube] abc123: Video unavailable"));
    }

    @Test
    void classifiesVideoIsNotAvailableError() {
        // The exact wording yt-dlp uses for a deleted/terminated video - distinct from
        // "Video unavailable" / "This video is unavailable" above, and previously fell
        // through to UNKNOWN_ERROR unmatched.
        assertEquals(FailureReason.VIDEO_UNAVAILABLE,
                classifier.classify("ERROR: [youtube] hUuC9GoaELI: This video is not available"));
    }

    @Test
    void classifiesInvalidUrlError() {
        assertEquals(FailureReason.INVALID_URL, classifier.classify("ERROR: Unsupported URL: not-a-url"));
    }

    @Test
    void classifiesUnrecognizedTextAsUnknown() {
        assertEquals(FailureReason.UNKNOWN_ERROR, classifier.classify("ERROR: something totally unexpected happened"));
    }

    @Test
    void classifiesBlankStderrAsUnknown() {
        assertEquals(FailureReason.UNKNOWN_ERROR, classifier.classify(""));
        assertEquals(FailureReason.UNKNOWN_ERROR, classifier.classify(null));
    }
}
