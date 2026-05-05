package com.example.cruscotto.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class QueryOutputHtmlService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path outputDir;
    private final int maxOutputItems;

    public QueryOutputHtmlService(@Value("${app.output.folder:output}") String outputFolder,
                                  @Value("${app.output.max-items:100}") int maxOutputItems) {
        this.outputDir = Paths.get(outputFolder).toAbsolutePath();
        this.maxOutputItems = maxOutputItems;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(outputDir);
    }

    public Path getOutputDir() {
        return outputDir;
    }

    /**
     * Salva i risultati della query come file HTML nella cartella di output.
     *
     * @return il nome del file generato (es. "MioScript_20260422_143000.html")
     */
    public String saveAsHtml(String procedureName, List<Map<String, Object>> rows) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_TS);
        String baseName = sanitizeFilename(procedureName) + "_" + timestamp;

        String htmlFilename = baseName + ".html";
        Path htmlFile = outputDir.resolve(htmlFilename);
        Files.writeString(htmlFile, buildHtml(procedureName, rows, timestamp), StandardCharsets.UTF_8);

        Path csvFile = outputDir.resolve(baseName + ".csv");
        Files.writeString(csvFile, buildCsv(rows), StandardCharsets.UTF_8);

        Path xlsxFile = outputDir.resolve(baseName + ".xlsx");
        writeXlsx(xlsxFile, rows);

        enforceOutputRetention();

        return htmlFilename;
    }

    private void enforceOutputRetention() {
        if (maxOutputItems <= 0) {
            return;
        }

        try (Stream<Path> files = Files.list(outputDir)) {
            List<Path> htmlOutputs = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".html"))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .toList();

            if (htmlOutputs.size() <= maxOutputItems) {
                return;
            }

            for (int i = maxOutputItems; i < htmlOutputs.size(); i++) {
                deleteOutputBundle(htmlOutputs.get(i));
            }
        } catch (Exception ignored) {
            // Retention best-effort: non deve interrompere l'esecuzione della query.
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private void deleteOutputBundle(Path htmlFile) {
        String name = htmlFile.getFileName().toString();
        if (!name.toLowerCase().endsWith(".html")) {
            return;
        }

        String baseName = name.substring(0, name.length() - 5);
        deleteQuietly(outputDir.resolve(baseName + ".html"));
        deleteQuietly(outputDir.resolve(baseName + ".csv"));
        deleteQuietly(outputDir.resolve(baseName + ".xlsx"));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private String buildCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        StringBuilder csv = new StringBuilder();

        csv.append(String.join(",", columns.stream().map(this::escapeCsv).toList()));
        csv.append(System.lineSeparator());

        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                Object value = row.get(column);
                values.add(escapeCsv(value == null ? "" : String.valueOf(value)));
            }
            csv.append(String.join(",", values));
            csv.append(System.lineSeparator());
        }

        return csv.toString();
    }

    private void writeXlsx(Path filePath, List<Map<String, Object>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream outputStream = Files.newOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("output");

            if (rows == null || rows.isEmpty()) {
                Row emptyRow = sheet.createRow(0);
                emptyRow.createCell(0).setCellValue("Nessun risultato");
                workbook.write(outputStream);
                return;
            }

            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellValue(columns.get(i));
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> rowData = rows.get(rowIndex);
                Row excelRow = sheet.createRow(rowIndex + 1);
                for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                    Object value = rowData.get(columns.get(colIndex));
                    excelRow.createCell(colIndex).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        }
    }

    private String buildHtml(String procedureName, List<Map<String, Object>> rows, String timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"it\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<title>Output: ").append(escapeHtml(procedureName)).append("</title>")
          .append("<style>")
          .append("body{font-family:sans-serif;margin:2rem;background:#f5f7fa;color:#222}")
          .append("h1{color:#1e3a5f}p.meta{color:#666;font-size:.9rem}")
          .append("table{border-collapse:collapse;width:100%;margin-top:1rem}")
          .append("th{background:#1e3a5f;color:#fff;padding:8px 14px;text-align:left;font-weight:600}")
          .append("td{padding:7px 14px;border-bottom:1px solid #dde3ec;vertical-align:top}")
          .append("tr:nth-child(even) td{background:#eef2f8}")
          .append("tr:hover td{background:#dce7f5}")
          .append(".count{margin-top:.75rem;font-size:.9rem;color:#555}")
          .append("</style></head><body>")
          .append("<h1>Output: ").append(escapeHtml(procedureName)).append("</h1>")
          .append("<p class=\"meta\">Generato il: ").append(escapeHtml(timestamp)).append("</p>");

        if (rows == null || rows.isEmpty()) {
            sb.append("<p>Nessun risultato restituito dallo script.</p>");
        } else {
            sb.append("<table><thead><tr>");
            rows.get(0).keySet().forEach(col ->
                sb.append("<th>").append(escapeHtml(String.valueOf(col))).append("</th>")
            );
            sb.append("</tr></thead><tbody>");
            for (Map<String, Object> row : rows) {
                sb.append("<tr>");
                row.values().forEach(val ->
                    sb.append("<td>").append(val == null ? "" : escapeHtml(String.valueOf(val))).append("</td>")
                );
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
            sb.append("<p class=\"count\">").append(rows.size()).append(" righe restituite.</p>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private String escapeCsv(String text) {
        boolean needsQuotes = text.contains(",") || text.contains("\n") || text.contains("\r") || text.contains("\"");
        String escaped = text.replace("\"", "\"\"");
        if (needsQuotes) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
