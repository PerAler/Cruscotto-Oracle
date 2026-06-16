package com.example.cruscotto.controller;

import com.example.cruscotto.model.OracleConnectionInfo;
import com.example.cruscotto.service.ExecutionLogService;
import com.example.cruscotto.service.OracleConnectionManager;
import com.example.cruscotto.service.OracleProcedureExecutorService;
import com.example.cruscotto.service.OracleSchemaService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class DashboardController {

    private record ErrorViewModel(com.example.cruscotto.model.ExecutionLogEntry error,
                                  String oracleCauseSection) {
    }

    private static final String APP_VERSION = "1.4.0";

    private final SqlProcedureCatalogService catalogService;
    private final OracleProcedureExecutorService executorService;
    private final OracleSchemaService oracleSchemaService;
    private final ScheduledExecutionService scheduledExecutionService;
    private final ExecutionLogService executionLogService;
    private final QueryOutputHtmlService queryOutputHtmlService;
    private final OracleConnectionManager connectionManager;
    private final List<String> applications;
    private final String defaultApplication;
    private final String runtimePid;
    private final Instant runtimeStartedAt;

    public DashboardController(SqlProcedureCatalogService catalogService,
                               OracleProcedureExecutorService executorService,
                               OracleSchemaService oracleSchemaService,
                               ScheduledExecutionService scheduledExecutionService,
                               ExecutionLogService executionLogService,
                               QueryOutputHtmlService queryOutputHtmlService,
                               OracleConnectionManager connectionManager,
                               @Value("${app.target-applications:ALER}") String configuredApplications,
                               @Value("${app.default-application:ALER}") String defaultApplication) {
        this.catalogService = catalogService;
        this.executorService = executorService;
        this.oracleSchemaService = oracleSchemaService;
        this.scheduledExecutionService = scheduledExecutionService;
        this.executionLogService = executionLogService;
        this.queryOutputHtmlService = queryOutputHtmlService;
        this.connectionManager = connectionManager;
        this.applications = Arrays.stream(configuredApplications.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        this.defaultApplication = defaultApplication;
        this.runtimePid = resolvePid();
        this.runtimeStartedAt = Instant.now();
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping({"/dashboard", "/dashboard/"})
    public String dashboard() {
        return "redirect:/editor";
    }

    @GetMapping({"/utility", "/utility/"})
    public String index(@RequestParam(value = "selectedProcedure", required = false) String selectedProcedure,
                        @RequestParam(value = "successMessage", required = false) String successMessage,
                        @RequestParam(value = "errorMessage", required = false) String errorMessage,
                        @RequestParam(value = "outputFile", required = false) String outputFile,
                        Model model) {
        String activeConnectionId = connectionManager.getActiveConnectionId().orElse(null);
        String activeConnectionLabel = resolveConnectionLabel(activeConnectionId);
        List<com.example.cruscotto.model.ProcedureDefinition> allProcedures = catalogService.findAll(activeConnectionLabel);
        String effectiveSelection = resolveSelection(selectedProcedure, allProcedures);

        List<com.example.cruscotto.model.ProcedureDefinition> visibleProcedures = allProcedures;
        if (effectiveSelection != null && !effectiveSelection.isBlank()) {
            visibleProcedures = allProcedures.stream()
                    .filter(p -> p.name().equals(effectiveSelection))
                    .toList();
        }

        boolean dbConnected = isDatabaseConnected();
        List<com.example.cruscotto.model.ExecutionLogEntry> latestLogs = executionLogService.latest();
        String robotState = resolveRobotState(dbConnected, errorMessage, latestLogs);

        // Conta errori totali
        long errorCount = latestLogs
                .stream()
                .filter(e -> "KO".equals(e.status()))
                .count();
        long okCount = latestLogs
            .stream()
            .filter(e -> "OK".equals(e.status()))
            .count();
        long outputCount = latestLogs
            .stream()
            .filter(e -> e.outputHtmlFile() != null)
            .count();

        model.addAttribute("allProcedures", allProcedures);
        model.addAttribute("selectedProcedure", effectiveSelection);
        model.addAttribute("procedures", visibleProcedures);
        model.addAttribute("logs", latestLogs);
        model.addAttribute("successMessage", successMessage);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("outputFile", outputFile);
        model.addAttribute("errorCount", errorCount);
        model.addAttribute("okCount", okCount);
        model.addAttribute("outputCount", outputCount);
        model.addAttribute("dbConnected", dbConnected);
        model.addAttribute("robotState", robotState);
        model.addAttribute("jobs", scheduledExecutionService.listJobs());
        model.addAttribute("applications", applications);
        model.addAttribute("defaultApplication", defaultApplication);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        model.addAttribute("activeConnectionId", activeConnectionId == null ? "" : activeConnectionId);
        model.addAttribute("activeConnectionLabel", activeConnectionLabel == null ? "" : activeConnectionLabel);
        model.addAttribute("appVersion", APP_VERSION);
        return "utility";
    }

    @GetMapping("/errors")
    public String errors(@RequestParam(value = "successMessage", required = false) String successMessage,
                         @RequestParam(value = "errorMessage", required = false) String errorMessage,
                         Model model) {
        List<com.example.cruscotto.model.ExecutionLogEntry> allErrors = executionLogService.latest()
                .stream()
                .filter(e -> "KO".equals(e.status()))
                .toList();
        List<ErrorViewModel> errorViews = allErrors.stream()
            .map(e -> new ErrorViewModel(e, extractCausedByErrorSection(e.stackTrace())))
            .toList();

        model.addAttribute("allErrors", allErrors);
        model.addAttribute("errorViews", errorViews);
        model.addAttribute("successMessage", successMessage);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        return "errors";
    }

    @PostMapping("/errors/delete")
    public String deleteSingleError(@RequestParam("errorIndex") int errorIndex) {
        boolean removed = executionLogService.deleteErrorAtIndex(errorIndex);
        if (removed) {
            return redirectToErrors("Errore eliminato dalla lista", null);
        }
        return redirectToErrors(null, "Impossibile eliminare l'errore selezionato");
    }

    @PostMapping("/errors/delete-all")
    public String deleteAllErrors() {
        int removed = executionLogService.deleteAllErrors();
        if (removed > 0) {
            return redirectToErrors("Tutti gli errori sono stati eliminati", null);
        }
        return redirectToErrors(null, "Nessun errore da eliminare");
    }

    @GetMapping("/errors/delete")
    public String rejectGetDeleteError() {
        return redirectToErrors(null, "Eliminazione errore disponibile solo via POST.");
    }

    @GetMapping("/errors/delete-all")
    public String rejectGetDeleteAllErrors() {
        return redirectToErrors(null, "Eliminazione totale errori disponibile solo via POST.");
    }

    private boolean isDatabaseConnected() {
        return connectionManager.isActiveConnectionReachable();
    }

    private String resolveRobotState(boolean dbConnected,
                                     String errorMessage,
                                     List<com.example.cruscotto.model.ExecutionLogEntry> latestLogs) {
        if (!dbConnected) {
            return "db-offline";
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            return "script-error";
        }
        boolean latestExecutionFailed = !latestLogs.isEmpty() && "KO".equals(latestLogs.get(0).status());
        if (latestExecutionFailed) {
            return "script-error";
        }
        return "ok";
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
            String activeConnectionId = connectionManager.getActiveConnectionId().orElse(null);
            Map<String, Object> params = extractProcedureParams(requestParams);
            String message = executorService.runProcedure(activeConnectionId, name, params);
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
    public String reloadCatalog(
            @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        String successMessage = null;
        String errorMessage = null;
        String effectiveConnectionId = (connectionId == null || connectionId.isBlank())
                ? connectionManager.getActiveConnectionId().orElse(null)
                : connectionId.trim();
        try {
            String activeConnectionLabel = resolveConnectionLabel(effectiveConnectionId);
            catalogService.reloadProcedures(activeConnectionLabel);
            successMessage = "Catalogo SQL ricaricato";
        } catch (Exception ex) {
            errorMessage = "Errore durante la ricarica del catalogo SQL: " + ex.getMessage();
        }
        if (selectedProcedure == null || selectedProcedure.isBlank()) {
            return redirectToEditor(null, successMessage, errorMessage, effectiveConnectionId);
        }
        return redirectToDashboard(selectedProcedure, successMessage, errorMessage);
    }

    @PostMapping("/query/execute")
    public String runEditorQuery(@RequestParam("sqlText") String sqlText,
                                 @RequestParam(value = "queryLabel", required = false) String queryLabel,
                                 @RequestParam(value = "queryParams", required = false) String queryParams,
                                 @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure,
                                 @RequestParam(value = "connectionId", required = false) String connectionId) {
        String successMessage = null;
        String errorMessage = null;
        String outputFile = null;

        try {
            String activeConnectionId = (connectionId == null || connectionId.isBlank())
                    ? connectionManager.getActiveConnectionId().orElse(null)
                    : connectionId.trim();
            Map<String, Object> params = parseEditorParams(queryParams);
            String message = executorService.runAdhocSelect(activeConnectionId, queryLabel, sqlText, params);
            String label = (queryLabel == null || queryLabel.isBlank()) ? "SQL Editor" : queryLabel.trim();
            successMessage = label + ": " + message;
            
            // Recupera l'ultima entry di log per estrarre il nome del file output
            List<com.example.cruscotto.model.ExecutionLogEntry> latest = executionLogService.latest();
            if (!latest.isEmpty()) {
                String outputReference = latest.get(0).outputHtmlFile();
                if (outputReference != null && !outputReference.isBlank()) {
                    outputFile = queryOutputHtmlService.toHtmlFilename(outputReference);
                }
            }
        } catch (Exception ex) {
            errorMessage = "Editor SQL: " + ex.getMessage();
        }

        return redirectToDashboardWithOutput(selectedProcedure, successMessage, errorMessage, outputFile);
    }

    @PostMapping("/query/save")
    public String saveEditorQuery(@RequestParam("sqlText") String sqlText,
                                  @RequestParam(value = "queryFileName", required = false) String queryFileName,
                                  @RequestParam(value = "queryLabel", required = false) String queryLabel,
                                  @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure,
                                  @RequestParam(value = "connectionId", required = false) String connectionId) {
        String successMessage = null;
        String errorMessage = null;
        String nextSelection = selectedProcedure;

        try {
            String activeConnectionLabel = resolveConnectionLabel(connectionId);
            String requestedName = resolveQuerySaveName(queryFileName, queryLabel);
            String savedName = catalogService.saveSqlFile(activeConnectionLabel, requestedName, sqlText, false);
            successMessage = "Query salvata come '" + savedName + ".sql'";
            nextSelection = savedName;
        } catch (Exception ex) {
            errorMessage = "Salvataggio query: " + ex.getMessage();
        }

        return redirectToDashboard(nextSelection, successMessage, errorMessage);
    }

    @PostMapping("/query/update")
    public String updateSelectedQuery(@RequestParam("sqlText") String sqlText,
                                      @RequestParam(value = "selectedProcedure", required = false) String selectedProcedure,
                                      @RequestParam(value = "connectionId", required = false) String connectionId) {
        String successMessage = null;
        String errorMessage = null;

        try {
            String activeConnectionLabel = resolveConnectionLabel(connectionId);
            if (selectedProcedure == null || selectedProcedure.isBlank()) {
                throw new IllegalArgumentException("Seleziona lo script da aggiornare");
            }
            String updatedName = catalogService.updateSqlFile(activeConnectionLabel, selectedProcedure, sqlText);
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
    public ResponseEntity<String> viewOutput(@PathVariable String filename,
                                             @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                             @RequestParam(value = "size", required = false, defaultValue = "200") int size) throws IOException {
        Path outputDir = queryOutputHtmlService.getOutputDir();
        Path file = outputDir.resolve(filename).normalize();
        if (!file.startsWith(outputDir)) {
            return ResponseEntity.badRequest().build();
        }

        if (filename.endsWith(".html")) {
            String htmlFromCsv = queryOutputHtmlService.buildHtmlFromCsv(filename, page, size);
            if (htmlFromCsv != null) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlFromCsv);
            }
        }

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        MediaType contentType = filename.endsWith(".csv")
                ? MediaType.parseMediaType("text/csv")
                : MediaType.TEXT_HTML;
        return ResponseEntity.ok().contentType(contentType).body(content);
    }

    @GetMapping("/output/download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadOutput(@PathVariable String filename) throws IOException {
        Path outputDir = queryOutputHtmlService.getOutputDir();
        Path file = outputDir.resolve(filename).normalize();
        if (!file.startsWith(outputDir)) {
            return ResponseEntity.badRequest().build();
        }

        if (filename.endsWith(".xlsx") && !Files.exists(file)) {
            byte[] generated = queryOutputHtmlService.buildXlsxFromCsv(filename);
            if (generated == null) {
                return ResponseEntity.notFound().build();
            }
            ByteArrayResource resource = new ByteArrayResource(generated);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    .body(resource);
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

    private String resolveConnectionLabel(String connectionId) {
        if (connectionId != null && !connectionId.isBlank()) {
            Optional<OracleConnectionInfo> info = connectionManager.findConnectionInfo(connectionId);
            if (info.isPresent()) {
                return info.get().label();
            }
        }
        return connectionManager.getActiveConnectionInfo()
                .map(OracleConnectionInfo::label)
                .orElse(null);
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

    private String extractCausedByErrorSection(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return "";
        }

        List<String> extracted = new ArrayList<>();
        String[] lines = stackTrace.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("caused by:") && trimmed.toLowerCase().contains("error")) {
                extracted.add(trimmed);
            }
        }
        return String.join(System.lineSeparator(), extracted);
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

    private String redirectToDashboardWithOutput(String selectedProcedure, String successMessage, String errorMessage, String outputFile) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/utility");
        if (selectedProcedure != null && !selectedProcedure.isBlank()) {
            builder.queryParam("selectedProcedure", selectedProcedure);
        }
        if (successMessage != null && !successMessage.isBlank()) {
            builder.queryParam("successMessage", successMessage);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            builder.queryParam("errorMessage", errorMessage);
        }
        if (outputFile != null && !outputFile.isBlank()) {
            builder.queryParam("outputFile", outputFile);
        }
        return "redirect:" + builder.toUriString();
    }

    private String redirectToDashboard(String selectedProcedure, String successMessage, String errorMessage) {
        return redirectToDashboardWithOutput(selectedProcedure, successMessage, errorMessage, null);
    }

    private String redirectToErrors(String successMessage, String errorMessage) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/errors");
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
                             @RequestParam(value = "connectionId", required = false) String connectionId,
                             @RequestParam(value = "msg", required = false) String msg,
                             @RequestParam(value = "error", required = false) String error,
                             Model model) {
        String requestedConnectionId = (connectionId == null || connectionId.isBlank())
                ? connectionManager.getActiveConnectionId().orElse(null)
                : connectionId.trim();
        if (requestedConnectionId != null && !requestedConnectionId.isBlank()) {
            try {
                connectionManager.activateConnection(requestedConnectionId);
            } catch (Exception ignored) {
                requestedConnectionId = connectionManager.getActiveConnectionId().orElse(null);
            }
        }
        String activeConnectionLabel = resolveConnectionLabel(requestedConnectionId);
        List<com.example.cruscotto.model.ProcedureDefinition> allProcedures = catalogService.findAll(activeConnectionLabel);
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

        String activeConnectionId = connectionManager.getActiveConnectionId().orElse(null);
        boolean dbConnected = activeConnectionId != null && connectionManager.isConnectionReachable(activeConnectionId);
        List<com.example.cruscotto.model.ExecutionLogEntry> latestLogs = executionLogService.latest();
        long errorCount = latestLogs.stream().filter(e -> "KO".equals(e.status())).count();
        long okCount = latestLogs.stream().filter(e -> "OK".equals(e.status())).count();
        long outputCount = latestLogs.stream().filter(e -> e.outputHtmlFile() != null).count();
        String robotState = resolveRobotState(dbConnected, error, latestLogs);
        List<com.example.cruscotto.model.SchemaGroupSummary> schemaGroups = activeConnectionId == null
                ? List.of()
                : oracleSchemaService.getSchemaGroupSummaries(activeConnectionId);

        model.addAttribute("allProcedures", allProcedures);
        model.addAttribute("selectedScript", effectiveSelection);
        model.addAttribute("sqlContent", sqlContent);
        model.addAttribute("schemaGroups", schemaGroups);
        model.addAttribute("activeConnectionId", activeConnectionId == null ? "" : activeConnectionId);
        model.addAttribute("activeConnectionLabel", activeConnectionLabel == null ? "" : activeConnectionLabel);
        model.addAttribute("successMessage", msg);
        model.addAttribute("errorMessage", error);
        model.addAttribute("robotState", robotState);
        model.addAttribute("logs", latestLogs);
        model.addAttribute("errorCount", errorCount);
        model.addAttribute("okCount", okCount);
        model.addAttribute("outputCount", outputCount);
        model.addAttribute("runtimePid", runtimePid);
        model.addAttribute("runtimeStartedAt", runtimeStartedAt);
        model.addAttribute("appVersion", APP_VERSION);
        return "editor";
    }

    @GetMapping(value = "/editor/load-script", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> editorLoadScript(
            @RequestParam("script") String scriptName,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String activeConnectionLabel = resolveConnectionLabel(connectionId);
        catalogService.findByName(activeConnectionLabel, scriptName).ifPresentOrElse(
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

    @GetMapping(value = "/api/schema/object-source", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> editorLoadSchemaObjectSource(
            @RequestParam(value = "connectionId", required = false) String connectionId,
            @RequestParam("objectType") String objectType,
            @RequestParam("objectName") String objectName) {
        Map<String, Object> result = new LinkedHashMap<>();
        oracleSchemaService.getSchemaObjectSource(connectionId, objectType, objectName).ifPresentOrElse(
                sqlText -> {
                    result.put("ok", true);
                    result.put("objectType", objectType);
                    result.put("objectName", objectName);
                    result.put("sqlText", sqlText);
                },
                () -> {
                    result.put("ok", false);
                    result.put("message", "Sorgente non disponibile per " + objectType + " " + objectName);
                }
        );
        return result;
    }

    @PostMapping(value = "/editor/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> editorExecute(
            @RequestParam("sqlText") String sqlText,
            @RequestParam(value = "queryLabel", required = false) String queryLabel,
            @RequestParam(value = "queryParams", required = false) String queryParams,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> params = parseEditorParams(queryParams);
            String message = executorService.runAdhocSelect(connectionId, queryLabel, sqlText, params);
            result.put("ok", true);
            result.put("message", message);
            List<com.example.cruscotto.model.ExecutionLogEntry> logs = executionLogService.latest();
            if (!logs.isEmpty() && logs.get(0).outputHtmlFile() != null) {
                result.put("outputUrl", "/output/" + queryOutputHtmlService.toHtmlFilename(logs.get(0).outputHtmlFile()));
            }
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    @PostMapping(value = "/editor/compile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> editorCompile(
            @RequestParam("sqlText") String sqlText,
            @RequestParam(value = "queryLabel", required = false) String queryLabel,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String message = executorService.runAdhocDdl(connectionId, queryLabel, sqlText);
            result.put("ok", true);
            result.put("message", message);
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
            @RequestParam(value = "queryLabel", required = false) String queryLabel,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        try {
            String activeConnectionLabel = resolveConnectionLabel(connectionId);
            String requestedName = resolveQuerySaveName(queryFileName, queryLabel);
            String savedName = catalogService.saveSqlFile(activeConnectionLabel, requestedName, sqlText, false);
            return redirectToEditor(savedName, "Script salvato: '" + savedName + ".sql'", null, connectionId);
        } catch (Exception ex) {
            return redirectToEditor(null, null, "Salvataggio: " + ex.getMessage(), connectionId);
        }
    }

    @PostMapping("/editor/update")
    public String editorUpdate(
            @RequestParam("sqlText") String sqlText,
            @RequestParam(value = "selectedScript", required = false) String selectedScript,
            @RequestParam(value = "connectionId", required = false) String connectionId) {
        try {
            String activeConnectionLabel = resolveConnectionLabel(connectionId);
            if (selectedScript == null || selectedScript.isBlank()) {
                throw new IllegalArgumentException("Seleziona uno script da aggiornare");
            }
            String updatedName = catalogService.updateSqlFile(activeConnectionLabel, selectedScript, sqlText);
            return redirectToEditor(updatedName, "Script aggiornato: '" + updatedName + ".sql'", null, connectionId);
        } catch (Exception ex) {
            return redirectToEditor(selectedScript, null, "Aggiornamento: " + ex.getMessage(), connectionId);
        }
    }

    private String redirectToEditor(String script, String msg, String error, String connectionId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/editor");
        if (connectionId != null && !connectionId.isBlank()) {
            builder.queryParam("connectionId", connectionId);
        }
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

    @GetMapping(value = "/api/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getSchemaObjects(@RequestParam(value = "connectionId", required = false) String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String effectiveConnectionId = (connectionId == null || connectionId.isBlank()) ? null : connectionId.trim();
            result.put("ok", true);
            result.put("schemaGroups", oracleSchemaService.getSchemaGroupSummaries(effectiveConnectionId));
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("error", ex.getMessage());
        }
        return result;
    }

    @GetMapping(value = "/api/schema/group", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getSchemaGroup(@RequestParam("group") String group,
                                              @RequestParam(value = "connectionId", required = false) String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("ok", true);
            result.put("group", group);
            result.put("items", oracleSchemaService.getSchemaObjectsForGroup(connectionId, group));
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("error", ex.getMessage());
        }
        return result;
    }

    @GetMapping(value = "/api/connections", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> listConnections() {
        Map<String, Object> result = new LinkedHashMap<>();
        String activeConnectionId = connectionManager.getActiveConnectionId().orElse("");
        String activeConnectionLabel = resolveConnectionLabel(activeConnectionId);
        result.put("ok", true);
        result.put("activeConnectionId", activeConnectionId);
        result.put("activeConnectionLabel", activeConnectionLabel);
        result.put("availableScripts", catalogService.findAll(activeConnectionLabel).stream()
                .map(com.example.cruscotto.model.ProcedureDefinition::name)
                .toList());
        result.put("connections", connectionManager.listConnections().stream().map(view -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", view.info().id());
            row.put("label", view.info().label());
            row.put("connectionTarget", view.info().connectionTarget());
            row.put("jdbcUrl", view.info().jdbcUrl());
            row.put("username", view.info().username());
            row.put("schema", view.info().schema());
            row.put("reachable", view.reachable());
            return row;
        }).toList());
        result.put("savedProfiles", connectionManager.listSavedProfiles());
        return result;
    }

    @PostMapping(value = "/api/connections/open", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> openConnection(@RequestParam("label") String label,
                                              @RequestParam("connectionTarget") String connectionTarget,
                                              @RequestParam("username") String username,
                                              @RequestParam("password") String password,
                                              @RequestParam(value = "schema", required = false) String schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            connectionManager.openConnection(label, connectionTarget, username, password, schema);
            result.put("ok", true);
            result.put("message", "Connessione aperta");
            result.put("activeConnectionId", connectionManager.getActiveConnectionId().orElse(""));
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    @PostMapping(value = "/api/connections/activate", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> activateConnection(@RequestParam("connectionId") String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            connectionManager.activateConnection(connectionId);
            result.put("ok", true);
            result.put("activeConnectionId", connectionManager.getActiveConnectionId().orElse(""));
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    @PostMapping(value = "/api/connections/close", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> closeConnection(@RequestParam("connectionId") String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            connectionManager.closeConnection(connectionId);
            result.put("ok", true);
            result.put("activeConnectionId", connectionManager.getActiveConnectionId().orElse(""));
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    @GetMapping("/readme")
    public String readmePage() {
        return "redirect:/api/readme";
    }

    @GetMapping(value = "/api/readme", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @ResponseBody
    public String getReadme() {
        try {
            Path readmePath = Path.of("README.md");
            if (Files.exists(readmePath)) {
                return Files.readString(readmePath);
            }
        } catch (Exception ex) {
            // fallback
        }
        return "# README non trovato";
    }
}
