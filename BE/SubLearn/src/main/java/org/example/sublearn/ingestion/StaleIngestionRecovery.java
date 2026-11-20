package org.example.sublearn.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Previously marked PENDING/PROCESSING videos FAILED on startup because ingestion ran on an
 * in-memory {@code @Async} executor. Video ingestion and translation are now backed by durable
 * RabbitMQ queues + {@code processing_job} records, so queued jobs survive server restarts and
 * must NOT be marked FAILED at boot.
 */
@Component
public class StaleIngestionRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleIngestionRecovery.class);

    @Override
    public void run(ApplicationArguments args) {
        log.debug("Durable RabbitMQ job queue is enabled; skipping in-memory stale ingestion cleanup");
    }
}
