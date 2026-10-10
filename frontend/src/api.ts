import type { AggregateReport, AdvancedAnalysis, Dashboard, EntityCatalog, PageResponse, ReportResource, ModuleReport, ResourceInfo, BiReport, OlapCatalog } from "./types";

const API_BASE = "/api";
export interface Credentials { username: string; password: string; }
function authHeader(c: Credentials) { return "Basic " + btoa(unescape(encodeURIComponent(c.username + ":" + c.password))); }

export async function apiFetch<T>(path:string, credentials:Credentials, init:RequestInit={}):Promise<T>{
  const headers=new Headers(init.headers); headers.set("Authorization",authHeader(credentials)); headers.set("Accept","application/json");
  if(init.body && !headers.has("Content-Type")) headers.set("Content-Type","application/json");
  const response=await fetch(API_BASE+path,{...init,headers,credentials:"same-origin"});
  if(response.status===401) throw new Error("AUTH_REQUIRED"); if(response.status===403) throw new Error("ACCESS_DENIED");
  if(!response.ok){
    const body=await response.text();
    let message = body || `Request failed: ${response.status}`;
    try {
      const parsed = JSON.parse(body);
      message = parsed.message || parsed.error || parsed.detail || message;
      if (parsed.path) message += ` (${parsed.path})`;
    } catch { /* keep plain-text response */ }
    throw new Error(message);
  }
  if(response.status===204)return undefined as T; return response.json() as Promise<T>;
}
export const api={
 resources:(c:Credentials)=>apiFetch<ResourceInfo[]>("/resources",c),
 registerAdmin:(body:{username:string;password:string;adminToken:string})=>fetch(API_BASE+"/auth/register-admin",{method:"POST",headers:{"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify(body)}).then(async r=>{if(!r.ok)throw new Error((await r.text())||`Admin registration failed: ${r.status}`);return r.json();}),
 register:(body:{username:string;password:string})=>fetch(API_BASE+"/auth/register",{method:"POST",headers:{"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify(body)}).then(async r=>{if(!r.ok)throw new Error((await r.text())||`Registration failed: ${r.status}`);return r.json();}),
 dashboard:(c:Credentials)=>apiFetch<Dashboard>("/analytics/dashboard",c),
 catalog:(c:Credentials)=>apiFetch<EntityCatalog>("/analytics/catalog",c),
 rows:(slug:string,page:number,size:number,c:Credentials)=>apiFetch<PageResponse>(`/resources/${encodeURIComponent(slug)}?page=${page}&size=${size}`,c),
 get:(slug:string,id:string,c:Credentials)=>apiFetch<any>(`/resources/${encodeURIComponent(slug)}/${id}`,c),
 metadata:(slug:string,id:string|undefined,c:Credentials)=>apiFetch<any>(`/resources/${encodeURIComponent(slug)}/metadata${id?"/"+id:""}`,c),
 create:(slug:string,values:Record<string,string>,c:Credentials)=>apiFetch<any>(`/resources/${encodeURIComponent(slug)}`,c,{method:"POST",body:JSON.stringify({values})}),
 update:(slug:string,id:string,values:Record<string,string>,c:Credentials)=>apiFetch<any>(`/resources/${encodeURIComponent(slug)}/${id}`,c,{method:"PUT",body:JSON.stringify({values})}),
 remove:(slug:string,id:string,c:Credentials)=>apiFetch<void>(`/resources/${encodeURIComponent(slug)}/${id}`,c,{method:"DELETE"}),
 aggregate:(slug:string,body:object,c:Credentials)=>apiFetch<AggregateReport>(`/analytics/resources/${encodeURIComponent(slug)}/aggregate`,c,{method:"POST",body:JSON.stringify(body)}),
 profile:(slug:string,c:Credentials)=>apiFetch<any>(`/analytics/resources/${encodeURIComponent(slug)}/profile`,c),
 advanced:(slug:string,body:object,c:Credentials)=>apiFetch<AdvancedAnalysis>(`/analytics/resources/${encodeURIComponent(slug)}/advanced`,c,{method:"POST",body:JSON.stringify(body)}),
 reportResources:(c:Credentials)=>apiFetch<ReportResource[]>("/reports/resources",c),
 report:(slug:string,c:Credentials)=>apiFetch<any>(`/reports/resources/${encodeURIComponent(slug)}`,c),
 modules:(c:Credentials)=>apiFetch<Record<string,ModuleReport>>("/reports/modules",c),
 module:(module:string,c:Credentials)=>apiFetch<ModuleReport>(`/reports/modules/${encodeURIComponent(module)}`,c),
 biQuery:(slug:string,body:object,c:Credentials)=>apiFetch<BiReport>(`/bi/resources/${encodeURIComponent(slug)}/query`,c,{method:"POST",body:JSON.stringify(body)}),
 biAnalyze:(slug:string,body:object,c:Credentials)=>apiFetch<any>(`/bi/resources/${encodeURIComponent(slug)}/analyze`,c,{method:"POST",body:JSON.stringify(body)}),
 olapCatalog:(c:Credentials)=>apiFetch<OlapCatalog>("/bi/olap/catalog",c),
 olapProfiles:(c:Credentials)=>apiFetch<any>("/bi/olap/profiles",c),
 olapCube:(slug:string,body:object,c:Credentials)=>apiFetch<any>(`/bi/olap/resources/${encodeURIComponent(slug)}/cube`,c,{method:"POST",body:JSON.stringify(body)}),
 olapDrilldown:(slug:string,body:object,c:Credentials)=>apiFetch<any>(`/bi/olap/resources/${encodeURIComponent(slug)}/drilldown`,c,{method:"POST",body:JSON.stringify(body)}),
 autoOlapCubes:(c:Credentials,relationDepth=2)=>apiFetch<import("./types").AutoOlapCatalog>(`/bi/olap/cubes?relationDepth=${relationDepth}`,c),
 autoOlapRelations:(slug:string,relation:string,c:Credentials,relationDepth=2)=>apiFetch<import("./types").AutoOlapRelationDiscovery>(`/bi/olap/cubes/${encodeURIComponent(slug)}/relations?relation=${encodeURIComponent(relation)}&relationDepth=${relationDepth}`,c),
 autoOlapQuery:(slug:string,body:object,c:Credentials)=>apiFetch<any>(`/bi/olap/cubes/${encodeURIComponent(slug)}/query`,c,{method:"POST",body:JSON.stringify(body)}),
 businessRuleSchema:(resource:string,c:Credentials,relationDepth=3)=>apiFetch<import("./types").BusinessRuleSchema>(`/business-rules/schema/${encodeURIComponent(resource)}?relationDepth=${relationDepth}`,c),
 evaluateBusinessRule:(resource:string,id:number,ruleset:import("./types").BusinessRuleSet,c:Credentials)=>apiFetch<any>(`/business-rules/resources/${encodeURIComponent(resource)}/${id}/evaluate`,c,{method:"POST",body:JSON.stringify(ruleset)}),
 executeBusinessRule:(resource:string,id:number,ruleset:import("./types").BusinessRuleSet,c:Credentials)=>apiFetch<any>(`/business-rules/resources/${encodeURIComponent(resource)}/${id}/execute`,c,{method:"POST",body:JSON.stringify(ruleset)}),
 submitAnalyticsJob:(request:import("./types").AnalyticsJobSubmitRequest,c:Credentials)=>apiFetch<import("./types").AnalyticsJobView>("/analytics/jobs",c,{method:"POST",body:JSON.stringify(request)}),
 analyticsJobs:(tenantId:string,c:Credentials,status?:string)=>apiFetch<import("./types").AnalyticsJobView[]>(`/analytics/jobs?tenantId=${encodeURIComponent(tenantId)}${status?`&status=${encodeURIComponent(status)}`:""}`,c),
 analyticsJob:(id:string,tenantId:string,c:Credentials)=>apiFetch<import("./types").AnalyticsJobView>(`/analytics/jobs/${encodeURIComponent(id)}?tenantId=${encodeURIComponent(tenantId)}`,c),
 cancelAnalyticsJob:(id:string,tenantId:string,c:Credentials)=>apiFetch<import("./types").AnalyticsJobView>(`/analytics/jobs/${encodeURIComponent(id)}/cancel?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"POST"}),

 biWorkspaceReports:(tenantId:string,c:Credentials)=>apiFetch<import("./types").SavedBiReport[]>(`/bi-workspace/reports?tenantId=${encodeURIComponent(tenantId)}`,c),
 saveBiReport:(body:import("./types").SaveBiReportRequest,c:Credentials)=>apiFetch<import("./types").SavedBiReport>("/bi-workspace/reports",c,{method:"POST",body:JSON.stringify(body)}),
 runBiReport:(id:string,tenantId:string,c:Credentials)=>apiFetch<BiReport>(`/bi-workspace/reports/${encodeURIComponent(id)}/run?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"POST"}),
 exportBiReport:(id:string,tenantId:string,format:"CSV"|"XLSX"|"PDF",c:Credentials)=>fetch(API_BASE+`/bi-workspace/reports/${encodeURIComponent(id)}/export?tenantId=${encodeURIComponent(tenantId)}&format=${format}`,{headers:{Authorization:authHeader(c),Accept:"*/*"},credentials:"same-origin"}).then(async response=>{if(!response.ok)throw new Error((await response.text())||`Export failed: ${response.status}`);return response.blob();}),
 biMeasures:(tenantId:string,resourceSlug:string,c:Credentials)=>apiFetch<import("./types").CalculatedMeasure[]>(`/bi-workspace/measures?tenantId=${encodeURIComponent(tenantId)}&resourceSlug=${encodeURIComponent(resourceSlug)}`,c),
 createBiMeasure:(body:import("./types").SaveCalculatedMeasureRequest,c:Credentials)=>apiFetch<import("./types").CalculatedMeasure>("/bi-workspace/measures",c,{method:"POST",body:JSON.stringify(body)}),
 evaluateBiMeasures:(body:{tenantId:string;resourceSlug:string;expressions:Record<string,string>;values:Record<string,unknown>},c:Credentials)=>apiFetch<Record<string,number>>("/bi-workspace/measures/evaluate",c,{method:"POST",body:JSON.stringify(body)}),
 createBiReportSchedule:(id:string,tenantId:string,body:import("./types").CreateBiReportScheduleRequest,c:Credentials)=>apiFetch<import("./types").BiReportSchedule>(`/bi-workspace/reports/${encodeURIComponent(id)}/schedules?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"POST",body:JSON.stringify(body)}),
 biReportSchedules:(id:string,tenantId:string,c:Credentials)=>apiFetch<import("./types").BiReportSchedule[]>(`/bi-workspace/reports/${encodeURIComponent(id)}/schedules?tenantId=${encodeURIComponent(tenantId)}`,c),
 biReportDeliveries:(tenantId:string,c:Credentials)=>apiFetch<import("./types").BiReportDelivery[]>(`/bi-workspace/deliveries?tenantId=${encodeURIComponent(tenantId)}`,c),
 biMonitoring:(tenantId:string,c:Credentials)=>apiFetch<Record<string,unknown>>(`/bi-workspace/monitoring?tenantId=${encodeURIComponent(tenantId)}`,c),

 createAnalyticsSchedule:(body:import("./types").CreateAnalyticsScheduleRequest,c:Credentials)=>apiFetch<import("./types").AnalyticsModelSchedule>("/analytics/schedules",c,{method:"POST",body:JSON.stringify(body)}),
 analyticsSchedules:(tenantId:string,c:Credentials)=>apiFetch<import("./types").AnalyticsModelSchedule[]>(`/analytics/schedules?tenantId=${encodeURIComponent(tenantId)}`,c),
 toggleAnalyticsSchedule:(id:string,tenantId:string,enabled:boolean,c:Credentials)=>apiFetch<import("./types").AnalyticsModelSchedule>(`/analytics/schedules/${encodeURIComponent(id)}?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"PATCH",body:JSON.stringify({enabled})}),
 analyticsEvaluations:(tenantId:string,name:string,version:number,c:Credentials)=>apiFetch<import("./types").AnalyticsModelEvaluation[]>(`/analytics/models/${encodeURIComponent(name)}/${version}/evaluations?tenantId=${encodeURIComponent(tenantId)}`,c),
 analyticsModels:(tenantId:string,c:Credentials,modelName?:string)=>apiFetch<import("./types").AnalyticsModelView[]>(`/analytics/models?tenantId=${encodeURIComponent(tenantId)}${modelName?`&modelName=${encodeURIComponent(modelName)}`:""}`,c),
 analyticsExperiments:(tenantId:string,c:Credentials)=>apiFetch<import("./types").AnalyticsExperimentView[]>(`/analytics/experiments?tenantId=${encodeURIComponent(tenantId)}`,c),
 analyticsDrift:(tenantId:string,c:Credentials,modelName?:string)=>apiFetch<import("./types").AnalyticsDriftView[]>(`/analytics/drift?tenantId=${encodeURIComponent(tenantId)}${modelName?`&modelName=${encodeURIComponent(modelName)}`:""}`,c),
 reviewAnalyticsModel:(tenantId:string,name:string,version:number,approved:boolean,note:string,c:Credentials)=>apiFetch<import("./types").AnalyticsModelView>(`/analytics/models/${encodeURIComponent(name)}/${version}/review?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"POST",body:JSON.stringify({approved,note})}),
 promoteAnalyticsModel:(tenantId:string,name:string,version:number,note:string,c:Credentials)=>apiFetch<import("./types").AnalyticsModelView>(`/analytics/models/${encodeURIComponent(name)}/${version}/promote?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"POST",body:JSON.stringify({note})}),
 rollbackAnalyticsModel:(tenantId:string,name:string,version:number,note:string,c:Credentials)=>apiFetch<import("./types").AnalyticsModelView>(`/analytics/models/${encodeURIComponent(name)}/${version}/rollback?tenantId=${encodeURIComponent(tenantId)}`,c,{method:"POST",body:JSON.stringify({note})})
};