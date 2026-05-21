package com.example.cruscotto.service;

import java.util.List;

public record XlsxImportAnalysis(
        String token,
        String fileName,
        String sheetName,
        int dataRowCount,
        List<XlsxColumnSuggestion> columns,
        List<List<String>> previewRows,
        String createTableSql
) {
}
