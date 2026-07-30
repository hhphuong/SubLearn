package org.example.sublearn.ingestion;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.nlp.TokenizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VideoIngestionService {

    private static final Logger log = LoggerFactory.getLogger(VideoIngestionService.class);

    private final YtDlpClient ytDlpClient;
    private final VideoIngestionPersister persister;

    public VideoIngestionService(YtDlpClient ytDlpClient, VideoIngestionPersister persister) {
        this.ytDlpClient = ytDlpClient;
        this.persister = persister;
    }

    @Async("ingestionTaskExecutor")
    public void ingest(Long videoId, String youtubeVideoId) {
        persister.markProcessing(videoId);
        try {
            YtDlpResult result = ytDlpClient.fetchCaptionsAndMetadata(youtubeVideoId);
            List<VttCue> cues = VttParser.parse(result.vttContent());
            persister.persistReady(videoId, result, cues);
        } catch (YtDlpProcessException e) {
            log.info("Ingestion failed for video {}: {}", videoId, e.getMessage());
            persister.markFailed(videoId, e.getFailureReason(), e.getMessage());
        } catch (TokenizationException e) {
            log.warn("Tokenization failed for video {}", videoId, e);
            persister.markFailed(videoId, FailureReason.TOKENIZATION_FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected ingestion failure for video {}", videoId, e);
            persister.markFailed(videoId, FailureReason.UNKNOWN_ERROR, e.getMessage());
        }
    }
}
