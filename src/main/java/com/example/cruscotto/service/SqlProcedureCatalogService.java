package com.example.cruscotto.service;

import com.example.cruscotto.model.ProcedureDefinition;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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

@Service
public class SqlProcedureCatalogService {

    private static final Pattern PARAM_PATTERN = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern PARAM_DESCRIPTION_PATTERN =
            Pattern.compile("(?im)^\\s*--\\s*@param\\s+([A-Za-z][A-Za-z0-9_]*)\\s*(?::|-)?\\s*(.+?)\\s*$");
    private static final Pattern INVALID_FILE_CHARS = Pattern.compile("[^A-Za-z0-9 _-]");
    private static final String Q_QUOTE_DELIMITERS = "[({<";
    private static final String Q_QUOTE_CLOSERS = "])>}";

    private final Map<String, ProcedureDefinition> procedures = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadProcedures() {
        reloadProcedures();
    }

    public synchronized void reloadProcedures() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            procedures.clear();
            Resource[] resources = resolver.getResources("file:src/main/resources/sql/*.sql");
            if (resources.length == 0) {
                resources = resolver.getResources("classpath*:sql/*.sql");
            }
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.isBlank()) {
                    continue;
                }

                String sqlText = resource.getContentAsString(StandardCharsets.UTF_8);
                String name = filename.replaceFirst("\\.sql$", "");
                List<String> parameterNames = extractParameterNames(sqlText);
                Map<String, String> parameterDescriptions = extractParameterDescriptions(sqlText);
                long lastModifiedEpochMs = readLastModified(resource);

                procedures.put(name, new ProcedureDefinition(
                        name,
                        "sql/" + filename,
                        sqlText,
                        parameterNames,
                    parameterDescriptions,
                        lastModifiedEpochMs
                ));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Errore nel caricamento dei file SQL", ex);
        }
    }

    public List<ProcedureDefinition> findAll() {
        return procedures.values().stream()
                .sorted(Comparator.comparingLong(ProcedureDefinition::lastModifiedEpochMs)
                        .reversed()
                        .thenComparing(ProcedureDefinition::name))
                .toList();
    }

    public Optional<ProcedureDefinition> findByName(String name) {
        return Optional.ofNullable(procedures.get(name));
    }

    public synchronized String saveSqlFile(String requestedName, String sqlText, boolean overwrite) {
        String normalizedSql = sqlText == null ? "" : sqlText.trim();
        if (normalizedSql.isBlank()) {
            throw new IllegalArgumentException("SQL vuoto: impossibile salvare il file");
        }
        validateReadOnlySql(normalizedSql);

        String baseName = normalizeBaseName(requestedName);
        if (baseName.isBlank()) {
            throw new IllegalArgumentException("Nome file non valido");
        }

        Path sqlDir = Paths.get("src", "main", "resources", "sql").toAbsolutePath().normalize();
        Path sqlFile = sqlDir.resolve(baseName + ".sql").normalize();
        if (!sqlFile.startsWith(sqlDir)) {
            throw new IllegalArgumentException("Percorso file non valido");
        }

        try {
            Files.createDirectories(sqlDir);
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
        String normalizedName = normalizeBaseName(existingName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Seleziona uno script valido da aggiornare");
        }

        Path sqlDir = Paths.get("src", "main", "resources", "sql").toAbsolutePath().normalize();
        Path sqlFile = sqlDir.resolve(normalizedName + ".sql").normalize();
        if (!sqlFile.startsWith(sqlDir)) {
            throw new IllegalArgumentException("Percorso file non valido");
        }
        if (!Files.exists(sqlFile)) {
            throw new IllegalArgumentException("Script '" + normalizedName + "' non trovato");
        }

        return saveSqlFile(normalizedName, sqlText, true);
    }

    private void validateReadOnlySql(String sqlText) {
        String sanitized = stripIgnoredSections(sqlText).trim();
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("SQL non valido");
        }

        String upper = sanitized.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("Sono consentiti solo script SELECT o WITH");
        }
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
