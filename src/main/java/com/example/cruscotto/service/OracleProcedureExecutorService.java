package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import com.example.cruscotto.model.ProcedureDefinition;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

@Service
public class OracleProcedureExecutorService {

    private static final Logger log = LoggerFactory.getLogger(OracleProcedureExecutorService.class);
    private static volatile boolean dbmsOutputCaptureInitialized = false;
    private static final Object initLock = new Object();

    private final SqlProcedureCatalogService catalogService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ExecutionLogService executionLogService;
    private final QueryOutputHtmlService queryOutputHtmlService;
    private final DataSource dataSource;

    public OracleProcedureExecutorService(SqlProcedureCatalogService catalogService,
                                          NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                          ExecutionLogService executionLogService,
                                          QueryOutputHtmlService queryOutputHtmlService) {
        this.catalogService = catalogService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.executionLogService = executionLogService;
        this.queryOutputHtmlService = queryOutputHtmlService;
        DataSource resolvedDataSource = namedParameterJdbcTemplate.getJdbcTemplate().getDataSource();
        if (resolvedDataSource == null) {
            throw new IllegalStateException("Datasource JDBC non disponibile");
        }
        this.dataSource = resolvedDataSource;
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
        String outputHtmlFile = null;
        String dbmsOutput = null;

        try {
            String executableSql = sanitizeSql(sqlText);
            if (executableSql.isBlank()) {
                throw new IllegalArgumentException("SQL vuoto");
            }

            if (selectOnly && !isAllowedExecutionStatement(executableSql)) {
                throw new IllegalArgumentException("Editor SQL: sono consentiti solo script SELECT, WITH, UPDATE, INSERT, GRANT");
            }

            MapSqlParameterSource source = new MapSqlParameterSource(safeParameters);
            ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(executableSql);
            String jdbcSql = NamedParameterUtils.substituteNamedParameters(parsedSql, source);
            Object[] values = NamedParameterUtils.buildValueArray(parsedSql, source, null);

            try (Connection connection = dataSource.getConnection()) {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    enableDbmsOutput(connection);

                    if (isSelectStatement(executableSql)) {
                        List<Map<String, Object>> rows = executeSelect(connection, jdbcSql, values);
                        outputHtmlFile = queryOutputHtmlService.saveAsHtml(executionName, rows);
                    } else {
                        executeUpdate(connection, jdbcSql, values);
                        outputHtmlFile = null;
                    }

                    flushDbmsOutputToTable(connection);
                    dbmsOutput = captureDbmsOutput(connection);
                    connection.commit();
                } catch (Exception ex) {
                    try {
                        flushDbmsOutputToTable(connection);
                        dbmsOutput = captureDbmsOutput(connection);
                    } catch (Exception captureEx) {
                        log.warn("[DBMS_OUTPUT] Cattura fallita dopo errore di esecuzione", captureEx);
                        dbmsOutput = null;
                    }
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackEx) {
                        log.warn("[DBMS_OUTPUT] Rollback fallito", rollbackEx);
                    }
                    throw ex;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
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
                    outputHtmlFile,
                    dbmsOutput
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
                    stackTrace,
                    dbmsOutput
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

    /** Restituisce true se lo statement è un SQL permesso per l'esecuzione (SELECT, WITH, UPDATE, INSERT, GRANT). */
    private boolean isAllowedExecutionStatement(String sql) {
        String upper = sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\n]*", " ")
                .stripLeading()
                .toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH") || 
               upper.startsWith("UPDATE") || upper.startsWith("INSERT") || 
               upper.startsWith("GRANT");
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

    private List<Map<String, Object>> executeSelect(Connection connection, String jdbcSql, Object[] values) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(jdbcSql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            boolean hasResults = ps.execute();
            if (!hasResults) {
                return List.of();
            }
            try (ResultSet rs = ps.getResultSet()) {
                return mapResultSet(rs);
            }
        }
    }

    private void executeUpdate(Connection connection, String jdbcSql, Object[] values) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(jdbcSql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.execute();
        }
    }

    private String sanitizeSql(String sqlText) {
        return sqlText
                .replaceAll("(?m)^\\s*/\\s*$", "")
                .trim();
    }

    private void initializeDbmsOutputCapture() {
        if (dbmsOutputCaptureInitialized) {
            return;
        }
        synchronized (initLock) {
            if (dbmsOutputCaptureInitialized) {
                return;
            }
            try {
                // Verifica se la tabella esiste; se non esiste, creala
                try {
                    namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM user_tables WHERE table_name = 'APP_DBMS_OUTPUT_LINES'",
                        Integer.class
                    );
                    log.debug("[DBMS_OUTPUT] Tabella app_dbms_output_lines esiste già");
                } catch (Exception checkEx) {
                    // Tabella non esiste, creiamola
                    log.debug("[DBMS_OUTPUT] Tabella app_dbms_output_lines non esiste, creazione in corso");
                    namedParameterJdbcTemplate.getJdbcTemplate().execute(
                        "CREATE GLOBAL TEMPORARY TABLE app_dbms_output_lines (" +
                        "  line_num NUMBER PRIMARY KEY, " +
                        "  line_text VARCHAR2(32767)" +
                        ") ON COMMIT DELETE ROWS"
                    );
                    log.debug("[DBMS_OUTPUT] Tabella app_dbms_output_lines creata con successo");
                }
                dbmsOutputCaptureInitialized = true;
                log.debug("[DBMS_OUTPUT] Inizializzazione DBMS_OUTPUT capture completata");
            } catch (Exception ex) {
                // Se fallisce la creazione, logghiamo ma non interrompiamo
                log.warn("[DBMS_OUTPUT] Avvertimento durante inizializzazione: " + ex.getMessage());
                // Non settare il flag a true se la creazione è fallita, così ritenteremo alla prossima esecuzione
            }
        }
    }

    private String captureDbmsOutput(Connection connection) throws SQLException {
        initializeDbmsOutputCapture();
        log.debug("[DBMS_OUTPUT] Lettura output dalla tabella temporanea");
        try (PreparedStatement ps = connection.prepareStatement("SELECT line_text FROM app_dbms_output_lines ORDER BY line_num");
             ResultSet rs = ps.executeQuery()) {
            StringBuilder output = new StringBuilder();
            while (rs.next()) {
                String line = rs.getString(1);
                if (line != null) {
                    output.append(line).append('\n');
                }
            }
            String result = output.toString().trim();
            if (result.isBlank()) {
                log.debug("[DBMS_OUTPUT] Nessuna linea di output catturata");
                return null;
            }
            log.debug("[DBMS_OUTPUT] Output catturato ({} chars)", result.length());
            return result;
        }
    }

    private void enableDbmsOutput(Connection connection) throws SQLException {
        initializeDbmsOutputCapture();
        log.debug("[DBMS_OUTPUT] Abilitazione DBMS_OUTPUT e pulizia buffer");
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN DBMS_OUTPUT.ENABLE(10000); DELETE FROM app_dbms_output_lines; END;");
        }
        log.debug("[DBMS_OUTPUT] DBMS_OUTPUT abilitato e buffer pulito");
    }

    private void flushDbmsOutputToTable(Connection connection) throws SQLException {
        log.debug("[DBMS_OUTPUT] Flush da DBMS_OUTPUT a tabella temporanea");
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "DECLARE " +
                "  v_lines DBMS_OUTPUT.chararr; " +
                "  v_numlines INTEGER := 0; " +
                "BEGIN " +
                "  DBMS_OUTPUT.GET_LINES(v_lines, v_numlines); " +
                "  FOR i IN 1..v_numlines LOOP " +
                "    INSERT INTO app_dbms_output_lines (line_num, line_text) " +
                "    VALUES (i, v_lines(i)); " +
                "  END LOOP; " +
                "END;"
            );
        }
        log.debug("[DBMS_OUTPUT] Flush completato");
    }

    /** Converte un'eccezione in una stringa di stack trace. */
    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}

