package com.erplancers.erp.common.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Small, deterministic expression language for BI measures. It deliberately does not use
 * SpEL, JavaScript, reflection, SQL fragments, or arbitrary method invocation.
 *
 * Supported operators: + - * / and parentheses.
 * Supported functions: ratio(a,b), margin(revenue,cost), periodChange(current,previous),
 * periodChangePct(current,previous), abs(x), min(a,b), max(a,b).
 * Identifiers resolve only to numeric values supplied by the caller.
 */
public final class CalculatedMeasureExpression {
    private CalculatedMeasureExpression() {}

    public static void validate(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > 1000) {
            throw new IllegalArgumentException("Expression must contain 1 to 1000 characters");
        }
        if (expression.matches("runningTotal\\([A-Za-z_][A-Za-z0-9_]{0,63}\\)")) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z_][A-Za-z0-9_]{0,63}").matcher(expression);
        Map<String, Object> sample = new java.util.HashMap<>();
        java.util.Set<String> functions = java.util.Set.of("ratio", "margin", "periodChange",
                "periodChangePct", "abs", "min", "max");
        while (matcher.find()) {
            String name = matcher.group();
            int after = matcher.end();
            while (after < expression.length() && Character.isWhitespace(expression.charAt(after))) after++;
            if (after < expression.length() && expression.charAt(after) == '(') {
                if (!functions.contains(name)) throw new IllegalArgumentException("Unsupported function '" + name + "'");
            } else {
                sample.put(name, 1.0d);
            }
        }
        evaluate(expression, sample);
    }

    public static double evaluate(String expression, Map<String, ?> values) {
        if (expression == null || expression.isBlank() || expression.length() > 1000) {
            throw new IllegalArgumentException("Expression must contain 1 to 1000 characters");
        }
        Objects.requireNonNull(values, "values");
        Parser parser = new Parser(expression, values);
        double result = parser.expression();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.error("Unexpected token");
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Expression result must be finite");
        return result;
    }

    /** Evaluates a cumulative measure over rows in their supplied, already-sorted order. */
    public static List<Double> evaluateRunningTotal(String field, List<? extends Map<String, ?>> rows) {
        if (field == null || !field.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid running-total field");
        }
        List<Double> result = new ArrayList<>(rows.size());
        double total = 0.0;
        for (Map<String, ?> row : rows) {
            Object value = row.get(field);
            if (!(value instanceof Number number)) throw new IllegalArgumentException("Running-total field must be numeric: " + field);
            total += number.doubleValue();
            if (!Double.isFinite(total)) throw new IllegalArgumentException("Running total overflow");
            result.add(total);
        }
        return List.copyOf(result);
    }

    private static final class Parser {
        private final String input;
        private final Map<String, ?> values;
        private int pos;

        private Parser(String input, Map<String, ?> values) {
            this.input = input;
            this.values = values;
        }

        private double expression() {
            double value = term();
            while (true) {
                skipWhitespace();
                if (take('+')) value += term();
                else if (take('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            double value = unary();
            while (true) {
                skipWhitespace();
                if (take('*')) value *= unary();
                else if (take('/')) {
                    double denominator = unary();
                    if (denominator == 0.0) throw error("Division by zero");
                    value /= denominator;
                } else return value;
            }
        }

        private double unary() {
            skipWhitespace();
            if (take('+')) return unary();
            if (take('-')) return -unary();
            return primary();
        }

        private double primary() {
            skipWhitespace();
            if (take('(')) {
                double value = expression();
                skipWhitespace();
                if (!take(')')) throw error("Expected ')'");
                return value;
            }
            if (!atEnd() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) return number();
            String identifier = identifier();
            skipWhitespace();
            if (take('(')) {
                List<Double> args = new ArrayList<>();
                skipWhitespace();
                if (!take(')')) {
                    do { args.add(expression()); skipWhitespace(); } while (take(','));
                    if (!take(')')) throw error("Expected ')' after function arguments");
                }
                return function(identifier, args);
            }
            Object value = values.get(identifier);
            if (!(value instanceof Number number)) throw error("Unknown or non-numeric measure '" + identifier + "'");
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric)) throw error("Non-finite value for '" + identifier + "'");
            return numeric;
        }

        private double function(String name, List<Double> args) {
            return switch (name) {
                case "ratio" -> { requireArity(name, args, 2); yield divide(args.get(0), args.get(1)); }
                case "margin" -> { requireArity(name, args, 2); yield divide(args.get(0) - args.get(1), args.get(0)); }
                case "periodChange" -> { requireArity(name, args, 2); yield args.get(0) - args.get(1); }
                case "periodChangePct" -> { requireArity(name, args, 2); yield divide(args.get(0) - args.get(1), args.get(1)); }
                case "abs" -> { requireArity(name, args, 1); yield Math.abs(args.get(0)); }
                case "min" -> { requireArity(name, args, 2); yield Math.min(args.get(0), args.get(1)); }
                case "max" -> { requireArity(name, args, 2); yield Math.max(args.get(0), args.get(1)); }
                default -> throw error("Unsupported function '" + name + "'");
            };
        }

        private double divide(double numerator, double denominator) {
            if (denominator == 0.0) throw error("Division by zero");
            return numerator / denominator;
        }

        private void requireArity(String name, List<Double> args, int expected) {
            if (args.size() != expected) throw error("Function " + name + " expects " + expected + " arguments");
        }

        private double number() {
            int start = pos;
            boolean dot = false;
            while (!atEnd()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c)) pos++;
                else if (c == '.' && !dot) { dot = true; pos++; }
                else break;
            }
            try { return Double.parseDouble(input.substring(start, pos)); }
            catch (NumberFormatException ex) { throw error("Invalid numeric literal"); }
        }

        private String identifier() {
            skipWhitespace();
            int start = pos;
            if (atEnd() || !(Character.isLetter(input.charAt(pos)) || input.charAt(pos) == '_')) {
                throw error("Expected a measure name, number, or '('");
            }
            pos++;
            while (!atEnd()) {
                char c = input.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_') pos++;
                else break;
            }
            String name = input.substring(start, pos);
            if (name.length() > 64) throw error("Measure name is too long");
            return name;
        }

        private boolean take(char c) {
            if (!atEnd() && input.charAt(pos) == c) { pos++; return true; }
            return false;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        private boolean atEnd() { return pos >= input.length(); }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}
