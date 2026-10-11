package com.erplancers.erp.analytics;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesTrendIntelligenceTest {
    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        // MetricsSystem reads this JVM property while SparkContext is constructed.
        // Keep the test config free of the servlet sink: Spark 4's shaded Jetty expects
        // jakarta.servlet.SingleThreadModel, which Spring Boot 4's Servlet API no longer has.
        System.setProperty("spark.metrics.conf",
                Path.of("src/test/resources/spark-metrics.properties").toAbsolutePath().toString());
        String metricsConfig = Path.of("src/test/resources/spark-metrics.properties")
                .toAbsolutePath().toString();
        spark = SparkSession.builder()
                .config("spark.metrics.conf", metricsConfig)
                .appName("sales-trend-intelligence-test")
                .master("local[1]")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", "1")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
            spark = null;
        }
        System.clearProperty("spark.metrics.conf");
    }

    @Test
    void fillsCalendarGapsAndCalculatesMonthlyMetrics() {
        Dataset<Row> source = orders(
                row("o-1", "2026-01-05", 40.0),
                row("o-2", "2026-01-19", 60.0),
                row("o-3", "2026-03-12", 300.0));

        List<Row> trends = SalesTrendIntelligence.monthlyTrends(
                source, "order_id", "order_date", "net_amount").collectAsList();

        assertEquals(3, trends.size(), "A missing month must still appear in the output");
        assertMonth(trends.get(0), "2026-01-01");
        assertNumber(trends.get(0), "revenue", 100.0);
        assertNumber(trends.get(0), "order_count", 2.0);
        assertNumber(trends.get(0), "average_order_value", 50.0);
        assertNull(trends.get(0).getAs("prior_month_revenue"));

        assertMonth(trends.get(1), "2026-02-01");
        assertNumber(trends.get(1), "revenue", 0.0);
        assertNumber(trends.get(1), "order_count", 0.0);
        assertNumber(trends.get(1), "mom_change", -100.0);
        assertNumber(trends.get(1), "mom_growth_pct", -100.0);

        assertMonth(trends.get(2), "2026-03-01");
        assertNumber(trends.get(2), "revenue", 300.0);
        assertNumber(trends.get(2), "trailing_3_month_avg_revenue", 400.0 / 3.0);
        assertNull(trends.get(2).getAs("mom_growth_pct"),
                "Growth is undefined when the prior month revenue is zero");
        assertNull(trends.get(2).getAs("yoy_growth_pct"),
                "Year-over-year growth needs a prior-year value");
    }

    @Test
    void filtersRowsWithMissingRequiredValuesAndCountsDistinctOrders() {
        Dataset<Row> source = orders(
                row("same-order", "2026-04-02", 12.0),
                row("same-order", "2026-04-03", 8.0),
                row(null, "2026-04-04", 100.0),
                row("missing-date", null, 100.0),
                row("missing-amount", "2026-04-05", null));

        List<Row> trends = SalesTrendIntelligence.monthlyTrends(
                source, "order_id", "order_date", "net_amount").collectAsList();

        assertEquals(1, trends.size());
        assertNumber(trends.get(0), "revenue", 20.0);
        assertNumber(trends.get(0), "order_count", 1.0);
        assertNumber(trends.get(0), "average_order_value", 20.0);
    }

    @Test
    void computesYearOverYearGrowthAcrossAFullCalendarYear() {
        List<Row> input = new java.util.ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            input.add(org.apache.spark.sql.RowFactory.create(
                    "old-" + month, Date.valueOf(String.format("2025-%02d-10", month)), 100.0));
        }
        input.add(org.apache.spark.sql.RowFactory.create(
                "new-1", Date.valueOf("2026-01-10"), 150.0));
        Dataset<Row> source = spark.createDataFrame(input, schema());

        List<Row> trends = SalesTrendIntelligence.monthlyTrends(
                source, "order_id", "order_date", "net_amount").collectAsList();

        assertEquals(13, trends.size());
        Row january2026 = trends.get(12);
        assertMonth(january2026, "2026-01-01");
        assertNumber(january2026, "prior_year_revenue", 100.0);
        assertNumber(january2026, "yoy_growth_pct", 50.0);
    }

    @Test
    void rejectsMissingDatasetAndMissingColumnNames() {
        assertThrows(IllegalArgumentException.class,
                () -> SalesIntelligence.requireColumns(null, "order_id"));
        assertThrows(IllegalArgumentException.class,
                () -> SalesIntelligence.requireColumns(null, (String[]) null));
    }

    private static Dataset<Row> orders(Object[]... values) {
        List<Row> rows = new java.util.ArrayList<>();
        for (Object[] value : values) {
            rows.add(org.apache.spark.sql.RowFactory.create(value));
        }
        return spark.createDataFrame(rows, schema());
    }

    private static Object[] row(String id, String date, Double amount) {
        return new Object[] {
                id,
                date == null ? null : Date.valueOf(date),
                amount
        };
    }

    private static StructType schema() {
        return new StructType()
                .add("order_id", DataTypes.StringType, true)
                .add("order_date", DataTypes.DateType, true)
                .add("net_amount", DataTypes.DoubleType, true);
    }

    private static void assertMonth(Row row, String expected) {
        assertEquals(Date.valueOf(expected), row.getAs("month"));
    }

    private static void assertNumber(Row row, String column, double expected) {
        Object value = row.getAs(column);
        assertNotNull(value, column + " should not be null");
        assertEquals(expected, ((Number) value).doubleValue(), 0.001, column);
    }
}
