import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { useAuth } from "../auth";
import { api } from "../api";

type Measure = { name: string; expression: string };
type QueryConfig = {
  dimensions: string[]; measures: string[]; filters: Record<string, string>;
  orderBy?: string; limit: number; includeRank: boolean;
  includeShareOfTotal: boolean; includeGrandTotal: boolean;
};

export function BiReportTools({ resourceSlug, query, calculatedMeasures }: {
  resourceSlug: string; query: QueryConfig; calculatedMeasures: Measure[];
}) {
  const { credentials } = useAuth();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [visibility, setVisibility] = useState("PRIVATE");
  const [selectedId, setSelectedId] = useState("");
  const [shareUsers, setShareUsers] = useState("");
  const [email, setEmail] = useState("");
  const [cron, setCron] = useState("0 8 * * 1");
  const [format, setFormat] = useState<"CSV"|"XLSX"|"PDF">("CSV");
  const [error, setError] = useState("");
  const reports = useQuery({ queryKey: ["saved-bi-reports"], queryFn: () => api.savedBiReports(credentials!), enabled: !!credentials });

  async function save() {
    if (!credentials || !name.trim()) return;
    try {
      const report = await api.saveBiReport({ resourceSlug, reportName: name.trim(), visibility, query, calculatedMeasures }, credentials);
      setSelectedId(report.id);
      await queryClient.invalidateQueries({ queryKey: ["saved-bi-reports"] });
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Save failed"); }
  }
  async function share() {
    if (!credentials || !selectedId) return;
    try {
      const grants = shareUsers.split(",").map(x => x.trim()).filter(Boolean).map(username => ({username, permission:"READ" as const}));
      await api.shareBiReport(selectedId, grants, credentials);
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Sharing failed"); }
  }
  async function subscribe() {
    if (!credentials || !selectedId) return;
    try {
      await api.subscribeBiReport(selectedId, {recipientEmail:email, cronExpression:cron, exportFormat:format}, credentials);
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Subscription failed"); }
  }
  async function exportReport(exportFormat: "CSV"|"XLSX"|"PDF") {
    if (!credentials || !selectedId) return;
    try {
      const blob = await api.exportBiReport(selectedId, exportFormat, credentials);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url; anchor.download = (name || "bi-report") + "." + exportFormat.toLowerCase();
      document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Export failed"); }
  }

  return <Card title="Saved reports, sharing and delivery" className="mt-3">
    {error && <Message severity="error" text={error} className="mb-2" />}
    <div className="flex flex-wrap gap-2 align-items-end">
      <label className="flex flex-column gap-1"><span className="text-sm">Report name</span><InputText value={name} onChange={e=>setName(e.target.value)} placeholder="Monthly sales margin" /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Visibility</span><Dropdown value={visibility} options={[{label:"Private",value:"PRIVATE"},{label:"Shared",value:"SHARED"}]} onChange={e=>setVisibility(e.value)} /></label>
      <Button label="Save report" icon="pi pi-save" onClick={save} disabled={!query.dimensions.length || !query.measures.length || !name.trim()} />
      <label className="flex flex-column gap-1"><span className="text-sm">Saved report</span>
        <Dropdown value={selectedId} options={(reports.data ?? []).map(r=>({label:r.reportName+" ("+r.resourceSlug+")",value:r.id}))} onChange={e=>setSelectedId(e.value)} placeholder="Select report" showClear />
      </label>
    </div>
    <div className="flex flex-wrap gap-2 mt-3">
      <Button label="CSV" icon="pi pi-file" outlined onClick={()=>exportReport("CSV")} disabled={!selectedId} />
      <Button label="Excel" icon="pi pi-file-excel" outlined onClick={()=>exportReport("XLSX")} disabled={!selectedId} />
      <Button label="PDF" icon="pi pi-file-pdf" outlined onClick={()=>exportReport("PDF")} disabled={!selectedId} />
    </div>
    <div className="flex flex-wrap gap-2 align-items-end mt-3">
      <label className="flex flex-column gap-1" style={{minWidth:"260px",flex:"1"}}><span className="text-sm">Share with usernames (comma-separated)</span><InputText value={shareUsers} onChange={e=>setShareUsers(e.target.value)} placeholder="analyst1, manager2" /></label>
      <Button label="Update sharing" icon="pi pi-users" outlined onClick={share} disabled={!selectedId} />
    </div>
    <div className="flex flex-wrap gap-2 align-items-end mt-3">
      <label className="flex flex-column gap-1"><span className="text-sm">Email recipient</span><InputText value={email} onChange={e=>setEmail(e.target.value)} placeholder="reports@example.com" /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Cron (UTC)</span><InputText value={cron} onChange={e=>setCron(e.target.value)} /></label>
      <label className="flex flex-column gap-1"><span className="text-sm">Format</span><Dropdown value={format} options={[{label:"CSV",value:"CSV"},{label:"Excel",value:"XLSX"},{label:"PDF",value:"PDF"}]} onChange={e=>setFormat(e.value)} /></label>
      <Button label="Schedule email" icon="pi pi-calendar" outlined onClick={subscribe} disabled={!selectedId || !email.trim()} />
    </div>
    <p className="text-500 text-sm">Scheduled email delivery requires SMTP configuration. Cron expressions use UTC.</p>
  </Card>;
}
