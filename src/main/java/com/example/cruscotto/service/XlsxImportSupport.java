package com.example.cruscotto.service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

final class XlsxImportSupport {

    private static final int ORACLE_IDENTIFIER_MAX_LENGTH = 30;

    private XlsxImportSupport() {
    }

    static String normalizeOracleIdentifier(String value, String fallbackPrefix, int fallbackIndex) {
        String base = value == null ? "" : value.trim();
        base = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        base = base.replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);

        if (base.isBlank()) {
            base = fallbackPrefix + "_" + fallbackIndex;
        }
        if (Character.isDigit(base.charAt(0))) {
            base = fallbackPrefix + "_" + base;
        }
        return trimToOracleLimit(base);
    }

    static String makeUniqueOracleIdentifier(String value, Set<String> usedNames) {
        String candidate = trimToOracleLimit(value);
        if (usedNames.add(candidate)) {
            return candidate;
        }

        int suffix = 2;
        while (true) {
            String suffixText = "_" + suffix++;
            int maxBaseLength = ORACLE_IDENTIFIER_MAX_LENGTH - suffixText.length();
            String base = candidate.length() > maxBaseLength ? candidate.substring(0, maxBaseLength) : candidate;
            base = base.replaceAll("_+$", "");
            if (base.isBlank()) {
                base = "COL";
            }
            String attempt = trimToOracleLimit(base + suffixText);
            if (usedNames.add(attempt)) {
                return attempt;
            }
        }
    }

    static String buildCreateTableSql(String tableName, List<XlsxColumnSuggestion> columns) {
        String normalizedTableName = normalizeOracleIdentifier(tableName, "XLSX_TABLE", 1);
        StringJoiner joiner = new StringJoiner(",\n    ", "CREATE TABLE " + normalizedTableName + " (\n    ", "\n)");
        for (XlsxColumnSuggestion column : columns) {
            joiner.add(column.oracleName() + " " + column.oracleType());
        }
        return joiner.toString();
    }

    private static String trimToOracleLimit(String value) {
        if (value.length() <= ORACLE_IDENTIFIER_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, ORACLE_IDENTIFIER_MAX_LENGTH).replaceAll("_+$", "");
    }
}
