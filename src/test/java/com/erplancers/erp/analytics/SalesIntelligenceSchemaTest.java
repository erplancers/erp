package com.erplancers.erp.analytics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SalesIntelligenceSchemaTest {
    @Test
    void rejectsMissingOrEmptyColumnNamesEarly() {
        assertThrows(IllegalArgumentException.class,
                () -> SalesIntelligence.requireColumns(null, "customer_id"));
        assertThrows(IllegalArgumentException.class,
                () -> SalesIntelligence.requireColumns(null, (String) null));
    }
}
