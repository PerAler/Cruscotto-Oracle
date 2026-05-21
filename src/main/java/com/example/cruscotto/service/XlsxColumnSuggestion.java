package com.example.cruscotto.service;

public record XlsxColumnSuggestion(
        int index,
        String sourceName,
        String oracleName,
        String oracleType,
        boolean selected,
        String sampleValue
) {
}
