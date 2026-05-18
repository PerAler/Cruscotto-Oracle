package com.example.cruscotto.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlProcedureCatalogServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void reloadProceduresLoadsNestedSqlFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("Unit_Test.sql"), "SELECT 1 FROM dual");
        Files.writeString(tempDir.resolve("nested").resolve("Nested_Test.sql"), "SELECT 2 FROM dual");

        SqlProcedureCatalogService service = new SqlProcedureCatalogService(tempDir.toString());
        service.reloadProcedures();

        assertTrue(service.findByName("Unit_Test").isPresent());
        assertTrue(service.findByName("Nested_Test").isPresent());
    }

    @Test
    void saveAndUpdateUseConfiguredSqlFolder() throws Exception {
        SqlProcedureCatalogService service = new SqlProcedureCatalogService(tempDir.toString());

        String savedName = service.saveSqlFile("My Script", "SELECT 1 FROM dual", false);
        assertEquals("My_Script", savedName);
        assertTrue(Files.exists(tempDir.resolve("My_Script.sql")));

        String updatedName = service.updateSqlFile(savedName, "SELECT 2 FROM dual");
        assertEquals("My_Script", updatedName);
        assertTrue(Files.readString(tempDir.resolve("My_Script.sql")).contains("SELECT 2 FROM dual"));
    }
}
