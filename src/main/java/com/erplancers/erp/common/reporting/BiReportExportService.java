package com.erplancers.erp.common.reporting;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared exporter for interactive downloads and scheduled report delivery. */
@Service
public class BiReportExportService {
    public ExportedFile export(ErpBiEngine.BiReport report, String reportName, String format) throws IOException {
        String normalized = format == null ? "CSV" : format.toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rows = report.rows() == null ? List.of() : report.rows();
        List<String> columns = rows.stream().flatMap(row -> row.keySet().stream()).distinct().toList();
        return switch (normalized) {
            case "CSV" -> csv(rows, columns);
            case "XLSX" -> xlsx(rows, columns);
            case "PDF" -> pdf(rows, columns, reportName, report.resource());
            default -> throw new IllegalArgumentException("format must be CSV, XLSX, or PDF");
        };
    }

    private ExportedFile csv(List<Map<String, Object>> rows, List<String> columns) {
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(BiReportExportService::csvCell).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(columns.stream().map(key -> csvCell(row.get(key))).collect(Collectors.joining(","))).append("\r\n");
        }
        return new ExportedFile(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8), "text/csv; charset=utf-8", "csv");
    }

    private ExportedFile xlsx(List<Map<String, Object>> rows, List<String> columns) throws IOException {
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
            return new ExportedFile(out.toByteArray(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        }
    }

    private ExportedFile pdf(List<Map<String, Object>> rows, List<String> columns,
                             String reportName, String resource) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<String> lines = new ArrayList<>();
            lines.add((reportName == null ? "ERP Report" : reportName) + " — " + resource);
            lines.add(String.join(" | ", columns));
            for (Map<String, Object> row : rows) {
                lines.add(columns.stream().map(key -> String.valueOf(row.getOrDefault(key, "")))
                        .collect(Collectors.joining(" | ")));
            }
            int index = 0;
            while (index < lines.size()) {
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
            }
            document.save(out);
            return new ExportedFile(out.toByteArray(), "application/pdf", "pdf");
        }
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : value instanceof Map<?, ?> || value instanceof List<?>
                ? value.toString() : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public record ExportedFile(byte[] bytes, String contentType, String extension) {}
}
