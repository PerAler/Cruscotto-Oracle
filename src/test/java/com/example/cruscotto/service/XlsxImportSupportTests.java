package com.example.cruscotto.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxImportSupportTests {

    @Test
    void normalizesOracleIdentifiersAndTruncatesToOracleLimit() {
        String normalized = XlsxImportSupport.normalizeOracleIdentifier("  Rèport 2026 / alpha  ", "COL", 1);
        assertEquals("REPORT_2026_ALPHA", normalized);

        String longName = XlsxImportSupport.normalizeOracleIdentifier("abcdefghijklmnopqrstuvwxyz0123456789", "COL", 1);
        assertTrue(longName.length() <= 30);
    }

    @Test
    void makesOracleIdentifiersUnique() {
        Set<String> used = new LinkedHashSet<>();
        String first = XlsxImportSupport.makeUniqueOracleIdentifier("COLUMN", used);
        String second = XlsxImportSupport.makeUniqueOracleIdentifier("COLUMN", used);

        assertEquals("COLUMN", first);
        assertEquals("COLUMN_2", second);
    }

    @Test
    void buildsCreateTableSqlFromSuggestions() {
        String sql = XlsxImportSupport.buildCreateTableSql("fatture xlsx", List.of(
                new XlsxColumnSuggestion(0, "Numero Fattura", "NUMERO_FATTURA", "VARCHAR2(30 CHAR)", true, "A-1"),
                new XlsxColumnSuggestion(1, "Importo", "IMPORTO", "NUMBER(38,0)", true, "10")
        ));

        assertTrue(sql.startsWith("CREATE TABLE FATTURE_XLSX"));
        assertTrue(sql.contains("NUMERO_FATTURA VARCHAR2(30 CHAR)"));
        assertTrue(sql.contains("IMPORTO NUMBER(38,0)"));
    }
}
