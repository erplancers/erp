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
 olapDrilldown:(slug:string,body:object,c:Credentials)=>apiFetch<any>(`/bi/olap/resources/${encodeURIComponent(slug)}/drilldown`,c,{method:"POST",body:JSON.stringify(body)})
};