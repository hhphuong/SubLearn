package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class YtDlpClient {

    private static final Logger log = LoggerFactory.getLogger(YtDlpClient.class);

    private final String ytDlpPath;
    private final long timeoutSeconds;
    private final YtDlpErrorClassifier errorClassifier;
    private final ObjectMapper objectMapper;
    private final VideoProgressTracker progressTracker;

    public YtDlpClient(@Value("${sublearn.ytdlp.path}") String ytDlpPath,
                        @Value("${sublearn.ytdlp.timeout-seconds}") long timeoutSeconds,
                        YtDlpErrorClassifier errorClassifier,
                        ObjectMapper objectMapper,
                        VideoProgressTracker progressTracker) {
        this.ytDlpPath = ytDlpPath;
        this.timeoutSeconds = timeoutSeconds;
        this.errorClassifier = errorClassifier;
        this.objectMapper = objectMapper;
        this.progressTracker = progressTracker;
    }

    /**
     * Runs in two steps instead of one {@code --sub-langs all} call:
     * <ol>
     *   <li>a quick {@code --dump-json} lookup to read the video's metadata and see which
     *       subtitle languages actually exist, picking exactly one (manual captions
     *       preferred over auto-generated);</li>
     *   <li>a download restricted to that single language.</li>
     * </ol>
     * Requesting "all" languages used to make yt-dlp fetch every auto-translated variant
     * YouTube offers (frequently 100+), which is slow and reliably tripped YouTube's rate
     * limiting (HTTP 429) - this keeps each ingestion to at most two lightweight requests.
     */
    public YtDlpResult fetchCaptionsAndMetadata(Long videoId, String youtubeVideoId) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("sublearn-ytdlp-");
        } catch (IOException e) {
            throw new YtDlpProcessException(FailureReason.UNKNOWN_ERROR, "Could not create a temp directory for yt-dlp output");
        }
        try {
            SubtitleDiscovery discovery = discoverSubtitleLanguage(youtubeVideoId);
            progressTracker.markFetchStage(videoId, 20);

            downloadSubtitle(videoId, youtubeVideoId, discovery.langCode(), discovery.manual(), workDir);

            Optional<Path> vttFile = findVttFile(workDir);
            if (vttFile.isEmpty()) {
                throw new YtDlpProcessException(FailureReason.NO_CAPTIONS_AVAILABLE,
                        "No caption tracks (manual or auto-generated) are available for this video");
            }

            String vttContent = Files.readString(vttFile.get(), StandardCharsets.UTF_8);
            return new YtDlpResult(discovery.title(), discovery.durationSeconds(),
                    primaryLanguageSubtag(discovery.langCode()), vttContent);
        } catch (IOException e) {
            throw new YtDlpProcessException(FailureReason.UNKNOWN_ERROR, "I/O error while running yt-dlp: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YtDlpProcessException(FailureReason.UNKNOWN_ERROR, "yt-dlp invocation was interrupted");
        } finally {
            deleteRecursively(workDir);
        }
    }

    private record SubtitleDiscovery(String title, Integer durationSeconds, String langCode, boolean manual) {
    }

    private SubtitleDiscovery discoverSubtitleLanguage(String youtubeVideoId) throws IOException, InterruptedException {
        List<String> command = List.of(ytDlpPath, "--skip-download", "--dump-json", "--no-warnings",
                "https://www.youtube.com/watch?v=" + youtubeVideoId);
        Process process = new ProcessBuilder(command).start();

        LineCollector stdout = LineCollector.start(process.getInputStream(), null);
        LineCollector stderr = LineCollector.start(process.getErrorStream(), null);

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new YtDlpProcessException(FailureReason.PROCESS_TIMEOUT,
                    "yt-dlp did not finish within " + timeoutSeconds + " seconds");
        }
        String stdoutText = stdout.join();
        String stderrText = stderr.join();

        if (process.exitValue() != 0) {
            FailureReason reason = errorClassifier.classify(stderrText);
            throw new YtDlpProcessException(reason, "yt-dlp exited with code " + process.exitValue() + ": " + firstLine(stderrText));
        }

        JsonNode json = objectMapper.readTree(firstLine(stdoutText));
        String title = json.path("title").isMissingNode() ? null : json.path("title").stringValue(null);
        Integer duration = json.hasNonNull("duration") ? json.path("duration").asInt() : null;
        // yt-dlp's own best-guess at the video's spoken language, e.g. "en" - used to pick
        // the actual original track instead of whichever key happens to sort first.
        String detectedLanguage = json.path("language").isMissingNode() ? null : json.path("language").stringValue(null);

        Optional<String> manualLang = pickLanguageKey(json.path("subtitles"), detectedLanguage, null);
        if (manualLang.isPresent()) {
            return new SubtitleDiscovery(title, duration, manualLang.get(), true);
        }
        // For auto-generated-only videos, YouTube exposes the untranslated original under
        // a "<lang>-orig" key alongside translations into every other language (100+ keys,
        // all plain language codes with no way to tell them apart by name alone) - without
        // this, picking "first key" reliably lands on "ab" (Abkhazian), alphabetically first.
        String preferredOrigKey = detectedLanguage != null ? detectedLanguage + "-orig" : null;
        Optional<String> autoLang = pickLanguageKey(json.path("automatic_captions"), detectedLanguage, preferredOrigKey);
        if (autoLang.isPresent()) {
            return new SubtitleDiscovery(title, duration, autoLang.get(), false);
        }
        throw new YtDlpProcessException(FailureReason.NO_CAPTIONS_AVAILABLE,
                "No caption tracks (manual or auto-generated) are available for this video");
    }

    private Optional<String> pickLanguageKey(JsonNode captionsNode, String detectedLanguage, String preferredKey) {
        if (captionsNode == null || captionsNode.isMissingNode() || !captionsNode.isObject()) {
            return Optional.empty();
        }
        var keys = captionsNode.propertyNames();
        if (preferredKey != null && keys.contains(preferredKey)) {
            return Optional.of(preferredKey);
        }
        if (detectedLanguage != null && keys.contains(detectedLanguage)) {
            return Optional.of(detectedLanguage);
        }
        return keys.stream().findFirst();
    }

    private void downloadSubtitle(Long videoId, String youtubeVideoId, String langCode, boolean manual, Path workDir)
            throws IOException, InterruptedException {
        List<String> command = List.of(ytDlpPath,
                manual ? "--write-sub" : "--write-auto-sub",
                "--sub-format", "vtt", "--sub-langs", langCode,
                "--skip-download", "--no-warnings",
                "https://www.youtube.com/watch?v=" + youtubeVideoId);
        ProcessBuilder processBuilder = new ProcessBuilder(command).directory(workDir.toFile());
        Process process = processBuilder.start();

        LineCollector stdout = LineCollector.start(process.getInputStream(), line -> reportFetchMilestone(videoId, line));
        LineCollector stderr = LineCollector.start(process.getErrorStream(), null);

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new YtDlpProcessException(FailureReason.PROCESS_TIMEOUT,
                    "yt-dlp did not finish within " + timeoutSeconds + " seconds");
        }
        stdout.join();
        String stderrText = stderr.join();

        if (process.exitValue() != 0) {
            FailureReason reason = errorClassifier.classify(stderrText);
            throw new YtDlpProcessException(reason, "yt-dlp exited with code " + process.exitValue() + ": " + firstLine(stderrText));
        }
    }

    /**
     * Maps yt-dlp's own real-time log lines (printed to stdout as it works, as long as
     * {@code --dump-json} isn't also active - that flag silences them entirely) to
     * progress percentages, so the fetch stage shows genuine progress instead of a fake
     * animation.
     */
    private void reportFetchMilestone(Long videoId, String line) {
        int percent;
        if (line.contains("Extracting URL")) {
            percent = 30;
        } else if (line.contains("Downloading webpage")) {
            percent = 40;
        } else if (line.contains("player API JSON") || line.contains("Downloading player")) {
            percent = 50;
        } else if (line.contains("Downloading subtitles") || line.contains("Downloading automatic captions")) {
            percent = 60;
        } else if (line.contains("Writing video subtitles") || line.contains("Writing video metadata")) {
            percent = 65;
        } else {
            return;
        }
        progressTracker.markFetchStage(videoId, percent);
    }

    private Optional<Path> findVttFile(Path workDir) throws IOException {
        try (Stream<Path> files = Files.list(workDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".vtt"))
                    .min(Comparator.comparing(p -> p.getFileName().toString()));
        }
    }

    private String primaryLanguageSubtag(String langCode) {
        int dashIndex = langCode.indexOf('-');
        String primary = dashIndex > 0 ? langCode.substring(0, dashIndex) : langCode;
        return primary.toLowerCase(Locale.ROOT);
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newlineIndex = text.indexOf('\n');
        return newlineIndex >= 0 ? text.substring(0, newlineIndex) : text;
    }

    private void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory {}", path, e);
        }
    }

    /**
     * Reads a process stream line by line, invoking {@code onLine} as each line arrives
     * (not just after the process exits) so real-time milestones can be detected, while
     * still buffering the full text for callers that need it once the process finishes.
     */
    private static final class LineCollector {
        private final Thread thread;
        private final StringBuilder buffer = new StringBuilder();
        private volatile IOException error;

        private LineCollector(InputStream inputStream, Consumer<String> onLine) {
            this.thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line).append('\n');
                        if (onLine != null) {
                            onLine.accept(line);
                        }
                    }
                } catch (IOException e) {
                    this.error = e;
                }
            });
        }

        static LineCollector start(InputStream inputStream, Consumer<String> onLine) {
            LineCollector collector = new LineCollector(inputStream, onLine);
            collector.thread.start();
            return collector;
        }

        String join() throws InterruptedException, IOException {
            thread.join();
            if (error != null) {
                throw error;
            }
            return buffer.toString();
        }
    }
}
