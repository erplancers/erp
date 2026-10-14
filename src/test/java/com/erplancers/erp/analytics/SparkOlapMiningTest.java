package com.erplancers.erp.analytics;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SparkOlapMiningTest {
    private static SparkSession spark;

    @BeforeAll
    static void startSpark() throws Exception {
        Path metricsConfig = Files.createTempFile("spark-olap-metrics", ".properties");
        Files.writeString(metricsConfig, "*.sink.console.class=org.apache.spark.metrics.sink.ConsoleSink\n*.sink.console.period=1\n*.sink.console.unit=minutes\n");
        spark = SparkSession.builder().appName("spark-olap-mining-test").master("local[2]")
                .config("spark.ui.enabled", "false")
                .config("spark.metrics.conf", metricsConfig.toString())
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", "2")
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) spark.stop();
    }

    private Dataset<Row> source() {
        StructType schema = new StructType()
                .add("region", DataTypes.StringType, false)
                .add("product", DataTypes.StringType, false)
                .add("amount", DataTypes.DoubleType, false);
        return spark.createDataFrame(List.of(
                org.apache.spark.sql.RowFactory.create("west", "a", 10.0),
                org.apache.spark.sql.RowFactory.create("west", "b", 20.0),
                org.apache.spark.sql.RowFactory.create("east", "a", 5.0)
        ), schema);
    }

    @Test
    void aggregatesMeasuresAndAppliesFilters() {
        Dataset<Row> result = SparkOlapMining.execute(source(), List.of("region"),
                List.of("sum:amount", "count"), Map.of("product", "a"),
                "GROUP", "sum_amount:desc", 20);
        List<Row> rows = result.collectAsList();
        assertEquals(2, rows.size());
        assertEquals("west", rows.getFirst().getAs("region"));
        assertEquals(10.0, ((Number) rows.getFirst().getAs("sum_amount")).doubleValue(), 0.0001);
        assertEquals(1L, ((Number) rows.getFirst().getAs("row_count")).longValue());
    }

    @Test
    void cubeProducesSubtotalRows() {
        Dataset<Row> result = SparkOlapMining.execute(source(), List.of("region", "product"),
                List.of("sum:amount"), Map.of(), "CUBE", "sum_amount:desc", 100);
        List<Row> rows = result.collectAsList();
        assertTrue(rows.stream().anyMatch(row -> row.isNullAt(0) && row.isNullAt(1)));
        assertTrue(rows.size() > 3);
    }

    @Test
    void rejectsUnknownColumnsAndUnsafeExpressions() {
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.execute(
                source(), List.of("region;drop"), List.of("count"), Map.of(), "GROUP", "", 10));
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.execute(
                source(), List.of("missing"), List.of("count"), Map.of(), "GROUP", "", 10));
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.execute(
                source(), List.of("region"), List.of("sum:amount desc"), Map.of(), "GROUP", "", 10));
    }
}
