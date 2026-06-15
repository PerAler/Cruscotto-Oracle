package com.example.cruscotto.service;

import com.example.cruscotto.model.ExecutionLogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class ExecutionLogFileStore {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLogFileStore.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter ISO_TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern BUNDLE_NAME_PATTERN = Pattern.compile("(.+)_\\d{8}_\\d{6}(?:_\\d{3})?$");

    private final Path outputDir;
    private final ObjectMapper objectMapper;

    public ExecutionLogFileStore(@Value("${app.output.folder:output}") String outputFolder,
                                 ObjectMapper objectMapper) {
        this.outputDir = Paths.get(outputFolder).toAbsolutePath();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(outputDir);
    }

    public void persist(ExecutionLogEntry entry) {
        String baseName = buildBaseName(entry);
        Path logFile = outputDir.resolve(baseName + ".log.json");
        String dbmsOutputFile = null;

        try {
            if (entry.dbmsOutput() != null && !entry.dbmsOutput().isBlank()) {
                dbmsOutputFile = baseName + ".dbms_output.txt";
                Files.writeString(outputDir.resolve(dbmsOutputFile), entry.dbmsOutput(), StandardCharsets.UTF_8);
            }

            PersistedExecutionLog persisted = new PersistedExecutionLog(
                    entry.timestamp().format(ISO_TS),
                    entry.procedureName(),
                    entry.status(),
                    entry.durationMs(),
                    entry.message(),
                    entry.parameters(),
                    entry.outputHtmlFile(),
                    entry.stackTrace(),
                    dbmsOutputFile
            );
            objectMapper.writeValue(logFile.toFile(), persisted);
        } catch (Exception ex) {
            log.warn("Persistenza file log non riuscita: {}", ex.getMessage());
        }
    }

    public List<ExecutionLogEntry> loadLatest(int maxRows) {
        if (maxRows <= 0) {
            return List.of();
        }

        List<StoredLogFile> persistedLogs = readPersistedLogs();
        List<ExecutionLogEntry> loaded = persistedLogs.stream()
                .map(StoredLogFile::entry)
                .toList();

        Set<String> coveredOutputFiles = new HashSet<>();
        for (ExecutionLogEntry entry : loaded) {
            if (entry.outputHtmlFile() != null && !entry.outputHtmlFile().isBlank()) {
                coveredOutputFiles.add(entry.outputHtmlFile());
                coveredOutputFiles.add(toCsvFilename(entry.outputHtmlFile()));
            }
        }

        List<ExecutionLogEntry> inferredFromOutput = inferOutputLogs(coveredOutputFiles);
        List<ExecutionLogEntry> merged = new ArrayList<>(loaded.size() + inferredFromOutput.size());
        merged.addAll(loaded);
        merged.addAll(inferredFromOutput);
        merged.sort(Comparator.comparing(ExecutionLogEntry::timestamp).reversed());

        if (merged.size() > maxRows) {
            return new ArrayList<>(merged.subList(0, maxRows));
        }
        return merged;
    }

    public boolean deleteOne(ExecutionLogEntry entry) {
        for (StoredLogFile stored : readPersistedLogs()) {
            if (!sameLog(entry, stored.entry())) {
                continue;
            }
            deletePersistedFiles(stored);
            return true;
        }
        return false;
    }

    public int deleteAllErrors() {
        int removed = 0;
        for (StoredLogFile stored : readPersistedLogs()) {
            if ("KO".equals(stored.entry().status())) {
                deletePersistedFiles(stored);
                removed++;
            }
        }
        return removed;
    }

    public int deleteErrorsForProcedure(String procedureName) {
        if (procedureName == null || procedureName.isBlank()) {
            return 0;
        }
        int removed = 0;
        for (StoredLogFile stored : readPersistedLogs()) {
            ExecutionLogEntry entry = stored.entry();
            if ("KO".equals(entry.status()) && procedureName.equals(entry.procedureName())) {
                deletePersistedFiles(stored);
                removed++;
            }
        }
        return removed;
    }

    public int trimToMaxRows(int maxRows) {
        if (maxRows <= 0) {
            return 0;
        }
        List<StoredLogFile> persisted = readPersistedLogs();
        if (persisted.size() <= maxRows) {
            return 0;
        }

        int removed = 0;
        for (int i = maxRows; i < persisted.size(); i++) {
            deletePersistedFiles(persisted.get(i));
            removed++;
        }
        return removed;
    }

    private List<StoredLogFile> readPersistedLogs() {
        List<Path> files;
        try (Stream<Path> stream = Files.list(outputDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".log.json"))
                    .toList();
        } catch (Exception ex) {
            log.warn("Lettura file log non riuscita: {}", ex.getMessage());
            return List.of();
        }

        List<StoredLogFile> logs = new ArrayList<>();
        for (Path file : files) {
            try {
                PersistedExecutionLog persisted = objectMapper.readValue(file.toFile(), PersistedExecutionLog.class);
                LocalDateTime timestamp = parseTimestamp(persisted.timestamp(), file);
                String dbmsOutput = readDbmsOutput(persisted.dbmsOutputFile());
                ExecutionLogEntry entry = new ExecutionLogEntry(
                        timestamp,
                        safeString(persisted.procedureName()),
                        safeString(persisted.status()),
                        persisted.durationMs(),
                        persisted.message(),
                        persisted.parameters() == null ? Map.of() : persisted.parameters(),
                        persisted.outputHtmlFile(),
                        persisted.stackTrace(),
                        dbmsOutput
                );
                logs.add(new StoredLogFile(file, persisted.dbmsOutputFile(), entry));
            } catch (Exception ex) {
                log.warn("File log non valido {}: {}", file.getFileName(), ex.getMessage());
            }
        }

        logs.sort(Comparator.comparing((StoredLogFile f) -> f.entry().timestamp()).reversed());
        return logs;
    }

    private List<ExecutionLogEntry> inferOutputLogs(Set<String> coveredOutputFiles) {
        Map<String, OutputBundle> bundleByBase = new HashMap<>();
        try (Stream<Path> stream = Files.list(outputDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String baseName = baseName(filename);
                        if (baseName == null) {
                            return;
                        }
                        OutputBundle bundle = bundleByBase.computeIfAbsent(baseName, k -> new OutputBundle());
                        bundle.add(path, filename);
                    });
        } catch (Exception ex) {
            log.warn("Lettura output per inferenza log non riuscita: {}", ex.getMessage());
            return List.of();
        }

        List<ExecutionLogEntry> inferred = new ArrayList<>();
        Set<String> coveredBaseNames = new LinkedHashSet<>();
        for (String outputFile : coveredOutputFiles) {
            String base = baseName(outputFile);
            if (base != null) {
                coveredBaseNames.add(base);
            }
        }

        for (Map.Entry<String, OutputBundle> e : bundleByBase.entrySet()) {
            OutputBundle bundle = e.getValue();
            String representative = bundle.representativeOutputFile();
            if (representative == null) {
                continue;
            }
            if (coveredOutputFiles.contains(representative) || coveredBaseNames.contains(e.getKey())) {
                continue;
            }

            LocalDateTime ts = toLocalDateTime(bundle.latestModified());
            String procedureName = extractProcedureName(e.getKey());
            inferred.add(new ExecutionLogEntry(
                    ts,
                    procedureName,
                    "OK",
                    0L,
                    "Output rilevato nella cartella output",
                    Map.of(),
                    representative,
                    null,
                    null
            ));
        }

        inferred.sort(Comparator.comparing(ExecutionLogEntry::timestamp).reversed());
        return inferred;
    }

    private void deletePersistedFiles(StoredLogFile stored) {
        deleteQuietly(stored.logFile());
        if (stored.dbmsOutputFile() != null && !stored.dbmsOutputFile().isBlank()) {
            deleteQuietly(outputDir.resolve(stored.dbmsOutputFile()));
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Impossibile eliminare {}: {}", path.getFileName(), ex.getMessage());
        }
    }

    private String readDbmsOutput(String dbmsOutputFile) {
        if (dbmsOutputFile == null || dbmsOutputFile.isBlank()) {
            return null;
        }
        Path dbmsPath = outputDir.resolve(dbmsOutputFile).normalize();
        if (!dbmsPath.startsWith(outputDir) || !Files.exists(dbmsPath)) {
            return null;
        }
        try {
            String content = Files.readString(dbmsPath, StandardCharsets.UTF_8);
            return content.isBlank() ? null : content;
        } catch (IOException ex) {
            log.warn("Lettura file DBMS output non riuscita {}: {}", dbmsOutputFile, ex.getMessage());
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String timestamp, Path fallbackFile) {
        if (timestamp != null && !timestamp.isBlank()) {
            try {
                return LocalDateTime.parse(timestamp, ISO_TS);
            } catch (Exception ignored) {
                // fallback su data file
            }
        }
        try {
            return toLocalDateTime(Files.getLastModifiedTime(fallbackFile));
        } catch (IOException ex) {
            return LocalDateTime.now();
        }
    }

    private LocalDateTime toLocalDateTime(FileTime time) {
        Instant instant = (time != null) ? time.toInstant() : Instant.now();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private String buildBaseName(ExecutionLogEntry entry) {
        String safeProcedure = sanitizeFilename(safeString(entry.procedureName()));
        return "exec_" + entry.timestamp().format(FILE_TS) + "_" + safeProcedure;
    }

    private String sanitizeFilename(String value) {
        String normalized = (value == null || value.isBlank()) ? "unknown" : value;
        return normalized.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String baseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        if (filename.endsWith(".log.json")) {
            return filename.substring(0, filename.length() - ".log.json".length());
        }
        if (filename.endsWith(".dbms_output.txt")) {
            return filename.substring(0, filename.length() - ".dbms_output.txt".length());
        }
        if (filename.endsWith(".csv")
                || filename.endsWith(".html")
                || filename.endsWith(".xlsx")) {
            return filename.substring(0, filename.lastIndexOf('.'));
        }
        return null;
    }

    private String extractProcedureName(String baseName) {
        Matcher matcher = BUNDLE_NAME_PATTERN.matcher(baseName);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return baseName;
    }

    private String toCsvFilename(String outputReference) {
        if (outputReference == null || outputReference.isBlank()) {
            return outputReference;
        }
        if (outputReference.endsWith(".csv")) {
            return outputReference;
        }
        if (outputReference.endsWith(".html") || outputReference.endsWith(".xlsx")) {
            return outputReference.substring(0, outputReference.lastIndexOf('.')) + ".csv";
        }
        return outputReference + ".csv";
    }

    private boolean sameLog(ExecutionLogEntry left, ExecutionLogEntry right) {
        return Objects.equals(left.timestamp(), right.timestamp())
                && Objects.equals(left.procedureName(), right.procedureName())
                && Objects.equals(left.status(), right.status())
                && left.durationMs() == right.durationMs()
                && Objects.equals(left.message(), right.message())
                && Objects.equals(left.outputHtmlFile(), right.outputHtmlFile())
                && Objects.equals(left.stackTrace(), right.stackTrace())
                && Objects.equals(left.dbmsOutput(), right.dbmsOutput());
    }

    private record PersistedExecutionLog(
            String timestamp,
            String procedureName,
            String status,
            long durationMs,
            String message,
            Map<String, Object> parameters,
            String outputHtmlFile,
            String stackTrace,
            String dbmsOutputFile
    ) {
    }

    private record StoredLogFile(
            Path logFile,
            String dbmsOutputFile,
            ExecutionLogEntry entry
    ) {
    }

    private static final class OutputBundle {
        private String csvFile;
        private String htmlFile;
        private String xlsxFile;
        private FileTime latestModified = FileTime.fromMillis(0L);

        void add(Path file, String filename) {
            try {
                FileTime modified = Files.getLastModifiedTime(file);
                if (modified.compareTo(latestModified) > 0) {
                    latestModified = modified;
                }
            } catch (IOException ignored) {
                // fallback su epoch
            }

            if (filename.endsWith(".csv")) {
                csvFile = filename;
            } else if (filename.endsWith(".html")) {
                htmlFile = filename;
            } else if (filename.endsWith(".xlsx")) {
                xlsxFile = filename;
            }
        }

        String representativeOutputFile() {
            if (csvFile != null) {
                return csvFile;
            }
            if (htmlFile != null) {
                return htmlFile;
            }
            return xlsxFile;
        }

        FileTime latestModified() {
            return latestModified;
        }
    }
}
