package com.erplancers.erp.common.web;

import com.erplancers.erp.common.reporting.BiReportExportService;
import com.erplancers.erp.common.reporting.ErpBiEngine;
import com.erplancers.erp.common.security.PolicyAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Optional SMTP worker for scheduled reports. Enable only after SMTP is configured with
 * bi.delivery.enabled=true. Each delivery rechecks the report owner's active tenant grant
 * and resource READ policy before evaluating or sending any report data.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bi.delivery.enabled", havingValue = "true")
public class BiReportDeliveryWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ErpBiEngine bi;
    private final BiReportExportService exporter;
    private final PolicyAuthorizationService authorization;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ObjectProvider<UserDetailsService> userDetailsServiceProvider;

    @Value("${bi.delivery.from:noreply@localhost}")
    private String fromAddress;

    @Scheduled(fixedDelayString = "${bi.delivery.worker-delay-ms:30000}")
    public void deliverOne() {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        UserDetailsService userDetailsService = userDetailsServiceProvider.getIfAvailable();
        if (mailSender == null || userDetailsService == null) return;

        Delivery delivery = claimOne();
        if (delivery == null) return;
        long started = System.nanoTime();
        try {
            Detail detail = loadDetail(delivery);
            if (detail == null) throw new IllegalStateException("Scheduled report or schedule no longer exists");
            Integer grant = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM analytics_tenant_grant
                     WHERE username = ? AND tenant_id = ? AND active = TRUE
                    """, Integer.class, detail.ownerUsername(), delivery.tenantId());
            if (grant == null || grant == 0) throw new SecurityException("Report owner's tenant grant is no longer active");

            UserDetails user = userDetailsService.loadUserByUsername(detail.ownerUsername());
            Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            if (!authorization.isAllowed(authentication, detail.resourceSlug(), "READ")) {
                throw new SecurityException("Report owner's resource READ policy was revoked");
            }

            ErpBiEngine.BiQuery query = mapper.treeToValue(
                    mapper.readTree(detail.definitionJson()), ErpBiEngine.BiQuery.class);
            ErpBiEngine.BiReport report = bi.query(detail.resourceSlug(), query);
            BiReportExportService.ExportedFile file = exporter.export(report, detail.reportName(), detail.exportFormat());
            List<String> recipients = mapper.readValue(detail.recipientsJson(),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (recipients.isEmpty() || recipients.size() > 20
                    || recipients.stream().anyMatch(email -> email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                throw new IllegalArgumentException("Scheduled report recipients are invalid");
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(String[]::new));
            helper.setSubject("ERP report: " + detail.reportName());
            helper.setText("Your scheduled ERP report is attached. Report: " + detail.reportName()
                    + "\nGenerated: " + java.time.Instant.now(), false);
            helper.addAttachment(safeFilename(detail.reportName()) + "." + file.extension(),
                    new ByteArrayResource(file.bytes()), file.contentType());
            mailSender.send(message);
            jdbc.update("""
                    UPDATE bi_report_delivery SET status = 'SUCCEEDED', completed_at = CURRENT_TIMESTAMP,
                           error_message = NULL WHERE id = ? AND status = 'RUNNING'
                    """, delivery.id());
            recordMetric(delivery.tenantId(), "SUCCEEDED", started, report.rowCount(), "delivery-sent");
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            if (message.length() > 1900) message = message.substring(0, 1900);
            jdbc.update("""
                    UPDATE bi_report_delivery
                       SET status = CASE WHEN attempts >= 3 THEN 'FAILED' ELSE 'QUEUED' END,
                           available_at = CURRENT_TIMESTAMP + INTERVAL '1 minute',
                           completed_at = CASE WHEN attempts >= 3 THEN CURRENT_TIMESTAMP ELSE NULL END,
                           error_message = ?
                     WHERE id = ? AND status = 'RUNNING'
                    """, message, delivery.id());
            recordMetric(delivery.tenantId(), "FAILED", started, 0, message);
        }
    }

    private Delivery claimOne() {
        List<Delivery> rows = jdbc.query("""
                UPDATE bi_report_delivery d
                   SET status = 'RUNNING', attempts = attempts + 1
                 WHERE d.id = (
                    SELECT q.id FROM bi_report_delivery q
                    JOIN bi_report_schedule s ON s.id = q.schedule_id
                    WHERE q.status = 'QUEUED' AND q.available_at <= CURRENT_TIMESTAMP AND s.enabled = TRUE
                    ORDER BY q.created_at LIMIT 1 FOR UPDATE OF q SKIP LOCKED
                 )
                RETURNING d.id, d.tenant_id
                """, (rs, row) -> new Delivery(rs.getString("id"), rs.getString("tenant_id")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Detail loadDetail(Delivery delivery) {
        List<Detail> rows = jdbc.query("""
                SELECT r.owner_username, r.name, r.resource_slug, r.definition_json,
                       s.recipients_json, s.export_format
                  FROM bi_report_delivery d
                  JOIN bi_report_schedule s ON s.id = d.schedule_id
                  JOIN bi_saved_report r ON r.id = d.report_id
                 WHERE d.id = ? AND d.tenant_id = ?
                """, (rs, row) -> new Detail(rs.getString("owner_username"), rs.getString("name"),
                rs.getString("resource_slug"), rs.getString("definition_json"),
                rs.getString("recipients_json"), rs.getString("export_format")), delivery.id(), delivery.tenantId());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void recordMetric(String tenantId, String status, long started, long rows, String detail) {
        long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        String sanitized = detail.length() > 500 ? detail.substring(0, 500) : detail;
        jdbc.update("""
                INSERT INTO analytics_operation_metric
                  (tenant_id, component, operation, status, duration_ms, rows_processed, detail_json)
                VALUES (?, 'BI_DELIVERY', 'send_report', ?, ?, ?, ?)
                """, tenantId, status, duration, rows, sanitized);
    }

    private String safeFilename(String value) {
        return value == null ? "erp-report" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private record Delivery(String id, String tenantId) {}
    private record Detail(String ownerUsername, String reportName, String resourceSlug,
                          String definitionJson, String recipientsJson, String exportFormat) {}
}
