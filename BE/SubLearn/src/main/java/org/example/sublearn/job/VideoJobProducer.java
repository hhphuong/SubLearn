package org.example.sublearn.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class VideoJobProducer {

    private static final Logger log = LoggerFactory.getLogger(VideoJobProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public VideoJobProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishAfterCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(jobId);
                }
            });
        } else {
            publish(jobId);
        }
    }

    public void publish(Long jobId) {
        log.info("[RABBITMQ] Enqueuing job {}", jobId);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY_VIDEO_JOBS, String.valueOf(jobId));
    }

    public void publishRetry(Long jobId) {
        log.info("[RABBITMQ] Scheduling retry for job {}", jobId);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY_VIDEO_JOBS_RETRY, String.valueOf(jobId));
    }

    public void publishDlq(Long jobId) {
        log.warn("[RABBITMQ] Moving failed job {} to DLQ", jobId);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY_VIDEO_JOBS_DLQ, String.valueOf(jobId));
    }
}
