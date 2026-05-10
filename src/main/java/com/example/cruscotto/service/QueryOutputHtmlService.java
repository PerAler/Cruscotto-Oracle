package com.example.cruscotto.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class QueryOutputHtmlService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 1000;

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
     * Salva i risultati della query in formato CSV nella cartella di output.
     * Gli output HTML/XLSX vengono generati on-demand partendo dal CSV.
     *
     * @return il nome del file CSV generato (es. "MioScript_20260422_143000.csv")
     */
    public String saveAsHtml(String procedureName, List<Map<String, Object>> rows) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_TS);
        String baseName = sanitizeFilename(procedureName) + "_" + timestamp;

        Path csvFile = outputDir.resolve(baseName + ".csv");
        Files.writeString(csvFile, buildCsv(rows), StandardCharsets.UTF_8);

        enforceOutputRetention();

        return csvFile.getFileName().toString();
    }

    public String toCsvFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return filename;
        }
        if (filename.endsWith(".csv")) {
            return filename;
        }
        if (filename.endsWith(".html")) {
            return filename.substring(0, filename.length() - 5) + ".csv";
        }
        if (filename.endsWith(".xlsx")) {
            return filename.substring(0, filename.length() - 5) + ".csv";
        }
        return filename + ".csv";
    }

    public String toHtmlFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return filename;
        }
        if (filename.endsWith(".html")) {
            return filename;
        }
        if (filename.endsWith(".csv") || filename.endsWith(".xlsx")) {
            return filename.substring(0, filename.lastIndexOf('.')) + ".html";
        }
        return filename + ".html";
    }

    public String toXlsxFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return filename;
        }
        if (filename.endsWith(".xlsx")) {
            return filename;
        }
        if (filename.endsWith(".csv") || filename.endsWith(".html")) {
            return filename.substring(0, filename.lastIndexOf('.')) + ".xlsx";
        }
        return filename + ".xlsx";
    }

    public String buildHtmlFromCsv(String requestedHtmlFilename, int page, int pageSize) throws IOException {
        String csvFilename = toCsvFilename(requestedHtmlFilename);
        Path csvPath = outputDir.resolve(csvFilename).normalize();
        if (!csvPath.startsWith(outputDir) || !Files.exists(csvPath)) {
            return null;
        }

        String csvContent = Files.readString(csvPath, StandardCharsets.UTF_8);
        List<List<String>> parsed = parseCsv(csvContent);
        if (parsed.isEmpty()) {
            return buildCsvBackedHtml(csvFilename, List.of(), List.of(), 1, 1, 0, normalizePageSize(pageSize));
        }

        List<String> header = parsed.get(0);
        List<List<String>> rows = parsed.size() > 1 ? parsed.subList(1, parsed.size()) : List.of();
        int safePageSize = normalizePageSize(pageSize);
        int totalRows = rows.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) safePageSize));
        int safePage = Math.min(Math.max(page, 1), totalPages);
        int from = (safePage - 1) * safePageSize;
        int to = Math.min(from + safePageSize, totalRows);
        List<List<String>> pageRows = rows.subList(from, to);

        return buildCsvBackedHtml(csvFilename, header, pageRows, safePage, totalPages, totalRows, safePageSize);
    }

    public byte[] buildXlsxFromCsv(String requestedXlsxFilename) throws IOException {
        String csvFilename = toCsvFilename(requestedXlsxFilename);
        Path csvPath = outputDir.resolve(csvFilename).normalize();
        if (!csvPath.startsWith(outputDir) || !Files.exists(csvPath)) {
            return null;
        }

        String csvContent = Files.readString(csvPath, StandardCharsets.UTF_8);
        List<List<String>> parsed = parseCsv(csvContent);

        try (Workbook workbook = new XSSFWorkbook(); java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("output");

            if (parsed.isEmpty()) {
                Row emptyRow = sheet.createRow(0);
                emptyRow.createCell(0).setCellValue("Nessun risultato");
            } else {
                List<String> header = parsed.get(0);

                Font boldFont = workbook.createFont();
                boldFont.setBold(true);
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFont(boldFont);

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < header.size(); i++) {
                    Cell headerCell = headerRow.createCell(i);
                    headerCell.setCellStyle(headerStyle);
                    headerCell.setCellValue(truncateCellValue(header.get(i)));
                }

                for (int rowIndex = 1; rowIndex < parsed.size(); rowIndex++) {
                    List<String> rowData = parsed.get(rowIndex);
                    Row excelRow = sheet.createRow(rowIndex);
                    for (int colIndex = 0; colIndex < header.size(); colIndex++) {
                        String value = colIndex < rowData.size() ? rowData.get(colIndex) : "";
                        excelRow.createCell(colIndex).setCellValue(truncateCellValue(value));
                    }
                }

                for (int i = 0; i < header.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private void enforceOutputRetention() {
        if (maxOutputItems <= 0) {
            return;
        }

        try (Stream<Path> files = Files.list(outputDir)) {
            // Estrai nomi base unici (rimuovi estensione) per contare i bundle
            List<String> allFiles = files
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .toList();

            // Estrai timestamp base (es. "SQL_Editor_20260507_161333" da "SQL_Editor_20260507_161333.html")
            java.util.Set<String> uniqueBundles = new java.util.LinkedHashSet<>();
            for (String filename : allFiles) {
                String baseName = filename;
                if (filename.endsWith(".html") || filename.endsWith(".csv") || filename.endsWith(".xlsx")) {
                    baseName = filename.substring(0, filename.lastIndexOf('.'));
                }
                uniqueBundles.add(baseName);
            }

            if (uniqueBundles.size() <= maxOutputItems) {
                return;
            }

            // Ordina i bundle per data modificazione (più recenti prima)
            List<String> sortedBundles = uniqueBundles.stream()
                    .sorted((b1, b2) -> Long.compare(
                            getNewestFileTimestamp(b2),
                            getNewestFileTimestamp(b1)
                    ))
                    .toList();

            // Elimina i bundle più vecchi
            for (int i = maxOutputItems; i < sortedBundles.size(); i++) {
                deleteBundleByName(sortedBundles.get(i));
            }
        } catch (Exception ignored) {
            // Retention best-effort: non deve interrompere l'esecuzione della query.
        }
    }

    private long getNewestFileTimestamp(String baseName) {
        long maxTime = 0L;
        for (String ext : new String[]{".html", ".csv", ".xlsx"}) {
            Path p = outputDir.resolve(baseName + ext);
            long time = lastModifiedMillis(p);
            if (time > maxTime) {
                maxTime = time;
            }
        }
        return maxTime;
    }

    private void deleteBundleByName(String baseName) {
        deleteQuietly(outputDir.resolve(baseName + ".html"));
        deleteQuietly(outputDir.resolve(baseName + ".csv"));
        deleteQuietly(outputDir.resolve(baseName + ".xlsx"));
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
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

    private String truncateCellValue(String value) {
        // Apache POI limit: 32767 characters per cell
        if (value == null) {
            return "";
        }
        if (value.length() > 32767) {
            return value.substring(0, 32764) + "...";
        }
        return value;
    }

    private String buildCsvBackedHtml(String csvFilename,
                                      List<String> header,
                                      List<List<String>> rows,
                                      int page,
                                      int totalPages,
                                      int totalRows,
                                      int pageSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"it\"><head>")
          .append("<meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
          .append("<title>Output CSV: ").append(escapeHtml(csvFilename)).append("</title>")
          .append("<style>")
          .append(":root { --ink: #102529; --accent: #05668d; --ok: #198754; --line: #d6ccb8; }")
          .append("*{box-sizing:border-box}")
          .append("body{")
          .append("  margin:0;padding:16px;")
          .append("  font-family:\"Trebuchet MS\",\"Segoe UI\",sans-serif;")
          .append("  background:linear-gradient(180deg,#f8fbfd 0%,#ffffff 100%);")
          .append("  color:var(--ink);")
          .append("}")
          .append("h1{margin:0 0 0.5rem;font-size:1.5rem;color:var(--ink);letter-spacing:0.5px}")
          .append(".header{display:flex;align-items:baseline;gap:1rem;flex-wrap:wrap;margin-bottom:1rem}")
          .append(".toolbar{display:flex;gap:0.4rem;align-items:center;flex-wrap:wrap;margin-bottom:0.8rem}")
          .append("p.meta{margin:0 0 0.3rem;color:#5b6f72;font-size:0.9rem;letter-spacing:0.02em}")
          .append(".btn{border:none;border-radius:6px;padding:0.35rem 0.85rem;font-weight:600;font-size:0.85rem;cursor:pointer;transition:filter 0.15s,transform 0.1s;white-space:nowrap}")
          .append(".btn:hover{filter:brightness(1.1)}")
          .append(".btn:active{transform:scale(0.97)}")
          .append(".btn-toggle{background:#e8e2d8;color:var(--ink)}")
          .append(".table-wrap{border:1px solid #d2e2e8;border-radius:10px;overflow:auto;box-shadow:0 6px 16px rgba(0,0,0,0.06);background:#fff;margin-top:1rem}")
          .append("table{width:100%;border-collapse:collapse;min-width:780px}")
          .append("thead th{position:sticky;top:0;z-index:2;background:linear-gradient(180deg,#e7f3f8 0%,#d7ebf3 100%);color:#16363d;text-transform:uppercase;letter-spacing:0.03em;font-size:0.74rem;font-weight:700;border-bottom:1px solid #b9d5df}")
          .append("th,td{padding:9px 10px;border-bottom:1px solid #e5eef2;text-align:left;vertical-align:top;font-size:0.84rem;white-space:nowrap}")
          .append("tbody tr:nth-child(even) td{background:#f8fbfe}")
          .append("tbody tr:hover td{background:#eef7fb}")
          .append("body[data-density=\"compact\"] th,body[data-density=\"compact\"] td{padding:5px 7px;font-size:0.76rem}")
          .append("body[data-density=\"compact\"] thead th{font-size:0.68rem}")
          .append("body[data-density=\"compact\"] table{min-width:640px}")
          .append("body[data-column-wrap=\"true\"] th,body[data-column-wrap=\"true\"] td{white-space:normal;max-width:200px;word-break:break-word}")
          .append(".count{margin-top:0.75rem;font-size:0.9rem;color:#5b6f72}")
          .append("</style></head><body>")
          .append("<div class=\"header\">")
          .append("<div style=\"flex:1\"><h1>").append(escapeHtml(csvFilename)).append("</h1>")
          .append("<p class=\"meta\">Vista HTML generata da CSV (pagina ")
          .append(page)
          .append("/")
          .append(totalPages)
          .append(")</p></div>")
          .append("<div class=\"toolbar\">")
          .append("<button class=\"btn btn-toggle\" onclick=\"toggleDensity()\">Vista compatta</button>")
          .append("<button class=\"btn btn-toggle\" onclick=\"toggleColumnWrap()\">Colonne strette</button>")
          .append("</div>")
          .append("</div>");

        if (header == null || header.isEmpty()) {
            sb.append("<p>Nessun risultato restituito dallo script.</p>");
        } else {
            sb.append("<div class=\"table-wrap\"><table><thead><tr>");
            header.forEach(col -> sb.append("<th>").append(escapeHtml(col)).append("</th>"));
            sb.append("</tr></thead><tbody>");
            for (List<String> row : rows) {
                sb.append("<tr>");
                for (int i = 0; i < header.size(); i++) {
                    String val = i < row.size() ? row.get(i) : "";
                    sb.append("<td>").append(val == null ? "" : escapeHtml(val)).append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table></div>");
            sb.append("<p class=\"count\">Righe totali: ").append(totalRows)
              .append(" | Pagina ").append(page).append(" di ").append(totalPages)
              .append(" | Righe per pagina: ").append(pageSize).append("</p>");

            String baseHtml = toHtmlFilename(csvFilename);
            sb.append("<div class=\"toolbar\">");
            if (page > 1) {
                sb.append("<a class=\"btn btn-toggle\" style=\"text-decoration:none\" href=\"/output/")
                  .append(escapeHtml(baseHtml))
                  .append("?page=").append(page - 1)
                  .append("&size=").append(pageSize)
                  .append("\">Pagina precedente</a>");
            }
            if (page < totalPages) {
                sb.append("<a class=\"btn btn-toggle\" style=\"text-decoration:none\" href=\"/output/")
                  .append(escapeHtml(baseHtml))
                  .append("?page=").append(page + 1)
                  .append("&size=").append(pageSize)
                  .append("\">Pagina successiva</a>");
            }
            sb.append("</div>");
        }

        sb.append("<script>")
          .append("const DENSITY_KEY='cruscotto.output.density';const WRAP_KEY='cruscotto.output.columnWrap';")
          .append("let density=localStorage.getItem(DENSITY_KEY)||'comfortable';")
          .append("let columnWrap=localStorage.getItem(WRAP_KEY)==='true';")
          .append("document.body.setAttribute('data-density',density);")
          .append("document.body.setAttribute('data-column-wrap',columnWrap?'true':'false');")
          .append("function toggleDensity(){")
          .append("density=density==='compact'?'comfortable':'compact';")
          .append("localStorage.setItem(DENSITY_KEY,density);")
          .append("document.body.setAttribute('data-density',density);")
          .append("event.target.textContent=density==='compact'?'Vista estesa':'Vista compatta';")
          .append("}")
          .append("function toggleColumnWrap(){")
          .append("columnWrap=!columnWrap;")
          .append("localStorage.setItem(WRAP_KEY,columnWrap);")
          .append("document.body.setAttribute('data-column-wrap',columnWrap?'true':'false');")
          .append("event.target.textContent=columnWrap?'Colonne strette':'Adatta colonne';")
          .append("}")
          .append("document.querySelectorAll('button')[0].textContent=density==='compact'?'Vista estesa':'Vista compatta';")
          .append("document.querySelectorAll('button')[1].textContent=columnWrap?'Colonne strette':'Adatta colonne';")
          .append("</script>")
          .append("</body></html>");

        return sb.toString();
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                rows.add(new ArrayList<>(row));
                row.clear();
                cell.setLength(0);
            } else if (ch == '\r') {
                // Ignore CR: gestito dal newline successivo o da solo.
            } else {
                cell.append(ch);
            }
        }

        if (inQuotes) {
            row.add(cell.toString());
            rows.add(new ArrayList<>(row));
            return rows;
        }

        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(new ArrayList<>(row));
        }

        return rows;
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
