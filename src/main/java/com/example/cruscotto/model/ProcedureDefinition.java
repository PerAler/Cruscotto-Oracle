package com.example.cruscotto.model;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record ProcedureDefinition(
	String name,
	String resourcePath,
	String sqlText,
	List<String> parameterNames,
    Map<String, String> parameterDescriptions,
	long lastModifiedEpochMs
) {
    private static final Pattern BLOCK_PATTERN =
            Pattern.compile("(?i)\\b(BEGIN|DECLARE)\\b");
    private static final Pattern QUERY_PATTERN =
            Pattern.compile("(?i)\\b(WITH|SELECT)\\b");

    /**
     * "Procedura" per blocchi anonimi (BEGIN/DECLARE),
     * "Script" per query (WITH/SELECT).
     */
    public String scriptType() {
        if (BLOCK_PATTERN.matcher(sqlText).find()) {
            return "Procedura";
        }
        if (QUERY_PATTERN.matcher(sqlText).find()) {
            return "Script";
        }
        return "Script";
    }

    public String paramDescription(String paramName) {
        String direct = parameterDescriptions.get(paramName);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        for (Map.Entry<String, String> entry : parameterDescriptions.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(paramName) && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return null;
    }
}
