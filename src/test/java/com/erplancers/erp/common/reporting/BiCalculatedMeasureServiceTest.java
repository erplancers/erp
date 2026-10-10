package com.erplancers.erp.common.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BiCalculatedMeasureServiceTest {
    private final BiCalculatedMeasureService service = new BiCalculatedMeasureService();

    @Test
    void evaluatesSafeArithmeticAndMargins() {
        var rows = service.apply(List.of(Map.of("revenue", 125, "cost", 75)),
                List.of(new BiCalculatedMeasureService.CalculatedMeasure("margin", "revenue - cost"),
                        new BiCalculatedMeasureService.CalculatedMeasure("margin_pct", "(revenue-cost)/revenue*100")),
                List.of(), List.of());
        assertEquals(new BigDecimal("50"), rows.getFirst().get("margin"));
        assertEquals(0, ((BigDecimal) rows.getFirst().get("margin_pct")).compareTo(new BigDecimal("40")));
    }

    @Test
    void calculatesRunningTotalsAndPeriodDeltas() {
        var source = List.of(Map.<String,Object>of("sales", 10, "prior_sales", 8),
                Map.<String,Object>of("sales", 20, "prior_sales", 10));
        var rows = service.apply(source, List.of(), List.of("sales"),
                List.of(new BiCalculatedMeasureService.PeriodComparison("sales", "sales", "prior_sales")));
        assertEquals(new BigDecimal("10"), rows.get(0).get("sales_running_total"));
        assertEquals(new BigDecimal("30"), rows.get(1).get("sales_running_total"));
        assertEquals(new BigDecimal("2"), rows.get(0).get("sales_change"));
        assertEquals(0, ((BigDecimal) rows.get(0).get("sales_change_pct")).compareTo(new BigDecimal("25")));
    }

    @Test
    void rejectsCodeExecutionAndInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> service.apply(
                List.of(Map.of("sales", 1)),
                List.of(new BiCalculatedMeasureService.CalculatedMeasure("x", "sales; Runtime.exec(1)")),
                List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.apply(
                List.of(Map.of("sales", 1)), List.of(),
                List.of("sales;drop"), List.of()));
    }

    @Test
    void handlesDivisionByZeroAsNullForTheCalculatedCell() {
        var rows = service.apply(List.of(Map.of("sales", 0, "cost", 1)),
                List.of(new BiCalculatedMeasureService.CalculatedMeasure("ratio", "cost/sales")),
                List.of(), List.of());
        assertNull(rows.getFirst().get("ratio"));
    }
}
