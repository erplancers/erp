package com.erplancers.erp.analytics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** At-least-once outbox relay: job creation and notification intent commit atomically. */
@Component
@RequiredArgsConstructor
public class AnalyticsOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsOutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${analytics.kafka.outbox-batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${analytics.kafka.outbox-poll-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = jdbc.query("""
                SELECT event_id, topic, event_key, payload_json
                  FROM analytics_event_outbox
                 WHERE published_at IS NULL
                 ORDER BY created_at
                 LIMIT ?
                """, (rs, row) -> new OutboxEvent(rs.getString("event_id"), rs.getString("topic"),
                rs.getString("event_key"), rs.getString("payload_json")), Math.max(1, Math.min(batchSize, 500)));
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.topic(), event.key(), event.payload()).get(10, TimeUnit.SECONDS);
                jdbc.update("""
                        UPDATE analytics_event_outbox
                           SET published_at = CURRENT_TIMESTAMP, last_error = NULL
                         WHERE event_id = ? AND published_at IS NULL
                        """, event.id());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
                if (message.length() > 1000) message = message.substring(0, 1000);
                jdbc.update("""
                        UPDATE analytics_event_outbox
                           SET attempts = attempts + 1, last_error = ?
                         WHERE event_id = ? AND published_at IS NULL
                        """, message, event.id());
                log.warn("Kafka outbox publish failed for event {}; it will be retried", event.id());
            }
        }
    }
    private record OutboxEvent(String id, String topic, String key, String payload) {}
}
