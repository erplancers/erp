import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputNumber } from "primereact/inputnumber";
import { InputTextarea } from "primereact/inputtextarea";
import { Checkbox } from "primereact/checkbox";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Tag } from "primereact/tag";
import { TabView, TabPanel } from "primereact/tabview";
import { Message } from "primereact/message";
import { Divider } from "primereact/divider";
import { useAuth } from "../auth";
import { api } from "../api";

type Field = { path: string; javaType?: string; writable?: boolean };
type RelationPath = { path: string; targetEntity?: string; collection?: boolean };
type RuleSchema = {
  resource: string;
  fields?: Field[];
  relations?: RelationPath[];
  relationPaths?: RelationPath[];
};
type Condition = { path: string; operator: string; value?: unknown };
type Action = { type: string; target?: string; expression?: string; value?: unknown; message?: string };
type Rule = { name: string; priority: number; enabled: boolean; conditions: Condition[]; actions: Action[] };
type RuleSet = { name: string; entity: string; trigger: string; rules: Rule[] };
type SavedRuleSet = RuleSet & { id: string; updatedAt: string };

const operators = ["EXISTS","NOT_NULL","NULL","IS_NULL","EQUALS","NOT_EQUALS","GT","GTE","LT","LTE","CONTAINS","IN"].map(value => ({label:value, value}));
const actions = ["CALCULATE","SET","REJECT","VALIDATE","LOG"].map(value => ({label:value, value}));
const triggers = ["BEFORE_SAVE","AFTER_SAVE","BEFORE_CREATE","AFTER_CREATE","BEFORE_UPDATE","AFTER_UPDATE","BEFORE_DELETE","AFTER_DELETE","ON_STATUS_CHANGE"].map(value => ({label:value, value}));

const emptyRule = (): Rule => ({name:"New rule", priority:100, enabled:true, conditions:[{path:"",operator:"NOT_NULL",value:""}], actions:[{type:"VALIDATE",message:"Validation failed"}]});
const emptySet = (entity="") : RuleSet => ({name:"New business ruleset",entity,trigger:"BEFORE_SAVE",rules:[emptyRule()]});

function readSaved(): SavedRuleSet[] {
  try { return JSON.parse(localStorage.getItem("erp.businessRules") || "[]"); } catch { return []; }
}
function writeSaved(items: SavedRuleSet[]) { localStorage.setItem("erp.businessRules", JSON.stringify(items)); }

export function BusinessRulesPage() {
  const { credentials } = useAuth();
  const resources = useQuery({queryKey:["business-rule-resources"], queryFn:()=>api.resources(credentials!), enabled:!!credentials, staleTime:30000});
  const [resource, setResource] = useState("");
  const [depth, setDepth] = useState(3);
  const [ruleSet, setRuleSet] = useState<RuleSet>(emptySet());
  const [saved, setSaved] = useState<SavedRuleSet[]>(readSaved);
  const [selectedId, setSelectedId] = useState("");
  const [testId, setTestId] = useState<number|null>(null);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState("");

  const schema = useQuery<RuleSchema>({
    queryKey:["business-rule-schema",resource,depth],
    queryFn:()=>api.businessRuleSchema(resource,credentials!,depth),
    enabled:!!credentials && !!resource,
    staleTime:30000
  });

  const fields = useMemo(()=>schema.data?.fields ?? [],[schema.data]);
  const relationPaths = useMemo(()=>schema.data?.relationPaths ?? schema.data?.relations ?? [],[schema.data]);

  function chooseResource(value:string) {
    setResource(value);
    setRuleSet(emptySet(value));
    setResult(null);
    setError("");
  }
  function updateRule(index:number, patch:Partial<Rule>) {
    setRuleSet(s=>({...s,rules:s.rules.map((r,i)=>i===index?{...r,...patch}:r)}));
  }
  function updateCondition(ri:number, ci:number, patch:Partial<Condition>) {
    setRuleSet(s=>({...s,rules:s.rules.map((r,i)=>i===ri?{...r,conditions:r.conditions.map((c,j)=>j===ci?{...c,...patch}:c)}:r)}));
  }
  function updateAction(ri:number, ai:number, patch:Partial<Action>) {
    setRuleSet(s=>({...s,rules:s.rules.map((r,i)=>i===ri?{...r,actions:r.actions.map((a,j)=>j===ai?{...a,...patch}:a)}:r)}));
  }
  function save() {
    if (!ruleSet.entity || !ruleSet.name.trim()) { setError("Choose a resource and name the ruleset."); return; }
    const item:SavedRuleSet={...ruleSet,id:crypto.randomUUID(),updatedAt:new Date().toISOString()};
    const next=[...saved,item]; setSaved(next); writeSaved(next); setSelectedId(item.id); setError("");
  }
  function load(item:SavedRuleSet) { setRuleSet({...item}); setResource(item.entity); setSelectedId(item.id); setResult(null); }
  function remove(item:SavedRuleSet) { const next=saved.filter(x=>x.id!==item.id); setSaved(next); writeSaved(next); if(selectedId===item.id)setSelectedId(""); }
  async function run(mode:"evaluate"|"execute") {
    if (!credentials || !resource || !testId) { setError("Select a resource and enter an entity ID."); return; }
    try {
      setError(""); setResult(await (mode==="evaluate" ? api.evaluateBusinessRule(resource,testId,ruleSet,credentials) : api.executeBusinessRule(resource,testId,ruleSet,credentials)));
    } catch(e) { setError(e instanceof Error?e.message:String(e)); }
  }

  if(resources.isPending) return <div className="flex justify-content-center p-4"><i className="pi pi-spin pi-spinner" style={{fontSize:"2rem"}}/></div>;
  if(resources.isError) return <Message severity="error" text={String(resources.error)}/>;
  const resourceOptions=(resources.data??[]).map((r:any)=>({label:r.name ?? r.slug,value:r.slug}));

  return <div className="rules-page">
    <div className="section-heading">
      <div><div className="eyebrow">AUTOMATION / GOVERNANCE</div><h2>Business Rule Platform</h2><p>Design, inspect, test and manage JPA-relation-aware business rules without hard-coding every workflow.</p></div>
      <div className="page-actions"><Tag value={`${saved.length} local rulesets`} icon="pi pi-bolt"/><Button label="New ruleset" icon="pi pi-plus" onClick={()=>{setRuleSet(emptySet(resource));setResult(null);}}/></div>
    </div>

    <div className="rules-platform-grid">
      <Card title="Rule Library" subTitle="Client-side drafts are stored in this browser.">
        <DataTable value={saved} size="small" paginator rows={6} selectionMode="single" selection={saved.find(x=>x.id===selectedId)||null} onSelectionChange={e=>e.value&&load(e.value)} emptyMessage="No saved rulesets">
          <Column field="name" header="Ruleset"/>
          <Column field="entity" header="Resource"/>
          <Column field="trigger" header="Trigger"/>
          <Column field="rules" header="Rules" body={(r:SavedRuleSet)=>r.rules.length}/>
          <Column body={(r:SavedRuleSet)=><Button icon="pi pi-trash" text rounded severity="danger" onClick={()=>remove(r)} aria-label="Delete ruleset"/>}/>
        </DataTable>
      </Card>

      <Card title="Runtime Tester" subTitle="Evaluate first; execute only when write access is intended.">
        <div className="form-grid">
          <label>Resource<Dropdown value={resource} options={resourceOptions} onChange={e=>chooseResource(e.value)} filter placeholder="Select entity" className="field-control"/></label>
          <label>Entity ID<InputNumber value={testId} onValueChange={e=>setTestId(e.value??null)} min={1} placeholder="Order ID"/></label>
        </div>
        <div className="rule-actions">
          <Button label="Evaluate" icon="pi pi-search" outlined disabled={!resource||!testId} onClick={()=>run("evaluate")}/>
          <Button label="Execute" icon="pi pi-play" severity="warning" disabled={!resource||!testId} onClick={()=>run("execute")}/>
        </div>
        {result && <pre className="rule-result">{JSON.stringify(result,null,2)}</pre>}
      </Card>
    </div>

    <TabView className="mt-3">
      <TabPanel header="Rule Designer" leftIcon="pi pi-sliders-h">
        <Card>
          <div className="rule-header-grid">
            <label>Ruleset name<InputText value={ruleSet.name} onChange={e=>setRuleSet({...ruleSet,name:e.target.value})}/></label>
            <label>Entity<Dropdown value={ruleSet.entity} options={resourceOptions} onChange={e=>{setRuleSet({...ruleSet,entity:e.value});setResource(e.value)}} filter placeholder="Resource" className="field-control"/></label>
            <label>Trigger<Dropdown value={ruleSet.trigger} options={triggers} onChange={e=>setRuleSet({...ruleSet,trigger:e.value})} className="field-control"/></label>
            <label>Relation depth<InputNumber value={depth} onValueChange={e=>setDepth(Math.min(6,Math.max(1,e.value??3)))} min={1} max={6}/></label>
          </div>
          {schema.isFetching && <div className="rule-schema-loading"><i className="pi pi-spin pi-spinner"/> Loading JPA relation schema…</div>}
          {schema.isError && <Message severity="error" text={String(schema.error)}/>}
          {schema.data && <div className="schema-summary"><Tag value={`${fields.length} fields`} severity="info"/><Tag value={`${relationPaths.length} relation paths`} severity="success"/><span>Relations are discovered from the JPA metamodel.</span></div>}

          {ruleSet.rules.map((rule,ri)=><Card key={ri} className="rule-card" title={`Rule ${ri+1}`}>
            <div className="rule-title-row">
              <InputText value={rule.name} onChange={e=>updateRule(ri,{name:e.target.value})} placeholder="Rule name"/>
              <InputNumber value={rule.priority} onValueChange={e=>updateRule(ri,{priority:e.value??100})} min={0}/>
              <div className="flex align-items-center gap-2"><Checkbox inputId={`enabled-${ri}`} checked={rule.enabled} onChange={e=>updateRule(ri,{enabled:Boolean(e.checked)})}/><label htmlFor={`enabled-${ri}`}>Enabled</label></div>
              <Button icon="pi pi-trash" text rounded severity="danger" onClick={()=>setRuleSet(s=>({...s,rules:s.rules.filter((_,i)=>i!==ri)}))} aria-label="Delete rule"/>
            </div>
            <Divider align="left">Conditions</Divider>
            {rule.conditions.map((condition,ci)=><div className="rule-row" key={ci}>
              <Dropdown value={condition.path} options={fields.map(f=>({label:f.path,value:f.path}))} onChange={e=>updateCondition(ri,ci,{path:e.value})} filter editable placeholder="JPA path" className="field-control"/>
              <Dropdown value={condition.operator} options={operators} onChange={e=>updateCondition(ri,ci,{operator:e.value})} className="field-control"/>
              <InputText value={String(condition.value??"")} onChange={e=>updateCondition(ri,ci,{value:e.target.value})} placeholder="Value"/>
              <Button icon="pi pi-times" text rounded severity="secondary" onClick={()=>updateRule(ri,{conditions:rule.conditions.filter((_,i)=>i!==ci)})} aria-label="Remove condition"/>
            </div>)}
            <Button label="Add condition" icon="pi pi-plus" text size="small" onClick={()=>updateRule(ri,{conditions:[...rule.conditions,{path:"",operator:"NOT_NULL",value:""}]})}/>
            <Divider align="left">Actions</Divider>
            {rule.actions.map((action,ai)=><div className="rule-row rule-action-row" key={ai}>
              <Dropdown value={action.type} options={actions} onChange={e=>updateAction(ri,ai,{type:e.value})} className="field-control"/>
              {(action.type==="CALCULATE") && <><Dropdown value={action.target} options={fields.filter(f=>f.writable!==false).map(f=>({label:f.path,value:f.path}))} editable filter onChange={e=>updateAction(ri,ai,{target:e.value})} placeholder="Target field" className="field-control"/><InputText value={action.expression??""} onChange={e=>updateAction(ri,ai,{expression:e.target.value})} placeholder="SUM(lines.quantity * lines.unitPrice)"/></>}
              {action.type==="SET" && <><Dropdown value={action.target} options={fields.filter(f=>f.writable!==false).map(f=>({label:f.path,value:f.path}))} editable filter onChange={e=>updateAction(ri,ai,{target:e.value})} placeholder="Target field" className="field-control"/><InputText value={String(action.value??"")} onChange={e=>updateAction(ri,ai,{value:e.target.value})} placeholder="Value"/></>}
              {action.type==="REJECT" && <InputText value={action.message??""} onChange={e=>updateAction(ri,ai,{message:e.target.value})} placeholder="Reason"/>
              }
              {action.type==="VALIDATE" && <InputText value={action.message??""} onChange={e=>updateAction(ri,ai,{message:e.target.value})} placeholder="Validation message"/>
              }
              {action.type==="LOG" && <InputText value={action.message??""} onChange={e=>updateAction(ri,ai,{message:e.target.value})} placeholder="Audit message"/>
              }
              <Button icon="pi pi-times" text rounded severity="secondary" onClick={()=>updateRule(ri,{actions:rule.actions.filter((_,i)=>i!==ai)})} aria-label="Remove action"/>
            </div>)}
            <Button label="Add action" icon="pi pi-plus" text size="small" onClick={()=>updateRule(ri,{actions:[...rule.actions,{type:"VALIDATE",message:"Validation failed"}]})}/>
          </Card>)}
          <div className="rule-designer-footer">
            <Button label="Add rule" icon="pi pi-plus" outlined onClick={()=>setRuleSet(s=>({...s,rules:[...s.rules,emptyRule()]}))}/>
            <Button label="Save ruleset" icon="pi pi-save" onClick={save}/>
          </div>
          {error && <Message severity="error" text={error} className="mt-3"/>}
        </Card>
      </TabPanel>

      <TabPanel header="Relation Explorer" leftIcon="pi pi-share-alt">
        <Card title="JPA relation graph">
          <div className="relation-browser">
            {relationPaths.length ? relationPaths.map((r,i)=><div className="relation-node" key={i}><i className="pi pi-arrow-right"/><div><b>{r.path}</b><span>{r.targetEntity ?? "Relation"}{r.collection?" · collection":""}</span></div></div>) : <Message severity="info" text="Select an entity and load its schema to discover relation paths."/>}
          </div>
        </Card>
      </TabPanel>

      <TabPanel header="JSON Preview" leftIcon="pi pi-code">
        <Card title="Executable ruleset payload"><pre className="rule-json">{JSON.stringify(ruleSet,null,2)}</pre></Card>
      </TabPanel>
    </TabView>
  </div>;
}
