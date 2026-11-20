package org.example.sublearn.job;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "sublearn.jobs.exchange";

    public static final String QUEUE_VIDEO_JOBS = "sublearn.video.jobs";
    public static final String ROUTING_KEY_VIDEO_JOBS = "video.jobs";

    public static final String QUEUE_VIDEO_JOBS_RETRY = "sublearn.video.jobs.retry";
    public static final String ROUTING_KEY_VIDEO_JOBS_RETRY = "video.jobs.retry";

    public static final String QUEUE_VIDEO_JOBS_DLQ = "sublearn.video.jobs.dlq";
    public static final String ROUTING_KEY_VIDEO_JOBS_DLQ = "video.jobs.dlq";

    @Bean
    public DirectExchange jobsExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue videoJobsQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_JOBS).build();
    }

    @Bean
    public Queue videoJobsRetryQueue(@Value("${sublearn.job.retry-delay-ms:5000}") long retryDelayMs) {
        return QueueBuilder.durable(QUEUE_VIDEO_JOBS_RETRY)
                .ttl((int) retryDelayMs)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY_VIDEO_JOBS)
                .build();
    }

    @Bean
    public Queue videoJobsDlq() {
        return QueueBuilder.durable(QUEUE_VIDEO_JOBS_DLQ).build();
    }

    @Bean
    public Binding videoJobsBinding(Queue videoJobsQueue, DirectExchange jobsExchange) {
        return BindingBuilder.bind(videoJobsQueue).to(jobsExchange).with(ROUTING_KEY_VIDEO_JOBS);
    }

    @Bean
    public Binding videoJobsRetryBinding(Queue videoJobsRetryQueue, DirectExchange jobsExchange) {
        return BindingBuilder.bind(videoJobsRetryQueue).to(jobsExchange).with(ROUTING_KEY_VIDEO_JOBS_RETRY);
    }

    @Bean
    public Binding videoJobsDlqBinding(Queue videoJobsDlq, DirectExchange jobsExchange) {
        return BindingBuilder.bind(videoJobsDlq).to(jobsExchange).with(ROUTING_KEY_VIDEO_JOBS_DLQ);
    }
}
