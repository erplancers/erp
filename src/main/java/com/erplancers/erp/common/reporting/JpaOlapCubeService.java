package com.erplancers.erp.common.reporting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds logical OLAP cubes automatically from the JPA model.
 *
 * <p>Every registered ERP entity becomes a cube. Scalar JPA attributes become
 * dimensions and numeric scalar attributes become measures. Relationship
 * attributes discovered by {@link EntityDataAnalysisService} are exposed as
 * dotted dimensions/measures, for example {@code customer.name} or
 * {@code product.amount}.</p>
 *
 * <p>The cubes are virtual: no separate star-schema tables are required.
 * Queries are translated to the existing JPA Criteria aggregation engine.</p>
 */
@Service
@Transactional(readOnly = true)
public class JpaOlapCubeService {

    private final EntityDataAnalysisService entityAnalysis;
    private final OlapAnalysisEngine olap;

    public JpaOlapCubeService(
            EntityDataAnalysisService entityAnalysis,
            OlapAnalysisEngine olap) {
        this.entityAnalysis = entityAnalysis;
        this.olap = olap;
    }

    /**
     * Creates a cube definition for every registered JPA entity.
     */
    public AutoCubeCatalog cubes() {
        EntityDataAnalysisService.EntityCatalog catalog = entityAnalysis.catalog();

        List<AutoCube> cubes = catalog.resources().stream()
                .map(this::createCube)
                .toList();

        return new AutoCubeCatalog(cubes.size(), cubes);
    }

    /**
     * Creates one logical cube from the current JPA metadata.
     */
    public AutoCube cube(String resource) {
        EntityDataAnalysisService.EntityCatalog catalog = entityAnalysis.catalog();

        EntityDataAnalysisService.ResourceSchema schema = catalog.resources().stream()
                .filter(r -> r.slug().equals(resource))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown ERP resource: " + resource));

        return createCube(schema);
    }

    /**
     * Queries an automatically generated cube. Requested fields are validated
     * against the JPA-derived cube definition before the query reaches JPA.
     */
    public OlapAnalysisEngine.CubeResult query(
            String cubeName,
            AutoCubeQueryRequest request) {

        AutoCube cube = cube(cubeName);

        List<String> dimensions = request.dimensions().isEmpty()
                ? List.of()
                : validateDimensions(cube, request.dimensions());

        List<String> measures = request.measures().isEmpty()
                ? List.of("count")
                : validateMeasures(cube, request.measures());

        return olap.cube(
                cube.resource(),
                new OlapAnalysisEngine.CubeRequest(
                        dimensions,
                        measures,
                        request.filters(),
                        request.orderBy(),
                        request.limit(),
                        request.includeTotals(),
                        request.includeRollup(),
                        request.pivotDimension()));
    }

    private AutoCube createCube(EntityDataAnalysisService.ResourceSchema schema) {
        List<String> dimensions = schema.fields().stream()
                .map(EntityDataAnalysisService.FieldSchema::name)
                .toList();

        List<String> measures = new ArrayList<>();
        measures.add("count");

        schema.fields().stream()
                .filter(field -> isNumeric(field.javaType()))
                .forEach(field -> {
                    measures.add("sum:" + field.name());
                    measures.add("avg:" + field.name());
                    measures.add("min:" + field.name());
                    measures.add("max:" + field.name());
                });

        schema.fields().stream()
                .map(EntityDataAnalysisService.FieldSchema::name)
                .forEach(field -> measures.add("countDistinct:" + field));

        Set<String> relationships = schema.fields().stream()
                .map(EntityDataAnalysisService.FieldSchema::name)
                .filter(name -> name.contains("."))
                .map(name -> name.substring(0, name.indexOf('.')))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return new AutoCube(
                schema.slug(),
                schema.slug(),
                schema.module(),
                schema.entity(),
                dimensions,
                List.copyOf(new LinkedHashSet<>(measures)),
                List.copyOf(relationships));
    }

    private List<String> validateDimensions(AutoCube cube, List<String> requested) {
        for (String dimension : requested) {
            if (!cube.dimensions().contains(dimension)) {
                throw new IllegalArgumentException(
                        "Unknown OLAP dimension '" + dimension
                                + "' for cube '" + cube.name() + "'");
            }
        }
        return List.copyOf(requested);
    }

    private List<String> validateMeasures(AutoCube cube, List<String> requested) {
        for (String measure : requested) {
            if (!cube.measures().contains(measure)
                    && !measure.equalsIgnoreCase("count")) {
                throw new IllegalArgumentException(
                        "Unknown OLAP measure '" + measure
                                + "' for cube '" + cube.name() + "'");
            }
        }
        return List.copyOf(requested);
    }

    private boolean isNumeric(String javaType) {
        return Set.of(
                "byte", "short", "int", "long", "float", "double",
                "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
                "java.lang.Long", "java.lang.Float", "java.lang.Double",
                "java.math.BigDecimal", "java.math.BigInteger"
        ).contains(javaType);
    }

    public record AutoCubeCatalog(
            int cubeCount,
            List<AutoCube> cubes) {
    }

    public record AutoCube(
            String name,
            String resource,
            String module,
            String entity,
            List<String> dimensions,
            List<String> measures,
            List<String> relationships) {
    }

    public record AutoCubeQueryRequest(
            List<String> dimensions,
            List<String> measures,
            Map<String, String> filters,
            String orderBy,
            Integer limit,
            Boolean includeTotals,
            Boolean includeRollup,
            String pivotDimension) {

        public AutoCubeQueryRequest {
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            measures = measures == null ? List.of() : List.copyOf(measures);
            filters = filters == null ? Map.of() : Map.copyOf(filters);
            orderBy = orderBy == null ? "" : orderBy;
            limit = limit == null || limit <= 0 ? 100 : Math.min(limit, 1000);
            includeTotals = Boolean.TRUE.equals(includeTotals);
            includeRollup = Boolean.TRUE.equals(includeRollup);
            pivotDimension = pivotDimension == null ? "" : pivotDimension;
        }
    }
}
