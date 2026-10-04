import type { AggregateReport, Dashboard, EntityCatalog, PageResponse, ResourceInfo, BiReport } from "./types";

const API_BASE = "/api";

export interface Credentials {
  username: string;
  password: string;
}

function authHeader(credentials: Credentials): string {
  return "Basic " + btoa(unescape(encodeURIComponent(credentials.username + ":" + credentials.password)));
}

export async function apiFetch<T>(
  path: string,
  credentials: Credentials,
  init: RequestInit = {}
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", authHeader(credentials));
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  const response = await fetch(API_BASE + path, {
    ...init,
    headers,
    credentials: "same-origin"
  });

  if (response.status === 401) throw new Error("AUTH_REQUIRED");
  if (response.status === 403) throw new Error("ACCESS_DENIED");
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed: ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  resources: (c: Credentials) => apiFetch<ResourceInfo[]>("/analytics/resources", c),
  register: (body: { username: string; password: string }) =>
    fetch(API_BASE + "/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify(body)
    }).then(async response => {
      if (!response.ok) throw new Error((await response.text()) || `Registration failed: ${response.status}`);
      return response.json();
    }),
  dashboard: (c: Credentials) => apiFetch<Dashboard>("/analytics/dashboard", c),
  catalog: (c: Credentials) => apiFetch<EntityCatalog>("/analytics/catalog", c),
  rows: (slug: string, page: number, size: number, c: Credentials) =>
    apiFetch<PageResponse>(`/resources/${encodeURIComponent(slug)}?page=${page}&size=${size}`, c),
  aggregate: (slug: string, body: object, c: Credentials) =>
    apiFetch<AggregateReport>(`/analytics/resources/${encodeURIComponent(slug)}/aggregate`, c, {
      method: "POST",
      body: JSON.stringify(body)
    }),
  biQuery: (slug: string, body: object, c: Credentials) =>
    apiFetch<BiReport>(`/bi/resources/${encodeURIComponent(slug)}/query`, c, {
      method: "POST",
      body: JSON.stringify(body)
    })
};
