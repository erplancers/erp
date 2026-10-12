package com.erplancers.erp.analytics;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsEventOutboxRepository extends JpaRepository<AnalyticsEventOutbox, String> {
    List<AnalyticsEventOutbox> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
