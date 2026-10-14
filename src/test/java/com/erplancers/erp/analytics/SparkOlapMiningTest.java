package com.erplancers.erp.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SparkOlapMiningTest {
    private static final Set<String> COLUMNS = Set.of("region", "product", "amount");

    @Test
    void acceptsSafeGroupAndCubeRequests() {
        assertDoesNotThrow(() -> SparkOlapMining.validateRequest(
                List.of("region", "product"), List.of("sum:amount", "count"),
                Map.of("region", "west"), "CUBE", "sum_amount:desc", 100, COLUMNS));
    }

    @Test
    void rejectsUnknownDimensionsAndFilterColumns() {
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of("missing"), List.of("count"), Map.of(), "GROUP", "", 100, COLUMNS));
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of("region"), List.of("count"), Map.of("unknown", "x"), "GROUP", "", 100, COLUMNS));
    }

    @Test
    void rejectsUnsafeMeasuresAndDuplicateOutputAliases() {
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of("region"), List.of("sum:amount desc"), Map.of(), "GROUP", "", 100, COLUMNS));
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of("region"), List.of("sum:amount", "sum:amount"), Map.of(), "GROUP", "", 100, COLUMNS));
    }

    @Test
    void enforcesCubeDimensionAndOutputOrderingRules() {
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of(), List.of("count"), Map.of(), "CUBE", "", 100, COLUMNS));
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of("region"), List.of("count"), Map.of(), "GROUP", "amount:desc", 100, COLUMNS));
        assertThrows(IllegalArgumentException.class, () -> SparkOlapMining.validateRequest(
                List.of("region"), List.of("count"), Map.of(), "GROUP", "", 1001, COLUMNS));
    }
}
