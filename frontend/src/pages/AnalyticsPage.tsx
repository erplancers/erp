import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Dropdown } from "primereact/dropdown";
import { MultiSelect } from "primereact/multiselect";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Message } from "primereact/message";
import { ProgressSpinner } from "primereact/progressspinner";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Tag } from "primereact/tag";
import { TabView, TabPanel } from "primereact/tabview";
import { useAuth } from "../auth";
import { api } from "../api";
import type {
  AnalyticsJobView, AnalyticsModelView, AnalyticsJobSubmitRequest
} from "../types";

const NUMERIC_TYPES = new Set([
  "byte", "short", "int", "long", "float", "double",
  "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
  "java.lang.Long", "java.lang.Float", "java.lang.Double",
  "java.math.BigDecimal", "java.math.BigInteger"
]);

const DATASET_JOBS: Record<string, string[]> = {
  sales_orders: ["RFM", "COHORT_ANALYSIS", "CUSTOMER_LIFETIME_VALUE", "CUSTOMER_SEGMENTATION", "DATA_DRIFT"],
  inventory_daily: ["DEMAND_FORECAST", "STOCKOUT_RISK", "INVENTORY_ANOMALY", "DATA_DRIFT"],
  order_lines: ["MARKET_BASKET"],
  receivables: ["RECEIVABLES_RISK", "DATA_DRIFT"]
};

const DATASETS = [
  { label: "Sales order reporting view", value: "sales_orders" },
  { label: "Daily inventory reporting view", value: "inventory_daily" },
  { label: "Order-line basket view", value: "order_lines" },
  { label: "Receivables reporting view", value: "receivables" }
];

function initialParameters(job: string): string {
  switch (job) {
    case "RFM": return JSON.stringify({ asOfDate: new Date().toISOString().slice(0, 10) }, null, 2);
    case "CUSTOMER_SEGMENTATION": return JSON.stringify({ clusters: 5, maxIterations: 100, featureColumns: ["recency_days", "frequency", "monetary_value"] }, null, 2);
    case "DEMAND_FORECAST": return JSON.stringify({ trainThroughDate: "2026-09-30" }, null, 2);
    case "STOCKOUT_RISK": return JSON.stringify({ trainThroughDate: "2026-09-30", featureColumns: ["recent_demand", "on_hand", "lead_time_days"], labelColumn: "stockout_label" }, null, 2);
    case "INVENTORY_ANOMALY": return JSON.stringify({ zThreshold: 3 }, null, 2);
    case "MARKET_BASKET": return JSON.stringify({ minSupport: 0.01, minConfidence: 0.25 }, null, 2);
    case "RECEIVABLES_RISK": return JSON.stringify({ trainThroughDate: "2026-06-30", reviewThreshold: 0.65, featureColumns: ["invoice_amount", "customer_overdue_rate", "historical_average_payment_delay", "payment_terms_days"] }, null, 2);
    case "DATA_DRIFT": return JSON.stringify({ modelName: "customer_segmentation", modelVersion: 1, featureColumns: ["recency_days", "frequency", "monetary_value"], alertThreshold: 0.25, asOfDate: new Date().toISOString().slice(0, 10) }, null, 2);
    default: return "{}";
  }
}

function prettyJson(value?: string | null) {
  if (!value) return "—";
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

function statusSeverity(status: string): "success" | "info" | "warning" | "danger" | "secondary" {
  if (["SUCCEEDED", "PRODUCTION", "VALIDATED"].includes(status)) return "success";
  if (["RUNNING", "CANDIDATE"].includes(status)) return "info";
  if (["QUEUED", "CANCEL_REQUESTED", "RETIRED"].includes(status)) return "warning";
  if (["FAILED", "REJECTED"].includes(status)) return "danger";
  return "secondary";
}

function errorText(error: unknown) {
  return error instanceof Error ? error.message : String(error);
}

function AdvancedAnalyticsPanel() {
  const { credentials } = useAuth();
  const cat = useQuery({
    queryKey: ["catalog", credentials?.username],
    queryFn: () => api.catalog(credentials!),
    enabled: !!credentials
  });
  const [slug, setSlug] = useState("");
  const [fields, setFields] = useState<string[]>([]);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const schema = cat.data?.resources.find(r => r.slug === slug);
  const opts = schema?.fields.filter(f => !f.name.includes(".") && NUMERIC_TYPES.has(f.javaType)) || [];

  async function run() {
    if (!slug || fields.length === 0 || !credentials) return;
    setError(null);
    setResult(null);
    try {
      setResult(await api.advanced(slug, { fields, sampleSize: 10000, zScoreThreshold: 3 }, credentials));
    } catch (e) {
      setError(errorText(e));
    }
  }

  if (cat.isPending) return <ProgressSpinner />;
  if (cat.isError) return <Message severity="error" text={errorText(cat.error)} />;
  return (
    <div className="stack" style={{ display: "grid", gap: "1rem" }}>
      <Card>
        <div style={{ display: "flex", flexWrap: "wrap", gap: ".75rem", alignItems: "center" }}>
          <Dropdown value={slug} options={cat.data?.resources || []} optionLabel="slug" optionValue="slug"
            onChange={e => { setSlug(e.value); setFields([]); setResult(null); setError(null); }}
            placeholder="Select ERP resource" style={{ minWidth: 260 }} />
          <MultiSelect value={fields} options={opts} optionLabel="name" optionValue="name"
            onChange={e => setFields(e.value)} placeholder="Numeric fields" disabled={!slug || opts.length === 0}
            display="chip" style={{ minWidth: 280, maxWidth: "100%" }} />
          <Button label="Analyze" icon="pi pi-chart-line" onClick={run} disabled={!slug || fields.length === 0} />
        </div>
      </Card>
      {error && <Message severity="error" text={error} />}
      {result && <Card title="Advanced analytics result"><pre style={{ whiteSpace: "pre-wrap", overflowX: "auto" }}>{JSON.stringify(result, null, 2)}</pre></Card>}
    </div>
  );
}

function SparkMlOperations() {
  const { credentials } = useAuth();
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState("");
  const [datasetKey, setDatasetKey] = useState("sales_orders");
  const [jobType, setJobType] = useState("RFM");
  const [parametersText, setParametersText] = useState(() => initialParameters("RFM"));
  const [idempotencyKey, setIdempotencyKey] = useState(() => `erp-bi-${Date.now()}`);
  const [reviewNote, setReviewNote] = useState("Reviewed validation metrics and feature lineage.");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedModelName, setSelectedModelName] = useState("");

  const jobs = useQuery({
    queryKey: ["spark-analytics-jobs", tenantId, credentials?.username],
    queryFn: () => api.analyticsJobs(tenantId.trim(), credentials!),
    enabled: Boolean(credentials && tenantId.trim())
  });
  const models = useQuery({
    queryKey: ["spark-analytics-models", tenantId, credentials?.username],
    queryFn: () => api.analyticsModels(tenantId.trim(), credentials!, selectedModelName || undefined),
    enabled: Boolean(credentials && tenantId.trim())
  });
  const experiments = useQuery({
    queryKey: ["spark-analytics-experiments", tenantId, credentials?.username],
    queryFn: () => api.analyticsExperiments(tenantId.trim(), credentials!),
    enabled: Boolean(credentials && tenantId.trim())
  });
  const drift = useQuery({
    queryKey: ["spark-analytics-drift", tenantId, credentials?.username, selectedModelName],
    queryFn: () => api.analyticsDrift(tenantId.trim(), credentials!, selectedModelName || undefined),
    enabled: Boolean(credentials && tenantId.trim())
  });

  const availableJobs = useMemo(() => (DATASET_JOBS[datasetKey] || []).map(value => ({ label: value, value })), [datasetKey]);

  const submit = useMutation({
    mutationFn: (request: AnalyticsJobSubmitRequest) => api.submitAnalyticsJob(request, credentials!),
    onSuccess: async result => {
      setError("");
      setNotice(`Queued ${result.jobType} job ${result.id}`);
      setIdempotencyKey(`erp-bi-${Date.now()}`);
      await queryClient.invalidateQueries({ queryKey: ["spark-analytics-jobs", tenantId] });
    },
    onError: e => { setNotice(""); setError(errorText(e)); }
  });
  const cancel = useMutation({
    mutationFn: (job: AnalyticsJobView) => api.cancelAnalyticsJob(job.id, tenantId.trim(), credentials!),
    onSuccess: async result => {
      setNotice(`Cancellation state: ${result.status}`);
      setError("");
      await queryClient.invalidateQueries({ queryKey: ["spark-analytics-jobs", tenantId] });
    },
    onError: e => { setNotice(""); setError(errorText(e)); }
  });

  function changeDataset(next: string) {
    setDatasetKey(next);
    const nextJob = DATASET_JOBS[next]?.[0] || "RFM";
    setJobType(nextJob);
    setParametersText(initialParameters(nextJob));
  }

  function changeJob(next: string) {
    setJobType(next);
    setParametersText(initialParameters(next));
  }

  async function refreshAll() {
    setError("");
    await Promise.all([
      jobs.refetch(), models.refetch(), experiments.refetch(), drift.refetch()
    ]);
  }

  function submitJob() {
    if (!credentials) return;
    setError("");
    setNotice("");
    if (!tenantId.trim()) {
      setError("Enter the tenant ID granted to this account by an administrator.");
      return;
    }
    let parameters: unknown;
    try { parameters = JSON.parse(parametersText); } catch { setError("Parameters must be valid JSON."); return; }
    if (!parameters || typeof parameters !== "object" || Array.isArray(parameters)) {
      setError("Parameters must be a JSON object.");
      return;
    }
    submit.mutate({
      tenantId: tenantId.trim(),
      datasetKey,
      jobType,
      parameters: parameters as Record<string, unknown>,
      idempotencyKey: idempotencyKey.trim() || undefined
    });
  }

  async function modelAction(action: "approve" | "reject" | "promote" | "rollback", model: AnalyticsModelView) {
    if (!credentials || !tenantId.trim()) return;
    setError(""); setNotice("");
    try {
      let result: AnalyticsModelView;
      if (action === "approve" || action === "reject") {
        result = await api.reviewAnalyticsModel(tenantId.trim(), model.modelName, model.version,
          action === "approve", reviewNote, credentials);
      } else if (action === "promote") {
        result = await api.promoteAnalyticsModel(tenantId.trim(), model.modelName, model.version, reviewNote, credentials);
      } else {
        result = await api.rollbackAnalyticsModel(tenantId.trim(), model.modelName, model.version, reviewNote, credentials);
      }
      setNotice(`Model ${result.modelName} v${result.version}: ${result.status}`);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["spark-analytics-models", tenantId] }),
        queryClient.invalidateQueries({ queryKey: ["spark-analytics-drift", tenantId] })
      ]);
    } catch (e) {
      setError(errorText(e));
    }
  }

  const actionCell = (model: AnalyticsModelView) => (
    <div style={{ display: "flex", flexWrap: "wrap", gap: ".35rem" }}>
      {model.status === "CANDIDATE" && <>
        <Button label="Validate" icon="pi pi-check" size="small" onClick={() => modelAction("approve", model)} />
        <Button label="Reject" icon="pi pi-times" size="small" severity="danger" outlined onClick={() => modelAction("reject", model)} />
      </>}
      {model.status === "VALIDATED" && <Button label="Promote" icon="pi pi-upload" size="small" onClick={() => modelAction("promote", model)} />}
      {model.status === "RETIRED" && <Button label="Rollback" icon="pi pi-history" size="small" severity="warning" onClick={() => modelAction("rollback", model)} />}
    </div>
  );

  return (
    <div style={{ display: "grid", gap: "1rem" }}>
      <Card title="Tenant and job controls" subTitle="Spark runs in a separate worker; API requests only enqueue durable jobs.">
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: ".75rem", alignItems: "end" }}>
          <label style={{ display: "grid", gap: ".35rem" }}>
            <span>Tenant ID</span>
            <InputText value={tenantId} onChange={e => setTenantId(e.target.value)} placeholder="Admin-granted tenant ID" />
          </label>
          <label style={{ display: "grid", gap: ".35rem" }}>
            <span>Dataset</span>
            <Dropdown value={datasetKey} options={DATASETS} optionLabel="label" optionValue="value" onChange={e => changeDataset(e.value)} />
          </label>
          <label style={{ display: "grid", gap: ".35rem" }}>
            <span>Job type</span>
            <Dropdown value={jobType} options={availableJobs} optionLabel="label" optionValue="value" onChange={e => changeJob(e.value)} />
          </label>
          <label style={{ display: "grid", gap: ".35rem" }}>
            <span>Idempotency key</span>
            <InputText value={idempotencyKey} onChange={e => setIdempotencyKey(e.target.value)} maxLength={160} />
          </label>
        </div>
        <label style={{ display: "grid", gap: ".35rem", marginTop: "1rem" }}>
          <span>Job parameters (JSON)</span>
          <InputTextarea value={parametersText} onChange={e => setParametersText(e.target.value)} rows={8} autoResize
            style={{ width: "100%", fontFamily: "monospace" }} />
        </label>
        <div style={{ display: "flex", flexWrap: "wrap", gap: ".5rem", marginTop: ".75rem" }}>
          <Button label={submit.isPending ? "Queueing…" : "Queue job"} icon="pi pi-play" onClick={submitJob}
            disabled={!credentials || submit.isPending || !tenantId.trim()} />
          <Button label="Refresh platform" icon="pi pi-refresh" outlined onClick={refreshAll} disabled={!tenantId.trim()} />
        </div>
      </Card>

      <Message severity="info" text="The source catalogue and tenant/contact/warehouse grants must be configured by an administrator. Receivables risk is decision support only; it never automatically changes credit terms or blocks an order." />
      {error && <Message severity="error" text={error} />}
      {notice && <Message severity="success" text={notice} />}

      <Card title="Job queue" subTitle="Durable jobs, retries, cancellation and last-known results. Refresh to reload the latest state.">
        {jobs.isError && <Message severity="error" text={errorText(jobs.error)} />}
        {jobs.isLoading ? <ProgressSpinner /> : (
          <DataTable value={jobs.data || []} dataKey="id" paginator rows={8} size="small" emptyMessage="No analytics jobs for this tenant/account yet.">
            <Column field="createdAt" header="Created" body={(row: AnalyticsJobView) => new Date(row.createdAt).toLocaleString()} />
            <Column field="jobType" header="Job" />
            <Column field="datasetKey" header="Dataset" />
            <Column field="status" header="Status" body={(row: AnalyticsJobView) => <Tag value={row.status} severity={statusSeverity(row.status)} />} />
            <Column field="attemptCount" header="Attempts" body={(row: AnalyticsJobView) => `${row.attemptCount}/${row.maxAttempts}`} />
            <Column header="Details" body={(row: AnalyticsJobView) => (
              <details><summary>View</summary><pre style={{ whiteSpace: "pre-wrap", maxWidth: 360 }}>{prettyJson(row.resultJson || row.errorMessage)}</pre></details>
            )} />
            <Column header="Action" body={(row: AnalyticsJobView) =>
              ["QUEUED", "RUNNING"].includes(row.status)
                ? <Button label="Cancel" icon="pi pi-stop" size="small" severity="danger" outlined
                    loading={cancel.isPending} onClick={() => cancel.mutate(row)} />
                : "—"
            } />
          </DataTable>
        )}
      </Card>

      <Card title="Model registry" subTitle="Candidate → validated → production; promotion and rollback require separate ERP policy permissions.">
        <div style={{ display: "flex", flexWrap: "wrap", gap: ".5rem", alignItems: "center", marginBottom: ".75rem" }}>
          <InputText value={reviewNote} onChange={e => setReviewNote(e.target.value)} placeholder="Review/promotion note" style={{ minWidth: 280 }} />
          <Dropdown value={selectedModelName} options={[{ label: "All models", value: "" }, ...Array.from(new Set((models.data || []).map(m => m.modelName))).map(name => ({ label: name, value: name }))]}
            onChange={e => setSelectedModelName(e.value)} placeholder="Filter model" />
        </div>
        {models.isError && <Message severity="error" text={errorText(models.error)} />}
        {models.isLoading ? <ProgressSpinner /> : (
          <DataTable value={models.data || []} dataKey="id" paginator rows={6} size="small" emptyMessage="No model versions visible for this tenant/account.">
            <Column field="modelName" header="Model" />
            <Column field="version" header="Version" />
            <Column field="status" header="Lifecycle" body={(row: AnalyticsModelView) => <Tag value={row.status} severity={statusSeverity(row.status)} />} />
            <Column field="createdAt" header="Created" body={(row: AnalyticsModelView) => new Date(row.createdAt).toLocaleString()} />
            <Column header="Metrics" body={(row: AnalyticsModelView) => (
              <details><summary>Inspect</summary><pre style={{ whiteSpace: "pre-wrap", maxWidth: 380 }}>{prettyJson(row.metricsJson)}</pre></details>
            )} />
            <Column header="Lifecycle action" body={actionCell} />
          </DataTable>
        )}
      </Card>

      <Card title="Experiments" subTitle="Persisted run parameters, metrics, and input lineage.">
        {experiments.isError && <Message severity="error" text={errorText(experiments.error)} />}
        <DataTable value={experiments.data || []} dataKey="id" paginator rows={5} size="small" emptyMessage="No experiment runs available.">
          <Column field="createdAt" header="Created" body={(row: any) => new Date(row.createdAt).toLocaleString()} />
          <Column field="runName" header="Run" />
          <Column field="algorithm" header="Algorithm" />
          <Column field="status" header="Status" />
          <Column header="Metrics" body={(row: any) => <details><summary>Inspect</summary><pre>{prettyJson(row.metricsJson)}</pre></details>} />
        </DataTable>
      </Card>

      <Card title="Feature drift" subTitle="Numeric-feature population stability index (PSI); threshold requires per-feature calibration.">
        {drift.isError && <Message severity="error" text={errorText(drift.error)} />}
        <DataTable value={drift.data || []} dataKey="id" paginator rows={8} size="small" emptyMessage="No drift measurements recorded yet.">
          <Column field="measuredAt" header="Measured" body={(row: any) => new Date(row.measuredAt).toLocaleString()} />
          <Column field="modelName" header="Model" />
          <Column field="modelVersion" header="Version" />
          <Column field="featureName" header="Feature" />
          <Column field="psi" header="PSI" body={(row: any) => Number(row.psi).toFixed(4)} />
          <Column field="sampleSize" header="Rows" />
          <Column field="status" header="Status" body={(row: any) => <Tag value={row.status} severity={row.status === "ALERT" ? "danger" : statusSeverity(row.status)} />} />
        </DataTable>
      </Card>
    </div>
  );
}

export function AnalyticsPage() {
  return (
    <>
      <div className="section-heading">
        <div>
          <div className="eyebrow">BUSINESS INTELLIGENCE</div>
          <h2>Analytics & ML Platform</h2>
          <p style={{ margin: 0, color: "var(--text-color-secondary)" }}>ERP data mining, Spark ML jobs, model governance and monitoring.</p>
        </div>
      </div>
      <TabView>
        <TabPanel header="Advanced Analytics"><AdvancedAnalyticsPanel /></TabPanel>
        <TabPanel header="Spark ML Operations"><SparkMlOperations /></TabPanel>
      </TabView>
    </>
  );
}
