package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class YtDlpClient {

    private static final Logger log = LoggerFactory.getLogger(YtDlpClient.class);

    private final String ytDlpPath;
    private final long timeoutSeconds;
    private final YtDlpErrorClassifier errorClassifier;
    private final ObjectMapper objectMapper;

    public YtDlpClient(@Value("${sublearn.ytdlp.path}") String ytDlpPath,
                        @Value("${sublearn.ytdlp.timeout-seconds}") long timeoutSeconds,
                        YtDlpErrorClassifier errorClassifier,
                        ObjectMapper objectMapper) {
        this.ytDlpPath = ytDlpPath;
        this.timeoutSeconds = timeoutSeconds;
        this.errorClassifier = errorClassifier;
        this.objectMapper = objectMapper;
    }

    public YtDlpResult fetchCaptionsAndMetadata(String youtubeVideoId) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("sublearn-ytdlp-");
        } catch (IOException e) {
            throw new YtDlpProcessException(FailureReason.UNKNOWN_ERROR, "Could not create a temp directory for yt-dlp output");
        }
        try {
            List<String> command = List.of(ytDlpPath,
                    "--write-sub", "--write-auto-sub", "--sub-format", "vtt", "--sub-langs", "all",
                    "--skip-download", "--dump-json", "--no-warnings",
                    "https://www.youtube.com/watch?v=" + youtubeVideoId);
            ProcessBuilder processBuilder = new ProcessBuilder(command).directory(workDir.toFile());
            Process process = processBuilder.start();

            StreamCollector stdout = StreamCollector.start(process.getInputStream());
            StreamCollector stderr = StreamCollector.start(process.getErrorStream());

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

            Optional<Path> vttFile = findVttFile(workDir);
            if (vttFile.isEmpty()) {
                throw new YtDlpProcessException(FailureReason.NO_CAPTIONS_AVAILABLE,
                        "No caption tracks (manual or auto-generated) are available for this video");
            }

            String vttContent = Files.readString(vttFile.get(), StandardCharsets.UTF_8);
            String sourceLanguage = extractLanguageCode(vttFile.get(), youtubeVideoId);
            if (sourceLanguage == null) {
                throw new YtDlpProcessException(FailureReason.SOURCE_LANGUAGE_UNDETECTABLE,
                        "Could not determine the caption language from yt-dlp output");
            }

            return new YtDlpResult(title, duration, sourceLanguage, vttContent);
        } catch (IOException e) {
            throw new YtDlpProcessException(FailureReason.UNKNOWN_ERROR, "I/O error while running yt-dlp: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YtDlpProcessException(FailureReason.UNKNOWN_ERROR, "yt-dlp invocation was interrupted");
        } finally {
            deleteRecursively(workDir);
        }
    }

    private Optional<Path> findVttFile(Path workDir) throws IOException {
        try (Stream<Path> files = Files.list(workDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".vtt"))
                    .min(Comparator.comparing(p -> p.getFileName().toString()));
        }
    }

    private String extractLanguageCode(Path vttFile, String youtubeVideoId) {
        String fileName = vttFile.getFileName().toString();
        String withoutExtension = fileName.substring(0, fileName.length() - ".vtt".length());
        int lastDot = withoutExtension.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String lang = withoutExtension.substring(lastDot + 1);
        int dashIndex = lang.indexOf('-');
        String primary = dashIndex > 0 ? lang.substring(0, dashIndex) : lang;
        return primary.isBlank() ? null : primary.toLowerCase(Locale.ROOT);
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

    private static final class StreamCollector {
        private final Thread thread;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile IOException error;

        private StreamCollector(InputStream inputStream) {
            this.thread = new Thread(() -> {
                try {
                    inputStream.transferTo(buffer);
                } catch (IOException e) {
                    this.error = e;
                }
            });
        }

        static StreamCollector start(InputStream inputStream) {
            StreamCollector collector = new StreamCollector(inputStream);
            collector.thread.start();
            return collector;
        }

        String join() throws InterruptedException, IOException {
            thread.join();
            if (error != null) {
                throw error;
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
