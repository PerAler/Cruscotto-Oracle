package com.example.cruscotto.service;

public record XlsxImportResult(
        String message,
        String tableName,
        int rowCount,
        String normalizedTableName
) {
}
