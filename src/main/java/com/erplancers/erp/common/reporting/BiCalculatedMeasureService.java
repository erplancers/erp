package com.erplancers.erp.common.reporting;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Safe calculated-measure evaluator. Expressions are arithmetic only: no SQL,
 * reflection, method calls, scripting, or arbitrary code execution.
 */
@Service
public class BiCalculatedMeasureService {
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    public List<Map<String, Object>> apply(List<Map<String, Object>> source,
                                           List<CalculatedMeasure> measures,
                                           List<String> runningTotals,
                                           List<PeriodComparison> comparisons) {
        List<Map<String, Object>> rows = source.stream()
                .map(LinkedHashMap<String, Object>::new)
                .toList();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mutable = new LinkedHashMap<>(row);
            for (CalculatedMeasure measure : safeMeasures(measures)) {
                try {
                    BigDecimal value = new Parser(measure.expression(), mutable).parse();
                    mutable.put(measure.name(), value);
                } catch (ArithmeticException invalid) {
                    mutable.put(measure.name(), null);
                }
            }
            result.add(mutable);
        }

        for (String measure : runningTotals == null ? List.<String>of() : runningTotals) {
            if (!ALIAS.matcher(measure).matches()) throw new IllegalArgumentException("Invalid running-total measure");
            BigDecimal running = BigDecimal.ZERO;
            for (Map<String, Object> row : result) {
                Object value = row.get(measure);
                if (value instanceof Number n) running = running.add(new BigDecimal(n.toString()));
                row.put(measure + "_running_total", running);
            }
        }
        for (PeriodComparison comparison : comparisons == null ? List.<PeriodComparison>of() : comparisons) {
            if (!ALIAS.matcher(comparison.currentMeasure()).matches()
                    || !ALIAS.matcher(comparison.previousMeasure()).matches()
                    || !ALIAS.matcher(comparison.name()).matches()) {
                throw new IllegalArgumentException("Invalid period comparison identifier");
            }
            for (int index = 0; index < result.size(); index++) {
                Map<String, Object> row = result.get(index);
                BigDecimal current = decimal(row.get(comparison.currentMeasure()));
                boolean previousRowMode = "PREVIOUS_PERIOD".equalsIgnoreCase(comparison.previousMeasure())
                        || "PREVIOUS".equalsIgnoreCase(comparison.previousMeasure());
                BigDecimal previous = previousRowMode
                        ? index == 0 ? null : decimal(result.get(index - 1).get(comparison.currentMeasure()))
                        : decimal(row.get(comparison.previousMeasure()));
                if (current == null || previous == null) {
                    row.put(comparison.name() + "_previous", previous);
                    row.put(comparison.name() + "_change", null);
                    row.put(comparison.name() + "_change_pct", null);
                } else {
                    BigDecimal delta = current.subtract(previous);
                    row.put(comparison.name() + "_previous", previous);
                    row.put(comparison.name() + "_change", delta);
                    row.put(comparison.name() + "_change_pct",
                            previous.signum() == 0 ? null
                                    : delta.multiply(BigDecimal.valueOf(100)).divide(previous, MathContext.DECIMAL64));
                }
            }
        }
        return result;
    }

    private List<CalculatedMeasure> safeMeasures(List<CalculatedMeasure> measures) {
        if (measures == null) return List.of();
        if (measures.size() > 50) throw new IllegalArgumentException("At most 50 calculated measures are allowed");
        for (CalculatedMeasure measure : measures) {
            if (measure == null || !ALIAS.matcher(measure.name()).matches()
                    || measure.expression() == null || measure.expression().length() > 500) {
                throw new IllegalArgumentException("Invalid calculated measure");
            }
        }
        return measures;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number n) {
            try { return new BigDecimal(n.toString()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    public record CalculatedMeasure(String name, String expression) {}
    public record PeriodComparison(String name, String currentMeasure, String previousMeasure) {}

    private static final class Parser {
        private final String input;
        private final Map<String, Object> row;
        private int position;

        private Parser(String input, Map<String, Object> row) {
            this.input = input;
            this.row = row;
        }

        BigDecimal parse() {
            BigDecimal result = expression();
            whitespace();
            if (position != input.length()) throw new IllegalArgumentException("Unexpected expression token");
            return result;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                whitespace();
                if (take('+')) value = value.add(term());
                else if (take('-')) value = value.subtract(term());
                else return value;
            }
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            while (true) {
                whitespace();
                if (take('*')) value = value.multiply(factor());
                else if (take('/')) {
                    BigDecimal divisor = factor();
                    if (divisor.signum() == 0) throw new ArithmeticException("Division by zero");
                    value = value.divide(divisor, MathContext.DECIMAL64);
                } else return value;
            }
        }

        private BigDecimal factor() {
            whitespace();
            if (take('+')) return factor();
            if (take('-')) return factor().negate();
            if (take('(')) {
                BigDecimal value = expression();
                whitespace();
                if (!take(')')) throw new IllegalArgumentException("Missing closing parenthesis");
                return value;
            }
            if (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
                int start = position++;
                while (position < input.length() && (Character.isDigit(input.charAt(position))
                        || input.charAt(position) == '.')) position++;
                return new BigDecimal(input.substring(start, position));
            }
            int start = position;
            while (position < input.length() && (Character.isLetterOrDigit(input.charAt(position))
                    || input.charAt(position) == '_')) position++;
            if (start == position) throw new IllegalArgumentException("Expected number or measure name");
            String name = input.substring(start, position);
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid measure reference");
            Object value = row.get(name);
            if (value == null && name.contains("_")) value = row.get(name.replaceFirst("_", ":"));
            if (!(value instanceof Number number)) throw new IllegalArgumentException("Measure is not numeric: " + name);
            return new BigDecimal(number.toString());
        }

        private boolean take(char c) {
            if (position < input.length() && input.charAt(position) == c) { position++; return true; }
            return false;
        }

        private void whitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }
    }
}
