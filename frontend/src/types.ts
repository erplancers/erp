export interface ResourceInfo { slug:string; module:string; entity:string; path?:string; name?:string; }
export interface PageResponse { content:Array<{id?:number;slug?:string;module?:string;values?:Record<string,unknown>;[key:string]:unknown}>; totalElements:number; totalPages:number; number:number; size:number; }
export interface Dashboard { totalRecords:number; resourceCount:number; moduleCount:number; recordsByModule:Record<string,number>; recordsByResource:Record<string,number>; modules:unknown[]; }
export interface AggregateReport { resource:string; module:string; groupBy:string[]; metrics:string[]; filters:Record<string,string>; data:Array<Record<string,unknown>>; }
export interface AdvancedAnalysis { resource:string; [key:string]:unknown; }
export interface BiReport { resource:string; module:string; dimensions:string[]; measures:string[]; filters:Record<string,string>; rows:Array<Record<string,unknown>>; grandTotal?:Record<string,unknown>; ranked?:boolean; shareOfTotal?:boolean; }
export interface FieldSchema { name:string; javaType:string; persistentType:string; }
export interface ResourceSchema { slug:string; module:string; entity:string; fields:FieldSchema[]; }
export interface EntityCatalog { resourceCount:number; resources:ResourceSchema[]; }
export interface ReportResource { module:string; slug:string; name:string; path:string; }
export interface ModuleReport { [key:string]:unknown; }
export interface OlapCatalog { [key:string]:unknown; }
export interface AutoOlapCube { name:string; resource:string; module:string; entity:string; dimensions:string[]; measures:string[]; relationships:string[]; }
export interface AutoOlapCatalog { cubeCount:number; cubes:AutoOlapCube[]; }
