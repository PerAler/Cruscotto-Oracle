package com.example.cruscotto.service;

import com.example.cruscotto.model.OracleConnectionInfo;
import com.example.cruscotto.model.SchemaObject;
import com.example.cruscotto.model.SchemaGroupSummary;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OracleSchemaServiceTests {

    @Test
    void returnsGroupSummariesAndLoadsSingleGroupOnDemand() {
        OracleConnectionManager connectionManager = mock(OracleConnectionManager.class);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(namedParameterJdbcTemplate.getJdbcTemplate()).thenReturn(jdbcTemplate);
        OracleConnectionInfo info = new OracleConnectionInfo(
                "conn-1",
                "local",
                "dbserver:1521:ORCL",
                "jdbc:oracle:thin:@//host:1521/service",
                "user",
                null,
                Instant.now()
        );
        OracleConnectionManager.ResolvedConnection resolved = new OracleConnectionManager.ResolvedConnection(
                info,
                null,
                namedParameterJdbcTemplate
        );
        when(connectionManager.resolveConnection(null)).thenReturn(resolved);

        when(jdbcTemplate.queryForList(contains("GROUP BY object_type")))
                .thenReturn(List.of(
                        Map.of("OBJECT_TYPE", "TABLE", "OBJECT_COUNT", 2),
                        Map.of("OBJECT_TYPE", "VIEW", "OBJECT_COUNT", 1),
                        Map.of("OBJECT_TYPE", "PACKAGE", "OBJECT_COUNT", 1)
                ));
        when(namedParameterJdbcTemplate.queryForList(contains("WHERE object_type = :objectType"), anyMap(), eq(String.class)))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    String objectType = String.valueOf(params.get("objectType"));
                    return switch (objectType) {
                        case "PACKAGE" -> List.of("PKG_UTIL");
                        case "TABLE" -> List.of("ALFA", "BETA");
                        case "VIEW" -> List.of("BETA_VIEW");
                        default -> List.of();
                    };
                });

        OracleSchemaService service = new OracleSchemaService(connectionManager);

        List<SchemaGroupSummary> summaries = service.getSchemaGroupSummaries();
        assertEquals("TABLES", summaries.get(0).key());
        assertEquals("VIEWS", summaries.get(1).key());
        assertTrue(summaries.stream().anyMatch(s -> "PACKAGES".equals(s.key())));
        assertTrue(summaries.stream().anyMatch(s -> "OTHER OBJECTS".equals(s.key())));

        List<SchemaObject> packages = service.getSchemaObjectsForGroup("PACKAGES");
        assertTrue(packages.stream().anyMatch(o -> "PKG_UTIL".equals(o.name())));
        assertTrue(packages.stream().allMatch(o -> "PACKAGE".equals(o.type())));

        List<SchemaObject> otherObjects = service.getSchemaObjectsForGroup("OTHER OBJECTS");
        assertTrue(otherObjects.isEmpty());
    }
}
