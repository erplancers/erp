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
import type {
  AutoOlapCatalog,
  AutoOlapCube,
  AutoOlapRelationDiscovery
} from "../types";

type Row = Record<string, unknown>;

type QueryResult = {
  rows?: Row[];
  data?: Row[];
  cells?: Row[];
  grandTotal?: Row;
};

type FilterOperator =
  | "eq"
  | "ne"
  | "like"
  | "gt"
  | "gte"
  | "lt"
  | "lte"
  | "isnull"
  | "notnull";

const FILTER_OPERATORS: Array<{ label: string; value: FilterOperator }> = [
  { label: "Equals", value: "eq" },
  { label: "Not equal", value: "ne" },
  { label: "Contains", value: "like" },
  { label: "Greater than", value: "gt" },
  { label: "Greater or equal", value: "gte" },
  { label: "Less than", value: "lt" },
  { label: "Less or equal", value: "lte" },
  { label: "Is null", value: "isnull" },
  { label: "Is not null", value: "notnull" }
];

function rowsOf(result: QueryResult | null): Row[] {
  return result?.rows ?? result?.data ?? result?.cells ?? [];
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function options(values: string[]) {
  return values.map((value) => ({ label: value, value }));
}

export function OlapPage() {
  const { credentials } = useAuth();

  const [cubeName, setCubeName] = useState("");
  const [dimensions, setDimensions] = useState<string[]>([]);
  const [measures, setMeasures] = useState<string[]>([]);

  const [filterField, setFilterField] = useState("");
  const [filterOperator, setFilterOperator] =
    useState<FilterOperator>("eq");
  const [filterValue, setFilterValue] = useState("");
  const [filters, setFilters] = useState<Record<string, string>>({});

  const [orderBy, setOrderBy] = useState("");
  const [limit, setLimit] = useState(100);
  const [includeTotals, setIncludeTotals] = useState(true);
  const [includeRollup, setIncludeRollup] = useState(false);
  const [pivotDimension, setPivotDimension] = useState("");
  const [result, setResult] = useState<QueryResult | null>(null);
  const [queryError, setQueryError] = useState("");

  const [reportDepth, setReportDepth] = useState(2);
  const [reportName, setReportName] = useState("");
  const [reportRelations, setReportRelations] = useState<string[]>([]);
  const [reportDimensions, setReportDimensions] = useState<string[]>([]);
  const [reportMeasure, setReportMeasure] = useState("");
  const [reportResult, setReportResult] = useState<QueryResult | null>(null);
  const [reportError, setReportError] = useState("");

  const catalog = useQuery<AutoOlapCatalog>({
    queryKey: ["auto-olap-cubes", credentials?.username, reportDepth],
    queryFn: () => api.autoOlapCubes(credentials!, reportDepth),
    enabled: Boolean(credentials),
    staleTime: 30_000
  });

  const cubes = catalog.data?.cubes ?? [];
  const cube = cubes.find((item) => item.name === cubeName);

  const relationQueries = useQuery<AutoOlapRelationDiscovery[]>({
    queryKey: [
      "auto-olap-report-relations",
      credentials?.username,
      cubeName,
      reportRelations,
      reportDepth
    ],
    queryFn: async () =>
      Promise.all(
        reportRelations.map((relation) =>
          api.autoOlapRelations(
            cubeName,
            relation,
            credentials!,
            reportDepth
          )
        )
      ),
    enabled:
      Boolean(credentials) &&
      Boolean(cubeName) &&
      reportRelations.length > 0 &&
      reportDepth > 0,
    staleTime: 30_000
  });

  const dimensionOptions = useMemo(
    () => options(cube?.dimensions ?? []),
    [cube]
  );

  const measureOptions = useMemo(
    () => options(cube?.measures ?? []),
    [cube]
  );

  const reportDimensionOptions = useMemo(() => {
    if (!cube) return [];

    if (reportRelations.length === 0) {
      return options(cube.dimensions);
    }

    const related = cube.dimensions.filter((field) =>
      reportRelations.some(
        (relation) =>
          field === relation || field.startsWith(relation + ".")
      )
    );

    return options(related);
  }, [cube, reportRelations]);

  const rows = rowsOf(result);
  const reportRows = rowsOf(reportResult);
  const columns = rows.length ? Object.keys(rows[0]) : [];
  const reportColumns = reportRows.length
    ? Object.keys(reportRows[0])
    : [];

  function resetQuery() {
    setDimensions([]);
    setMeasures([]);
    setFilterField("");
    setFilterOperator("eq");
    setFilterValue("");
    setFilters({});
    setOrderBy("");
    setLimit(100);
    setIncludeTotals(true);
    setIncludeRollup(false);
    setPivotDimension("");
    setResult(null);
    setQueryError("");
  }

  function selectCube(value: string | null) {
    const name = value ?? "";
    const selected = cubes.find((item) => item.name === name);

    setCubeName(name);
    setDimensions(selected?.dimensions.slice(0, 2) ?? []);
    setMeasures(
      selected
        ? selected.measures.includes("count")
          ? ["count"]
          : selected.measures.slice(0, 1)
        : []
    );

    const defaultMeasure =
      selected?.measures.find((measure) => measure.startsWith("sum:")) ??
      selected?.measures[0] ??
      "";

    setReportMeasure(defaultMeasure);
    setReportRelations([]);
    setReportDimensions([]);
    setReportName("");
    setReportResult(null);
    setReportError("");
    resetQuery();
  }

  function addFilter() {
    if (!filterField) return;

    const needsValue =
      filterOperator !== "isnull" && filterOperator !== "notnull";

    if (needsValue && !filterValue.trim()) return;

    const key = `${filterField}:${filterOperator}`;

    setFilters((previous) => ({
      ...previous,
      [key]: needsValue ? filterValue : ""
    }));

    setFilterField("");
    setFilterValue("");
  }

  function removeFilter(key: string) {
    setFilters((previous) => {
      const next = { ...previous };
      delete next[key];
      return next;
    });
  }

  async function runQuery() {
    if (
      !credentials ||
      !cube ||
      dimensions.length === 0 ||
      measures.length === 0
    ) {
      return;
    }

    setQueryError("");
    setResult(null);

    try {
      const response = await api.autoOlapQuery(
        cube.name,
        {
          dimensions,
          measures,
          filters,
          orderBy: orderBy || undefined,
          limit,
          includeTotals,
          includeRollup,
          pivotDimension: pivotDimension || undefined
        },
        credentials
      );

      setResult(response as QueryResult);
    } catch (error) {
      setQueryError(errorMessage(error));
    }
  }

  async function generateReport() {
    if (
      !credentials ||
      !cube ||
      reportDimensions.length === 0 ||
      !reportMeasure
    ) {
      return;
    }

    setReportError("");
    setReportResult(null);

    try {
      const response = await api.autoOlapQuery(
        cube.name,
        {
          dimensions: reportDimensions,
          measures: [reportMeasure],
          limit: 1000,
          includeTotals: true,
          includeRollup: false
        },
        credentials
      );

      setReportResult(response as QueryResult);
    } catch (error) {
      setReportError(errorMessage(error));
    }
  }

  if (catalog.isPending) {
    return (
      <div className="flex justify-content-center p-4">
        <ProgressSpinner />
      </div>
    );
  }

  if (catalog.isError) {
    return (
      <Message
        severity="error"
        text={errorMessage(catalog.error)}
      />
    );
  }

  return (
    <>
      <div className="section-heading">
        <div>
          <div className="eyebrow">OLAP / CUBES</div>
          <h2>OLAP Cube Explorer</h2>
          <p>
            Explore virtual cubes generated from JPA entities and their
            relationships.
          </p>
        </div>
        <Tag value={`${cubes.length} cubes`} icon="pi pi-database" />
      </div>

      <TabView>
        <TabPanel header="Cube Query" leftIcon="pi pi-sliders-h">
          <div className="olap-layout">
            <Card title="Query Builder">
              <div className="olap-form">
                <label>
                  Cube
                  <Dropdown
                    value={cubeName}
                    options={options(cubes.map((item) => item.name))}
                    onChange={(event) =>
                      selectCube(event.value as string | null)
                    }
                    placeholder="Select a cube"
                    filter
                    showClear
                    className="field-control"
                  />
                </label>

                {cube && (
                  <div className="olap-cube-info">
                    <span>
                      <b>{cube.entity}</b> · {cube.module}
                    </span>
                    <span>
                      {cube.relationships.length} relationships
                    </span>
                  </div>
                )}

                <label>
                  Dimensions
                  <MultiSelect
                    value={dimensions}
                    options={dimensionOptions}
                    onChange={(event) =>
                      setDimensions((event.value ?? []) as string[])
                    }
                    placeholder="Select dimensions"
                    display="chip"
                    filter
                    className="field-control"
                  />
                </label>

                <label>
                  Measures
                  <MultiSelect
                    value={measures}
                    options={measureOptions}
                    onChange={(event) =>
                      setMeasures((event.value ?? []) as string[])
                    }
                    placeholder="Select measures"
                    display="chip"
                    filter
                    className="field-control"
                  />
                </label>

                <div className="olap-filter-row">
                  <label>
                    Filter field
                    <Dropdown
                      value={filterField}
                      options={dimensionOptions}
                      onChange={(event) =>
                        setFilterField((event.value as string | null) ?? "")
                      }
                      placeholder="Field"
                      filter
                      showClear
                      className="field-control"
                    />
                  </label>

                  <label>
                    Operator
                    <Dropdown
                      value={filterOperator}
                      options={FILTER_OPERATORS}
                      onChange={(event) =>
                        setFilterOperator(
                          event.value as FilterOperator
                        )
                      }
                      className="field-control"
                    />
                  </label>

                  <label>
                    Value
                    <InputText
                      value={filterValue}
                      onChange={(event) =>
                        setFilterValue(event.target.value)
                      }
                      disabled={
                        filterOperator === "isnull" ||
                        filterOperator === "notnull"
                      }
                    />
                  </label>

                  <Button
                    label="Add"
                    icon="pi pi-plus"
                    outlined
                    onClick={addFilter}
                    disabled={
                      !filterField ||
                      (filterOperator !== "isnull" &&
                        filterOperator !== "notnull" &&
                        !filterValue.trim())
                    }
                  />
                </div>

                {Object.entries(filters).length > 0 && (
                  <div className="olap-filter-chips">
                    {Object.entries(filters).map(([key, value]) => (
                      <span key={key} className="inline-flex align-items-center gap-1">
                        <Tag
                          value={
                            value ? `${key} = ${value}` : key
                          }
                        />
                        <Button
                          icon="pi pi-times"
                          text
                          rounded
                          severity="secondary"
                          aria-label={`Remove filter ${key}`}
                          onClick={() => removeFilter(key)}
                        />
                      </span>
                    ))}
                  </div>
                )}

                <div className="olap-query-row">
                  <label>
                    Order by
                    <Dropdown
                      value={orderBy}
                      options={options(measures)}
                      onChange={(event) =>
                        setOrderBy((event.value as string | null) ?? "")
                      }
                      placeholder="Measure"
                      showClear
                      className="field-control"
                    />
                  </label>

                  <label>
                    Limit
                    <InputNumber
                      value={limit}
                      onValueChange={(event) => {
                        const value = event.value ?? 100;
                        setLimit(Math.min(1000, Math.max(1, value)));
                      }}
                      min={1}
                      max={1000}
                    />
                  </label>

                  <label>
                    Pivot
                    <Dropdown
                      value={pivotDimension}
                      options={options(dimensions)}
                      onChange={(event) =>
                        setPivotDimension(
                          (event.value as string | null) ?? ""
                        )
                      }
                      placeholder="Optional"
                      showClear
                      className="field-control"
                    />
                  </label>
                </div>

                <div className="olap-options">
                  <div>
                    <Checkbox
                      inputId="olap-totals"
                      checked={includeTotals}
                      onChange={(event) =>
                        setIncludeTotals(Boolean(event.checked))
                      }
                    />
                    <label htmlFor="olap-totals">Grand totals</label>
                  </div>

                  <div>
                    <Checkbox
                      inputId="olap-rollup"
                      checked={includeRollup}
                      onChange={(event) =>
                        setIncludeRollup(Boolean(event.checked))
                      }
                    />
                    <label htmlFor="olap-rollup">Rollup rows</label>
                  </div>
                </div>

                <Button
                  label="Run Cube Query"
                  icon="pi pi-play"
                  onClick={runQuery}
                  disabled={
                    !cube ||
                    dimensions.length === 0 ||
                    measures.length === 0
                  }
                />
              </div>
            </Card>

            <Card title="Cube Schema">
              {cube ? (
                <div className="olap-schema">
                  <div>
                    <b>Entity</b>
                    <span>{cube.entity}</span>
                  </div>
                  <div>
                    <b>Resource</b>
                    <span>{cube.resource}</span>
                  </div>
                  <div>
                    <b>Relationships</b>
                    <span>
                      {cube.relationships.join(", ") || "None"}
                    </span>
                  </div>
                  <div>
                    <b>Dimensions</b>
                    <span>{cube.dimensions.length}</span>
                  </div>
                  <div>
                    <b>Measures</b>
                    <span>{cube.measures.join(", ") || "None"}</span>
                  </div>
                </div>
              ) : (
                <p>Select a cube to inspect its generated schema.</p>
              )}
            </Card>
          </div>

          {queryError && (
            <Message
              severity="error"
              text={queryError}
              style={{ marginTop: "1rem" }}
            />
          )}

          {result && (
            <Card title="Query Result">
              <div className="olap-result-meta">
                <span>{rows.length} rows</span>
                {result.grandTotal && (
                  <Tag
                    value="Grand total included"
                    severity="success"
                  />
                )}
              </div>

              {rows.length > 0 ? (
                <DataTable
                  value={rows}
                  paginator
                  rows={20}
                  scrollable
                  size="small"
                  stripedRows
                >
                  {columns.map((column) => (
                    <Column
                      key={column}
                      field={column}
                      header={column}
                      sortable
                    />
                  ))}
                </DataTable>
              ) : (
                <Message
                  severity="info"
                  text="The query returned no rows."
                />
              )}
            </Card>
          )}
        </TabPanel>

        <TabPanel header="Cube Catalog" leftIcon="pi pi-th-large">
          <DataTable
            value={cubes}
            paginator
            rows={15}
            stripedRows
            responsiveLayout="scroll"
          >
            <Column field="name" header="Cube" sortable />
            <Column field="module" header="Module" sortable />
            <Column field="entity" header="Entity" sortable />
            <Column
              field="dimensions"
              header="Dimensions"
              body={(row: AutoOlapCube) => row.dimensions.length}
            />
            <Column
              field="measures"
              header="Measures"
              body={(row: AutoOlapCube) => row.measures.length}
            />
            <Column
              field="relationships"
              header="Relationships"
              body={(row: AutoOlapCube) => row.relationships.length}
            />
          </DataTable>
        </TabPanel>

        <TabPanel header="Auto Reports" leftIcon="pi pi-chart-bar">
          <Card title="Report from JPA relationships">
            <p className="olap-help">
              Build a report from discovered JPA relationship paths.
              Select a fact cube, inspect its relationships, then choose
              the dimensions and measure for the report.
            </p>

            <div className="olap-query-row">
              <label>
                Fact cube
                <Dropdown
                  value={cubeName}
                  options={options(cubes.map((item) => item.name))}
                  onChange={(event) =>
                    selectCube(event.value as string | null)
                  }
                  placeholder="Select a fact entity"
                  filter
                  showClear
                  className="field-control"
                />
              </label>

              <label>
                JPA relationships
                <MultiSelect
                  value={reportRelations}
                  options={options(cube?.relationships ?? [])}
                  onChange={(event) => {
                    setReportRelations(
                      (event.value ?? []) as string[]
                    );
                    setReportDimensions([]);
                    setReportResult(null);
                    setReportError("");
                  }}
                  placeholder="Customer / Product / Order"
                  display="chip"
                  filter
                  className="field-control"
                />
              </label>

              <label>
                Discovery depth
                <InputNumber
                  value={reportDepth}
                  onValueChange={(event) => {
                    const value = event.value ?? 2;
                    setReportDepth(Math.min(6, Math.max(1, value)));
                  }}
                  min={1}
                  max={6}
                />
              </label>
            </div>

            {relationQueries.isPending && (
              <ProgressSpinner
                style={{ width: "24px", height: "24px" }}
              />
            )}

            {relationQueries.isError && (
              <Message
                severity="error"
                text={errorMessage(relationQueries.error)}
              />
            )}

            {cube && (
              <label>
                Report dimensions
                <MultiSelect
                  value={reportDimensions}
                  options={reportDimensionOptions}
                  onChange={(event) =>
                    setReportDimensions(
                      (event.value ?? []) as string[]
                    )
                  }
                  placeholder="Select customer/product fields"
                  display="chip"
                  filter
                  className="field-control"
                />
              </label>
            )}

            {relationQueries.data && relationQueries.data.length > 0 && (
              <div className="olap-schema">
                {relationQueries.data.map((discovery) => (
                  <div key={discovery.relation}>
                    <b>{discovery.relation}</b>
                    <span>
                      {discovery.paths.length > 0
                        ? discovery.paths
                            .map(
                              (path) =>
                                `${path.path} → ${path.targetEntity}`
                            )
                            .join(", ")
                        : "No paths discovered"}
                    </span>
                  </div>
                ))}
              </div>
            )}

            {reportError && (
              <Message severity="error" text={reportError} />
            )}

            <div className="olap-query-row">
              <label>
                Report name
                <InputText
                  value={reportName}
                  onChange={(event) =>
                    setReportName(event.target.value)
                  }
                  placeholder="Income per product by customer"
                />
              </label>

              <label>
                Measure
                <Dropdown
                  value={reportMeasure}
                  options={measureOptions}
                  onChange={(event) =>
                    setReportMeasure(
                      (event.value as string | null) ?? ""
                    )
                  }
                  placeholder="Select a measure"
                  className="field-control"
                />
              </label>

              <Button
                label="Generate Report"
                icon="pi pi-chart-bar"
                onClick={generateReport}
                disabled={
                  !cube ||
                  reportDimensions.length === 0 ||
                  !reportMeasure
                }
              />
            </div>
          </Card>

          {reportResult && (
            <Card
              title={
                reportName.trim() ||
                (cube ? `${cube.entity} report` : "OLAP report")
              }
            >
              <div className="olap-result-meta">
                <span>{reportRows.length} rows</span>
                <Tag value={reportMeasure} />
              </div>

              {reportRows.length > 0 ? (
                <DataTable
                  value={reportRows}
                  paginator
                  rows={20}
                  scrollable
                  size="small"
                  stripedRows
                >
                  {reportColumns.map((column) => (
                    <Column
                      key={column}
                      field={column}
                      header={column}
                      sortable
                    />
                  ))}
                </DataTable>
              ) : (
                <Message
                  severity="info"
                  text="The generated report returned no rows."
                />
              )}
            </Card>
          )}
        </TabPanel>
      </TabView>
    </>
  );
}
