package com.example.cruscotto.service;

import com.example.cruscotto.model.SchemaObject;
import com.example.cruscotto.model.SchemaGroupSummary;
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

    private final OracleConnectionManager connectionManager;

    public OracleSchemaService(OracleConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public List<SchemaGroupSummary> getSchemaGroupSummaries() {
        return getSchemaGroupSummaries(null);
    }

    public List<SchemaGroupSummary> getSchemaGroupSummaries(String connectionId) {
        try {
            OracleConnectionManager.ResolvedConnection resolved = connectionManager.resolveConnection(connectionId);
            String owner = resolved.info().schema();
            String query = owner == null
                    ? """
                    SELECT object_type, COUNT(*) AS object_count
                    FROM user_objects
                    GROUP BY object_type
                    ORDER BY object_type
                    """
                    : """
                    SELECT object_type, COUNT(*) AS object_count
                    FROM all_objects
                    WHERE owner = :owner
                    GROUP BY object_type
                    ORDER BY object_type
                    """;
            List<Map<String, Object>> rows = owner == null
                    ? resolved.template().getJdbcTemplate().queryForList(query)
                    : resolved.template().queryForList(query, Map.of("owner", owner));
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
        return getSchemaObjectsForGroup(null, groupKey);
    }

    public List<SchemaObject> getSchemaObjectsForGroup(String connectionId, String groupKey) {
        String normalizedGroup = normalizeGroupKey(groupKey);
        if (normalizedGroup.isBlank()) {
            return List.of();
        }

        try {
            OracleConnectionManager.ResolvedConnection resolved = connectionManager.resolveConnection(connectionId);
            if ("OTHER OBJECTS".equals(normalizedGroup)) {
                return loadOtherObjects(resolved);
            }
            String objectType = toObjectType(normalizedGroup);
            if (objectType == null) {
                return List.of();
            }
            return loadObjectsByType(resolved, objectType);
        } catch (Exception ex) {
            log.error("Error fetching schema objects for group {}", groupKey, ex);
            return List.of();
        }
    }

    public Optional<String> getSchemaObjectSource(String connectionId, String objectType, String objectName) {
        String normalizedObjectType = normalizeGroupObjectType(objectType);
        String normalizedObjectName = objectName == null ? "" : objectName.trim();
        if (normalizedObjectType.isBlank() || normalizedObjectName.isBlank()) {
            return Optional.empty();
        }

        try {
            OracleConnectionManager.ResolvedConnection resolved = connectionManager.resolveConnection(connectionId);
            String owner = resolved.info().schema();
            String metadataType = toMetadataObjectType(normalizedObjectType);
            if (metadataType == null || metadataType.isBlank()) {
                return Optional.empty();
            }

            String query = owner == null
                    ? """
                    SELECT DBMS_METADATA.GET_DDL(:objectType, :objectName) AS ddl
                    FROM dual
                    """
                    : """
                    SELECT DBMS_METADATA.GET_DDL(:objectType, :objectName, :owner) AS ddl
                    FROM dual
                    """
                    ;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("objectType", metadataType);
            params.put("objectName", normalizedObjectName);
            if (owner != null && !owner.isBlank()) {
                params.put("owner", owner.trim());
            }

            String ddl = resolved.template().queryForObject(query, params, String.class);
            return Optional.ofNullable(ddl).map(String::trim).filter(sql -> !sql.isBlank());
        } catch (Exception ex) {
            log.error("Error fetching schema source for {} {}", normalizedObjectType, normalizedObjectName, ex);
            return Optional.empty();
        }
    }

    private List<SchemaObject> loadObjectsByType(OracleConnectionManager.ResolvedConnection resolved, String objectType) {
        String owner = resolved.info().schema();
        String query = owner == null
                ? """
                SELECT object_name
                FROM user_objects
                WHERE object_type = :objectType
                ORDER BY object_name
                """
                : """
                SELECT object_name
                FROM all_objects
                WHERE owner = :owner
                  AND object_type = :objectType
                ORDER BY object_name
                """;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("objectType", objectType);
        if (owner != null) {
            params.put("owner", owner);
        }
        List<String> names = resolved.template().queryForList(query, params, String.class);
        List<SchemaObject> result = new ArrayList<>();
        for (String name : names) {
            List<String> columns = ("TABLE".equals(objectType) || "VIEW".equals(objectType))
                    ? getTableColumns(resolved, name)
                    : List.of();
            result.add(new SchemaObject(name, objectType, columns));
        }
        return result;
    }

    private List<SchemaObject> loadOtherObjects(OracleConnectionManager.ResolvedConnection resolved) {
        String owner = resolved.info().schema();
        String query = owner == null
                ? """
                SELECT object_name, object_type
                FROM user_objects
                WHERE object_type NOT IN (
                    'TABLE', 'VIEW', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'PACKAGE BODY',
                    'TRIGGER', 'SEQUENCE', 'SYNONYM', 'TYPE', 'TYPE BODY',
                    'MATERIALIZED VIEW', 'MATERIALIZED VIEW LOG', 'JAVA SOURCE',
                    'LIBRARY', 'JOB', 'SCHEDULE'
                )
                ORDER BY object_type, object_name
                """
                : """
                SELECT object_name, object_type
                FROM all_objects
                WHERE owner = :owner
                  AND object_type NOT IN (
                    'TABLE', 'VIEW', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'PACKAGE BODY',
                    'TRIGGER', 'SEQUENCE', 'SYNONYM', 'TYPE', 'TYPE BODY',
                    'MATERIALIZED VIEW', 'MATERIALIZED VIEW LOG', 'JAVA SOURCE',
                    'LIBRARY', 'JOB', 'SCHEDULE'
                )
                ORDER BY object_type, object_name
                """;
        List<Map<String, Object>> rows = owner == null
                ? resolved.template().getJdbcTemplate().queryForList(query)
                : resolved.template().queryForList(query, Map.of("owner", owner));
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

    private List<String> getTableColumns(OracleConnectionManager.ResolvedConnection resolved, String tableName) {
        String owner = resolved.info().schema();
        try {
            if (owner == null) {
                String query = "SELECT column_name FROM user_tab_columns WHERE table_name = ? ORDER BY column_id";
                return resolved.template().getJdbcTemplate().queryForList(query, String.class, tableName);
            }
            String query = """
                    SELECT column_name
                    FROM all_tab_columns
                    WHERE owner = :owner
                      AND table_name = :tableName
                    ORDER BY column_id
                    """;
            Map<String, Object> params = Map.of("owner", owner, "tableName", tableName);
            return resolved.template().queryForList(query, params, String.class);
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

    private String toMetadataObjectType(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return null;
        }

        return switch (objectType.trim().toUpperCase(Locale.ROOT)) {
            case "TABLE" -> "TABLE";
            case "VIEW" -> "VIEW";
            case "PROCEDURE" -> "PROCEDURE";
            case "FUNCTION" -> "FUNCTION";
            case "PACKAGE" -> "PACKAGE";
            case "PACKAGE BODY" -> "PACKAGE_BODY";
            case "TRIGGER" -> "TRIGGER";
            case "SEQUENCE" -> "SEQUENCE";
            case "SYNONYM" -> "SYNONYM";
            case "TYPE" -> "TYPE";
            case "TYPE BODY" -> "TYPE_BODY";
            case "MATERIALIZED VIEW" -> "MATERIALIZED_VIEW";
            case "MATERIALIZED VIEW LOG" -> "MATERIALIZED_VIEW_LOG";
            case "JAVA SOURCE" -> "JAVA_SOURCE";
            case "LIBRARY" -> "LIBRARY";
            default -> objectType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
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

    private String normalizeGroupObjectType(String objectType) {
        if (objectType == null) {
            return "";
        }
        String normalized = objectType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
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
            default -> normalized;
        };
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
