import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { Card } from "primereact/card";
import { Dropdown } from "primereact/dropdown";
import { MultiSelect } from "primereact/multiselect";
import { Button } from "primereact/button";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Message } from "primereact/message";
import { ProgressSpinner } from "primereact/progressspinner";
import { Chart } from "primereact/chart";
import { useAuth } from "../auth";
import { api } from "../api";

export function BiPage() {
  const { slug = "" } = useParams();
  const { credentials } = useAuth();
  const [dimension, setDimension] = useState("");
  const [measures, setMeasures] = useState<string[]>(["count"]);
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const catalog = useQuery({ queryKey: ["catalog"], queryFn: () => api.catalog(credentials!), enabled: !!credentials });
  const schema = catalog.data?.resources.find(r => r.slug === slug);
  const options = useMemo(() => schema?.fields.filter(f => !["id","version"].includes(f.name)) ?? [], [schema]);

  async function execute() {
    if (!dimension) return;
    setLoading(true);
    try {
      setResult(await api.biQuery(slug, {
        dimensions: [dimension],
        measures,
        filters: {},
        orderBy: measures[0] ? `${measures[0]}:desc` : "",
        limit: 100
      }, credentials!));
    } finally {
      setLoading(false);
    }
  }

  if (catalog.isPending) return <ProgressSpinner />;
  if (catalog.isError) return <Message severity="error" text={catalog.error.message} />;

  const rows = result?.rows ?? [];
  const chartData = result ? {
    labels: rows.slice(0, 12).map((r: Record<string, unknown>) => String(r[dimension] ?? "Unknown")),
    datasets: [{ label: measures[0], data: rows.slice(0, 12).map((r: Record<string, unknown>) => Number(r[measures[0]] ?? 0)) }]
  } : undefined;

  return <>
    <div className="section-heading"><div><div className="eyebrow">BUSINESS INTELLIGENCE</div><h2>{slug}</h2><p>Interactive aggregation, ranking and drill-down.</p></div></div>
    <Card className="bi-toolbar">
      <label>Dimension<Dropdown value={dimension} options={options} optionLabel="name" optionValue="name" onChange={e=>setDimension(e.value)} placeholder="Select dimension" /></label>
      <label>Measures<MultiSelect value={measures} options={["count", ...options.filter(o=>/Number|BigDecimal|Long|Integer|Double|Float/.test(o.javaType)).map(o=>`sum:${o.name}`)]} onChange={e=>setMeasures(e.value)} /></label>
      <Button label="Run analysis" icon="pi pi-play" onClick={execute} disabled={!dimension} loading={loading} />
    </Card>
    {result && <div className="bi-grid"><Card title="Distribution"><Chart type="bar" data={chartData} options={{responsive:true,plugins:{legend:{display:false}}}} /></Card><Card title="Result set"><DataTable value={rows} stripedRows paginator rows={15}><Column field={dimension} header={dimension}/>{measures.map(m=><Column key={m} field={m} header={m}/>)}</DataTable></Card></div>}
  </>;
}
