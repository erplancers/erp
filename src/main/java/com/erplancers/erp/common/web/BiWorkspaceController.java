package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.CalculatedMeasureExpression;
import com.erplancers.erp.common.reporting.ErpBiEngine;
import com.erplancers.erp.common.security.PolicyAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tenant-scoped BI workspace API. Report definitions are data, not executable code.
 * Every report run rechecks resource READ access; sharing never bypasses ERP resource policies.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bi-workspace")
@SecurityRequirement(name = "basicAuth")
public class BiWorkspaceController {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> DEFINITION_KEYS = Set.of(
            "dimensions", "measures", "filters", "orderBy", "limit",
            "includeRank", "includeShareOfTotal", "includeGrandTotal");
    private static final Set<String> FORMAT_HINTS = Set.of("NUMBER", "PERCENT", "CURRENCY", "INTEGER");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ErpBiEngine bi;
    private final PolicyAuthorizationService authorization;

    @GetMapping("/reports")
    public List<ReportView> reports(Authentication auth, @RequestParam String tenantId) {
        requireTenant(auth, tenantId);
        return jdbc.query("""
                SELECT r.id, r.tenant_id, r.owner_username, r.name, r.resource_slug,
                       r.definition_json, r.visibility, r.version, r.created_at, r.updated_at
                  FROM bi_saved_report r
                 WHERE r.tenant_id = ? AND
                       (r.owner_username = ? OR r.visibility = 'SHARED' OR EXISTS (
                           SELECT 1 FROM bi_report_share s WHERE s.report_id = r.id
                             AND ((s.principal_type = 'USER' AND s.principal_name = ?)
                               OR (s.principal_type = 'ROLE' AND s.principal_name IN (%s)))
                       ))
                 ORDER BY r.updated_at DESC
                """.formatted(rolePlaceholders(auth)), (rs, row) -> new ReportView(
                rs.getString("id"), rs.getString("tenant_id"), rs.getString("owner_username"),
                rs.getString("name"), rs.getString("resource_slug"), rs.getString("definition_json"),
                rs.getString("visibility"), rs.getInt("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), reportListArgs(tenantId, auth));
    }

    @PostMapping("/reports")
    public ReportView createReport(Authentication auth, @RequestBody SaveReportRequest request) {
        requireTenant(auth, request.tenantId());
        requireResource(auth, request.resourceSlug(), "WRITE");
        String name = requiredText(request.name(), "name", 200);
        String slug = safeResource(request.resourceSlug());
        String definition = validateDefinition(request.definitionJson());
        String visibility = visibility(request.visibility());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO bi_saved_report
                  (id, tenant_id, owner_username, name, resource_slug, definition_json, visibility, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                """, id, request.tenantId(), auth.getName(), name, slug, definition, visibility);
        return reportById(id, request.tenantId(), auth.getName(), auth);
    }

    @PutMapping("/reports/{id}")
    public ReportView updateReport(Authentication auth, @PathVariable String id,
                                   @RequestBody SaveReportRequest request) {
        requireTenant(auth, request.tenantId());
        ReportView current = ownedReport(id, request.tenantId(), auth.getName());
        requireResource(auth, current.resourceSlug(), "WRITE");
        String name = requiredText(request.name(), "name", 200);
        String slug = safeResource(request.resourceSlug());
        requireResource(auth, slug, "WRITE");
        String definition = validateDefinition(request.definitionJson());
        String visibility = visibility(request.visibility());
        int updated = jdbc.update("""
                UPDATE bi_saved_report SET name = ?, resource_slug = ?, definition_json = ?,
                       visibility = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND tenant_id = ? AND owner_username = ?
                """, name, slug, definition, visibility, id, request.tenantId(), auth.getName());
        if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        return reportById(id, request.tenantId(), auth.getName(), auth);
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> deleteReport(Authentication auth, @PathVariable String id,
                                             @RequestParam String tenantId) {
        requireTenant(auth, tenantId);
        int deleted = jdbc.update("DELETE FROM bi_saved_report WHERE id = ? AND tenant_id = ? AND owner_username = ?",
                id, tenantId, auth.getName());
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reports/{id}/shares")
    public ResponseEntity<Void> shareReport(Authentication auth, @PathVariable String id,
                                             @RequestParam String tenantId, @RequestBody ShareRequest request) {
        requireTenant(auth, tenantId);
        ReportView report = ownedReport(id, tenantId, auth.getName());
        requireResource(auth, report.resourceSlug(), "WRITE");
        String type = requiredText(request.principalType(), "principalType", 16).toUpperCase(Locale.ROOT);
        String principal = requiredText(request.principalName(), "principalName", 160);
        String permission = request.permission() == null ? "VIEW" : request.permission().toUpperCase(Locale.ROOT);
        if (!Set.of("USER", "ROLE").contains(type) || !Set.of("VIEW", "EDIT", "ADMIN").contains(permission)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid share principal type or permission");
        }
        if (type.equals("ROLE") && !principal.matches("ROLE_[A-Z0-9_]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must use the ROLE_NAME format");
        }
        jdbc.update("""
                INSERT INTO bi_report_share(report_id, principal_type, principal_name, permission, created_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (report_id, principal_type, principal_name)
                DO UPDATE SET permission = EXCLUDED.permission, created_by = EXCLUDED.created_by,
                              created_at = CURRENT_TIMESTAMP
                """, id, type, principal, permission, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/reports/{id}/shares")
    public ResponseEntity<Void> unshareReport(Authentication auth, @PathVariable String id,
                                               @RequestParam String tenantId, @RequestParam String principalType,
                                               @RequestParam String principalName) {
        requireTenant(auth, tenantId);
        ownedReport(id, tenantId, auth.getName());
        jdbc.update("DELETE FROM bi_report_share WHERE report_id = ? AND principal_type = ? AND principal_name = ?",
                id, principalType.toUpperCase(Locale.ROOT), principalName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/{id}/run")
    public ErpBiEngine.BiReport runReport(Authentication auth, @PathVariable String id,
                                          @RequestParam String tenantId) {
        ReportView report = readableReport(id, tenantId, auth);
        requireResource(auth, report.resourceSlug(), "READ");
        return bi.query(report.resourceSlug(), queryFromDefinition(report.definitionJson()));
    }


    @GetMapping("/reports/{id}/export")
    public ResponseEntity<byte[]> exportReport(Authentication auth, @PathVariable String id,
                                                @RequestParam String tenantId,
                                                @RequestParam(defaultValue = "CSV") String format) {
        ReportView report = readableReport(id, tenantId, auth);
        requireResource(auth, report.resourceSlug(), "READ");
        ErpBiEngine.BiReport result = bi.query(report.resourceSlug(), queryFromDefinition(report.definitionJson()));
        List<Map<String, Object>> rows = result.rows() == null ? List.of() : result.rows();
        List<String> columns = rows.stream().flatMap(row -> row.keySet().stream()).distinct().toList();
        String normalized = format.toUpperCase(Locale.ROOT);
        try {
            byte[] bytes;
            String contentType;
            String extension;
            if ("CSV".equals(normalized)) {
                StringBuilder csv = new StringBuilder();
                csv.append(columns.stream().map(BiWorkspaceController::csvCell).collect(Collectors.joining(","))).append("\\r\\n");
                for (Map<String, Object> row : rows) {
                    csv.append(columns.stream().map(key -> csvCell(row.get(key))).collect(Collectors.joining(","))).append("\\r\\n");
                }
                bytes = ("\\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
                contentType = "text/csv; charset=utf-8";
                extension = "csv";
            } else if ("XLSX".equals(normalized)) {
                try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    Sheet sheet = workbook.createSheet("Report");
                    Row header = sheet.createRow(0);
                    for (int i = 0; i < columns.size(); i++) header.createCell(i).setCellValue(columns.get(i));
                    for (int r = 0; r < rows.size(); r++) {
                        Row excelRow = sheet.createRow(r + 1);
                        for (int col = 0; col < columns.size(); col++) {
                            Object value = rows.get(r).get(columns.get(col));
                            if (value instanceof Number number) excelRow.createCell(col).setCellValue(number.doubleValue());
                            else if (value instanceof Boolean bool) excelRow.createCell(col).setCellValue(bool);
                            else excelRow.createCell(col).setCellValue(value == null ? "" : String.valueOf(value));
                        }
                    }
                    for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);
                    workbook.write(out);
                    bytes = out.toByteArray();
                }
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                extension = "xlsx";
            } else if ("PDF".equals(normalized)) {
                try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                    List<String> lines = new ArrayList<>();
                    lines.add(report.name() + " — " + report.resourceSlug());
                    lines.add(String.join(" | ", columns));
                    for (Map<String, Object> row : rows) {
                        lines.add(columns.stream().map(key -> String.valueOf(row.getOrDefault(key, "")))
                                .collect(Collectors.joining(" | ")));
                    }
                    int index = 0;
                    while (index < lines.size() || (lines.isEmpty() && index == 0)) {
                        PDPage page = new PDPage();
                        document.addPage(page);
                        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                            stream.beginText();
                            stream.setFont(font, 8);
                            stream.newLineAtOffset(36, 756);
                            int count = 0;
                            while (index < lines.size() && count < 55) {
                                String line = lines.get(index++).replaceAll("[^\\x20-\\x7E]", "?");
                                if (line.length() > 130) line = line.substring(0, 127) + "...";
                                stream.showText(line);
                                stream.newLineAtOffset(0, -13);
                                count++;
                            }
                            stream.endText();
                        }
                        if (lines.isEmpty()) break;
                    }
                    document.save(out);
                    bytes = out.toByteArray();
                }
                contentType = "application/pdf";
                extension = "pdf";
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be CSV, XLSX, or PDF");
            }
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename(report.name()) + "." + extension + "\"")
                    .body(bytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to export report");
        }
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : value instanceof Map<?, ?> || value instanceof List<?>
                ? value.toString() : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String safeFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }


    @PostMapping("/reports/{id}/schedules")
    public ScheduleView createSchedule(Authentication auth, @PathVariable String id,
                                       @RequestParam String tenantId, @RequestBody CreateScheduleRequest request) {
        ReportView report = ownedReport(id, tenantId, auth.getName());
        requireTenant(auth, tenantId);
        requireResource(auth, report.resourceSlug(), "READ");
        String cron = requiredText(request.cronExpression(), "cronExpression", 120);
        String timezone = request.timezone() == null || request.timezone().isBlank() ? "UTC" : request.timezone();
        String format = request.exportFormat() == null ? "PDF" : request.exportFormat().toUpperCase(Locale.ROOT);
        if (!Set.of("CSV", "XLSX", "PDF").contains(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exportFormat must be CSV, XLSX, or PDF");
        }
        if (request.recipients() == null || request.recipients().isEmpty() || request.recipients().size() > 20
                || request.recipients().stream().anyMatch(email -> email == null
                || email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1 to 20 valid recipient email addresses");
        }
        try {
            java.time.ZoneId zone = java.time.ZoneId.of(timezone);
            CronExpression expression = CronExpression.parse(cron);
            java.time.ZonedDateTime next = expression.next(java.time.ZonedDateTime.now(zone));
            if (next == null) throw new IllegalArgumentException("Cron has no next execution");
            String idNew = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO bi_report_schedule
                      (id, report_id, tenant_id, created_by, cron_expression, timezone,
                       recipients_json, export_format, enabled, next_run_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                    """, idNew, id, tenantId, auth.getName(), cron, timezone,
                    mapper.writeValueAsString(request.recipients()), format,
                    java.sql.Timestamp.from(next.toInstant()));
            return scheduleById(idNew, tenantId, auth.getName());
        } catch (java.time.DateTimeException | IllegalArgumentException | JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression, timezone, or recipients");
        }
    }

    @GetMapping("/reports/{id}/schedules")
    public List<ScheduleView> schedules(Authentication auth, @PathVariable String id, @RequestParam String tenantId) {
        ownedReport(id, tenantId, auth.getName());
        return jdbc.query("""
                SELECT id, report_id, tenant_id, created_by, cron_expression, timezone, recipients_json,
                       export_format, enabled, next_run_at, last_run_at, created_at, updated_at
                  FROM bi_report_schedule WHERE report_id = ? AND tenant_id = ? AND created_by = ?
                 ORDER BY created_at DESC
                """, (rs, row) -> mapSchedule(rs), id, tenantId, auth.getName());
    }

    @PatchMapping("/schedules/{scheduleId}")
    public ScheduleView setScheduleEnabled(Authentication auth, @PathVariable String scheduleId,
                                            @RequestParam String tenantId, @RequestBody ToggleScheduleRequest request) {
        requireTenant(auth, tenantId);
        int changed = jdbc.update("""
                UPDATE bi_report_schedule SET enabled = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND tenant_id = ? AND created_by = ?
                """, request.enabled(), scheduleId, tenantId, auth.getName());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
        return scheduleById(scheduleId, tenantId, auth.getName());
    }

    @GetMapping("/deliveries")
    public List<DeliveryView> deliveries(Authentication auth, @RequestParam String tenantId) {
        requireTenant(auth, tenantId);
        return jdbc.query("""
                SELECT id, schedule_id, report_id, tenant_id, requested_by, status, attempts,
                       available_at, completed_at, error_message, created_at
                  FROM bi_report_delivery WHERE tenant_id = ? AND requested_by = ?
                 ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> new DeliveryView(rs.getString("id"), rs.getString("schedule_id"),
                rs.getString("report_id"), rs.getString("tenant_id"), rs.getString("requested_by"),
                rs.getString("status"), rs.getInt("attempts"), rs.getTimestamp("available_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
                rs.getString("error_message"), rs.getTimestamp("created_at").toInstant()), tenantId, auth.getName());
    }

    private ScheduleView scheduleById(String id, String tenantId, String username) {
        List<ScheduleView> rows = jdbc.query("""
                SELECT id, report_id, tenant_id, created_by, cron_expression, timezone, recipients_json,
                       export_format, enabled, next_run_at, last_run_at, created_at, updated_at
                  FROM bi_report_schedule WHERE id = ? AND tenant_id = ? AND created_by = ?
                """, (rs, row) -> mapSchedule(rs), id, tenantId, username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
        return rows.getFirst();
    }

    private ScheduleView mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ScheduleView(rs.getString("id"), rs.getString("report_id"), rs.getString("tenant_id"),
                rs.getString("created_by"), rs.getString("cron_expression"), rs.getString("timezone"),
                rs.getString("recipients_json"), rs.getString("export_format"), rs.getBoolean("enabled"),
                rs.getTimestamp("next_run_at").toInstant(),
                rs.getTimestamp("last_run_at") == null ? null : rs.getTimestamp("last_run_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    @GetMapping("/measures")
    public List<MeasureView> measures(Authentication auth, @RequestParam String tenantId,
                                      @RequestParam String resourceSlug) {
        requireTenant(auth, tenantId);
        requireResource(auth, safeResource(resourceSlug), "READ");
        return jdbc.query("""
                SELECT id, tenant_id, owner_username, resource_slug, measure_key, display_name,
                       expression, format_hint, visibility, created_at, updated_at
                  FROM bi_calculated_measure
                 WHERE tenant_id = ? AND resource_slug = ?
                   AND (owner_username = ? OR visibility = 'SHARED')
                 ORDER BY display_name
                """, (rs, row) -> new MeasureView(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("owner_username"), rs.getString("resource_slug"), rs.getString("measure_key"),
                rs.getString("display_name"), rs.getString("expression"), rs.getString("format_hint"),
                rs.getString("visibility"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), tenantId, resourceSlug, auth.getName());
    }

    @PostMapping("/measures")
    public MeasureView createMeasure(Authentication auth, @RequestBody SaveMeasureRequest request) {
        requireTenant(auth, request.tenantId());
        String slug = safeResource(request.resourceSlug());
        requireResource(auth, slug, "WRITE");
        String key = requiredText(request.measureKey(), "measureKey", 64);
        if (!SAFE_KEY.matcher(key).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid measure key");
        String name = requiredText(request.displayName(), "displayName", 160);
        String expression = requiredText(request.expression(), "expression", 1000);
        String format = request.formatHint() == null ? "NUMBER" : request.formatHint().toUpperCase(Locale.ROOT);
        if (!FORMAT_HINTS.contains(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported formatHint");
        // Parse and evaluate against harmless sample bindings before accepting the expression.
        validateExpressionSyntax(expression);
        String visibility = visibility(request.visibility());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO bi_calculated_measure
                  (id, tenant_id, owner_username, resource_slug, measure_key, display_name,
                   expression, format_hint, visibility)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, request.tenantId(), auth.getName(), slug, key, name, expression, format, visibility);
        return measureById(id, request.tenantId(), auth.getName());
    }

    @PostMapping("/measures/evaluate")
    public Map<String, Double> evaluateMeasures(Authentication auth, @RequestBody EvaluateMeasuresRequest request) {
        requireTenant(auth, request.tenantId());
        requireResource(auth, safeResource(request.resourceSlug()), "READ");
        if (request.values() == null || request.values().size() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at most 200 numeric input values");
        }
        Map<String, Double> output = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : request.expressions().entrySet()) {
            if (!SAFE_KEY.matcher(entry.getKey()).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid measure key");
            try {
                output.put(entry.getKey(), CalculatedMeasureExpression.evaluate(entry.getValue(), request.values()));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }
        return output;
    }

    private String validateDefinition(String raw) {
        try {
            JsonNode node = mapper.readTree(requiredText(raw, "definitionJson", 20000));
            if (!node.isObject()) throw new IllegalArgumentException();
            Iterator<String> keys = node.fieldNames();
            while (keys.hasNext()) if (!DEFINITION_KEYS.contains(keys.next())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported report definition property");
            }
            if (node.has("limit") && (!node.get("limit").canConvertToInt()
                    || node.get("limit").intValue() < 1 || node.get("limit").intValue() > 1000)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report limit must be between 1 and 1000");
            }
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report definition JSON");
        }
    }

    private void validateExpressionSyntax(String expression) {
        try {
            // All built-in functions and operators are exercised with bounded, numeric sample values.
            CalculatedMeasureExpression.evaluate(expression, Map.of("revenue", 10d, "cost", 4d,
                    "previous", 8d, "current", 10d, "value", 2d, "amount", 2d));
        } catch (IllegalArgumentException ex) {
            // Unknown identifiers are allowed at definition time; malformed syntax and unsafe calls are not.
            if (ex.getMessage() != null && (ex.getMessage().startsWith("Unsupported function")
                    || ex.getMessage().contains("Unexpected token") || ex.getMessage().contains("Expected")
                    || ex.getMessage().contains("Division by zero") || ex.getMessage().contains("too long"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }
    }

    private ErpBiEngine.BiQuery queryFromDefinition(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return mapper.treeToValue(node, ErpBiEngine.BiQuery.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored report definition is invalid");
        }
    }

    private ReportView readableReport(String id, String tenantId, Authentication auth) {
        requireTenant(auth, tenantId);
        List<ReportView> rows = jdbc.query("""
                SELECT id, tenant_id, owner_username, name, resource_slug, definition_json, visibility,
                       version, created_at, updated_at
                  FROM bi_saved_report WHERE id = ? AND tenant_id = ?
                """, (rs, row) -> new ReportView(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("owner_username"), rs.getString("name"), rs.getString("resource_slug"),
                rs.getString("definition_json"), rs.getString("visibility"), rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id, tenantId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        ReportView report = rows.getFirst();
        if (!report.ownerUsername().equals(auth.getName()) && !canReadShared(id, report.visibility(), auth)) {
            throw new AccessDeniedException("Report is not shared with this user");
        }
        return report;
    }

    private boolean canReadShared(String id, String visibility, Authentication auth) {
        if ("SHARED".equals(visibility)) return true;
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        String placeholders = roles.isEmpty() ? "''" : String.join(",", Collections.nCopies(roles.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(id, auth.getName()));
        args.addAll(roles);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM bi_report_share
                 WHERE report_id = ? AND ((principal_type = 'USER' AND principal_name = ?)
                   OR (principal_type = 'ROLE' AND principal_name IN (%s)))
                """.formatted(placeholders), Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private ReportView ownedReport(String id, String tenantId, String username) {
        List<ReportView> rows = jdbc.query("""
                SELECT id, tenant_id, owner_username, name, resource_slug, definition_json, visibility,
                       version, created_at, updated_at FROM bi_saved_report
                 WHERE id = ? AND tenant_id = ? AND owner_username = ?
                """, (rs, row) -> new ReportView(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("owner_username"), rs.getString("name"), rs.getString("resource_slug"),
                rs.getString("definition_json"), rs.getString("visibility"), rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                id, tenantId, username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found or not owned by caller");
        return rows.getFirst();
    }

    private ReportView reportById(String id, String tenantId, String username, Authentication auth) {
        return readableReport(id, tenantId, auth);
    }

    private MeasureView measureById(String id, String tenantId, String username) {
        return jdbc.queryForObject("""
                SELECT id, tenant_id, owner_username, resource_slug, measure_key, display_name,
                       expression, format_hint, visibility, created_at, updated_at
                  FROM bi_calculated_measure WHERE id = ? AND tenant_id = ? AND owner_username = ?
                """, (rs, row) -> new MeasureView(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("owner_username"), rs.getString("resource_slug"), rs.getString("measure_key"),
                rs.getString("display_name"), rs.getString("expression"), rs.getString("format_hint"),
                rs.getString("visibility"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), id, tenantId, username);
    }

    private void requireTenant(Authentication auth, String tenantId) {
        requireAuthenticated(auth);
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_tenant_grant
                 WHERE username = ? AND tenant_id = ? AND active = TRUE
                """, Integer.class, auth.getName(), tenantId);
        if (count == null || count == 0) throw new AccessDeniedException("No analytics grant for requested tenant");
    }

    private void requireResource(Authentication auth, String resource, String action) {
        if (auth == null || !authorization.isAllowed(auth, resource, action)) {
            throw new AccessDeniedException("BI " + action + " access denied for resource: " + resource);
        }
    }

    private void requireAuthenticated(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication required");
    }

    private String safeResource(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource slug");
        }
        return value;
    }

    private String visibility(String value) {
        String visibility = value == null ? "PRIVATE" : value.toUpperCase(Locale.ROOT);
        if (!Set.of("PRIVATE", "SHARED").contains(visibility)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Visibility must be PRIVATE or SHARED");
        }
        return visibility;
    }

    private String requiredText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }

    private String rolePlaceholders(Authentication auth) {
        int count = auth.getAuthorities().size();
        return count == 0 ? "''" : String.join(",", Collections.nCopies(count, "?"));
    }

    private Object[] reportListArgs(String tenantId, Authentication auth) {
        List<Object> args = new ArrayList<>(List.of(tenantId, auth.getName(), auth.getName()));
        auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).forEach(args::add);
        return args.toArray();
    }

    public record CreateScheduleRequest(String cronExpression, String timezone, List<String> recipients, String exportFormat) {}
    public record ToggleScheduleRequest(boolean enabled) {}
    public record ScheduleView(String id, String reportId, String tenantId, String createdBy,
                               String cronExpression, String timezone, String recipientsJson, String exportFormat,
                               boolean enabled, Instant nextRunAt, Instant lastRunAt, Instant createdAt, Instant updatedAt) {}
    public record DeliveryView(String id, String scheduleId, String reportId, String tenantId, String requestedBy,
                               String status, int attempts, Instant availableAt, Instant completedAt,
                               String errorMessage, Instant createdAt) {}
    public record SaveReportRequest(String tenantId, String name, String resourceSlug,
                                    String definitionJson, String visibility) {}
    public record ShareRequest(String principalType, String principalName, String permission) {}
    public record SaveMeasureRequest(String tenantId, String resourceSlug, String measureKey,
                                     String displayName, String expression, String formatHint, String visibility) {}
    public record EvaluateMeasuresRequest(String tenantId, String resourceSlug,
                                          Map<String, String> expressions, Map<String, ?> values) {}
    public record ReportView(String id, String tenantId, String ownerUsername, String name, String resourceSlug,
                             String definitionJson, String visibility, int version, Instant createdAt, Instant updatedAt) {}
    public record MeasureView(String id, String tenantId, String ownerUsername, String resourceSlug,
                              String measureKey, String displayName, String expression, String formatHint,
                              String visibility, Instant createdAt, Instant updatedAt) {}
}
