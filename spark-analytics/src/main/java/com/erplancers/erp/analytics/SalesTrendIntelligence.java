package com.erplancers.erp.analytics;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import static org.apache.spark.sql.functions.*;

/**
 * Calendar-complete executive sales trends. Missing months are retained as zero-sales months,
 * so month-over-month and year-over-year comparisons use calendar periods rather than row offsets
 * over only observed months.
 */
public final class SalesTrendIntelligence {
    private SalesTrendIntelligence() {}

    public static Dataset<Row> monthlyTrends(
            Dataset<Row> orders, String orderId, String orderDate, String netAmount) {
        SalesIntelligence.requireColumns(orders, orderId, orderDate, netAmount);

        Dataset<Row> monthly = orders
                .filter(col(orderId).isNotNull()
                        .and(col(orderDate).isNotNull())
                        .and(col(netAmount).isNotNull()))
                .withColumn("_month", trunc(to_date(col(orderDate)), "month"))
                .filter(col("_month").isNotNull())
                .groupBy(col("_month"))
                .agg(
                        sum(col(netAmount).cast("double")).alias("revenue"),
                        countDistinct(col(orderId)).alias("order_count"));

        Dataset<Row> bounds = monthly.agg(
                min(col("_month")).alias("first_month"),
                max(col("_month")).alias("last_month"));

        Dataset<Row> calendar = bounds
                .filter(col("first_month").isNotNull())
                .select(explode(sequence(col("first_month"), col("last_month"),
                        expr("INTERVAL 1 MONTH"))).alias("month"));

        Dataset<Row> complete = calendar.join(monthly,
                        calendar.col("month").equalTo(monthly.col("_month")), "left")
                .select(calendar.col("month").alias("month"),
                        coalesce(monthly.col("revenue"), lit(0.0)).alias("revenue"),
                        coalesce(monthly.col("order_count"), lit(0L)).alias("order_count"));

        WindowSpec chronological = Window.orderBy(col("month"));
        WindowSpec trailingThree = chronological.rowsBetween(-2, 0);

        Dataset<Row> comparisons = complete
                .withColumn("average_order_value",
                        when(col("order_count").gt(0),
                                col("revenue").divide(col("order_count"))))
                .withColumn("prior_month_revenue", lag(col("revenue"), 1).over(chronological))
                .withColumn("mom_change", col("revenue").minus(col("prior_month_revenue")))
                .withColumn("mom_growth_pct",
                        when(col("prior_month_revenue").isNotNull()
                                        .and(abs(col("prior_month_revenue")).gt(0.0)),
                                col("mom_change").divide(abs(col("prior_month_revenue"))).multiply(100.0)))
                .withColumn("trailing_3_month_avg_revenue", avg(col("revenue")).over(trailingThree))
                .withColumn("prior_year_revenue", lag(col("revenue"), 12).over(chronological))
                .withColumn("yoy_growth_pct",
                        when(col("prior_year_revenue").isNotNull()
                                        .and(abs(col("prior_year_revenue")).gt(0.0)),
                                col("revenue").minus(col("prior_year_revenue"))
                                        .divide(abs(col("prior_year_revenue"))).multiply(100.0)));

        return comparisons.select(
                col("month"), col("revenue"), col("order_count"), col("average_order_value"),
                col("prior_month_revenue"), col("mom_change"), col("mom_growth_pct"),
                col("trailing_3_month_avg_revenue"), col("prior_year_revenue"), col("yoy_growth_pct"))
                .orderBy(col("month"));
    }
}
