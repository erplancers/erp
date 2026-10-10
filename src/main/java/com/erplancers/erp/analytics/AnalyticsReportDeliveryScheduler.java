package com.erplancers.erp.analytics;

import com.erplancers.erp.common.reporting.BiCalculatedMeasureService;
import com.erplancers.erp.common.reporting.ErpBiEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Delivers due saved-report subscriptions without running report queries on HTTP request threads. */
@Component
@RequiredArgsConstructor
public class AnalyticsReportDeliveryScheduler {
    private final JdbcTemplate jdbc;
    private final ErpBiEngine bi;
    private final BiCalculatedMeasureService calculated;
    private final ObjectMapper mapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Scheduled(fixedDelayString = "${analytics.report.delivery.poll-ms:60000}")
    public void deliverDueReports() {
        List<DueSubscription> due = jdbc.query("""
                SELECT s.id, s.report_id, s.owner_username, s.recipient_email, s.cron_expression, s.export_format,
                       r.resource_slug, r.report_name, r.query_json, r.calculated_measures_json
                  FROM analytics_report_subscription s
                  JOIN analytics_saved_report r ON r.id = s.report_id
                 WHERE s.active = TRUE AND s.next_run_at <= CURRENT_TIMESTAMP
                 ORDER BY s.next_run_at LIMIT 20
                """, (rs, n) -> new DueSubscription(rs.getString("id"), rs.getString("report_id"),
                rs.getString("owner_username"), rs.getString("recipient_email"), rs.getString("cron_expression"),
                rs.getString("export_format"), rs.getString("resource_slug"), rs.getString("report_name"),
                rs.getString("query_json"), rs.getString("calculated_measures_json")));
        for (DueSubscription subscription : due) {
            try {
                deliver(subscription);
            } catch (Exception failure) {
                reschedule(subscription, "FAILED", failure.getMessage());
            }
        }
    }

    private void deliver(DueSubscription s) throws Exception {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            reschedule(s, "SKIPPED_NO_SMTP", "Configure spring.mail.host to enable email delivery");
            return;
        }
        ErpBiEngine.BiQuery query = mapper.readValue(s.queryJson(), ErpBiEngine.BiQuery.class);
        ErpBiEngine.BiReport report = bi.query(s.resourceSlug(), new ErpBiEngine.BiQuery(query.dimensions(),
                query.measures(), query.filters(), query.orderBy(), Math.min(5000, Math.max(1, query.limit())),
                query.includeRank(), query.includeShareOfTotal(), query.includeGrandTotal()));
        List<BiCalculatedMeasureService.CalculatedMeasure> measures = mapper.readerForListOf(
                BiCalculatedMeasureService.CalculatedMeasure.class).readValue(s.measuresJson());
        List<Map<String, Object>> rows = calculated.apply(report.rows(), measures, List.of(), List.of());
        String format = s.exportFormat();
        byte[] attachment;
        String extension;
        String contentType;
        if ("XLSX".equals(format)) {
            attachment = xlsx(rows); extension = "xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if ("PDF".equals(format)) {
            attachment = pdf(s.reportName(), rows); extension = "pdf"; contentType = "application/pdf";
        } else {
            attachment = csv(rows); extension = "csv"; contentType = "text/csv";
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setTo(s.recipientEmail());
        helper.setSubject("ERP BI report: " + s.reportName());
        helper.setText("Your scheduled ERP BI report is attached. Generated at " + Instant.now() + ".", false);
        helper.addAttachment(safeName(s.reportName()) + "." + extension,
                new org.springframework.core.io.ByteArrayResource(attachment), contentType);
        sender.send(message);
        reschedule(s, "SENT", null);
    }

    private byte[] csv(List<Map<String, Object>> rows) {
        List<String> columns = columns(rows);
        StringBuilder out = new StringBuilder("\uFEFF")
                .append(columns.stream().map(this::cell).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : rows) out.append(columns.stream()
                .map(c -> cell(row.get(c))).collect(Collectors.joining(","))).append("\r\n");
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
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] pdf(String title, List<Map<String, Object>> rows) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            document.open();
            document.add(new com.lowagie.text.Paragraph(title));
            List<String> columns = columns(rows);
            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(Math.max(1, columns.size()));
            for (String column : columns) table.addCell(column);
            for (Map<String, Object> row : rows) for (String column : columns)
                table.addCell(Objects.toString(row.get(column), ""));
            document.add(table);
            document.close();
            return out.toByteArray();
        }
    }

    private List<String> columns(List<Map<String, Object>> rows) {
        return rows.stream().flatMap(row -> row.keySet().stream()).distinct().limit(100).toList();
    }
    private String cell(Object value) { return "\"" + Objects.toString(value, "").replace("\"", "\"\"") + "\""; }
    private String safeName(String value) { return Objects.toString(value, "report").replaceAll("[^A-Za-z0-9._-]", "_"); }

    private void reschedule(DueSubscription s, String status, String error) {
        Instant now = Instant.now();
        Instant next;
        try {
            CronExpression cron = CronExpression.parse(s.cronExpression());
            next = cron.next(now.atZone(ZoneOffset.UTC)).toInstant();
        } catch (RuntimeException invalidCron) {
            jdbc.update("UPDATE analytics_report_subscription SET active=FALSE, last_run_at=?, last_status=?, last_error=? WHERE id=?",
                    Timestamp.from(now), "FAILED_INVALID_CRON", "Invalid cron expression", s.id());
            return;
        }
        jdbc.update("""
                UPDATE analytics_report_subscription SET last_run_at=?, last_status=?, last_error=?,
                    next_run_at=? WHERE id=?
                """, Timestamp.from(now), status, error == null ? null : error.substring(0, Math.min(1000, error.length())),
                Timestamp.from(next), s.id());
    }

    private record DueSubscription(String id, String reportId, String owner, String recipientEmail,
            String cronExpression, String exportFormat, String resourceSlug, String reportName,
            String queryJson, String measuresJson) {}
}
