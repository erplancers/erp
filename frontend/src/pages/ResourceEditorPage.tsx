import { useEffect,useMemo,useState } from "react";
import { useNavigate,useParams } from "react-router-dom";
import { useQuery,useMutation,useQueryClient } from "@tanstack/react-query";
import { InputText } from "primereact/inputtext"; import { InputTextarea } from "primereact/inputtextarea"; import { Dropdown } from "primereact/dropdown"; import { MultiSelect } from "primereact/multiselect"; import { Button } from "primereact/button"; import { Card } from "primereact/card"; import { Message } from "primereact/message"; import { ProgressSpinner } from "primereact/progressspinner";
import { useAuth } from "../auth"; import { api } from "../api";

export function ResourceEditorPage(){
 const {slug="",id}=useParams(); const edit=!!id; const {credentials}=useAuth(); const nav=useNavigate(); const qc=useQueryClient(); const [values,setValues]=useState<Record<string,string>>({});
 const meta=useQuery({queryKey:["metadata",slug,id],queryFn:()=>api.metadata(slug,id,credentials!),enabled:!!credentials&&!!slug});
 useEffect(()=>{if(meta.data){const v={...(meta.data.values||{})}; const selected=meta.data.selected||{}; Object.entries(selected).forEach(([k,x])=>v[k]=Array.isArray(x)?x.join(","):String(x)); setValues(v as Record<string,string>);}},[meta.data]);
 const mutation=useMutation({mutationFn:()=>edit?api.update(slug,id!,values,credentials!):api.create(slug,values,credentials!),onSuccess:()=>{qc.invalidateQueries({queryKey:["rows",slug]});nav(`/resources/${slug}`);}});
 const fields=meta.data?.fields as Record<string,string>||{}; const refs=meta.data?.references||[]; const collections=meta.data?.collections||[];
 const refMap=useMemo(()=>new Map((refs as any[]).map(r=>[r.name,r])),[refs]); const options=meta.data?.options||{};
 if(meta.isPending)return <ProgressSpinner/>; if(meta.isError)return <Message severity="error" text={meta.error.message}/>;
 function renderField(name:string,type:string){
   const value=values[name]??""; const set=(v:any)=>setValues(x=>({...x,[name]:v}));
   if(refMap.has(name)){const opts=(options[name]||[]).map((o:any)=>({label:o.label,value:String(o.id)}));return <Dropdown value={value} options={opts} onChange={e=>set(e.value)} placeholder={name} filter className="field-control"/>;}
   if(collections.some((c:any)=>c.name===name)){const opts=(options[name]||[]).map((o:any)=>({label:o.label,value:String(o.id)}));return <MultiSelect value={value?value.split(","):[]} options={opts} onChange={e=>set(e.value.join(","))} filter className="field-control"/>;}
   if(/String|Char|Text/i.test(type))return /description|note|comment/i.test(name)?<InputTextarea value={value} onChange={e=>set(e.target.value)} autoResize className="field-control"/>:<InputText value={value} onChange={e=>set(e.target.value)} className="field-control"/>;
   if(/Boolean/i.test(type))return <Dropdown value={value} options={[{label:"true",value:"true"},{label:"false",value:"false"}]} onChange={e=>set(e.value)} className="field-control"/>;
   return <InputText value={value} onChange={e=>set(e.target.value)} className="field-control"/>;
 }
 return <><div className="section-heading"><div><div className="eyebrow">CRUD</div><h2>{edit?"Edit":"New"} {slug}</h2></div><Button label="Back" icon="pi pi-arrow-left" outlined onClick={()=>nav(`/resources/${slug}`)}/></div>
 <Card><div className="form-grid">{Object.entries(fields).filter(([n])=>n!=="id"&&n!=="version").map(([name,type])=><label key={name}><span>{name}</span>{renderField(name,type)}</label>)}</div>
 {mutation.isError&&<Message severity="error" text={mutation.error.message}/>}<div className="form-actions"><Button label={edit?"Save changes":"Create"} icon="pi pi-check" onClick={()=>mutation.mutate()} loading={mutation.isPending}/><Button label="Cancel" outlined onClick={()=>nav(`/resources/${slug}`)}/></div></Card></>;
}