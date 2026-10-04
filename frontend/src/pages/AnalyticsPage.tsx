import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Dropdown } from "primereact/dropdown";
import { MultiSelect } from "primereact/multiselect";
import { Button } from "primereact/button";
import { Card } from "primereact/card";
import { Message } from "primereact/message";
import { ProgressSpinner } from "primereact/progressspinner";
import { useAuth } from "../auth";
import { api } from "../api";

const NUMERIC_TYPES = new Set([
  "byte", "short", "int", "long", "float", "double",
  "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
  "java.lang.Long", "java.lang.Float", "java.lang.Double",
  "java.math.BigDecimal", "java.math.BigInteger"
]);

export function AnalyticsPage() {
  const { credentials } = useAuth();
  const cat = useQuery({
    queryKey: ["catalog"],
    queryFn: () => api.catalog(credentials!),
    enabled: !!credentials
  });

  const [slug, setSlug] = useState("");
  const [fields, setFields] = useState<string[]>([]);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  const schema = cat.data?.resources.find(r => r.slug === slug);

  // AdvancedAnalyticsEngine currently accepts only direct numeric
  // attributes; relationship/string fields belong to BI/profile instead.
  const opts = schema?.fields.filter(
    f => !f.name.includes(".") && NUMERIC_TYPES.has(f.javaType)
  ) || [];

  async function run() {
    if (!slug || fields.length === 0) return;

    setError(null);
    try {
      setResult(await api.advanced(
        slug,
        { fields, sampleSize: 10000, zScoreThreshold: 3 },
        credentials!
      ));
    } catch (e) {
      setResult(null);
      setError(e instanceof Error ? e.message : "Advanced analytics request failed");
    }
  }

  if (cat.isPending) return <ProgressSpinner />;

  return (
    <>
      <div className="section-heading">
        <div>
          <div className="eyebrow">ANALYTICS</div>
          <h2>Advanced Analytics</h2>
        </div>
      </div>

      <Card>
        <Dropdown
          value={slug}
          options={cat.data?.resources || []}
          optionLabel="slug"
          optionValue="slug"
          onChange={e => {
            setSlug(e.value);
            setFields([]);
            setResult(null);
            setError(null);
          }}
          placeholder="Resource"
        />

        <MultiSelect
          value={fields}
          options={opts}
          optionLabel="name"
          optionValue="name"
          onChange={e => setFields(e.value)}
          placeholder="Numeric fields"
          disabled={!slug || opts.length === 0}
        />

        <Button
          label="Analyze"
          icon="pi pi-chart-line"
          onClick={run}
          disabled={!slug || fields.length === 0}
        />
      </Card>

      {error && (
        <Message
          severity="error"
          text={error}
          style={{ marginTop: "1rem" }}
        />
      )}

      {result && (
        <Card>
          <pre>{JSON.stringify(result, null, 2)}</pre>
        </Card>
      )}
    </>
  );
}
