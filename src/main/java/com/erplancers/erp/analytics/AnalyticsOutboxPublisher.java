package com.erplancers.erp.analytics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** At-least-once outbox relay backed by JPA. */
@Component
@ConditionalOnProperty(prefix = "analytics.engine", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AnalyticsOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsOutboxPublisher.class);
    private final AnalyticsEventOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${analytics.kafka.outbox-batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${analytics.kafka.outbox-poll-ms:1000}")
    public void publishPendingEvents() {
        List<AnalyticsEventOutbox> events = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(
                PageRequest.of(0, Math.max(1, Math.min(batchSize, 500))));
        for (AnalyticsEventOutbox event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayloadJson())
                        .get(10, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                outbox.save(event);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
                if (message.length() > 1000) message = message.substring(0, 1000);
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(message);
                outbox.save(event);
                log.warn("Kafka outbox publish failed for event {}; it will be retried", event.getEventId());
            }
        }
    }
}
