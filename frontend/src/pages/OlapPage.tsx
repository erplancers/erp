import { useQuery } from "@tanstack/react-query"; import { useState } from "react"; import { Dropdown } from "primereact/dropdown"; import { Button } from "primereact/button"; import { Card } from "primereact/card"; import { DataTable } from "primereact/datatable"; import { Column } from "primereact/column"; import { ProgressSpinner } from "primereact/progressspinner"; import { Message } from "primereact/message"; import { useAuth } from "../auth"; import { api } from "../api";
export function OlapPage(){
 const {credentials}=useAuth(); const cat=useQuery({queryKey:["olap-catalog"],queryFn:()=>api.olapCatalog(credentials!),enabled:!!credentials}); const [slug,setSlug]=useState(""); const [result,setResult]=useState<any>(null); const [error,setError]=useState("");
 const resources=((cat.data as any)?.resources||[]).map((r:any)=>({label:r.resource,value:r.resource}));
 async function run(){try{setError("");setResult(await api.olapCube(slug,{dimensions:[],measures:["count"],filters:{},limit:100},credentials!));}catch(e){setError(e instanceof Error?e.message:String(e));}}
 if(cat.isPending)return <ProgressSpinner/>; if(cat.isError)return <Message severity="error" text={cat.error.message}/>;
 const rows=result?.cells||[]; const cols=rows.length?Object.keys(rows[0]):[];
 return <><div className="section-heading"><div><div className="eyebrow">OLAP</div><h2>OLAP Explorer</h2><p>Cube, totals and drill-down endpoints.</p></div></div>
 <Card><Dropdown value={slug} options={resources} onChange={e=>setSlug(e.value)} placeholder="Resource" filter/><Button label="Run cube" icon="pi pi-table" onClick={run} disabled={!slug}/></Card>
 {error&&<Message severity="error" text={error}/>}
 {result&&<DataTable value={rows} paginator rows={20}>{cols.map(c=><Column key={c} field={c} header={c}/>)}</DataTable>}</>;
}