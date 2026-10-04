import { useQuery } from "@tanstack/react-query";
import { useParams, Link } from "react-router-dom";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Button } from "primereact/button";
import { ProgressSpinner } from "primereact/progressspinner";
import { Message } from "primereact/message";
import { useAuth } from "../auth";
import { api } from "../api";

export function ResourcePage() {
  const { slug = "" } = useParams();
  const { credentials } = useAuth();
  const [page, setPage] = React.useState(0);
  const query = useQuery({ queryKey: ["rows", slug, page], queryFn: () => api.rows(slug, page, 25, credentials!), enabled: !!credentials && !!slug });
  if (query.isPending) return <ProgressSpinner />;
  if (query.isError) return <Message severity="error" text={query.error.message} />;
  const rows = query.data.content.map(x => x.values ? ({ id: x.id, ...x.values }) : x);
  const columns = Array.from(new Set(rows.flatMap(r => Object.keys(r)))).slice(0, 12);
  return <>
    <div className="section-heading"><div><div className="eyebrow">RESOURCE</div><h2>{slug}</h2></div><Link className="p-button p-component" to={`/bi/${slug}`}>Open BI</Link></div>
    <DataTable value={rows} stripedRows scrollable loading={query.isFetching} emptyMessage="No records">
      {columns.map(c => <Column key={c} field={c} header={c} sortable />)}
    </DataTable>
    <div className="pager"><Button icon="pi pi-angle-left" disabled={page===0} onClick={()=>setPage(p=>p-1)} /><span>Page {page+1} of {Math.max(1,query.data.totalPages)}</span><Button icon="pi pi-angle-right" disabled={page+1>=query.data.totalPages} onClick={()=>setPage(p=>p+1)} /></div>
  </>;
}
