package me.copimine.narcotics.db;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.recipe.IngredientEntry;
import me.copimine.narcotics.use.OverdoseService;
import me.copimine.narcotics.util.BlockKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class NarcoticsDatabase {
    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final CopiMineNarcotics plugin;
    private final NarcoticsConfigService configService;
    private ExecutorService executor;
    private DbSettings dbSettings;

    public NarcoticsDatabase(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public void start() {
        try {
            Class.forName("org.postgresql.Driver");
            DriverManager.setLoginTimeout(10);
            int workers = configService.asyncThreads();
            executor = new ThreadPoolExecutor(
                    workers,
                    workers,
                    30L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(configService.asyncQueueCapacity()),
                    task -> {
                Thread thread = new Thread(task, "copimine-narcotics-db");
                thread.setDaemon(true);
                return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
            dbSettings = loadDbSettings();
            ensureSchema();
        } catch (Exception error) {
            throw new IllegalStateException("CopiMineNarcotics PostgreSQL init failed: " + safeError(error), error);
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public boolean hasAsyncCapacity() {
        if (!(executor instanceof ThreadPoolExecutor pool) || pool.isShutdown() || pool.isTerminated()) {
            return false;
        }
        return pool.getActiveCount() < pool.getMaximumPoolSize() || pool.getQueue().remainingCapacity() > 0;
    }

    public CompletableFuture<Map<BlockKey, LoadedBrewingState>> loadBrewingStates(int maxRows) {
        return supplyAsync(() -> {
            Map<BlockKey, LoadedBrewingState> states = new LinkedHashMap<>();
            try (Connection connection = openConnection();
                 PreparedStatement prune = connection.prepareStatement("""
                         DELETE FROM narcotics_brewing_states
                         WHERE deleted=TRUE
                            OR (CASE WHEN updated_at < 100000000000 THEN updated_at * 1000 ELSE updated_at END) < ?
                         """)) {
                long cutoff = Instant.now().toEpochMilli() - 15L * 60L * 1000L;
                prune.setLong(1, cutoff);
                prune.executeUpdate();
            }
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT world_name,x,y,z,state_payload,state_version,deleted,ingredients_csv,updated_at
                         FROM narcotics_brewing_states
                         WHERE deleted=FALSE
                         ORDER BY updated_at DESC
                         LIMIT ?
                         """)) {
                statement.setInt(1, Math.max(1, Math.min(maxRows, 10000)));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        BlockKey key = new BlockKey(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                        String payload = rs.getString(5);
                        long version = rs.getLong(6);
                        boolean deleted = rs.getBoolean(7);
                        String legacyCsv = rs.getString(8);
                        List<IngredientEntry> entries = parseEntriesPayload(payload, legacyCsv);
                        long updatedAt = rs.getLong(9);
                        if (!deleted && !entries.isEmpty()) {
                            states.put(key, new LoadedBrewingState(entries, version, updatedAt));
                        }
                    }
                }
            }
            return states;
        });
    }

    public CompletableFuture<Void> saveBrewingState(BlockKey key, long version, List<IngredientEntry> ingredients) {
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_brewing_states(world_name,x,y,z,ingredients_csv,state_payload,state_version,deleted,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (world_name,x,y,z)
                    DO UPDATE SET ingredients_csv=EXCLUDED.ingredients_csv,
                                  state_payload=EXCLUDED.state_payload,
                                  state_version=EXCLUDED.state_version,
                                  deleted=EXCLUDED.deleted,
                                  updated_at=EXCLUDED.updated_at
                    WHERE narcotics_brewing_states.state_version < EXCLUDED.state_version
                    """)) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.setString(5, joinLegacyCsv(ingredients));
                statement.setString(6, joinStatePayload(ingredients));
                statement.setLong(7, version);
                statement.setBoolean(8, false);
                statement.setLong(9, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> deleteBrewingState(BlockKey key, long version) {
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO narcotics_brewing_states(world_name,x,y,z,ingredients_csv,state_payload,state_version,deleted,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (world_name,x,y,z)
                    DO UPDATE SET ingredients_csv=EXCLUDED.ingredients_csv,
                                  state_payload=EXCLUDED.state_payload,
                                  state_version=EXCLUDED.state_version,
                                  deleted=EXCLUDED.deleted,
                                  updated_at=EXCLUDED.updated_at
                    WHERE narcotics_brewing_states.state_version < EXCLUDED.state_version
                    """)) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.setString(5, "");
                statement.setString(6, "");
                statement.setLong(7, version);
                statement.setBoolean(8, true);
                statement.setLong(9, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> clearBrewingStates() {
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM narcotics_brewing_states")) {
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<OverdoseService.PlayerState> loadPlayerState(UUID playerUuid) {
        return supplyAsync(() -> {
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT current_scale,last_consumed_at,overdose_until,inverted_movement_until,last_item_id,state_version
                         FROM narcotics_player_overdose
                         WHERE player_uuid=?
                         """)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return OverdoseService.PlayerState.empty(playerUuid);
                    }
                    return new OverdoseService.PlayerState(
                            playerUuid,
                            rs.getInt(1),
                            rs.getLong(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            Optional.ofNullable(rs.getString(5)).orElse(""),
                            rs.getLong(6)
                    );
                }
            }
        });
    }

    public CompletableFuture<Void> savePlayerState(OverdoseService.PlayerState state) {
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_player_overdose(player_uuid,current_scale,last_consumed_at,overdose_until,inverted_movement_until,last_item_id,state_version,updated_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON CONFLICT (player_uuid)
                    DO UPDATE SET current_scale=EXCLUDED.current_scale,
                                  last_consumed_at=EXCLUDED.last_consumed_at,
                                  overdose_until=EXCLUDED.overdose_until,
                                  inverted_movement_until=EXCLUDED.inverted_movement_until,
                                  last_item_id=EXCLUDED.last_item_id,
                                  state_version=EXCLUDED.state_version,
                                  updated_at=EXCLUDED.updated_at
                    WHERE narcotics_player_overdose.state_version < EXCLUDED.state_version
                    """)) {
                statement.setString(1, state.playerUuid().toString());
                statement.setInt(2, state.currentScale());
                statement.setLong(3, state.lastConsumedAt());
                statement.setLong(4, state.overdoseUntil());
                statement.setLong(5, state.invertedMovementUntil());
                statement.setString(6, state.lastItemId());
                statement.setLong(7, state.stateVersion());
                statement.setLong(8, Instant.now().getEpochSecond());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_player_usage_window(player_uuid,window_started_at,last_item_id,updated_at)
                    VALUES (?,?,?,?)
                    ON CONFLICT (player_uuid)
                    DO UPDATE SET window_started_at=EXCLUDED.window_started_at,last_item_id=EXCLUDED.last_item_id,updated_at=EXCLUDED.updated_at
                    """)) {
                statement.setString(1, state.playerUuid().toString());
                statement.setLong(2, state.lastConsumedAt());
                statement.setString(3, state.lastItemId());
                statement.setLong(4, Instant.now().getEpochSecond());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> resetNarcoticsState() {
        return runAsync(() -> tx(connection -> {
            for (String sql : List.of(
                    "DELETE FROM narcotics_brewing_states",
                    "DELETE FROM narcotics_player_overdose",
                    "DELETE FROM narcotics_player_usage_window",
                    "DELETE FROM narcotics_config_values",
                    "DELETE FROM narcotics_admin_audit",
                    "DELETE FROM narcotics_item_texture_migrations"
            )) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                }
            }
            return null;
        }));
    }

    public void auditAsync(String actor, String action, String details) {
        runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_admin_audit(id,actor,action,details,created_at)
                    VALUES (?,?,?,?,?)
                    """)) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, actor);
                statement.setString(3, action);
                statement.setString(4, details);
                statement.setLong(5, Instant.now().getEpochSecond());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    private void ensureSchema() throws Exception {
        tx(connection -> {
            for (String sql : schemaStatements()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_schema_version(version, applied_at)
                    VALUES (?,?)
                    ON CONFLICT (version) DO NOTHING
                    """)) {
                statement.setInt(1, configService.schemaVersion());
                statement.setLong(2, Instant.now().getEpochSecond());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private List<String> schemaStatements() {
        List<String> sql = new ArrayList<>();
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_schema_version (
                  version INTEGER PRIMARY KEY,
                  applied_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_brewing_states (
                  world_name TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  ingredients_csv TEXT NOT NULL DEFAULT '',
                  state_payload TEXT NOT NULL DEFAULT '',
                  state_version BIGINT NOT NULL DEFAULT 0,
                  deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  updated_at BIGINT NOT NULL DEFAULT 0,
                  PRIMARY KEY(world_name,x,y,z)
                )
                """);
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS state_payload TEXT NOT NULL DEFAULT ''");
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS state_version BIGINT NOT NULL DEFAULT 0");
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE");
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_player_overdose (
                  player_uuid TEXT PRIMARY KEY,
                  current_scale INTEGER NOT NULL DEFAULT 0,
                  last_consumed_at BIGINT NOT NULL DEFAULT 0,
                  overdose_until BIGINT NOT NULL DEFAULT 0,
                  inverted_movement_until BIGINT NOT NULL DEFAULT 0,
                  last_item_id TEXT NOT NULL DEFAULT '',
                  state_version BIGINT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("ALTER TABLE narcotics_player_overdose ADD COLUMN IF NOT EXISTS state_version BIGINT NOT NULL DEFAULT 0");
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_player_usage_window (
                  player_uuid TEXT PRIMARY KEY,
                  window_started_at BIGINT NOT NULL DEFAULT 0,
                  last_item_id TEXT NOT NULL DEFAULT '',
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_config_values (
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL DEFAULT '',
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_admin_audit (
                  id TEXT PRIMARY KEY,
                  actor TEXT NOT NULL DEFAULT '',
                  action TEXT NOT NULL DEFAULT '',
                  details TEXT NOT NULL DEFAULT '',
                  created_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_item_texture_migrations (
                  id TEXT PRIMARY KEY,
                  player_uuid TEXT NOT NULL DEFAULT '',
                  texture_mode TEXT NOT NULL DEFAULT '',
                  created_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_brewing_updated ON narcotics_brewing_states(updated_at)");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_admin_audit_created ON narcotics_admin_audit(created_at)");
        return sql;
    }

    private DbSettings loadDbSettings() throws Exception {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path envFile = resolveEnvFile();
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim().replace("\"", "");
                values.putIfAbsent(key, value);
            }
        }
        String host = first(values.get("POSTGRES_HOST"), values.get("PGHOST"), "127.0.0.1");
        int port = parseInt(first(values.get("POSTGRES_PORT"), values.get("PGPORT"), "5432"), 5432);
        String database = first(values.get("POSTGRES_DB"), values.get("PGDATABASE"), "copimine");
        String user = first(values.get("POSTGRES_USER"), values.get("PGUSER"), "copimine");
        String password = first(values.get("POSTGRES_PASSWORD"), values.get("PGPASSWORD"), "");
        String schema = first(values.get("POSTGRES_SCHEMA"), values.get("PGSCHEMA"), "copimine");
        if (password.isBlank()) {
            throw new IllegalStateException("POSTGRES_PASSWORD is required for CopiMineNarcotics.");
        }
        if (!SAFE_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Unsafe POSTGRES_SCHEMA.");
        }
        return new DbSettings(host, port, database, user, password, schema, envFile);
    }

    private Path resolveEnvFile() {
        String explicit = System.getenv("COPIMINE_ENV_FILE");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).normalize();
        }
        List<Path> candidates = List.of(
                releaseRoot().resolve("admin-web").resolve(".env"),
                Paths.get(System.getProperty("user.dir")).resolve("../../admin-web/.env").normalize(),
                Paths.get(System.getProperty("user.dir")).resolve("../admin-web/.env").normalize()
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private Path releaseRoot() {
        try {
            Path server = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            Path minecraft = server.getParent();
            Path root = minecraft == null ? null : minecraft.getParent();
            if (root != null) {
                return root;
            }
        } catch (Throwable error) {
            plugin.getLogger().warning("Narcotics database release root autodetect failed: " + safeError(error));
        }
        return Paths.get("/opt/copimine");
    }

    private CompletableFuture<Void> runAsync(SqlVoidWork work) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null) {
            future.completeExceptionally(new IllegalStateException("Narcotics database executor is unavailable."));
            return future;
        }
        try {
            currentExecutor.execute(() -> {
                try {
                    work.run();
                    future.complete(null);
                } catch (Exception error) {
                    future.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private <T> CompletableFuture<T> supplyAsync(SqlWork<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null) {
            future.completeExceptionally(new IllegalStateException("Narcotics database executor is unavailable."));
            return future;
        }
        try {
            currentExecutor.execute(() -> {
                try {
                    future.complete(work.run());
                } catch (Exception error) {
                    future.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private <T> T tx(ConnectionWork<T> work) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private Connection openConnection() throws Exception {
        Connection connection = DriverManager.getConnection(dbSettings.jdbcUrl(), dbSettings.user(), dbSettings.password());
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + dbSettings.schemaIdent());
            statement.execute("SET search_path TO " + dbSettings.schemaIdent());
        }
        return connection;
    }

    private List<IngredientEntry> parseEntriesPayload(String payload, String legacyCsv) {
        String source = payload;
        if (source == null || source.isBlank()) {
            source = legacyCsv;
        }
        if (source == null || source.isBlank()) {
            return List.of();
        }
        List<IngredientEntry> entries = new ArrayList<>();
        for (String token : source.split("\\|")) {
            IngredientEntry entry = IngredientEntry.deserialize(token);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    private String joinStatePayload(List<IngredientEntry> ingredients) {
        List<String> encoded = new ArrayList<>();
        for (IngredientEntry ingredient : ingredients) {
            if (ingredient != null) {
                encoded.add(ingredient.serialize());
            }
        }
        return String.join("|", encoded);
    }

    private String joinLegacyCsv(List<IngredientEntry> ingredients) {
        List<String> raw = new ArrayList<>();
        for (IngredientEntry ingredient : ingredients) {
            if (ingredient != null) {
                raw.add(ingredient.recipeKey());
            }
        }
        return String.join("|", raw);
    }

    private String safeError(Throwable error) {
        String message = error == null ? "unknown" : String.valueOf(error.getMessage());
        return message.replaceAll("(?i)(password=)[^\\s&]+", "$1***").replaceAll("(?i)(POSTGRES_PASSWORD=)[^\\s&]+", "$1***");
    }

    private void warnSuppressed(String context, Throwable error) {
        plugin.getLogger().warning(context + ": " + safeError(error));
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException parseError) {
            warnSuppressed("narcotics database parseInt raw=" + raw, parseError);
            return fallback;
        }
    }

    @FunctionalInterface
    private interface ConnectionWork<T> {
        T run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    private interface SqlVoidWork {
        void run() throws Exception;
    }

    public record LoadedBrewingState(List<IngredientEntry> ingredients, long version, long updatedAtEpochMillis) {
    }

    private record DbSettings(String host, int port, String database, String user, String password, String schema, Path envFile) {
        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        String schemaIdent() {
            return "\"" + schema.replace("\"", "\"\"") + "\"";
        }
    }
}
