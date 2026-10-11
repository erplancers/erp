package com.erplancers.erp.analytics;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/**
 * Source adapter for analytics jobs. The embedded ERP implementation uses JPA;
 * the standalone worker may provide a separate adapter.
 */
public interface AnalyticsSourceReader {
    Dataset<Row> read(AnalyticsDatasetCatalog.DatasetDefinition definition,
                      AnalyticsJobStore.Job job) throws Exception;

    String scopeFingerprint(AnalyticsDatasetCatalog.DatasetDefinition definition,
                            AnalyticsJobStore.Job job) throws Exception;
}
