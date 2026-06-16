package com.example.cruscotto.service;

import com.example.cruscotto.model.ProcedureDefinition;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class SqlProcedureCatalogService {

    private static final Pattern PARAM_PATTERN = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern PARAM_DESCRIPTION_PATTERN =
            Pattern.compile("(?im)^\\s*--\\s*@param\\s+([A-Za-z][A-Za-z0-9_]*)\\s*(?::|-)?\\s*(.+?)\\s*$");
    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[^A-Za-z0-9 _-]");
    private static final String Q_QUOTE_DELIMITERS = "[({<";
    private static final String Q_QUOTE_CLOSERS = "])>}";

    private final Map<String, ProcedureDefinition> procedures = new ConcurrentHashMap<>();
    private final Path sqlRootDir;

    public SqlProcedureCatalogService(@Value("${app.sql.root-dir:${app.sql.folder:sql}}") String sqlRootDir) {
        this.sqlRootDir = Paths.get(sqlRootDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void loadProcedures() {
        reloadProcedures();
    }

    public synchronized void reloadProcedures() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            procedures.clear();
            loadClasspathSql(resolver);
            loadFilesystemSql();
        } catch (IOException ex) {
            throw new IllegalStateException("Errore nel caricamento dei file SQL", ex);
        }
    }

    public List<ProcedureDefinition> findAll() {
        return findAll(null);
    }

    public List<ProcedureDefinition> findAll(String connectionLabel) {
        if (connectionLabel != null && !connectionLabel.isBlank()) {
            return loadNamespaceProcedures(connectionLabel).values().stream()
                    .sorted(Comparator.comparingLong(ProcedureDefinition::lastModifiedEpochMs)
                            .reversed()
                            .thenComparing(ProcedureDefinition::name))
                    .toList();
        }
        return procedures.values().stream()
                .sorted(Comparator.comparingLong(ProcedureDefinition::lastModifiedEpochMs)
                        .reversed()
                        .thenComparing(ProcedureDefinition::name))
                .toList();
    }

    public Optional<ProcedureDefinition> findByName(String name) {
        return findByName(null, name);
    }

    public Optional<ProcedureDefinition> findByName(String connectionLabel, String name) {
        if (connectionLabel != null && !connectionLabel.isBlank()) {
            return Optional.ofNullable(loadNamespaceProcedures(connectionLabel).get(name));
        }
        return Optional.ofNullable(procedures.get(name));
    }

    public synchronized String saveSqlFile(String requestedName, String sqlText, boolean overwrite) {
        return saveSqlFile(null, requestedName, sqlText, overwrite);
    }

    public synchronized String saveSqlFile(String connectionLabel, String requestedName, String sqlText, boolean overwrite) {
        String normalizedSql = sqlText == null ? "" : sqlText.trim();
        if (normalizedSql.isBlank()) {
            throw new IllegalArgumentException("SQL vuoto: impossibile salvare il file");
        }

        String baseName = normalizeBaseName(requestedName);
        if (baseName.isBlank()) {
            throw new IllegalArgumentException("Nome file non valido");
        }

        Path targetDir = resolveTargetSqlDir(connectionLabel);
        Path sqlFile = targetDir.resolve(baseName + ".sql").normalize();
        if (!sqlFile.startsWith(targetDir)) {
            throw new IllegalArgumentException("Percorso file non valido");
        }

        try {
            Files.createDirectories(targetDir);
            if (Files.exists(sqlFile) && !overwrite) {
                throw new IllegalArgumentException("Esiste già uno script con nome '" + baseName + "'");
            }

            String content = normalizedSql.endsWith("\n") ? normalizedSql : normalizedSql + System.lineSeparator();
            Files.writeString(sqlFile, content, StandardCharsets.UTF_8);
            reloadProcedures();
            return baseName;
        } catch (IOException ex) {
            throw new IllegalStateException("Errore durante il salvataggio del file SQL", ex);
        }
    }

    public synchronized String updateSqlFile(String existingName, String sqlText) {
        return updateSqlFile(null, existingName, sqlText);
    }

    public synchronized String updateSqlFile(String connectionLabel, String existingName, String sqlText) {
        String normalizedSql = sqlText == null ? "" : sqlText.trim();
        if (normalizedSql.isBlank()) {
            throw new IllegalArgumentException("SQL vuoto: impossibile salvare il file");
        }

        if (existingName == null || existingName.isBlank()) {
            throw new IllegalArgumentException("Seleziona uno script valido da aggiornare");
        }

        try {
            Map<String, ProcedureDefinition> source = (connectionLabel == null || connectionLabel.isBlank())
                    ? procedures
                    : loadNamespaceProcedures(connectionLabel);
            String resolvedName = source.containsKey(existingName)
                    ? existingName
                    : normalizeBaseName(existingName);
            if (resolvedName.isBlank()) {
                throw new IllegalArgumentException("Seleziona uno script valido da aggiornare");
            }

            Path targetDir = resolveTargetSqlDir(connectionLabel);
            Path sqlFile = targetDir.resolve(resolvedName + ".sql").normalize();
            if (!sqlFile.startsWith(targetDir)) {
                throw new IllegalArgumentException("Percorso file non valido");
            }

            String content = normalizedSql.endsWith("\n") ? normalizedSql : normalizedSql + System.lineSeparator();
            Files.createDirectories(sqlFile.getParent());
            Files.writeString(sqlFile, content, StandardCharsets.UTF_8);
            reloadProcedures();
            return resolvedName;
        } catch (IOException ex) {
            throw new IllegalStateException("Errore durante il salvataggio del file SQL: " + ex.getMessage(), ex);
        }
    }

    public synchronized void reloadProcedures(String connectionLabel) {
        if (connectionLabel == null || connectionLabel.isBlank()) {
            reloadProcedures();
        }
    }

    private void loadClasspathSql(PathMatchingResourcePatternResolver resolver) throws IOException {
        for (String pattern : new String[]{"classpath*:sql/*.sql", "classpath*:sql/**/*.sql"}) {
            for (Resource resource : resolver.getResources(pattern)) {
                registerResource(resource);
            }
        }
    }

    private void loadFilesystemSql() throws IOException {
        if (!Files.exists(sqlRootDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(sqlRootDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".sql"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            registerFile(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Errore nel caricamento del file SQL: " + path, ex);
                        }
                    });
        }
    }

    private Map<String, ProcedureDefinition> loadNamespaceProcedures(String connectionLabel) {
        Path namespaceDir = resolveTargetSqlDir(connectionLabel);
        Map<String, ProcedureDefinition> namespaceProcedures = new LinkedHashMap<>();
        if (!Files.exists(namespaceDir)) {
            return namespaceProcedures;
        }
        try (Stream<Path> paths = Files.walk(namespaceDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".sql"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            registerFile(path, namespaceDir, namespaceProcedures);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Errore nel caricamento del file SQL: " + path, ex);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Errore nel caricamento dei file SQL namespace '" + connectionLabel + "'", ex);
        }
        return namespaceProcedures;
    }

    private void registerResource(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            return;
        }

        String sqlText = resource.getContentAsString(StandardCharsets.UTF_8);
        String name = filename.replaceFirst("\\.sql$", "");
        List<String> parameterNames = extractParameterNames(sqlText);
        Map<String, String> parameterDescriptions = extractParameterDescriptions(sqlText);
        long lastModifiedEpochMs = readLastModified(resource);

        procedures.put(name, new ProcedureDefinition(
                name,
                resource.getDescription(),
                sqlText,
                parameterNames,
                parameterDescriptions,
                lastModifiedEpochMs
        ));
    }

    private void registerFile(Path file) throws IOException {
        registerFile(file, sqlRootDir, procedures);
    }

    private void registerFile(Path file, Path baseDir, Map<String, ProcedureDefinition> targetMap) throws IOException {
        String filename = file.getFileName().toString();
        String sqlText = Files.readString(file, StandardCharsets.UTF_8);
        String name = filename.replaceFirst("\\.sql$", "");
        List<String> parameterNames = extractParameterNames(sqlText);
        Map<String, String> parameterDescriptions = extractParameterDescriptions(sqlText);
        long lastModifiedEpochMs = Files.getLastModifiedTime(file).toMillis();
        String relativePath = baseDir.relativize(file).toString().replace('\\', '/');

        targetMap.put(name, new ProcedureDefinition(
                name,
                "file:" + relativePath,
                sqlText,
                parameterNames,
                parameterDescriptions,
                lastModifiedEpochMs
        ));
    }

    private Path resolveTargetSqlDir(String connectionLabel) {
        if (connectionLabel == null || connectionLabel.isBlank()) {
            return sqlRootDir;
        }
        String folderName = normalizeBaseName(connectionLabel);
        if (folderName.isBlank()) {
            throw new IllegalArgumentException("Etichetta connessione non valida");
        }
        Path targetDir = sqlRootDir.resolve(folderName).normalize();
        if (!targetDir.startsWith(sqlRootDir)) {
            throw new IllegalArgumentException("Percorso cartella SQL non valido");
        }
        return targetDir;
    }

    private String normalizeBaseName(String requestedName) {
        if (requestedName == null) {
            return "";
        }

        String trimmed = requestedName.trim();
        if (trimmed.toLowerCase().endsWith(".sql")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4).trim();
        }

        String cleaned = INVALID_FILE_CHARS.matcher(trimmed).replaceAll("_");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replace(' ', '_');
        return cleaned;
    }

    private List<String> extractParameterNames(String sqlText) {
        LinkedHashSet<String> params = new LinkedHashSet<>();
        Matcher matcher = PARAM_PATTERN.matcher(stripIgnoredSections(sqlText));
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return new ArrayList<>(params);
    }

    private Map<String, String> extractParameterDescriptions(String sqlText) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        Matcher matcher = PARAM_DESCRIPTION_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String description = matcher.group(2).trim();
            if (!description.isBlank() && !descriptions.containsKey(paramName)) {
                descriptions.put(paramName, description);
            }
        }
        return descriptions;
    }

    private String stripIgnoredSections(String sqlText) {
        StringBuilder sanitized = new StringBuilder(sqlText.length());
        int index = 0;
        while (index < sqlText.length()) {
            char current = sqlText.charAt(index);

            if (startsLineComment(sqlText, index)) {
                index = copyWhitespaceUntilNewline(sqlText, sanitized, index + 2);
                continue;
            }

            if (startsBlockComment(sqlText, index)) {
                index = copyWhitespaceUntilBlockCommentEnd(sqlText, sanitized, index + 2);
                continue;
            }

            if (startsQQuoteLiteral(sqlText, index)) {
                index = copyWhitespaceUntilQQuoteEnd(sqlText, sanitized, index);
                continue;
            }

            if (current == '\'') {
                index = copyWhitespaceUntilStringEnd(sqlText, sanitized, index + 1);
                continue;
            }

            sanitized.append(current);
            index++;
        }
        return sanitized.toString();
    }

    private boolean startsLineComment(String text, int index) {
        return index + 1 < text.length() && text.charAt(index) == '-' && text.charAt(index + 1) == '-';
    }

    private boolean startsBlockComment(String text, int index) {
        return index + 1 < text.length() && text.charAt(index) == '/' && text.charAt(index + 1) == '*';
    }

    private boolean startsQQuoteLiteral(String text, int index) {
        return index + 2 < text.length()
                && (text.charAt(index) == 'q' || text.charAt(index) == 'Q')
                && text.charAt(index + 1) == '\''
                && isQQuoteDelimiter(text.charAt(index + 2));
    }

    private boolean isQQuoteDelimiter(char delimiter) {
        return Q_QUOTE_DELIMITERS.indexOf(delimiter) >= 0 || Character.isLetterOrDigit(delimiter) || Character.isWhitespace(delimiter) || isPunctuationDelimiter(delimiter);
    }

    private boolean isPunctuationDelimiter(char delimiter) {
        return "!@#$%^&*_+=|\\:;,.?/~-".indexOf(delimiter) >= 0;
    }

    private int copyWhitespaceUntilNewline(String text, StringBuilder sanitized, int index) {
        sanitized.append(' ').append(' ');
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\r' || current == '\n') {
                break;
            }
            sanitized.append(' ');
            index++;
        }
        return index;
    }

    private int copyWhitespaceUntilBlockCommentEnd(String text, StringBuilder sanitized, int index) {
        sanitized.append(' ').append(' ');
        while (index < text.length()) {
            if (index + 1 < text.length() && text.charAt(index) == '*' && text.charAt(index + 1) == '/') {
                sanitized.append(' ').append(' ');
                return index + 2;
            }
            appendPreservingNewlines(sanitized, text.charAt(index));
            index++;
        }
        return index;
    }

    private int copyWhitespaceUntilStringEnd(String text, StringBuilder sanitized, int index) {
        sanitized.append(' ');
        while (index < text.length()) {
            char current = text.charAt(index);
            sanitized.append(current == '\r' || current == '\n' ? current : ' ');
            index++;
            if (current == '\'') {
                if (index < text.length() && text.charAt(index) == '\'') {
                    sanitized.append(' ');
                    index++;
                    continue;
                }
                break;
            }
        }
        return index;
    }

    private int copyWhitespaceUntilQQuoteEnd(String text, StringBuilder sanitized, int index) {
        char openingDelimiter = text.charAt(index + 2);
        char closingDelimiter = matchingQQuoteDelimiter(openingDelimiter);

        sanitized.append(' ').append(' ').append(' ');
        index += 3;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == closingDelimiter && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                sanitized.append(' ').append(' ');
                return index + 2;
            }
            appendPreservingNewlines(sanitized, current);
            index++;
        }
        return index;
    }

    private char matchingQQuoteDelimiter(char openingDelimiter) {
        int bracketIndex = Q_QUOTE_DELIMITERS.indexOf(openingDelimiter);
        if (bracketIndex >= 0) {
            return Q_QUOTE_CLOSERS.charAt(bracketIndex);
        }
        return openingDelimiter;
    }

    private void appendPreservingNewlines(StringBuilder sanitized, char current) {
        sanitized.append(current == '\r' || current == '\n' ? current : ' ');
    }

    private long readLastModified(Resource resource) {
        try {
            return resource.lastModified();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
