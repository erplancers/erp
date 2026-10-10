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


export interface AnalyticsJobView {
  id: string;
  tenantId: string;
  jobType: string;
  datasetKey: string;
  status: "QUEUED" | "RUNNING" | "CANCEL_REQUESTED" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  attemptCount: number;
  maxAttempts: number;
  resultJson?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface AnalyticsModelView {
  id: string;
  tenantId: string;
  modelName: string;
  version: number;
  status: "CANDIDATE" | "VALIDATED" | "PRODUCTION" | "RETIRED" | "REJECTED" | string;
  artifactUri: string;
  manifestJson: string;
  metricsJson: string;
  createdAt: string;
  promotedAt?: string | null;
  promotedBy?: string | null;
}
export interface AnalyticsExperimentView {
  id: string;
  runName: string;
  algorithm: string;
  parametersJson: string;
  metricsJson: string;
  inputSnapshot?: string | null;
  status: string;
  createdBy: string;
  createdAt: string;
}
export interface AnalyticsDriftView {
  id: number;
  modelName: string;
  modelVersion: number;
  featureName: string;
  psi: number;
  sampleSize: number;
  baselineSnapshot?: string | null;
  currentSnapshot?: string | null;
  measuredAt: string;
  status: string;
}
export interface AnalyticsJobSubmitRequest {
  tenantId: string;
  datasetKey: string;
  jobType: string;
  parameters: Record<string, unknown>;
  idempotencyKey?: string;
}

export interface SavedBiReport {
  id: string;
  tenantId: string;
  ownerUsername: string;
  name: string;
  resourceSlug: string;
  definitionJson: string;
  visibility: "PRIVATE" | "SHARED";
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface SaveBiReportRequest {
  tenantId: string;
  name: string;
  resourceSlug: string;
  definitionJson: string;
  visibility: "PRIVATE" | "SHARED";
}
export interface CalculatedMeasure {
  id: string;
  tenantId: string;
  ownerUsername: string;
  resourceSlug: string;
  measureKey: string;
  displayName: string;
  expression: string;
  formatHint: "NUMBER" | "PERCENT" | "CURRENCY" | "INTEGER";
  visibility: "PRIVATE" | "SHARED";
  createdAt: string;
  updatedAt: string;
}
export interface SaveCalculatedMeasureRequest {
  tenantId: string;
  resourceSlug: string;
  measureKey: string;
  displayName: string;
  expression: string;
  formatHint: "NUMBER" | "PERCENT" | "CURRENCY" | "INTEGER";
  visibility: "PRIVATE" | "SHARED";
}
export interface CreateBiReportScheduleRequest {
  cronExpression: string;
  timezone: string;
  recipients: string[];
  exportFormat: "CSV" | "XLSX" | "PDF";
}
export interface BiReportSchedule {
  id: string;
  reportId: string;
  tenantId: string;
  createdBy: string;
  cronExpression: string;
  timezone: string;
  recipientsJson: string;
  exportFormat: string;
  enabled: boolean;
  nextRunAt: string;
  lastRunAt?: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface BiReportDelivery {
  id: string;
  scheduleId: string;
  reportId: string;
  tenantId: string;
  requestedBy: string;
  status: string;
  attempts: number;
  availableAt: string;
  completedAt?: string | null;
  errorMessage?: string | null;
  createdAt: string;
}

export interface CreateAnalyticsScheduleRequest {
  tenantId: string;
  modelName: string;
  scheduleType: "RETRAIN" | "BATCH_SCORE";
  jobType: string;
  cronExpression: string;
  timezone: string;
  datasetKey: string;
  parameters: Record<string, unknown>;
}
export interface AnalyticsModelSchedule {
  id: string;
  tenantId: string;
  modelName: string;
  createdBy: string;
  scheduleType: string;
  jobType: string;
  cronExpression: string;
  timezone: string;
  datasetKey: string;
  parametersJson: string;
  enabled: boolean;
  nextRunAt: string;
  lastRunAt?: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface AnalyticsModelEvaluation {
  id: string;
  tenantId: string;
  modelName: string;
  modelVersion: number;
  evaluationType: string;
  metricsJson: string;
  sampleSize: number;
  datasetSnapshot?: string | null;
  evaluatedBy: string;
  createdAt: string;
}
