package com.example.cruscotto.controller;

import com.example.cruscotto.service.OracleTableColumnInfo;
import com.example.cruscotto.service.XlsxColumnSuggestion;
import com.example.cruscotto.service.XlsxImportAnalysis;
import com.example.cruscotto.service.XlsxImportResult;
import com.example.cruscotto.service.XlsxImportService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
public class XlsxImportController {

    private final XlsxImportService xlsxImportService;

    public XlsxImportController(XlsxImportService xlsxImportService) {
        this.xlsxImportService = xlsxImportService;
    }

    @GetMapping("/xlsx-import")
    public String page(Model model) {
        prepareModel(model, null, "", "", null, List.of(), null, null, null);
        return "xlsx-import";
    }

    @PostMapping(value = "/xlsx-import/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String analyze(@RequestParam("xlsxFile") MultipartFile xlsxFile,
                          @RequestParam("tableName") String tableName,
                          Model model) {
        try {
            requireTableName(tableName);
            if (xlsxFile == null || xlsxFile.isEmpty()) {
                throw new IllegalArgumentException("Seleziona un file XLSX");
            }
            String token = xlsxImportService.storeWorkbook(xlsxFile.getBytes(), xlsxFile.getOriginalFilename());
            XlsxImportAnalysis analysis = xlsxImportService.analyzeWorkbook(token);
            List<XlsxColumnSuggestion> selectedColumns = analysis.columns();
            String createSql = xlsxImportService.buildCreateTablePreview(tableName, selectedColumns);
            prepareModel(model, analysis, tableName, token, null, selectedColumns, createSql,
                    "File analizzato: " + xlsxFile.getOriginalFilename(), null);
        } catch (Exception ex) {
            prepareModel(model, null, tableName, "", null, List.of(), null, null, ex.getMessage());
        }
        return "xlsx-import";
    }

    @PostMapping("/xlsx-import/read-structure")
    public String readStructure(@RequestParam("tableName") String tableName,
                                @RequestParam(value = "importToken", required = false) String importToken,
                                Model model) {
        try {
            requireTableName(tableName);
            XlsxImportAnalysis analysis = loadAnalysis(importToken);
            List<OracleTableColumnInfo> structure = xlsxImportService.readTableStructure(tableName);
            List<XlsxColumnSuggestion> selectedColumns = analysis == null ? List.of() : analysis.columns();
            String createSql = analysis == null ? null : xlsxImportService.buildCreateTablePreview(tableName, selectedColumns);
            prepareModel(model, analysis, tableName, importToken, structure,
                    selectedColumns,
                    createSql, "Struttura letta per tabella " + tableName, null);
        } catch (Exception ex) {
            prepareModel(model, loadAnalysisOrNull(importToken), tableName, importToken, null, List.of(), null, null, ex.getMessage());
        }
        return "xlsx-import";
    }

    @PostMapping("/xlsx-import/create-table")
    public String createTable(@RequestParam("tableName") String tableName,
                              @RequestParam(value = "importToken", required = false) String importToken,
                              @RequestParam(value = "selectedColumns", required = false) String[] selectedColumns,
                              Model model) {
        try {
            requireTableName(tableName);
            XlsxImportAnalysis analysis = requireAnalysis(importToken);
            Set<Integer> selected = parseSelectedColumns(selectedColumns);
            requireSelectedColumns(selected);
            List<XlsxColumnSuggestion> selectedSuggestions = selectColumns(analysis, selected);
            String createSql = xlsxImportService.buildCreateTablePreview(tableName, selectedSuggestions);
            XlsxImportResult result = xlsxImportService.createTable(tableName, importToken, new java.util.ArrayList<>(selected));
            prepareModel(model, analysis, tableName, importToken, null, selectedSuggestions, createSql,
                    result.message() + " (" + result.normalizedTableName() + ")", null);
        } catch (Exception ex) {
            prepareModel(model, loadAnalysisOrNull(importToken), tableName, importToken, null, List.of(), null, null, ex.getMessage());
        }
        return "xlsx-import";
    }

    @PostMapping("/xlsx-import/import")
    public String importRows(@RequestParam("tableName") String tableName,
                             @RequestParam(value = "importToken", required = false) String importToken,
                             @RequestParam(value = "selectedColumns", required = false) String[] selectedColumns,
                             @RequestParam(value = "truncateBeforeImport", required = false, defaultValue = "false") boolean truncateBeforeImport,
                             Model model) {
        try {
            requireTableName(tableName);
            XlsxImportAnalysis analysis = requireAnalysis(importToken);
            Set<Integer> selected = parseSelectedColumns(selectedColumns);
            requireSelectedColumns(selected);
            List<XlsxColumnSuggestion> selectedSuggestions = selectColumns(analysis, selected);
            XlsxImportResult result = xlsxImportService.importRows(tableName, importToken, new java.util.ArrayList<>(selected), truncateBeforeImport);
            prepareModel(model, analysis, tableName, importToken, null, selectedSuggestions, null,
                    result.rowCount() + " righe importate in " + result.normalizedTableName(), null);
        } catch (Exception ex) {
            prepareModel(model, loadAnalysisOrNull(importToken), tableName, importToken, null, List.of(), null, null, ex.getMessage());
        }
        return "xlsx-import";
    }

    private void prepareModel(Model model,
                              XlsxImportAnalysis analysis,
                              String tableName,
                              String importToken,
                              List<OracleTableColumnInfo> dbStructure,
                              List<XlsxColumnSuggestion> selectedColumns,
                              String createSqlPreview,
                              String successMessage,
                              String errorMessage) {
        model.addAttribute("analysis", analysis);
        model.addAttribute("tableName", tableName == null ? "" : tableName);
        model.addAttribute("importToken", importToken == null ? "" : importToken);
        model.addAttribute("dbStructure", dbStructure);
        model.addAttribute("selectedColumns", selectedColumns);
        model.addAttribute("createSqlPreview", createSqlPreview);
        model.addAttribute("successMessage", successMessage);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("selectedColumnIndexes", selectedColumns == null
                ? Set.of()
                : selectedColumns.stream().map(XlsxColumnSuggestion::index).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private XlsxImportAnalysis requireAnalysis(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Analizza prima il file XLSX");
        }
        return xlsxImportService.analyzeWorkbook(token);
    }

    private void requireTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Inserisci il nome della tabella Oracle");
        }
    }

    private void requireSelectedColumns(Set<Integer> selectedColumns) {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            throw new IllegalArgumentException("Seleziona almeno una colonna da importare");
        }
    }

    private XlsxImportAnalysis loadAnalysis(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return xlsxImportService.analyzeWorkbook(token);
    }

    private XlsxImportAnalysis loadAnalysisOrNull(String token) {
        try {
            return loadAnalysis(token);
        } catch (Exception ex) {
            return null;
        }
    }

    private Set<Integer> parseSelectedColumns(String[] selectedColumns) {
        Set<Integer> selected = new LinkedHashSet<>();
        if (selectedColumns == null) {
            return selected;
        }
        Arrays.stream(selectedColumns)
                .filter(value -> value != null && !value.isBlank())
                .map(Integer::parseInt)
                .forEach(selected::add);
        return selected;
    }

    private List<XlsxColumnSuggestion> selectColumns(XlsxImportAnalysis analysis, Set<Integer> selected) {
        if (analysis == null || analysis.columns() == null) {
            return List.of();
        }
        if (selected.isEmpty()) {
            return analysis.columns();
        }
        return analysis.columns().stream()
                .filter(column -> selected.contains(column.index()))
                .toList();
    }

}
