package com.erplancers.erp.common.reporting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalculatedMeasureExpressionTest {
    @Test void calculatesRatiosMarginsAndPeriodComparisons() {
        Map<String, Object> row = Map.of("revenue", 125.0, "cost", 75.0, "previous", 100.0);
        assertEquals(0.4, CalculatedMeasureExpression.evaluate("margin(revenue, cost)", row), 1e-9);
        assertEquals(1.25, CalculatedMeasureExpression.evaluate("ratio(revenue, previous)", row), 1e-9);
        assertEquals(25.0, CalculatedMeasureExpression.evaluate("periodChange(revenue, previous)", row), 1e-9);
        assertEquals(0.25, CalculatedMeasureExpression.evaluate("periodChangePct(revenue, previous)", row), 1e-9);
    }

    @Test void honorsOperatorPrecedenceAndParentheses() {
        assertEquals(14.0, CalculatedMeasureExpression.evaluate("2 + 3 * 4", Map.of()), 1e-9);
        assertEquals(20.0, CalculatedMeasureExpression.evaluate("(2 + 3) * 4", Map.of()), 1e-9);
    }

    @Test void rejectsUnknownFunctionsIdentifiersAndDivisionByZero() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculatedMeasureExpression.evaluate("Runtime(1)", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> CalculatedMeasureExpression.evaluate("missing + 1", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> CalculatedMeasureExpression.evaluate("ratio(5, 0)", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> CalculatedMeasureExpression.evaluate("1 / 0", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> CalculatedMeasureExpression.evaluate("1; DROP TABLE x", Map.of()));
    }

    @Test void evaluatesRunningTotalInCallerProvidedChronologicalOrder() {
        List<Double> result = CalculatedMeasureExpression.evaluateRunningTotal("amount", List.of(
                Map.of("amount", 2), Map.of("amount", 3.5), Map.of("amount", 4)));
        assertEquals(List.of(2.0, 5.5, 9.5), result);
    }

    @Test void rejectsNonNumericRunningTotalValues() {
        assertThrows(IllegalArgumentException.class, () ->
                CalculatedMeasureExpression.evaluateRunningTotal("amount", List.of(Map.of("amount", "not-a-number"))));
    }
}
