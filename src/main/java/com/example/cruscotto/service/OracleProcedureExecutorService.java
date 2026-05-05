package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import com.example.cruscotto.model.ProcedureDefinition;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OracleProcedureExecutorService {

    private final SqlProcedureCatalogService catalogService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ExecutionLogService executionLogService;
    private final QueryOutputHtmlService queryOutputHtmlService;

    public OracleProcedureExecutorService(SqlProcedureCatalogService catalogService,
                                         NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                         ExecutionLogService executionLogService,
                                         QueryOutputHtmlService queryOutputHtmlService) {
        this.catalogService = catalogService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.executionLogService = executionLogService;
        this.queryOutputHtmlService = queryOutputHtmlService;
    }

    public String runProcedure(String procedureName, Map<String, Object> inputParameters) {
        ProcedureDefinition definition = catalogService.findByName(procedureName)
                .orElseThrow(() -> new IllegalArgumentException("Procedura non trovata: " + procedureName));
        return runSql(procedureName, definition.sqlText(), inputParameters, false);
    }

    public String runAdhocSelect(String queryLabel, String sqlText, Map<String, Object> inputParameters) {
        String effectiveLabel = (queryLabel == null || queryLabel.isBlank()) ? "SQL Editor" : queryLabel.trim();
        return runSql(effectiveLabel, sqlText, inputParameters, true);
    }

    private String runSql(String executionName, String sqlText, Map<String, Object> inputParameters, boolean selectOnly) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeParameters = new LinkedHashMap<>(inputParameters == null ? Map.of() : inputParameters);

        try {
            String executableSql = sanitizeSql(sqlText);
            if (executableSql.isBlank()) {
                throw new IllegalArgumentException("SQL vuoto");
            }

            if (selectOnly && !isSelectStatement(executableSql)) {
                throw new IllegalArgumentException("Editor SQL: sono consentite solo query SELECT/WITH");
            }

            MapSqlParameterSource source = new MapSqlParameterSource(safeParameters);
            ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(executableSql);
            String jdbcSql = NamedParameterUtils.substituteNamedParameters(parsedSql, source);
            Object[] values = NamedParameterUtils.buildValueArray(parsedSql, source, null);

            String outputHtmlFile = null;

            if (isSelectStatement(executableSql)) {
                // Esegui come query e cattura il ResultSet
                List<Map<String, Object>> rows = namedParameterJdbcTemplate.getJdbcTemplate()
                        .execute(connection -> {
                            PreparedStatement ps = connection.prepareStatement(jdbcSql);
                            for (int i = 0; i < values.length; i++) {
                                ps.setObject(i + 1, values[i]);
                            }
                            return ps;
                        }, (PreparedStatementCallback<List<Map<String, Object>>>) ps -> {
                            boolean hasResults = ps.execute();
                            if (hasResults) {
                                return mapResultSet(ps.getResultSet());
                            }
                            return List.of();
                        });

                outputHtmlFile = queryOutputHtmlService.saveAsHtml(executionName, rows);
            } else {
                // Esegui come DML o stored procedure (nessun output da catturare)
                namedParameterJdbcTemplate.getJdbcTemplate().execute(connection -> {
                    PreparedStatement ps = connection.prepareStatement(jdbcSql);
                    for (int i = 0; i < values.length; i++) {
                        ps.setObject(i + 1, values[i]);
                    }
                    return ps;
                }, (PreparedStatementCallback<Void>) ps -> {
                    ps.execute();
                    return null;
                });
            }

            long duration = System.currentTimeMillis() - start;
            String okMessage = outputHtmlFile != null
                    ? "Eseguita con successo – output salvato"
                    : "Eseguita con successo";
            executionLogService.clearErrorsForProcedure(executionName);
            executionLogService.add(new ExecutionLogEntry(
                    LocalDateTime.now(),
                    executionName,
                    "OK",
                    duration,
                    okMessage,
                    safeParameters,
                    outputHtmlFile
            ));
            return okMessage;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            String koMessage = "Errore: " + ex.getMessage();
            String stackTrace = getStackTrace(ex);
            executionLogService.add(new ExecutionLogEntry(
                    LocalDateTime.now(),
                    executionName,
                    "KO",
                    duration,
                    koMessage,
                    safeParameters,
                    null,
                    stackTrace
            ));
            throw new IllegalStateException(koMessage, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Restituisce true se lo statement SQL è una query SELECT (o WITH ... SELECT). */
    private boolean isSelectStatement(String sql) {
        String upper = sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\n]*", " ")
                .stripLeading()
                .toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    /** Converte un ResultSet in una lista di mappe colonna→valore. */
    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws java.sql.SQLException {
        ColumnMapRowMapper mapper = new ColumnMapRowMapper();
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        int rowNum = 0;
        while (rs.next()) {
            rows.add(mapper.mapRow(rs, rowNum++));
        }
        return rows;
    }

    private String sanitizeSql(String sqlText) {
        return sqlText
                .replaceAll("(?m)^\\s*/\\s*$", "")
                .trim();
    }

    /** Converte un'eccezione in una stringa di stack trace. */
    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
