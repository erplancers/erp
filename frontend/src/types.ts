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
export interface AutoOlapRelationPath { path:string; targetEntity:string; relationshipType:string; }
export interface AutoOlapRelationDiscovery { resource:string; relation:string; paths:AutoOlapRelationPath[]; }

export interface BusinessRulePath { path:string; javaType:string; persistentType:string; relation:boolean; }
export interface BusinessRuleSchema { resource:string; entity:string; paths:BusinessRulePath[]; }
export interface BusinessRuleCondition { path:string; operator:string; value:unknown; }
export interface BusinessRuleAction { type:string; target:string; expression:string; message:string; }
export interface BusinessRule { name:string; priority:number; condition:BusinessRuleCondition; actions:BusinessRuleAction[]; }
export interface BusinessRuleSet { name:string; entity:string; trigger:string; rules:BusinessRule[]; }
