package com.example.cruscotto.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class XlsxImportService {

    private static final Logger log = LoggerFactory.getLogger(XlsxImportService.class);
    private static final int PREVIEW_ROW_LIMIT = 15;
    private static final int STRING_COLUMN_MIN_LENGTH = 30;
    private static final int STRING_COLUMN_MAX_LENGTH = 4000;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Path workbookStoreDir;

    public XlsxImportService(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             @Value("${app.output.folder:output}") String outputFolder) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.workbookStoreDir = Path.of(outputFolder, "xlsx-import");
        try {
            Files.createDirectories(workbookStoreDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile creare la cartella per i file XLSX: " + workbookStoreDir, ex);
        }
    }

    public String storeWorkbook(byte[] workbookBytes, String originalFileName) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Path target = workbookStoreDir.resolve(token + ".xlsx");
        try {
            Files.write(target, workbookBytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile salvare il file XLSX: " + originalFileName, ex);
        }
        return token;
    }

    public XlsxImportAnalysis analyzeWorkbook(String token) {
        Path workbookPath = resolveWorkbookPath(token);
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Il file XLSX non contiene fogli leggibili");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Il file XLSX non contiene una riga intestazione");
            }

            List<String> headers = readHeaderValues(headerRow, formatter, evaluator);
            List<ColumnStats> stats = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                stats.add(new ColumnStats());
            }

            List<List<String>> previewRows = new ArrayList<>();
            int dataRowCount = 0;
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                List<String> preview = new ArrayList<>();
                boolean hasAnyValue = false;
                for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                    Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = formatCell(cell, formatter, evaluator);
                    preview.add(value);
                    if (!value.isBlank()) {
                        hasAnyValue = true;
                        stats.get(colIndex).accept(cell, value);
                    }
                }

                if (hasAnyValue) {
                    dataRowCount++;
                    if (previewRows.size() < PREVIEW_ROW_LIMIT) {
                        previewRows.add(preview);
                    }
                }
            }

            Set<String> usedNames = new LinkedHashSet<>();
            List<XlsxColumnSuggestion> suggestions = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                String oracleName = XlsxImportSupport.normalizeOracleIdentifier(headers.get(i), "COL", i + 1);
                oracleName = XlsxImportSupport.makeUniqueOracleIdentifier(oracleName, usedNames);
                String oracleType = stats.get(i).inferOracleType();
                String sampleValue = stats.get(i).sampleValue();
                suggestions.add(new XlsxColumnSuggestion(i, headers.get(i), oracleName, oracleType, true, sampleValue));
            }

            return new XlsxImportAnalysis(
                    token,
                    workbookPath.getFileName().toString(),
                    sheet.getSheetName(),
                    dataRowCount,
                    suggestions,
                    previewRows,
                    XlsxImportSupport.buildCreateTableSql("XLSX_TABLE", suggestions)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile leggere il file XLSX salvato", ex);
        }
    }

    public List<OracleTableColumnInfo> readTableStructure(String tableName) {
        String normalizedTableName = XlsxImportSupport.normalizeOracleIdentifier(tableName, "XLSX_TABLE", 1);
        String sql = """
                SELECT column_id, column_name, data_type, data_length, data_precision, data_scale, nullable
                FROM user_tab_columns
                WHERE table_name = ?
                ORDER BY column_id
                """;
        return namedParameterJdbcTemplate.getJdbcTemplate().query(sql, rs -> {
            List<OracleTableColumnInfo> columns = new ArrayList<>();
            while (rs.next()) {
                columns.add(mapColumnInfo(rs));
            }
            return columns;
        }, normalizedTableName);
    }

    public XlsxImportResult createTable(String tableName, String token, List<Integer> selectedColumns) {
        XlsxImportAnalysis analysis = analyzeWorkbook(token);
        List<XlsxColumnSuggestion> columns = resolveSelectedColumns(analysis, selectedColumns);
        String createSql = XlsxImportSupport.buildCreateTableSql(tableName, columns);
        namedParameterJdbcTemplate.getJdbcTemplate().execute(createSql);
        return new XlsxImportResult(
                "Tabella creata con successo",
                tableName,
                0,
                XlsxImportSupport.normalizeOracleIdentifier(tableName, "XLSX_TABLE", 1)
        );
    }

    public XlsxImportResult importRows(String tableName, String token, List<Integer> selectedColumns, boolean truncateBeforeImport) {
        XlsxImportAnalysis analysis = analyzeWorkbook(token);
        List<XlsxColumnSuggestion> columns = resolveSelectedColumns(analysis, selectedColumns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Seleziona almeno una colonna da importare");
        }

        Path workbookPath = resolveWorkbookPath(token);
        String normalizedTableName = XlsxImportSupport.normalizeOracleIdentifier(tableName, "XLSX_TABLE", 1);
        String insertSql = buildInsertSql(normalizedTableName, columns);

        int importedRows;
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Il file XLSX non contiene fogli leggibili");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            importedRows = 0;

            if (truncateBeforeImport) {
                namedParameterJdbcTemplate.getJdbcTemplate().execute("TRUNCATE TABLE " + normalizedTableName);
            }

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Object[] values = new Object[columns.size()];
                boolean hasValue = false;
                for (int colPos = 0; colPos < columns.size(); colPos++) {
                    XlsxColumnSuggestion column = columns.get(colPos);
                    Cell cell = row.getCell(column.index(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    Object value = extractJdbcValue(cell, formatter, evaluator, column.oracleType());
                    values[colPos] = value;
                    if (value != null) {
                        hasValue = true;
                    }
                }

                if (!hasValue) {
                    continue;
                }

                namedParameterJdbcTemplate.getJdbcTemplate().update(conn -> {
                    java.sql.PreparedStatement ps = conn.prepareStatement(insertSql);
                    for (int i = 0; i < values.length; i++) {
                        setPreparedValue(ps, i + 1, values[i]);
                    }
                    return ps;
                });
                importedRows++;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile importare i dati dal file XLSX", ex);
        }

        return new XlsxImportResult(
                "Importazione completata",
                tableName,
                importedRows,
                normalizedTableName
        );
    }

    public String buildCreateTablePreview(String tableName, List<XlsxColumnSuggestion> columns) {
        return XlsxImportSupport.buildCreateTableSql(tableName, columns);
    }

    private Path resolveWorkbookPath(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Carica prima un file XLSX");
        }
        Path workbookPath = workbookStoreDir.resolve(token + ".xlsx").normalize();
        if (!workbookPath.startsWith(workbookStoreDir)) {
            throw new IllegalArgumentException("Token file non valido");
        }
        if (!Files.exists(workbookPath)) {
            throw new IllegalArgumentException("File XLSX temporaneo non trovato");
        }
        return workbookPath;
    }

    private List<String> readHeaderValues(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        int maxColumn = headerRow.getLastCellNum();
        if (maxColumn < 0) {
            throw new IllegalArgumentException("La riga di intestazione è vuota");
        }
        List<String> headers = new ArrayList<>();
        for (int colIndex = 0; colIndex < maxColumn; colIndex++) {
            Cell cell = headerRow.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = formatCell(cell, formatter, evaluator);
            headers.add(value.isBlank() ? "COL_" + (colIndex + 1) : value);
        }
        return headers;
    }

    private List<XlsxColumnSuggestion> resolveSelectedColumns(XlsxImportAnalysis analysis, List<Integer> selectedColumns) {
        Set<Integer> selected = new LinkedHashSet<>();
        if (selectedColumns != null) {
            selectedColumns.stream()
                    .filter(Objects::nonNull)
                    .forEach(selected::add);
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Seleziona almeno una colonna");
        }
        List<XlsxColumnSuggestion> result = new ArrayList<>();
        for (XlsxColumnSuggestion column : analysis.columns()) {
            if (selected.contains(column.index())) {
                result.add(column);
            }
        }
        return result;
    }

    private OracleTableColumnInfo mapColumnInfo(ResultSet rs) throws java.sql.SQLException {
        return new OracleTableColumnInfo(
                rs.getInt("column_id"),
                rs.getString("column_name"),
                rs.getString("data_type"),
                rs.getObject("data_length", Integer.class),
                rs.getObject("data_precision", Integer.class),
                rs.getObject("data_scale", Integer.class),
                rs.getString("nullable")
        );
    }

    private String buildInsertSql(String tableName, List<XlsxColumnSuggestion> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns.get(i).oracleName());
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        return sql.toString();
    }

    private void setPreparedValue(java.sql.PreparedStatement ps, int index, Object value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        if (value instanceof Timestamp timestamp) {
            ps.setTimestamp(index, timestamp);
            return;
        }
        if (value instanceof BigDecimal bigDecimal) {
            ps.setBigDecimal(index, bigDecimal);
            return;
        }
        if (value instanceof Long longValue) {
            ps.setLong(index, longValue);
            return;
        }
        if (value instanceof Integer intValue) {
            ps.setInt(index, intValue);
            return;
        }
        ps.setObject(index, value);
    }

    private String buildSingleColumnValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return formatCell(cell, formatter, evaluator);
    }

    private Object extractJdbcValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator, String oracleType) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        String type = oracleType.toUpperCase(Locale.ROOT);
        if (type.startsWith("NUMBER")) {
            String raw = buildSingleColumnValue(cell, formatter, evaluator);
            if (raw.isBlank()) {
                return null;
            }
            return new BigDecimal(raw.replace(',', '.'));
        }
        if ("DATE".equals(type) || "TIMESTAMP".equals(type)) {
            java.util.Date parsedDate = extractDate(cell, formatter, evaluator);
            if (parsedDate == null) {
                return buildSingleColumnValue(cell, formatter, evaluator);
            }
            LocalDateTime dateTime = LocalDateTime.ofInstant(parsedDate.toInstant(), ZoneId.systemDefault());
            return Timestamp.valueOf(type.equals("DATE") ? dateTime.toLocalDate().atStartOfDay() : dateTime);
        }
        String value = buildSingleColumnValue(cell, formatter, evaluator);
        return value.isBlank() ? null : value;
    }

    private java.util.Date extractDate(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue();
        }
        String raw = formatCell(cell, formatter, evaluator);
        for (DateTimeFormatter formatterCandidate : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"))) {
            try {
                if (formatterCandidate == DateTimeFormatter.ISO_LOCAL_DATE) {
                    LocalDate date = LocalDate.parse(raw, formatterCandidate);
                    return java.sql.Date.valueOf(date);
                }
                LocalDateTime dateTime = LocalDateTime.parse(raw, formatterCandidate);
                return java.util.Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                // prova il formato successivo
            }
        }
        return null;
    }

    private String formatCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        String value = formatter.formatCellValue(cell, evaluator);
        return value == null ? "" : value.trim();
    }

    private static final class ColumnStats {
        private boolean sawValue;
        private boolean sawBoolean;
        private boolean sawDate;
        private boolean sawTimestamp;
        private boolean sawNumeric;
        private boolean sawDecimal;
        private boolean sawText;
        private int maxLength;
        private String sampleValue;
        private int scale;

        void accept(Cell cell, String formattedValue) {
            if (formattedValue == null || formattedValue.isBlank()) {
                return;
            }
            sawValue = true;
            if (sampleValue == null) {
                sampleValue = formattedValue;
            }
            maxLength = Math.max(maxLength, formattedValue.length());

            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                sawDate = true;
                if (cell.getDateCellValue() != null && hasTimeComponent(cell.getDateCellValue())) {
                    sawTimestamp = true;
                }
                return;
            }

            if (formattedValue.equalsIgnoreCase("true")
                    || formattedValue.equalsIgnoreCase("false")
                    || formattedValue.equalsIgnoreCase("si")
                    || formattedValue.equalsIgnoreCase("no")
                    || formattedValue.equals("0")
                    || formattedValue.equals("1")) {
                sawBoolean = true;
                return;
            }

            if (formattedValue.matches("[-+]?\\d+")) {
                sawNumeric = true;
                return;
            }
            if (formattedValue.matches("[-+]?\\d+[\\.,]\\d+")) {
                sawNumeric = true;
                sawDecimal = true;
                scale = Math.max(scale, formattedValue.replace(',', '.').length() - formattedValue.replaceAll(".*[\\.,]", "").length());
                return;
            }

            if (looksLikeDate(formattedValue)) {
                sawDate = true;
                return;
            }

            sawText = true;
        }

        String inferOracleType() {
            if (!sawValue) {
                return "VARCHAR2(4000 CHAR)";
            }
            if (sawText) {
                return stringType();
            }
            if (sawDate) {
                return sawTimestamp ? "TIMESTAMP" : "DATE";
            }
            if (sawBoolean && !sawNumeric) {
                return "NUMBER(1)";
            }
            if (sawNumeric) {
                return sawDecimal ? "NUMBER" : "NUMBER(38,0)";
            }
            return stringType();
        }

        String sampleValue() {
            return sampleValue == null ? "" : sampleValue;
        }

        private String stringType() {
            int length = Math.min(STRING_COLUMN_MAX_LENGTH, Math.max(STRING_COLUMN_MIN_LENGTH, maxLength));
            if (length >= STRING_COLUMN_MAX_LENGTH) {
                return "CLOB";
            }
            return "VARCHAR2(" + length + " CHAR)";
        }

        private static boolean looksLikeDate(String value) {
            return value.matches("\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}(:\\d{2})?)?")
                    || value.matches("\\d{2}/\\d{2}/\\d{4}([ T]\\d{2}:\\d{2}(:\\d{2})?)?");
        }

        private static boolean hasTimeComponent(java.util.Date date) {
            LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            return !localDateTime.toLocalTime().equals(LocalTime.MIDNIGHT);
        }
    }
}
