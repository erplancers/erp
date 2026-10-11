package com.erplancers.erp.analytics;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Exposes embedded analytics initialization/readiness through the existing Actuator health endpoint. */
@Component("analyticsEngine")
@ConditionalOnProperty(prefix = "analytics.engine", name = "enabled", havingValue = "true")
public class AnalyticsEngineHealthIndicator implements HealthIndicator {
    private final EmbeddedAnalyticsEngine engine;

    public AnalyticsEngineHealthIndicator(EmbeddedAnalyticsEngine engine) {
        this.engine = engine;
    }

    @Override
    public Health health() {
        String state = engine.engineState();
        if ("RUNNING".equals(state)) {
            return Health.up().withDetail("engineState", state).build();
        }
        if ("FAILED".equals(state)) {
            return Health.status(org.springframework.boot.actuate.health.Status.UNKNOWN).withDetail("engineState", state)
                    .withDetail("hint", "Check analytics catalogue, database grants, Spark startup and Kafka connectivity")
                    .build();
        }
        return Health.outOfService().withDetail("engineState", state).build();
    }
}
