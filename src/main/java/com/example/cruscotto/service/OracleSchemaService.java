package com.example.cruscotto.service;

import com.example.cruscotto.model.SchemaObject;
import com.example.cruscotto.model.SchemaGroupSummary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
public class OracleSchemaService {

    private static final Logger log = LoggerFactory.getLogger(OracleSchemaService.class);
    private static final List<String> GROUP_ORDER = List.of(
            "TABLES",
            "VIEWS",
            "PROCEDURES",
            "FUNCTIONS",
            "PACKAGES",
            "PACKAGE BODIES",
            "TRIGGERS",
            "SEQUENCES",
            "SYNONYMS",
            "TYPES",
            "TYPE BODIES",
            "MATERIALIZED VIEWS",
            "MATERIALIZED VIEW LOGS",
            "JAVA SOURCES",
            "LIBRARIES",
            "JOBS",
            "SCHEDULES",
            "OTHER OBJECTS"
    );
    private static final Set<String> STANDARD_OBJECT_TYPES = Set.of(
            "TABLE",
            "VIEW",
            "PROCEDURE",
            "FUNCTION",
            "PACKAGE",
            "PACKAGE BODY",
            "TRIGGER",
            "SEQUENCE",
            "SYNONYM",
            "TYPE",
            "TYPE BODY",
            "MATERIALIZED VIEW",
            "MATERIALIZED VIEW LOG",
            "JAVA SOURCE",
            "LIBRARY",
            "JOB",
            "SCHEDULE"
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public OracleSchemaService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<SchemaGroupSummary> getSchemaGroupSummaries() {
        try {
            String query = """
                    SELECT object_type, COUNT(*) AS object_count
                    FROM user_objects
                    GROUP BY object_type
                    ORDER BY object_type
                    """;
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.getJdbcTemplate().queryForList(query);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String objectType = Objects.toString(row.get("OBJECT_TYPE"), "").trim();
                if (objectType.isBlank()) {
                    continue;
                }
                int count = ((Number) row.get("OBJECT_COUNT")).intValue();
                counts.put(objectType.toUpperCase(Locale.ROOT), count);
            }

            List<SchemaGroupSummary> summaries = new ArrayList<>();
            for (String groupKey : GROUP_ORDER) {
                String objectType = toObjectType(groupKey);
                int count = "OTHER OBJECTS".equals(groupKey)
                        ? countOtherObjects(counts)
                        : counts.getOrDefault(objectType, 0);
                summaries.add(new SchemaGroupSummary(groupKey, toDomId(groupKey), count));
            }
            return summaries;
        } catch (Exception ex) {
            log.error("Error fetching schema group summaries", ex);
            return List.of();
        }
    }

    public List<SchemaObject> getSchemaObjectsForGroup(String groupKey) {
        String normalizedGroup = normalizeGroupKey(groupKey);
        if (normalizedGroup.isBlank()) {
            return List.of();
        }

        try {
            if ("OTHER OBJECTS".equals(normalizedGroup)) {
                return loadOtherObjects();
            }
            String objectType = toObjectType(normalizedGroup);
            if (objectType == null) {
                return List.of();
            }
            return loadObjectsByType(objectType);
        } catch (Exception ex) {
            log.error("Error fetching schema objects for group {}", groupKey, ex);
            return List.of();
        }
    }

    private List<SchemaObject> loadObjectsByType(String objectType) {
        String query = """
                SELECT object_name
                FROM user_objects
                WHERE object_type = :objectType
                ORDER BY object_name
                """;
        Map<String, Object> params = Map.of("objectType", objectType);
        List<String> names = namedParameterJdbcTemplate.queryForList(query, params, String.class);
        List<SchemaObject> result = new ArrayList<>();
        for (String name : names) {
            List<String> columns = ("TABLE".equals(objectType) || "VIEW".equals(objectType))
                    ? getTableColumns(name)
                    : List.of();
            result.add(new SchemaObject(name, objectType, columns));
        }
        return result;
    }

    private List<SchemaObject> loadOtherObjects() {
        String query = """
                SELECT object_name, object_type
                FROM user_objects
                WHERE object_type NOT IN (
                    'TABLE', 'VIEW', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'PACKAGE BODY',
                    'TRIGGER', 'SEQUENCE', 'SYNONYM', 'TYPE', 'TYPE BODY',
                    'MATERIALIZED VIEW', 'MATERIALIZED VIEW LOG', 'JAVA SOURCE',
                    'LIBRARY', 'JOB', 'SCHEDULE'
                )
                ORDER BY object_type, object_name
                """;
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.getJdbcTemplate().queryForList(query);
        List<SchemaObject> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String objectName = Objects.toString(row.get("OBJECT_NAME"), "").trim();
            String objectType = Objects.toString(row.get("OBJECT_TYPE"), "").trim();
            if (objectName.isBlank() || objectType.isBlank()) {
                continue;
            }
            result.add(new SchemaObject(objectName, objectType.toUpperCase(Locale.ROOT), List.of()));
        }
        return result;
    }

    private List<String> getTableColumns(String tableName) {
        try {
            String query = "SELECT column_name FROM user_tab_columns WHERE table_name = ? ORDER BY column_id";
            return namedParameterJdbcTemplate.getJdbcTemplate()
                    .queryForList(query, String.class, tableName);
        } catch (Exception ex) {
            log.warn("Error fetching columns for {}: {}", tableName, ex.getMessage());
            return new ArrayList<>();
        }
    }

    private String toGroupName(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return "OTHER OBJECTS";
        }

        return switch (objectType.toUpperCase(Locale.ROOT)) {
            case "TABLE" -> "TABLES";
            case "VIEW" -> "VIEWS";
            case "PROCEDURE" -> "PROCEDURES";
            case "FUNCTION" -> "FUNCTIONS";
            case "PACKAGE" -> "PACKAGES";
            case "PACKAGE BODY" -> "PACKAGE BODIES";
            case "TRIGGER" -> "TRIGGERS";
            case "SEQUENCE" -> "SEQUENCES";
            case "SYNONYM" -> "SYNONYMS";
            case "TYPE" -> "TYPES";
            case "TYPE BODY" -> "TYPE BODIES";
            case "MATERIALIZED VIEW" -> "MATERIALIZED VIEWS";
            case "MATERIALIZED VIEW LOG" -> "MATERIALIZED VIEW LOGS";
            case "JAVA SOURCE" -> "JAVA SOURCES";
            case "LIBRARY" -> "LIBRARIES";
            case "JOB" -> "JOBS";
            case "SCHEDULE" -> "SCHEDULES";
            default -> objectType.toUpperCase(Locale.ROOT);
        };
    }

    private String toObjectType(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return null;
        }

        return switch (groupKey.toUpperCase(Locale.ROOT)) {
            case "TABLES" -> "TABLE";
            case "VIEWS" -> "VIEW";
            case "PROCEDURES" -> "PROCEDURE";
            case "FUNCTIONS" -> "FUNCTION";
            case "PACKAGES" -> "PACKAGE";
            case "PACKAGE BODIES" -> "PACKAGE BODY";
            case "TRIGGERS" -> "TRIGGER";
            case "SEQUENCES" -> "SEQUENCE";
            case "SYNONYMS" -> "SYNONYM";
            case "TYPES" -> "TYPE";
            case "TYPE BODIES" -> "TYPE BODY";
            case "MATERIALIZED VIEWS" -> "MATERIALIZED VIEW";
            case "MATERIALIZED VIEW LOGS" -> "MATERIALIZED VIEW LOG";
            case "JAVA SOURCES" -> "JAVA SOURCE";
            case "LIBRARIES" -> "LIBRARY";
            case "JOBS" -> "JOB";
            case "SCHEDULES" -> "SCHEDULE";
            default -> null;
        };
    }

    private String toDomId(String groupKey) {
        return groupKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private int groupPriority(String groupKey) {
        int idx = GROUP_ORDER.indexOf(normalizeGroupKey(groupKey));
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private String normalizeGroupKey(String groupKey) {
        return groupKey == null ? "" : groupKey.trim().toUpperCase(Locale.ROOT);
    }

    private int countOtherObjects(Map<String, Integer> counts) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!STANDARD_OBJECT_TYPES.contains(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
