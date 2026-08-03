package me.copimine.narcotics.db;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.recipe.IngredientEntry;
import me.copimine.narcotics.use.OverdoseService;
import me.copimine.narcotics.util.BlockKey;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class NarcoticsDatabase {
    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final CopiMineNarcotics plugin;
    private final NarcoticsConfigService configService;
    private ExecutorService executor;
    private DbSettings dbSettings;
    private volatile CompletableFuture<Void> schemaReady = CompletableFuture.failedFuture(
            new IllegalStateException("Narcotics database has not been started."));
    private final AtomicLong writeGeneration = new AtomicLong(0L);
    private final AtomicBoolean resetBarrier = new AtomicBoolean(false);
    private final Object poolMonitor = new Object();
    private final BlockingQueue<Connection> idleConnections = new ArrayBlockingQueue<>(8);
    private final Set<Connection> allConnections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger createdConnections = new AtomicInteger(0);
    private volatile int maxConnections = 2;
    private final Map<Thread, Long> activeWriteGeneration = new ConcurrentHashMap<>();
    /**
     * A cauldron's state writes must be ordered per block.  The Bukkit thread
     * can persist version N-1 and complete version N in adjacent ticks while
     * the database pool has multiple workers; a per-key tail prevents the
     * completion CAS from overtaking the state save without serialising the
     * whole database.
     */
    private final Map<BlockKey, CompletableFuture<Void>> brewingWriteTails = new ConcurrentHashMap<>();
    private final Object refundJournalLock = new Object();
    private Path refundJournal;
    private final Object auditJournalLock = new Object();
    private Path auditJournal;
    private final Object brewingJournalLock = new Object();
    private Path brewingJournal;
    private final Object brewingCompletionJournalLock = new Object();
    private Path brewingCompletionJournal;

    /**
     * shutdownNow() returns queued Runnables without executing them.  Keeping
     * the future on the wrapper lets us fail those operations explicitly so
     * refund/audit callbacks can append their durable journals.
     */
    private static final class QueuedTask implements Runnable {
        private final Runnable delegate;
        private final CompletableFuture<?> future;

        private QueuedTask(Runnable delegate, CompletableFuture<?> future) {
            this.delegate = delegate;
            this.future = future;
        }

        @Override
        public void run() {
            delegate.run();
        }

        private void reject(Throwable error) {
            future.completeExceptionally(error);
        }
    }

    @FunctionalInterface
    private interface BrewingWriteOperation<T> {
        CompletableFuture<T> start();
    }

    public record PendingRefund(String id, String playerUuid, String narcoticId, int amount) {}

    /**
     * Durable mailbox row for a product produced by a completed brew.  This is
     * deliberately separate from the ingredient compensation table: narcotic
     * products are never refunds for consumed/destroyed narcotics, they are a
     * new output which must survive a crash between the tombstone and Bukkit
     * inventory delivery.
     */
    public record PendingBrewingOutput(String id, String playerUuid, String narcoticId, int amount) {}

    /**
     * Durable two-phase marker for a physical narcotic consumption.
     *
     * The player inventory is owned by the Bukkit thread while the overdose
     * state is committed by the PostgreSQL worker.  Keeping the marker in the
     * same database transaction as the state write means a restart cannot
     * turn a successful state update into a reusable physical item (or refund
     * the same item twice).
     */
    public record ConsumptionReservation(String reservationId, String instanceId, String playerUuid,
                                         String narcoticId, String status, int quantityBefore) {}

    public NarcoticsDatabase(CopiMineNarcotics plugin, NarcoticsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public CompletableFuture<Void> start() {
        try {
            Class.forName("org.postgresql.Driver");
            DriverManager.setLoginTimeout(10);
            int workers = configService.asyncThreads();
            maxConnections = Math.max(1, Math.min(8, workers));
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
            refundJournal = plugin.getDataFolder().toPath().resolve("pending-refunds.journal");
            auditJournal = plugin.getDataFolder().toPath().resolve("admin-audit.journal");
            brewingJournal = plugin.getDataFolder().toPath().resolve("brewing-state.journal");
            brewingCompletionJournal = plugin.getDataFolder().toPath().resolve("brewing-completion.journal");
            Files.createDirectories(refundJournal.getParent());
            CompletableFuture<Void> ready = new CompletableFuture<>();
            schemaReady = ready;
            try {
                executor.execute(() -> {
                    try {
                        ensureSchema();
                        replayRefundJournal();
                        replayAuditJournal();
                        replayBrewingJournal();
                        replayBrewingCompletionJournal();
                        ready.complete(null);
                    } catch (Exception error) {
                        ready.completeExceptionally(error);
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Narcotics PostgreSQL schema initialization failed", error);
                    }
                });
            } catch (RejectedExecutionException error) {
                ready.completeExceptionally(error);
            }
            return ready;
        } catch (Exception error) {
            CompletableFuture<Void> failed = CompletableFuture.failedFuture(
                    new IllegalStateException("CopiMineNarcotics PostgreSQL init failed: " + safeError(error), error));
            schemaReady = failed;
            return failed;
        }
    }

    private static final String FUNGIBLE_INSTANCE_PREFIX = "NARCOTIC_STACK:";

    /** Register a signed fungible identity before it can be consumed. */
    public CompletableFuture<Void> registerIssuedInstance(String instanceId, String narcoticId) {
        if (instanceId == null || instanceId.isBlank() || narcoticId == null || narcoticId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid narcotic issuance."));
        }
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_issued_instances(instance_id,narcotic_id,status,issued_at,updated_at)
                    VALUES (?,?, 'ACTIVE', ?, ?)
                    ON CONFLICT (instance_id) DO NOTHING
                    """)) {
                long now = Instant.now().getEpochSecond();
                statement.setString(1, instanceId);
                statement.setString(2, narcoticId);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    rejectDroppedTasks(executor.shutdownNow());
                    executor.awaitTermination(2L, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                rejectDroppedTasks(executor.shutdownNow());
            }
        }
        closePool();
        brewingWriteTails.clear();
    }

    private void rejectDroppedTasks(List<Runnable> dropped) {
        if (dropped == null || dropped.isEmpty()) {
            return;
        }
        RejectedExecutionException error = new RejectedExecutionException(
                "Narcotics database executor stopped before the task ran.");
        for (Runnable task : dropped) {
            if (task instanceof QueuedTask queued) {
                queued.reject(error);
            }
        }
    }

    public boolean hasAsyncCapacity() {
        if (!(executor instanceof ThreadPoolExecutor pool) || pool.isShutdown() || pool.isTerminated()
                || !schemaReady.isDone() || schemaReady.isCompletedExceptionally()) {
            return false;
        }
        return pool.getActiveCount() < pool.getMaximumPoolSize() || pool.getQueue().remainingCapacity() > 0;
    }

    public CompletableFuture<Map<BlockKey, LoadedBrewingState>> loadBrewingStates(int maxRows) {
        return supplyAsync(() -> {
            Map<BlockKey, LoadedBrewingState> states = new LinkedHashMap<>();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT world_name,x,y,z,state_payload,state_version,deleted,ingredients_csv,updated_at,owner_uuid
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
                        String ownerUuid = Optional.ofNullable(rs.getString(10)).orElse("");
                        if (!deleted && !entries.isEmpty()) {
                            states.put(key, new LoadedBrewingState(entries, version, updatedAt, ownerUuid));
                        }
                    }
                }
            }
            return states;
        });
    }

    public CompletableFuture<Void> saveBrewingState(BlockKey key, long version, List<IngredientEntry> ingredients) {
        return saveBrewingState(key, version, ingredients, (UUID) null);
    }

    public CompletableFuture<Void> saveBrewingState(BlockKey key, long version, List<IngredientEntry> ingredients, UUID ownerUuid) {
        List<IngredientEntry> snapshot = ingredients == null ? List.of() : List.copyOf(ingredients);
        String journalKey = brewingJournalKey(key, version);
        if (!appendBrewingJournal(key, version, snapshot, ownerUuid)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Brewing state journal is unavailable."));
        }
        return enqueueBrewingWrite(key, () -> runAsync(() -> tx(connection -> {
            persistBrewingState(connection, key, version, snapshot, ownerUuid);
            return null;
        }))).whenComplete((ignored, error) -> {
            if (error == null) {
                removeBrewingJournal(journalKey);
            }
        });
    }

    public CompletableFuture<Void> deleteBrewingState(BlockKey key, long version) {
        return enqueueBrewingWrite(key, () -> runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO narcotics_brewing_states(world_name,x,y,z,ingredients_csv,state_payload,state_version,deleted,updated_at,owner_uuid)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (world_name,x,y,z)
                    DO UPDATE SET ingredients_csv=EXCLUDED.ingredients_csv,
                                  state_payload=EXCLUDED.state_payload,
                                  state_version=EXCLUDED.state_version,
                                  deleted=EXCLUDED.deleted,
                                  updated_at=EXCLUDED.updated_at,
                                  owner_uuid=EXCLUDED.owner_uuid
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
                statement.setString(10, "");
                statement.executeUpdate();
            }
            return null;
        })));
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
        return savePlayerState(state, null);
    }

    /**
     * Save overdose state and atomically advance a consumption reservation.
     *
     * The reservation is optional for administrative/state-repair writes.  A
     * normal player consumption supplies its durable reservation id; if the
     * reservation is missing or belongs to another player the transaction is
     * rejected, so the state can never be committed without a durable marker.
     */
    public CompletableFuture<Void> savePlayerState(OverdoseService.PlayerState state, String reservationId) {
        if (state == null || state.playerUuid() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Player state is required."));
        }
        if (reservationId != null && reservationId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Consumption reservation id is blank."));
        }
        return runAsync(() -> tx(connection -> {
            long now = Instant.now().getEpochSecond();
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
                statement.setLong(8, now);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_player_usage_window(player_uuid,window_started_at,last_item_id,updated_at,state_version)
                    VALUES (?,?,?,?,?)
                    ON CONFLICT (player_uuid)
                    DO UPDATE SET window_started_at=EXCLUDED.window_started_at,last_item_id=EXCLUDED.last_item_id,
                                  updated_at=EXCLUDED.updated_at,state_version=EXCLUDED.state_version
                    WHERE narcotics_player_usage_window.state_version < EXCLUDED.state_version
                       OR (narcotics_player_usage_window.state_version = EXCLUDED.state_version
                           AND narcotics_player_usage_window.updated_at <= EXCLUDED.updated_at)
                    """)) {
                statement.setString(1, state.playerUuid().toString());
                statement.setLong(2, state.lastConsumedAt());
                statement.setString(3, state.lastItemId());
                statement.setLong(4, now);
                statement.setLong(5, state.stateVersion());
                statement.executeUpdate();
            }
            if (reservationId != null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE narcotics_consumption_reservations
                        SET status='STATE_COMMITTED',updated_at=?
                        WHERE reservation_id=? AND player_uuid=? AND status='RESERVED'
                        """)) {
                    statement.setLong(1, now);
                    statement.setString(2, reservationId);
                    statement.setString(3, state.playerUuid().toString());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("Consumption reservation is missing or already committed.");
                    }
                }
            }
            return null;
        }));
    }

    /**
     * Tombstone only the exact state version the caller observed.  Returning
     * the affected-row flag is important: a concurrent cauldron update must
     * not be mistaken for a successful delete, otherwise its ingredients
     * could be refunded a second time.
     */
    public CompletableFuture<Boolean> tombstoneBrewingState(BlockKey key, long expectedVersion) {
        if (key == null || expectedVersion < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid brewing tombstone."));
        }
        return enqueueBrewingWrite(key, () -> runAsyncResult(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE narcotics_brewing_states
                    SET ingredients_csv='',state_payload='',state_version=state_version+1,
                        deleted=TRUE,updated_at=?,owner_uuid=''
                    WHERE world_name=? AND x=? AND y=? AND z=?
                      AND state_version=? AND deleted=FALSE
                    """)) {
                statement.setLong(1, Instant.now().toEpochMilli());
                statement.setString(2, key.world());
                statement.setInt(3, key.x());
                statement.setInt(4, key.y());
                statement.setInt(5, key.z());
                statement.setLong(6, expectedVersion);
                return statement.executeUpdate() == 1;
            }
        })));
    }

    /**
     * Atomically close a finished brew and enqueue its product for the owner.
     * The physical inventory delivery is intentionally performed later on the
     * Bukkit thread; keeping the output row in the same transaction as the
     * tombstone closes the crash window between consuming ingredients and
     * dropping the finished item.
     */
    public CompletableFuture<Boolean> completeBrewingState(BlockKey key, long expectedVersion,
                                                           UUID ownerUuid, String narcoticId) {
        if (key == null || expectedVersion < 0L || narcoticId == null || narcoticId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid brewing completion."));
        }
        String journalKey = brewingCompletionJournalKey(key, expectedVersion, ownerUuid, narcoticId);
        if (!appendBrewingCompletionJournal(key, expectedVersion, ownerUuid, narcoticId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Brewing completion journal is unavailable."));
        }
        return enqueueBrewingWrite(key, () -> runAsyncResult(() -> tx(connection ->
                completeBrewingStateTx(connection, key, expectedVersion, ownerUuid, narcoticId))))
                .whenComplete((applied, error) -> {
            if (error == null && Boolean.TRUE.equals(applied)) {
                removeBrewingCompletionJournal(journalKey);
            }
        });
    }

    /**
     * Persist the completion intent before the final ingredient is removed
     * from a player's inventory.  The intent is keyed by the cauldron and
     * state version, so a retry after an acknowledgement loss is harmless.
     */
    public CompletableFuture<Void> prepareBrewingCompletionIntent(BlockKey key, long expectedVersion,
                                                                   UUID ownerUuid, String narcoticId,
                                                                   String finalIngredient) {
        if (key == null || expectedVersion < 0L || narcoticId == null || narcoticId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid brewing completion intent."));
        }
        if (!appendBrewingCompletionJournal(key, expectedVersion, ownerUuid, narcoticId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Brewing completion journal is unavailable."));
        }
        return enqueueBrewingWrite(key, () -> runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_brewing_completion_intents
                        (intent_id,world_name,x,y,z,state_version,owner_uuid,narcotic_id,final_ingredient,status,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,'PREPARED',?,?)
                    -- Older installations may have this table without a
                    -- location/version unique arbiter. A targeted ON
                    -- CONFLICT then aborts the brew instead of returning a
                    -- product. The per-cauldron Bukkit lock and journal make
                    -- this intent idempotent, so generic DO NOTHING works
                    -- with both old and new schemas.
                    ON CONFLICT DO NOTHING
                    """)) {
                long now = Instant.now().toEpochMilli();
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, key.world());
                statement.setInt(3, key.x());
                statement.setInt(4, key.y());
                statement.setInt(5, key.z());
                statement.setLong(6, expectedVersion);
                statement.setString(7, ownerUuid == null ? "" : ownerUuid.toString());
                statement.setString(8, narcoticId);
                statement.setString(9, finalIngredient == null ? "" : finalIngredient);
                statement.setLong(10, now);
                statement.setLong(11, now);
                statement.executeUpdate();
            }
            return null;
        })));
    }

    /** Cancel a prepared completion when the physical ingredient is no longer present. */
    public CompletableFuture<Boolean> abortBrewingCompletionIntent(BlockKey key, long expectedVersion,
                                                                     UUID ownerUuid, String narcoticId) {
        if (key == null || expectedVersion < 0L || narcoticId == null || narcoticId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid brewing completion abort."));
        }
        String journalKey = brewingCompletionJournalKey(key, expectedVersion, ownerUuid, narcoticId);
        return enqueueBrewingWrite(key, () -> runAsyncResult(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE narcotics_brewing_completion_intents
                    SET status='ABORTED',updated_at=?
                    WHERE world_name=? AND x=? AND y=? AND z=? AND state_version=?
                      AND status='PREPARED'
                    """)) {
                statement.setLong(1, Instant.now().toEpochMilli());
                statement.setString(2, key.world());
                statement.setInt(3, key.x());
                statement.setInt(4, key.y());
                statement.setInt(5, key.z());
                statement.setLong(6, expectedVersion);
                return statement.executeUpdate() > 0;
            }
        }))).whenComplete((aborted, error) -> {
            if (error == null && Boolean.TRUE.equals(aborted)) {
                removeBrewingCompletionJournal(journalKey);
            }
        });
    }

    /** Reserve one unit of a signed stack before an asynchronous state write starts. */
    public CompletableFuture<String> reserveConsumption(UUID playerUuid, String instanceId, String narcoticId) {
        return reserveConsumption(playerUuid, instanceId, narcoticId, 1);
    }

    /** Reserve one unit of a signed stack and remember its pre-consumption quantity.
     * The quantity lets crash recovery distinguish an interrupted removal from
     * a removal that already happened on a stack sharing the same instance id.
     */
    public CompletableFuture<String> reserveConsumption(UUID playerUuid, String instanceId, String narcoticId,
                                                        int quantityBefore) {
        if (playerUuid == null || instanceId == null || instanceId.isBlank()
                || narcoticId == null || narcoticId.isBlank() || quantityBefore < 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid consumption reservation."));
        }
        return runAsyncResult(() -> tx(connection -> {
            long now = Instant.now().getEpochSecond();
            String reservationId = UUID.randomUUID().toString();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO narcotics_consumption_reservations(reservation_id,instance_id,player_uuid,narcotic_id,status,quantity_before,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON CONFLICT (reservation_id) DO NOTHING
                    """)) {
                insert.setString(1, reservationId);
                insert.setString(2, instanceId);
                insert.setString(3, playerUuid.toString());
                insert.setString(4, narcoticId);
                insert.setString(5, "RESERVED");
                insert.setInt(6, quantityBefore);
                insert.setLong(7, now);
                insert.setLong(8, now);
                insert.executeUpdate();
            }
            try (PreparedStatement verifyIssued = connection.prepareStatement("""
                    SELECT narcotic_id,status
                    FROM narcotics_issued_instances
                    WHERE instance_id=?
                    FOR UPDATE
                    """)) {
                verifyIssued.setString(1, instanceId);
                try (ResultSet rs = verifyIssued.executeQuery()) {
                    if (!rs.next()
                            || !narcoticId.equalsIgnoreCase(rs.getString(1))
                            || !"ACTIVE".equalsIgnoreCase(rs.getString(2))) {
                        throw new IllegalStateException("Narcotic instance is not registered or is already consumed.");
                    }
                }
            }
            try (PreparedStatement verify = connection.prepareStatement("""
                    SELECT player_uuid,narcotic_id,status,quantity_before
                    FROM narcotics_consumption_reservations
                    WHERE reservation_id=?
                    FOR UPDATE
                    """)) {
                verify.setString(1, reservationId);
                try (ResultSet rs = verify.executeQuery()) {
                    if (!rs.next()
                            || !playerUuid.toString().equals(rs.getString(1))
                            || !narcoticId.equalsIgnoreCase(rs.getString(2))
                            || !"RESERVED".equalsIgnoreCase(rs.getString(3))
                            || rs.getInt(4) != quantityBefore) {
                        throw new IllegalStateException("Consumption item is already reserved or committed.");
                    }
                }
            }
            return reservationId;
        }));
    }

    public CompletableFuture<List<ConsumptionReservation>> loadConsumptionReservations(UUID playerUuid, int limit) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return supplyAsync(() -> {
            List<ConsumptionReservation> reservations = new ArrayList<>();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                          SELECT reservation_id,instance_id,player_uuid,narcotic_id,status,quantity_before
                         FROM narcotics_consumption_reservations
                         WHERE player_uuid=? AND status IN ('RESERVED','STATE_COMMITTED')
                         ORDER BY created_at ASC
                         LIMIT ?
                         """)) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, Math.max(1, Math.min(limit, 32)));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        reservations.add(new ConsumptionReservation(
                                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                                Math.max(1, rs.getInt(6))));
                    }
                }
            }
            return List.copyOf(reservations);
        });
    }

    public CompletableFuture<Void> completeConsumptionReservation(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return runAsync(() -> tx(connection -> {
            String instanceId = null;
            String narcoticId = null;
            String status = null;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT instance_id,narcotic_id,status
                    FROM narcotics_consumption_reservations
                    WHERE reservation_id=?
                    FOR UPDATE
                    """)) {
                select.setString(1, reservationId);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        instanceId = rs.getString(1);
                        narcoticId = rs.getString(2);
                        status = rs.getString(3);
                    }
                }
            }
            if (instanceId == null || instanceId.isBlank()) {
                return null;
            }
            if (!"STATE_COMMITTED".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Consumption reservation is not committed.");
            }
            if (!instanceId.startsWith(FUNGIBLE_INSTANCE_PREFIX)) {
                try (PreparedStatement consume = connection.prepareStatement("""
                        UPDATE narcotics_issued_instances
                        SET status='CONSUMED',updated_at=?
                        WHERE instance_id=? AND narcotic_id=? AND status='ACTIVE'
                        """)) {
                    consume.setLong(1, Instant.now().getEpochSecond());
                    consume.setString(2, instanceId);
                    consume.setString(3, narcoticId);
                    if (consume.executeUpdate() != 1) {
                        throw new IllegalStateException("Issued legacy narcotic instance is missing or already consumed.");
                    }
                }
            } else {
                try (PreparedStatement verify = connection.prepareStatement("""
                        SELECT 1
                        FROM narcotics_issued_instances
                        WHERE instance_id=? AND narcotic_id=? AND status='ACTIVE'
                        FOR UPDATE
                        """)) {
                    verify.setString(1, instanceId);
                    verify.setString(2, narcoticId);
                    try (ResultSet rs = verify.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Issued fungible narcotic identity is missing or inactive.");
                        }
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM narcotics_consumption_reservations WHERE reservation_id=?")) {
                delete.setString(1, reservationId);
                delete.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> releaseConsumptionReservation(String reservationId) {
        return deleteConsumptionReservation(reservationId);
    }

    private CompletableFuture<Void> deleteConsumptionReservation(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM narcotics_consumption_reservations WHERE reservation_id=?")) {
                statement.setString(1, reservationId);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> resetNarcoticsState() {
        if (!resetBarrier.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Narcotics reset is already in progress."));
        }
        long resetGeneration = writeGeneration.incrementAndGet();
        CompletableFuture<Void> reset = runAsyncInternal(() -> tx(connection -> {
            for (String sql : List.of(
                    "DELETE FROM narcotics_brewing_states",
                    "DELETE FROM narcotics_player_overdose",
                    "DELETE FROM narcotics_player_usage_window",
                    "DELETE FROM narcotics_config_values",
                    "DELETE FROM narcotics_admin_audit",
                    "DELETE FROM narcotics_item_texture_migrations",
                    "DELETE FROM narcotics_pending_refunds",
                    "DELETE FROM narcotics_pending_outputs",
                    "DELETE FROM narcotics_issued_instances",
                    "DELETE FROM narcotics_consumption_reservations"
            )) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                }
            }
            return null;
        }), resetGeneration, true);
        reset.whenComplete((ignored, error) -> {
            if (error == null) {
                // The SQL wipe is durable; only now may local outbox rows be
                // removed.  A failed reset must leave journals replayable.
                clearDurableJournals();
            }
            resetBarrier.set(false);
        });
        return reset;
    }

    public CompletableFuture<Void> queuePendingRefund(UUID playerUuid, String narcoticId, int amount) {
        if (playerUuid == null || narcoticId == null || narcoticId.isBlank() || amount <= 0
                || !narcoticId.startsWith("INGREDIENT:")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Only failed cauldron ingredients may be queued for compensation."));
        }
        return queuePendingRefundRows(playerUuid, List.of(new RefundRow(narcoticId, amount)));
    }

    /**
     * Queue every ingredient from a failed multi-step cauldron mutation in one
     * transaction.  A tombstone removes the persisted state as a whole, so a
     * compensation that only records the last ingredient would permanently
     * lose the earlier ingredients after an acknowledgement failure.
     */
    public CompletableFuture<Void> queuePendingIngredientRefunds(UUID playerUuid, List<IngredientEntry> ingredients) {
        if (playerUuid == null || ingredients == null || ingredients.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("At least one ingredient is required for compensation."));
        }
        List<RefundRow> rows = new ArrayList<>(ingredients.size());
        for (IngredientEntry ingredient : ingredients) {
            if (ingredient == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Ingredient compensation contains a null entry."));
            }
            rows.add(new RefundRow("INGREDIENT:" + ingredient.serialize(), 1));
        }
        return queuePendingRefundRows(playerUuid, rows);
    }

    private record RefundRow(String narcoticId, int amount) {}

    private CompletableFuture<Void> queuePendingRefundRows(UUID playerUuid, List<RefundRow> rows) {
        List<String> refundIds = new ArrayList<>(rows.size());
        for (RefundRow row : rows) {
            refundIds.add(UUID.randomUUID().toString());
        }
        CompletableFuture<Void> persisted = runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO narcotics_pending_refunds(id,player_uuid,narcotic_id,amount,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                long now = Instant.now().getEpochSecond();
                for (int index = 0; index < rows.size(); index++) {
                    RefundRow row = rows.get(index);
                    statement.setString(1, refundIds.get(index));
                    statement.setString(2, playerUuid.toString());
                    statement.setString(3, row.narcoticId());
                    statement.setInt(4, row.amount());
                    statement.setString(5, "PENDING");
                    statement.setLong(6, now);
                    statement.setLong(7, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }));
        persisted.whenComplete((ignored, error) -> {
            if (error != null) {
                for (int index = 0; index < rows.size(); index++) {
                    RefundRow row = rows.get(index);
                    try {
                        appendRefundJournal(refundIds.get(index), playerUuid, row.narcoticId(), row.amount());
                    } catch (Exception journalError) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Unable to persist narcotics refund journal", journalError);
                    }
                }
            }
        });
        return persisted;
    }

    /** Queue an administrator-issued product in the same durable mailbox used
     * by completed brews.  A full inventory must never cause an item to be
     * dropped into the world without an owner or retry record. */
    public CompletableFuture<Void> queuePendingBrewingOutput(UUID playerUuid, String narcoticId, int amount) {
        if (playerUuid == null || narcoticId == null || narcoticId.isBlank() || amount < 1 || amount > 64) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid pending narcotic output."));
        }
        String outputId = UUID.randomUUID().toString();
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO narcotics_pending_outputs(id,player_uuid,narcotic_id,amount,status,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?)
                    """)) {
                long now = Instant.now().getEpochSecond();
                statement.setString(1, outputId);
                statement.setString(2, playerUuid.toString());
                statement.setString(3, narcoticId);
                statement.setInt(4, amount);
                statement.setString(5, "PENDING");
                statement.setLong(6, now);
                statement.setLong(7, now);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<List<PendingRefund>> reservePendingRefunds(UUID playerUuid, int limit) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return runAsyncResult(() -> tx(connection -> {
            List<PendingRefund> result = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            long staleBefore = Instant.now().getEpochSecond() - 60L;
            try (PreparedStatement reset = connection.prepareStatement(
                    "UPDATE narcotics_pending_refunds SET status='PENDING',updated_at=? WHERE player_uuid=? AND status='DELIVERING' AND updated_at<?")) {
                reset.setLong(1, Instant.now().getEpochSecond());
                reset.setString(2, playerUuid.toString());
                reset.setLong(3, staleBefore);
                reset.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id,player_uuid,narcotic_id,amount FROM narcotics_pending_refunds WHERE player_uuid=? AND status='PENDING' ORDER BY created_at ASC LIMIT ? FOR UPDATE")) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, Math.max(1, Math.min(limit, 32)));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(new PendingRefund(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
                        ids.add(rs.getString(1));
                    }
                }
            }
            long now = Instant.now().getEpochSecond();
            for (String id : ids) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE narcotics_pending_refunds SET status='DELIVERING',updated_at=? WHERE id=? AND status='PENDING'")) {
                    update.setLong(1, now);
                    update.setString(2, id);
                    update.executeUpdate();
                }
            }
            return result;
        }));
    }

    public CompletableFuture<Void> completePendingRefund(String id) {
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE narcotics_pending_refunds SET status='DELIVERED',updated_at=? WHERE id=? AND status='DELIVERING'")) {
                statement.setLong(1, Instant.now().getEpochSecond());
                statement.setString(2, id);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> releasePendingRefund(String id) {
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE narcotics_pending_refunds SET status='PENDING',updated_at=? WHERE id=? AND status='DELIVERING'")) {
                statement.setLong(1, Instant.now().getEpochSecond());
                statement.setString(2, id);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    /** Reserve durable brew outputs for one online owner. */
    public CompletableFuture<List<PendingBrewingOutput>> reservePendingBrewingOutputs(UUID playerUuid, int limit) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return runAsyncResult(() -> tx(connection -> {
            List<PendingBrewingOutput> result = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            long now = Instant.now().getEpochSecond();
            long staleBefore = now - 60L;
            try (PreparedStatement reset = connection.prepareStatement(
                    "UPDATE narcotics_pending_outputs SET status='PENDING',updated_at=? WHERE player_uuid=? AND status='DELIVERING' AND updated_at<?")) {
                reset.setLong(1, now);
                reset.setString(2, playerUuid.toString());
                reset.setLong(3, staleBefore);
                reset.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id,player_uuid,narcotic_id,amount FROM narcotics_pending_outputs WHERE player_uuid=? AND status='PENDING' ORDER BY created_at ASC LIMIT ? FOR UPDATE")) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, Math.max(1, Math.min(limit, 32)));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(new PendingBrewingOutput(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
                        ids.add(rs.getString(1));
                    }
                }
            }
            for (String id : ids) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE narcotics_pending_outputs SET status='DELIVERING',updated_at=? WHERE id=? AND status='PENDING'")) {
                    update.setLong(1, now);
                    update.setString(2, id);
                    update.executeUpdate();
                }
            }
            return result;
        }));
    }

    /** Claim a completed brew for physical delivery at its cauldron. */
    public CompletableFuture<Boolean> claimPendingBrewingOutput(String id) {
        if (id == null || id.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return runAsyncResult(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE narcotics_pending_outputs SET status='DELIVERING',updated_at=? WHERE id=? AND status='PENDING'")) {
                statement.setLong(1, Instant.now().getEpochSecond());
                statement.setString(2, id);
                return statement.executeUpdate() == 1;
            }
        }));
    }

    /** Read in-flight rows so a reconnect can recognize an item already added
     * before the previous process crashed, without resetting a live claim. */
    public CompletableFuture<List<PendingBrewingOutput>> loadDeliveringBrewingOutputs(UUID playerUuid, int limit) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return supplyAsync(() -> {
            List<PendingBrewingOutput> result = new ArrayList<>();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT id,player_uuid,narcotic_id,amount FROM narcotics_pending_outputs WHERE player_uuid=? AND status='DELIVERING' ORDER BY created_at ASC LIMIT ?")) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, Math.max(1, Math.min(limit, 32)));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(new PendingBrewingOutput(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
                    }
                }
            }
            return result;
        });
    }

    public CompletableFuture<Void> completePendingBrewingOutput(String id) {
        if (id == null || id.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE narcotics_pending_outputs SET status='DELIVERED',updated_at=? WHERE id=? AND status='DELIVERING'")) {
                statement.setLong(1, Instant.now().getEpochSecond());
                statement.setString(2, id);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> releasePendingBrewingOutput(String id) {
        if (id == null || id.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return runAsync(() -> tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE narcotics_pending_outputs SET status='PENDING',updated_at=? WHERE id=? AND status='DELIVERING'")) {
                statement.setLong(1, Instant.now().getEpochSecond());
                statement.setString(2, id);
                statement.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> auditAsync(String actor, String action, String details) {
        return runAsync(() -> tx(connection -> {
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
        })).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Narcotics audit write failed for action " + action, error);
                try {
                    appendAuditJournal(actor, action, details);
                } catch (Exception journalError) {
                    // Keep the event visible even if the local journal is
                    // unavailable; callers deliberately do not block gameplay
                    // on audit persistence, but an audit must never vanish.
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Narcotics audit fallback journal write failed for action " + action, journalError);
                }
            }
        });
    }

    private void ensureSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(dbSettings.jdbcUrl(), dbSettings.user(), dbSettings.password())) {
            connection.setAutoCommit(false);
            try (Statement schema = connection.createStatement()) {
                schema.execute("CREATE SCHEMA IF NOT EXISTS " + dbSettings.schemaIdent());
                schema.execute("SET search_path TO " + dbSettings.schemaIdent());
            }
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
            connection.commit();
        }
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
                  owner_uuid TEXT NOT NULL DEFAULT '',
                  PRIMARY KEY(world_name,x,y,z)
                )
                """);
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS state_payload TEXT NOT NULL DEFAULT ''");
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS state_version BIGINT NOT NULL DEFAULT 0");
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE");
        sql.add("ALTER TABLE narcotics_brewing_states ADD COLUMN IF NOT EXISTS owner_uuid TEXT NOT NULL DEFAULT ''");
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
                  updated_at BIGINT NOT NULL DEFAULT 0,
                  state_version BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("ALTER TABLE narcotics_player_usage_window ADD COLUMN IF NOT EXISTS state_version BIGINT NOT NULL DEFAULT 0");
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
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_pending_refunds (
                  id TEXT PRIMARY KEY,
                  player_uuid TEXT NOT NULL,
                  narcotic_id TEXT NOT NULL,
                  amount INTEGER NOT NULL,
                  status TEXT NOT NULL DEFAULT 'PENDING',
                  created_at BIGINT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_pending_outputs (
                  id TEXT PRIMARY KEY,
                  player_uuid TEXT NOT NULL,
                  narcotic_id TEXT NOT NULL,
                  amount INTEGER NOT NULL,
                  status TEXT NOT NULL DEFAULT 'PENDING',
                  created_at BIGINT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_brewing_completion_intents (
                  intent_id TEXT PRIMARY KEY,
                  world_name TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  state_version BIGINT NOT NULL,
                  owner_uuid TEXT NOT NULL DEFAULT '',
                  narcotic_id TEXT NOT NULL,
                  final_ingredient TEXT NOT NULL DEFAULT '',
                  status TEXT NOT NULL DEFAULT 'PREPARED',
                  created_at BIGINT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL DEFAULT 0,
                  UNIQUE(world_name,x,y,z,state_version)
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_consumption_reservations (
                  reservation_id TEXT PRIMARY KEY,
                  instance_id TEXT NOT NULL,
                  player_uuid TEXT NOT NULL,
                  narcotic_id TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'RESERVED',
                  quantity_before INTEGER NOT NULL DEFAULT 1,
                  created_at BIGINT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        sql.add("""
                CREATE TABLE IF NOT EXISTS narcotics_issued_instances (
                  instance_id TEXT PRIMARY KEY,
                  narcotic_id TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'ACTIVE',
                  issued_at BIGINT NOT NULL DEFAULT 0,
                  updated_at BIGINT NOT NULL DEFAULT 0
                )
                """);
        // Older releases used instance_id as the primary key, which made a
        // stackable narcotic reservable only once for its entire lifetime.
        // Keep the item identity indexed, but give every consumption its own
        // durable reservation id.
        sql.add("ALTER TABLE narcotics_consumption_reservations ADD COLUMN IF NOT EXISTS reservation_id TEXT");
        sql.add("UPDATE narcotics_consumption_reservations SET reservation_id=instance_id WHERE reservation_id IS NULL OR reservation_id=''");
        sql.add("ALTER TABLE narcotics_consumption_reservations ALTER COLUMN reservation_id SET NOT NULL");
        sql.add("ALTER TABLE narcotics_consumption_reservations DROP CONSTRAINT IF EXISTS narcotics_consumption_reservations_pkey");
        sql.add("ALTER TABLE narcotics_consumption_reservations ADD CONSTRAINT narcotics_consumption_reservations_pkey PRIMARY KEY (reservation_id)");
        sql.add("ALTER TABLE narcotics_consumption_reservations ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'RESERVED'");
        sql.add("ALTER TABLE narcotics_consumption_reservations ADD COLUMN IF NOT EXISTS quantity_before INTEGER NOT NULL DEFAULT 1");
        sql.add("ALTER TABLE narcotics_consumption_reservations ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
        sql.add("ALTER TABLE narcotics_consumption_reservations ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT 0");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_consumption_reservations_player ON narcotics_consumption_reservations(player_uuid,status,created_at)");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_consumption_reservations_instance ON narcotics_consumption_reservations(instance_id,status,created_at)");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_issued_instances_status ON narcotics_issued_instances(status,updated_at)");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_pending_refunds_player ON narcotics_pending_refunds(player_uuid,status,created_at)");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_pending_outputs_player ON narcotics_pending_outputs(player_uuid,status,created_at)");
        sql.add("CREATE INDEX IF NOT EXISTS idx_narcotics_brewing_completion_status ON narcotics_brewing_completion_intents(status,updated_at)");
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
        if (resetBarrier.getAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Narcotics reset is in progress."));
        }
        return runAsyncInternal(work, writeGeneration.getAcquire(), false);
    }

    /**
     * Queue writes for one cauldron behind its previous write.  A failed
     * predecessor is deliberately swallowed for ordering purposes: the next
     * operation still gets a chance to observe/repair the durable row, while
     * its own future reports its actual result to the caller.
     */
    private <T> CompletableFuture<T> enqueueBrewingWrite(BlockKey key, BrewingWriteOperation<T> operation) {
        if (key == null || operation == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Brewing write is invalid."));
        }
        synchronized (brewingWriteTails) {
            CompletableFuture<Void> previous = brewingWriteTails.getOrDefault(key, CompletableFuture.completedFuture(null));
            CompletableFuture<T> next = previous.handle((ignored, error) -> null).thenCompose(ignored -> {
                try {
                    return operation.start();
                } catch (Throwable failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            });
            CompletableFuture<Void> tail = next.handle((ignored, error) -> null);
            brewingWriteTails.put(key, tail);
            tail.whenComplete((ignored, error) -> {
                synchronized (brewingWriteTails) {
                    brewingWriteTails.remove(key, tail);
                }
            });
            return next;
        }
    }

    /** Execute a transactional result-producing write under the same reset
     * generation fence as void writes. */
    private <T> CompletableFuture<T> runAsyncResult(SqlWork<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ExecutorService currentExecutor = executor;
        long generation = writeGeneration.getAcquire();
        if (currentExecutor == null) {
            future.completeExceptionally(new IllegalStateException("Narcotics database executor is unavailable."));
            return future;
        }
        if (resetBarrier.getAcquire()) {
            future.completeExceptionally(new IllegalStateException("Narcotics reset is in progress."));
            return future;
        }
        schemaReady.whenComplete((ready, schemaError) -> {
            if (schemaError != null) {
                future.completeExceptionally(schemaError);
                return;
            }
            if (resetBarrier.getAcquire() || writeGeneration.getAcquire() != generation) {
                future.completeExceptionally(new IllegalStateException("Narcotics write fenced by reset."));
                return;
            }
            try {
                currentExecutor.execute(new QueuedTask(() -> {
                    activeWriteGeneration.put(Thread.currentThread(), generation);
                    try {
                        if (writeGeneration.getAcquire() != generation) {
                            throw new IllegalStateException("Narcotics write fenced by reset.");
                        }
                        future.complete(work.run());
                    } catch (Exception error) {
                        future.completeExceptionally(error);
                    } finally {
                        activeWriteGeneration.remove(Thread.currentThread());
                    }
                }, future));
            } catch (RejectedExecutionException error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private CompletableFuture<Void> runAsyncInternal(SqlVoidWork work, long generation, boolean bypassReset) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null) {
            future.completeExceptionally(new IllegalStateException("Narcotics database executor is unavailable."));
            return future;
        }
        schemaReady.whenComplete((ready, schemaError) -> {
            if (schemaError != null) {
                future.completeExceptionally(schemaError);
                return;
            }
            if (!bypassReset && resetBarrier.getAcquire()) {
                future.completeExceptionally(new IllegalStateException("Narcotics reset is in progress."));
                return;
            }
            try {
                currentExecutor.execute(new QueuedTask(() -> {
                    activeWriteGeneration.put(Thread.currentThread(), generation);
                    try {
                        if (!bypassReset && writeGeneration.getAcquire() != generation) {
                            throw new IllegalStateException("Narcotics write fenced by reset.");
                        }
                        work.run();
                        future.complete(null);
                    } catch (Exception error) {
                        future.completeExceptionally(error);
                    } finally {
                        activeWriteGeneration.remove(Thread.currentThread());
                    }
                }, future));
            } catch (RejectedExecutionException error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private <T> CompletableFuture<T> supplyAsync(SqlWork<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ExecutorService currentExecutor = executor;
        long readGeneration = writeGeneration.getAcquire();
        if (currentExecutor == null) {
            future.completeExceptionally(new IllegalStateException("Narcotics database executor is unavailable."));
            return future;
        }
        schemaReady.whenComplete((ready, schemaError) -> {
            if (schemaError != null) {
                future.completeExceptionally(schemaError);
                return;
            }
            try {
                currentExecutor.execute(new QueuedTask(() -> {
                    try {
                        T result = work.run();
                        if (writeGeneration.getAcquire() != readGeneration) {
                            throw new IllegalStateException("Narcotics read fenced by reset.");
                        }
                        future.complete(result);
                    } catch (Exception error) {
                        future.completeExceptionally(error);
                    }
                }, future));
            } catch (RejectedExecutionException error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private <T> T tx(ConnectionWork<T> work) throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                Long generation = activeWriteGeneration.get(Thread.currentThread());
                if (generation != null && writeGeneration.getAcquire() != generation) {
                    throw new IllegalStateException("Narcotics write fenced by reset.");
                }
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private Connection openConnection() throws Exception {
        Connection raw = acquireConnection();
        try {
            try (Statement statement = raw.createStatement()) {
                statement.execute("SET search_path TO " + dbSettings.schemaIdent());
            }
        } catch (Exception error) {
            releaseConnection(raw);
            throw error;
        }
        AtomicBoolean released = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (released.compareAndSet(false, true)) {
                            releaseConnection(raw);
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return released.getAcquire() || raw.isClosed();
                    }
                    try {
                        return method.invoke(raw, args);
                    } catch (InvocationTargetException targetError) {
                        throw targetError.getCause();
                    }
                });
    }

    private Connection acquireConnection() throws Exception {
        synchronized (poolMonitor) {
            while (true) {
                Connection idle = idleConnections.poll();
                if (idle != null) {
                    if (!idle.isClosed() && idle.isValid(2)) {
                        return idle;
                    }
                    allConnections.remove(idle);
                    createdConnections.decrementAndGet();
                    try {
                        idle.close();
                    } catch (Exception ignored) {
                    }
                }
                if (createdConnections.getAcquire() < maxConnections) {
                    Connection created = DriverManager.getConnection(dbSettings.jdbcUrl(), dbSettings.user(), dbSettings.password());
                    createdConnections.incrementAndGet();
                    allConnections.add(created);
                    return created;
                }
                poolMonitor.wait(5000L);
            }
        }
    }

    private void releaseConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            connection.clearWarnings();
            if (connection.isClosed()) {
                allConnections.remove(connection);
                createdConnections.decrementAndGet();
            } else if (!idleConnections.offer(connection)) {
                allConnections.remove(connection);
                createdConnections.decrementAndGet();
                connection.close();
            }
        } catch (Exception error) {
            allConnections.remove(connection);
            createdConnections.decrementAndGet();
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        } finally {
            synchronized (poolMonitor) {
                poolMonitor.notifyAll();
            }
        }
    }

    private void closePool() {
        for (Connection connection : List.copyOf(allConnections)) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
        idleConnections.clear();
        allConnections.clear();
        createdConnections.set(0);
    }

    private void persistBrewingState(Connection connection, BlockKey key, long version,
                                     List<IngredientEntry> ingredients, UUID ownerUuid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO narcotics_brewing_states(world_name,x,y,z,ingredients_csv,state_payload,state_version,deleted,updated_at,owner_uuid)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (world_name,x,y,z)
                DO UPDATE SET ingredients_csv=EXCLUDED.ingredients_csv,
                              state_payload=EXCLUDED.state_payload,
                              state_version=EXCLUDED.state_version,
                              deleted=EXCLUDED.deleted,
                              updated_at=EXCLUDED.updated_at,
                              owner_uuid=EXCLUDED.owner_uuid
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
            statement.setString(10, ownerUuid == null ? "" : ownerUuid.toString());
            statement.executeUpdate();
        }
    }

    private String brewingJournalKey(BlockKey key, long version) {
        return key.world() + "\t" + key.x() + "\t" + key.y() + "\t" + key.z() + "\t" + version;
    }

    private String brewingCompletionJournalKey(BlockKey key, long expectedVersion,
                                               UUID ownerUuid, String narcoticId) {
        return key.world() + "\t" + key.x() + "\t" + key.y() + "\t" + key.z() + "\t"
                + expectedVersion + "\t" + (ownerUuid == null ? "" : ownerUuid.toString()) + "\t" + narcoticId;
    }

    /** Stable id shared by the durable completion row and its physical drop. */
    public String brewingOutputId(BlockKey key, long expectedVersion, UUID ownerUuid, String narcoticId) {
        if (key == null || expectedVersion < 0L || narcoticId == null || narcoticId.isBlank()) {
            throw new IllegalArgumentException("Invalid brewing output identity.");
        }
        return "brew-" + UUID.nameUUIDFromBytes(
                brewingCompletionJournalKey(key, expectedVersion, ownerUuid, narcoticId)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private boolean appendBrewingJournal(BlockKey key, long version, List<IngredientEntry> ingredients, UUID ownerUuid) {
        if (brewingJournal == null || key == null || version < 0L) {
            return false;
        }
        String line = String.join("\t",
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key.world().getBytes(StandardCharsets.UTF_8)),
                Integer.toString(key.x()), Integer.toString(key.y()), Integer.toString(key.z()), Long.toString(version),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                        (ownerUuid == null ? "" : ownerUuid.toString()).getBytes(StandardCharsets.UTF_8)),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                        joinStatePayload(ingredients).getBytes(StandardCharsets.UTF_8))) + System.lineSeparator();
        synchronized (brewingJournalLock) {
            try {
                if (resetBarrier.getAcquire()) {
                    return false;
                }
                Files.createDirectories(brewingJournal.getParent());
                try (FileChannel channel = FileChannel.open(brewingJournal,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    ByteBuffer buffer = StandardCharsets.UTF_8.encode(line);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                return true;
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Brewing state journal append failed", error);
                return false;
            }
        }
    }

    private void removeBrewingJournal(String journalKey) {
        if (brewingJournal == null || journalKey == null) {
            return;
        }
        synchronized (brewingJournalLock) {
            try {
                if (!Files.isRegularFile(brewingJournal)) {
                    return;
                }
                List<String> keep = new ArrayList<>();
                for (String line : Files.readAllLines(brewingJournal, StandardCharsets.UTF_8)) {
                    String[] fields = line.split("\\t", 7);
                    if (fields.length != 7) {
                        keep.add(line);
                        continue;
                    }
                    String key = new String(java.util.Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8)
                            + "\t" + fields[1] + "\t" + fields[2] + "\t" + fields[3] + "\t" + fields[4];
                    if (!journalKey.equals(key)) {
                        keep.add(line);
                    }
                }
                rewriteBrewingJournal(keep);
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to compact brewing state journal", error);
            }
        }
    }

    private void rewriteBrewingJournal(List<String> lines) throws Exception {
        if (brewingJournal == null) {
            return;
        }
        Files.createDirectories(brewingJournal.getParent());
        Path temporary = Files.createTempFile(brewingJournal.getParent(), brewingJournal.getFileName().toString() + ".", ".tmp");
        try {
            String text = lines == null || lines.isEmpty() ? "" : String.join(System.lineSeparator(), lines) + System.lineSeparator();
            Files.writeString(temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, brewingJournal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private boolean appendBrewingCompletionJournal(BlockKey key, long expectedVersion,
                                                   UUID ownerUuid, String narcoticId) {
        if (brewingCompletionJournal == null || key == null || expectedVersion < 0L
                || narcoticId == null || narcoticId.isBlank()) {
            return false;
        }
        String line = String.join("\t",
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key.world().getBytes(StandardCharsets.UTF_8)),
                Integer.toString(key.x()), Integer.toString(key.y()), Integer.toString(key.z()),
                Long.toString(expectedVersion),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                        (ownerUuid == null ? "" : ownerUuid.toString()).getBytes(StandardCharsets.UTF_8)),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(narcoticId.getBytes(StandardCharsets.UTF_8)))
                + System.lineSeparator();
        synchronized (brewingCompletionJournalLock) {
            try {
                if (resetBarrier.getAcquire()) {
                    return false;
                }
                Files.createDirectories(brewingCompletionJournal.getParent());
                try (FileChannel channel = FileChannel.open(brewingCompletionJournal,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    ByteBuffer buffer = StandardCharsets.UTF_8.encode(line);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                return true;
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Brewing completion journal append failed", error);
                return false;
            }
        }
    }

    private void removeBrewingCompletionJournal(String journalKey) {
        if (brewingCompletionJournal == null || journalKey == null) {
            return;
        }
        synchronized (brewingCompletionJournalLock) {
            try {
                if (!Files.isRegularFile(brewingCompletionJournal)) {
                    return;
                }
                List<String> keep = new ArrayList<>();
                for (String line : Files.readAllLines(brewingCompletionJournal, StandardCharsets.UTF_8)) {
                    String[] fields = line.split("\\t", 7);
                    if (fields.length != 7) {
                        keep.add(line);
                        continue;
                    }
                    String key = new String(java.util.Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8)
                            + "\t" + fields[1] + "\t" + fields[2] + "\t" + fields[3] + "\t" + fields[4]
                            + "\t" + new String(java.util.Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8)
                            + "\t" + new String(java.util.Base64.getUrlDecoder().decode(fields[6]), StandardCharsets.UTF_8);
                    if (!journalKey.equals(key)) {
                        keep.add(line);
                    }
                }
                rewriteBrewingCompletionJournal(keep);
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to compact brewing completion journal", error);
            }
        }
    }

    private void rewriteBrewingCompletionJournal(List<String> lines) throws Exception {
        if (brewingCompletionJournal == null) {
            return;
        }
        Files.createDirectories(brewingCompletionJournal.getParent());
        Path temporary = Files.createTempFile(brewingCompletionJournal.getParent(),
                brewingCompletionJournal.getFileName().toString() + ".", ".tmp");
        try {
            String text = lines == null || lines.isEmpty() ? ""
                    : String.join(System.lineSeparator(), lines) + System.lineSeparator();
            Files.writeString(temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, brewingCompletionJournal,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void clearBrewingJournal() {
        synchronized (brewingJournalLock) {
            try {
                if (brewingJournal != null) {
                    Files.deleteIfExists(brewingJournal);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to clear brewing state journal", error);
            }
        }
    }

    private void clearDurableJournals() {
        clearBrewingJournal();
        synchronized (brewingCompletionJournalLock) {
            try {
                if (brewingCompletionJournal != null) {
                    Files.deleteIfExists(brewingCompletionJournal);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to clear narcotics completion journal after reset", error);
            }
        }
        synchronized (refundJournalLock) {
            try {
                if (refundJournal != null) {
                    Files.deleteIfExists(refundJournal);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to clear narcotics refund journal after reset", error);
            }
        }
        synchronized (auditJournalLock) {
            try {
                if (auditJournal != null) {
                    Files.deleteIfExists(auditJournal);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to clear narcotics audit journal after reset", error);
            }
        }
    }

    private void replayBrewingJournal() {
        synchronized (brewingJournalLock) {
            if (brewingJournal == null || !Files.isRegularFile(brewingJournal)) {
                return;
            }
            List<String> pending;
            try {
                pending = Files.readAllLines(brewingJournal, StandardCharsets.UTF_8);
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to read brewing state journal", error);
                return;
            }
            List<String> remaining = new ArrayList<>();
            for (String line : pending) {
                String[] fields = line.split("\\t", 7);
                if (fields.length != 7) {
                    remaining.add(line);
                    continue;
                }
                try {
                    String world = new String(java.util.Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8);
                    BlockKey key = new BlockKey(world, Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
                    long version = Long.parseLong(fields[4]);
                    String owner = new String(java.util.Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(fields[6]), StandardCharsets.UTF_8);
                    List<IngredientEntry> entries = parseEntriesPayload(payload, "");
                    UUID ownerUuid = owner.isBlank() ? null : UUID.fromString(owner);
                    tx(connection -> {
                        persistBrewingState(connection, key, version, entries, ownerUuid);
                        return null;
                    });
                } catch (Exception error) {
                    remaining.add(line);
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to replay brewing state journal row", error);
                }
            }
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(brewingJournal);
                } else {
                    rewriteBrewingJournal(remaining);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to compact brewing state journal", error);
            }
        }
    }

    private void replayBrewingCompletionJournal() {
        synchronized (brewingCompletionJournalLock) {
            if (brewingCompletionJournal == null || !Files.isRegularFile(brewingCompletionJournal)) {
                return;
            }
            List<String> pending;
            try {
                pending = Files.readAllLines(brewingCompletionJournal, StandardCharsets.UTF_8);
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to read brewing completion journal", error);
                return;
            }
            List<String> remaining = new ArrayList<>();
            for (String line : pending) {
                String[] fields = line.split("\\t", 7);
                if (fields.length != 7) {
                    remaining.add(line);
                    continue;
                }
                try {
                    String world = new String(java.util.Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8);
                    BlockKey key = new BlockKey(world, Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
                    long expectedVersion = Long.parseLong(fields[4]);
                    String owner = new String(java.util.Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
                    String narcoticId = new String(java.util.Base64.getUrlDecoder().decode(fields[6]), StandardCharsets.UTF_8);
                    UUID ownerUuid = owner.isBlank() ? null : UUID.fromString(owner);
                    boolean applied = tx(connection -> completeBrewingStateTx(connection, key, expectedVersion, ownerUuid, narcoticId));
                    if (!applied && !isBrewingCompletionResolved(key, expectedVersion)) {
                        remaining.add(line);
                    }
                } catch (Exception error) {
                    remaining.add(line);
                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "Unable to replay brewing completion journal row", error);
                }
            }
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(brewingCompletionJournal);
                } else {
                    rewriteBrewingCompletionJournal(remaining);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to compact brewing completion journal", error);
            }
        }
    }

    /**
     * Confirm whether a completion CAS was committed when its future was
     * interrupted after the database transaction.  The brewing service uses
     * this acknowledgement-recovery check before retrying a completion.
     */
    public CompletableFuture<Boolean> brewingCompletionResolved(BlockKey key, long expectedVersion) {
        if (key == null || expectedVersion < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid brewing completion lookup."));
        }
        return runAsyncResult(() -> isBrewingCompletionResolved(key, expectedVersion));
    }

    private boolean isBrewingCompletionResolved(BlockKey key, long expectedVersion) throws Exception {
        return tx(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT state_version,deleted FROM narcotics_brewing_states WHERE world_name=? AND x=? AND y=? AND z=?")) {
                statement.setString(1, key.world());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return true;
                    }
                    return rs.getBoolean(2) || rs.getLong(1) > expectedVersion;
                }
            }
        });
    }

    private boolean completeBrewingStateTx(Connection connection, BlockKey key, long expectedVersion,
                                           UUID ownerUuid, String narcoticId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE narcotics_brewing_states
                SET ingredients_csv='',state_payload='',state_version=state_version+1,
                    deleted=TRUE,updated_at=?,owner_uuid=''
                WHERE world_name=? AND x=? AND y=? AND z=?
                  AND state_version=? AND deleted=FALSE
                """)) {
            statement.setLong(1, Instant.now().toEpochMilli());
            statement.setString(2, key.world());
            statement.setInt(3, key.x());
            statement.setInt(4, key.y());
            statement.setInt(5, key.z());
            statement.setLong(6, expectedVersion);
            if (statement.executeUpdate() != 1) {
                return false;
            }
        }
        try (PreparedStatement output = connection.prepareStatement("""
                INSERT INTO narcotics_pending_outputs(id,player_uuid,narcotic_id,amount,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?)
                """)) {
            long now = Instant.now().getEpochSecond();
            output.setString(1, brewingOutputId(key, expectedVersion, ownerUuid, narcoticId));
            // The product is first delivered at the cauldron.  A blank owner
            // keeps the row durable for ownerless/admin-triggered brews while
            // still satisfying the legacy NOT NULL schema.
            output.setString(2, ownerUuid == null ? "" : ownerUuid.toString());
            output.setString(3, narcoticId);
            output.setInt(4, 1);
            output.setString(5, "PENDING");
            output.setLong(6, now);
            output.setLong(7, now);
            output.executeUpdate();
        }
        try (PreparedStatement intent = connection.prepareStatement("""
                UPDATE narcotics_brewing_completion_intents
                SET status='COMMITTED',updated_at=?
                WHERE world_name=? AND x=? AND y=? AND z=? AND state_version=?
                  AND status IN ('PREPARED','CONSUMED')
                """)) {
            intent.setLong(1, Instant.now().toEpochMilli());
            intent.setString(2, key.world());
            intent.setInt(3, key.x());
            intent.setInt(4, key.y());
            intent.setInt(5, key.z());
            intent.setLong(6, expectedVersion);
            intent.executeUpdate();
        }
        return true;
    }

    private void appendRefundJournal(String id, UUID playerUuid, String narcoticId, int amount) throws Exception {
        if (refundJournal == null) {
            throw new IllegalStateException("Refund journal is not initialized.");
        }
        String line = id + "\t" + playerUuid + "\t" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(narcoticId.getBytes(StandardCharsets.UTF_8)) + "\t" + amount
                + System.lineSeparator();
        synchronized (refundJournalLock) {
            Files.writeString(refundJournal, line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(refundJournal,
                    java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }
    }

    private void appendAuditJournal(String actor, String action, String details) throws Exception {
        if (auditJournal == null) {
            throw new IllegalStateException("Audit journal is not initialized.");
        }
        String id = UUID.randomUUID().toString();
        String line = id + "\t" + encodeJournalField(actor) + "\t" + encodeJournalField(action)
                + "\t" + encodeJournalField(details) + "\t" + Instant.now().getEpochSecond()
                + System.lineSeparator();
        synchronized (auditJournalLock) {
            Files.writeString(auditJournal, line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(auditJournal,
                    java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }
    }

    private String encodeJournalField(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                first(value).getBytes(StandardCharsets.UTF_8));
    }

    private String decodeJournalField(String value) {
        return new String(java.util.Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void replayAuditJournal() {
        synchronized (auditJournalLock) {
            if (auditJournal == null || !Files.isRegularFile(auditJournal)) {
                return;
            }
            List<String> pending;
            try {
                pending = Files.readAllLines(auditJournal, StandardCharsets.UTF_8);
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to read narcotics audit journal", error);
                return;
            }
            if (pending.isEmpty()) {
                return;
            }
            List<String> remaining = new ArrayList<>();
            for (String line : pending) {
                String[] fields = line.split("\\t", 5);
                if (fields.length != 5) {
                    remaining.add(line);
                    continue;
                }
                try {
                    long createdAt = Long.parseLong(fields[4].trim());
                    String actor = decodeJournalField(fields[1]);
                    String action = decodeJournalField(fields[2]);
                    String details = decodeJournalField(fields[3]);
                    tx(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                INSERT INTO narcotics_admin_audit(id,actor,action,details,created_at)
                                VALUES (?,?,?,?,?) ON CONFLICT (id) DO NOTHING
                                """)) {
                            statement.setString(1, fields[0]);
                            statement.setString(2, actor);
                            statement.setString(3, action);
                            statement.setString(4, details);
                            statement.setLong(5, createdAt);
                            statement.executeUpdate();
                        }
                        return null;
                    });
                } catch (Exception error) {
                    remaining.add(line);
                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "Unable to replay narcotics audit journal row", error);
                }
            }
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(auditJournal);
                } else {
                    Path temporary = auditJournal.resolveSibling(auditJournal.getFileName() + ".tmp");
                    Files.write(temporary, remaining, StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary,
                            java.nio.file.StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }
                    Files.move(temporary, auditJournal, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to compact narcotics audit journal", error);
            }
        }
    }

    private void replayRefundJournal() {
        synchronized (refundJournalLock) {
            if (refundJournal == null || !Files.isRegularFile(refundJournal)) {
                return;
            }
            List<String> pending;
            try {
                pending = Files.readAllLines(refundJournal, StandardCharsets.UTF_8);
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to read narcotics refund journal", error);
                return;
            }
            if (pending.isEmpty()) {
                return;
            }
            List<String> remaining = new ArrayList<>();
            for (String line : pending) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4) {
                    remaining.add(line);
                    plugin.getLogger().warning("Keeping malformed narcotics refund journal row for manual recovery.");
                    continue;
                }
                try {
                    UUID playerUuid = UUID.fromString(fields[1]);
                    String narcoticId = new String(java.util.Base64.getUrlDecoder().decode(fields[2]), StandardCharsets.UTF_8);
                    int amount = Integer.parseInt(fields[3].trim());
                    tx(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO narcotics_pending_refunds(id,player_uuid,narcotic_id,amount,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING")) {
                            long now = Instant.now().getEpochSecond();
                            statement.setString(1, fields[0]);
                            statement.setString(2, playerUuid.toString());
                            statement.setString(3, narcoticId);
                            statement.setInt(4, amount);
                            statement.setString(5, "PENDING");
                            statement.setLong(6, now);
                            statement.setLong(7, now);
                            statement.executeUpdate();
                        }
                        return null;
                    });
                } catch (Exception error) {
                    remaining.add(line);
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to replay narcotics refund journal row", error);
                }
            }
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(refundJournal);
                } else {
                    Path temporary = refundJournal.resolveSibling(refundJournal.getFileName() + ".tmp");
                    Files.write(temporary, remaining, StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary,
                            java.nio.file.StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }
                    Files.move(temporary, refundJournal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to compact narcotics refund journal", error);
            }
        }
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

    public record LoadedBrewingState(List<IngredientEntry> ingredients, long version, long updatedAtEpochMillis, String ownerUuid) {
    }

    private record DbSettings(String host, int port, String database, String user, String password, String schema, Path envFile) {
        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database + "?currentSchema=" + schema;
        }

        String schemaIdent() {
            return "\"" + schema.replace("\"", "\"\"") + "\"";
        }
    }
}
