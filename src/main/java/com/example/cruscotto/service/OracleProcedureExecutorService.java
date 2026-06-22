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
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OracleProcedureExecutorService {

    private static final Logger log = LoggerFactory.getLogger(OracleProcedureExecutorService.class);

    private final SqlProcedureCatalogService catalogService;
    private final OracleConnectionManager connectionManager;
    private final ExecutionLogService executionLogService;
    private final QueryOutputHtmlService queryOutputHtmlService;
    private final Map<String, Statement> runningStatements = new ConcurrentHashMap<>();

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

    public String runAdhocDdl(String connectionId, String queryLabel, String sqlText) {
        String effectiveLabel = (queryLabel == null || queryLabel.isBlank()) ? "DDL Editor" : queryLabel.trim();
        return runSql(connectionId, effectiveLabel, sqlText, Map.of(), false);
    }

    public boolean cancelRunningExecution(String connectionId) {
        OracleConnectionManager.ResolvedConnection resolved = connectionManager.resolveConnection(connectionId);
        String resolvedConnectionId = resolved.info().id();
        Statement runningStatement = runningStatements.get(resolvedConnectionId);
        if (runningStatement == null) {
            return false;
        }
        try {
            runningStatement.cancel();
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Impossibile interrompere l'elaborazione: " + ex.getMessage(), ex);
        }
    }

    private String runSql(String connectionId, String executionName, String sqlText, Map<String, Object> inputParameters, boolean selectOnly) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeParameters = new LinkedHashMap<>(inputParameters == null ? Map.of() : inputParameters);
        OracleConnectionManager.ResolvedConnection resolvedConnection = connectionManager.resolveConnection(connectionId);

        String executableSql;
        boolean autoLimitApplied = false;
        try {
            executableSql = sanitizeSql(sqlText);
            if (executableSql.isBlank()) {
                throw new IllegalArgumentException("SQL vuoto");
            }
            if (selectOnly && !isAllowedExecutionStatement(executableSql)) {
                throw new IllegalArgumentException(
                    "Editor SQL: sono consentiti SELECT, WITH, INSERT, UPDATE, DELETE, MERGE, GRANT, BEGIN, DECLARE, CALL");
            }
            if (selectOnly && isSelectStatement(executableSql) && !hasTopLevelWhereClause(executableSql)) {
                executableSql = wrapSelectWithAutoRownumLimit(executableSql);
                autoLimitApplied = true;
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
        String resolvedConnectionId = resolvedConnection.info().id();
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
                    registerRunningStatement(resolvedConnectionId, ps);
                    try {
                        for (int i = 0; i < values.length; i++) {
                            ps.setObject(i + 1, values[i]);
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            List<Map<String, Object>> rows = mapResultSet(rs);
                            outputHtmlFile = queryOutputHtmlService.saveAsHtml(executionName, rows);
                        }
                    } finally {
                        clearRunningStatement(resolvedConnectionId, ps);
                    }
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(jdbcSql)) {
                    registerRunningStatement(resolvedConnectionId, ps);
                    try {
                        for (int i = 0; i < values.length; i++) {
                            ps.setObject(i + 1, values[i]);
                        }
                        ps.execute();
                    } finally {
                        clearRunningStatement(resolvedConnectionId, ps);
                    }
                }
            }

            // Cattura DBMS_OUTPUT dalla stessa connessione dopo l'esecuzione
            dbmsOutput = captureDbmsOutputFromConnection(conn);

            long duration = System.currentTimeMillis() - start;
            String okMessage = outputHtmlFile != null
                    ? "Eseguita con successo – output salvato"
                    : "Eseguita con successo";
            if (autoLimitApplied) {
                okMessage += " (limite automatico: WHERE ROWNUM < 200)";
            }
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

    private void registerRunningStatement(String connectionId, Statement statement) {
        Statement previous = runningStatements.putIfAbsent(connectionId, statement);
        if (previous != null && previous != statement) {
            throw new IllegalStateException("C'è già un'elaborazione in corso sulla connessione selezionata.");
        }
    }

    private void clearRunningStatement(String connectionId, Statement statement) {
        runningStatements.remove(connectionId, statement);
    }

    private String wrapSelectWithAutoRownumLimit(String sql) {
        return "SELECT * FROM (\n" + sql + "\n) CRUSCOTTO_AUTO_LIMIT WHERE ROWNUM < 200";
    }

    private boolean hasTopLevelWhereClause(String sql) {
        String sanitized = stripSqlLiteralsAndComments(sql);
        int depth = 0;
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < sanitized.length(); i++) {
            char current = sanitized.charAt(i);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth = Math.max(0, depth - 1);
            }

            if (Character.isLetter(current)) {
                token.append(Character.toUpperCase(current));
                continue;
            }

            if (depth == 0 && "WHERE".contentEquals(token)) {
                return true;
            }
            token.setLength(0);
        }
        return depth == 0 && "WHERE".contentEquals(token);
    }

    private String stripSqlLiteralsAndComments(String sql) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            char third = i + 2 < sql.length() ? sql.charAt(i + 2) : '\0';

            if (current == '-' && next == '-') {
                i += 2;
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                result.append(' ');
                continue;
            }
            if (current == '/' && next == '*') {
                i += 2;
                while (i + 1 < sql.length() && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(sql.length(), i + 2);
                result.append(' ');
                continue;
            }
            if ((current == 'q' || current == 'Q') && next == '\'' && third != '\0') {
                char closer = matchingQQuoteDelimiter(third);
                i += 3;
                while (i + 1 < sql.length() && !(sql.charAt(i) == closer && sql.charAt(i + 1) == '\'')) {
                    i++;
                }
                i = Math.min(sql.length(), i + 2);
                result.append(' ');
                continue;
            }
            if (current == '\'') {
                i++;
                while (i < sql.length()) {
                    char ch = sql.charAt(i);
                    char chNext = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
                    if (ch == '\'' && chNext == '\'') {
                        i += 2;
                    } else if (ch == '\'') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                result.append(' ');
                continue;
            }

            result.append(current);
            i++;
        }
        return result.toString();
    }

    private char matchingQQuoteDelimiter(char openingDelimiter) {
        return switch (openingDelimiter) {
            case '[' -> ']';
            case '(' -> ')';
            case '{' -> '}';
            case '<' -> '>';
            default -> openingDelimiter;
        };
    }

    private String stripCommentsAndTrim(String sql) {
        return sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\n]*", " ")
                .stripLeading()
                .toUpperCase(Locale.ROOT);
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
                .replaceAll("(?m)^\\s*/\\s*$", "")  // rimuove righe con solo /
                .replaceAll(";\\s*$", "")            // rimuove ; finale (invalido in JDBC/subquery)
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
