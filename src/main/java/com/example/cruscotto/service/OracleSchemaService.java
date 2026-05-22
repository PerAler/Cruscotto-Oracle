package com.example.cruscotto.service;

import com.example.cruscotto.model.SchemaObject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
public class OracleSchemaService {

    private static final Logger log = LoggerFactory.getLogger(OracleSchemaService.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public OracleSchemaService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<SchemaObject> getSchemaObjects() {
        List<SchemaObject> result = new ArrayList<>();
        
        try {
            // Query tables
            String tablesQuery = "SELECT table_name FROM user_tables ORDER BY table_name";
            List<String> tables = namedParameterJdbcTemplate.getJdbcTemplate()
                    .queryForList(tablesQuery, String.class);
            
            for (String tableName : tables) {
                List<String> columns = getTableColumns(tableName);
                result.add(new SchemaObject(tableName, "TABLE", columns));
            }
            
            // Query views
            String viewsQuery = "SELECT view_name FROM user_views ORDER BY view_name";
            List<String> views = namedParameterJdbcTemplate.getJdbcTemplate()
                    .queryForList(viewsQuery, String.class);
            
            for (String viewName : views) {
                List<String> columns = getTableColumns(viewName);
                result.add(new SchemaObject(viewName, "VIEW", columns));
            }
            
        } catch (Exception ex) {
            log.error("Error fetching schema objects", ex);
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

    public Map<String, List<SchemaObject>> getGroupedSchemaObjects() {
        List<SchemaObject> all = getSchemaObjects();
        Map<String, List<SchemaObject>> grouped = new LinkedHashMap<>();
        
        List<SchemaObject> tables = new ArrayList<>();
        List<SchemaObject> views = new ArrayList<>();
        
        for (SchemaObject obj : all) {
            if ("TABLE".equals(obj.type())) {
                tables.add(obj);
            } else {
                views.add(obj);
            }
        }
        
        if (!tables.isEmpty()) {
            grouped.put("TABLES", tables);
        }
        if (!views.isEmpty()) {
            grouped.put("VIEWS", views);
        }
        
        return grouped;
    }
}
