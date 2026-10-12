package com.erplancers.erp.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "analytics_event_outbox")
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsEventOutbox {
    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;
    @Column(nullable = false, length = 200)
    private String topic;
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    @jakarta.persistence.PrePersist
    void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }
}
