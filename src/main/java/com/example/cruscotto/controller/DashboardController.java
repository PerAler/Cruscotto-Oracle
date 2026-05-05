package com.example.cruscotto.controller;

import com.example.cruscotto.service.ExecutionLogService;
import com.example.cruscotto.service.OracleProcedureExecutorService;
import com.example.cruscotto.service.QueryOutputHtmlService;
import com.example.cruscotto.service.ScheduledExecutionService;
import com.example.cruscotto.service.SqlProcedureCatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class DashboardController {

    private final SqlProcedureCatalogService catalogService;
    private final OracleProcedureExecutorService executorService;
    private final ScheduledExecutionService scheduledExecutionService;
    private final ExecutionLogService executionLogService;
    private final QueryOutputHtmlService queryOutputHtmlService;
    private final List<String> applications;
    private final String defaultApplication;
    private final String runtimePid;
    private final Instant runtimeStartedAt;

    public DashboardController(SqlProcedureCatalogService catalogService,
                               OracleProcedureExecutorService executorService,
                               ScheduledExecutionService scheduledExecutionService,
                               ExecutionLogService executionLogService,
                               QueryOutputHtmlService queryOutputHtmlService,
                               @Value("${app.target-applications:ALER}") String configuredApplications,
                               @Value("${app.default-application:ALER}") String defaultApplication) {
        this.catalogService = catalogService;
        this.executorService = executorService;
        this.scheduledExecutionService = scheduledExecutionService;
        this.executionLogService = executionLogService;
        this.queryOutputHtmlService = queryOutputHtmlService;
        this.applications = Arrays.stream(configuredApplications.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        this.defaultApplication = defaultApplication;
        this.runtimePid = resolvePid();
        this.runtimeStartedAt = Instant.now();
    }

    @GetMapping({"/", "/dashboard", "/dashboard/"})
    public String index(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure,
                        @RequestParam(value = "successMessage", required = false) String successMessage,
                        @RequestParam(value = "errorMessage", required = false) String errorMessage,
                        Model model) {
        List<com.example.cruscotto.model.ProcedureDefinition> allProcedures = catalogService.findAll();
        String effectiveSelection = resolveSelection(selectedProcedure, allProcedures);

        List<com.example.cruscotto.model.ProcedureDefinition> visibleProcedures = allProcedures;
        if (effectiveSelection != null && !effectiveSelection.isBlank()) {
            visibleProcedures = allProcedures.stream()
                    .filter(p -> p.name().equals(effectiveSelection))
                    .toList();
        }

        // Conta errori totali
        long errorCount = executionLogService.latest()
                .stream()
                .filter(e -> "KO".equals(e.status()))
                .count();
        long okCount = executionLogService.latest()
            .stream()
            .filter(e -> "OK".equals(e.status()))
            .count();
        long outputCount = executionLogService.latest()
            .stream()
            .filter(e -> e.outputHtmlFile() != null)
            .count();

        model.addAttribute("allProcedures", allProcedures);
        model.addAttribute("selectedProcedure", effectiveSelection);
        model.addAttribute("procedures", visibleProcedures);
        model.addAttribute("logs", executionLogService.latest());
        model.addAttribute("successMessage", successMessage);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("errorCount", errorCount);
        model.addAttribute("okCount", okCount);
        model.addAttribute("outputCount", outputCount);
        model.addAttribute("jobs", scheduledExecutionService.listJobs());
        model.addAttribute("applications", applications);
        model.addAttribute("defaultApplication", defaultApplication);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        return "dashboard";
    }

    @GetMapping("/errors")
    public String errors(Model model) {
        List<com.example.cruscotto.model.ExecutionLogEntry> allErrors = executionLogService.latest()
                .stream()
                .filter(e -> "KO".equals(e.status()))
                .toList();

        model.addAttribute("allErrors", allErrors);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        return "errors";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(value = "status", required = false) String status,
                       Model model) {
        List<com.example.cruscotto.model.ExecutionLogEntry> allLogs = executionLogService.latest();
        String requestedStatus = (status == null) ? "" : status.trim().toUpperCase();
        String statusFilter;

        if ("OK".equals(requestedStatus) || "KO".equals(requestedStatus)) {
            final String lambdaStatus = requestedStatus;
            allLogs = allLogs.stream()
                    .filter(e -> lambdaStatus.equals(e.status()))
                    .toList();
            statusFilter = requestedStatus;
        } else if ("HAS_OUTPUT".equals(requestedStatus)) {
            allLogs = allLogs.stream()
                    .filter(e -> e.outputHtmlFile() != null)
                    .toList();
            statusFilter = requestedStatus;
        } else {
            statusFilter = "";
        }

        model.addAttribute("allLogs", allLogs);
        model.addAttribute("logStatusFilter", statusFilter);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        return "logs";
    }

    @PostMapping("/run/{name}")
    public String runNow(@PathVariable String name,
                         @RequestParam Map<String, String> requestParams) {
        String successMessage = null;
        String errorMessage = null;
        try {
            Map<String, Object> params = extractProcedureParams(requestParams);
            String message = executorService.runProcedure(name, params);
            successMessage = name + ": " + message;
        } catch (Exception ex) {
            errorMessage = name + ": " + ex.getMessage();
        }
        return redirectToDashboard(name, successMessage, errorMessage);
    }

    @PostMapping("/schedule/{name}")
    public String schedule(@PathVariable String name,
                           @RequestParam("cron") String cron,
                           @RequestParam Map<String, String> requestParams) {
        String successMessage = null;
        String errorMessage = null;
        try {
            Map<String, Object> params = extractProcedureParams(requestParams);
            scheduledExecutionService.schedule(name, cron, params);
            successMessage = "Schedulazione aggiornata per " + name;
        } catch (Exception ex) {
            errorMessage = "Errore schedulazione " + name + ": " + ex.getMessage();
        }
        return redirectToDashboard(name, successMessage, errorMessage);
    }

    @PostMapping("/schedule-once/{name}")
    public String scheduleOnce(@PathVariable String name,
                               @RequestParam("runAt") String runAt,
                               @RequestParam Map<String, String> requestParams) {
        String successMessage = null;
        String errorMessage = null;
        try {
            LocalDateTime runAtLocal = LocalDateTime.parse(runAt);
            Instant runAtInstant = runAtLocal.atZone(ZoneId.systemDefault()).toInstant();
            if (!runAtInstant.isAfter(Instant.now())) {
                throw new IllegalArgumentException("La data/ora deve essere futura");
            }

            Map<String, Object> params = extractProcedureParams(requestParams);
            scheduledExecutionService.scheduleOnce(name, runAtInstant, params);
            successMessage = "Lancio singolo pianificato per " + name + " alle " + runAtLocal;
        } catch (Exception ex) {
            errorMessage = "Errore lancio singolo " + name + ": " + ex.getMessage();
        }
        return redirectToDashboard(name, successMessage, errorMessage);
    }

    @PostMapping("/schedule/{name}/cancel")
    public String cancelSchedule(@PathVariable String name) {
        scheduledExecutionService.cancel(name);
        return redirectToDashboard(name, "Schedulazione rimossa per " + name, null);
    }

    @PostMapping("/catalog/reload")
    public String reloadCatalog(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        String successMessage = null;
        String errorMessage = null;
        try {
            catalogService.reloadProcedures();
            successMessage = "Catalogo SQL ricaricato da src/main/resources/sql";
        } catch (Exception ex) {
            errorMessage = "Errore durante la ricarica del catalogo SQL: " + ex.getMessage();
        }
        return redirectToDashboard(selectedProcedure, successMessage, errorMessage);
    }

    @PostMapping("/query/execute")
    public String runEditorQuery(@RequestParam("sqlText") String sqlText,
                                 @RequestParam(value = "queryLabel", required = false) String queryLabel,
                                 @RequestParam(value = "queryParams", required = false) String queryParams,
                                 @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        String successMessage = null;
        String errorMessage = null;

        try {
            Map<String, Object> params = parseEditorParams(queryParams);
            String message = executorService.runAdhocSelect(queryLabel, sqlText, params);
            String label = (queryLabel == null || queryLabel.isBlank()) ? "SQL Editor" : queryLabel.trim();
            successMessage = label + ": " + message;
        } catch (Exception ex) {
            errorMessage = "Editor SQL: " + ex.getMessage();
        }

        return redirectToDashboard(selectedProcedure, successMessage, errorMessage);
    }

    @PostMapping("/query/save")
    public String saveEditorQuery(@RequestParam("sqlText") String sqlText,
                                  @RequestParam(value = "queryFileName", required = false) String queryFileName,
                                  @RequestParam(value = "queryLabel", required = false) String queryLabel,
                                  @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        String successMessage = null;
        String errorMessage = null;
        String nextSelection = selectedProcedure;

        try {
            String requestedName = resolveQuerySaveName(queryFileName, queryLabel);
            String savedName = catalogService.saveSqlFile(requestedName, sqlText, false);
            successMessage = "Query salvata in src/main/resources/sql come '" + savedName + ".sql'";
            nextSelection = savedName;
        } catch (Exception ex) {
            errorMessage = "Salvataggio query: " + ex.getMessage();
        }

        return redirectToDashboard(nextSelection, successMessage, errorMessage);
    }

    @PostMapping("/query/update")
    public String updateSelectedQuery(@RequestParam("sqlText") String sqlText,
                                      @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        String successMessage = null;
        String errorMessage = null;

        try {
            if (selectedProcedure == null || selectedProcedure.isBlank()) {
                throw new IllegalArgumentException("Seleziona lo script da aggiornare");
            }
            String updatedName = catalogService.updateSqlFile(selectedProcedure, sqlText);
            successMessage = "Script aggiornato: '" + updatedName + ".sql'";
        } catch (Exception ex) {
            errorMessage = "Aggiornamento script: " + ex.getMessage();
        }

        return redirectToDashboard(selectedProcedure, successMessage, errorMessage);
    }

    @GetMapping({"/run/{name}", "/schedule/{name}", "/schedule-once/{name}", "/schedule/{name}/cancel"})
    public String rejectGetOnAction(@PathVariable String name) {
        return redirectToDashboard(name, null, "Operazione non valida via GET. Usa i pulsanti del cruscotto.");
    }

    @GetMapping("/query/execute")
    public String rejectGetQueryExecute(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        return redirectToDashboard(selectedProcedure, null, "Editor SQL disponibile solo via POST.");
    }

    @GetMapping("/query/save")
    public String rejectGetQuerySave(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        return redirectToDashboard(selectedProcedure, null, "Salvataggio query disponibile solo via POST.");
    }

    @GetMapping("/query/update")
    public String rejectGetQueryUpdate(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        return redirectToDashboard(selectedProcedure, null, "Aggiornamento query disponibile solo via POST.");
    }

    @GetMapping("/catalog/reload")
    public String rejectGetReload(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure) {
        return redirectToDashboard(selectedProcedure, null, "Ricarica catalogo disponibile solo via POST.");
    }

    /**
     * Serve il file HTML di output generato da una query SELECT.
     * Protezione path-traversal: il file deve risiedere nella cartella di output configurata.
     */
    @GetMapping("/output/{filename:.+}")
    @ResponseBody
    public ResponseEntity<String> viewOutput(@PathVariable String filename) throws IOException {
        Path outputDir = queryOutputHtmlService.getOutputDir();
        Path file = outputDir.resolve(filename).normalize();
        if (!file.startsWith(outputDir)) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(content);
    }

    @GetMapping("/output/download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadOutput(@PathVariable String filename) throws IOException {
        Path outputDir = queryOutputHtmlService.getOutputDir();
        Path file = outputDir.resolve(filename).normalize();
        if (!file.startsWith(outputDir)) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        byte[] bytes = Files.readAllBytes(file);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        MediaType mediaType = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    private String resolveSelection(String selectedProcedure,
                                    List<com.example.cruscotto.model.ProcedureDefinition> allProcedures) {
        if (allProcedures.isEmpty()) {
            return null;
        }

        if (selectedProcedure == null || selectedProcedure.isBlank()) {
            return allProcedures.get(0).name();
        }

        boolean exists = allProcedures.stream().anyMatch(p -> p.name().equals(selectedProcedure));
        return exists ? selectedProcedure : allProcedures.get(0).name();
    }

    private Map<String, Object> extractProcedureParams(Map<String, String> requestParams) {
        Map<String, Object> params = new LinkedHashMap<>();

        String selectedApplication = requestParams.get("appCode");
        if (selectedApplication != null && !selectedApplication.isBlank()) {
            String appCode = selectedApplication.trim();
            params.put("applicazione", appCode);
            params.put("application", appCode);
            params.put("appCode", appCode);
        }

        requestParams.forEach((key, value) -> {
            if (key.startsWith("p_") && value != null && !value.isBlank()) {
                params.put(key.substring(2), value.trim());
            }
        });
        return params;
    }

    private String resolvePid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        if (separator > 0) {
            return runtimeName.substring(0, separator);
        }
        return runtimeName;
    }

    private Map<String, Object> parseEditorParams(String queryParams) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (queryParams == null || queryParams.isBlank()) {
            return params;
        }

        String[] lines = queryParams.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Parametro non valido: '" + trimmed + "'. Usa formato nome=valore");
            }

            String name = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Nome parametro mancante nella riga: '" + trimmed + "'");
            }
            params.put(name, value);
        }
        return params;
    }

    private String resolveQuerySaveName(String queryFileName, String queryLabel) {
        if (queryFileName != null && !queryFileName.isBlank()) {
            return queryFileName.trim();
        }
        if (queryLabel != null && !queryLabel.isBlank()) {
            return queryLabel.trim();
        }
        throw new IllegalArgumentException("Specifica un nome file per il salvataggio");
    }

    private String redirectToDashboard(String selectedProcedure, String successMessage, String errorMessage) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/");
        if (selectedProcedure != null && !selectedProcedure.isBlank()) {
            builder.queryParam("selectedProcedure", selectedProcedure);
        }
        if (successMessage != null && !successMessage.isBlank()) {
            builder.queryParam("successMessage", successMessage);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            builder.queryParam("errorMessage", errorMessage);
        }
        return "redirect:" + builder.build().encode().toUriString();
    }

    // ── Editor SQL dedicato ──────────────────────────────────────────────────

    @GetMapping("/editor")
    public String editorPage(@RequestParam(value = "script", required = false) String scriptName,
                             @RequestParam(value = "msg", required = false) String msg,
                             @RequestParam(value = "error", required = false) String error,
                             Model model) {
        List<com.example.cruscotto.model.ProcedureDefinition> allProcedures = catalogService.findAll();
        String effectiveSelection = null;
        if (scriptName != null && !scriptName.isBlank()) {
            boolean found = allProcedures.stream().anyMatch(p -> p.name().equals(scriptName));
            effectiveSelection = found ? scriptName : null;
        }

        String sqlContent = "";
        if (effectiveSelection != null) {
            String sel = effectiveSelection;
            sqlContent = allProcedures.stream()
                    .filter(p -> p.name().equals(sel))
                    .findFirst()
                    .map(com.example.cruscotto.model.ProcedureDefinition::sqlText)
                    .orElse("");
        }

        model.addAttribute("allProcedures", allProcedures);
        model.addAttribute("selectedScript", effectiveSelection);
        model.addAttribute("sqlContent", sqlContent);
        model.addAttribute("successMessage", msg);
        model.addAttribute("errorMessage", error);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        return "editor";
    }

    @GetMapping(value = "/editor/load-script", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> editorLoadScript(
            @RequestParam("script") String scriptName) {
        Map<String, Object> result = new LinkedHashMap<>();
        catalogService.findByName(scriptName).ifPresentOrElse(
                proc -> {
                    result.put("ok", true);
                    result.put("sqlText", proc.sqlText());
                },
                () -> {
                    result.put("ok", false);
                    result.put("message", "Script non trovato: " + scriptName);
                }
        );
        return result;
    }

    @PostMapping(value = "/editor/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> editorExecute(
            @RequestParam("sqlText") String sqlText,
            @RequestParam(value = "queryLabel", required = false) String queryLabel,
            @RequestParam(value = "queryParams", required = false) String queryParams) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> params = parseEditorParams(queryParams);
            String message = executorService.runAdhocSelect(queryLabel, sqlText, params);
            result.put("ok", true);
            result.put("message", message);
            List<com.example.cruscotto.model.ExecutionLogEntry> logs = executionLogService.latest();
            if (!logs.isEmpty() && logs.get(0).outputHtmlFile() != null) {
                result.put("outputUrl", "/output/" + logs.get(0).outputHtmlFile());
            }
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    @PostMapping("/editor/save")
    public String editorSave(
            @RequestParam("sqlText") String sqlText,
            @RequestParam(value = "queryFileName", required = false) String queryFileName,
            @RequestParam(value = "queryLabel", required = false) String queryLabel) {
        try {
            String requestedName = resolveQuerySaveName(queryFileName, queryLabel);
            String savedName = catalogService.saveSqlFile(requestedName, sqlText, false);
            return redirectToEditor(savedName, "Script salvato: '" + savedName + ".sql'", null);
        } catch (Exception ex) {
            return redirectToEditor(null, null, "Salvataggio: " + ex.getMessage());
        }
    }

    @PostMapping("/editor/update")
    public String editorUpdate(
            @RequestParam("sqlText") String sqlText,
            @RequestParam(value = "selectedScript", required = false) String selectedScript) {
        try {
            if (selectedScript == null || selectedScript.isBlank()) {
                throw new IllegalArgumentException("Seleziona uno script da aggiornare");
            }
            String updatedName = catalogService.updateSqlFile(selectedScript, sqlText);
            return redirectToEditor(updatedName, "Script aggiornato: '" + updatedName + ".sql'", null);
        } catch (Exception ex) {
            return redirectToEditor(selectedScript, null, "Aggiornamento: " + ex.getMessage());
        }
    }

    private String redirectToEditor(String script, String msg, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/editor");
        if (script != null && !script.isBlank()) {
            builder.queryParam("script", script);
        }
        if (msg != null && !msg.isBlank()) {
            builder.queryParam("msg", msg);
        }
        if (error != null && !error.isBlank()) {
            builder.queryParam("error", error);
        }
        return "redirect:" + builder.build().encode().toUriString();
    }
}
