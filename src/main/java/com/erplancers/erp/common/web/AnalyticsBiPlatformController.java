package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.BiCalculatedMeasureService;
import com.erplancers.erp.common.reporting.ErpBiEngine;
import com.erplancers.erp.common.security.PolicyAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.librepdf.openpdf.text.Document;
import com.github.librepdf.openpdf.text.Paragraph;
import com.github.librepdf.openpdf.text.pdf.PdfPTable;
import com.github.librepdf.openpdf.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Saved reports, scoped sharing, calculated measures, query cache and report export APIs. */
@RestController
@RequestMapping("/api/bi")
public class AnalyticsBiPlatformController {
    private static final int MAX_ROWS = 5000;
    private final JdbcTemplate jdbc;
    private final ErpBiEngine bi;
    private final BiCalculatedMeasureService calculated;
    private final PolicyAuthorizationService authorization;
    private final ObjectMapper mapper;

    public AnalyticsBiPlatformController(JdbcTemplate jdbc, ErpBiEngine bi,
            BiCalculatedMeasureService calculated, PolicyAuthorizationService authorization, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.bi = bi;
        this.calculated = calculated;
        this.authorization = authorization;
        this.mapper = mapper;
    }

    @PostMapping("/resources/{slug}/query/cached")
    public QueryResult queryCached(Authentication auth, @PathVariable String slug,
                                   @RequestBody CachedQueryRequest request) {
        requireRead(auth, slug);
        if (request.query() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        int requestedLimit = Math.min(MAX_ROWS, Math.max(1, request.query().limit()));
        ErpBiEngine.BiQuery bounded = new ErpBiEngine.BiQuery(request.query().dimensions(),
                request.query().measures(), request.query().filters(), request.query().orderBy(),
                requestedLimit, request.query().includeRank(), request.query().includeShareOfTotal(),
                request.query().includeGrandTotal());
        String canonical = writeJson(Map.of("resource", slug, "query", bounded,
                "measures", request.calculatedMeasures() == null ? List.of() : request.calculatedMeasures(),
                "runningTotals", request.runningTotals() == null ? List.of() : request.runningTotals(),
                "comparisons", request.periodComparisons() == null ? List.of() : request.periodComparisons()));
        String hash = sha256(auth.getName() + "|" + canonical);
        List<QueryResult> cached = jdbc.query("""
                SELECT result_json, created_at FROM analytics_query_cache
                 WHERE cache_key = ? AND owner_username = ? AND expires_at > CURRENT_TIMESTAMP
                """, (rs, n) -> new QueryResult(mapper.readValue(rs.getString("result_json"), ErpBiEngine.BiReport.class),
                true, rs.getTimestamp("created_at").toInstant()), hash, auth.getName());
        if (!cached.isEmpty()) return cached.getFirst();

        ErpBiEngine.BiReport base = bi.query(slug, bounded);
        List<Map<String, Object>> rows = calculated.apply(base.rows(), request.calculatedMeasures(),
                request.runningTotals(), request.periodComparisons());
        ErpBiEngine.BiReport enriched = new ErpBiEngine.BiReport(base.resource(), base.module(), base.dimensions(),
                base.measures(), base.filters(), rows, base.grandTotal(), rows.size());
        String resultJson = writeJson(enriched);
        jdbc.update("""
                INSERT INTO analytics_query_cache(cache_key, owner_username, resource_slug, query_hash,
                    result_json, row_count, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                ON CONFLICT(cache_key) DO UPDATE SET result_json = EXCLUDED.result_json,
                    row_count = EXCLUDED.row_count, created_at = CURRENT_TIMESTAMP,
                    expires_at = CURRENT_TIMESTAMP + INTERVAL '5 minutes'
                """, hash, auth.getName(), slug, hash, resultJson, rows.size());
        return new QueryResult(enriched, false, Instant.now());
    }

    @PostMapping("/reports")
    public SavedReportView save(Authentication auth, @RequestBody SaveReportRequest request) {
        requireAllowed(auth, request.resourceSlug(), "WRITE");
        validateSave(request);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO analytics_saved_report(id, owner_username, resource_slug, report_name, description,
                    query_json, calculated_measures_json, visibility)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, auth.getName(), request.resourceSlug(), request.reportName().trim(),
                request.description(), writeJson(request.query()), writeJson(request.calculatedMeasures() == null
                        ? List.of() : request.calculatedMeasures()),
                "SHARED".equalsIgnoreCase(request.visibility()) ? "SHARED" : "PRIVATE");
        return getReport(auth, id);
    }

    @GetMapping("/reports")
    public List<SavedReportView> list(Authentication auth) {
        requireAuthenticated(auth);
        return jdbc.query("""
                SELECT DISTINCT r.id, r.owner_username, r.resource_slug, r.report_name, r.description,
                       r.query_json, r.calculated_measures_json, r.visibility, r.created_at, r.updated_at
                  FROM analytics_saved_report r
                  LEFT JOIN analytics_saved_report_grant g ON g.report_id = r.id
                 WHERE r.owner_username = ? OR (r.visibility = 'SHARED' AND g.username = ?)
                       OR g.username = ?
                 ORDER BY r.updated_at DESC LIMIT 200
                """, (rs, n) -> view(rs.getString("id"), rs.getString("owner_username"),
                rs.getString("resource_slug"), rs.getString("report_name"), rs.getString("description"),
                rs.getString("query_json"), rs.getString("calculated_measures_json"), rs.getString("visibility"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                auth.getName(), auth.getName(), auth.getName());
    }

    @GetMapping("/reports/{id}")
    public SavedReportView getReport(Authentication auth, @PathVariable String id) {
        requireAuthenticated(auth);
        List<SavedReportView> rows = jdbc.query("""
                SELECT r.id, r.owner_username, r.resource_slug, r.report_name, r.description,
                       r.query_json, r.calculated_measures_json, r.visibility, r.created_at, r.updated_at
                  FROM analytics_saved_report r
                 WHERE r.id = ? AND (r.owner_username = ? OR
                       (r.visibility = 'SHARED' AND EXISTS
                         (SELECT 1 FROM analytics_saved_report_grant g WHERE g.report_id = r.id AND g.username = ?))
                       OR EXISTS (SELECT 1 FROM analytics_saved_report_grant g
                                   WHERE g.report_id = r.id AND g.username = ?))
                """, (rs, n) -> view(rs.getString("id"), rs.getString("owner_username"),
                rs.getString("resource_slug"), rs.getString("report_name"), rs.getString("description"),
                rs.getString("query_json"), rs.getString("calculated_measures_json"), rs.getString("visibility"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                id, auth.getName(), auth.getName(), auth.getName());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        return rows.getFirst();
    }

    @PutMapping("/reports/{id}")
    public SavedReportView update(Authentication auth, @PathVariable String id,
                                  @RequestBody SaveReportRequest request) {
        SavedReportView existing = getReport(auth, id);
        requireOwner(auth, existing);
        requireAllowed(auth, existing.resourceSlug(), "WRITE");
        validateSave(request);
        jdbc.update("""
                UPDATE analytics_saved_report SET resource_slug=?, report_name=?, description=?,
                    query_json=?, calculated_measures_json=?, visibility=?, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND owner_username=?
                """, request.resourceSlug(), request.reportName().trim(), request.description(),
                writeJson(request.query()), writeJson(request.calculatedMeasures() == null ? List.of() : request.calculatedMeasures()),
                "SHARED".equalsIgnoreCase(request.visibility()) ? "SHARED" : "PRIVATE", id, auth.getName());
        return getReport(auth, id);
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable String id) {
        SavedReportView report = getReport(auth, id);
        requireOwner(auth, report);
        jdbc.update("DELETE FROM analytics_saved_report WHERE id=? AND owner_username=?", id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reports/{id}/grants")
    public ResponseEntity<Void> setGrants(Authentication auth, @PathVariable String id, @RequestBody GrantRequest request) {
        SavedReportView report = getReport(auth, id);
        requireOwner(auth, report);
        if (request.grants() == null || request.grants().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 grants are allowed");
        }
        jdbc.update("DELETE FROM analytics_saved_report_grant WHERE report_id=?", id);
        for (Grant grant : request.grants()) {
            if (grant.username() == null || !grant.username().matches("[A-Za-z0-9_.@-]{1,120}")
                    || auth.getName().equals(grant.username())) continue;
            String permission = "EDIT".equalsIgnoreCase(grant.permission()) ? "EDIT" : "READ";
            jdbc.update("INSERT INTO analytics_saved_report_grant(report_id, username, permission) VALUES (?, ?, ?)",
                    id, grant.username(), permission);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/{id}/subscriptions")
    public SubscriptionView subscribe(Authentication auth, @PathVariable String id,
                                      @RequestBody SubscriptionRequest request) {
        SavedReportView report = getReport(auth, id);
        requireOwner(auth, report);
        if (request.recipientEmail() == null || !request.recipientEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || request.cronExpression() == null || request.cronExpression().length() > 120
                || !Set.of("CSV", "XLSX", "PDF").contains(Objects.toString(request.exportFormat(), "CSV").toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription");
        }
        String subscriptionId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO analytics_report_subscription(id, report_id, owner_username, recipient_email,
                    cron_expression, export_format, next_run_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, subscriptionId, id, auth.getName(), request.recipientEmail(),
                request.cronExpression(), Objects.toString(request.exportFormat(), "CSV").toUpperCase(Locale.ROOT));
        return new SubscriptionView(subscriptionId, id, request.recipientEmail(), request.cronExpression(),
                Objects.toString(request.exportFormat(), "CSV").toUpperCase(Locale.ROOT), true, "QUEUED");
    }

    @GetMapping("/reports/{id}/export")
    public ResponseEntity<byte[]> export(Authentication auth, @PathVariable String id,
                                         @RequestParam(defaultValue = "CSV") String format) {
        SavedReportView saved = getReport(auth, id);
        requireAllowed(auth, saved.resourceSlug(), "READ");
        ErpBiEngine.BiQuery query = readQuery(saved.queryJson());
        ErpBiEngine.BiReport report = bi.query(saved.resourceSlug(), bounded(query));
        List<Map<String, Object>> rows = calculated.apply(report.rows(), readMeasures(saved.calculatedMeasuresJson()),
                List.of(), List.of());
        byte[] bytes;
        String contentType;
        String extension;
        try {
            switch (format.toUpperCase(Locale.ROOT)) {
                case "XLSX" -> { bytes = xlsx(rows); contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; extension = "xlsx"; }
                case "PDF" -> { bytes = pdf(saved.reportName(), rows); contentType = "application/pdf"; extension = "pdf"; }
                case "CSV" -> { bytes = csv(rows); contentType = "text/csv; charset=UTF-8"; extension = "csv"; }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format must be CSV, XLSX or PDF");
            }
        } catch (Exception e) {
            if (e instanceof ResponseStatusException responseStatusException) throw responseStatusException;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Report export failed");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + safeFilename(saved.reportName()) + "." + extension + "\"")
                .contentType(MediaType.parseMediaType(contentType)).body(bytes);
    }

    private ErpBiEngine.BiQuery bounded(ErpBiEngine.BiQuery query) {
        return new ErpBiEngine.BiQuery(query.dimensions(), query.measures(), query.filters(), query.orderBy(),
                Math.min(MAX_ROWS, Math.max(1, query.limit())), query.includeRank(),
                query.includeShareOfTotal(), query.includeGrandTotal());
    }

    private byte[] csv(List<Map<String, Object>> rows) {
        List<String> columns = columns(rows);
        StringBuilder out = new StringBuilder("\uFEFF").append(columns.stream().map(this::csvCell).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : rows) out.append(columns.stream().map(c -> csvCell(row.get(c))).collect(Collectors.joining(","))).append("\r\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(List<Map<String, Object>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Report");
            List<String> columns = columns(rows);
            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) header.createCell(c).setCellValue(columns.get(c));
            for (int r = 0; r < rows.size(); r++) {
                Row line = sheet.createRow(r + 1);
                for (int c = 0; c < columns.size(); c++) {
                    Object value = rows.get(r).get(columns.get(c));
                    if (value instanceof Number number) line.createCell(c).setCellValue(number.doubleValue());
                    else line.createCell(c).setCellValue(Objects.toString(value, ""));
                }
            }
            for (int c = 0; c < columns.size(); c++) sheet.autoSizeColumn(c);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] pdf(String title, List<Map<String, Object>> rows) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(title));
            List<String> columns = columns(rows);
            PdfPTable table = new PdfPTable(Math.max(1, columns.size()));
            for (String column : columns) table.addCell(column);
            for (Map<String, Object> row : rows) for (String column : columns) table.addCell(Objects.toString(row.get(column), ""));
            document.add(table);
            document.close();
            return out.toByteArray();
        }
    }

    private List<String> columns(List<Map<String, Object>> rows) {
        return rows.stream().flatMap(row -> row.keySet().stream()).distinct().limit(100).toList();
    }
    private String csvCell(Object value) { return "\"" + Objects.toString(value, "").replace("\"", "\"\"") + "\""; }
    private String safeFilename(String value) { return Objects.toString(value, "report").replaceAll("[^A-Za-z0-9._-]", "_"); }
    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report JSON"); }
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private ErpBiEngine.BiQuery readQuery(String value) {
        try { return mapper.readValue(value, ErpBiEngine.BiQuery.class); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved report query is invalid"); }
    }
    private List<BiCalculatedMeasureService.CalculatedMeasure> readMeasures(String value) {
        try { return mapper.readerForListOf(BiCalculatedMeasureService.CalculatedMeasure.class).readValue(value); }
        catch (Exception e) { return List.of(); }
    }
    private void validateSave(SaveReportRequest request) {
        if (request.resourceSlug() == null || request.resourceSlug().isBlank() || request.resourceSlug().length() > 180
                || request.reportName() == null || request.reportName().isBlank() || request.reportName().length() > 200
                || request.query() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceSlug, reportName and query are required");
        if (request.calculatedMeasures() != null && request.calculatedMeasures().size() > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 50 calculated measures are allowed");
    }
    private SavedReportView view(String id, String owner, String slug, String name, String description,
            String queryJson, String measuresJson, String visibility, Instant created, Instant updated) {
        return new SavedReportView(id, owner, slug, name, description, queryJson, measuresJson, visibility, created, updated);
    }
    private void requireAuthenticated(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication required");
    }
    private void requireAllowed(Authentication auth, String resource, String permission) {
        requireAuthenticated(auth);
        if (!authorization.isAllowed(auth, resource, permission)) throw new AccessDeniedException("BI " + permission + " access denied");
    }
    private void requireRead(Authentication auth, String resource) { requireAllowed(auth, resource, "READ"); }
    private void requireOwner(Authentication auth, SavedReportView report) {
        if (!auth.getName().equals(report.ownerUsername())) throw new AccessDeniedException("Only the report owner may change this report");
    }

    public record CachedQueryRequest(ErpBiEngine.BiQuery query,
            List<BiCalculatedMeasureService.CalculatedMeasure> calculatedMeasures,
            List<String> runningTotals, List<BiCalculatedMeasureService.PeriodComparison> periodComparisons) {}
    public record QueryResult(ErpBiEngine.BiReport report, boolean cached, Instant cachedAt) {}
    public record SaveReportRequest(String resourceSlug, String reportName, String description,
            ErpBiEngine.BiQuery query, List<BiCalculatedMeasureService.CalculatedMeasure> calculatedMeasures,
            String visibility) {}
    public record SavedReportView(String id, String ownerUsername, String resourceSlug, String reportName,
            String description, String queryJson, String calculatedMeasuresJson, String visibility,
            Instant createdAt, Instant updatedAt) {}
    public record Grant(String username, String permission) {}
    public record GrantRequest(List<Grant> grants) {}
    public record SubscriptionRequest(String recipientEmail, String cronExpression, String exportFormat) {}
    public record SubscriptionView(String id, String reportId, String recipientEmail, String cronExpression,
            String exportFormat, boolean active, String status) {}
}
