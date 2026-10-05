import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputNumber } from "primereact/inputnumber";
import { Checkbox } from "primereact/checkbox";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Tag } from "primereact/tag";
import { TabView, TabPanel } from "primereact/tabview";
import { Message } from "primereact/message";
import { Divider } from "primereact/divider";
import { useAuth } from "../auth";
import { api } from "../api";

type RelationPath={path:string;javaType:string;persistentType:string;relation:boolean};
type RuleSchema={resource:string;entity:string;paths:RelationPath[]};
type Condition={path:string;operator:string;value?:unknown};
type Action={type:string;target:string;expression:string;message:string};
type Rule={name:string;priority:number;enabled:boolean;condition:Condition;actions:Action[]};
type RuleSet={name:string;entity:string;trigger:string;rules:Rule[]};
type SavedRuleSet=RuleSet&{id:string;updatedAt:string};

const operators=["EXISTS","NOT_NULL","NULL","IS_NULL","EQUALS","NOT_EQUALS","GT","GTE","LT","LTE","CONTAINS","IN"].map(value=>({label:value,value}));
const actionTypes=["CALCULATE","SET","REJECT","VALIDATE","LOG"].map(value=>({label:value,value}));
const triggers=["BEFORE_SAVE","AFTER_SAVE","BEFORE_CREATE","AFTER_CREATE","BEFORE_UPDATE","AFTER_UPDATE","BEFORE_DELETE","AFTER_DELETE","ON_STATUS_CHANGE"].map(value=>({label:value,value}));
const emptyAction=():Action=>({type:"VALIDATE",target:"",expression:"",message:"Validation failed"});
const emptyRule=():Rule=>({name:"New rule",priority:100,enabled:true,condition:{path:"",operator:"NOT_NULL",value:""},actions:[emptyAction()]});
const emptySet=(entity=""):RuleSet=>({name:"New business ruleset",entity,trigger:"BEFORE_SAVE",rules:[emptyRule()]});
function readSaved():SavedRuleSet[]{try{return JSON.parse(localStorage.getItem("erp.businessRules")||"[]")}catch{return[]}}
function writeSaved(items:SavedRuleSet[]){localStorage.setItem("erp.businessRules",JSON.stringify(items))}
function payload(s:RuleSet):RuleSet{return {...s,rules:s.rules.filter(r=>r.enabled).map(({enabled,...r})=>r)}}

export function BusinessRulesPage(){
 const {credentials}=useAuth();
 const resources=useQuery({queryKey:["business-rule-resources"],queryFn:()=>api.resources(credentials!),enabled:!!credentials,staleTime:30000});
 const [resource,setResource]=useState(""); const [depth,setDepth]=useState(3); const [ruleSet,setRuleSet]=useState<RuleSet>(emptySet());
 const [saved,setSaved]=useState<SavedRuleSet[]>(readSaved); const [selectedId,setSelectedId]=useState(""); const [testId,setTestId]=useState<number|null>(null);
 const [result,setResult]=useState<any>(null); const [error,setError]=useState("");
 const schema=useQuery<RuleSchema>({queryKey:["business-rule-schema",resource,depth],queryFn:()=>api.businessRuleSchema(resource,credentials!,depth),enabled:!!credentials&&!!resource,staleTime:30000});
 const paths=useMemo(()=>schema.data?.paths??[],[schema.data]);
 const pathOptions=paths.map(p=>({label:p.path,value:p.path}));
 const writableOptions=paths.filter(p=>!p.relation&&!p.path.includes(".")).map(p=>({label:p.path,value:p.path}));
 const resourceOptions=(resources.data??[]).map((r:any)=>({label:r.name??r.slug,value:r.slug}));
 function chooseResource(value:string){setResource(value);setRuleSet(emptySet(value));setResult(null);setError("")}
 function updateRule(i:number,patch:Partial<Rule>){setRuleSet(s=>({...s,rules:s.rules.map((r,j)=>j===i?{...r,...patch}:r)}))}
 function updateAction(ri:number,ai:number,patch:Partial<Action>){setRuleSet(s=>({...s,rules:s.rules.map((r,i)=>i===ri?{...r,actions:r.actions.map((a,j)=>j===ai?{...a,...patch}:a)}:r)}))}
 function save(){if(!ruleSet.entity||!ruleSet.name.trim()){setError("Choose a resource and name the ruleset.");return}const item={...ruleSet,id:crypto.randomUUID(),updatedAt:new Date().toISOString()};const next=[...saved,item];setSaved(next);writeSaved(next);setSelectedId(item.id);setError("")}
 function load(item:SavedRuleSet){setRuleSet({...item});setResource(item.entity);setSelectedId(item.id);setResult(null)}
 function remove(item:SavedRuleSet){const next=saved.filter(x=>x.id!==item.id);setSaved(next);writeSaved(next);if(selectedId===item.id)setSelectedId("")}
 async function run(mode:"evaluate"|"execute"){if(!credentials||!resource||!testId){setError("Select a resource and enter an entity ID.");return}try{setError("");setResult(await (mode==="evaluate"?api.evaluateBusinessRule(resource,testId,payload(ruleSet),credentials):api.executeBusinessRule(resource,testId,payload(ruleSet),credentials)))}catch(e){setError(e instanceof Error?e.message:String(e))}}
 if(resources.isPending)return <div className="flex justify-content-center p-4"><i className="pi pi-spin pi-spinner" style={{fontSize:"2rem"}}/></div>;
 if(resources.isError)return <Message severity="error" text={String(resources.error)}/>;
 return <div className="rules-page">
  <div className="section-heading"><div><div className="eyebrow">AUTOMATION / GOVERNANCE</div><h2>Business Rule Platform</h2><p>Design, inspect, test and manage JPA-relation-aware business rules from the ERP client.</p></div><div className="page-actions"><Tag value={`${saved.length} local rulesets`} icon="pi pi-bolt"/><Button label="New ruleset" icon="pi pi-plus" onClick={()=>{setRuleSet(emptySet(resource));setResult(null)}}/></div></div>
  <div className="rules-platform-grid">
   <Card title="Rule Library" subTitle="Browser drafts are local until a persistent ruleset API is added."><DataTable value={saved} size="small" paginator rows={6} selectionMode="single" selection={saved.find(x=>x.id===selectedId)||null} onSelectionChange={e=>e.value&&load(e.value)} emptyMessage="No saved rulesets"><Column field="name" header="Ruleset"/><Column field="entity" header="Resource"/><Column field="trigger" header="Trigger"/><Column header="Rules" body={(r:SavedRuleSet)=>r.rules.length}/><Column header="" body={(r:SavedRuleSet)=><Button icon="pi pi-trash" text rounded severity="danger" onClick={()=>remove(r)} aria-label="Delete ruleset"}/>}</DataTable></Card>
   <Card title="Runtime Tester" subTitle="Evaluate before executing a write action."><div className="form-grid"><label>Resource<Dropdown value={resource} options={resourceOptions} onChange={e=>chooseResource(e.value)} filter placeholder="Select entity" className="field-control"/></label><label>Entity ID<InputNumber value={testId} onValueChange={e=>setTestId(e.value??null)} min={1} placeholder="Order ID"/></label></div><div className="rule-actions"><Button label="Evaluate" icon="pi pi-search" outlined disabled={!resource||!testId} onClick={()=>run("evaluate")}/><Button label="Execute" icon="pi pi-play" severity="warning" disabled={!resource||!testId} onClick={()=>run("execute")}/></div>{result&&<pre className="rule-result">{JSON.stringify(result,null,2)}</pre>}</Card>
  </div>
  <TabView className="mt-3">
   <TabPanel header="Rule Designer" leftIcon="pi pi-sliders-h"><Card>
    <div className="rule-header-grid"><label>Ruleset name<InputText value={ruleSet.name} onChange={e=>setRuleSet({...ruleSet,name:e.target.value})}/></label><label>Entity<Dropdown value={ruleSet.entity} options={resourceOptions} onChange={e=>{setRuleSet({...ruleSet,entity:e.value});setResource(e.value)}} filter placeholder="Resource" className="field-control"/></label><label>Trigger<Dropdown value={ruleSet.trigger} options={triggers} onChange={e=>setRuleSet({...ruleSet,trigger:e.value})} className="field-control"/></label><label>Relation depth<InputNumber value={depth} onValueChange={e=>setDepth(Math.min(6,Math.max(1,e.value??3)))} min={1} max={6}/></label></div>
    {schema.isFetching&&<div className="rule-schema-loading"><i className="pi pi-spin pi-spinner"/> Loading JPA metamodel…</div>}{schema.data&&<div className="schema-summary"><Tag value={`${paths.length} discovered paths`} severity="success"/><span>{paths.filter(p=>p.relation).length} relationships · depth {depth}</span></div>}{schema.isError&&<Message severity="error" text={String(schema.error)}/>}
    {ruleSet.rules.map((rule,ri)=><Card key={ri} className="rule-card" title={`Rule ${ri+1}`}>
     <div className="rule-title-row"><InputText value={rule.name} onChange={e=>updateRule(ri,{name:e.target.value})} placeholder="Rule name"/><InputNumber value={rule.priority} onValueChange={e=>updateRule(ri,{priority:e.value??100})} min={0}/><div className="flex align-items-center gap-2"><Checkbox inputId={`enabled-${ri}`} checked={rule.enabled} onChange={e=>updateRule(ri,{enabled:Boolean(e.checked)})}/><label htmlFor={`enabled-${ri}`}>Enabled</label></div><Button icon="pi pi-trash" text rounded severity="danger" onClick={()=>setRuleSet(s=>({...s,rules:s.rules.filter((_,i)=>i!==ri)}))} aria-label="Delete rule"/></div>
     <Divider align="left">Condition</Divider><div className="rule-row"><Dropdown value={rule.condition.path} options={pathOptions} onChange={e=>updateRule(ri,{condition:{...rule.condition,path:e.value}})} editable filter placeholder="JPA property path" className="field-control"/><Dropdown value={rule.condition.operator} options={operators} onChange={e=>updateRule(ri,{condition:{...rule.condition,operator:e.value}})} className="field-control"/><InputText value={String(rule.condition.value??"")} onChange={e=>updateRule(ri,{condition:{...rule.condition,value:e.target.value}})} placeholder="Value"/></div>
     <Divider align="left">Actions</Divider>{rule.actions.map((a,ai)=><div className="rule-row" key={ai}><Dropdown value={a.type} options={actionTypes} onChange={e=>updateAction(ri,ai,{type:e.value})} className="field-control"/>{(a.type==="CALCULATE"||a.type==="SET")&&<Dropdown value={a.target} options={writableOptions} editable filter onChange={e=>updateAction(ri,ai,{target:e.value})} placeholder="Target root field" className="field-control"/>}{(a.type==="CALCULATE"||a.type==="SET")&&<InputText value={a.expression} onChange={e=>updateAction(ri,ai,{expression:e.target.value})} placeholder={a.type==="CALCULATE"?"SUM(lines.quantity * lines.unitPrice)":"100"}/>} {(a.type==="REJECT"||a.type==="VALIDATE"||a.type==="LOG")&&<InputText value={a.message} onChange={e=>updateAction(ri,ai,{message:e.target.value})} placeholder="Message / audit text"/>}<Button icon="pi pi-times" text rounded severity="secondary" onClick={()=>updateRule(ri,{actions:rule.actions.filter((_,i)=>i!==ai)})} aria-label="Remove action"/></div>)}<Button label="Add action" icon="pi pi-plus" text size="small" onClick={()=>updateRule(ri,{actions:[...rule.actions,emptyAction()]})}/>
    </Card>)}
    <div className="rule-designer-footer"><Button label="Add rule" icon="pi pi-plus" outlined onClick={()=>setRuleSet(s=>({...s,rules:[...s.rules,emptyRule()]}))}/><Button label="Save ruleset" icon="pi pi-save" onClick={save}/></div>{error&&<Message severity="error" text={error} className="mt-3" />}
   </Card></TabPanel>
   <TabPanel header="Relation Explorer" leftIcon="pi pi-share-alt"><Card title="JPA relation graph"><div className="relation-browser">{paths.length?paths.map((p,i)=><div className={`relation-node ${p.relation?"is-relation":""}`} key={i}><i className={p.relation?"pi pi-share-alt":"pi pi-circle-fill"}/><div><b>{p.path}</b><span>{p.javaType} · {p.persistentType}{p.relation?" · relation":""}</span></div></div>):<Message severity="info" text="Select an entity to discover its JPA paths."/>}</div></Card></TabPanel>
   <TabPanel header="JSON Preview" leftIcon="pi pi-code"><Card title="Executable ruleset payload"><pre className="rule-json">{JSON.stringify(payload(ruleSet),null,2)}</pre></Card></TabPanel>
  </TabView>
 </div>
}
