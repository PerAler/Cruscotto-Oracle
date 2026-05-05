package com.example.cruscotto.model;

import java.time.LocalDateTime;
import java.util.Map;

public record ExecutionLogEntry(
        LocalDateTime timestamp,
        String procedureName,
        String status,
        long durationMs,
        String message,
        Map<String, Object> parameters,
        String outputHtmlFile,
        String stackTrace
) {
    /** Costruttore di compatibilità senza output HTML (procedure DML/stored procedure). */
    public ExecutionLogEntry(LocalDateTime timestamp, String procedureName, String status,
                             long durationMs, String message, Map<String, Object> parameters) {
        this(timestamp, procedureName, status, durationMs, message, parameters, null, null);
    }

    /** Costruttore con output HTML. */
    public ExecutionLogEntry(LocalDateTime timestamp, String procedureName, String status,
                             long durationMs, String message, Map<String, Object> parameters, String outputHtmlFile) {
        this(timestamp, procedureName, status, durationMs, message, parameters, outputHtmlFile, null);
    }
}
