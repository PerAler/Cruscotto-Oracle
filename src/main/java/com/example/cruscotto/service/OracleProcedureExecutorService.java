package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import com.example.cruscotto.model.ProcedureDefinition;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OracleProcedureExecutorService {

    private static final Logger log = LoggerFactory.getLogger(OracleProcedureExecutorService.class);

    private final SqlProcedureCatalogService catalogService;
    private final OracleConnectionManager connectionManager;
    private final ExecutionLogService executionLogService;
    private final QueryOutputHtmlService queryOutputHtmlService;

    public OracleProcedureExecutorService(SqlProcedureCatalogService catalogService,
                                         OracleConnectionManager connectionManager,
                                         ExecutionLogService executionLogService,
                                         QueryOutputHtmlService queryOutputHtmlService) {
        this.catalogService = catalogService;
        this.connectionManager = connectionManager;
        this.executionLogService = executionLogService;
        this.queryOutputHtmlService = queryOutputHtmlService;
    }

    public String runProcedure(String procedureName, Map<String, Object> inputParameters) {
        return runProcedure(null, procedureName, inputParameters);
    }

    public String runProcedure(String connectionId, String procedureName, Map<String, Object> inputParameters) {
        ProcedureDefinition definition = catalogService.findByName(procedureName)
                .orElseThrow(() -> new IllegalArgumentException("Procedura non trovata: " + procedureName));
        return runSql(connectionId, procedureName, definition.sqlText(), inputParameters, false);
    }

    public String runAdhocSelect(String queryLabel, String sqlText, Map<String, Object> inputParameters) {
        return runAdhocSelect(null, queryLabel, sqlText, inputParameters);
    }

    public String runAdhocSelect(String connectionId, String queryLabel, String sqlText, Map<String, Object> inputParameters) {
        String effectiveLabel = (queryLabel == null || queryLabel.isBlank()) ? "SQL Editor" : queryLabel.trim();
        return runSql(connectionId, effectiveLabel, sqlText, inputParameters, true);
    }

    private String runSql(String connectionId, String executionName, String sqlText, Map<String, Object> inputParameters, boolean selectOnly) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeParameters = new LinkedHashMap<>(inputParameters == null ? Map.of() : inputParameters);
        OracleConnectionManager.ResolvedConnection resolvedConnection = connectionManager.resolveConnection(connectionId);

        String executableSql;
        try {
            executableSql = sanitizeSql(sqlText);
            if (executableSql.isBlank()) {
                throw new IllegalArgumentException("SQL vuoto");
            }
            if (selectOnly && !isAllowedExecutionStatement(executableSql)) {
                throw new IllegalArgumentException(
                    "Editor SQL: sono consentiti SELECT, WITH, INSERT, UPDATE, DELETE, MERGE, GRANT, BEGIN, DECLARE, CALL");
            }
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            executionLogService.add(new ExecutionLogEntry(
                    LocalDateTime.now(), executionName, "KO", duration,
                    "Errore: " + ex.getMessage(), safeParameters, null, getStackTrace(ex), null));
            throw new IllegalStateException("Errore: " + ex.getMessage(), ex);
        }

        MapSqlParameterSource source = new MapSqlParameterSource(safeParameters);
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(executableSql);
        String jdbcSql = NamedParameterUtils.substituteNamedParameters(parsedSql, source);
        Object[] values = NamedParameterUtils.buildValueArray(parsedSql, source, null);

        DataSource dataSource = resolvedConnection.dataSource();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        String outputHtmlFile = null;
        String dbmsOutput = null;

        try {
            String schema = resolvedConnection.info().schema();
            if (schema != null && !schema.isBlank()) {
                connectionManager.applyCurrentSchema(conn, schema);
            }

            // Abilita DBMS_OUTPUT sulla stessa connessione che eseguirà lo script
            try (CallableStatement cs = conn.prepareCall("BEGIN DBMS_OUTPUT.ENABLE(NULL); END;")) {
                cs.execute();
                log.debug("[DBMS_OUTPUT] Abilitato su connessione {}", System.identityHashCode(conn));
            } catch (Exception ex) {
                log.warn("[DBMS_OUTPUT] Impossibile abilitare DBMS_OUTPUT: {}", ex.getMessage());
            }

            // Esecuzione dello script
            if (isSelectStatement(executableSql)) {
                try (PreparedStatement ps = conn.prepareStatement(jdbcSql)) {
                    for (int i = 0; i < values.length; i++) {
                        ps.setObject(i + 1, values[i]);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        List<Map<String, Object>> rows = mapResultSet(rs);
                        outputHtmlFile = queryOutputHtmlService.saveAsHtml(executionName, rows);
                    }
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(jdbcSql)) {
                    for (int i = 0; i < values.length; i++) {
                        ps.setObject(i + 1, values[i]);
                    }
                    ps.execute();
                }
            }

            // Cattura DBMS_OUTPUT dalla stessa connessione dopo l'esecuzione
            dbmsOutput = captureDbmsOutputFromConnection(conn);

            long duration = System.currentTimeMillis() - start;
            String okMessage = outputHtmlFile != null
                    ? "Eseguita con successo – output salvato"
                    : "Eseguita con successo";
            executionLogService.clearErrorsForProcedure(executionName);
            executionLogService.add(new ExecutionLogEntry(
                    LocalDateTime.now(), executionName, "OK", duration,
                    okMessage, safeParameters, outputHtmlFile, dbmsOutput));
            return okMessage;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            String koMessage = "Errore: " + ex.getMessage();

            // Tenta cattura DBMS_OUTPUT anche in caso di errore (potrebbe contenere messaggi di debug)
            try {
                dbmsOutput = captureDbmsOutputFromConnection(conn);
            } catch (Exception ignored) {
                // Se fallisce, continua comunque
            }

            executionLogService.add(new ExecutionLogEntry(
                    LocalDateTime.now(), executionName, "KO", duration,
                    koMessage, safeParameters, null, getStackTrace(ex), dbmsOutput));
            throw new IllegalStateException(koMessage, ex);

        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Cattura le righe di DBMS_OUTPUT dalla connessione corrente usando GET_LINE in loop. */
    private String captureDbmsOutputFromConnection(Connection conn) {
        StringBuilder output = new StringBuilder();
        try (CallableStatement cs = conn.prepareCall("{ call DBMS_OUTPUT.GET_LINE(?, ?) }")) {
            int status;
            do {
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.registerOutParameter(2, Types.INTEGER);
                cs.execute();
                status = cs.getInt(2);
                if (status == 0) {
                    try {
                        java.sql.Clob clob = cs.getClob(1);
                        if (clob != null) {
                            try (java.io.Reader reader = clob.getCharacterStream()) {
                                char[] buffer = new char[4096];
                                int read;
                                while ((read = reader.read(buffer)) != -1) {
                                    output.append(buffer, 0, read);
                                }
                            }
                        } else {
                            String line = cs.getString(1);
                            output.append(line != null ? line : "");
                        }
                    } catch (SQLException clobEx) {
                        String line = cs.getString(1);
                        output.append(line != null ? line : "");
                    }
                    output.append("\n");
                }
            } while (status == 0);
            log.debug("[DBMS_OUTPUT] Catturate {} chars", output.length());
        } catch (Exception ex) {
            log.warn("[DBMS_OUTPUT] Errore durante la cattura: {}", ex.getMessage());
        }
        String result = output.toString();
        return result.isBlank() ? null : result.trim();
    }

    /** Restituisce true se lo statement SQL è una query SELECT (o WITH ... SELECT). */
    private boolean isSelectStatement(String sql) {
        String upper = stripCommentsAndTrim(sql);
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    /**
     * Restituisce true se lo statement è un tipo Oracle SQL/PL-SQL eseguibile
     * dall'editor (query, DML, PL/SQL anonimi, chiamate a procedure).
     * Non sono consentiti script non-Oracle (es. script shell/batch).
     */
    private boolean isAllowedExecutionStatement(String sql) {
        String upper = stripCommentsAndTrim(sql);
        return upper.startsWith("SELECT")
            || upper.startsWith("WITH")
            || upper.startsWith("INSERT")
            || upper.startsWith("UPDATE")
            || upper.startsWith("DELETE")
            || upper.startsWith("MERGE")
            || upper.startsWith("GRANT")
            || upper.startsWith("REVOKE")
            || upper.startsWith("BEGIN")
            || upper.startsWith("DECLARE")
            || upper.startsWith("CALL")
            || upper.startsWith("EXECUTE")
            || upper.startsWith("EXEC");
    }

    private String stripCommentsAndTrim(String sql) {
        return sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\n]*", " ")
                .stripLeading()
                .toUpperCase();
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
