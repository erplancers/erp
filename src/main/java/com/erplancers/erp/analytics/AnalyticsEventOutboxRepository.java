package com.erplancers.erp.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalyticsEventOutboxRepository extends JpaRepository<AnalyticsEventOutbox, String> {
    @Query("select e from AnalyticsEventOutbox e where e.publishedAt is null order by e.createdAt asc")
    List<AnalyticsEventOutbox> findPending(@Param("unused") String unused);
}
