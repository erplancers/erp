import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Dropdown } from "primereact/dropdown";
import { MultiSelect } from "primereact/multiselect";
import { InputText } from "primereact/inputtext";
import { InputNumber } from "primereact/inputnumber";
import { Checkbox } from "primereact/checkbox";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Message } from "primereact/message";
import { ProgressSpinner } from "primereact/progressspinner";
import { Tag } from "primereact/tag";
import { TabView, TabPanel } from "primereact/tabview";
import { useAuth } from "../auth";
import { api } from "../api";
import type { AutoOlapCube, AutoOlapCatalog } from "../types";

export function OlapPage() {
  const { credentials } = useAuth();
  const catalog = useQuery({
    queryKey: ["auto-olap-cubes"],
    queryFn: () => api.autoOlapCubes(credentials!),
    enabled: !!credentials
  });

  const [cubeName, setCubeName] = useState("");
  const [dimensions, setDimensions] = useState<string[]>([]);
  const [measures, setMeasures] = useState<string[]>([]);
  const [filterField, setFilterField] = useState("");
  const [filterOperator, setFilterOperator] = useState("eq");
  const [filterValue, setFilterValue] = useState("");
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [orderBy, setOrderBy] = useState("");
  const [limit, setLimit] = useState(100);
  const [includeTotals, setIncludeTotals] = useState(true);
  const [includeRollup, setIncludeRollup] = useState(false);
  const [pivotDimension, setPivotDimension] = useState("");
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState("");
  const [reportDepth, setReportDepth] = useState(2);
  const [reportName, setReportName] = useState("");
  const [reportRelations, setReportRelations] = useState<string[]>([]);
  const [reportDimensions, setReportDimensions] = useState<string[]>([]);
  const [reportMeasure, setReportMeasure] = useState("count");

  const cubes: AutoOlapCube[] = (catalog.data as AutoOlapCatalog | undefined)?.cubes || [];
  const cube = cubes.find(c => c.name === cubeName);
  const relationQueries = useQuery({
    queryKey: ["auto-olap-report-relations", cubeName, reportRelations, reportDepth],
    queryFn: async () => Promise.all(reportRelations.map(relation => api.autoOlapRelations(cubeName, relation, credentials!, reportDepth))),
    enabled: !!credentials && !!cubeName && reportRelations.length > 0
  });

  const dimensionOptions = useMemo(
    () => (cube?.dimensions || []).map(value => ({ label: value, value })),
    [cube]
  );
  const measureOptions = useMemo(
    () => (cube?.measures || []).map(value => ({ label: value, value })),
    [cube]
  );
  const filterOptions = useMemo(
    () => (cube?.dimensions || []).map(value => ({ label: value, value })),
    [cube]
  );

  function selectCube(value: string) {
    const selected = cubes.find(c => c.name === value);
    setCubeName(value);
    setDimensions(selected?.dimensions.slice(0, 2) || []);
    setMeasures(selected?.measures.includes("count") ? ["count"] : (selected?.measures.slice(0, 1) || []));
    setFilters({});
    setFilterField("");
    setFilterValue("");
    setOrderBy("");
    setPivotDimension("");
    setReportRelations([]);
    setReportDimensions([]);
    setReportName("");
    setReportMeasure(selected?.measures.find(m => m.startsWith("sum:")) || "count");
    setResult(null);
    setError("");
  }

  function addFilter() {
    if (!filterField || !filterValue) return;
    setFilters(previous => ({ ...previous, [filterField + ":" + filterOperator]: filterValue }));
    setFilterField("");
    setFilterValue("");
  }

  function removeFilter(key: string) {
    setFilters(previous => {
      const next = { ...previous };
      delete next[key];
      return next;
    });
  }

  function createAutoReport() {\n    if (!cube || reportDimensions.length === 0) return;\n    const finalName = reportName.trim() || cube.entity + " report";\n    setDimensions(reportDimensions);\n    setMeasures([reportMeasure]);\n    setReportName(finalName);\n    setResult(null);\n    setError("");\n  }\n\n  async function runQuery() {
    if (!cubeName || !cube || measures.length === 0) return;
    try {
      setError("");
      setResult(await api.autoOlapQuery(cubeName, {
        dimensions,
        measures,
        filters,
        orderBy: orderBy || undefined,
        limit,
        includeTotals,
        includeRollup,
        pivotDimension: pivotDimension || undefined
      }, credentials!));
    } catch (e) {
      setResult(null);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  if (catalog.isPending) return <ProgressSpinner />;
  if (catalog.isError) return <Message severity="error" text={catalog.error.message} />;

  const rows = result?.rows || result?.data || result?.cells || [];
  const columns = rows.length ? Object.keys(rows[0]) : [];

  return (
    <>
      <div className="section-heading">
        <div>
          <div className="eyebrow">OLAP / CUBES</div>
          <h2>OLAP Cube Explorer</h2>
          <p>Automatically generated virtual cubes from your JPA entities and relationships.</p>
        </div>
        <Tag value={`${cubes.length} cubes`} icon="pi pi-database" />
      </div>

      <TabView>
        <TabPanel header="Cube Query" leftIcon="pi pi-sliders-h">
          <div className="olap-layout">
            <Card title="Query Builder">
              <div className="olap-form">
                <label>Cube
                  <Dropdown value={cubeName} options={cubes.map(c => ({ label: c.name, value: c.name }))}
                    onChange={e => selectCube(e.value)} placeholder="Select a cube" filter className="field-control" />
                </label>

                {cube && <div className="olap-cube-info">
                  <span><b>{cube.entity}</b> · {cube.module}</span>
                  <span>{cube.relationships.length} relationships</span>
                </div>}

                <label>Dimensions
                  <MultiSelect value={dimensions} options={dimensionOptions} onChange={e => setDimensions(e.value)}
                    placeholder="Select dimensions" display="chip" className="field-control" />
                </label>

                <label>Measures
                  <MultiSelect value={measures} options={measureOptions} onChange={e => setMeasures(e.value)}
                    placeholder="Select measures" display="chip" className="field-control" />
                </label>

                <div className="olap-filter-row">
                  <label>Filter field
                    <Dropdown value={filterField} options={filterOptions} onChange={e => setFilterField(e.value)}
                      placeholder="Field" filter className="field-control" />
                  </label>
                  <label>Operator
                    <Dropdown value={filterOperator}
                      options={["eq","ne","like","gt","gte","lt","lte","isnull","notnull"].map(value => ({ label: value, value }))}
                      onChange={e => setFilterOperator(e.value)} className="field-control" />
                  </label>
                  <label>Value
                    <InputText value={filterValue} onChange={e => setFilterValue(e.target.value)}
                      disabled={filterOperator === "isnull" || filterOperator === "notnull"} />
                  </label>
                  <Button label="Add" icon="pi pi-plus" outlined onClick={addFilter} disabled={!filterField} />
                </div>

                {Object.keys(filters).length > 0 && (
                  <div className="olap-filter-chips">
                    {Object.entries(filters).map(([key, value]) => (
                      <Tag key={key} value={`${key} = ${value}`} removable onRemove={() => removeFilter(key)} />
                    ))}
                  </div>
                )}

                <div className="olap-query-row">
                  <label>Order by
                    <Dropdown value={orderBy} options={measures.map(m => ({ label: m, value: m }))}
                      onChange={e => setOrderBy(e.value)} placeholder="Measure" className="field-control" showClear />
                  </label>
                  <label>Limit
                    <InputNumber value={limit} onValueChange={e => setLimit(e.value || 100)} min={1} max={1000} />
                  </label>
                  <label>Pivot
                    <Dropdown value={pivotDimension} options={dimensions.map(d => ({ label: d, value: d }))}
                      onChange={e => setPivotDimension(e.value)} placeholder="Optional" showClear className="field-control" />
                  </label>
                </div>

                <div className="olap-options">
                  <div><Checkbox inputId="totals" checked={includeTotals} onChange={e => setIncludeTotals(!!e.checked)} /><label htmlFor="totals">Grand totals</label></div>
                  <div><Checkbox inputId="rollup" checked={includeRollup} onChange={e => setIncludeRollup(!!e.checked)} /><label htmlFor="rollup">Rollup rows</label></div>
                </div>

                <Button label="Run Cube Query" icon="pi pi-play" onClick={runQuery}
                  disabled={!cube || dimensions.length === 0 || measures.length === 0} />
              </div>
            </Card>

            <Card title="Cube Schema">
              {cube ? (
                <div className="olap-schema">
                  <div><b>Entity</b><span>{cube.entity}</span></div>
                  <div><b>Resource</b><span>{cube.resource}</span></div>
                  <div><b>Relationships</b><span>{cube.relationships.join(", ") || "None"}</span></div>
                  <div><b>Measures</b><span>{cube.measures.join(", ")}</span></div>
                </div>
              ) : <p>Select a cube to inspect its generated schema.</p>}
            </Card>
          </div>

          {error && <Message severity="error" text={error} style={{ marginTop: "1rem" }} />}

          {result && (
            <Card title="Query Result">
              <div className="olap-result-meta">
                <span>{rows.length} rows</span>
                {result.grandTotal && <Tag value="Grand total included" severity="success" />}
              </div>
              <DataTable value={rows} paginator rows={20} scrollable size="small">
                {columns.map(column => <Column key={column} field={column} header={column} sortable />)}
              </DataTable>
            </Card>
          )}
        </TabPanel>

        <TabPanel header="Cube Catalog" leftIcon="pi pi-th-large">
          <DataTable value={cubes} paginator rows={15} stripedRows responsiveLayout="scroll">
            <Column field="name" header="Cube" sortable />
            <Column field="module" header="Module" sortable />
            <Column field="entity" header="Entity" sortable />
            <Column field="dimensions" header="Dimensions" body={(row: AutoOlapCube) => row.dimensions.length} />
            <Column field="measures" header="Measures" body={(row: AutoOlapCube) => row.measures.length} />
            <Column field="relationships" header="Relationships" body={(row: AutoOlapCube) => row.relationships.length} />
          </DataTable>
        </TabPanel>
        <TabPanel header="Auto Reports" leftIcon="pi pi-chart-bar">
          <Card title="Create report from JPA relationships">
            <p className="olap-help">Select JPA relations and fields to create reports such as <b>income per product by customer</b>.</p>
            <div className="olap-query-row">
              <label>Cube
                <Dropdown value={cubeName} options={cubes.map(c => ({ label: c.name, value: c.name }))}
                  onChange={e => selectCube(e.value)} placeholder="Select a fact entity" filter className="field-control" />
              </label>
              <label>JPA relations
                <MultiSelect value={reportRelations}
                  options={(cube?.relationships || []).map(value => ({ label: value, value }))}
                  onChange={e => { setReportRelations(e.value); setReportDimensions([]); }}
                  placeholder="Customer / Product / Order" display="chip" className="field-control" />
              </label>
              <label>Depth
                <InputNumber value={reportDepth} onValueChange={e => setReportDepth(e.value || 2)} min={1} max={6} />
              </label>
            </div>
            {cube && reportRelations.length > 0 && (
              <label>Report dimensions
                <MultiSelect value={reportDimensions}
                  options={cube.dimensions.filter(field =>
                    reportRelations.some(relation => field.startsWith(relation + "."))
                  ).map(field => ({ label: field, value: field }))}
                  onChange={e => setReportDimensions(e.value)}
                  placeholder="Select customer/product fields" display="chip" className="field-control" />
              </label>
            )}
            <div className="olap-query-row">
              <label>Report name
                <InputText value={reportName} onChange={e => setReportName(e.target.value)}
                  placeholder="Income per product by customer" />
              </label>
              <label>Measure
                <Dropdown value={reportMeasure} options={(cube?.measures || []).map(value => ({ label: value, value }))}
                  onChange={e => setReportMeasure(e.value)} className="field-control" />
              </label>
              <Button label="Create Report" icon="pi pi-save" onClick={createAutoReport}
                disabled={!cube || reportDimensions.length === 0 || !reportMeasure} />
            </div>
          </Card>
        </TabPanel>

      </TabView>
    </>
  );
}
