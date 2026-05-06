package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExecutionLogOracleStore {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionLogOracleStore.class);

    private static final String CREATE_TABLE_BLOCK = """
            BEGIN
                EXECUTE IMMEDIATE '
                    CREATE TABLE CRUSCOTTO_EXEC_LOG (
                        EVENT_TS TIMESTAMP(6) NOT NULL,
                        PROCEDURE_NAME VARCHAR2(255) NOT NULL,
                        STATUS VARCHAR2(10) NOT NULL,
                        DURATION_MS NUMBER(19),
                        MESSAGE VARCHAR2(4000),
                        PARAMETERS_TEXT CLOB,
                        OUTPUT_HTML_FILE VARCHAR2(512),
                        STACK_TRACE CLOB
                    )';
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE != -955 THEN
                        RAISE;
                    END IF;
            END;
            """;

    private static final String INSERT_SQL = """
            INSERT INTO CRUSCOTTO_EXEC_LOG (
                EVENT_TS,
                PROCEDURE_NAME,
                STATUS,
                DURATION_MS,
                MESSAGE,
                PARAMETERS_TEXT,
                OUTPUT_HTML_FILE,
                STACK_TRACE
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String DELETE_ONE_SQL = """
            DELETE FROM CRUSCOTTO_EXEC_LOG
            WHERE ROWID IN (
                SELECT ROWID
                FROM (
                    SELECT ROWID
                    FROM CRUSCOTTO_EXEC_LOG
                    WHERE EVENT_TS = ?
                      AND PROCEDURE_NAME = ?
                      AND STATUS = ?
                      AND DURATION_MS = ?
                      AND NVL(MESSAGE, ' ') = NVL(?, ' ')
                      AND NVL(OUTPUT_HTML_FILE, ' ') = NVL(?, ' ')
                    ORDER BY EVENT_TS DESC
                )
                WHERE ROWNUM = 1
            )
            """;

    private static final String DELETE_ALL_ERRORS_SQL = """
            DELETE FROM CRUSCOTTO_EXEC_LOG
            WHERE STATUS = 'KO'
            """;

            private static final String SELECT_LATEST_SQL = """
                 SELECT EVENT_TS,
                     PROCEDURE_NAME,
                     STATUS,
                     DURATION_MS,
                     MESSAGE,
                     PARAMETERS_TEXT,
                     OUTPUT_HTML_FILE,
                     STACK_TRACE
                 FROM (
                  SELECT EVENT_TS,
                      PROCEDURE_NAME,
                      STATUS,
                      DURATION_MS,
                      MESSAGE,
                      PARAMETERS_TEXT,
                      OUTPUT_HTML_FILE,
                      STACK_TRACE
                  FROM CRUSCOTTO_EXEC_LOG
                  ORDER BY EVENT_TS DESC
                 )
                 WHERE ROWNUM <= ?
                 """;

    private final JdbcTemplate jdbcTemplate;
    private final boolean persistenceEnabled;
    private final AtomicBoolean initAttempted = new AtomicBoolean(false);
    private final AtomicBoolean persistenceAvailable = new AtomicBoolean(false);

    public ExecutionLogOracleStore(JdbcTemplate jdbcTemplate,
                                   @Value("${app.logs.persist.enabled:true}") boolean persistenceEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceEnabled = persistenceEnabled;
    }

    public void persist(ExecutionLogEntry entry) {
        if (!persistenceEnabled) {
            return;
        }
        if (!ensureStoreReady()) {
            return;
        }

        try {
            jdbcTemplate.update(INSERT_SQL,
                    Timestamp.valueOf(entry.timestamp()),
                    entry.procedureName(),
                    entry.status(),
                    entry.durationMs(),
                    trimForVarchar(entry.message(), 4000),
                    stringifyParams(entry.parameters()),
                    trimForVarchar(entry.outputHtmlFile(), 512),
                    entry.stackTrace());
        } catch (Exception ex) {
            logger.warn("Persistenza log non riuscita: {}", ex.getMessage());
            persistenceAvailable.set(false);
        }
    }

    public List<ExecutionLogEntry> loadLatest(int maxRows) {
        if (!persistenceEnabled || maxRows <= 0) {
            return Collections.emptyList();
        }
        if (!ensureStoreReady()) {
            return Collections.emptyList();
        }

        try {
            return jdbcTemplate.query(SELECT_LATEST_SQL, ps -> ps.setInt(1, maxRows), (rs, rowNum) -> {
                Timestamp eventTs = rs.getTimestamp("EVENT_TS");
                LocalDateTime timestamp = (eventTs != null) ? eventTs.toLocalDateTime() : LocalDateTime.now();

                long durationMs = rs.getLong("DURATION_MS");
                if (rs.wasNull()) {
                    durationMs = 0L;
                }

                return new ExecutionLogEntry(
                        timestamp,
                        rs.getString("PROCEDURE_NAME"),
                        rs.getString("STATUS"),
                        durationMs,
                        rs.getString("MESSAGE"),
                        parseParamsText(rs.getString("PARAMETERS_TEXT")),
                        rs.getString("OUTPUT_HTML_FILE"),
                        rs.getString("STACK_TRACE")
                );
            });
        } catch (Exception ex) {
            logger.warn("Lettura log persistiti non riuscita: {}", ex.getMessage());
            persistenceAvailable.set(false);
            return Collections.emptyList();
        }
    }

    public boolean deleteOne(ExecutionLogEntry entry) {
        if (!persistenceEnabled || entry == null) {
            return false;
        }
        if (!ensureStoreReady()) {
            return false;
        }

        try {
            int updated = jdbcTemplate.update(DELETE_ONE_SQL,
                    Timestamp.valueOf(entry.timestamp()),
                    entry.procedureName(),
                    entry.status(),
                    entry.durationMs(),
                    trimForVarchar(entry.message(), 4000),
                    trimForVarchar(entry.outputHtmlFile(), 512));
            return updated > 0;
        } catch (Exception ex) {
            logger.warn("Cancellazione log persistito non riuscita: {}", ex.getMessage());
            persistenceAvailable.set(false);
            return false;
        }
    }

    public int deleteAllErrors() {
        if (!persistenceEnabled) {
            return 0;
        }
        if (!ensureStoreReady()) {
            return 0;
        }

        try {
            return jdbcTemplate.update(DELETE_ALL_ERRORS_SQL);
        } catch (Exception ex) {
            logger.warn("Cancellazione completa errori non riuscita: {}", ex.getMessage());
            persistenceAvailable.set(false);
            return 0;
        }
    }

    private boolean ensureStoreReady() {
        if (!initAttempted.compareAndSet(false, true)) {
            return persistenceAvailable.get();
        }

        try {
            jdbcTemplate.execute(CREATE_TABLE_BLOCK);
            persistenceAvailable.set(true);
            return true;
        } catch (Exception ex) {
            logger.warn("Store log Oracle non disponibile: {}", ex.getMessage());
            persistenceAvailable.set(false);
            return false;
        }
    }

    private String stringifyParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return params.toString();
    }

    private String trimForVarchar(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private Map<String, Object> parseParamsText(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String body = trimmed.substring(1, trimmed.length() - 1).trim();
            if (body.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, Object> parsed = new LinkedHashMap<>();
            String[] pairs = body.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                String key = keyValue[0].trim();
                String value = keyValue.length > 1 ? keyValue[1].trim() : "";
                if (!key.isEmpty()) {
                    parsed.put(key, value);
                }
            }
            return parsed;
        }

        return Map.of("raw", trimmed);
    }
}