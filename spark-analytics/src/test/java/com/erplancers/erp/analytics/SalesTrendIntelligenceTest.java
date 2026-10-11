package com.erplancers.erp.analytics;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Date;
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
                .config("spark.sql.shuffle.partitions", "1")
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void fillsMissingCalendarMonthsAndComputesComparisons() {
        StructType schema = new StructType()
                .add("order_id", DataTypes.StringType, false)
                .add("order_date", DataTypes.DateType, false)
                .add("net_amount", DataTypes.DoubleType, false);
        List<Row> rows = List.of(
                org.apache.spark.sql.RowFactory.create("o-1", Date.valueOf("2026-01-05"), 40.0),
                org.apache.spark.sql.RowFactory.create("o-2", Date.valueOf("2026-01-19"), 60.0),
                org.apache.spark.sql.RowFactory.create("o-3", Date.valueOf("2026-03-12"), 300.0));
        Dataset<Row> source = spark.createDataFrame(rows, schema);

        List<Row> trends = SalesTrendIntelligence.monthlyTrends(
                source, "order_id", "order_date", "net_amount").collectAsList();

        assertEquals(3, trends.size(), "February must be represented despite no source orders");
        assertEquals(Date.valueOf("2026-01-01"), trends.get(0).getAs("month"));
        assertEquals(100.0, ((Number) trends.get(0).getAs("revenue")).doubleValue(), 0.0001);
        assertEquals(2L, ((Number) trends.get(0).getAs("order_count")).longValue());
        assertEquals(50.0, ((Number) trends.get(0).getAs("average_order_value")).doubleValue(), 0.0001);
        assertEquals(Date.valueOf("2026-02-01"), trends.get(1).getAs("month"));
        assertEquals(0.0, ((Number) trends.get(1).getAs("revenue")).doubleValue(), 0.0001);
        assertEquals(-100.0, ((Number) trends.get(1).getAs("mom_change")).doubleValue(), 0.0001);
        assertNull(trends.get(1).getAs("mom_growth_pct"), "Growth is undefined from a zero baseline");
        assertEquals(300.0, ((Number) trends.get(2).getAs("revenue")).doubleValue(), 0.0001);
        assertEquals(133.333333, ((Number) trends.get(2).getAs("trailing_3_month_avg_revenue")).doubleValue(), 0.001);
        assertNull(trends.get(2).getAs("yoy_growth_pct"), "YoY growth requires a 12-month baseline");
    }
}
