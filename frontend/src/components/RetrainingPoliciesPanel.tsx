import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Message } from "primereact/message";
import { Tag } from "primereact/tag";
import { useAuth } from "../auth";
import { api } from "../api";

const DATASETS = [
  {label:"Sales orders",value:"sales_orders"},
  {label:"Daily inventory",value:"inventory_daily"},
  {label:"Receivables",value:"receivables"}
];
const JOBS: Record<string,Array<{label:string;value:string}>> = {
  sales_orders:[{label:"Customer segmentation",value:"CUSTOMER_SEGMENTATION"},{label:"Customer churn risk",value:"CUSTOMER_CHURN_RISK"},{label:"Scheduled batch scoring",value:"MODEL_SCORING"}],
  inventory_daily:[{label:"Demand forecast",value:"DEMAND_FORECAST"},{label:"Stockout risk",value:"STOCKOUT_RISK"},{label:"Scheduled batch scoring",value:"MODEL_SCORING"}],
  receivables:[{label:"Receivables risk",value:"RECEIVABLES_RISK"},{label:"Scheduled batch scoring",value:"MODEL_SCORING"}]
};
function defaultsFor(job:string, dataset:string):string {
  if(job==="MODEL_SCORING") {
    const modelName=dataset==="inventory_daily"?"demand_forecast":dataset==="receivables"?"receivables_risk":"customer_segmentation";
    const params:Record<string,unknown>={modelName,modelVersion:1};
    if(dataset==="sales_orders") params.asOfDate=new Date().toISOString().slice(0,10);
    if(dataset==="receivables") params.reviewThreshold=0.65;
    return JSON.stringify(params);
  }
  return DEFAULTS[job] || "{}";
}
const DEFAULTS: Record<string,string> = {
  CUSTOMER_SEGMENTATION:'{"clusters":5,"maxIterations":100,"featureColumns":["recency_days","frequency","monetary_value"]}',
  CUSTOMER_CHURN_RISK:'{"trainThroughDate":"2026-09-30","labelColumn":"churn_label","featureColumns":["net_amount"],"algorithm":"RANDOM_FOREST","reviewThreshold":0.65}',
  DEMAND_FORECAST:'{"trainThroughDate":"2026-09-30"}',
  STOCKOUT_RISK:'{"trainThroughDate":"2026-09-30","featureColumns":["recent_demand","on_hand","lead_time_days"],"labelColumn":"stockout_label"}',
  RECEIVABLES_RISK:'{"trainThroughDate":"2026-06-30","reviewThreshold":0.65,"featureColumns":["invoice_amount","customer_overdue_rate","historical_average_payment_delay","payment_terms_days"]}'
};

export function RetrainingPoliciesPanel({tenantId}:{tenantId:string}) {
  const {credentials}=useAuth();
  const qc=useQueryClient();
  const [modelName,setModelName]=useState("customer_segmentation");
  const [datasetKey,setDatasetKey]=useState("sales_orders");
  const [jobType,setJobType]=useState("CUSTOMER_SEGMENTATION");
  const [parameters,setParameters]=useState(DEFAULTS.CUSTOMER_SEGMENTATION);
  const [cron,setCron]=useState("0 0 3 * * SUN");
  const [minDays,setMinDays]=useState("7");
  const [enabled,setEnabled]=useState(true);
  const [error,setError]=useState("");
  const policies=useQuery({queryKey:["analytics-retraining-policies",tenantId,credentials?.username],
    queryFn:()=>api.retrainingPolicies(tenantId,credentials!),enabled:!!credentials&&!!tenantId.trim()});

  async function save() {
    if(!credentials||!tenantId.trim()) return;
    try {
      const parsed=JSON.parse(parameters);
      await api.saveRetrainingPolicy({tenantId:tenantId.trim(),modelName,enabled,datasetKey,jobType,
        parameters:parsed,cronExpression:cron,minDaysBetweenRuns:Number(minDays)},credentials);
      await qc.invalidateQueries({queryKey:["analytics-retraining-policies",tenantId]});
      setError("");
    } catch(e) { setError(e instanceof Error?e.message:"Unable to save policy; verify parameters JSON."); }
  }

  return <Card title="Scheduled model jobs" subTitle="Schedule retraining or batch scoring into the durable Spark queue. Use only validated dataset feature names.">
    {!tenantId.trim() && <Message severity="info" text="Enter a tenant ID above to manage retraining policies." />}
    {error && <Message severity="error" text={error} />}
    <div className="flex flex-wrap gap-2 align-items-end mb-3">
      <label className="flex flex-column gap-1"><span className="text-sm">Model name</span><InputText value={modelName} onChange={e=>setModelName(e.target.value)} /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Dataset</span><Dropdown value={datasetKey} options={DATASETS} onChange={e=>{
        const dataset=e.value as string; const job=JOBS[dataset][0].value;
        setDatasetKey(dataset); setJobType(job); setParameters(defaultsFor(job,dataset));
      }} /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Training job</span><Dropdown value={jobType} options={JOBS[datasetKey]||[]} onChange={e=>{setJobType(e.value);setParameters(defaultsFor(e.value,datasetKey));}} /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Cron (UTC)</span><InputText value={cron} onChange={e=>setCron(e.target.value)} /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Minimum days between runs</span><InputText value={minDays} onChange={e=>setMinDays(e.target.value)} /></label>
      <Button label={enabled?"Policy enabled":"Policy disabled"} icon={enabled?"pi pi-check":"pi pi-ban"} severity={enabled?"success":"secondary"} outlined onClick={()=>setEnabled(!enabled)} />
      <Button label="Save policy" icon="pi pi-save" onClick={save} disabled={!tenantId.trim()||!modelName.trim()||!Number.isInteger(Number(minDays))||Number(minDays)<1||Number(minDays)>365} />
    </div>
    <label className="flex flex-column gap-1 mb-3"><span className="text-sm">Training parameters (JSON)</span><InputTextarea value={parameters} onChange={e=>setParameters(e.target.value)} rows={4} autoResize /></label>
    {policies.isError && <Message severity="error" text={policies.error.message} />}
    <div className="flex flex-wrap gap-2">{(policies.data||[]).map(p=><Tag key={p.tenantId+"-"+p.modelName}
      value={p.modelName+" · "+p.jobType+" · "+(p.enabled?"enabled":"disabled")+" · "+p.cronExpression}
      severity={p.enabled?"success":"secondary"} />)}</div>
  </Card>;
}
