import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Dropdown } from "primereact/dropdown";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Column } from "primereact/column";
import { DataTable } from "primereact/datatable";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Message } from "primereact/message";
import { Tag } from "primereact/tag";
import { TabView, TabPanel } from "primereact/tabview";
import { useAuth } from "../auth";
import { api } from "../api";
import type { AnalyticsJobSubmitRequest, AnalyticsJobView } from "../types";

type DatasetKey = "sales_orders" | "inventory_daily" | "order_lines" | "receivables";
type MiningJob = { label: string; value: string; dataset: DatasetKey };
type JsonRecord = Record<string, unknown>;

const DATASETS: Array<{ label: string; value: DatasetKey }> = [
  { label: "Sales orders", value: "sales_orders" },
  { label: "Daily inventory", value: "inventory_daily" },
  { label: "Order lines / baskets", value: "order_lines" },
  { label: "Receivables", value: "receivables" }
];
const MINING_JOBS: MiningJob[] = [
  { label: "Sales trend analysis", value: "SALES_TREND_ANALYSIS", dataset: "sales_orders" },
  { label: "RFM customer analysis", value: "RFM", dataset: "sales_orders" },
  { label: "Cohort retention", value: "COHORT_ANALYSIS", dataset: "sales_orders" },
  { label: "Customer lifetime value", value: "CUSTOMER_LIFETIME_VALUE", dataset: "sales_orders" },
  { label: "Customer segmentation (K-means)", value: "CUSTOMER_SEGMENTATION", dataset: "sales_orders" },
  { label: "Customer churn risk", value: "CUSTOMER_CHURN_RISK", dataset: "sales_orders" },
  { label: "Demand forecast", value: "DEMAND_FORECAST", dataset: "inventory_daily" },
  { label: "Stockout risk", value: "STOCKOUT_RISK", dataset: "inventory_daily" },
  { label: "Inventory anomaly detection", value: "INVENTORY_ANOMALY", dataset: "inventory_daily" },
  { label: "Market-basket rules (FP-Growth)", value: "MARKET_BASKET", dataset: "order_lines" },
  { label: "Product recommendations (ALS)", value: "PRODUCT_RECOMMENDATIONS", dataset: "order_lines" },
  { label: "Receivables risk", value: "RECEIVABLES_RISK", dataset: "receivables" }
];
const MODES = [
  { label: "Group by", value: "GROUP" },
  { label: "Roll-up totals", value: "ROLLUP" },
  { label: "Cube / all subtotals", value: "CUBE" }
];
const INITIAL_PARAMETERS: Record<string, string> = {
  SALES_TREND_ANALYSIS: "{}",
  RFM: JSON.stringify({ asOfDate: new Date().toISOString().slice(0, 10) }, null, 2),
  COHORT_ANALYSIS: "{}",
  CUSTOMER_LIFETIME_VALUE: "{}",
  CUSTOMER_SEGMENTATION: JSON.stringify({ clusters: 5, maxIterations: 100, featureColumns: ["recency_days", "frequency", "monetary_value"] }, null, 2),
  CUSTOMER_CHURN_RISK: JSON.stringify({ trainThroughDate: "2026-09-30", labelColumn: "churn_label", featureColumns: ["net_amount"], algorithm: "RANDOM_FOREST", reviewThreshold: 0.65 }, null, 2),
  DEMAND_FORECAST: JSON.stringify({ trainThroughDate: "2026-09-30" }, null, 2),
  STOCKOUT_RISK: JSON.stringify({ trainThroughDate: "2026-09-30", featureColumns: ["recent_demand", "on_hand", "lead_time_days"], labelColumn: "stockout_label" }, null, 2),
  INVENTORY_ANOMALY: JSON.stringify({ zThreshold: 3 }, null, 2),
  MARKET_BASKET: JSON.stringify({ minSupport: 0.01, minConfidence: 0.25 }, null, 2),
  PRODUCT_RECOMMENDATIONS: JSON.stringify({ topN: 10, rank: 16, maxIterations: 15, regularization: 0.1 }, null, 2),
  RECEIVABLES_RISK: JSON.stringify({ trainThroughDate: "2026-06-30", reviewThreshold: 0.65, featureColumns: ["invoice_amount", "customer_overdue_rate", "historical_average_payment_delay", "payment_terms_days"] }, null, 2)
};

function errorText(error: unknown) { return error instanceof Error ? error.message : String(error); }
function parsePreview(job: AnalyticsJobView | undefined): JsonRecord[] {
  if (!job?.resultJson) return [];
  try {
    const result = JSON.parse(job.resultJson) as { metrics?: { preview?: JsonRecord[] } };
    return Array.isArray(result.metrics?.preview) ? result.metrics.preview : [];
  } catch { return []; }
}
function parseResult(job: AnalyticsJobView | undefined): JsonRecord | null {
  if (!job?.resultJson) return null;
  try { return JSON.parse(job.resultJson) as JsonRecord; } catch { return null; }
}
function statusSeverity(status: string): "success" | "info" | "warning" | "danger" | "secondary" {
  if (status === "SUCCEEDED") return "success";
  if (status === "RUNNING") return "info";
  if (["QUEUED", "CANCEL_REQUESTED"].includes(status)) return "warning";
  if (["FAILED", "CANCELLED"].includes(status)) return "danger";
  return "secondary";
}

export function SparkOlapPage() {
  const { credentials } = useAuth();
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState("");
  const [datasetKey, setDatasetKey] = useState<DatasetKey>("sales_orders");
  const [dimensionsText, setDimensionsText] = useState("order_date");
  const [measuresText, setMeasuresText] = useState("sum:net_amount,count");
  const [filtersText, setFiltersText] = useState("{}");
  const [mode, setMode] = useState("GROUP");
  const [orderBy, setOrderBy] = useState("sum_net_amount:desc");
  const [limit, setLimit] = useState("100");
  const [miningJob, setMiningJob] = useState("RFM");
  const [miningParameters, setMiningParameters] = useState(INITIAL_PARAMETERS.RFM);
  const [selectedJobId, setSelectedJobId] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const jobs = useQuery({
    queryKey: ["spark-olap-jobs", tenantId.trim(), credentials?.username],
    queryFn: () => api.analyticsJobs(tenantId.trim(), credentials!),
    enabled: Boolean(credentials && tenantId.trim()),
    refetchInterval: 5000
  });
  const submit = useMutation({
    mutationFn: (request: AnalyticsJobSubmitRequest) => api.submitAnalyticsJob(request, credentials!),
    onSuccess: async (job) => {
      setSelectedJobId(job.id);
      setNotice("Queued " + job.jobType + " job " + job.id);
      setError("");
      await queryClient.invalidateQueries({ queryKey: ["spark-olap-jobs", tenantId.trim()] });
    },
    onError: (e) => { setError(errorText(e)); setNotice(""); }
  });
  const cancel = useMutation({
    mutationFn: (job: AnalyticsJobView) => api.cancelAnalyticsJob(job.id, tenantId.trim(), credentials!),
    onSuccess: async () => {
      setNotice("Cancellation requested.");
      await queryClient.invalidateQueries({ queryKey: ["spark-olap-jobs", tenantId.trim()] });
    },
    onError: (e) => setError(errorText(e))
  });

  const availableMiningJobs = useMemo(() => MINING_JOBS.filter(job => job.dataset === datasetKey), [datasetKey]);
  const selectedJob = jobs.data?.find(job => job.id === selectedJobId) ?? jobs.data?.[0];
  const previewRows = parsePreview(selectedJob);
  const previewColumns = previewRows.length ? Object.keys(previewRows[0]) : [];
  const selectedResult = parseResult(selectedJob);

  function changeDataset(value: DatasetKey) {
    setDatasetKey(value);
    const nextJob = MINING_JOBS.find(job => job.dataset === value)?.value ?? "RFM";
    setMiningJob(nextJob);
    setMiningParameters(INITIAL_PARAMETERS[nextJob] ?? "{}");
    setSelectedJobId("");
    if (value === "sales_orders") { setDimensionsText("order_date"); setMeasuresText("sum:net_amount,count"); setOrderBy("sum_net_amount:desc"); }
    else if (value === "inventory_daily") { setDimensionsText("warehouse_id"); setMeasuresText("sum:demand_qty,avg:demand_qty,count"); setOrderBy("sum_demand_qty:desc"); }
    else if (value === "order_lines") { setDimensionsText("product_id"); setMeasuresText("countDistinct:transaction_id,count"); setOrderBy("row_count:desc"); }
    else { setDimensionsText("customer_id"); setMeasuresText("sum:invoice_amount,avg:historical_average_payment_delay,count"); setOrderBy("sum_invoice_amount:desc"); }
  }

  function submitOlap() {
    if (!credentials || !tenantId.trim()) { setError("Enter the admin-granted tenant ID first."); return; }
    let filters: Record<string, string>;
    try {
      const parsed = JSON.parse(filtersText || "{}") as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Filters must be a JSON object.");
      filters = parsed as Record<string, string>;
      if (Object.values(filters).some(value => typeof value !== "string")) throw new Error("Filter values must be strings.");
    } catch (e) { setError(errorText(e)); return; }
    const dimensions = dimensionsText.split(",").map(s => s.trim()).filter(Boolean);
    const measures = measuresText.split(",").map(s => s.trim()).filter(Boolean);
    if (!measures.length) { setError("Add at least one measure (for example sum:net_amount,count)."); return; }
    const parsedLimit = Number(limit);
    if (!Number.isInteger(parsedLimit) || parsedLimit < 1 || parsedLimit > 1000) { setError("Limit must be an integer between 1 and 1000."); return; }
    setError("");
    submit.mutate({
      tenantId: tenantId.trim(),
      datasetKey,
      jobType: "SPARK_OLAP",
      parameters: { groupBy: dimensions, measures, filters, mode, orderBy, limit: parsedLimit },
      idempotencyKey: crypto.randomUUID()
    });
  }

  function submitMining() {
    if (!credentials || !tenantId.trim()) { setError("Enter the admin-granted tenant ID first."); return; }
    let parameters: JsonRecord;
    try {
      const parsed = JSON.parse(miningParameters) as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Parameters must be a JSON object.");
      parameters = parsed as JsonRecord;
    } catch (e) { setError(errorText(e)); return; }
    setError("");
    submit.mutate({
      tenantId: tenantId.trim(),
      datasetKey,
      jobType: miningJob,
      parameters,
      idempotencyKey: crypto.randomUUID()
    });
  }

  function refreshJobs() {
    void queryClient.invalidateQueries({ queryKey: ["spark-olap-jobs", tenantId.trim()] });
  }

  return (
    <div className="page-stack" style={{ display: "grid", gap: "1rem" }}>
      <div className="page-heading">
        <div><div className="eyebrow">DISTRIBUTED ANALYTICS</div><h1>Spark OLAP &amp; Data Mining</h1>
          <p className="text-600">Build multidimensional aggregates and queue Spark ML jobs over approved, row-scoped ERP datasets.</p></div>
        <Tag value="Spark 4 · Async jobs" severity="info" />
      </div>

      <Card title="Execution scope" subTitle="Jobs require an administrator-granted tenant and data scope.">
        <div className="olap-options-grid">
          <label>Tenant ID
            <InputText value={tenantId} onChange={e => setTenantId(e.target.value)} placeholder="Admin-granted tenant ID" />
          </label>
          <label>Source dataset
            <Dropdown value={datasetKey} options={DATASETS} optionLabel="label" optionValue="value" onChange={e => changeDataset(e.value as DatasetKey)} className="field-control" />
          </label>
        </div>
        {jobs.isError && <Message severity="error" text={errorText(jobs.error)} className="mt-3" />}
      </Card>

      {error && <Message severity="error" text={error} />}
      {notice && <Message severity="success" text={notice} />}

      <TabView>
        <TabPanel header="Spark OLAP" leftIcon="pi pi-table">
          <div className="olap-builder-grid">
            <Card title="1. Dimensions">
              <label>Comma-separated logical aliases</label>
              <InputText value={dimensionsText} onChange={e => setDimensionsText(e.target.value)} placeholder="region, product_id, order_date" className="w-full" />
              <small className="text-600">Use aliases configured for the selected analytics dataset. Leave empty for a grand total.</small>
            </Card>
            <Card title="2. Measures">
              <label>Aggregation expressions</label>
              <InputText value={measuresText} onChange={e => setMeasuresText(e.target.value)} placeholder="sum:net_amount,avg:net_amount,count" className="w-full" />
              <small className="text-600">Supported: count, sum:field, avg:field, min:field, max:field, countDistinct:field, count:field.</small>
            </Card>
            <Card title="3. Cube operation">
              <label>Aggregation mode</label>
              <Dropdown value={mode} options={MODES} optionLabel="label" optionValue="value" onChange={e => setMode(e.value)} className="field-control" />
              <label className="mt-3">Order by output column</label>
              <InputText value={orderBy} onChange={e => setOrderBy(e.target.value)} placeholder="sum_net_amount:desc" className="w-full" />
              <label className="mt-3">Maximum result rows (1–1000)</label>
              <InputText value={limit} onChange={e => setLimit(e.target.value)} inputMode="numeric" className="w-full" />
            </Card>
            <Card title="4. Filters">
              <label>Equality filters as JSON</label>
              <InputTextarea value={filtersText} onChange={e => setFiltersText(e.target.value)} rows={5} autoResize className="w-full" />
              <small className="text-600">Example: <code>{"{\"region\":\"west\"}"}</code>. Filters apply before aggregation.</small>
            </Card>
          </div>
          <div className="flex justify-content-end mt-3">
            <Button label="Run Spark OLAP" icon="pi pi-play" onClick={submitOlap} loading={submit.isPending} disabled={!tenantId.trim()} />
          </div>
        </TabPanel>

        <TabPanel header="Data mining" leftIcon="pi pi-sitemap">
          <div className="olap-builder-grid">
            <Card title="Mining algorithm">
              <label>Algorithm</label>
              <Dropdown value={miningJob} options={availableMiningJobs} optionLabel="label" optionValue="value"
                onChange={e => { const value = e.value as string; setMiningJob(value); setMiningParameters(INITIAL_PARAMETERS[value] ?? "{}"); }}
                className="field-control" />
              <p className="text-600">Runs Spark ML pipelines including K-means, FP-Growth, ALS, regression/classification and time-series analytics.</p>
            </Card>
            <Card title="Algorithm parameters">
              <label>JSON parameters</label>
              <InputTextarea value={miningParameters} onChange={e => setMiningParameters(e.target.value)} rows={12} autoResize className="w-full" />
              <div className="flex justify-content-end mt-3">
                <Button label="Queue mining job" icon="pi pi-play" onClick={submitMining} loading={submit.isPending} disabled={!tenantId.trim()} />
              </div>
            </Card>
          </div>
        </TabPanel>
      </TabView>

      <Card title="Spark job queue" subTitle="Auto-refreshes every five seconds. Select a job to inspect its metrics and result preview.">
        <div className="flex justify-content-end mb-3">
          <Button label="Refresh" icon="pi pi-refresh" outlined onClick={refreshJobs} disabled={!tenantId.trim()} />
        </div>
        {jobs.isPending && tenantId.trim() && <div className="flex justify-content-center p-4"><i className="pi pi-spin pi-spinner" /></div>}
        {jobs.data && <DataTable value={jobs.data} dataKey="id" paginator rows={8} size="small" stripedRows selectionMode="single"
          selection={selectedJob ?? null} onSelectionChange={e => { const job = e.value as AnalyticsJobView | null; if (job) setSelectedJobId(job.id); }}>
          <Column field="jobType" header="Job" sortable />
          <Column field="datasetKey" header="Dataset" sortable />
          <Column field="status" header="Status" body={(job: AnalyticsJobView) => <Tag value={job.status} severity={statusSeverity(job.status)} />} sortable />
          <Column field="attemptCount" header="Attempts" />
          <Column field="updatedAt" header="Updated" />
          <Column header="Action" body={(job: AnalyticsJobView) => ["QUEUED", "RUNNING"].includes(job.status)
            ? <Button label="Cancel" icon="pi pi-times" severity="danger" text size="small" onClick={() => cancel.mutate(job)} />
            : <span className="text-500">—</span>} />
        </DataTable>}
        {!tenantId.trim() && <Message severity="info" text="Enter a tenant ID to load jobs. Your account must have an analytics tenant grant and a matching CONTACT or WAREHOUSE scope grant." />}
      </Card>

      {selectedJob && <Card title={"Selected job · " + selectedJob.jobType} subTitle={selectedJob.id}>
        <div className="flex gap-2 align-items-center flex-wrap mb-3">
          <Tag value={selectedJob.status} severity={statusSeverity(selectedJob.status)} />
          <span className="text-600">Created {selectedJob.createdAt}</span>
          {selectedResult?.outputPath && <span className="text-600">Output: {String(selectedResult.outputPath)}</span>}
        </div>
        {selectedJob.errorMessage && <Message severity="error" text={selectedJob.errorMessage} />}
        {previewRows.length > 0 && <DataTable value={previewRows} paginator rows={10} size="small" stripedRows scrollable>
          {previewColumns.map(column => <Column key={column} field={column} header={column} sortable />)}
        </DataTable>}
        {selectedResult && <details className="mt-3"><summary>Execution result JSON</summary><pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{JSON.stringify(selectedResult, null, 2)}</pre></details>}
        {!selectedResult && selectedJob.status === "SUCCEEDED" && <Message severity="info" text="This algorithm stores full output as a Spark artifact; this job type does not include an inline preview." />}
      </Card>}
    </div>
  );
}
