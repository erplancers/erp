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

type QueryResult = {
  rows?: Array<Record<string, unknown>>;
  data?: Array<Record<string, unknown>>;
  cells?: Array<Record<string, unknown>>;
  grandTotal?: Record<string, unknown>;
};

const FILTER_OPERATORS = [
  "eq",
  "ne",
  "like",
  "gt",
  "gte",
  "lt",
  "lte",
  "isnull",
  "notnull"
];

function resultRows(result: QueryResult | null): Array<Record<string, unknown>> {
  return result?.rows ?? result?.data ?? result?.cells ?? [];
}

export function OlapPage() {
  const { credentials } = useAuth();

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
  const [result, setResult] = useState<QueryResult | null>(null);
  const [error, setError] = useState("");

  const [reportDepth, setReportDepth] = useState(2);
  const [reportName, setReportName] = useState("");
  const [reportRelations, setReportRelations] = useState<string[]>([]);
  const [reportDimensions, setReportDimensions] = useState<string[]>([]);
  const [reportMeasure, setReportMeasure] = useState("count");
  const [reportResult, setReportResult] = useState<QueryResult | null>(null);

  const catalog = useQuery({
    queryKey: ["auto-olap-cubes", credentials?.username],
    queryFn: () => api.autoOlapCubes(credentials!, reportDepth),
    enabled: !!credentials
  });

  const cubes: AutoOlapCube[] =
    (catalog.data as AutoOlapCatalog | undefined)?.cubes ?? [];

  const cube = cubes.find((item) => item.name === cubeName);

  const relationQueries = useQuery({
    queryKey: [
      "auto-olap-report-relations",
      cubeName,
      reportRelations,
      reportDepth
    ],
    queryFn: async (): Promise<AutoOlapRelationDiscovery[]> =>
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
      !!credentials &&
      !!cubeName &&
      reportRelations.length > 0 &&
      reportDepth > 0
  });

  const dimensionOptions = useMemo(
    () =>
      (cube?.dimensions ?? []).map((value) => ({
        label: value,
        value
      })),
    [cube]
  );

  const measureOptions = useMemo(
    () =>
      (cube?.measures ?? []).map((value) => ({
        label: value,
        value
      })),
    [cube]
  );

  const filterOptions = useMemo(
    () =>
      (cube?.dimensions ?? []).map((value) => ({
        label: value,
        value
      })),
    [cube]
  );

  const reportDimensionOptions = useMemo(() => {
    if (!cube) return [];

    if (reportRelations.length === 0) {
      return cube.dimensions.map((value) => ({
        label: value,
        value
      }));
    }

    return cube.dimensions
      .filter((field) =>
        reportRelations.some(
          (relation) =>
            field === relation || field.startsWith(relation + ".")
        )
      )
      .map((value) => ({
        label: value,
        value
      }));
  }, [cube, reportRelations]);

  const rows = resultRows(result);
  const reportRows = resultRows(reportResult);
  const columns = rows.length ? Object.keys(rows[0]) : [];
  const reportColumns = reportRows.length
    ? Object.keys(reportRows[0])
    : [];

  function resetQueryState() {
    setFilters({});
    setFilterField("");
    setFilterValue("");
    setFilterOperator("eq");
    setOrderBy("");
    setPivotDimension("");
    setResult(null);
    setError("");
  }

  function selectCube(value: string) {
    const selected = cubes.find((item) => item.name === value);

    setCubeName(value);
    setDimensions(selected?.dimensions.slice(0, 2) ?? []);
    setMeasures(
      selected?.measures.includes("count")
        ? ["count"]
        : selected?.measures.slice(0, 1) ?? []
    );
    setReportMeasure(
      selected?.measures.find((measure) => measure.startsWith("sum:")) ??
        selected?.measures[0] ??
        "count"
    );
    setReportRelations([]);
    setReportDimensions([]);
    setReportName("");
    setReportResult(null);
    resetQueryState();
  }

  function addFilter() {
    if (!filterField) return;

    const key = filterField + ":" + filterOperator;
    setFilters((previous) => ({
      ...previous,
      [key]: filterOperator === "isnull" || filterOperator === "notnull"
        ? ""
        : filterValue
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
    if (!cubeName || !cube || dimensions.length === 0 || measures.length === 0) {
      return;
    }

    try {
      setError("");
      setResult(
        await api.autoOlapQuery(
          cubeName,
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
          credentials!
        )
      );
    } catch (cause) {
      setResult(null);
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  async function generateReport() {
    if (
      !cube ||
      reportDimensions.length === 0 ||
      !reportMeasure ||
      !credentials
    ) {
      return;
    }

    try {
      setError("");
      setReportResult(
        await api.autoOlapQuery(
          cube.name,
          {
            dimensions: reportDimensions,
            measures: [reportMeasure],
            limit: 1000,
            includeTotals: true,
            includeRollup: false
          },
          credentials
        )
      );
    } catch (cause) {
      setReportResult(null);
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  if (catalog.isPending) {
    return <ProgressSpinner />;
  }

  if (catalog.isError) {
    return (
      <Message
        severity="error"
        text={
          catalog.error instanceof Error
            ? catalog.error.message
            : String(catalog.error)
        }
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
        <Tag
          value={`${cubes.length} cubes`}
          icon="pi pi-database"
        />
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
                    options={cubes.map((item) => ({
                      label: item.name,
                      value: item.name
                    }))}
                    onChange={(event) => selectCube(event.value)}
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
                    onChange={(event) => setDimensions(event.value)}
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
                    onChange={(event) => setMeasures(event.value)}
                    placeholder="Select measures"
                    display="chip"
                    className="field-control"
                  />
                </label>

                <div className="olap-filter-row">
                  <label>
                    Filter field
                    <Dropdown
                      value={filterField}
                      options={filterOptions}
                      onChange={(event) => setFilterField(event.value)}
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
                      options={FILTER_OPERATORS.map((value) => ({
                        label: value,
                        value
                      }))}
                      onChange={(event) =>
                        setFilterOperator(event.value)
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
                        !filterValue)
                    }
                  />
                </div>

                {Object.keys(filters).length > 0 && (
                  <div className="olap-filter-chips">
                    {Object.entries(filters).map(([key, value]) => (
                      <Tag
                        key={key}
                        value={
                          value
                            ? `${key} = ${value}`
                            : key
                        }
                      />
                    ))}
                  </div>
                )}

                <div className="olap-query-row">
                  <label>
                    Order by
                    <Dropdown
                      value={orderBy}
                      options={measures.map((value) => ({
                        label: value,
                        value
                      }))}
                      onChange={(event) => setOrderBy(event.value)}
                      placeholder="Measure"
                      showClear
                      className="field-control"
                    />
                  </label>

                  <label>
                    Limit
                    <InputNumber
                      value={limit}
                      onValueChange={(event) =>
                        setLimit(Math.min(1000, Math.max(1, event.value ?? 100)))
                      }
                      min={1}
                      max={1000}
                    />
                  </label>

                  <label>
                    Pivot
                    <Dropdown
                      value={pivotDimension}
                      options={dimensions.map((value) => ({
                        label: value,
                        value
                      }))}
                      onChange={(event) =>
                        setPivotDimension(event.value)
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
                        setIncludeTotals(!!event.checked)
                      }
                    />
                    <label htmlFor="olap-totals">Grand totals</label>
                  </div>
                  <div>
                    <Checkbox
                      inputId="olap-rollup"
                      checked={includeRollup}
                      onChange={(event) =>
                        setIncludeRollup(!!event.checked)
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
                    <span>{cube.measures.join(", ")}</span>
                  </div>
                </div>
              ) : (
                <p>Select a cube to inspect its generated schema.</p>
              )}
            </Card>
          </div>

          {error && (
            <Message
              severity="error"
              text={error}
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
                <Message severity="info" text="The query returned no rows." />
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
              Build a report directly from discovered JPA relationship paths.
              For example: <b>income per product by customer</b>.
            </p>

            <div className="olap-query-row">
              <label>
                Fact cube
                <Dropdown
                  value={cubeName}
                  options={cubes.map((item) => ({
                    label: item.name,
                    value: item.name
                  }))}
                  onChange={(event) => selectCube(event.value)}
                  placeholder="Select a fact entity"
                  filter
                  className="field-control"
                />
              </label>

              <label>
                JPA relationships
                <MultiSelect
                  value={reportRelations}
                  options={(cube?.relationships ?? []).map((value) => ({
                    label: value,
                    value
                  }))}
                  onChange={(event) => {
                    setReportRelations(event.value);
                    setReportDimensions([]);
                    setReportResult(null);
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
                  onValueChange={(event) =>
                    setReportDepth(
                      Math.min(6, Math.max(1, event.value ?? 2))
                    )
                  }
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
                text={
                  relationQueries.error instanceof Error
                    ? relationQueries.error.message
                    : String(relationQueries.error)
                }
              />
            )}

            {cube && reportRelations.length > 0 && (
              <label>
                Report dimensions
                <MultiSelect
                  value={reportDimensions}
                  options={reportDimensionOptions}
                  onChange={(event) =>
                    setReportDimensions(event.value)
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
                      {discovery.paths.length
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
                    setReportMeasure(event.value)
                  }
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
                reportName.trim() || cube?.entity + " report"
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
