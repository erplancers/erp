package com.erplancers.erp.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsDatasetCatalogTest {
    @TempDir Path tempDir;

    @Test
    void loadsExplicitMappingsAndRejectsUnknownDataset() throws IOException {
        Path config = write("""
                analytics.datasets.sales.table=reporting.sales_view
                analytics.datasets.sales.columns=contact_id:customer_id,order_total:net_amount
                analytics.datasets.sales.tenant-column=tenant_id
                analytics.datasets.sales.scope-column=contact_id
                analytics.datasets.sales.scope-type=CONTACT
                """);
        AnalyticsDatasetCatalog catalog = AnalyticsDatasetCatalog.load(config);
        var definition = catalog.require("sales");
        assertEquals("reporting.sales_view", definition.table());
        assertEquals(2, definition.projections().size());
        assertTrue(definition.logicalColumns().contains("customer_id"));
        assertThrows(IllegalArgumentException.class, () -> catalog.require("unknown"));
    }

    @Test
    void rejectsUnsafeSqlIdentifiersAndPlaceholderTables() throws IOException {
        Path injection = write("""
                analytics.datasets.sales.table=reporting.sales;DROP_TABLE
                analytics.datasets.sales.columns=contact_id:customer_id
                analytics.datasets.sales.tenant-column=tenant_id
                analytics.datasets.sales.scope-column=contact_id
                analytics.datasets.sales.scope-type=CONTACT
                """);
        assertThrows(IllegalArgumentException.class, () -> AnalyticsDatasetCatalog.load(injection));

        Path placeholder = write("""
                analytics.datasets.sales.table=YOUR_APPROVED_SCHEMA.YOUR_APPROVED_VIEW
                analytics.datasets.sales.columns=contact_id:customer_id
                analytics.datasets.sales.tenant-column=tenant_id
                analytics.datasets.sales.scope-column=contact_id
                analytics.datasets.sales.scope-type=CONTACT
                """);
        assertThrows(IllegalArgumentException.class, () -> AnalyticsDatasetCatalog.load(placeholder));
    }

    @Test
    void rejectsDuplicateAliasesAndUnsupportedScopes() throws IOException {
        Path duplicate = write("""
                analytics.datasets.sales.table=reporting.sales_view
                analytics.datasets.sales.columns=contact_id:customer_id,customer_key:customer_id
                analytics.datasets.sales.tenant-column=tenant_id
                analytics.datasets.sales.scope-column=contact_id
                analytics.datasets.sales.scope-type=CONTACT
                """);
        assertThrows(IllegalArgumentException.class, () -> AnalyticsDatasetCatalog.load(duplicate));

        Path scope = write("""
                analytics.datasets.sales.table=reporting.sales_view
                analytics.datasets.sales.columns=contact_id:customer_id
                analytics.datasets.sales.tenant-column=tenant_id
                analytics.datasets.sales.scope-column=contact_id
                analytics.datasets.sales.scope-type=ALL_USERS
                """);
        assertThrows(IllegalArgumentException.class, () -> AnalyticsDatasetCatalog.load(scope));
    }

    private Path write(String source) throws IOException {
        Path config = tempDir.resolve("datasets-" + System.nanoTime() + ".properties");
        Files.writeString(config, source);
        return config;
    }
}
