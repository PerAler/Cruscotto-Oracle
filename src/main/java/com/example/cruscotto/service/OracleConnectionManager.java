package com.example.cruscotto.service;

import com.example.cruscotto.model.OracleConnectionInfo;
import com.example.cruscotto.model.OracleConnectionProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.function.Function;

@Service
public class OracleConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(OracleConnectionManager.class);

    private static final Pattern ORACLE_IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_$#]{0,127}");
    private static final Pattern CONNECTION_TARGET_SID = Pattern.compile("[A-Za-z0-9._-]+:\\d{1,5}:[A-Za-z0-9._$#-]+");
    private static final Pattern CONNECTION_TARGET_SERVICE = Pattern.compile("//[A-Za-z0-9._-]+:\\d{1,5}/[A-Za-z0-9._$#-]+");
    private static final String JDBC_PREFIX = "jdbc:oracle:thin:@";
    private static final Path CONNECTION_PROFILES_FILE = Path.of("config", "cruscotto-oracle-connections.json");

    public record ConnectionView(OracleConnectionInfo info, boolean reachable) {
    }

    public record ResolvedConnection(
            OracleConnectionInfo info,
            DataSource dataSource,
            NamedParameterJdbcTemplate template
    ) {
    }

    private record ManagedConnection(
            OracleConnectionInfo info,
            HikariDataSource dataSource,
            NamedParameterJdbcTemplate template
    ) {
    }

    private final Map<String, ManagedConnection> connections = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeConnectionId = new AtomicReference<>();
    private final ObjectMapper objectMapper;
    private final List<OracleConnectionProfile> savedProfiles = new ArrayList<>();

    public OracleConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadSavedProfilesFromDisk();
    }

    public List<ConnectionView> listConnections() {
        return connections.values().stream()
                .sorted(Comparator.comparing(c -> c.info().createdAt()))
                .map(c -> new ConnectionView(c.info(), isReachable(c.dataSource())))
                .toList();
    }

    public synchronized List<OracleConnectionProfile> listSavedProfiles() {
        return savedProfiles.stream()
                .sorted(Comparator.comparing(OracleConnectionProfile::lastSuccessfulAt).reversed())
                .toList();
    }

    public Optional<String> getActiveConnectionId() {
        String value = activeConnectionId.get();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public Optional<OracleConnectionInfo> getActiveConnectionInfo() {
        String activeId = activeConnectionId.get();
        if (activeId == null || activeId.isBlank()) {
            return Optional.empty();
        }
        ManagedConnection managed = connections.get(activeId);
        return managed == null ? Optional.empty() : Optional.of(managed.info());
    }

    public Optional<OracleConnectionInfo> findConnectionInfo(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return Optional.empty();
        }
        ManagedConnection managed = connections.get(connectionId.trim());
        return managed == null ? Optional.empty() : Optional.of(managed.info());
    }

    public boolean hasConnections() {
        return !connections.isEmpty();
    }

    public boolean isActiveConnectionReachable() {
        String activeId = activeConnectionId.get();
        if (activeId == null || activeId.isBlank()) {
            return false;
        }
        return isConnectionReachable(activeId);
    }

    public boolean isConnectionReachable(String connectionId) {
        ResolvedConnection resolved = resolveConnection(connectionId);
        return isReachable(resolved.dataSource());
    }

    public ConnectionView openConnection(String label,
                                         String connectionTarget,
                                         String username,
                                         String password,
                                         String schema) {
        String normalizedLabel = requireNonBlank(label, "Etichetta connessione obbligatoria");
        String normalizedTarget = normalizeConnectionTarget(connectionTarget);
        String normalizedUrl = JDBC_PREFIX + normalizedTarget;
        String normalizedUsername = requireNonBlank(username, "Username obbligatorio");
        String normalizedPassword = requireNonBlank(password, "Password obbligatoria");
        String normalizedSchema = normalizeSchema(schema);

        String id = UUID.randomUUID().toString();
        HikariDataSource dataSource = buildDataSource(id, normalizedUrl, normalizedUsername, normalizedPassword);

        try {
            try (Connection conn = dataSource.getConnection()) {
                if (normalizedSchema != null) {
                    applyCurrentSchema(conn, normalizedSchema);
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM DUAL");
                     ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) != 1) {
                        throw new IllegalStateException("Connessione Oracle non valida");
                    }
                }
            }
        } catch (Exception ex) {
            dataSource.close();
            throw new IllegalStateException("Connessione Oracle non riuscita: " + ex.getMessage(), ex);
        }

        OracleConnectionInfo info = new OracleConnectionInfo(
                id,
                normalizedLabel,
                normalizedTarget,
                normalizedUrl,
                normalizedUsername,
                normalizedSchema,
                Instant.now()
        );
        ManagedConnection managed = new ManagedConnection(info, dataSource, new NamedParameterJdbcTemplate(dataSource));
        connections.put(id, managed);
        activeConnectionId.set(id);
        rememberSuccessfulProfile(info);
        return new ConnectionView(info, true);
    }

    public void activateConnection(String connectionId) {
        String resolvedId = requireNonBlank(connectionId, "connectionId obbligatorio");
        if (!connections.containsKey(resolvedId)) {
            throw new IllegalArgumentException("Connessione non trovata: " + resolvedId);
        }
        activeConnectionId.set(resolvedId);
    }

    public void closeConnection(String connectionId) {
        String resolvedId = requireNonBlank(connectionId, "connectionId obbligatorio");
        ManagedConnection removed = connections.remove(resolvedId);
        if (removed == null) {
            throw new IllegalArgumentException("Connessione non trovata: " + resolvedId);
        }
        removed.dataSource().close();

        String activeId = activeConnectionId.get();
        if (resolvedId.equals(activeId)) {
            String nextActive = connections.values().stream()
                    .sorted(Comparator.comparing(c -> c.info().createdAt()))
                    .map(c -> c.info().id())
                    .findFirst()
                    .orElse(null);
            activeConnectionId.set(nextActive);
        }
    }

    public ResolvedConnection resolveConnection(String requestedConnectionId) {
        String targetId = requestedConnectionId;
        if (targetId == null || targetId.isBlank()) {
            targetId = activeConnectionId.get();
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalStateException("Nessuna connessione Oracle aperta. Apri una connessione nell'Editor.");
        }

        ManagedConnection managed = connections.get(targetId);
        if (managed == null) {
            throw new IllegalArgumentException("Connessione non trovata: " + targetId);
        }
        return new ResolvedConnection(managed.info(), managed.dataSource(), managed.template());
    }

    public <T> T withTemplate(String requestedConnectionId, Function<NamedParameterJdbcTemplate, T> operation) {
        return operation.apply(resolveConnection(requestedConnectionId).template());
    }

    public void applyCurrentSchema(Connection connection, String schema) {
        if (schema == null || schema.isBlank()) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossibile impostare lo schema corrente '" + schema + "': " + ex.getMessage(), ex);
        }
    }

    private HikariDataSource buildDataSource(String id, String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(0);
        config.setPoolName("oracle-session-" + id.substring(0, 8));
        return new HikariDataSource(config);
    }

    private boolean isReachable(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM DUAL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return null;
        }
        String normalized = schema.trim().toUpperCase(Locale.ROOT);
        if (!ORACLE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Schema non valido: usa un identificatore Oracle semplice");
        }
        return normalized;
    }

    private String normalizeConnectionTarget(String connectionTarget) {
        String normalized = requireNonBlank(connectionTarget, "Target DB obbligatorio (server:porta:sid)");
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:oracle:thin:@")) {
            normalized = normalized.substring("jdbc:oracle:thin:@".length()).trim();
        } else if (lower.startsWith("jdbc:oracle:tin:@")) {
            normalized = normalized.substring("jdbc:oracle:tin:@".length()).trim();
        }

        if (CONNECTION_TARGET_SID.matcher(normalized).matches()) {
            return normalized;
        }
        if (CONNECTION_TARGET_SERVICE.matcher(normalized).matches()) {
            return normalized;
        }
        throw new IllegalArgumentException("Target DB non valido: usa server:porta:sid oppure //server:porta/service");
    }

    private synchronized void rememberSuccessfulProfile(OracleConnectionInfo info) {
        OracleConnectionProfile profile = new OracleConnectionProfile(
                info.label(),
                info.connectionTarget(),
                info.username(),
                info.schema(),
                Instant.now()
        );
        String key = profileKey(profile.label(), profile.connectionTarget(), profile.username(), profile.schema());
        int existingIdx = -1;
        for (int i = 0; i < savedProfiles.size(); i++) {
            OracleConnectionProfile existing = savedProfiles.get(i);
            if (profileKey(existing.label(), existing.connectionTarget(), existing.username(), existing.schema()).equals(key)) {
                existingIdx = i;
                break;
            }
        }
        if (existingIdx >= 0) {
            savedProfiles.set(existingIdx, profile);
        } else {
            savedProfiles.add(profile);
        }
        persistSavedProfilesToDisk();
    }

    private synchronized void loadSavedProfilesFromDisk() {
        Path absPath = CONNECTION_PROFILES_FILE.toAbsolutePath();
        if (!Files.exists(CONNECTION_PROFILES_FILE)) {
            log.warn("File profili connessioni non trovato: {}", absPath);
            return;
        }
        log.info("Caricamento profili connessioni da: {}", absPath);
        try {
            List<OracleConnectionProfile> loaded = objectMapper.readValue(
                    CONNECTION_PROFILES_FILE.toFile(),
                    new TypeReference<List<OracleConnectionProfile>>() {
                    }
            );
            savedProfiles.clear();
            if (loaded != null) {
                for (OracleConnectionProfile profile : loaded) {
                    if (profile == null) {
                        continue;
                    }
                    if (profile.label() == null || profile.label().isBlank()) {
                        continue;
                    }
                    if (profile.connectionTarget() == null || profile.connectionTarget().isBlank()) {
                        continue;
                    }
                    if (profile.username() == null || profile.username().isBlank()) {
                        continue;
                    }
                    savedProfiles.add(profile);
                }
            }
            log.info("Caricati {} profili connessioni", savedProfiles.size());
        } catch (Exception e) {
            log.error("Errore caricamento profili connessioni da {}: {}", absPath, e.getMessage(), e);
            savedProfiles.clear();
        }
    }

    private synchronized void persistSavedProfilesToDisk() {
        try {
            Files.createDirectories(CONNECTION_PROFILES_FILE.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CONNECTION_PROFILES_FILE.toFile(), savedProfiles);
        } catch (Exception ignored) {
        }
    }

    private String profileKey(String label, String target, String username, String schema) {
        return String.join("|",
                normalizeProfilePart(label),
                normalizeProfilePart(target),
                normalizeProfilePart(username),
                normalizeProfilePart(schema));
    }

    private String normalizeProfilePart(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
