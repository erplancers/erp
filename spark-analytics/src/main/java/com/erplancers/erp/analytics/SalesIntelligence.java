package com.erplancers.erp.analytics;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Objects;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import static org.apache.spark.sql.functions.*;

/** RFM, cohort retention and clearly-labelled historical/annualized CLV analytics. */
public final class SalesIntelligence {
    private SalesIntelligence() {}

    public static Dataset<Row> rfm(
            Dataset<Row> orders, String customer, String orderId, String orderDate,
            String netAmount, LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate");
        requireColumns(orders, customer, orderId, orderDate, netAmount);
        return orders.filter(col(customer).isNotNull()
                        .and(col(orderId).isNotNull())
                        .and(col(orderDate).isNotNull())
                        .and(col(netAmount).isNotNull())
                        .and(to_date(col(orderDate)).leq(lit(Date.valueOf(asOfDate)))))
                .groupBy(col(customer))
                .agg(
                        datediff(lit(Date.valueOf(asOfDate)), max(to_date(col(orderDate))))
                                .alias("recency_days"),
                        countDistinct(col(orderId)).alias("frequency"),
                        sum(col(netAmount)).alias("monetary_value"),
                        min(to_date(col(orderDate))).alias("first_order_date"),
                        max(to_date(col(orderDate))).alias("last_order_date"))
                .withColumn("active_months",
                        greatest(lit(1.0), months_between(col("last_order_date"), col("first_order_date"))))
                .withColumn("historical_annualized_clv",
                        col("monetary_value").divide(col("active_months")).multiply(lit(12.0)))
                .withColumn("clv_method", lit("historical annualized revenue; not a predictive model"));
    }

    /** Cohort month × months-since-first-purchase with active customer counts. */
    public static Dataset<Row> cohorts(
            Dataset<Row> orders, String customer, String orderDate) {
        requireColumns(orders, customer, orderDate);
        Dataset<Row> valid = orders.filter(col(customer).isNotNull().and(col(orderDate).isNotNull()))
                .withColumn("_purchase_month", trunc(to_date(col(orderDate)), "month"));
        Dataset<Row> first = valid.groupBy(col(customer))
                .agg(min(col("_purchase_month")).alias("cohort_month"));
        Dataset<Row> cohortSizes = first.groupBy(col("cohort_month"))
                .agg(countDistinct(col(customer)).alias("cohort_size"));
        Dataset<Row> monthlyActive = valid.join(first, customer)
                .withColumn("months_since_cohort",
                        greatest(lit(0), months_between(col("_purchase_month"), col("cohort_month")).cast("int")))
                .groupBy(col("cohort_month"), col("months_since_cohort"))
                .agg(countDistinct(col(customer)).alias("active_customers"));
        return monthlyActive.join(cohortSizes, "cohort_month")
                .withColumn("retention_rate",
                        col("active_customers").cast("double").divide(col("cohort_size")))
                .orderBy(col("cohort_month"), col("months_since_cohort"));
    }

    /** Customer-level historical revenue and a transparent annualized run-rate estimate. */
    public static Dataset<Row> customerLifetimeValue(
            Dataset<Row> orders, String customer, String orderId, String orderDate, String netAmount) {
        requireColumns(orders, customer, orderId, orderDate, netAmount);
        return orders.filter(col(customer).isNotNull().and(col(orderId).isNotNull())
                        .and(col(orderDate).isNotNull()).and(col(netAmount).isNotNull()))
                .groupBy(col(customer))
                .agg(sum(col(netAmount)).alias("historical_revenue"),
                        countDistinct(col(orderId)).alias("lifetime_orders"),
                        min(to_date(col(orderDate))).alias("first_order_date"),
                        max(to_date(col(orderDate))).alias("last_order_date"))
                .withColumn("observed_months",
                        greatest(lit(1.0), months_between(col("last_order_date"), col("first_order_date"))))
                .withColumn("annualized_revenue_estimate",
                        col("historical_revenue").divide(col("observed_months")).multiply(lit(12.0)))
                .withColumn("estimate_method", lit("annualized observed revenue; excludes retention/margin discounting"));
    }


    static Dataset<Row> selectColumns(Dataset<Row> data, String... columns) {
        if (columns == null || columns.length == 0) throw new IllegalArgumentException("At least one column is required");
        return data.select(java.util.Arrays.stream(columns)
                .map(org.apache.spark.sql.functions::col)
                .toArray(org.apache.spark.sql.Column[]::new));
    }

    static void requireColumns(Dataset<Row> data, String... columns) {
        if (data == null) throw new IllegalArgumentException("Dataset is required");
        java.util.Set<String> available = java.util.Set.of(data.columns());
        for (String name : columns) {
            if (name == null || name.isBlank() || !available.contains(name)) {
                throw new IllegalArgumentException("Missing dataset column: " + name);
            }
        }
    }
}
