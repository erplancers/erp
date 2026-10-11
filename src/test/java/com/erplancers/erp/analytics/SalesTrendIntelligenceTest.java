package com.erplancers.erp.analytics;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesTrendIntelligenceTest {
    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        spark = SparkSession.builder()
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
    }

    @Test
    void aggregatesRevenueAndFillsMissingCalendarMonths() {
        List<Row> trends = trends(
                order("jan-a", "2026-01-05", 40.0),
                order("jan-b", "2026-01-19", 60.0),
                order("mar-a", "2026-03-12", 300.0));

        assertEquals(3, trends.size());
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
                "Growth must be undefined when previous-month revenue is zero");
        assertNull(trends.get(2).getAs("yoy_growth_pct"),
                "Year-over-year growth needs a prior-year month");
    }

    @Test
    void ignoresIncompleteOrdersAndCountsDistinctOrderIds() {
        List<Row> trends = trends(
                order("duplicate", "2026-04-02", 12.0),
                order("duplicate", "2026-04-03", 8.0),
                order(null, "2026-04-04", 100.0),
                order("missing-date", null, 100.0),
                order("missing-amount", "2026-04-05", null));

        assertEquals(1, trends.size());
        assertNumber(trends.get(0), "revenue", 20.0);
        assertNumber(trends.get(0), "order_count", 1.0);
        assertNumber(trends.get(0), "average_order_value", 20.0);
    }

    @Test
    void computesYearOverYearGrowthUsingTheSameCalendarMonth() {
        List<Row> input = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            input.add(order("old-" + month, String.format("2025-%02d-10", month), 100.0));
        }
        input.add(order("new-jan", "2026-01-10", 150.0));

        List<Row> result = SalesTrendIntelligence.monthlyTrends(
                dataset(input), "order_id", "order_date", "net_amount").collectAsList();

        assertEquals(13, result.size());
        Row january2026 = result.get(12);
        assertMonth(january2026, "2026-01-01");
        assertNumber(january2026, "prior_year_revenue", 100.0);
        assertNumber(january2026, "yoy_growth_pct", 50.0);
    }

    @Test
    void returnsNoRowsForAnEmptyDataset() {
        Dataset<Row> empty = spark.createDataFrame(List.of(), schema());

        assertTrue(SalesTrendIntelligence.monthlyTrends(
                empty, "order_id", "order_date", "net_amount").collectAsList().isEmpty());
    }

    @Test
    void rejectsNullDatasetAndMissingColumnNames() {
        assertThrows(IllegalArgumentException.class,
                () -> SalesTrendIntelligence.monthlyTrends(null, "order_id", "order_date", "net_amount"));
        assertThrows(IllegalArgumentException.class,
                () -> SalesTrendIntelligence.monthlyTrends(dataset(List.of()),
                        "order_id", "unknown_date", "net_amount"));
        assertThrows(IllegalArgumentException.class,
                () -> SalesTrendIntelligence.monthlyTrends(dataset(List.of()),
                        " ", "order_date", "net_amount"));
    }

    private static List<Row> trends(Object[]... orders) {
        List<Row> rows = new ArrayList<>();
        for (Object[] order : orders) {
            rows.add(RowFactory.create(order));
        }
        return SalesTrendIntelligence.monthlyTrends(
                spark.createDataFrame(rows, schema()),
                "order_id", "order_date", "net_amount").collectAsList();
    }

    private static Dataset<Row> dataset(List<Row> rows) {
        return spark.createDataFrame(rows, schema());
    }

    private static Object[] order(String id, String date, Double amount) {
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
        Object actual = row.getAs(column);
        assertNotNull(actual, column + " should not be null");
        assertEquals(expected, ((Number) actual).doubleValue(), 0.001, column);
    }
}
