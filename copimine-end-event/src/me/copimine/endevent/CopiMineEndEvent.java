package me.copimine.endevent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import me.copimine.artifacts.api.EventArtifactRewardRequest;
import me.copimine.artifacts.api.EventArtifactRewardService;
import me.copimine.artifacts.api.RewardIssueResult;
import me.copimine.endevent.domain.BossThresholdPolicy;
import me.copimine.endevent.domain.CoreDepositMath;
import me.copimine.endevent.domain.CoreInteractionGuard;
import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EndRiftAiPolicy;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.FinalDrainMath;
import me.copimine.endevent.domain.GateOpeningPlan;
import me.copimine.endevent.domain.PadLayout;
import me.copimine.endevent.domain.RewardRoster;
import me.copimine.endevent.domain.ResourceProgressFormatter;
import me.copimine.worldcore.api.WorldAccessResult;
import me.copimine.worldcore.api.WorldAccessService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * First-party, Paper-authoritative End Rift Event.  The class owns gameplay
 * only; WorldCore and Artifacts remain the authorities for End access and
 * official item lifecycle respectively.
 */
public final class CopiMineEndEvent extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String EVENT_KIND_CORE = "CORE";
    private static final String EVENT_KIND_PAD = "PAD";
    private static final String EVENT_KIND_DISPLAY = "DISPLAY";
    private static final String EVENT_KIND_WAVE_MOB = "WAVE_MOB";
    private static final String EVENT_KIND_ELITE = "ELITE";
    private static final String EVENT_KIND_BOSS = "BOSS";
    private static final String EVENT_KIND_FINAL_WAVE = "FINAL_WAVE";
    private static final String EVENT_KIND_PROJECTILE = "RIFT_PROJECTILE";
    private static final String CLIENT_VISUAL_ENDERMAN = "END_RIFT_ENDERMAN_V1";
    private static final String CLIENT_VISUAL_ELITE = "END_RIFT_ELITE_V1";
    private static final String CLIENT_VISUAL_SPIDER = "END_RIFT_SPIDER_V1";
    private static final String CLIENT_VISUAL_SHULKER = "END_RIFT_SHULKER_V1";
    private static final Material EVENT_OVERLAY_ITEM = Material.PAPER;
    private static final int MODEL_CORE_OVERLAY = 830001;
    private static final int MODEL_CORE_CHARGED_OVERLAY = 830002;
    private static final int MODEL_RUNE_OVERLAY = 830003;
    private static final int MODEL_RUNE_OVERLAY_OCCUPIED = 830005;
    private static final double MAX_COMBAT_RADIUS_BLOCKS = 20.0D;
    private static final int DEFAULT_ARENA_PREVIEW_SECONDS = 10;
    private static final int MAX_ARENA_PREVIEW_SECONDS = 300;
    private static final double ARENA_BOUNDARY_STEP = 0.5D;
    private static final long MAX_GATE_VOLUME = 16_384L;
    private static final int DEFAULT_GATE_TICKS_PER_LAYER = 5;
    private static final int MIN_GATE_TICKS_PER_LAYER = 1;
    private static final int MAX_GATE_TICKS_PER_LAYER = 200;
    private static final int DEFAULT_GATE_SELECTION_PREVIEW_SECONDS = 10;
    private static final int VOID_MARK_RADIUS_BLOCKS = 3;
    private static final int VOID_MARK_DURATION_SECONDS = 6;
    private static final int MAX_ACTIVE_VOID_MARKS = 2;
    private static final int MAX_ACTIVE_RIFT_PROJECTILES = 8;
    private static final int SPELL_FLIGHT_TICKS = 8;
    private static final String SPELL_FLIGHT_EFFECT = "spell-flight";
    private static final int RIFT_PROJECTILE_MAX_TICKS = 80;
    private static final double RIFT_PROJECTILE_SPEED = 0.65D;
    private static final int CREATIVE_TEST_MAX_STAGE_TICKS = 100;
    private static final String VICTORY_BOSS_DEATH = "BOSS_DEATH_CONFIRMED";
    private static final String VICTORY_UNLOCK_PENDING = "END_UNLOCK_PENDING";
    private static final String VICTORY_UNLOCKED = "END_UNLOCKED_COMMITTED";
    private static final String VICTORY_REWARDS_PENDING = "REWARDS_PENDING";
    private static final String VICTORY_REWARDS_DELIVERED = "REWARDS_DELIVERED";
    private static final String VICTORY_GATE_OPENING = "GATE_OPENING";
    private static final String VICTORY_GATE_OPENED = "GATE_OPENED";
    private static final String VICTORY_COMPLETE = "VICTORY_COMPLETE";
    private static final String BOSS_REWARDS_PENDING = "PENDING";
    private static final String BOSS_REWARDS_RESERVED = "BOSS_REWARDS_RESERVED";
    private static final String BOSS_REWARDS_DELIVERED = "BOSS_REWARDS_DELIVERED";
    private static final String BOSS_REWARDS_RETRY = "BOSS_REWARDS_PENDING_RETRY";
    private static final String BOSS_REWARDS_REVIEW = "BOSS_REWARDS_REVIEW_REQUIRED";

    private final Random random = new Random();
    private final Map<UUID, Entity> ownedEntities = new HashMap<>();
    private final Map<UUID, Long> controlCooldowns = new HashMap<>();
    private final Map<UUID, Long> controlEnds = new HashMap<>();
    private final Map<UUID, String> controlInstances = new HashMap<>();
    private final Map<UUID, Location> shardChannelStarts = new HashMap<>();
    private final Map<UUID, BukkitTask> shardChannelTasks = new HashMap<>();
    private final Map<String, UUID> padOccupants = new LinkedHashMap<>();
    private final Map<UUID, String> playerCategories = new HashMap<>();
    private final Set<UUID> combatHelpers = new LinkedHashSet<>();
    private final Set<UUID> finalWaveEntities = new HashSet<>();
    private final Set<UUID> lootIssuedEntityUuids = new HashSet<>();
    private final Set<UUID> spellServants = new HashSet<>();
    private final Map<UUID, String> entityBindingInstances = new HashMap<>();
    private final Map<UUID, EndRiftAiPolicy.MiniBossSpell> miniBossSpells = new HashMap<>();
    private final Map<UUID, Long> nextMiniBossSpellMillis = new HashMap<>();
    private final Map<UUID, BukkitTask> activeVoidMarkTasks = new LinkedHashMap<>();
    private final Map<UUID, Location> activeVoidMarkCenters = new LinkedHashMap<>();
    private final Set<UUID> activeRiftProjectiles = new HashSet<>();
    private final Map<UUID, BukkitTask> riftProjectileTasks = new HashMap<>();
    private final Deque<UUID> recentBossTargets = new ArrayDeque<>();
    private final CoreInteractionGuard coreInteractionGuard = new CoreInteractionGuard();

    private EventConfig config;
    private EventStateStore stateStore;
    private EventLayoutStore layoutStore;
    private EventLayoutState layoutState = EventLayoutState.empty();
    private DepositJournal depositJournal;
    private EventSnapshot loadedSnapshot;
    private EndEventStateMachine stateMachine;
    private EventTaskRegistry taskRegistry;
    private ExecutorService stateExecutor;
    private WorldAccessService worldAccessService;
    private EventArtifactRewardService rewardService;
    private BukkitTask bootstrapTask;
    private BukkitTask tickTask;
    private BukkitTask musicLoopTask;
    private BukkitTask finalRitualVisualTask;
    private BukkitTask arenaBoundaryTask;
    private BukkitTask gateSelectionPreviewTask;
    private BukkitTask gateOpeningTask;
    private BukkitTask creativeTestTask;
    private boolean victoryGatePending;
    private boolean bootstrapped;

    private String eventId = "";
    private long generation;
    private EventPhase phase = EventPhase.UNCONFIGURED;
    private String worldName = "";
    private int coreX;
    private int coreY;
    private int coreZ;
    private String coreBlockData = "";
    private int requiredPlayers;
    private int arenaMinX;
    private int arenaMinY;
    private int arenaMinZ;
    private int arenaMaxX;
    private int arenaMaxY;
    private int arenaMaxZ;
    private final Map<String, Integer> resourceRequirements = new LinkedHashMap<>();
    private final Map<String, Integer> depositedResources = new LinkedHashMap<>();
    private final List<EventSnapshot.PadSnapshot> pads = new ArrayList<>();
    private final Set<UUID> resourceContributors = new LinkedHashSet<>();
    private final Set<UUID> participantUuids = new LinkedHashSet<>();
    private final Set<UUID> officialRewardRoster = new LinkedHashSet<>();
    private final Map<UUID, String> rewardStatuses = new LinkedHashMap<>();
    private final Set<UUID> rewardRequestsInFlight = new HashSet<>();
    private final Map<UUID, Long> shardCooldowns = new LinkedHashMap<>();
    private boolean coreCharged;
    private boolean halfHealthTriggered;
    private boolean controlSpellUnlocked;
    private boolean finalDrainTriggered;
    private boolean finalDrainApplied;
    private final Map<UUID, Double> finalDrainTargets = new LinkedHashMap<>();
    private final Set<UUID> finalDrainAppliedPlayers = new LinkedHashSet<>();
    private boolean endUnlocked;
    private boolean officialBossDeathCommitted;
    private boolean bossLootCommitted;
    private String bossRewardStatus = BOSS_REWARDS_PENDING;
    private UUID bossRewardRecipientUuid;
    private String returnStoneStatus = "PENDING";
    private String victoryStep = "NONE";
    private String recoveryReason = "";
    private long updatedAt;
    private int activeWave;
    private long phaseDeadlineMillis;
    private long nextTargetMillis;
    private long nextSpellMillis;
    private long nextWaveTargetMillis;
    private long bossSpellPauseUntilMillis;
    private long lastBossTeleportMillis;
    private long nextRitualVisualRepairMillis;
    private int bossTargetCursor;
    private int bossSpellCursor;
    private int waveTargetCursor;
    private EndRiftAiPolicy.BossSpell previousBossSpell;
    private boolean servantsSummonedAt70;
    private boolean servantsSummonedAt35;
    /**
     * Explicitly non-persistent local verification mode.  It reuses the
     * production AI controllers while keeping the durable official phase,
     * roster, rewards, and End unlock saga untouched.
     */
    private boolean testCombatAiMode;
    private UUID creativeTestPlayerUuid;
    private long creativeTestGeneration;
    private int creativeTestStage;
    private int creativeTestStageTicks;
    private long nextVictoryRetryMillis;
    private UUID bossUuid;
    private UUID bossKillerUuid;
    private String bossBindingInstanceId = "";
    private String activeMusicTrackId = "";
    private BossBar bossBar;

    private NamespacedKey keyEventId;
    private NamespacedKey keyGeneration;
    private NamespacedKey keyKind;
    private NamespacedKey keyWave;
    private NamespacedKey keyEventSessionId;
    private NamespacedKey keyEventRole;
    private NamespacedKey keyEventWave;
    private NamespacedKey keyEventGeneration;
    private NamespacedKey keyLootProfile;
    private NamespacedKey keyOfficial;
    private NamespacedKey keyBossTest;
    private NamespacedKey keyMiniBossSpell;
    private NamespacedKey keyArtifactItemId;
    private NamespacedKey keyArtifactUniqueId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            config = EventConfig.load(this);
            if ("production".equalsIgnoreCase(config.environment())) {
                getLogger().severe("End Rift Event refuses to start with environment=production; use an explicit local/staging server.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            stateExecutor = new ThreadPoolExecutor(
                    1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32),
                    new ThreadPoolExecutor.AbortPolicy());
            stateStore = new EventStateStore(
                    getDataFolder().toPath(), config.stateFile(), config.backupStateFile(), config.schemaVersion());
            layoutStore = new EventLayoutStore(getDataFolder().toPath());
            layoutState = layoutStore.load();
            depositJournal = new DepositJournal(getDataFolder().toPath());
            loadedSnapshot = stateStore.load().snapshot();
            applySnapshot(loadedSnapshot);
            if (layoutState.arenaPos1() != null && layoutState.arenaPos2() != null) {
                applyArenaBoundsFromLayout();
            }
            keyEventId = new NamespacedKey(this, "end_event_id");
            keyGeneration = new NamespacedKey(this, "end_event_generation");
            keyKind = new NamespacedKey(this, "end_event_kind");
            keyWave = new NamespacedKey(this, "end_event_wave");
            keyEventSessionId = new NamespacedKey(this, "event_session_id");
            keyEventRole = new NamespacedKey(this, "event_role");
            keyEventWave = new NamespacedKey(this, "event_wave");
            keyEventGeneration = new NamespacedKey(this, "event_generation");
            keyLootProfile = new NamespacedKey(this, "end_event_loot_profile");
            keyOfficial = new NamespacedKey(this, "end_event_official");
            keyBossTest = new NamespacedKey(this, "end_event_test_boss");
            keyMiniBossSpell = new NamespacedKey(this, "end_event_miniboss_spell");
            keyArtifactItemId = new NamespacedKey("copimineartifacts", "artifact_item_id");
            keyArtifactUniqueId = new NamespacedKey("copimineartifacts", "artifact_unique_item_id");
            registerCommandsAndListeners();
            bootstrapTask = Bukkit.getScheduler().runTaskTimer(this, this::tryBootstrap, 1L, 20L);
            getLogger().info("CopiMineEndEvent loaded in " + config.environment() + " mode; persistent phase=" + phase);
        } catch (RuntimeException error) {
            getLogger().log(Level.SEVERE, "CopiMineEndEvent failed closed during configuration/state load", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommandsAndListeners() {
        PluginCommand command = getCommand("cmend");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void tryBootstrap() {
        if (bootstrapped || !isEnabled()) {
            return;
        }
        worldAccessService = Bukkit.getServicesManager().load(WorldAccessService.class);
        rewardService = Bukkit.getServicesManager().load(EventArtifactRewardService.class);
        if (worldAccessService == null || rewardService == null) {
            return;
        }
        bootstrapped = true;
        getServer().getMessenger().registerOutgoingPluginChannel(this, config.bridgeChannel());
        if (bootstrapTask != null) {
            bootstrapTask.cancel();
            bootstrapTask = null;
        }
        taskRegistry = new EventTaskRegistry(Math.max(1L, generation));
        restorePersistedGateIfNeeded();
        boolean configuredEventInProgress = isConfigured()
                && phase != EventPhase.UNCONFIGURED
                && phase != EventPhase.UNLOCKED
                && !VICTORY_COMPLETE.equals(victoryStep);
        if (worldAccessService.isEndEnabled()) {
            endUnlocked = true;
            if (configuredEventInProgress) {
                getLogger().info("WorldCore already reports End unlocked; preserving active event event=" + eventId
                        + " phase=" + phase);
            } else if (!isConfigured()) {
                EventLayoutState previousLayout = layoutState;
                layoutState = new EventLayoutState(null, null, null, null, Map.of(), "UNSET", previousLayout.portalRoom());
                victoryStep = VICTORY_COMPLETE;
                forcePhase(EventPhase.UNCONFIGURED, "WorldCore already reports End unlocked without Core");
                saveStateSync();
            } else {
                victoryStep = VICTORY_COMPLETE;
                forcePhase(EventPhase.UNLOCKED, "WorldCore already reports End unlocked");
            }
        } else if (phase != EventPhase.UNCONFIGURED && EndEventStateMachine.recoveryPhase(phase) != phase) {
            recoverTransientSession();
        } else if (phase == EventPhase.RECOVERY_REQUIRED) {
            getLogger().severe("End Rift state requires recovery; no gameplay session will start until an admin resets/rebuilds it.");
        }
        rebuildPersistedVisuals();
        recoverUnresolvedDeposits();
        resumeVictorySaga();
        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 5L);
        playEventMusic(musicForPhase());
        getLogger().info("CopiMineEndEvent services ready; phase=" + phase + " event=" + eventId);
    }

    private void applySnapshot(EventSnapshot snapshot) {
        phase = snapshot.eventPhase();
        if (!snapshot.recoveryReason().isBlank() && !snapshot.configured()) {
            phase = EventPhase.RECOVERY_REQUIRED;
            recoveryReason = snapshot.recoveryReason();
        }
        stateMachine = new EndEventStateMachine(phase);
        eventId = snapshot.eventId();
        generation = snapshot.generation();
        worldName = snapshot.worldName();
        coreX = snapshot.coreX();
        coreY = snapshot.coreY();
        coreZ = snapshot.coreZ();
        coreBlockData = snapshot.coreBlockData();
        requiredPlayers = snapshot.requiredPlayers();
        arenaMinX = snapshot.arenaMinX();
        arenaMinY = snapshot.arenaMinY();
        arenaMinZ = snapshot.arenaMinZ();
        arenaMaxX = snapshot.arenaMaxX();
        arenaMaxY = snapshot.arenaMaxY();
        arenaMaxZ = snapshot.arenaMaxZ();
        resourceRequirements.clear();
        resourceRequirements.putAll(snapshot.resourceRequirements().isEmpty()
                ? config.resourceRequirements() : snapshot.resourceRequirements());
        depositedResources.clear();
        depositedResources.putAll(resourceRequirements);
        depositedResources.replaceAll((key, ignored) -> snapshot.depositedResources().getOrDefault(key, 0));
        pads.clear();
        pads.addAll(snapshot.pads());
        participantUuids.clear();
        participantUuids.addAll(snapshot.participants());
        resourceContributors.addAll(snapshot.resourceContributors());
        participantUuids.addAll(snapshot.resourceContributors());
        officialRewardRoster.addAll(snapshot.officialRewardRoster());
        participantUuids.addAll(snapshot.officialRewardRoster());
        rewardStatuses.putAll(snapshot.rewardStatuses());
        shardCooldowns.putAll(snapshot.shardCooldowns());
        finalDrainTargets.clear();
        finalDrainTargets.putAll(snapshot.finalDrainTargets());
        finalDrainAppliedPlayers.clear();
        finalDrainAppliedPlayers.addAll(snapshot.finalDrainAppliedPlayers());
        coreCharged = snapshot.coreCharged();
        halfHealthTriggered = snapshot.halfHealthTriggered();
        controlSpellUnlocked = snapshot.controlSpellUnlocked();
        finalDrainTriggered = snapshot.finalDrainTriggered();
        finalDrainApplied = snapshot.finalDrainApplied();
        endUnlocked = snapshot.endUnlocked();
        officialBossDeathCommitted = snapshot.officialBossDeathCommitted();
        bossRewardStatus = snapshot.bossRewardStatus();
        bossRewardRecipientUuid = snapshot.bossRewardRecipient();
        if (snapshot.bossLootCommitted() && BOSS_REWARDS_PENDING.equals(bossRewardStatus)) {
            // Backward-compatible read of snapshots written before the
            // explicit boss reward saga was introduced.
            bossRewardStatus = BOSS_REWARDS_DELIVERED;
        }
        bossLootCommitted = snapshot.bossLootCommitted()
                || BOSS_REWARDS_DELIVERED.equals(bossRewardStatus);
        returnStoneStatus = snapshot.returnStoneStatus();
        victoryStep = snapshot.victoryStep();
        updatedAt = snapshot.updatedAt();
    }

    private EventSnapshot snapshot() {
        return new EventSnapshot(
                config.schemaVersion(), eventId, generation, phase.name(), worldName,
                coreX, coreY, coreZ, coreBlockData, requiredPlayers,
                arenaMinX, arenaMinY, arenaMinZ, arenaMaxX, arenaMaxY, arenaMaxZ,
                resourceRequirements, depositedResources, pads, resourceContributors,
                officialRewardRoster, rewardStatuses, shardCooldowns, coreCharged,
                halfHealthTriggered, controlSpellUnlocked, finalDrainTriggered, finalDrainApplied,
                endUnlocked, officialBossDeathCommitted, bossLootCommitted, bossRewardStatus,
                bossRewardRecipientUuid, returnStoneStatus, victoryStep, updatedAt, recoveryReason,
                participantUuids, finalDrainTargets,
                finalDrainAppliedPlayers);
    }

    private boolean saveStateSync() {
        updatedAt = Instant.now().getEpochSecond();
        return stateStore != null && layoutStore != null
                && stateStore.save(snapshot()) && layoutStore.save(layoutState);
    }

    private void saveStateAsync() {
        updatedAt = Instant.now().getEpochSecond();
        if (stateStore == null || stateExecutor == null) {
            return;
        }
        try {
            stateStore.saveAsync(snapshot(), stateExecutor);
        } catch (RuntimeException error) {
            getLogger().log(Level.WARNING, "End event state queue rejected a save", error);
        }
    }

    private boolean transition(EventPhase next, String reason, String idempotencyKey) {
        return transition(next, reason, idempotencyKey, true);
    }

    private boolean transition(EventPhase next, String reason, String idempotencyKey, boolean persist) {
        EventPhase current = stateMachine.phase();
        if (current == next) {
            return true;
        }
        EndEventStateMachine.TransitionResult result = stateMachine.transition(
                current, next, reason, idempotencyKey);
        if (!result.success()) {
            getLogger().warning("Rejected End Event transition " + current + " -> " + next + " code=" + result.code());
            return false;
        }
        phase = next;
        getLogger().info("END_EVENT_STATE event=" + eventId + " generation=" + generation
                + " from=" + current + " to=" + next + " reason=" + reason);
        if (persist) {
            saveStateAsync();
        }
        return true;
    }

    private void forcePhase(EventPhase next, String reason) {
        phase = next;
        stateMachine = new EndEventStateMachine(next);
        if (next == EventPhase.UNLOCKED) {
            releaseOverlayChunkTickets();
        }
        getLogger().info("END_EVENT_STATE forced=" + next + " reason=" + reason);
        saveStateAsync();
    }

    private void recoverTransientSession() {
        getLogger().info("RECOVERY_STARTED event=" + eventId + " generation=" + generation);
        long staleGeneration = generation;
        cancelSessionTasks();
        cleanupOwnedEntities(eventId, staleGeneration);
        clearClientEffects();
        generation = Math.max(1L, staleGeneration + 1L);
        taskRegistry = new EventTaskRegistry(generation);
        activeWave = 0;
        bossUuid = null;
        bossKillerUuid = null;
        clearCombatAiState();
        lootIssuedEntityUuids.clear();
        halfHealthTriggered = false;
        controlSpellUnlocked = false;
        finalDrainTriggered = false;
        finalDrainApplied = false;
        boolean rosterWasCommitted = !officialRewardRoster.isEmpty();
        boolean victoryWasCommitted = officialBossDeathCommitted
                || VICTORY_BOSS_DEATH.equals(victoryStep)
                || VICTORY_UNLOCK_PENDING.equals(victoryStep)
                || VICTORY_UNLOCKED.equals(victoryStep)
                || VICTORY_REWARDS_PENDING.equals(victoryStep)
                || VICTORY_REWARDS_DELIVERED.equals(victoryStep);
        if (!rosterWasCommitted) {
            discardUncommittedRoster();
        }
        if (!victoryWasCommitted) {
            discardUncommittedRewardStatuses();
        }
        padOccupants.clear();
        if (!isConfigured()) {
            EventLayoutState previousLayout = layoutState;
            layoutState = new EventLayoutState(null, null, null, null, Map.of(), "UNSET", previousLayout.portalRoom());
            forcePhase(EventPhase.UNCONFIGURED, "unconfigured session recovered without combat");
            saveStateSync();
        } else if (victoryWasCommitted) {
            forcePhase(endUnlocked ? EventPhase.UNLOCKED : EventPhase.VICTORY_PROCESSING,
                    "victory saga recovered without replaying combat");
        } else if (coreCharged && allResourcesComplete()) {
            forcePhase(EventPhase.READY_FOR_PLAYERS, "transient combat recovered to ready");
        } else {
            coreCharged = false;
            forcePhase(EventPhase.COLLECTING, "transient combat recovered to collecting");
        }
        getLogger().info("RECOVERY_COMPLETE event=" + eventId + " generation=" + generation);
    }

    private void cancelSessionTasks() {
        cancelCreativeTestTask();
        if (taskRegistry != null) {
            taskRegistry.cancelAll();
        }
        cancelArenaBoundaryPreview();
        cancelGateSelectionPreview();
        cancelGateOpeningTask();
        if (finalRitualVisualTask != null) {
            finalRitualVisualTask.cancel();
            finalRitualVisualTask = null;
        }
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        for (BukkitTask task : shardChannelTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        shardChannelTasks.clear();
        stopEventMusic();
        phaseDeadlineMillis = 0L;
        nextWaveTargetMillis = 0L;
        bossSpellPauseUntilMillis = 0L;
    }

    private void cancelCreativeTestTask() {
        if (creativeTestTask != null) {
            creativeTestTask.cancel();
            creativeTestTask = null;
        }
        creativeTestPlayerUuid = null;
        creativeTestGeneration = 0L;
        creativeTestStage = 0;
        creativeTestStageTicks = 0;
    }

    private void discardUncommittedRoster() {
        officialRewardRoster.clear();
    }

    private void discardUncommittedRewardStatuses() {
        rewardStatuses.clear();
    }

    private void clearCombatAiState() {
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        testCombatAiMode = false;
        miniBossSpells.clear();
        nextMiniBossSpellMillis.clear();
        combatHelpers.clear();
        recentBossTargets.clear();
        previousBossSpell = null;
        bossTargetCursor = 0;
        bossSpellCursor = 0;
        waveTargetCursor = 0;
        nextTargetMillis = 0L;
        nextSpellMillis = 0L;
        nextWaveTargetMillis = 0L;
        bossSpellPauseUntilMillis = 0L;
        lastBossTeleportMillis = 0L;
        servantsSummonedAt70 = false;
        servantsSummonedAt35 = false;
    }

    private void clearClientEffects() {
        stopEventMusic();
        for (UUID playerUuid : new HashSet<>(controlInstances.keySet())) {
            sendControlStop(Bukkit.getPlayer(playerUuid), controlInstances.get(playerUuid));
        }
        if (!bossBindingInstanceId.isBlank()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendClientPacket(player, "END_BOSS_UNBIND", bossBindingInstanceId, 0L, "");
            }
        }
        for (UUID entityUuid : new HashSet<>(entityBindingInstances.keySet())) {
            unbindEventEntityClient(entityUuid);
        }
        controlEnds.clear();
        controlInstances.clear();
        bossBindingInstanceId = "";
    }

    private void clearClientEffects(Player player) {
        if (player == null) {
            return;
        }
        stopEventMusic(player);
        stopControl(player.getUniqueId());
        if (!bossBindingInstanceId.isBlank()) {
            sendClientPacket(player, "END_BOSS_UNBIND", bossBindingInstanceId, 0L, "");
        }
        for (UUID entityUuid : entityBindingInstances.keySet()) {
            String instance = entityBindingInstances.get(entityUuid);
            sendClientPacket(player, "END_ENTITY_UNBIND", instance, 0L, entityUuid.toString(), "");
        }
    }

    private EventConfig.MusicTrack musicForPhase() {
        return switch (phase) {
            case WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3 -> config.wavesMusic();
            case BOSS_ACTIVE -> halfHealthTriggered ? config.bossHalfMusic() : config.bossMusic();
            case FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH -> config.bossFinalMusic();
            default -> null;
        };
    }

    private void playEventMusic(EventConfig.MusicTrack track) {
        if (track == null || track.soundId().isBlank()) {
            return;
        }
        if (track.soundId().equals(activeMusicTrackId)
                && musicLoopTask != null && !musicLoopTask.isCancelled()) {
            return;
        }
        stopEventMusic();
        for (Player player : activeLivingPlayers()) {
            player.playSound(player.getLocation(), track.soundId(), SoundCategory.MUSIC,
                    (float) config.musicVolume(), 1.0F);
        }
        activeMusicTrackId = track.soundId();
        if (track.loopSeconds() > 0) {
            long period = Math.max(20L, track.loopSeconds() * 20L);
            musicLoopTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (!isCombatPhase()) {
                    stopEventMusic();
                    return;
                }
                for (Player player : activeLivingPlayers()) {
                    player.playSound(player.getLocation(), track.soundId(), SoundCategory.MUSIC,
                            (float) config.musicVolume(), 1.0F);
                }
            }, period, period);
        }
        getLogger().info("END_EVENT_MUSIC track=" + track.soundId() + " phase=" + phase
                + " loopSeconds=" + track.loopSeconds());
    }

    private void stopEventMusic() {
        if (musicLoopTask != null) {
            musicLoopTask.cancel();
            musicLoopTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopEventMusic(player);
        }
        activeMusicTrackId = "";
    }

    private void stopEventMusic(Player player) {
        if (player == null || config == null) {
            return;
        }
        for (EventConfig.MusicTrack track : List.of(
                config.wavesMusic(), config.bossMusic(), config.bossHalfMusic(),
                config.bossFinalMusic(), config.victoryMusic())) {
            player.stopSound(track.soundId(), SoundCategory.MUSIC);
        }
    }

    private void syncEventMusic(Player player) {
        EventConfig.MusicTrack track = musicForPhase();
        if (track == null || player == null || !isActiveArenaParticipant(player)) {
            return;
        }
        if (activeMusicTrackId.isBlank()) {
            playEventMusic(track);
            return;
        }
        stopEventMusic(player);
        player.playSound(player.getLocation(), track.soundId(), SoundCategory.MUSIC,
                (float) config.musicVolume(), 1.0F);
    }

    private void playTestMusic(Player player, String requested) {
        EventConfig.MusicTrack track = switch (requested.toLowerCase(Locale.ROOT)) {
            case "waves", "wave" -> config.wavesMusic();
            case "boss" -> config.bossMusic();
            case "half", "boss-half" -> config.bossHalfMusic();
            case "final", "boss-final" -> config.bossFinalMusic();
            case "victory" -> config.victoryMusic();
            default -> null;
        };
        if (track == null) {
            message(player, "&e/cmend test music <waves|boss|half|final|victory>");
            return;
        }
        stopEventMusic(player);
        player.playSound(player.getLocation(), track.soundId(), SoundCategory.MUSIC,
                (float) config.musicVolume(), 1.0F);
        getLogger().info("END_EVENT_MUSIC_TEST player=" + player.getName()
                + " track=" + track.soundId() + " loopSeconds=" + track.loopSeconds());
        message(player, "&aЛокальная музыкальная проверка: &f" + track.soundId());
    }

    @Override
    public void onDisable() {
        clearClientEffects();
        cancelSessionTasks();
        releaseOverlayChunkTickets();
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (bootstrapTask != null) {
            bootstrapTask.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        if (stateStore != null) {
            saveStateSync();
        }
        if (stateExecutor != null) {
            stateExecutor.shutdown();
            try {
                stateExecutor.awaitTermination(2L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            stateExecutor.shutdownNow();
        }
        if (config != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, config.bridgeChannel());
        }
    }

    private boolean isAdmin(CommandSender sender) {
        return sender != null && sender.hasPermission("copimine.endevent.admin");
    }

    private void message(CommandSender sender, String text) {
        if (sender != null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    private Player playerSender(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        message(sender, "&cЭта операция доступна только игроку.");
        return null;
    }

    private boolean validAdmin(CommandSender sender) {
        if (!isAdmin(sender)) {
            message(sender, "&cНедостаточно прав.");
            return false;
        }
        return true;
    }

    private boolean validTest(CommandSender sender) {
        if (sender == null || !sender.hasPermission("copimine.endevent.test")) {
            message(sender, "&cНедостаточно прав для локального теста.");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"cmend".equalsIgnoreCase(command.getName())) {
            return false;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            handleStatus(sender);
            return true;
        }
        String group = args[0].toLowerCase(Locale.ROOT);
        if ("test".equals(group)) {
            if (validTest(sender)) {
                handleTest(sender, args);
            }
            return true;
        }
        if (!validAdmin(sender)) {
            return true;
        }
        switch (group) {
            case "core" -> handleCore(sender, args);
            case "arena", "gate", "portalroom" -> handleLayout(sender, group, args);
            case "resources" -> handleResources(sender, args);
            case "ritual" -> handleRitual(sender, args);
            case "cleanup" -> {
                if (confirmed(args, 1)) {
                    cleanupOwnedEntities(eventId, generation);
                    clearClientEffects();
                    message(sender, "&aУдалены только event-owned entities текущей сессии.");
                } else {
                    message(sender, "&cПовтори /cmend cleanup confirm.");
                }
            }
            case "reset" -> handleReset(sender, args);
            case "unlock" -> {
                if (confirmed(args, 1)) {
                    unlockEnd(sender, "admin-unlock");
                } else {
                    message(sender, "&cПовтори /cmend unlock confirm.");
                }
            }
            case "debug", "recovery" -> handleStatus(sender);
            case "wave" -> handleWave(sender, args);
            case "boss" -> handleBoss(sender, args);
            case "client" -> handleClient(sender, args);
            default -> message(sender, "&eИспользование: /cmend status|debug|recovery|core|arena|gate|portalroom|resources|ritual|wave|boss|client|test|cleanup|reset|unlock");
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        message(sender, "&6End Rift Event");
        message(sender, "&7state=&f" + phase + " &7event=&f" + eventId + " &7generation=&f" + generation);
        message(sender, "&7core=&f" + coreLocationText() + " &7requiredPlayers=&f" + requiredPlayers);
        message(sender, "&7arena=&f" + arenaBoundsText() + " &7gate=&f" + layoutState.gateStatus()
                + " &7portal=&f" + pointText(layoutState.portalRoom() == null ? null
                : new EventLayoutState.Point(layoutState.portalRoom().world(),
                (int) layoutState.portalRoom().x(), (int) layoutState.portalRoom().y(), (int) layoutState.portalRoom().z())));
        message(sender, "&7resources=&f" + resourceProgressText());
        message(sender, "&7pads=&f" + padOccupants.size() + "/" + pads.size()
                + " &7roster=&f" + officialRewardRoster.size()
                + " &7participants=&f" + participantUuids.size()
                + " &7helpers=&f" + combatHelpers.size());
        message(sender, "&7visuals=&f" + visualStatusText());
        message(sender, "&7wave=&f" + activeWave + " &7event-mobs=&f" + countLiveOwnedMobs()
                + " &7boss=&f" + (bossUuid == null ? "none" : bossUuid));
        message(sender, "&7half=&f" + halfHealthTriggered + " &7final=&f" + finalDrainTriggered
                + " &7endUnlocked=&f" + endUnlocked + " &7victory=&f" + victoryStep);
        if (!recoveryReason.isBlank()) {
            message(sender, "&cRecovery reason: " + recoveryReason);
        }
    }

    private void handleCore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend core set <N> | info | rebuild | remove confirm");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                Player player = playerSender(sender);
                if (player != null && args.length >= 3) {
                    try {
                        setCore(player, Integer.parseInt(args[2]));
                    } catch (NumberFormatException invalid) {
                        message(sender, "&cКоличество игроков должно быть числом.");
                    }
                }
            }
            case "info" -> handleStatus(sender);
            case "rebuild" -> {
                if (!isConfigured()) {
                    message(sender, "&cCore ещё не настроен.");
                } else {
                    rebuildPersistedVisuals();
                    message(sender, "&aCore и сохранённые руны пересобраны без сброса ресурсов.");
                }
            }
            case "remove" -> {
                if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
                    message(sender, "&cОпасная операция: повтори /cmend core remove confirm.");
                } else {
                    removeCore(sender);
                }
            }
            default -> message(sender, "&e/cmend core set <N> | info | rebuild | remove confirm");
        }
    }

    private void handleLayout(CommandSender sender, String group, String[] args) {
        if ("portalroom".equals(group)) {
            if (args.length > 1 && "info".equalsIgnoreCase(args[1])) {
                EventLayoutState.Portal portal = portalRoom();
                message(sender, "&7portalroom=&f" + portal.world() + " "
                        + portal.x() + "," + portal.y() + "," + portal.z()
                        + " &7source=&f" + (layoutState.portalRoom() == null ? "config" : "event-layout"));
            } else if (args.length > 1 && "set".equalsIgnoreCase(args[1])) {
                Player player = playerSender(sender);
                if (player != null) {
                    EventLayoutState previous = layoutState;
                    Location location = player.getLocation();
                    layoutState = new EventLayoutState(
                            previous.arenaPos1(), previous.arenaPos2(), previous.gatePos1(), previous.gatePos2(),
                            previous.gateSnapshot(), previous.gateStatus(),
                            new EventLayoutState.Portal(location.getWorld().getName(), location.getX(), location.getY(),
                                    location.getZ(), location.getYaw(), location.getPitch()));
                    if (saveStateSync()) {
                        message(sender, "&aПортальная комната сохранена: &f" + locationText(location));
                    } else {
                        layoutState = previous;
                        message(sender, "&cПозиция не сохранена durable; изменение отменено.");
                    }
                }
            } else {
                message(sender, "&e/cmend portalroom set | info");
            }
            return;
        }
        if (!isConfigured()) {
            message(sender, "&cСначала настрой Core.");
            return;
        }
        if ("arena".equals(group)) {
            if (args.length > 1 && ("border".equalsIgnoreCase(args[1])
                    || "boundary".equalsIgnoreCase(args[1]))) {
                int seconds = args.length > 2
                        ? parseInt(args, 2, Integer.MIN_VALUE)
                        : DEFAULT_ARENA_PREVIEW_SECONDS;
                if (seconds == Integer.MIN_VALUE) {
                    message(sender, "&cИспользование: /cmend arena border <seconds>.");
                } else {
                    showArenaBoundary(sender, seconds);
                }
            } else if (args.length > 1 && ("pos1".equalsIgnoreCase(args[1]) || "pos2".equalsIgnoreCase(args[1]))) {
                Player player = playerSender(sender);
                if (player != null) {
                    EventLayoutState.Point point = pointAt(player.getLocation());
                    if (!worldName.equalsIgnoreCase(point.world())) {
                        message(sender, "&cArena point должен находиться в event world: " + worldName);
                    } else {
                        EventLayoutState previous = layoutState;
                        layoutState = "pos1".equalsIgnoreCase(args[1])
                                ? withArenaPoints(point, previous.arenaPos2())
                                : withArenaPoints(previous.arenaPos1(), point);
                        boolean complete = layoutState.arenaPos1() != null && layoutState.arenaPos2() != null;
                        if ((!complete || applyArenaBoundsFromLayout()) && saveStateSync()) {
                            message(sender, "&aArena " + args[1] + " сохранена: &f" + locationText(player.getLocation()));
                        } else {
                            layoutState = previous;
                            message(sender, "&cArena requires same-world pos1/pos2 и durable save; изменение отменено.");
                        }
                    }
                }
            } else if (args.length > 2 && "clear".equalsIgnoreCase(args[1])
                    && "confirm".equalsIgnoreCase(args[2])) {
                World eventWorld = Bukkit.getWorld(worldName);
                if (eventWorld == null) {
                    message(sender, "&cEvent world не загружен; arena не изменена.");
                    return;
                }
                EventLayoutState previous = layoutState;
                int defaultMinX = coreX - (int) Math.ceil(config.arenaRadius());
                int defaultMaxX = coreX + (int) Math.ceil(config.arenaRadius());
                int verticalRadius = (int) Math.ceil(config.arenaVerticalRadius());
                int defaultMinY = Math.max(eventWorld.getMinHeight(), coreY - verticalRadius);
                int defaultMaxY = Math.min(eventWorld.getMaxHeight() - 1, coreY + verticalRadius);
                int defaultMinZ = coreZ - (int) Math.ceil(config.arenaRadius());
                int defaultMaxZ = coreZ + (int) Math.ceil(config.arenaRadius());
                layoutState = withArenaPoints(
                        new EventLayoutState.Point(worldName, defaultMinX, defaultMinY, defaultMinZ),
                        new EventLayoutState.Point(worldName, defaultMaxX, defaultMaxY, defaultMaxZ));
                applyArenaBoundsFromLayout();
                if (saveStateSync()) {
                    message(sender, "&aCustom arena bounds очищены; восстановлены bounded bounds от Core.");
                } else {
                    layoutState = previous;
                    message(sender, "&cArena layout не сохранён; изменение отменено.");
                }
            } else if (args.length > 1 && "info".equalsIgnoreCase(args[1])) {
                message(sender, "&7arena=&f" + arenaBoundsText());
                message(sender, "&7pos1=&f" + pointText(layoutState.arenaPos1())
                        + " &7pos2=&f" + pointText(layoutState.arenaPos2()));
            } else {
                message(sender, "&e/cmend arena pos1|pos2|info|clear confirm|border <seconds>");
            }
        } else {
            if (args.length < 2) {
                message(sender, "&e/cmend gate pos1|pos2|info|preview|open [ticks-per-layer]|restore confirm");
                return;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "pos1", "pos2" -> {
                    Player player = playerSender(sender);
                    if (player == null) {
                        return;
                    }
                    if (gateOpeningTask != null || !layoutState.gateSnapshot().isEmpty()) {
                        message(sender, "&cСначала выполни /cmend gate restore confirm; сохранённый Gate нельзя переназначить поверх старого snapshot.");
                        return;
                    }
                    EventLayoutState.Point point = pointAt(player.getLocation());
                    EventLayoutState.Point other = "pos1".equalsIgnoreCase(args[1])
                            ? previousPoint(layoutState.gatePos2()) : previousPoint(layoutState.gatePos1());
                    if (other != null && other.configured()
                            && !point.world().equalsIgnoreCase(other.world())) {
                        message(sender, "&cGate points must be in one world; текущая точка не сохранена.");
                        return;
                    }
                    EventLayoutState previous = layoutState;
                    layoutState = "pos1".equalsIgnoreCase(args[1])
                            ? withGatePoints(point, previous.gatePos2())
                            : withGatePoints(previous.gatePos1(), point);
                    if (saveStateSync()) {
                        startGateSelectionPreview(sender);
                        message(sender, "&aGate " + args[1] + " сохранена: &f" + locationText(player.getLocation()));
                    } else {
                        layoutState = previous;
                        message(sender, "&cGate point не сохранён durable; изменение отменено.");
                    }
                }
                case "info" -> {
                    message(sender, "&7gate.pos1=&f" + pointText(layoutState.gatePos1())
                            + " &7pos2=&f" + pointText(layoutState.gatePos2()));
                    message(sender, "&7gate.status=&f" + layoutState.gateStatus()
                            + " &7volume=&f" + gateVolumeText()
                            + " &7progress=&f" + gateProgressText());
                }
                case "preview" -> previewGate(sender);
                case "open" -> {
                    int ticks = args.length > 2
                            ? parseInt(args, 2, Integer.MIN_VALUE)
                            : DEFAULT_GATE_TICKS_PER_LAYER;
                    if (ticks == Integer.MIN_VALUE) {
                        message(sender, "&cИспользование: /cmend gate open [ticks-per-layer].");
                    } else {
                        openGate(sender, ticks, "admin-gate-open", false);
                    }
                }
                case "restore" -> {
                    if (!confirmed(args, 2)) {
                        message(sender, "&cПовтори /cmend gate restore confirm.");
                    } else {
                        restoreGate(sender);
                    }
                }
                default -> message(sender, "&e/cmend gate pos1|pos2|info|preview|open [ticks-per-layer]|restore confirm");
            }
        }
    }

    private EventLayoutState.Point pointAt(Location location) {
        return new EventLayoutState.Point(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private EventLayoutState withArenaPoints(EventLayoutState.Point pos1, EventLayoutState.Point pos2) {
        return new EventLayoutState(pos1, pos2, layoutState.gatePos1(), layoutState.gatePos2(),
                layoutState.gateSnapshot(), layoutState.gateStatus(), layoutState.portalRoom());
    }

    private EventLayoutState withGatePoints(EventLayoutState.Point pos1, EventLayoutState.Point pos2) {
        return new EventLayoutState(layoutState.arenaPos1(), layoutState.arenaPos2(), pos1, pos2,
                Map.of(), "UNSET", layoutState.portalRoom());
    }

    private EventLayoutState.Point previousPoint(EventLayoutState.Point point) {
        return point == null || !point.configured() ? null : point;
    }

    private boolean applyArenaBoundsFromLayout() {
        EventLayoutState.Point first = layoutState.arenaPos1();
        EventLayoutState.Point second = layoutState.arenaPos2();
        if (first == null || second == null || !first.configured() || !first.world().equalsIgnoreCase(second.world())
                || !worldName.equalsIgnoreCase(first.world())) {
            return false;
        }
        arenaMinX = Math.min(first.x(), second.x());
        arenaMinY = Math.min(first.y(), second.y());
        arenaMinZ = Math.min(first.z(), second.z());
        arenaMaxX = Math.max(first.x(), second.x());
        arenaMaxY = Math.max(first.y(), second.y());
        arenaMaxZ = Math.max(first.z(), second.z());
        return arenaVolume() > 0L && arenaVolume() <= 262_144L;
    }

    private long arenaVolume() {
        return (long) (arenaMaxX - arenaMinX + 1) * (arenaMaxY - arenaMinY + 1)
                * (arenaMaxZ - arenaMinZ + 1);
    }

    private String arenaBoundsText() {
        if (!isConfigured()) {
            return "unset";
        }
        return worldName + " [" + arenaMinX + "," + arenaMinY + "," + arenaMinZ + "]..["
                + arenaMaxX + "," + arenaMaxY + "," + arenaMaxZ + "] volume=" + arenaVolume();
    }

    private boolean showArenaBoundary(CommandSender sender, int seconds) {
        if (!isConfigured()) {
            message(sender, "&cСначала настрой Core.");
            return false;
        }
        if (seconds < 1 || seconds > MAX_ARENA_PREVIEW_SECONDS) {
            message(sender, "&cДлительность границы должна быть от 1 до "
                    + MAX_ARENA_PREVIEW_SECONDS + " секунд.");
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            message(sender, "&cEvent world не загружен; граница не показана.");
            return false;
        }
        cancelArenaBoundaryPreview();
        String previewEventId = eventId;
        long previewGeneration = generation;
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        arenaBoundaryTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!isConfigured() || !eventId.equals(previewEventId) || generation != previewGeneration
                    || System.currentTimeMillis() >= expiresAt) {
                cancelArenaBoundaryPreview();
                return;
            }
            drawArenaBoundaryFrame(world);
        }, 0L, 5L);
        getLogger().info("END_EVENT_ARENA_BOUNDARY event=" + eventId + " generation=" + generation
                + " seconds=" + seconds + " bounds=" + arenaBoundsText());
        message(sender, "&aГраница арены показана частицами на &f" + seconds + " сек."
                + " &7Линии: ±20 по X/Z, ±3 по Y.");
        return true;
    }

    private void cancelArenaBoundaryPreview() {
        if (arenaBoundaryTask != null) {
            arenaBoundaryTask.cancel();
            arenaBoundaryTask = null;
        }
    }

    /**
     * Highlights the selected gate without changing any world block. The
     * first point is shown as one outlined block; after the second point is
     * selected, every solid coordinate in the bounded cuboid that the opening
     * command will process is highlighted. The task is tied to the current
     * event generation so a reset or core removal cannot leave stale particles
     * running.
     */
    private void startGateSelectionPreview(CommandSender sender) {
        cancelGateSelectionPreview();
        EventLayoutState.Point first = layoutState.gatePos1();
        EventLayoutState.Point second = layoutState.gatePos2();
        if (first == null || !first.configured()) {
            return;
        }
        World world = Bukkit.getWorld(first.world());
        if (world == null) {
            message(sender, "&cМир Gate не загружен; подсветка не запущена.");
            return;
        }
        long previewVolume = 1L;
        int solidBlocks = world.getBlockAt(first.x(), first.y(), first.z()).getType().isAir() ? 0 : 1;
        if (second != null && second.configured()) {
            try {
                GateOpeningPlan plan = GateOpeningPlan.from(
                        new GateOpeningPlan.Point(first.world(), first.x(), first.y(), first.z()),
                        new GateOpeningPlan.Point(second.world(), second.x(), second.y(), second.z()),
                        MAX_GATE_VOLUME);
                previewVolume = plan.volume();
                solidBlocks = 0;
                for (GateOpeningPlan.Layer layer : plan.layersDescending()) {
                    for (GateOpeningPlan.Point point : layer.blocks()) {
                        if (!world.getBlockAt(point.x(), point.y(), point.z()).getType().isAir()) {
                            solidBlocks++;
                        }
                    }
                }
            } catch (IllegalArgumentException invalid) {
                message(sender, "&cТочки Gate сохранены, но bounded-подсветка не запущена: "
                        + invalid.getMessage());
                return;
            }
        }
        String previewEventId = eventId;
        long previewGeneration = generation;
        long expiresAt = System.currentTimeMillis()
                + DEFAULT_GATE_SELECTION_PREVIEW_SECONDS * 1000L;
        gateSelectionPreviewTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!isConfigured() || !eventId.equals(previewEventId)
                    || generation != previewGeneration || System.currentTimeMillis() >= expiresAt
                    || gateOpeningTask != null) {
                cancelGateSelectionPreview();
                return;
            }
            drawGateSelectionPreview(world, layoutState.gatePos1(), layoutState.gatePos2());
        }, 0L, 5L);
        getLogger().info("END_EVENT_GATE_SELECTION_PREVIEW event=" + eventId
                + " generation=" + generation + " seconds=" + DEFAULT_GATE_SELECTION_PREVIEW_SECONDS
                + " pos1=" + pointText(first) + " pos2=" + pointText(second)
                + " volume=" + previewVolume + " solidBlocks=" + solidBlocks);
        message(sender, "&aТочка Gate подсвечена частицами на &f"
                + DEFAULT_GATE_SELECTION_PREVIEW_SECONDS + " сек.; "
                + "после второй точки будут подсвечены все заполненные блоки,"
                + " которые откроются послойно.");
    }

    private void cancelGateSelectionPreview() {
        if (gateSelectionPreviewTask != null) {
            gateSelectionPreviewTask.cancel();
            gateSelectionPreviewTask = null;
        }
    }

    private void drawGateSelectionPreview(World world, EventLayoutState.Point first,
                                          EventLayoutState.Point second) {
        if (world == null || first == null || !first.configured()) {
            return;
        }
        if (second == null || !second.configured()) {
            drawGateBlockOutline(world, first.x(), first.y(), first.z());
            return;
        }
        GateOpeningPlan plan;
        try {
            plan = GateOpeningPlan.from(
                    new GateOpeningPlan.Point(first.world(), first.x(), first.y(), first.z()),
                    new GateOpeningPlan.Point(second.world(), second.x(), second.y(), second.z()),
                    MAX_GATE_VOLUME);
        } catch (IllegalArgumentException invalid) {
            return;
        }
        for (GateOpeningPlan.Layer layer : plan.layersDescending()) {
            for (GateOpeningPlan.Point point : layer.blocks()) {
                if (!world.getBlockAt(point.x(), point.y(), point.z()).getType().isAir()) {
                    drawGateBlockOutline(world, point.x(), point.y(), point.z());
                }
            }
        }
    }

    private void drawGateBlockOutline(World world, int x, int y, int z) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(177, 70, 255), 1.1F);
        double[] coordinates = {0.08D, 0.92D};
        for (double dx : coordinates) {
            for (double dy : coordinates) {
                for (double dz : coordinates) {
                    world.spawnParticle(Particle.DUST,
                            new Location(world, x + dx, y + dy, z + dz),
                            1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
                }
            }
        }
        world.spawnParticle(Particle.END_ROD,
                new Location(world, x + 0.5D, y + 0.5D, z + 0.5D),
                2, 0.12D, 0.12D, 0.12D, 0.0D);
    }

    private void drawArenaBoundaryFrame(World world) {
        if (world == null) {
            return;
        }
        double minX = arenaMinX + 0.5D;
        double maxX = arenaMaxX + 0.5D;
        double minY = arenaMinY + 0.05D;
        double maxY = arenaMaxY + 0.95D;
        double minZ = arenaMinZ + 0.5D;
        double maxZ = arenaMaxZ + 0.5D;

        // Two horizontal rectangles show the exact top and bottom of the
        // protected cuboid.  Four vertical lines make the volume readable
        // without filling the arena with particles.
        for (double x = minX; x <= maxX + 0.001D; x += ARENA_BOUNDARY_STEP) {
            spawnArenaBoundaryPoint(world, x, minY, minZ);
            spawnArenaBoundaryPoint(world, x, minY, maxZ);
            spawnArenaBoundaryPoint(world, x, maxY, minZ);
            spawnArenaBoundaryPoint(world, x, maxY, maxZ);
        }
        for (double z = minZ; z <= maxZ + 0.001D; z += ARENA_BOUNDARY_STEP) {
            spawnArenaBoundaryPoint(world, minX, minY, z);
            spawnArenaBoundaryPoint(world, maxX, minY, z);
            spawnArenaBoundaryPoint(world, minX, maxY, z);
            spawnArenaBoundaryPoint(world, maxX, maxY, z);
        }
        for (double y = minY; y <= maxY + 0.001D; y += ARENA_BOUNDARY_STEP) {
            spawnArenaBoundaryPoint(world, minX, y, minZ);
            spawnArenaBoundaryPoint(world, minX, y, maxZ);
            spawnArenaBoundaryPoint(world, maxX, y, minZ);
            spawnArenaBoundaryPoint(world, maxX, y, maxZ);
        }
    }

    private void spawnArenaBoundaryPoint(World world, double x, double y, double z) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(177, 70, 255), 1.0F);
        world.spawnParticle(Particle.DUST, new Location(world, x, y, z),
                1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
    }

    private String pointText(EventLayoutState.Point point) {
        return point == null ? "unset" : point.world() + " " + point.x() + "," + point.y() + "," + point.z();
    }

    private void previewGate(CommandSender sender) {
        if (isCombatPhase() || gateOpeningTask != null) {
            message(sender, "&cGate preview запрещён во время боя или открытия.");
            return;
        }
        if ("OPENED".equalsIgnoreCase(layoutState.gateStatus())) {
            message(sender, "&eGate уже открыт; сначала выполни /cmend gate restore confirm.");
            return;
        }
        List<Block> blocks = gateBlocks();
        if (blocks.isEmpty()) {
            message(sender, "&cGate pos1/pos2 не настроены, мир не изменён.");
            return;
        }
        if ("PREVIEW".equalsIgnoreCase(layoutState.gateStatus()) && !layoutState.gateSnapshot().isEmpty()) {
            startGateSelectionPreview(sender);
            message(sender, "&eGate уже находится в preview; snapshot не перезаписан, подсветка запущена заново.");
            return;
        }
        EventLayoutState previous = layoutState;
        Map<String, String> snapshot = captureGateSnapshot(blocks);
        layoutState = withGateState(snapshot, "PREVIEW");
        // The original block data is durable before the preview mutation.  If
        // the save fails, no world block is touched.
        if (!saveStateSync()) {
            layoutState = previous;
            message(sender, "&cGate snapshot не сохранён; preview не начат.");
            return;
        }
        startGateSelectionPreview(sender);
        message(sender, "&aGate preview создан частицами; ванильные блоки не заменялись. "
                + "Snapshot durable сохранён для безопасного открытия/restore.");
    }

    private boolean openGate(CommandSender sender, int ticksPerLayer, String reason, boolean forVictory) {
        if (ticksPerLayer < MIN_GATE_TICKS_PER_LAYER || ticksPerLayer > MAX_GATE_TICKS_PER_LAYER) {
            message(sender, "&cИнтервал слоя должен быть от " + MIN_GATE_TICKS_PER_LAYER
                    + " до " + MAX_GATE_TICKS_PER_LAYER + " тиков.");
            return false;
        }
        if ("OPENED".equalsIgnoreCase(layoutState.gateStatus())) {
            if (forVictory) {
                victoryGatePending = false;
                victoryStep = VICTORY_GATE_OPENED;
                saveStateSync();
            }
            message(sender, "&aGate уже открыт.");
            return true;
        }
        GateOpeningPlan plan = gateOpeningPlan(sender);
        if (plan == null) {
            return false;
        }
        if (gateOpeningTask != null) {
            if (forVictory) {
                victoryGatePending = true;
                victoryStep = VICTORY_GATE_OPENING;
                saveStateSync();
            }
            message(sender, "&eGate уже открывается послойно.");
            return false;
        }
        cancelGateSelectionPreview();
        if ("OPENING".equalsIgnoreCase(layoutState.gateStatus())) {
            // A stale in-memory task must never continue against an ambiguous
            // world. Restore the durable snapshot before starting afresh.
            restoreGateSnapshot(layoutState.gatePos1(), layoutState.gateSnapshot());
            layoutState = withGateState(Map.of(), "RESTORED_ON_BOOT");
            if (!saveStateSync()) {
                message(sender, "&cСостояние Gate не удалось безопасно восстановить; открытие отменено.");
                return false;
            }
        }
        Map<String, String> snapshot = layoutState.gateSnapshot().isEmpty()
                ? captureGateSnapshot(gateBlocks()) : layoutState.gateSnapshot();
        if (snapshot.isEmpty() || snapshot.size() != plan.volume()) {
            message(sender, "&cGate snapshot не совпадает с bounded cuboid; блоки не изменены.");
            return false;
        }
        EventLayoutState previousLayout = layoutState;
        String previousVictoryStep = victoryStep;
        layoutState = withGateState(snapshot, "OPENING");
        if (forVictory) {
            victoryGatePending = true;
            victoryStep = VICTORY_GATE_OPENING;
        }
        // Persist the complete snapshot and OPENING marker before removing a
        // single block. Boot recovery can therefore restore every coordinate.
        if (!saveStateSync()) {
            layoutState = previousLayout;
            victoryStep = previousVictoryStep;
            victoryGatePending = false;
            message(sender, "&cGate snapshot не сохранён durable; открытие отменено.");
            return false;
        }

        String openingEventId = eventId;
        long openingGeneration = generation;
        boolean openingForVictory = forVictory;
        boolean openingFromPreview = "PREVIEW".equalsIgnoreCase(previousLayout.gateStatus());
        int[] layerIndex = {0};
        List<GateOpeningPlan.Layer> layers = plan.layersDescending();
        gateOpeningTask = Bukkit.getScheduler().runTaskTimer(this, () -> tickGateOpening(
                openingEventId, openingGeneration, layers, layerIndex, snapshot,
                openingForVictory, openingFromPreview),
                0L, ticksPerLayer);
        getLogger().info("END_EVENT_GATE_OPENING event=" + eventId + " generation=" + generation
                + " reason=" + reason + " layers=" + layers.size() + " ticksPerLayer=" + ticksPerLayer
                + " volume=" + plan.volume());
        message(sender, "&aGate открывается сверху вниз: &f" + layers.size()
                + " слоёв, интервал &f" + ticksPerLayer + " тиков.");
        return false;
    }

    private void tickGateOpening(String openingEventId, long openingGeneration,
                                 List<GateOpeningPlan.Layer> layers, int[] layerIndex,
                                 Map<String, String> snapshot, boolean openingForVictory,
                                 boolean openingFromPreview) {
        if (!isEnabled() || !eventId.equals(openingEventId) || generation != openingGeneration) {
            cancelGateOpeningTask();
            return;
        }
        if (layerIndex[0] >= layers.size()) {
            finishGateOpening(openingForVictory, snapshot);
            return;
        }
        GateOpeningPlan.Layer layer = layers.get(layerIndex[0]++);
        World world = Bukkit.getWorld(layoutState.gatePos1().world());
        if (world == null) {
            abortGateOpening("event world is unavailable", snapshot, openingForVictory);
            return;
        }
        int removed = 0;
        int conflicts = 0;
        for (GateOpeningPlan.Point point : layer.blocks()) {
            Block block = world.getBlockAt(point.x(), point.y(), point.z());
            String expected = snapshot.get(gateKey(block));
            if (expected == null) {
                conflicts++;
                continue;
            }
            if (block.getType().isAir()) {
                if (!isAirBlockData(expected)) {
                    removed++;
                }
                continue;
            }
            if (!sameBlockData(block, expected)
                    && !(openingFromPreview && block.getType() == Material.PURPLE_STAINED_GLASS)) {
                conflicts++;
                continue;
            }
            block.setType(Material.AIR, false);
            removed++;
        }
        // Keep the particle/sound budget proportional to one layer.
        Location center = gateEffectLocation(world, layer.y());
        world.spawnParticle(Particle.PORTAL, center, 28, 0.65D, 0.15D, 0.65D, 0.03D);
        world.spawnParticle(Particle.END_ROD, center, 8, 0.35D, 0.08D, 0.35D, 0.01D);
        world.spawnParticle(Particle.DUST, center, 14, 0.6D, 0.18D, 0.6D, 0.0D,
                new Particle.DustOptions(Color.fromRGB(177, 70, 255), 1.15F));
        world.spawnParticle(Particle.DUST, center, 5, 0.35D, 0.08D, 0.35D, 0.0D,
                new Particle.DustOptions(Color.fromRGB(22, 7, 31), 1.0F));
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.8F, 0.7F + (layerIndex[0] * 0.03F));
        getLogger().info("END_EVENT_GATE_LAYER event=" + eventId + " generation=" + generation
                + " y=" + layer.y() + " removed=" + removed + " conflicts=" + conflicts
                + " index=" + layerIndex[0] + "/" + layers.size());
        if (conflicts > 0) {
            abortGateOpening("snapshot conflict at layer y=" + layer.y(), snapshot, openingForVictory);
            return;
        }
        if (!saveStateSync()) {
            abortGateOpening("durable save failed after layer y=" + layer.y(), snapshot, openingForVictory);
            return;
        }
        if (layerIndex[0] >= layers.size()) {
            finishGateOpening(openingForVictory, snapshot);
        }
    }

    private void finishGateOpening(boolean openingForVictory, Map<String, String> snapshot) {
        layoutState = withGateState(snapshot, "OPENED");
        if (!saveStateSync()) {
            abortGateOpening("durable OPENED state failed", snapshot, openingForVictory);
            return;
        }
        cancelGateOpeningTask();
        getLogger().info("END_EVENT_GATE_OPENED event=" + eventId + " generation=" + generation
                + " progress=" + gateProgressText());
        if (openingForVictory) {
            victoryGatePending = false;
            victoryStep = VICTORY_GATE_OPENED;
            saveStateSync();
            checkVictoryRewardCompletion();
        }
    }

    private void abortGateOpening(String reason, Map<String, String> snapshot, boolean openingForVictory) {
        cancelGateOpeningTask();
        restoreGateSnapshot(layoutState.gatePos1(), snapshot);
        layoutState = withGateState(Map.of(), "RESTORED");
        if (openingForVictory) {
            victoryGatePending = false;
        }
        saveStateSync();
        getLogger().warning("END_EVENT_GATE_ABORTED event=" + eventId + " generation=" + generation
                + " reason=" + reason);
    }

    private void cancelGateOpeningTask() {
        if (gateOpeningTask != null) {
            gateOpeningTask.cancel();
            gateOpeningTask = null;
        }
    }

    private GateOpeningPlan gateOpeningPlan(CommandSender sender) {
        EventLayoutState.Point first = layoutState.gatePos1();
        EventLayoutState.Point second = layoutState.gatePos2();
        if (first == null || second == null || !first.configured() || !second.configured()) {
            message(sender, "&cСначала задай две точки: /cmend gate pos1 и /cmend gate pos2.");
            return null;
        }
        try {
            GateOpeningPlan plan = GateOpeningPlan.from(
                    new GateOpeningPlan.Point(first.world(), first.x(), first.y(), first.z()),
                    new GateOpeningPlan.Point(second.world(), second.x(), second.y(), second.z()),
                    MAX_GATE_VOLUME);
            if (Bukkit.getWorld(first.world()) == null) {
                message(sender, "&cМир Gate не загружен: " + first.world());
                return null;
            }
            return plan;
        } catch (IllegalArgumentException invalid) {
            message(sender, "&cGate bounded validation failed: " + invalid.getMessage());
            return null;
        }
    }

    private boolean isGateConfigured() {
        EventLayoutState.Point first = layoutState.gatePos1();
        EventLayoutState.Point second = layoutState.gatePos2();
        return first != null && second != null && first.configured() && second.configured()
                && first.world().equalsIgnoreCase(second.world());
    }

    private List<Block> gateBlocks() {
        GateOpeningPlan plan = gateOpeningPlan(null);
        if (plan == null) {
            return List.of();
        }
        World world = Bukkit.getWorld(plan.first().world());
        List<Block> blocks = new ArrayList<>((int) plan.volume());
        for (GateOpeningPlan.Layer layer : plan.layersDescending()) {
            for (GateOpeningPlan.Point point : layer.blocks()) {
                blocks.add(world.getBlockAt(point.x(), point.y(), point.z()));
            }
        }
        return blocks;
    }

    private Map<String, String> captureGateSnapshot(List<Block> blocks) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Block block : blocks) {
            snapshot.put(gateKey(block), block.getBlockData().getAsString());
        }
        return snapshot;
    }

    private EventLayoutState withGateState(Map<String, String> snapshot, String status) {
        return new EventLayoutState(layoutState.arenaPos1(), layoutState.arenaPos2(),
                layoutState.gatePos1(), layoutState.gatePos2(), snapshot, status, layoutState.portalRoom());
    }

    private String gateVolumeText() {
        GateOpeningPlan plan = gateOpeningPlan(null);
        return plan == null ? "invalid" : Long.toString(plan.volume());
    }

    private String gateProgressText() {
        if (layoutState.gateSnapshot().isEmpty()) {
            return "0/0";
        }
        World world = layoutState.gatePos1() == null ? null : Bukkit.getWorld(layoutState.gatePos1().world());
        if (world == null) {
            return "unknown/" + layoutState.gateSnapshot().size();
        }
        int removed = 0;
        for (Map.Entry<String, String> entry : layoutState.gateSnapshot().entrySet()) {
            String[] parts = entry.getKey().split(",", -1);
            if (parts.length != 3) {
                continue;
            }
            try {
                Block block = world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                if (!isAirBlockData(entry.getValue()) && block.getType().isAir()) {
                    removed++;
                }
            } catch (NumberFormatException ignored) {
                // Corrupt layout keys do not trigger a world scan.
            }
        }
        return removed + "/" + layoutState.gateSnapshot().size();
    }

    private Location gateEffectLocation(World world, int y) {
        EventLayoutState.Point first = layoutState.gatePos1();
        EventLayoutState.Point second = layoutState.gatePos2();
        double x = (Math.min(first.x(), second.x()) + Math.max(first.x(), second.x())) * 0.5D + 0.5D;
        double z = (Math.min(first.z(), second.z()) + Math.max(first.z(), second.z())) * 0.5D + 0.5D;
        return new Location(world, x, y + 0.5D, z);
    }

    private boolean sameBlockData(Block block, String expected) {
        return block != null && expected != null && expected.equalsIgnoreCase(block.getBlockData().getAsString());
    }

    private boolean isAirBlockData(String blockData) {
        if (blockData == null || blockData.isBlank()) {
            return true;
        }
        try {
            return Bukkit.createBlockData(blockData).getMaterial().isAir();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private void restoreGate(CommandSender sender) {
        cancelGateOpeningTask();
        cancelGateSelectionPreview();
        victoryGatePending = false;
        if (layoutState.gateSnapshot().isEmpty()) {
            message(sender, "&eУ Gate нет сохранённого snapshot.");
            return;
        }
        restoreGateSnapshot(layoutState.gatePos1(), layoutState.gateSnapshot());
        EventLayoutState previous = layoutState;
        layoutState = new EventLayoutState(previous.arenaPos1(), previous.arenaPos2(), previous.gatePos1(), previous.gatePos2(),
                Map.of(), "RESTORED", previous.portalRoom());
        if (saveStateSync()) {
            message(sender, "&aGate восстановлен по durable snapshot.");
        } else {
            message(sender, "&cGate восстановлен в мире, но новый layout status не сохранился; повтори restore после проверки.");
        }
    }

    private void restorePersistedGateIfNeeded() {
        boolean preview = "PREVIEW".equalsIgnoreCase(layoutState.gateStatus());
        boolean opening = "OPENING".equalsIgnoreCase(layoutState.gateStatus());
        if ((!preview && !opening) || layoutState.gateSnapshot().isEmpty()) {
            return;
        }
        restoreGateSnapshot(layoutState.gatePos1(), layoutState.gateSnapshot());
        EventLayoutState previous = layoutState;
        layoutState = new EventLayoutState(previous.arenaPos1(), previous.arenaPos2(), previous.gatePos1(), previous.gatePos2(),
                Map.of(), "RESTORED_ON_BOOT", previous.portalRoom());
        if (layoutStore != null && !layoutStore.save(layoutState)) {
            layoutState = previous;
            getLogger().warning("Gate was restored from durable preview snapshot, but layout status could not be saved.");
        } else {
            getLogger().info("Restored bounded gate from durable " + layoutState.gateStatus()
                    + " snapshot during local bootstrap.");
        }
    }

    private String gateKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private void restoreGateSnapshot(EventLayoutState.Point origin, Map<String, String> snapshot) {
        if (origin == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(origin.world());
        if (world == null) {
            return;
        }
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            String[] parts = entry.getKey().split(",", -1);
            if (parts.length != 3) {
                continue;
            }
            try {
                restoreBlock(world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), entry.getValue());
            } catch (NumberFormatException ignored) {
                // Corrupt coordinates are ignored; no broad world scan is attempted.
            }
        }
    }

    private EventLayoutState.Portal portalRoom() {
        return layoutState.portalRoom() != null && layoutState.portalRoom().configured()
                ? layoutState.portalRoom()
                : new EventLayoutState.Portal(config.portalWorld(), config.portalX(), config.portalY(), config.portalZ(),
                        config.portalYaw(), config.portalPitch());
    }

    private void handleResources(CommandSender sender, String[] args) {
        if (args.length < 2 || "status".equalsIgnoreCase(args[1])) {
            message(sender, "&7" + resourceProgressText());
            return;
        }
        if ("reset".equalsIgnoreCase(args[1])) {
            if (!confirmed(args, 2)) {
                message(sender, "&cПовтори /cmend resources reset confirm.");
                return;
            }
            if (phase != EventPhase.COLLECTING && phase != EventPhase.READY_FOR_PLAYERS) {
                message(sender, "&cРесурсы можно сбросить только вне боя и ритуала.");
                return;
            }
            depositedResources.replaceAll((key, ignored) -> 0);
            coreCharged = false;
            forcePhase(EventPhase.COLLECTING, "admin resource reset");
            saveStateSync();
            message(sender, "&aПрогресс ресурсов сброшен в локальном состоянии события.");
            return;
        }
        if ("add".equalsIgnoreCase(args[1]) && args.length >= 4) {
            String material = args[2].toUpperCase(Locale.ROOT);
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException invalid) {
                message(sender, "&cКоличество должно быть числом.");
                return;
            }
            if (!resourceRequirements.containsKey(material) || amount < 1) {
                message(sender, "&cМатериал или количество недопустимы.");
                return;
            }
            depositedResources.put(material, Math.min(resourceRequirements.get(material),
                    depositedResources.getOrDefault(material, 0) + amount));
            resourceContributors.add(sender instanceof Player player ? player.getUniqueId() : UUID.nameUUIDFromBytes("admin".getBytes(StandardCharsets.UTF_8)));
            updateCoreChargeState();
            saveStateSync();
            message(sender, "&aРесурс добавлен только в локальный event state: &f" + resourceProgressText());
        }
    }

    private void handleRitual(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend ritual start|cancel|cleanup|reset|unlock");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (phase == EventPhase.READY_FOR_PLAYERS) {
                    beginCountdownIfReady();
                    message(sender, "&aПроверка ритуала запущена.");
                } else {
                    message(sender, "&cРитуал можно запускать только в READY_FOR_PLAYERS.");
                }
            }
            case "cancel" -> {
                if (confirmed(args, 2)) {
                    cancelRitual("admin cancel");
                    message(sender, "&aCountdown отменён; event-owned бой не затронут.");
                } else {
                    message(sender, "&cПовтори /cmend ritual cancel confirm.");
                }
            }
            case "cleanup" -> {
                if (confirmed(args, 2)) {
                    cleanupOwnedEntities(eventId, generation);
                    clearClientEffects();
                    message(sender, "&aTransient entities/effects очищены.");
                } else {
                    message(sender, "&cПовтори /cmend ritual cleanup confirm.");
                }
            }
            case "reset" -> {
                if (confirmed(args, 2)) {
                    resetEventSafely(sender);
                } else {
                    message(sender, "&cПовтори /cmend ritual reset confirm.");
                }
            }
            case "unlock" -> {
                if (confirmed(args, 2)) {
                    unlockEnd(sender, "ritual-unlock");
                } else {
                    message(sender, "&cПовтори /cmend ritual unlock confirm.");
                }
            }
            default -> message(sender, "&e/cmend ritual start|cancel|cleanup|reset|unlock");
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!confirmed(args, 1)) {
            message(sender, "&cПовтори /cmend reset confirm. End world, player data и DB не затрагиваются.");
            return;
        }
        resetEventSafely(sender);
    }

    private boolean confirmed(String[] args, int index) {
        return args != null && args.length > index && "confirm".equalsIgnoreCase(args[index]);
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend test run creative | wave <1|2|3|final> | ai | boss | music <waves|boss|half|final|victory> [player]");
            return;
        }
        if ("run".equalsIgnoreCase(args[1])) {
            if (args.length >= 4 && "creative".equalsIgnoreCase(args[2])
                    && "cancel".equalsIgnoreCase(args[3])) {
                if (creativeTestTask == null) {
                    message(sender, "&eЛокальный Creative test сейчас не запущен.");
                } else {
                    finishCreativeTest(false, "admin cancellation");
                    message(sender, "&aЛокальный Creative test остановлен; official phase/roster не изменены.");
                }
            } else if (args.length >= 3 && "creative".equalsIgnoreCase(args[2])) {
                startCreativeTest(sender);
            } else {
                message(sender, "&e/cmend test run creative [cancel]");
            }
            return;
        }
        if ("music".equalsIgnoreCase(args[1])) {
            String requested = args.length > 2 ? args[2] : "";
            Player player = sender instanceof Player current
                    ? current
                    : args.length > 3 ? Bukkit.getPlayerExact(args[3]) : null;
            if (player == null) {
                message(sender, "&cУкажи онлайн-игрока: /cmend test music <waves|boss|half|final|victory> <player>.");
                return;
            }
            playTestMusic(player, requested);
            return;
        }
        if ("wave".equalsIgnoreCase(args[1])) {
            int wave = "final".equalsIgnoreCase(args.length > 2 ? args[2] : "")
                    ? 4 : parseInt(args, 2, 0);
            if (wave < 1 || wave > 4) {
                message(sender, "&cВолна должна быть 1, 2, 3 или final.");
                return;
            }
            if (!hasSpawnAnchor()) {
                message(sender, "&cТестовая волна не создана: сначала настрой Core в загруженном event world.");
                return;
            }
            spawnWave(wave, true);
            message(sender, "&aТестовая волна создана; official phase/roster/victory не изменены.");
            return;
        }
        if ("ai".equalsIgnoreCase(args[1])) {
            spawnTestAi(sender);
            return;
        }
        if ("boss".equalsIgnoreCase(args[1])) {
            spawnTestBoss(sender);
            return;
        }
        message(sender, "&e/cmend test run creative | wave <1|2|3|final> | ai | boss | music <waves|boss|half|final|victory> [player]");
    }

    /**
     * Runs a disposable, local-only visual/combat pass while the operator stays
     * in Creative.  It intentionally does not transition the durable event
     * state machine, freeze the official roster, unlock End, or issue rewards.
     * Test entities still use the production spawn, leash, client-bind, and
     * spell-flight paths, so the run is useful for a real Paper/Fabric check.
     */
    private void startCreativeTest(CommandSender sender) {
        if (config == null || !"local".equalsIgnoreCase(config.environment())) {
            message(sender, "&cCreative full-run разрешён только при environment=local.");
            return;
        }
        Player player = playerSender(sender);
        if (player == null) {
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            message(sender, "&cДля полного локального прогона оператор должен быть в Creative.");
            return;
        }
        if (!isConfigured() || !hasSpawnAnchor()) {
            message(sender, "&cСначала настрой Core в локальном event world.");
            return;
        }
        if (creativeTestTask != null) {
            message(sender, "&eCreative full-run уже выполняется; для остановки: /cmend test run creative cancel.");
            return;
        }
        if (endUnlocked || phase == EventPhase.UNLOCKED || !officialRewardRoster.isEmpty()
                || officialCombatStateActive()) {
            message(sender, "&cCreative full-run остановлен: активная official session или End уже разблокирован.");
            return;
        }
        if (!isArenaLocation(player.getLocation())) {
            message(sender, "&cВстань в пределах локальной арены возле Core и повтори команду.");
            return;
        }
        clearWaveEntities();
        clearBossOnly();
        clearActiveRiftProjectiles();
        clearVoidMarkZones();
        creativeTestPlayerUuid = player.getUniqueId();
        creativeTestGeneration = generation;
        creativeTestStage = 0;
        creativeTestStageTicks = 0;
        testCombatAiMode = true;
        controlSpellUnlocked = false;
        halfHealthTriggered = false;
        finalDrainTriggered = false;
        finalDrainApplied = false;
        rebuildPersistedVisuals();
        getLogger().info("CREATIVE_TEST_START event=" + eventId + " generation=" + generation
                + " operator=" + player.getUniqueId() + " gamemode=" + player.getGameMode()
                + " official_phase=" + phase + " official_roster=" + officialRewardRoster.size()
                + " official_phase_unchanged=true");
        message(sender, "&aЗапущен локальный Creative full-run. Оператор не входит в official roster; прогресс смотри в latest.log.");
        creativeTestTask = Bukkit.getScheduler().runTaskTimer(this, this::tickCreativeTest, 1L, 1L);
    }

    private boolean officialCombatStateActive() {
        return switch (phase) {
            case COUNTDOWN, WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3,
                    BOSS_ACTIVE, FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH,
                    VICTORY_PROCESSING, VICTORY, UNLOCKED, RECOVERY_REQUIRED -> true;
            default -> false;
        };
    }

    private void tickCreativeTest() {
        Player player = creativeTestPlayer();
        if (player == null || player.getGameMode() != GameMode.CREATIVE
                || !isArenaLocation(player.getLocation()) || creativeTestGeneration != generation
                || endUnlocked || !officialRewardRoster.isEmpty()) {
            finishCreativeTest(false, "operator/session guard failed");
            return;
        }
        if (++creativeTestStageTicks < creativeTestStageDelay(creativeTestStage)) {
            return;
        }
        creativeTestStageTicks = 0;
        runCreativeTestStage(player, creativeTestStage++);
    }

    private int creativeTestStageDelay(int stage) {
        return Math.min(CREATIVE_TEST_MAX_STAGE_TICKS, switch (stage) {
            case 1, 2, 4, 6, 13, 15, 17, 18 -> 20;
            case 3, 5, 9, 10, 11, 12, 14 -> 50;
            case 7 -> 20;
            case 8 -> Math.max(80, config.miniBossTuning().spellTelegraphTicks() + SPELL_FLIGHT_TICKS + 5);
            case 16 -> 40;
            default -> 20;
        });
    }

    private Player creativeTestPlayer() {
        if (creativeTestPlayerUuid == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(creativeTestPlayerUuid);
        return player != null && player.isOnline() && !player.isDead() ? player : null;
    }

    private void runCreativeTestStage(Player player, int stage) {
        switch (stage) {
            case 1 -> {
                rebuildPersistedVisuals();
                getLogger().info("CREATIVE_TEST_CORE event=" + eventId + " generation=" + generation
                        + " target=" + coreX + "," + coreY + "," + coreZ
                        + " visual=" + visualStatusText());
            }
            case 2 -> {
                rebuildPersistedVisuals();
                getLogger().info("CREATIVE_TEST_RESOURCES event=" + eventId + " generation=" + generation
                        + " progress=" + resourceProgressText() + " charged=" + coreCharged);
                getLogger().info("CREATIVE_TEST_RUNES event=" + eventId + " generation=" + generation
                        + " visual=" + visualStatusText() + " occupied=" + padOccupants.size());
            }
            case 3 -> {
                clearWaveEntities();
                spawnWave(1, true);
                getLogger().info("CREATIVE_TEST_WAVE_1 event=" + eventId + " generation=" + generation
                        + " live=" + countLiveOwnedMobs() + " composition=" + liveOwnedComposition());
            }
            case 4 -> {
                clearWaveEntities();
                getLogger().info("CREATIVE_TEST_INTERMISSION_1 event=" + eventId + " generation=" + generation
                        + " live=" + countLiveOwnedMobs());
            }
            case 5 -> {
                spawnWave(2, true);
                getLogger().info("CREATIVE_TEST_WAVE_2 event=" + eventId + " generation=" + generation
                        + " live=" + countLiveOwnedMobs() + " composition=" + liveOwnedComposition());
            }
            case 6 -> {
                clearWaveEntities();
                getLogger().info("CREATIVE_TEST_INTERMISSION_2 event=" + eventId + " generation=" + generation
                        + " live=" + countLiveOwnedMobs());
            }
            case 7 -> {
                spawnWave(3, true);
                for (UUID entityUuid : miniBossSpells.keySet()) {
                    nextMiniBossSpellMillis.put(entityUuid, 0L);
                }
                getLogger().info("CREATIVE_TEST_WAVE_3 event=" + eventId + " generation=" + generation
                        + " live=" + countLiveOwnedMobs() + " composition=" + liveOwnedComposition()
                        + " miniBossSpells=" + miniBossSpells.values().stream().map(EndRiftAiPolicy.MiniBossSpell::id).toList());
            }
            case 8 -> {
                clearWaveEntities();
                spawnCreativeTestBoss(player);
                getLogger().info("CREATIVE_TEST_BOSS_ACTIVE event=" + eventId + " generation=" + generation
                        + " health=" + config.bossHealth() + " maxHealth=" + config.bossHealth()
                        + " bossbar=" + (bossBar != null));
            }
            case 9, 10, 11, 12 -> {
                LivingEntity boss = liveBoss();
                EndRiftAiPolicy.BossSpell spell = switch (stage) {
                    case 9 -> EndRiftAiPolicy.BossSpell.VOID_BLAST;
                    case 10 -> EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE;
                    case 11 -> EndRiftAiPolicy.BossSpell.VOID_MARK;
                    default -> EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS;
                };
                if (boss != null) {
                    castBossSpell(boss, spell, true);
                    getLogger().info("CREATIVE_TEST_BOSS_SPELL event=" + eventId + " generation=" + generation
                            + " spell=" + spell.id() + " flight_expected=true");
                }
            }
            case 13 -> {
                LivingEntity boss = liveBoss();
                halfHealthTriggered = true;
                controlSpellUnlocked = true;
                if (boss != null) {
                    boss.setHealth(Math.min(config.bossHalfHealth(), boss.getMaxHealth()));
                }
                getLogger().info("CREATIVE_TEST_HALF event=" + eventId + " generation=" + generation
                        + " threshold=" + config.bossHalfHealth() + " health="
                        + (boss == null ? "missing" : boss.getHealth()));
            }
            case 14 -> {
                LivingEntity boss = liveBoss();
                if (boss != null) {
                    castBossSpell(boss, EndRiftAiPolicy.BossSpell.WILL_DISTORTION, true);
                }
                getLogger().info("CREATIVE_TEST_CONTROL event=" + eventId + " generation=" + generation
                        + " spell=will_distortion flight_expected=true");
            }
            case 15 -> {
                LivingEntity boss = liveBoss();
                finalDrainTriggered = true;
                if (boss != null) {
                    boss.setInvulnerable(true);
                    boss.setHealth(Math.min(config.bossFinalHealth(), boss.getMaxHealth()));
                }
                getLogger().info("CREATIVE_TEST_FINAL_DRAIN event=" + eventId + " generation=" + generation
                        + " threshold=" + config.bossFinalThreshold() + " finalHealth=" + config.bossFinalHealth()
                        + " drainFraction=" + config.finalDrainFraction());
            }
            case 16 -> {
                spawnWave(4, true);
                getLogger().info("CREATIVE_TEST_FINAL_WAVE event=" + eventId + " generation=" + generation
                        + " live=" + countLiveOwnedMobs() + " composition=" + liveOwnedComposition());
            }
            case 17 -> {
                clearWaveEntities();
                getLogger().info("CREATIVE_TEST_BOSS_FINISH event=" + eventId + " generation=" + generation
                        + " finalWaveLive=" + countLiveOwnedMobs());
            }
            case 18 -> finishCreativeTest(true, "all disposable stages completed");
            default -> {
                if (stage > 18) {
                    finishCreativeTest(true, "all disposable stages completed");
                }
            }
        }
    }

    private void spawnCreativeTestBoss(Player player) {
        clearBossOnly();
        testCombatAiMode = true;
        Location core = coreLocation();
        if (core == null || core.getWorld() == null) {
            return;
        }
        Enderman boss = (Enderman) core.getWorld().spawnEntity(core.clone().add(0.0D, 1.0D, 0.0D), EntityType.ENDERMAN);
        configureBoss(boss, true);
        if (boss instanceof Mob mob) {
            mob.setTarget(player);
        }
        ensureBossBar();
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
        bindBossClient(player);
        bindEventEntitiesClient(player);
    }

    private String liveOwnedComposition() {
        Map<String, Integer> composition = new LinkedHashMap<>();
        for (Entity entity : ownedEntities.values()) {
            if (!(entity instanceof LivingEntity) || !isLiveOwnedEntity(entity.getUniqueId())) {
                continue;
            }
            String key = entity.getType().name().toLowerCase(Locale.ROOT);
            composition.merge(key, 1, Integer::sum);
        }
        return composition.toString();
    }

    private void finishCreativeTest(boolean success, String reason) {
        if (creativeTestTask != null) {
            creativeTestTask.cancel();
            creativeTestTask = null;
        }
        UUID operator = creativeTestPlayerUuid;
        long runGeneration = creativeTestGeneration;
        getLogger().info("CREATIVE_TEST_CLEANUP event=" + eventId + " generation=" + runGeneration
                + " success=" + success + " reason=" + reason
                + " official_phase_unchanged=" + !officialCombatStateActive()
                + " official_roster=" + officialRewardRoster.size());
        clearWaveEntities();
        clearBossOnly();
        clearActiveRiftProjectiles();
        clearVoidMarkZones();
        testCombatAiMode = false;
        controlSpellUnlocked = false;
        halfHealthTriggered = false;
        finalDrainTriggered = false;
        finalDrainApplied = false;
        if (bossBar != null) {
            bossBar.removeAll();
        }
        creativeTestPlayerUuid = null;
        creativeTestGeneration = 0L;
        creativeTestStage = 0;
        creativeTestStageTicks = 0;
        getLogger().info("CREATIVE_TEST_COMPLETE event=" + eventId + " generation=" + runGeneration
                + " success=" + success + " operator=" + operator
                + " official_phase=" + phase + " official_roster=" + officialRewardRoster.size()
                + " endUnlocked=" + endUnlocked);
    }

    private void handleWave(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend wave spawn <1|2|3|final> | clear");
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            clearWaveEntities();
            message(sender, "&aУдалены только event-owned wave entities.");
            return;
        }
        if ("spawn".equalsIgnoreCase(args[1])) {
            String requested = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
            int wave = "final".equals(requested) ? 4 : parseInt(args, 2, 0);
            if (wave < 1 || wave > 3 && wave != 4) {
                message(sender, "&cВолна должна быть 1, 2, 3 или final.");
                return;
            }
            if (!hasSpawnAnchor()) {
                message(sender, "&cВолна не создана: сначала настрой Core в загруженном event world.");
                return;
            }
            spawnWave(wave, true);
            message(sender, "&aТестовая волна создана; она не изменяет official victory roster.");
        }
    }

    private void handleBoss(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend boss spawn [official confirm]|info|damage <n>|phase <normal|half|final>|kill <cleanup|simulate-victory confirm>|spell <type>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (args.length >= 4 && "official".equalsIgnoreCase(args[2]) && confirmed(args, 3)) {
                    spawnOfficialBoss(sender);
                } else if (args.length >= 3 && "official".equalsIgnoreCase(args[2])) {
                    message(sender, "&cОфициальный boss требует /cmend boss spawn official confirm.");
                } else {
                    spawnTestBoss(sender);
                }
            }
            case "official" -> {
                if (args.length >= 3 && "confirm".equalsIgnoreCase(args[2])) {
                    spawnOfficialBoss(sender);
                } else {
                    message(sender, "&cОфициальный boss требует /cmend boss official confirm.");
                }
            }
            case "info" -> handleStatus(sender);
            case "damage" -> {
                LivingEntity boss = liveBoss();
                if (boss == null) {
                    message(sender, "&cBoss отсутствует.");
                } else {
                    double damage = parseDouble(args, 2, 0.0D);
                    applyBossDamage(boss, damage, null);
                }
            }
            case "phase" -> {
                LivingEntity boss = liveBoss();
                String requested = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (boss == null) {
                    message(sender, "&cBoss отсутствует.");
                } else if ("normal".equals(requested)) {
                    boss.setInvulnerable(false);
                    message(sender, "&aBoss test phase: normal.");
                } else if ("half".equals(requested)) {
                    triggerHalfPhase(boss);
                    message(sender, "&aBoss phase 50% вызвана.");
                } else if ("final".equals(requested)) {
                    triggerFinalPhase(boss, true);
                    message(sender, "&aBoss final ritual вызван.");
                } else {
                    message(sender, "&e/cmend boss phase <normal|half|final>");
                }
            }
            case "kill" -> {
                LivingEntity boss = liveBoss();
                if (boss == null) {
                    message(sender, "&cBoss отсутствует.");
                } else if ("cleanup".equalsIgnoreCase(args.length > 2 ? args[2] : "")) {
                    clearBossOnly();
                    message(sender, "&aBoss удалён как test cleanup; victory не вызвана.");
                } else if (args.length >= 4 && "simulate-victory".equalsIgnoreCase(args[2])
                        && confirmed(args, 3) && isOfficialEntity(boss) && !isTestBoss(boss)) {
                    if (phase == EventPhase.BOSS_ACTIVE) {
                        forcePhase(EventPhase.BOSS_FINISH, "admin simulate-victory");
                    }
                    boss.setHealth(0.0D);
                    message(sender, "&aOfficial boss victory simulation requested.");
                } else {
                    message(sender, "&e/cmend boss kill cleanup | boss kill simulate-victory confirm");
                }
            }
            case "spell" -> {
                String spell = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                LivingEntity boss = liveBoss();
                if (boss == null) {
                    message(sender, "&cBoss отсутствует.");
                } else if ("control_reverse".equals(spell)) {
                    Player requested = args.length > 3 ? Bukkit.getPlayerExact(args[3]) : null;
                    Player target = requested != null && isCombatTarget(requested)
                            ? requested : selectBossSpellTarget(boss);
                    if (target != null) {
                        telegraphBossSpell(boss, target, EndRiftAiPolicy.BossSpell.WILL_DISTORTION, true);
                    }
                    message(sender, "&aControl reversal test requested.");
                } else if (List.of("void_blast", "rift_projectile", "void_mark", "summon", "summon_servants")
                        .contains(spell)) {
                    EndRiftAiPolicy.BossSpell requestedSpell = switch (spell) {
                        case "void_blast" -> EndRiftAiPolicy.BossSpell.VOID_BLAST;
                        case "rift_projectile" -> EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE;
                        case "void_mark" -> EndRiftAiPolicy.BossSpell.VOID_MARK;
                        default -> EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS;
                    };
                    castBossSpell(boss, requestedSpell, true);
                    message(sender, "&aBoss spell test requested: &f" + requestedSpell.id());
                } else {
                    message(sender, "&e/cmend boss spell <void_blast|rift_projectile|void_mark|summon|control_reverse>");
                }
            }
            default -> message(sender, "&e/cmend boss spawn [official confirm]|info|damage <n>|phase <normal|half|final>|kill <cleanup|simulate-victory confirm>|spell <type>");
        }
    }

    private void handleClient(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend client status|bindboss|clear [player]");
            return;
        }
        Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> message(sender, "&7channel=&f" + config.bridgeChannel() + " &7boss-id=&f" + config.clientBossId()
                    + " &7control-id=&f" + config.clientControlId() + " &7active=&f"
                    + (target == null ? controlInstances.size() : controlInstances.containsKey(target.getUniqueId())));
            case "bindboss" -> {
                if (target == null) {
                    bindBossClientForOnlinePlayers();
                } else {
                    bindBossClient(target);
                }
            }
            case "clear" -> {
                if (target == null) {
                    clearClientEffects();
                } else {
                    clearClientEffects(target);
                }
            }
            default -> message(sender, "&e/cmend client status|bindboss|clear [player]");
        }
    }

    private int parseInt(String[] args, int index, int fallback) {
        try {
            return args.length > index ? Integer.parseInt(args[index]) : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private double parseDouble(String[] args, int index, double fallback) {
        try {
            return args.length > index ? Double.parseDouble(args[index]) : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private boolean isConfigured() {
        return !eventId.isBlank() && !worldName.isBlank() && requiredPlayers > 0;
    }

    private boolean hasSpawnAnchor() {
        return isConfigured() && coreLocation() != null;
    }

    private void setCore(Player player, int players) {
        if (players < config.minPlayers() || players > config.maxPlayers()) {
            message(player, "&cТребуется число игроков от " + config.minPlayers() + " до " + config.maxPlayers() + ".");
            return;
        }
        if (isConfigured() && phase != EventPhase.UNCONFIGURED) {
            message(player, "&cCore уже настроен. Сначала используй /cmend core remove confirm.");
            return;
        }
        if (!config.arenaWorld().equalsIgnoreCase(player.getWorld().getName())) {
            message(player, "&cCore должен находиться в настроенном event world: " + config.arenaWorld());
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType().isAir() || block.isPassable() || block.isLiquid()) {
            message(player, "&cНаведи прицел на реальный твёрдый блок в радиусе 8 блоков и повтори команду.");
            return;
        }
        if (!player.getWorld().equals(block.getWorld())) {
            message(player, "&cЦелевой блок должен находиться в event world.");
            return;
        }
        String originalBlockData = block.getBlockData().getAsString();
        boolean endWasAlreadyUnlocked = endUnlocked;
        EventSnapshot previousSnapshot = snapshot();
        EventLayoutState previousLayout = layoutState;
        eventId = UUID.randomUUID().toString();
        generation = Math.max(1L, generation + 1L);
        worldName = player.getWorld().getName();
        coreX = block.getX();
        coreY = block.getY();
        coreZ = block.getZ();
        coreBlockData = originalBlockData;
        requiredPlayers = players;
        arenaMinX = coreX - (int) Math.ceil(config.arenaRadius());
        arenaMaxX = coreX + (int) Math.ceil(config.arenaRadius());
        int verticalRadius = (int) Math.ceil(config.arenaVerticalRadius());
        arenaMinY = Math.max(player.getWorld().getMinHeight(), coreY - verticalRadius);
        arenaMaxY = Math.min(player.getWorld().getMaxHeight() - 1, coreY + verticalRadius);
        arenaMinZ = coreZ - (int) Math.ceil(config.arenaRadius());
        arenaMaxZ = coreZ + (int) Math.ceil(config.arenaRadius());
        layoutState = new EventLayoutState(
                new EventLayoutState.Point(worldName, arenaMinX, arenaMinY, arenaMinZ),
                new EventLayoutState.Point(worldName, arenaMaxX, arenaMaxY, arenaMaxZ),
                null, null, Map.of(), "UNSET", previousLayout.portalRoom());
        resourceRequirements.clear();
        resourceRequirements.putAll(config.resourceRequirements());
        depositedResources.clear();
        depositedResources.putAll(resourceRequirements);
        depositedResources.replaceAll((key, ignored) -> 0);
        pads.clear();
        resourceContributors.clear();
        participantUuids.clear();
        officialRewardRoster.clear();
        rewardStatuses.clear();
        rewardRequestsInFlight.clear();
        coreCharged = false;
        halfHealthTriggered = false;
        controlSpellUnlocked = false;
        finalDrainTriggered = false;
        finalDrainApplied = false;
        finalDrainTargets.clear();
        finalDrainAppliedPlayers.clear();
        bossKillerUuid = null;
        clearCombatAiState();
        lootIssuedEntityUuids.clear();
        // End access is a permanent WorldCore fact; creating a new local
        // event Core must never roll it back in the event snapshot.
        endUnlocked = endWasAlreadyUnlocked;
        officialBossDeathCommitted = false;
        bossLootCommitted = false;
        bossRewardStatus = BOSS_REWARDS_PENDING;
        bossRewardRecipientUuid = null;
        returnStoneStatus = "PENDING";
        victoryStep = "NONE";
        recoveryReason = "";
        activeWave = 0;
        padOccupants.clear();
        stateMachine = new EndEventStateMachine(EventPhase.UNCONFIGURED);
        phase = EventPhase.UNCONFIGURED;
        taskRegistry = new EventTaskRegistry(generation);
        try {
            calculateAndPlacePads(block.getWorld());
        } catch (RuntimeException invalidLayout) {
            restoreCoreAndPads();
            restoreBlock(block, originalBlockData);
            applySnapshot(previousSnapshot);
            layoutState = previousLayout;
            message(player, "&cCore не создан: layout рун не прошёл bounded-проверки; мир восстановлен.");
            getLogger().log(Level.WARNING, "Rift Core pad preflight rejected", invalidLayout);
            return;
        }
        if (!saveStateSync()) {
            restoreCoreAndPads();
            restoreBlock(block, originalBlockData);
            applySnapshot(previousSnapshot);
            layoutState = previousLayout;
            message(player, "&cСостояние не удалось durable-сохранить; мир оставлен без Core.");
            return;
        }
        forcePhase(EventPhase.COLLECTING, "core configured");
        rebuildPersistedVisuals();
        showArenaBoundary(player, DEFAULT_ARENA_PREVIEW_SECONDS);
        message(player, "&aRift Core настроен: &f" + players + " игроков, event=" + eventId);
    }

    private void calculateAndPlacePads(World world) {
        PadLayout.Result layout = PadLayout.compute(requiredPlayers, 0.0D, config.padRadii());
        if (!layout.valid() || layout.points().size() != requiredPlayers) {
            throw new IllegalStateException("Pad layout invalid: " + layout.reason());
        }
        if (world == null) {
            throw new IllegalStateException("event world is unavailable");
        }
        Set<String> coordinates = new HashSet<>();
        List<EventSnapshot.PadSnapshot> planned = new ArrayList<>();
        for (PadLayout.Point point : layout.points()) {
            Block padBlock = findSafePadBlock(world, point, coordinates);
            if (padBlock == null) {
                throw new IllegalStateException("no safe two-air-blocks over solid floor near "
                        + (coreX + point.blockX()) + ":" + coreY + ":" + (coreZ + point.blockZ()));
            }
            String coordinate = padBlock.getX() + ":" + padBlock.getY() + ":" + padBlock.getZ();
            coordinates.add(coordinate);
            String original = padBlock.getBlockData().getAsString();
            double dx = padBlock.getX() + 0.5D - (coreX + 0.5D);
            double dz = padBlock.getZ() + 0.5D - (coreZ + 0.5D);
            planned.add(new EventSnapshot.PadSnapshot(
                    padBlock.getX(), padBlock.getY(), padBlock.getZ(), Math.sqrt(dx * dx + dz * dz),
                    Math.atan2(dz, dx), original));
        }
        // The pad coordinate is the air block above the floor.  Keep both
        // blocks vanilla and render only a thin event overlay on the floor.
        // No block material is replaced here.
        pads.addAll(planned);
    }

    /**
     * Find the nearest intact vanilla position for a rune.  The admin may
     * target a block in a cave, on a slope, or beside an old structure, so a
     * single mathematically perfect ring coordinate is not a valid reason to
     * reject the Core.  We search a bounded neighbourhood and never write a
     * block: the pad remains two passable blocks above a real solid floor.
     */
    private Block findSafePadBlock(World world, PadLayout.Point point, Set<String> coordinates) {
        int desiredX = coreX + point.blockX();
        int desiredZ = coreZ + point.blockZ();
        int searchRadius = Math.min(12, Math.max(4, (int) Math.ceil(config.arenaRadius() / 2.0D)));
        int[] yOffsets = {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5, -6, 6};
        List<Block> candidates = new ArrayList<>();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (dx * dx + dz * dz > searchRadius * searchRadius) {
                    continue;
                }
                for (int yOffset : yOffsets) {
                    Block candidate = world.getBlockAt(desiredX + dx, coreY + yOffset, desiredZ + dz);
                    String coordinate = candidate.getX() + ":" + candidate.getY() + ":" + candidate.getZ();
                    if (coordinates.contains(coordinate)
                            || (candidate.getX() == coreX && candidate.getY() == coreY && candidate.getZ() == coreZ)
                            || !isArenaLocation(candidate.getLocation())
                            || isGateLocation(candidate.getLocation())
                            || !candidate.isPassable() || candidate.isLiquid()
                            || candidate.getType() == Material.FIRE) {
                        continue;
                    }
                    Block head = candidate.getRelative(BlockFace.UP);
                    Block floor = candidate.getRelative(BlockFace.DOWN);
                    if ((floor.getX() == coreX && floor.getY() == coreY && floor.getZ() == coreZ)
                            || !head.isPassable() || head.isLiquid()
                            || !floor.getType().isSolid() || floor.isLiquid()
                            || floor.getType() == Material.FIRE) {
                        continue;
                    }
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator
                .comparingDouble((Block candidate) -> {
                    double dx = candidate.getX() - desiredX;
                    double dz = candidate.getZ() - desiredZ;
                    double dy = candidate.getY() - coreY;
                    return dx * dx + dz * dz + dy * dy * 4.0D;
                })
                .thenComparingInt(Block::getY)
                .thenComparingInt(Block::getX)
                .thenComparingInt(Block::getZ));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void removeCore(CommandSender sender) {
        if (!isConfigured()) {
            message(sender, "&eCore уже удалён.");
            return;
        }
        String removedEventId = eventId;
        cancelSessionTasks();
        clearBossOnly();
        clearWaveEntities();
        // Core removal is a hard session boundary.  Old combat entities can
        // carry a previous generation after a restart, so remove every entity
        // belonging to this event id, not only the current generation.
        cleanupOwnedEntitiesForEvent(removedEventId);
        restoreCoreAndPads();
        clearClientEffects();
        eventId = "";
        requiredPlayers = 0;
        pads.clear();
        depositedResources.clear();
        resourceContributors.clear();
        participantUuids.clear();
        officialRewardRoster.clear();
        rewardStatuses.clear();
        rewardRequestsInFlight.clear();
        finalDrainTargets.clear();
        finalDrainAppliedPlayers.clear();
        coreCharged = false;
        bossKillerUuid = null;
        bossLootCommitted = false;
        bossRewardStatus = BOSS_REWARDS_PENDING;
        bossRewardRecipientUuid = null;
        clearCombatAiState();
        lootIssuedEntityUuids.clear();
        stateMachine = new EndEventStateMachine(EventPhase.UNCONFIGURED);
        phase = EventPhase.UNCONFIGURED;
        EventLayoutState previousLayout = layoutState;
        layoutState = new EventLayoutState(null, null, null, null, Map.of(), "UNSET", previousLayout.portalRoom());
        releaseOverlayChunkTickets();
        saveStateSync();
        message(sender, "&aCore и event-owned руны восстановлены по сохранённым block data.");
    }

    private void resetEventSafely(CommandSender sender) {
        if (phase == EventPhase.UNLOCKED || endUnlocked) {
            message(sender, "&cUNLOCKED нельзя reset-нуть: End unlock permanent.");
            return;
        }
        if (phase == EventPhase.WAVE_1 || phase == EventPhase.INTERMISSION_1 || phase == EventPhase.WAVE_2
                || phase == EventPhase.INTERMISSION_2 || phase == EventPhase.WAVE_3 || phase == EventPhase.BOSS_ACTIVE
                || phase == EventPhase.FINAL_DRAIN || phase == EventPhase.FINAL_RITUAL
                || phase == EventPhase.FINAL_WAVE || phase == EventPhase.BOSS_FINISH
                || phase == EventPhase.VICTORY_PROCESSING || phase == EventPhase.VICTORY) {
            message(sender, "&cСначала отмените бой через /cmend ritual cancel.");
            return;
        }
        depositedResources.replaceAll((key, ignored) -> 0);
        coreCharged = false;
        participantUuids.clear();
        officialRewardRoster.clear();
        rewardStatuses.clear();
        finalDrainTargets.clear();
        finalDrainAppliedPlayers.clear();
        bossLootCommitted = false;
        bossRewardStatus = BOSS_REWARDS_PENDING;
        bossRewardRecipientUuid = null;
        forcePhase(EventPhase.COLLECTING, "safe admin reset");
        saveStateSync();
        message(sender, "&aСброшен только event progress; world и player data не затронуты.");
    }

    private void updateCoreChargeState() {
        boolean complete = allResourcesComplete();
        if (complete && !coreCharged) {
            coreCharged = true;
            if (phase == EventPhase.COLLECTING) {
                forcePhase(EventPhase.READY_FOR_PLAYERS, "resources complete");
            }
            rebuildPersistedVisuals();
            getLogger().info("COLLECT_COMPLETE event=" + eventId + " generation=" + generation);
        } else if (!complete && coreCharged) {
            coreCharged = false;
            if (phase == EventPhase.READY_FOR_PLAYERS || phase == EventPhase.COUNTDOWN) {
                cancelRitual("resources became incomplete");
                forcePhase(EventPhase.COLLECTING, "resource regression");
            }
            rebuildPersistedVisuals();
        }
    }

    private boolean allResourcesComplete() {
        if (resourceRequirements.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : resourceRequirements.entrySet()) {
            if (depositedResources.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private String resourceProgressText() {
        return ResourceProgressFormatter.format(resourceRequirements, depositedResources);
    }

    private Location coreLocation() {
        return coreBlockTopLocation();
    }

    private Location coreBlockTopLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, coreX + 0.5D, coreY + 1.0D, coreZ + 0.5D);
    }

    private boolean isCoreBlockPosition(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        return location.getBlockX() == coreX
                && location.getBlockZ() == coreZ
                && (location.getBlockY() == coreY || location.getBlockY() == coreY + 1);
    }

    private String coreLocationText() {
        return worldName.isBlank() ? "unset" : worldName + " " + coreX + "," + coreY + "," + coreZ;
    }

    private String locationText(Location location) {
        return location == null || location.getWorld() == null
                ? "unset" : location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private boolean sameCore(Block block) {
        return block != null && isConfigured() && block.getWorld().getName().equals(worldName)
                && block.getX() == coreX && block.getY() == coreY && block.getZ() == coreZ;
    }

    private void openCoreRemovalConfirm(Player player) {
        if (player == null || (!isAdmin(player) && !player.isOp())) {
            return;
        }
        CoreRemovalConfirmHolder holder = new CoreRemovalConfirmHolder(
                player.getUniqueId(), eventId, generation);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Подтверждение снятия Core", NamedTextColor.DARK_RED));
        holder.attach(inventory);
        ItemStack filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(11, menuItem(Material.LIME_WOOL, "§aПодтвердить снятие Core", List.of(
                "§7Вернуть исходный блок на место.",
                "§7Удалить руны и текст Core.",
                "§7Остановить текущий сеанс события.",
                "§cПотребуется заново установить Core.")));
        inventory.setItem(15, menuItem(Material.RED_WOOL, "§cОтмена", List.of("§7Ничего не менять.")));
        player.openInventory(inventory);
        message(player, "&eCore защищён. Подтверди снятие в открывшемся GUI.");
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEndEventInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CoreRemovalConfirmHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.ownerUuid().equals(player.getUniqueId())
                || (!isAdmin(player) && !player.isOp())) {
            player.closeInventory();
            message(player, "&cЭто подтверждение принадлежит другому администратору.");
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        if (event.getRawSlot() == 11) {
            player.closeInventory();
            if (!isConfigured() || !eventId.equals(holder.eventId()) || generation != holder.generation()) {
                message(player, "&cСеанс Core уже изменился; подтверждение устарело.");
                return;
            }
            removeCore(player);
        } else if (event.getRawSlot() == 15) {
            player.closeInventory();
            message(player, "&7Снятие Core отменено.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEndEventInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CoreRemovalConfirmHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaBlockBreak(BlockBreakEvent event) {
        if (sameCore(event.getBlock())) {
            event.setCancelled(true);
            if (event.getPlayer().isOp() || isAdmin(event.getPlayer())) {
                openCoreRemovalConfirm(event.getPlayer());
            } else {
                event.getPlayer().sendActionBar(Component.text(
                        "Ядро защищено: снять его может только администратор", NamedTextColor.RED));
            }
            return;
        }
        if (phase != EventPhase.UNLOCKED && (isArenaLocation(event.getBlock().getLocation())
                || isGateLocation(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Арена Разлома защищена до победы", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaBlockPlace(BlockPlaceEvent event) {
        if (phase != EventPhase.UNLOCKED && (isArenaLocation(event.getBlock().getLocation())
                || isGateLocation(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Арена Разлома защищена до победы", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaBlockExplode(BlockExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> isProtectedEventLocation(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtectedEventLocation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaPistonExtend(BlockPistonExtendEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())
                || event.getBlocks().stream().anyMatch(block -> isProtectedEventLocation(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaPistonRetract(BlockPistonRetractEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())
                || event.getBlocks().stream().anyMatch(block -> isProtectedEventLocation(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaFluidFlow(BlockFromToEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())
                || isProtectedEventLocation(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaFire(BlockBurnEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaFade(BlockFadeEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaInventoryMove(InventoryMoveItemEvent event) {
        if (isProtectedInventory(event.getSource()) || isProtectedInventory(event.getDestination())
                || isProtectedInventory(event.getInitiator())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaInventoryPickup(InventoryPickupItemEvent event) {
        if (isProtectedInventory(event.getInventory())
                || isProtectedEventLocation(event.getItem().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOwnedDisplayDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TextDisplay || entity instanceof ItemDisplay) {
            String kind = readString(entity, keyKind);
            if (ownedEntities.containsKey(entity.getUniqueId())
                    && (EVENT_KIND_DISPLAY.equals(kind) || EVENT_KIND_CORE.equals(kind)
                    || EVENT_KIND_PAD.equals(kind))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)
                || phase == EventPhase.UNLOCKED) {
            return;
        }
        if (isArenaLocation(victim.getLocation()) && isArenaLocation(attacker.getLocation())
                && victim.getWorld().equals(attacker.getWorld())) {
            event.setCancelled(true);
        }
    }

    private boolean isArenaLocation(Location location) {
        if (!isConfigured() || location == null || location.getWorld() == null
                || !location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= arenaMinX && x <= arenaMaxX && y >= arenaMinY && y <= arenaMaxY
                && z >= arenaMinZ && z <= arenaMaxZ;
    }

    private boolean isProtectedEventLocation(Location location) {
        return phase != EventPhase.UNLOCKED
                && (isArenaLocation(location) || isGateLocation(location));
    }

    private boolean isProtectedInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof org.bukkit.block.BlockState state) {
            return isProtectedEventLocation(state.getLocation());
        }
        if (holder instanceof Entity entity) {
            return isProtectedEventLocation(entity.getLocation());
        }
        return false;
    }

    private boolean isGateLocation(Location location) {
        if (location == null || location.getWorld() == null || layoutState.gatePos1() == null
                || layoutState.gatePos2() == null) {
            return false;
        }
        EventLayoutState.Point first = layoutState.gatePos1();
        EventLayoutState.Point second = layoutState.gatePos2();
        return first.world().equalsIgnoreCase(location.getWorld().getName())
                && location.getBlockX() >= Math.min(first.x(), second.x())
                && location.getBlockX() <= Math.max(first.x(), second.x())
                && location.getBlockY() >= Math.min(first.y(), second.y())
                && location.getBlockY() <= Math.max(first.y(), second.y())
                && location.getBlockZ() >= Math.min(first.z(), second.z())
                && location.getBlockZ() <= Math.max(first.z(), second.z());
    }

    private void restoreCoreAndPads() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        Block core = world.getBlockAt(coreX, coreY, coreZ);
        if (!coreBlockData.isBlank()) {
            restoreBlock(core, coreBlockData);
        }
        for (EventSnapshot.PadSnapshot pad : pads) {
            Block block = world.getBlockAt(pad.x(), pad.y(), pad.z());
            if (!pad.originalBlockData().isBlank()) {
                restoreBlock(block, pad.originalBlockData());
            }
        }
        cleanupOwnedEntities(eventId, generation);
    }

    private void restoreBlock(Block block, String blockData) {
        if (block == null || blockData == null || blockData.isBlank()) {
            return;
        }
        try {
            BlockData data = Bukkit.createBlockData(blockData);
            block.setBlockData(data, false);
        } catch (IllegalArgumentException error) {
            getLogger().warning("Refused to restore invalid event block data at " + block.getLocation());
        }
    }

    private void rebuildPersistedVisuals() {
        if (!bootstrapped || !isConfigured()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Configured End Event world is unavailable: " + worldName);
            return;
        }
        if (phase == EventPhase.UNLOCKED) {
            releaseOverlayChunkTickets();
        } else {
            ensureOverlayChunksLoaded(world);
        }
        restoreLegacyMaterializedBlocks(world);
        if (phase == EventPhase.UNLOCKED) {
            removeOwnedVisuals();
            return;
        }
        Block core = world.getBlockAt(coreX, coreY, coreZ);
        if (core.getType().isAir() || core.isPassable() || core.isLiquid()) {
            getLogger().warning("Configured Core target is no longer a solid block; refusing to create a floating overlay.");
            return;
        }
        removeOwnedVisuals();
        spawnCoreOverlay(world, core);
        for (EventSnapshot.PadSnapshot pad : pads) {
            spawnRuneOverlay(world, pad);
        }
        spawnCoreText(world);
        getLogger().info("END_EVENT_PHYSICAL_VISUALS core=" + core.getType()
                + " pads=VANILLA_FLOOR_BLOCKS displays=CORE_OVERLAY_AND_RUNE_OVERLAYS");
    }

    /**
     * ItemDisplay entities are stored in their chunks. A configured Core may
     * be far from spawn with no player nearby, so an unloaded chunk would
     * make the visual look missing and trigger an endless rebuild loop. Keep
     * only the Core and rune chunks loaded for the lifetime of this event.
     */
    private void ensureOverlayChunksLoaded(World world) {
        world.removePluginChunkTickets(this);
        Set<String> requiredChunks = new LinkedHashSet<>();
        requiredChunks.add((coreX >> 4) + ":" + (coreZ >> 4));
        for (EventSnapshot.PadSnapshot pad : pads) {
            requiredChunks.add((pad.x() >> 4) + ":" + (pad.z() >> 4));
        }
        for (String key : requiredChunks) {
            String[] parts = key.split(":", 2);
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);
            world.getChunkAt(chunkX, chunkZ, true).addPluginChunkTicket(this);
        }
    }

    private void releaseOverlayChunkTickets() {
        for (World world : Bukkit.getWorlds()) {
            world.removePluginChunkTickets(this);
        }
    }

    private void restoreLegacyMaterializedBlocks(World world) {
        Block core = world.getBlockAt(coreX, coreY, coreZ);
        Material originalCoreMaterial = materialFromBlockData(coreBlockData);
        if (isLegacyEventMaterial(core.getType())
                && originalCoreMaterial != null && originalCoreMaterial != core.getType()) {
            restoreBlock(core, coreBlockData);
            getLogger().info("END_EVENT_LEGACY_BLOCK_RESTORED kind=CORE material=" + originalCoreMaterial);
        }
        for (EventSnapshot.PadSnapshot pad : pads) {
            Block block = world.getBlockAt(pad.x(), pad.y(), pad.z());
            Material originalPadMaterial = materialFromBlockData(pad.originalBlockData());
            if (block.getType() == Material.CRYING_OBSIDIAN
                    && originalPadMaterial != null && originalPadMaterial != Material.CRYING_OBSIDIAN) {
                restoreBlock(block, pad.originalBlockData());
                getLogger().info("END_EVENT_LEGACY_BLOCK_RESTORED kind=PAD x=" + pad.x()
                        + " y=" + pad.y() + " z=" + pad.z());
            }
        }
    }

    private Material materialFromBlockData(String blockData) {
        if (blockData == null || blockData.isBlank()) {
            return null;
        }
        try {
            return Bukkit.createBlockData(blockData).getMaterial();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private boolean isLegacyEventMaterial(Material material) {
        return material == Material.RESPAWN_ANCHOR || material == Material.CRYING_OBSIDIAN;
    }

    private void removeOwnedVisuals() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        int removed = 0;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            String kind = readString(entity, keyKind);
            boolean visual = EVENT_KIND_DISPLAY.equals(kind) || EVENT_KIND_CORE.equals(kind)
                    || EVENT_KIND_PAD.equals(kind);
            boolean currentSession = ownedBySession(entity, eventId, generation);
            boolean legacyVisualInArena = visual && isArenaLocation(entity.getLocation());
            if (visual && (currentSession || legacyVisualInArena)) {
                entity.remove();
                ownedEntities.remove(entity.getUniqueId());
                removed++;
            }
        }
        if (removed > 0) {
            getLogger().info("END_EVENT_VISUAL_CLEANUP removed=" + removed + " scope=event-arena");
        }
    }

    private void spawnCoreOverlay(World world, Block core) {
        // The target block remains the real block selected by the admin.  Keep
        // the display at the block centre and let the tiny shell expansion
        // expose every face without moving the visual onto the block above.
        // The vanilla block itself is still preserved and restored from its
        // original BlockData when the event is removed.
        Location displayLocation = coreOverlayLocation(core);
        ItemDisplay display = world.spawn(displayLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(overlayItem(coreCharged ? MODEL_CORE_CHARGED_OVERLAY : MODEL_CORE_OVERLAY,
                    coreCharged ? "end_event_core_charged" : "end_event_core"));
            // Keep the custom shell readable on any vanilla block and at any
            // time of day.  Without an explicit light level the display can
            // look like an untextured black cube even though the resource
            // pack model is present.
            entity.setBrightness(new Display.Brightness(15, 15));
            // The event models are authored as raw block-space cubes.  FIXED
            // applies the item-model fixed transform again in 1.21.1, which
            // leaves the display depth-tested inside the real block.  NONE
            // keeps the cube at the exact block anchor and makes its faces
            // render over the vanilla block without replacing that block.
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setViewRange(64.0F);
            // A display exactly inside an opaque block is depth-tested away.
            // Keep its origin at the block centre, but give the shell a small
            // symmetric margin so all six faces remain visible without moving
            // the Core onto the block above.
            entity.setDisplayWidth(1.10F);
            entity.setDisplayHeight(1.10F);
            entity.setPersistent(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setShadowRadius(0.0F);
            entity.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(1.10F, 1.10F, 1.10F), new AxisAngle4f()));
        });
        tag(display, EVENT_KIND_CORE, 0, false);
    }

    private void spawnRuneOverlay(World world, EventSnapshot.PadSnapshot pad) {
        Block floor = world.getBlockAt(pad.x(), pad.y() - 1, pad.z());
        if (!floor.getType().isSolid() || floor.isLiquid()) {
            getLogger().warning("Refused to create a floating rune overlay: floor is not solid at "
                    + floor.getLocation());
            return;
        }
        // The pad coordinate is the air block above this floor.  The custom
        // rune model is a full-width, low slab whose lower face is anchored
        // to the floor top; the occupied variant is selected from the live
        // pad roster so a player can see the registration immediately.
        Location displayLocation = runeOverlayLocation(floor);
        ItemDisplay display = world.spawn(displayLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(runeOverlayItem(pad));
            entity.setBrightness(new Display.Brightness(15, 15));
            // Runes use the same raw block-space model convention as the Core;
            // NONE is required for the thin slab to render on the floor top.
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setViewRange(64.0F);
            entity.setDisplayWidth(1.0F);
            entity.setDisplayHeight(0.25F);
            entity.setPersistent(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setShadowRadius(0.0F);
            entity.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(1.0F, 1.0F, 1.0F), new AxisAngle4f()));
        });
        tag(display, EVENT_KIND_PAD, 0, false);
    }

    private Location coreOverlayLocation(Block core) {
        // ItemDisplay block models are centred on their display origin.  A
        // centre anchor leaves the opaque vanilla block in front of the
        // shell, so only the top face survives depth testing.  Anchor the
        // model at the block's top plane: its lower half covers the real
        // block and its tiny 1.10 scale margin keeps all faces visible
        // without replacing or moving the vanilla block.
        return core.getLocation().add(0.5D, 1.0D, 0.5D);
    }

    private Location runeOverlayLocation(Block floor) {
        return floor.getLocation().add(0.5D, 1.0D, 0.5D);
    }

    private boolean sameRuneOverlayBlock(ItemDisplay display, Block floor) {
        if (display == null || floor == null || display.getWorld() == null
                || !display.getWorld().equals(floor.getWorld())) {
            return false;
        }
        Location location = display.getLocation();
        return location.getBlockX() == floor.getX()
                && location.getBlockY() == floor.getY() + 1
                && location.getBlockZ() == floor.getZ();
    }

    private boolean sameCoreOverlayBlock(ItemDisplay display, Block core) {
        if (display == null || core == null || display.getWorld() == null
                || !display.getWorld().equals(core.getWorld())) {
            return false;
        }
        Location location = display.getLocation();
        return location.getBlockX() == core.getX()
                && location.getBlockY() == core.getY() + 1
                && location.getBlockZ() == core.getZ();
    }

    private boolean hasCoreOverlay(World world, Block core) {
        return findCoreOverlay(world, core) != null;
    }

    private ItemDisplay findCoreOverlay(World world, Block core) {
        if (world == null || core == null) {
            return null;
        }
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ItemDisplay display
                    && EVENT_KIND_CORE.equals(readString(entity, keyKind))
                    && ownedBySession(entity, eventId, generation)
                    && sameCoreOverlayBlock(display, core)) {
                return display;
            }
        }
        return null;
    }

    private ItemDisplay findRuneOverlay(World world, Block floor) {
        if (world == null || floor == null) {
            return null;
        }
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ItemDisplay display
                    && EVENT_KIND_PAD.equals(readString(entity, keyKind))
                    && ownedBySession(entity, eventId, generation)
                    && sameRuneOverlayBlock(display, floor)) {
                return display;
            }
        }
        return null;
    }

    private ItemStack runeOverlayItem(EventSnapshot.PadSnapshot pad) {
        boolean occupied = padOccupants.containsKey(padKey(pad));
        return overlayItem(occupied ? MODEL_RUNE_OVERLAY_OCCUPIED : MODEL_RUNE_OVERLAY,
                occupied ? "end_event_pad_occupied" : "end_event_pad");
    }

    private void refreshRuneOverlayVisuals() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        for (EventSnapshot.PadSnapshot pad : pads) {
            Block floor = world.getBlockAt(pad.x(), pad.y() - 1, pad.z());
            ItemDisplay display = findRuneOverlay(world, floor);
            if (display != null) {
                display.setItemStack(runeOverlayItem(pad));
            }
        }
    }

    private ItemStack overlayItem(int customModelData, String modelId) {
        ItemStack item = new ItemStack(EVENT_OVERLAY_ITEM);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setCustomModelData(customModelData);
        meta.setDisplayName("§f" + modelId);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private int overlayModelData(ItemDisplay display) {
        if (display == null) {
            return 0;
        }
        ItemStack item = display.getItemStack();
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer customModelData = item.getItemMeta().getCustomModelData();
        return customModelData == null ? 0 : customModelData;
    }

    private String visualStatusText() {
        if (!isConfigured()) {
            return "coreOverlay=false coreModel=0 runes=0/0 occupied=0";
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return "coreOverlay=false coreModel=0 runes=0/" + pads.size() + " occupied=0 world=unloaded";
        }
        ItemDisplay coreDisplay = findCoreOverlay(world, world.getBlockAt(coreX, coreY, coreZ));
        int runeCount = 0;
        int occupiedCount = 0;
        for (EventSnapshot.PadSnapshot pad : pads) {
            ItemDisplay rune = findRuneOverlay(world, world.getBlockAt(pad.x(), pad.y() - 1, pad.z()));
            if (rune == null) {
                continue;
            }
            runeCount++;
            if (overlayModelData(rune) == MODEL_RUNE_OVERLAY_OCCUPIED) {
                occupiedCount++;
            }
        }
        return "coreOverlay=" + (coreDisplay != null)
                + " coreModel=" + overlayModelData(coreDisplay)
                + " runes=" + runeCount + "/" + pads.size()
                + " occupied=" + occupiedCount;
    }

    private void spawnCoreText(World world) {
        Location location = new Location(world, coreX + 0.5D, coreY + 2.25D, coreZ + 0.5D);
        TextDisplay display = world.spawn(location, TextDisplay.class);
        tag(display, EVENT_KIND_DISPLAY, 0, false);
        display.setText(coreCharged ? "§5РАЗЛОМ ЗАРЯЖЕН\n§7Соберите игроков на рунах" : "§5РАЗЛОМ\n§7" + resourceProgressText().replace(", ", "\n§7"));
        display.setBillboard(TextDisplay.Billboard.CENTER);
        display.setLineWidth(180);
        display.setTransformation(new Transformation(
                new Vector3f(), new AxisAngle4f(), new Vector3f(0.45F, 0.45F, 0.45F), new AxisAngle4f()));
        ownedEntities.put(display.getUniqueId(), display);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoreInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !sameCore(event.getClickedBlock()) || phase != EventPhase.COLLECTING) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!coreInteractionGuard.accept(Bukkit.getCurrentTick(), eventId, generation, player.getUniqueId())) {
            return;
        }
        registerParticipant(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR || held.getAmount() < 1) {
            return;
        }
        String material = held.getType().name();
        if (!resourceRequirements.containsKey(material)) {
            player.sendActionBar(Component.text("Нужен ресурс из списка Разлома", NamedTextColor.RED));
            return;
        }
        boolean official = rewardService != null && rewardService.isOfficialArtifact(held);
        boolean customProtected = held.hasItemMeta() && !held.getItemMeta().getPersistentDataContainer().getKeys().isEmpty();
        if (!CoreDepositMath.canAccept(true, material, resourceRequirements.keySet(), official, customProtected)) {
            player.sendActionBar(Component.text("Этот custom item нельзя пожертвовать", NamedTextColor.RED));
            return;
        }
        int required = resourceRequirements.get(material);
        int progress = depositedResources.getOrDefault(material, 0);
        int accepted = CoreDepositMath.acceptedAmount(held.getAmount(), required, progress);
        if (accepted <= 0) {
            player.sendActionBar(Component.text("Этот ресурс уже заполнен", NamedTextColor.YELLOW));
            return;
        }
        DepositJournal.Entry entry = new DepositJournal.Entry(
                eventId + ":" + UUID.randomUUID(), player.getUniqueId(), held.getType(), accepted, progress + accepted, "PREPARED");
        if (!depositJournal.prepare(entry)) {
            player.sendMessage(ChatColor.RED + "Внесение не записано durable; предмет не изменён.");
            return;
        }
        EventSnapshot before = snapshot();
        int previousAmount = held.getAmount();
        if (!depositJournal.markItemRemoved(entry)) {
            depositJournal.refund(entry);
            player.sendMessage(ChatColor.RED + "Журнал внесения недоступен; предмет не изменён.");
            return;
        }
        held.setAmount(previousAmount - accepted);
        depositedResources.put(material, progress + accepted);
        resourceContributors.add(player.getUniqueId());
        updateCoreChargeState();
        if (!saveStateSync()) {
            applySnapshot(before);
            if (restoreDepositItem(player, held, previousAmount)) {
                depositJournal.refund(entry);
                player.sendMessage(ChatColor.RED + "Состояние Core не сохранилось; ресурс возвращён.");
            } else {
                depositJournal.refundPending(entry);
                player.sendMessage(ChatColor.RED + "Состояние Core не сохранилось; возврат ресурса ожидает свободный слот.");
            }
            return;
        }
        if (!depositJournal.commit(entry)) {
            getLogger().warning("Deposit state was saved but journal commit is pending: " + entry.id());
        }
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        playerCategory(player, "resource_contributor");
        rebuildPersistedVisuals();
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.2F);
        player.sendActionBar(Component.text("Внесено " + accepted + " " + material + ". " + resourceProgressText(), NamedTextColor.LIGHT_PURPLE));
    }

    private ItemStack heldWithAmount(ItemStack held, int amount) {
        ItemStack restored = held.clone();
        restored.setAmount(Math.max(1, amount));
        return restored;
    }

    private boolean restoreDepositItem(Player player, ItemStack original, int amount) {
        if (player == null || original == null || original.getType() == Material.AIR || amount < 1) {
            return false;
        }
        ItemStack restored = heldWithAmount(original, amount);
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current == null || current.getType() == Material.AIR || current.isSimilar(original)) {
            player.getInventory().setItemInMainHand(restored);
            return true;
        }
        if (!inventoryCanFitExact(player.getInventory(), restored)) {
            return false;
        }
        return player.getInventory().addItem(restored).isEmpty();
    }

    private boolean inventoryCanFitExact(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null || item.getType() == Material.AIR) {
            return false;
        }
        int remaining = item.getAmount();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (remaining < 1) {
                return true;
            }
            if (stack != null && stack.isSimilar(item)) {
                remaining -= Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            }
        }
        for (ItemStack stack : inventory.getStorageContents()) {
            if (remaining < 1) {
                return true;
            }
            if (stack == null || stack.getType() == Material.AIR) {
                remaining -= item.getMaxStackSize();
            }
        }
        return remaining <= 0;
    }

    private void recoverUnresolvedDeposits() {
        for (DepositJournal.Entry entry : depositJournal.unresolved()) {
            Player player = Bukkit.getPlayer(entry.playerUuid());
            int progress = depositedResources.getOrDefault(entry.material().name(), 0);
            if (progress >= entry.afterProgress()) {
                depositJournal.commit(entry);
                continue;
            }
            if (player != null && player.isOnline()) {
                ItemStack refund = new ItemStack(entry.material(), entry.amount());
                if (inventoryCanFitExact(player.getInventory(), refund)
                        && player.getInventory().addItem(refund).isEmpty()) {
                    depositJournal.refund(entry);
                } else {
                    depositJournal.refundPending(entry);
                    getLogger().warning("DEPOSIT_REFUND_PENDING event=" + eventId
                            + " player=" + entry.playerUuid() + " material=" + entry.material()
                            + " amount=" + entry.amount());
                }
            }
        }
    }

    private void registerParticipant(Player player) {
        if (player == null || !isConfigured()) {
            return;
        }
        if (participantUuids.add(player.getUniqueId())) {
            saveStateAsync();
        }
    }

    private void playerCategory(Player player, String category) {
        if (player != null) {
            playerCategories.put(player.getUniqueId(), category);
        }
    }

    private void tick() {
        if (!bootstrapped || !isEnabled()) {
            return;
        }
        expireControlEffects();
        updatePadOccupancy();
        updateCombatHelpers();
        tickWaveMobAi();
        tickMiniBosses();
        if (testCombatAiMode && liveBoss() != null) {
            tickBoss();
        }
        switch (phase) {
            case COUNTDOWN -> tickCountdown();
            case INTERMISSION_1, INTERMISSION_2 -> tickIntermission();
            case WAVE_1, WAVE_2, WAVE_3, FINAL_WAVE -> tickWaveCompletion();
            case BOSS_ACTIVE, BOSS_FINISH -> tickBoss();
            case FINAL_DRAIN, FINAL_RITUAL -> tickFinalRitual();
            default -> {
            }
        }
        if (endUnlocked && (phase == EventPhase.VICTORY_PROCESSING || phase == EventPhase.VICTORY
                || phase == EventPhase.UNLOCKED)
                && System.currentTimeMillis() >= nextVictoryRetryMillis) {
            nextVictoryRetryMillis = System.currentTimeMillis() + 5_000L;
            issueVictoryRewards();
        }
        tickShardChannels();
    }

    private void updatePadOccupancy() {
        maintainRitualVisuals();
        Map<String, UUID> previousOccupants = new LinkedHashMap<>(padOccupants);
        padOccupants.clear();
        if (!coreCharged || (phase != EventPhase.READY_FOR_PLAYERS && phase != EventPhase.COUNTDOWN)) {
            if (!previousOccupants.isEmpty()) {
                refreshRuneOverlayVisuals();
            }
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (!previousOccupants.isEmpty()) {
                refreshRuneOverlayVisuals();
            }
            return;
        }
        Set<UUID> assigned = new HashSet<>();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(player -> player.getUniqueId().toString()));
        for (EventSnapshot.PadSnapshot pad : pads) {
            Location padLocation = new Location(world, pad.x() + 0.5D, pad.y(), pad.z() + 0.5D);
            Player closest = players.stream()
                    .filter(player -> player.getWorld().equals(world) && !player.isDead()
                            && player.getHealth() > 0.0D
                            && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                            && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                            && !assigned.contains(player.getUniqueId()))
                    .filter(player -> player.getLocation().distanceSquared(padLocation)
                            <= config.padOccupancyRadius() * config.padOccupancyRadius())
                    .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(padLocation)))
                    .orElse(null);
            if (closest != null) {
                assigned.add(closest.getUniqueId());
                padOccupants.put(padKey(pad), closest.getUniqueId());
                registerParticipant(closest);
            }
        }
        if (!previousOccupants.equals(padOccupants)
                || (coreCharged && (phase == EventPhase.READY_FOR_PLAYERS || phase == EventPhase.COUNTDOWN))) {
            refreshRuneOverlayVisuals();
        }
        if (padOccupants.size() == requiredPlayers) {
            beginCountdownIfReady();
        } else if (phase == EventPhase.COUNTDOWN) {
            cancelRitual("pad occupancy changed");
        }
    }

    private void maintainRitualVisuals() {
        if (!bootstrapped || !isConfigured() || phase == EventPhase.UNLOCKED || pads.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        Block core = world.getBlockAt(coreX, coreY, coreZ);
        if (!hasCoreOverlay(world, core)) {
            if (System.currentTimeMillis() >= nextRitualVisualRepairMillis) {
                nextRitualVisualRepairMillis = System.currentTimeMillis() + 1_000L;
                getLogger().warning("END_EVENT_CORE_VISUAL_REBUILD event=" + eventId
                        + " phase=" + phase + " reason=loaded_entity_missing");
                rebuildPersistedVisuals();
            }
            return;
        }
        if (!coreCharged || (phase != EventPhase.READY_FOR_PLAYERS && phase != EventPhase.COUNTDOWN)) {
            return;
        }
        int missing = 0;
        for (EventSnapshot.PadSnapshot pad : pads) {
            Block floor = world.getBlockAt(pad.x(), pad.y() - 1, pad.z());
            if (findRuneOverlay(world, floor) == null) {
                missing++;
            }
        }
        if (missing > 0 && System.currentTimeMillis() >= nextRitualVisualRepairMillis) {
            nextRitualVisualRepairMillis = System.currentTimeMillis() + 1_000L;
            getLogger().warning("END_EVENT_RUNE_VISUAL_REBUILD event=" + eventId
                    + " phase=" + phase + " missing=" + missing + " expected=" + pads.size());
            rebuildPersistedVisuals();
        }
    }

    private String padKey(EventSnapshot.PadSnapshot pad) {
        return pad.x() + ":" + pad.y() + ":" + pad.z();
    }

    private boolean isEventPad(Block block) {
        return block != null && isConfigured() && block.getWorld() != null
                && block.getWorld().getName().equalsIgnoreCase(worldName)
                && pads.stream().anyMatch(pad -> pad.x() == block.getX()
                        && (pad.y() == block.getY() || pad.y() - 1 == block.getY())
                        && pad.z() == block.getZ());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEventPadInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && isEventPad(event.getClickedBlock())
                && phase != EventPhase.UNLOCKED) {
            event.setCancelled(true);
        }
    }

    private void beginCountdownIfReady() {
        if (phase != EventPhase.READY_FOR_PLAYERS || padOccupants.size() != requiredPlayers) {
            return;
        }
        phaseDeadlineMillis = System.currentTimeMillis() + config.countdownSeconds() * 1000L;
        if (transition(EventPhase.COUNTDOWN, "all unique pads occupied", eventId + ":countdown")) {
            getLogger().info("RITUAL_STARTED event=" + eventId + " generation=" + generation);
        }
    }

    private void tickCountdown() {
        if (phaseDeadlineMillis <= 0L) {
            cancelRitual("countdown deadline missing");
            return;
        }
        long remaining = Math.max(0L, phaseDeadlineMillis - System.currentTimeMillis());
        int seconds = (int) Math.ceil(remaining / 1000.0D);
        if (seconds > 0 && seconds <= config.countdownSeconds()) {
            for (UUID playerUuid : padOccupants.values()) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.sendActionBar(Component.text("Ритуал: " + seconds, NamedTextColor.LIGHT_PURPLE));
                }
            }
        }
        if (remaining > 0L) {
            return;
        }
        if (padOccupants.size() != requiredPlayers) {
            cancelRitual("countdown finished without full roster");
            return;
        }
        try {
            RewardRoster roster = RewardRoster.commitExactly(new HashSet<>(padOccupants.values()), requiredPlayers);
            if (!officialRewardRoster.isEmpty() && !officialRewardRoster.equals(roster.players())) {
                // A committed roster is immutable across restart/recovery.  A
                // replacement player may not silently change the number or
                // identity of shard recipients.
                cancelRitual("committed roster mismatch after recovery");
                getLogger().warning("RITUAL_ROSTER_MISMATCH event=" + eventId
                        + " committed=" + officialRewardRoster + " occupied=" + roster.players());
                return;
            }
            if (officialRewardRoster.isEmpty()) {
                officialRewardRoster.addAll(roster.players());
            }
            participantUuids.addAll(roster.players());
        } catch (IllegalArgumentException invalid) {
            cancelRitual("roster uniqueness failed");
            return;
        }
        generation = Math.max(1L, generation + 1L);
        cancelSessionTasks();
        taskRegistry = new EventTaskRegistry(generation);
        halfHealthTriggered = false;
        controlSpellUnlocked = false;
        finalDrainTriggered = false;
        finalDrainApplied = false;
        finalDrainTargets.clear();
        finalDrainAppliedPlayers.clear();
        clearCombatAiState();
        lootIssuedEntityUuids.clear();
        activeWave = 1;
        if (!transition(EventPhase.WAVE_1, "official roster frozen", eventId + ":roster:" + generation, false)) {
            officialRewardRoster.clear();
            activeWave = 0;
            forcePhase(EventPhase.READY_FOR_PLAYERS, "roster transition rejected");
            return;
        }
        if (!saveStateSync()) {
            // No wave may spawn until the phase, generation, and frozen roster
            // have crossed the durable boundary together.
            officialRewardRoster.clear();
            rewardStatuses.clear();
            activeWave = 0;
            padOccupants.clear();
            clearCombatAiState();
            forcePhase(EventPhase.READY_FOR_PLAYERS, "official roster commit could not be persisted");
            getLogger().severe("RITUAL_COMMIT_FAILED event=" + eventId + " generation=" + generation);
            return;
        }
        getLogger().info("RITUAL_COMPLETED event=" + eventId + " roster=" + officialRewardRoster);
        spawnWave(1, false);
    }

    private void cancelRitual(String reason) {
        if (phase == EventPhase.COUNTDOWN) {
            phaseDeadlineMillis = 0L;
            padOccupants.clear();
            transition(EventPhase.READY_FOR_PLAYERS, reason, eventId + ":ritual-cancel:" + UUID.randomUUID());
            getLogger().info("RITUAL_CANCELLED event=" + eventId + " reason=" + reason);
        }
    }

    private void updateCombatHelpers() {
        if (!isCombatPhase()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isActiveArenaParticipant(player)) {
                combatHelpers.add(player.getUniqueId());
                registerParticipant(player);
                playerCategory(player, officialRewardRoster.contains(player.getUniqueId())
                        ? "official_participant" : "combat_helper");
            }
        }
    }

    /**
     * One bounded controller owns wave targeting and the containment watchdog.
     * Natural mobs are never included because every lookup starts in
     * ownedEntities and checks the event role tag.
     */
    private void tickWaveMobAi() {
        // Test waves deliberately do not enter the official state machine, but
        // their event-owned mobs still need the exact same containment policy.
        enforceWaveMobContainment();
        if (!isCombatPhase()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Player> candidates = activeLivingPlayers();
        if (now >= nextWaveTargetMillis && !candidates.isEmpty()) {
            List<UUID> candidateIds = candidates.stream()
                    .map(Player::getUniqueId)
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();
            for (Entity entity : new ArrayList<>(ownedEntities.values())) {
                String kind = readString(entity, keyKind);
                if (!(entity instanceof Mob mob) || !isWaveCombatKind(kind) || !isLiveOwnedEntity(entity.getUniqueId())) {
                    continue;
                }
                UUID current = mob.getTarget() == null ? null : mob.getTarget().getUniqueId();
                EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                        candidateIds, current, List.of(), waveTargetCursor++);
                Player target = choice.target() == null ? null : Bukkit.getPlayer(choice.target());
                if (target != null && isCombatTarget(target)) {
                    mob.setTarget(target);
                    getLogger().info("WAVE_AI_TARGET entity=" + entity.getUniqueId()
                            + " role=" + kind + " target=" + target.getUniqueId());
                }
            }
            nextWaveTargetMillis = now
                    + randomSeconds(config.bossTargetMinSeconds(), config.bossTargetMaxSeconds()) * 1000L;
        }
    }

    private void enforceWaveMobContainment() {
        Location anchor = coreLocation();
        if (anchor == null) {
            return;
        }
        double radius = boundedCombatRadius(config.containmentRadius());
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            String kind = readString(entity, keyKind);
            if (isWaveCombatKind(kind) && isLiveOwnedEntity(entity.getUniqueId())) {
                enforceCombatLeash(entity, anchor, radius, "WAVE_AI_LEASH");
            }
        }
    }

    private boolean isWaveCombatKind(String kind) {
        return EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind)
                || EVENT_KIND_FINAL_WAVE.equals(kind);
    }

    /** Each elite owns exactly one spell; there is no shared repeating task per mob. */
    private void tickMiniBosses() {
        if (!isMiniBossCombatPhase()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, EndRiftAiPolicy.MiniBossSpell> entry : new HashMap<>(miniBossSpells).entrySet()) {
            Entity entity = ownedEntities.get(entry.getKey());
            if (!(entity instanceof Enderman miniBoss) || !isLiveOwnedEntity(entity.getUniqueId())) {
                miniBossSpells.remove(entry.getKey());
                nextMiniBossSpellMillis.remove(entry.getKey());
                continue;
            }
            if (nextMiniBossSpellMillis.getOrDefault(entity.getUniqueId(), 0L) > now) {
                continue;
            }
            List<Player> candidates = activeLivingPlayers();
            if (candidates.isEmpty()) {
                nextMiniBossSpellMillis.put(entity.getUniqueId(), now + 1000L);
                continue;
            }
            Player current = miniBoss.getTarget() instanceof Player player && isCombatTarget(player)
                    ? player : null;
            List<UUID> candidateIds = candidates.stream().map(Player::getUniqueId)
                    .sorted(Comparator.comparing(UUID::toString)).toList();
            EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                    candidateIds, current == null ? null : current.getUniqueId(), List.of(), waveTargetCursor++);
            Player target = choice.target() == null ? null : Bukkit.getPlayer(choice.target());
            if (target == null || !isCombatTarget(target)) {
                nextMiniBossSpellMillis.put(entity.getUniqueId(), now + 1000L);
                continue;
            }
            miniBoss.setTarget(target);
            nextMiniBossSpellMillis.put(entity.getUniqueId(), now
                    + randomSeconds(config.miniBossTuning().spellMinSeconds(), config.miniBossTuning().spellMaxSeconds()) * 1000L);
            telegraphMiniBossSpell(miniBoss, target, entry.getValue());
        }
    }

    private void telegraphMiniBossSpell(Enderman miniBoss, Player target, EndRiftAiPolicy.MiniBossSpell spell) {
        if (taskRegistry == null || miniBoss == null || target == null || !isCombatTarget(target)) {
            return;
        }
        long callbackGeneration = generation;
        UUID miniBossId = miniBoss.getUniqueId();
        Location mark = target.getLocation().clone();
        getLogger().info("MINIBOSS_SPELL_TELEGRAPH entity=" + miniBossId + " spell=" + spell.id()
                + " target=" + target.getUniqueId() + " generation=" + generation);
        target.sendActionBar(Component.text("Мини-босс готовит: " + spell.displayName(), NamedTextColor.LIGHT_PURPLE));
        final int[] ticks = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || !isMiniBossCombatPhase() || !miniBoss.isValid() || miniBoss.isDead()
                    || miniBossSpells.get(miniBossId) != spell) {
                holder[0].cancel();
                return;
            }
            ticks[0] += 5;
            Location effect = spell == EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE
                    ? miniBoss.getLocation().add(0.0D, 1.0D, 0.0D) : mark;
            Particle particle = spell == EndRiftAiPolicy.MiniBossSpell.VOID_SNARE
                    ? Particle.REVERSE_PORTAL : Particle.END_ROD;
            miniBoss.getWorld().spawnParticle(particle, effect, 8, 0.65D, 0.15D, 0.65D, 0.01D);
            if (ticks[0] >= config.miniBossTuning().spellTelegraphTicks()) {
                holder[0].cancel();
                if (taskRegistry.owns(callbackGeneration) && isMiniBossCombatPhase()
                        && miniBoss.isValid() && !miniBoss.isDead()) {
                    executeMiniBossSpell(miniBoss, target, mark, spell, callbackGeneration);
                }
            }
        }, 0L, 5L);
        taskRegistry.register(holder[0]);
    }

    private void executeMiniBossSpell(Enderman miniBoss, Player target, Location mark,
                                      EndRiftAiPolicy.MiniBossSpell spell, long callbackGeneration) {
        if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                || !isMiniBossCombatPhase() || !isCombatTarget(target)) {
            return;
        }
        launchSpellFlight(miniBoss, mark,
                "MINIBOSS_SPELL_FLIGHT", spell.id(), target.getUniqueId(), false,
                callbackGeneration, () -> {
                    if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                            || !isMiniBossCombatPhase() || !isCombatTarget(target)
                            || !miniBoss.isValid() || miniBoss.isDead()
                            || miniBossSpells.get(miniBoss.getUniqueId()) != spell) {
                        return;
                    }
                    getLogger().info("MINIBOSS_SPELL_CAST entity=" + miniBoss.getUniqueId()
                            + " spell=" + spell.id() + " target=" + target.getUniqueId()
                            + " generation=" + callbackGeneration);
                    switch (spell) {
                        case RIFT_STEP -> miniBossRiftStep(miniBoss, target);
                        case VOID_SNARE -> miniBossVoidSnare(miniBoss, target, mark);
                        case ECHO_PULSE -> miniBossEchoPulse(miniBoss);
                    }
                });
    }

    /**
     * Sends every event spell through a short, visible particle flight before
     * applying its gameplay effect.  The callback is owned by the current
     * event generation so a reset, cancellation, or phase change cannot leave
     * a delayed damage task behind.
     */
    private void launchSpellFlight(LivingEntity caster, Location destination,
                                   String logMarker, String spellId, UUID targetId, boolean forced,
                                   long callbackGeneration, Runnable impact) {
        if (taskRegistry == null || caster == null || destination == null
                || impact == null || !isSpellFlightAllowed(caster, forced)
                || caster.getWorld() == null || destination.getWorld() == null
                || !caster.getWorld().equals(destination.getWorld())) {
            return;
        }
        Location start = caster.getEyeLocation().clone();
        Location end = destination.clone().add(0.0D, 0.75D, 0.0D);
        Vector delta = end.toVector().subtract(start.toVector());
        getLogger().info(logMarker + " caster=" + caster.getUniqueId()
                + " spell=" + spellId + " target=" + targetId
                + " effect=" + SPELL_FLIGHT_EFFECT + " visual=particle-only pattern=" + spellId
                + " ticks=" + SPELL_FLIGHT_TICKS
                + " generation=" + callbackGeneration);
        final int[] ticks = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean allowed = taskRegistry != null
                    && taskRegistry.owns(callbackGeneration)
                    && isSpellFlightAllowed(caster, forced);
            if (!allowed) {
                holder[0].cancel();
                return;
            }
            double progress = Math.min(1.0D, ++ticks[0] / (double) SPELL_FLIGHT_TICKS);
            Location point = start.clone().add(delta.clone().multiply(progress));
            spawnSpellFlightPattern(caster.getWorld(), point, delta, spellId, ticks[0]);
            if (progress >= 1.0D) {
                holder[0].cancel();
                impact.run();
            }
        }, 0L, 1L);
        taskRegistry.register(holder[0]);
    }

    /**
     * Renders one named, particle-only flight pattern for each event spell.
     * There is intentionally no ItemDisplay or custom model in this path:
     * the geometry is sent by the server as vanilla particles and therefore
     * remains visible without changing any vanilla texture.
     */
    private void spawnSpellFlightPattern(World world, Location point, Vector direction,
                                          String spellId, int tick) {
        if (world == null || point == null || direction == null || spellId == null) {
            return;
        }
        Vector forward = direction.clone();
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        forward.normalize();
        Vector reference = Math.abs(forward.getY()) > 0.85D
                ? new Vector(1.0D, 0.0D, 0.0D)
                : new Vector(0.0D, 1.0D, 0.0D);
        Vector side = forward.clone().crossProduct(reference);
        if (side.lengthSquared() < 0.0001D) {
            side = new Vector(1.0D, 0.0D, 0.0D);
        }
        side.normalize();
        Vector vertical = side.clone().crossProduct(forward).normalize();
        double phase = tick * 0.78D;

        switch (spellId) {
            case "void_blast" -> {
                world.spawnParticle(Particle.DRAGON_BREATH, point, 12,
                        0.14D, 0.14D, 0.14D, 0.015D);
                world.spawnParticle(Particle.DUST, point, 7,
                        0.04D, 0.04D, 0.04D, 0.0D,
                        new Particle.DustOptions(Color.fromRGB(244, 39, 125), 1.35F));
                spawnPatternRing(world, point, side, vertical, 0.18D + (tick % 3) * 0.05D,
                        10, phase, Particle.FLAME);
            }
            case "rift_projectile" -> spawnRiftProjectileTrail(point, direction, tick);
            case "void_mark" -> {
                Particle.DustOptions markDust = new Particle.DustOptions(Color.fromRGB(190, 76, 255), 1.25F);
                double halfDiagonal = 0.31D + (tick % 2) * 0.04D;
                Location[] corners = new Location[4];
                for (int i = 0; i < corners.length; i++) {
                    double angle = phase * 0.35D + Math.PI / 4.0D + i * Math.PI / 2.0D;
                    corners[i] = point.clone()
                            .add(side.clone().multiply(Math.cos(angle) * halfDiagonal))
                            .add(vertical.clone().multiply(Math.sin(angle) * halfDiagonal));
                    world.spawnParticle(Particle.END_ROD, corners[i], 2,
                            0.015D, 0.015D, 0.015D, 0.0D);
                    world.spawnParticle(Particle.DUST, corners[i], 2,
                            0.015D, 0.015D, 0.015D, 0.0D, markDust);
                }
                for (int i = 0; i < corners.length; i++) {
                    spawnPatternSegment(world, corners[i], corners[(i + 1) % corners.length],
                            Particle.END_ROD, markDust);
                }
            }
            case "summon", "summon_servants" -> {
                Vector up = new Vector(0.0D, 1.0D, 0.0D);
                for (int i = 0; i < 6; i++) {
                    double angle = phase + i * Math.PI / 3.0D;
                    Location spiral = point.clone()
                            .add(side.clone().multiply(Math.cos(angle) * 0.24D))
                            .add(vertical.clone().multiply(Math.sin(angle) * 0.24D))
                            .add(up.clone().multiply((i - 2.5D) * 0.07D));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, spiral, 2,
                            0.015D, 0.015D, 0.015D, 0.005D);
                    world.spawnParticle(Particle.WITCH, spiral, 1,
                            0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            case "will_distortion" -> {
                for (int i = 0; i < 6; i++) {
                    double along = (i - 2.5D) * 0.15D;
                    double swing = i % 2 == 0 ? -0.26D : 0.26D;
                    Location zig = point.clone()
                            .add(forward.clone().multiply(along))
                            .add(side.clone().multiply(swing))
                            .add(vertical.clone().multiply(Math.sin(phase + i) * 0.08D));
                    world.spawnParticle(Particle.ELECTRIC_SPARK, zig, 2,
                            0.02D, 0.02D, 0.02D, 0.01D);
                    world.spawnParticle(Particle.WITCH, zig, 1,
                            0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            case "rift_step" -> {
                for (int i = 0; i < 6; i++) {
                    double angle = phase + i * Math.PI / 3.0D;
                    Vector along = forward.clone().multiply((i - 2.5D) * 0.12D);
                    Location first = point.clone().add(along.clone())
                            .add(side.clone().multiply(Math.cos(angle) * 0.23D))
                            .add(vertical.clone().multiply(Math.sin(angle) * 0.23D));
                    Location second = point.clone().add(along)
                            .subtract(side.clone().multiply(Math.cos(angle) * 0.23D))
                            .add(vertical.clone().multiply(Math.sin(angle) * 0.23D));
                    world.spawnParticle(Particle.PORTAL, first, 2,
                            0.015D, 0.015D, 0.015D, 0.01D);
                    world.spawnParticle(Particle.END_ROD, second, 1,
                            0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            case "void_snare" -> {
                double radius = Math.max(0.16D, 0.44D - tick * 0.035D);
                spawnPatternRing(world, point, side, vertical, radius, 12, phase,
                        Particle.REVERSE_PORTAL);
                for (int i = 0; i < 6; i++) {
                    double angle = phase + i * Math.PI / 3.0D;
                    Location chain = point.clone()
                            .add(side.clone().multiply(Math.cos(angle) * radius))
                            .add(vertical.clone().multiply(Math.sin(angle) * radius));
                    world.spawnParticle(Particle.SMOKE, chain, 1,
                            0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            case "echo_pulse" -> {
                double radius = 0.10D + tick * 0.075D;
                spawnPatternRing(world, point, side, vertical, radius, 14, phase,
                        Particle.SCULK_SOUL);
                world.spawnParticle(Particle.SONIC_BOOM, point, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
            default -> world.spawnParticle(Particle.END_ROD, point, 4,
                    0.06D, 0.06D, 0.06D, 0.0D);
        }
    }

    private void spawnRiftProjectileTrail(Location point, Vector direction, int tick) {
        if (point == null || point.getWorld() == null || direction == null) {
            return;
        }
        World world = point.getWorld();
        Vector forward = direction.clone();
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        forward.normalize();
        Vector reference = Math.abs(forward.getY()) > 0.85D
                ? new Vector(1.0D, 0.0D, 0.0D)
                : new Vector(0.0D, 1.0D, 0.0D);
        Vector side = forward.clone().crossProduct(reference).normalize();
        Vector vertical = side.clone().crossProduct(forward).normalize();
        Particle.DustOptions riftDust = new Particle.DustOptions(Color.fromRGB(69, 218, 255), 1.20F);
        for (int i = 0; i < 8; i++) {
            double angle = tick * 0.95D + i * Math.PI / 4.0D;
            double radius = 0.16D + i * 0.025D;
            Location spiral = point.clone()
                    .add(side.clone().multiply(Math.cos(angle) * radius))
                    .add(vertical.clone().multiply(Math.sin(angle) * radius))
                    .add(forward.clone().multiply((i - 3.5D) * 0.035D));
            world.spawnParticle(Particle.REVERSE_PORTAL, spiral, 2,
                    0.015D, 0.015D, 0.015D, 0.01D);
            world.spawnParticle(Particle.DUST, spiral, 1,
                    0.01D, 0.01D, 0.01D, 0.0D, riftDust);
        }
        world.spawnParticle(Particle.DRAGON_BREATH, point, 5,
                0.04D, 0.04D, 0.04D, 0.01D);
    }

    private void spawnPatternRing(World world, Location center, Vector axisA, Vector axisB,
                                  double radius, int points, double phase, Particle particle) {
        for (int i = 0; i < points; i++) {
            double angle = phase + (Math.PI * 2.0D * i / points);
            Location ringPoint = center.clone()
                    .add(axisA.clone().multiply(Math.cos(angle) * radius))
                    .add(axisB.clone().multiply(Math.sin(angle) * radius));
            world.spawnParticle(particle, ringPoint, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnPatternSegment(World world, Location start, Location end, Particle particle,
                                     Particle.DustOptions dust) {
        Vector delta = end.toVector().subtract(start.toVector());
        int steps = 4;
        for (int i = 1; i < steps; i++) {
            Location linePoint = start.clone().add(delta.clone().multiply(i / (double) steps));
            world.spawnParticle(particle, linePoint, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            if (dust != null) {
                world.spawnParticle(Particle.DUST, linePoint, 1,
                        0.0D, 0.0D, 0.0D, 0.0D, dust);
            }
        }
    }

    private boolean isSpellFlightAllowed(LivingEntity caster, boolean forced) {
        if (caster == null || !caster.isValid() || caster.isDead()) {
            return false;
        }
        String kind = readString(caster, keyKind);
        if (EVENT_KIND_BOSS.equals(kind)) {
            return phase == EventPhase.BOSS_ACTIVE || forced
                    || testCombatAiMode && isTestBoss(caster);
        }
        return (EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_FINAL_WAVE.equals(kind))
                && isMiniBossCombatPhase();
    }

    private void miniBossRiftStep(Enderman miniBoss, Player target) {
        Location anchor = coreLocation();
        Location destination = findSafeCombatLocation(anchor, target.getLocation(), config.containmentRadius());
        if (destination != null) {
            miniBoss.teleport(destination);
        }
        target.damage(config.miniBossTuning().riftStepDamage(), miniBoss);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, false, true, true));
        miniBoss.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0.0D, 1.0D, 0.0D),
                24, 0.5D, 0.8D, 0.5D, 0.02D);
    }

    private void miniBossVoidSnare(Enderman miniBoss, Player target, Location mark) {
        target.damage(config.miniBossTuning().voidSnareDamage(), miniBoss);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
        miniBoss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mark, 28, 1.2D, 0.1D, 1.2D, 0.02D);
    }

    private void miniBossEchoPulse(Enderman miniBoss) {
        Location center = miniBoss.getLocation();
        for (Player player : activeLivingPlayers()) {
            if (player.getLocation().distanceSquared(center) > 25.0D) {
                continue;
            }
            player.damage(config.miniBossTuning().echoPulseDamage(), miniBoss);
            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01D) {
                push = new Vector(0.0D, 0.25D, 0.0D);
            }
            player.setVelocity(push.normalize().multiply(0.35D).setY(0.22D));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true, true));
        }
        miniBoss.getWorld().spawnParticle(Particle.END_ROD, center.add(0.0D, 1.0D, 0.0D),
                32, 1.0D, 0.6D, 1.0D, 0.03D);
    }

    private void enforceCombatLeash(Entity entity, Location anchor, double radius, String logMarker) {
        if (entity == null || anchor == null || entity.getWorld() == null
                || !entity.getWorld().equals(anchor.getWorld())) {
            return;
        }
        Location current = entity.getLocation();
        boolean outsideHorizontalRadius = horizontalDistanceSquared(current, anchor) > radius * radius;
        boolean offCoreLevel = current.getBlockY() != anchor.getBlockY();
        if (!outsideHorizontalRadius && !offCoreLevel) {
            return;
        }
        Location safe = findSafeCombatLocation(anchor, anchor, radius - 0.75D);
        if (safe != null && entity.teleport(safe)) {
            getLogger().info(logMarker + " entity=" + entity.getUniqueId()
                    + " location=" + locationText(safe));
        }
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        double deltaX = first.getX() - second.getX();
        double deltaZ = first.getZ() - second.getZ();
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private double boundedCombatRadius(double configuredRadius) {
        return Math.max(1.0D, Math.min(MAX_COMBAT_RADIUS_BLOCKS, configuredRadius));
    }

    private Location findSafeCombatLocation(Location anchor, Location preferred, double radius) {
        if (anchor == null || anchor.getWorld() == null) {
            return null;
        }
        double safeRadius = Math.max(1.0D, radius);
        Location center = anchor.clone();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = attempt * 2.399963229728653D;
            double distance = attempt == 0 ? 0.0D : 1.5D + (attempt % 6) * 1.7D;
            Location candidate = attempt < 4 && preferred != null
                    ? preferred.clone()
                    : center.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            // Combat teleports stay on the horizontal level of the Core block.
            candidate.setY(center.getBlockY());
            if (horizontalDistanceSquared(candidate, center) > safeRadius * safeRadius) {
                continue;
            }
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(BlockFace.UP);
            Block floor = feet.getRelative(BlockFace.DOWN);
            if (feet.isPassable() && head.isPassable() && floor.getType().isSolid() && !floor.isLiquid()) {
                return new Location(candidate.getWorld(), feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
            }
        }
        return null;
    }

    private boolean isCombatPhase() {
        if (testCombatAiMode) {
            return true;
        }
        return switch (phase) {
            case WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3,
                    BOSS_ACTIVE, FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH -> true;
            default -> false;
        };
    }

    private boolean isMiniBossCombatPhase() {
        return testCombatAiMode || phase == EventPhase.WAVE_1 || phase == EventPhase.WAVE_2
                || phase == EventPhase.WAVE_3 || phase == EventPhase.FINAL_WAVE;
    }

    private boolean isActiveArenaParticipant(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || player.getWorld() == null
                || player.getHealth() <= 0.0D
                || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE
                || !player.getWorld().getName().equals(worldName)) {
            return false;
        }
        return isArenaLocation(player.getLocation());
    }

    private boolean isCreativeTestTarget(Player player) {
        return creativeTestTask != null && creativeTestPlayerUuid != null
                && creativeTestPlayerUuid.equals(player == null ? null : player.getUniqueId())
                && player != null && player.isOnline() && !player.isDead()
                && player.getHealth() > 0.0D && player.getGameMode() == GameMode.CREATIVE
                && player.getWorld() != null && player.getWorld().getName().equals(worldName)
                && isArenaLocation(player.getLocation());
    }

    private boolean isCombatTarget(Player player) {
        return isActiveArenaParticipant(player) || isCreativeTestTarget(player);
    }

    private void tickFinalRitual() {
        LivingEntity boss = liveBoss();
        if (boss == null || finalDrainApplied) {
            return;
        }
        // Do not consume the once-only drain while no eligible player is
        // present.  The next bounded tick retries when somebody returns.
        applyFinalDrain(boss);
    }

    private void tickIntermission() {
        if (phaseDeadlineMillis <= 0L || System.currentTimeMillis() < phaseDeadlineMillis) {
            return;
        }
        if (phase == EventPhase.INTERMISSION_1) {
            activeWave = 2;
            if (transition(EventPhase.WAVE_2, "intermission complete", eventId + ":wave:2")) {
                spawnWave(2, false);
            }
        } else if (phase == EventPhase.INTERMISSION_2) {
            activeWave = 3;
            if (transition(EventPhase.WAVE_3, "intermission complete", eventId + ":wave:3")) {
                spawnWave(3, false);
            }
        }
        phaseDeadlineMillis = 0L;
    }

    private void tickWaveCompletion() {
        if (phase == EventPhase.FINAL_WAVE) {
            if (finalWaveEntities.stream().noneMatch(this::isLiveOwnedEntity)) {
                finalWaveEntities.clear();
                LivingEntity boss = liveBoss();
                if (boss != null) {
                    boss.setInvulnerable(false);
                    boss.setHealth(Math.min(config.bossFinalHealth(), boss.getMaxHealth()));
                }
                if (bossBar != null) {
                    bossBar.setTitle("Хранитель Разлома");
                }
                transition(EventPhase.BOSS_FINISH, "final wave defeated", eventId + ":final-wave-complete");
                getLogger().info("WAVE_COMPLETED event=" + eventId + " wave=FINAL");
            }
            return;
        }
        if (activeWave < 1 || activeWave > 3) {
            return;
        }
        boolean live = ownedEntities.values().stream().anyMatch(entity -> {
            String kind = readString(entity, keyKind);
            int wave = readInt(entity, keyWave, 0);
            return (EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind))
                    && wave == activeWave && isLiveOwnedEntity(entity.getUniqueId());
        });
        if (live) {
            return;
        }
        getLogger().info("WAVE_COMPLETED event=" + eventId + " wave=" + activeWave);
        if (activeWave == 1) {
            phaseDeadlineMillis = System.currentTimeMillis() + config.intermissionSeconds() * 1000L;
            transition(EventPhase.INTERMISSION_1, "wave 1 defeated", eventId + ":intermission:1");
        } else if (activeWave == 2) {
            phaseDeadlineMillis = System.currentTimeMillis() + config.intermissionSeconds() * 1000L;
            transition(EventPhase.INTERMISSION_2, "wave 2 defeated", eventId + ":intermission:2");
        } else if (activeWave == 3) {
            activeWave = 0;
            if (transition(EventPhase.BOSS_ACTIVE, "wave 3 defeated", eventId + ":boss-active")) {
                spawnOfficialBoss(null);
            }
        }
    }

    private void spawnWave(int wave, boolean test) {
        World world = Bukkit.getWorld(worldName);
        Location core = coreLocation();
        if (world == null || core == null) {
            getLogger().warning("Cannot spawn End Event wave without configured world/core.");
            return;
        }
        EventConfig.WaveDefinition definition = switch (wave) {
            case 1 -> config.wave1();
            case 2 -> config.wave2();
            case 3 -> config.wave3();
            case 4 -> config.finalWave();
            default -> null;
        };
        if (definition == null) {
            return;
        }
        int scalePlayers = Math.max(config.minPlayers(), officialRewardRoster.size());
        double scale = Math.max(0.8D, Math.min(2.0D, scalePlayers / 5.0D));
        int endermen = scaled(definition.endermen(), scale);
        int spiders = scaled(definition.spiders(), scale);
        int shulkers = scaled(definition.shulkers(), scale);
        int elites = scaled(definition.eliteEndermen(), scale);
        int total = endermen + spiders + shulkers + elites;
        if (total > config.waveHardCap()) {
            int overflow = total - config.waveHardCap();
            int[] counts = {endermen, spiders, shulkers, elites};
            for (int index = 0; index < counts.length && overflow > 0; index++) {
                int remove = Math.min(overflow, Math.max(0, counts[index] - 1));
                counts[index] -= remove;
                overflow -= remove;
            }
            endermen = counts[0];
            spiders = counts[1];
            shulkers = counts[2];
            elites = counts[3];
        }
        if (!test) {
            activeWave = wave;
            playEventMusic(wave == 4 ? config.bossFinalMusic() : config.wavesMusic());
        }
        for (int index = 0; index < endermen; index++) {
            spawnEnderman(world, core, wave, false, wave == 4, test, index, index);
        }
        for (int index = 0; index < elites; index++) {
            spawnEnderman(world, core, wave, true, wave == 4, test, index + endermen, index);
        }
        for (int index = 0; index < spiders; index++) {
            spawnOwnedMob(world, core, EntityType.SPIDER, wave,
                    wave == 4 ? EVENT_KIND_FINAL_WAVE : EVENT_KIND_WAVE_MOB, test, index);
        }
        for (int index = 0; index < shulkers; index++) {
            spawnOwnedMob(world, core, EntityType.SHULKER, wave,
                    wave == 4 ? EVENT_KIND_FINAL_WAVE : EVENT_KIND_WAVE_MOB, test, index);
        }
        if (wave == 4 && !test) {
                finalWaveEntities.addAll(ownedEntities.keySet().stream()
                    .filter(id -> {
                        Entity entity = ownedEntities.get(id);
                        return entity != null && isOfficialEntity(entity) && readInt(entity, keyWave, 0) == 4;
                    }).toList());
            getLogger().info("FINAL_WAVE_STARTED event=" + eventId + " count=" + finalWaveEntities.size());
        } else if (!test) {
            getLogger().info("WAVE_STARTED event=" + eventId + " wave=" + wave
                    + " count=" + (endermen + elites + spiders + shulkers));
        }
    }

    private int scaled(int base, double scale) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(base * scale));
    }

    private void spawnEnderman(World world, Location core, int wave, boolean elite,
                               boolean finalWave, boolean test, int index, int abilityIndex) {
        Location location = safeSpawnLocation(core, index, elite ? 4.0D : 2.0D);
        Enderman enderman = (Enderman) world.spawnEntity(location, EntityType.ENDERMAN);
        String kind = finalWave ? EVENT_KIND_FINAL_WAVE : elite ? EVENT_KIND_ELITE : EVENT_KIND_WAVE_MOB;
        tag(enderman, kind, wave, !test);
        setLootProfile(enderman, test ? "test"
                : elite ? "elite-enderman" : finalWave ? "final-wave" : "common-enderman");
        enderman.setPersistent(true);
        enderman.setRemoveWhenFarAway(false);
        enderman.setCanPickupItems(false);
        if (elite) {
            EndRiftAiPolicy.MiniBossSpell miniBossSpell = EndRiftAiPolicy.miniBossSpell(wave, abilityIndex);
            AttributeInstance max = enderman.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(80.0D);
                enderman.setHealth(80.0D);
            }
            AttributeInstance attack = enderman.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attack != null) {
                attack.setBaseValue(attack.getBaseValue() + 2.0D);
            }
            tagMiniBossSpell(enderman, miniBossSpell);
            miniBossSpells.put(enderman.getUniqueId(), miniBossSpell);
            nextMiniBossSpellMillis.put(enderman.getUniqueId(), 0L);
            enderman.setCustomName(ChatColor.LIGHT_PURPLE + (finalWave ? "Элитный страж" : "Элитный эндермен")
                    + ChatColor.DARK_PURPLE + " · " + miniBossSpell.displayName());
            enderman.setCustomNameVisible(true);
            enderman.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    enderman.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.35D, 0.55D, 0.35D, 0.02D);
        }
        ownedEntities.put(enderman.getUniqueId(), enderman);
        bindEventEntityClientForOnlinePlayers(enderman);
        if (finalWave && !test) {
            finalWaveEntities.add(enderman.getUniqueId());
        }
    }

    private Entity spawnOwnedMob(World world, Location core, EntityType type, int wave,
                                 String kind, boolean test, int index) {
        Entity entity = world.spawnEntity(safeSpawnLocation(core, index, 3.0D), type);
        tag(entity, kind, wave, !test);
        setLootProfile(entity, test ? "test"
                : type == EntityType.SPIDER ? "spider"
                : type == EntityType.SHULKER ? "shulker" : "final-wave");
        if (entity instanceof LivingEntity living) {
            living.setPersistent(true);
            configureEventMobStats(living, type);
            if (living instanceof Shulker shulker) {
                shulker.setAI(true);
            }
        }
        ownedEntities.put(entity.getUniqueId(), entity);
        bindEventEntityClientForOnlinePlayers(entity);
        if (wave == 4 && !test) {
            finalWaveEntities.add(entity.getUniqueId());
        }
        return entity;
    }

    private void configureEventMobStats(LivingEntity living, EntityType type) {
        if (living == null || type != EntityType.SPIDER) {
            return;
        }
        AttributeInstance health = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        AttributeInstance attack = living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (health != null) {
            double maxHealth = health.getBaseValue() + config.spiderHealthBonus();
            health.setBaseValue(maxHealth);
            living.setHealth(maxHealth);
        }
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() + config.spiderAttackDamageBonus());
        }
        getLogger().info("SPIDER_STATS entity=" + living.getUniqueId()
                + " health=" + (health == null ? "unknown" : health.getBaseValue())
                + " attack=" + (attack == null ? "unknown" : attack.getBaseValue()));
    }

    private Location spawnLocation(Location core, int index, double offset) {
        double angle = (index * 2.399963229728653D) % (Math.PI * 2.0D);
        double radius = 5.0D + ((index % 4) * offset);
        return core.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
    }

    private Location safeSpawnLocation(Location core, int index, double offset) {
        if (core == null || core.getWorld() == null) {
            return core;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            Location candidate = spawnLocation(core, index + attempt, offset);
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(BlockFace.UP);
            Block floor = feet.getRelative(BlockFace.DOWN);
            if (feet.isPassable() && head.isPassable() && floor.getType().isSolid()
                    && core.distanceSquared(candidate) <= config.arenaRadius() * config.arenaRadius()) {
                return candidate;
            }
        }
        return core.clone();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOwnedEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !ownedEntities.containsKey(entity.getUniqueId())) {
            return;
        }
        Location anchor = coreLocation();
        Location target = event.getTo();
        if (anchor == null || target == null || !anchor.getWorld().equals(target.getWorld())) {
            event.setCancelled(true);
            return;
        }
        if (isCoreBlockPosition(target)) {
            // A teleport request may name the solid Core block itself.  Put
            // the mob on its top face, not inside the block and not one block
            // above the arena, so elevated Cores do not become mob magnets.
            event.setTo(coreBlockTopLocation());
            return;
        }
        String kind = readString(entity, keyKind);
        double radius = EVENT_KIND_BOSS.equals(kind)
                ? boundedCombatRadius(config.bossRadius())
                : boundedCombatRadius(config.containmentRadius());
        boolean outsideHorizontalRadius = horizontalDistanceSquared(target, anchor) > radius * radius;
        boolean offCoreLevel = target.getBlockY() != anchor.getBlockY();
        if (outsideHorizontalRadius || offCoreLevel) {
            Location safe = findSafeCombatLocation(anchor, target, radius - 0.75D);
            if (safe == null) {
                event.setCancelled(true);
            } else {
                event.setTo(safe);
            }
        }
    }

    private void clearWaveEntities() {
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            String kind = readString(entity, keyKind);
            if (EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind)
                    || EVENT_KIND_FINAL_WAVE.equals(kind)) {
                entity.remove();
                ownedEntities.remove(entity.getUniqueId());
                finalWaveEntities.remove(entity.getUniqueId());
            }
        }
        spellServants.clear();
        miniBossSpells.clear();
        nextMiniBossSpellMillis.clear();
        lootIssuedEntityUuids.clear();
    }

    private boolean isLiveOwnedEntity(UUID entityUuid) {
        Entity entity = ownedEntities.get(entityUuid);
        return entity != null && !entity.isDead() && entity.isValid();
    }

    private int countLiveOwnedMobs() {
        int count = 0;
        for (Entity entity : ownedEntities.values()) {
            if (entity instanceof LivingEntity && isLiveOwnedEntity(entity.getUniqueId())
                    && !EVENT_KIND_DISPLAY.equals(readString(entity, keyKind))) {
                count++;
            }
        }
        return count;
    }

    private void spawnTestBoss(CommandSender sender) {
        if (!isConfigured()) {
            message(sender, "&cСначала настрой Core в загруженном event world.");
            return;
        }
        Location core = coreLocation();
        World world = core == null ? null : core.getWorld();
        if (world == null) {
            message(sender, "&cСначала настрой Core.");
            return;
        }
        clearBossOnly();
        // /cmend boss spawn is the disposable local boss harness.  Keep the
        // official phase untouched, but run the same target/telegraph/flight
        // controller and bind the real client visual for every online target.
        testCombatAiMode = true;
        halfHealthTriggered = false;
        finalDrainTriggered = false;
        finalDrainApplied = false;
        controlSpellUnlocked = false;
        Enderman boss = (Enderman) world.spawnEntity(core.clone().add(0.0D, 1.0D, 0.0D), EntityType.ENDERMAN);
        configureBoss(boss, true);
        ensureBossBar();
        bindBossClientForOnlinePlayers();
        message(sender, "&aTest boss создан: он не открывает End и не выдаёт official rewards.");
    }

    private void spawnTestAi(CommandSender sender) {
        if (!hasSpawnAnchor()) {
            message(sender, "&cСначала настрой Core в загруженном event world.");
            return;
        }
        if (isCombatPhase()) {
            message(sender, "&cAI test refused during an official combat phase; сначала завершите или отмените бой.");
            return;
        }
        clearBossOnly();
        clearWaveEntities();
        testCombatAiMode = true;
        spawnWave(3, true);
        Location core = coreLocation();
        if (core != null && core.getWorld() != null) {
            Enderman boss = (Enderman) core.getWorld().spawnEntity(
                    core.clone().add(0.0D, 1.0D, 0.0D), EntityType.ENDERMAN);
            configureBoss(boss, true);
        }
        List<Player> eligiblePlayers = activeLivingPlayers();
        getLogger().info("TEST_AI_STARTED event=" + eventId
                + " phase=" + phase + " official_phase_unchanged=true"
                + " eligible_players=" + eligiblePlayers.size());
        message(sender, "&aTest AI запущен: реальные wave/boss controllers, official phase/roster/victory не изменены.");
    }

    private void spawnOfficialBoss(CommandSender sender) {
        if (!isConfigured() || (!endUnlocked && phase != EventPhase.BOSS_ACTIVE && phase != EventPhase.WAVE_3)) {
            if (sender != null) {
                message(sender, "&cОфициальный boss доступен только после Wave 3 или в BOSS_ACTIVE.");
            }
            return;
        }
        if (liveBoss() != null) {
            if (sender != null) {
                message(sender, "&eОфициальный boss уже активен.");
            }
            return;
        }
        if (phase == EventPhase.WAVE_3) {
            transition(EventPhase.BOSS_ACTIVE, "admin confirmed official boss", eventId + ":boss-confirm");
        }
        Location core = coreLocation();
        if (core == null) {
            return;
        }
        Enderman boss = (Enderman) core.getWorld().spawnEntity(core.clone().add(0.0D, 1.0D, 0.0D), EntityType.ENDERMAN);
        configureBoss(boss, false);
        getLogger().info("BOSS_SPAWNED event=" + eventId + " boss=" + boss.getUniqueId());
        if (sender != null) {
            message(sender, "&aОфициальный Rift Guardian создан.");
        }
    }

    private void configureBoss(Enderman boss, boolean test) {
        bossUuid = boss.getUniqueId();
        bossKillerUuid = null;
        tag(boss, EVENT_KIND_BOSS, 0, !test);
        setLootProfile(boss, test ? "test" : "boss");
        if (test) {
            tagTestBoss(boss);
        }
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        boss.setCanPickupItems(false);
        boss.setAware(true);
        boss.setCustomName("§5Хранитель Разлома");
        boss.setCustomNameVisible(true);
        AttributeInstance maxHealth = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(config.bossHealth());
        }
        boss.setHealth(config.bossHealth());
        AttributeInstance attack = boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() + config.bossAttackDamageBonus());
        }
        boss.setInvulnerable(false);
        ownedEntities.put(boss.getUniqueId(), boss);
        if (!test) {
            ensureBossBar();
            bindBossClientForOnlinePlayers();
            playEventMusic(config.bossMusic());
            nextTargetMillis = 0L;
            nextSpellMillis = 0L;
            recentBossTargets.clear();
            previousBossSpell = null;
            bossTargetCursor = 0;
            bossSpellCursor = 0;
            bossSpellPauseUntilMillis = 0L;
            lastBossTeleportMillis = 0L;
            servantsSummonedAt70 = false;
            servantsSummonedAt35 = false;
        }
    }

    private void ensureBossBar() {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("Хранитель Разлома", BarColor.PURPLE, BarStyle.SEGMENTED_20);
        }
        bossBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isCombatTarget(player) && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private LivingEntity liveBoss() {
        if (bossUuid == null) {
            return null;
        }
        Entity entity = ownedEntities.get(bossUuid);
        if (entity instanceof LivingEntity living && entity.isValid() && !entity.isDead()
                && EVENT_KIND_BOSS.equals(readString(entity, keyKind))) {
            return living;
        }
        Entity direct = Bukkit.getEntity(bossUuid);
        return direct instanceof LivingEntity living && direct.isValid() && !direct.isDead()
                && EVENT_KIND_BOSS.equals(readString(living, keyKind)) ? living : null;
    }

    private void clearBossOnly() {
        LivingEntity boss = liveBoss();
        if (boss != null) {
            boss.remove();
        }
        if (bossUuid != null) {
            ownedEntities.remove(bossUuid);
        }
        bossUuid = null;
        bossKillerUuid = null;
        testCombatAiMode = false;
        recentBossTargets.clear();
        previousBossSpell = null;
        bossTargetCursor = 0;
        bossSpellCursor = 0;
        nextTargetMillis = 0L;
        nextSpellMillis = 0L;
        bossSpellPauseUntilMillis = 0L;
        lastBossTeleportMillis = 0L;
        servantsSummonedAt70 = false;
        servantsSummonedAt35 = false;
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        clearClientEffects();
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void tickBoss() {
        LivingEntity boss = liveBoss();
        if (boss == null) {
            return;
        }
        Location core = coreLocation();
        enforceCombatLeash(boss, core, config.bossRadius(), "BOSS_AI_LEASH");
        if (bossBar != null) {
            double max = Math.max(1.0D, boss.getMaxHealth());
            bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, boss.getHealth() / max)));
            String title = halfHealthTriggered ? "Хранитель Разлома — Искажённая воля" : "Хранитель Разлома";
            bossBar.setTitle(title + " — " + Math.round(boss.getHealth()) + "/" + Math.round(max) + " HP");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isCombatTarget(player) && !bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                } else if (!isCombatTarget(player) && bossBar.getPlayers().contains(player)) {
                    bossBar.removePlayer(player);
                }
            }
        }
        boolean testBossAi = testCombatAiMode && isTestBoss(boss);
        if (phase != EventPhase.BOSS_ACTIVE && !testBossAi) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextTargetMillis <= now) {
            if (boss instanceof Mob mob) {
                rotateBossTarget(mob);
            }
            nextTargetMillis = now + randomSeconds(config.bossTargetMinSeconds(), config.bossTargetMaxSeconds()) * 1000L;
        }
        maintainBossTeleport(boss, now);
        if (now >= bossSpellPauseUntilMillis && nextSpellMillis <= now) {
            if (!activeLivingPlayers().isEmpty()) {
                castBossSpell(boss, false);
                nextSpellMillis = now + randomSeconds(config.bossSpellMinSeconds(), config.bossSpellMaxSeconds()) * 1000L;
            } else {
                // No eligible target is not a real cast; retry soon instead of
                // consuming the whole spell cooldown while the arena is empty.
                nextSpellMillis = now + 1000L;
            }
        }
    }

    private int randomSeconds(int minimum, int maximum) {
        return minimum >= maximum ? minimum : minimum + random.nextInt(maximum - minimum + 1);
    }

    private void rotateBossTarget(Mob boss) {
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(this::isCombatTarget)
                .map(player -> (Player) player)
                .sorted(Comparator.comparing(player -> player.getUniqueId().toString()))
                .toList();
        if (candidates.isEmpty()) {
            boss.setTarget(null);
            return;
        }
        Player current = boss.getTarget() instanceof Player player ? player : null;
        EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                candidates.stream().map(Player::getUniqueId).toList(),
                current == null ? null : current.getUniqueId(),
                new ArrayList<>(recentBossTargets), bossTargetCursor);
        bossTargetCursor = choice.nextCursor();
        Player target = choice.target() == null ? null : Bukkit.getPlayer(choice.target());
        if (target == null || !isCombatTarget(target)) {
            boss.setTarget(null);
            return;
        }
        boss.setTarget(target);
        recentBossTargets.addLast(target.getUniqueId());
        while (recentBossTargets.size() > config.bossRecentTargetMemory()) {
            recentBossTargets.removeFirst();
        }
        combatHelpers.add(target.getUniqueId());
        getLogger().info("BOSS_AI_TARGET boss=" + boss.getUniqueId() + " target=" + target.getUniqueId()
                + " recent=" + recentBossTargets.size());
    }

    private void maintainBossTeleport(LivingEntity boss, long now) {
        if (!(boss instanceof Mob mob) || now - lastBossTeleportMillis
                < config.bossTeleportCooldownSeconds() * 1000L) {
            return;
        }
        Location anchor = coreLocation();
        if (anchor == null || !(mob.getTarget() instanceof Player target)
                || !isCombatTarget(target)) {
            return;
        }
        boolean targetTooFar = horizontalDistanceSquared(boss.getLocation(), target.getLocation()) > 144.0D;
        boolean arenaTooWide = horizontalDistanceSquared(boss.getLocation(), anchor)
                > Math.max(1.0D, config.bossRadius() - 2.0D) * Math.max(1.0D, config.bossRadius() - 2.0D);
        if (!targetTooFar && !arenaTooWide) {
            return;
        }
        Location safe = findSafeCombatLocation(anchor, target.getLocation(), config.bossRadius() - 1.0D);
        if (safe != null && boss.teleport(safe)) {
            lastBossTeleportMillis = now;
            boss.getWorld().spawnParticle(Particle.PORTAL, safe.add(0.0D, 1.0D, 0.0D),
                    20, 0.45D, 0.7D, 0.45D, 0.02D);
            getLogger().info("BOSS_AI_TELEPORT boss=" + boss.getUniqueId()
                    + " target=" + target.getUniqueId() + " location=" + locationText(safe));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)
                || bossUuid == null || !bossUuid.equals(boss.getUniqueId())
                || !EVENT_KIND_BOSS.equals(readString(boss, keyKind))) {
            return;
        }
        if (isTestBoss(boss)) {
            return;
        }
        if (phase != EventPhase.BOSS_ACTIVE || boss.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        applyBossDamage(boss, Math.max(0.0D, event.getFinalDamage()), event instanceof EntityDamageByEntityEvent byEntity
                ? byEntity.getDamager() : null);
    }

    private void applyBossDamage(LivingEntity boss, double damage, Entity source) {
        if (boss == null || !boss.isValid() || boss.isDead() || isTestBoss(boss)) {
            return;
        }
        BossThresholdPolicy.Decision decision = BossThresholdPolicy.evaluate(
                boss.getHealth(), damage, boss.getMaxHealth(), config.bossHalfHealth(),
                config.bossFinalThreshold(), config.bossFinalHealth(), halfHealthTriggered, finalDrainTriggered);
        if (decision.triggerHalf()) {
            triggerHalfPhase(boss);
        }
        if (decision.triggerFinal()) {
            triggerFinalPhase(boss, false);
            return;
        }
        if (!boss.isInvulnerable()) {
            boss.setHealth(Math.max(1.0D, Math.min(boss.getMaxHealth(), decision.appliedHealth())));
        }
        if (source instanceof Player player && isActiveArenaParticipant(player)) {
            combatHelpers.add(player.getUniqueId());
        }
    }

    private void triggerHalfPhase(LivingEntity boss) {
        if (halfHealthTriggered) {
            return;
        }
        halfHealthTriggered = true;
        controlSpellUnlocked = true;
        // Persist the threshold marker before healing/control side effects.
        if (!saveStateSync()) {
            forcePhase(EventPhase.RECOVERY_REQUIRED, "half phase could not be persisted");
            return;
        }
        for (Player player : activeLivingPlayers()) {
            player.setHealth(player.getMaxHealth());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Печать слабеет... Энергия Разлома возвращает вам силы.");
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        bossSpellPauseUntilMillis = System.currentTimeMillis() + 3_000L;
        nextSpellMillis = bossSpellPauseUntilMillis;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.0F, 0.6F);
        playEventMusic(config.bossHalfMusic());
        getLogger().info("BOSS_PHASE_50 event=" + eventId + " boss=" + boss.getUniqueId()
                + " spell_unlocked=will_distortion spell_pause_ms=3000");
        getLogger().info("BOSS_AI_PHASE event=" + eventId + " phase=HALF control_spell=WILL_DISTORTION");
    }

    private List<Player> activeLivingPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(this::isCombatTarget)
                .map(player -> (Player) player)
                .toList();
    }

    private void triggerFinalPhase(LivingEntity boss, boolean forced) {
        if (boss == null || isTestBoss(boss) || finalDrainTriggered) {
            return;
        }
        if (!forced && phase != EventPhase.BOSS_ACTIVE) {
            return;
        }
        finalDrainTriggered = true;
        boss.setInvulnerable(true);
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        if (!saveStateSync()) {
            forcePhase(EventPhase.RECOVERY_REQUIRED, "final phase could not be persisted before side effects");
            return;
        }
        clearClientEffects();
        Location core = coreLocation();
        if (core != null) {
            boss.teleport(core.clone().add(0.0D, 1.0D, 0.0D));
        }
        boss.setHealth(Math.min(config.bossFinalHealth(), boss.getMaxHealth()));
        if (!transition(EventPhase.FINAL_DRAIN, "boss crossed final threshold", eventId + ":final-drain")) {
            return;
        }
        playEventMusic(config.bossFinalMusic());
        if (bossBar != null) {
            bossBar.setTitle("Хранитель Разлома — ПОГЛОЩЕНИЕ ЖИЗНИ");
        }
        applyFinalDrain(boss);
    }

    private void applyFinalDrain(LivingEntity boss) {
        if (!finalDrainApplied) {
            if (!finalDrainTargets.isEmpty()
                    && finalDrainAppliedPlayers.containsAll(finalDrainTargets.keySet())) {
                finalDrainApplied = true;
                if (!saveStateSync()) {
                    forcePhase(EventPhase.RECOVERY_REQUIRED, "final drain completion could not be persisted");
                    return;
                }
                scheduleFinalRitualVisual(boss);
                return;
            }
            List<Player> eligible = activeLivingPlayers();
            if (eligible.isEmpty()) {
                getLogger().info("FINAL_DRAIN_WAITING event=" + eventId + " reason=no eligible living players");
                return;
            }
            boolean planChanged = false;
            for (Player player : eligible) {
                UUID playerUuid = player.getUniqueId();
                if (!finalDrainTargets.containsKey(playerUuid)) {
                    double before = player.getHealth();
                    double after = FinalDrainMath.healthAfterDrain(
                            before, player.getMaxHealth(), config.finalDrainFraction(), config.finalDrainMinHealth());
                    finalDrainTargets.put(playerUuid, after);
                    participantUuids.add(playerUuid);
                    planChanged = true;
                }
            }
            // Persist every absolute target before changing any player health.
            // A restart can therefore replay the same target without applying
            // the percentage drain a second time.
            if (planChanged && !saveStateSync()) {
                forcePhase(EventPhase.RECOVERY_REQUIRED, "final drain plan could not be persisted");
                return;
            }
            for (Player player : eligible) {
                UUID playerUuid = player.getUniqueId();
                if (finalDrainAppliedPlayers.contains(playerUuid)
                        || !player.isOnline() || player.isDead() || player.getHealth() <= 0.0D) {
                    continue;
                }
                Double target = finalDrainTargets.get(playerUuid);
                if (target == null) {
                    continue;
                }
                player.setHealth(Math.max(config.finalDrainMinHealth(),
                        Math.min(player.getMaxHealth(), target)));
                finalDrainAppliedPlayers.add(playerUuid);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Хранитель вытягивает вашу жизненную силу!");
                if (!saveStateSync()) {
                    forcePhase(EventPhase.RECOVERY_REQUIRED, "final drain player result could not be persisted");
                    return;
                }
            }
            if (finalDrainTargets.isEmpty()
                    || !finalDrainAppliedPlayers.containsAll(finalDrainTargets.keySet())) {
                getLogger().info("FINAL_DRAIN_WAITING event=" + eventId
                        + " applied=" + finalDrainAppliedPlayers.size()
                        + " planned=" + finalDrainTargets.size());
                return;
            }
            finalDrainApplied = true;
            if (!saveStateSync()) {
                forcePhase(EventPhase.RECOVERY_REQUIRED, "final drain completion could not be persisted");
                return;
            }
        }
        scheduleFinalRitualVisual(boss);
    }

    private void scheduleFinalRitualVisual(LivingEntity boss) {
        if (taskRegistry == null || boss == null
                || (finalRitualVisualTask != null && !finalRitualVisualTask.isCancelled())) {
            return;
        }
        long callbackGeneration = generation;
        final int[] ticks = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!taskRegistry.owns(callbackGeneration)
                    || phase != EventPhase.FINAL_DRAIN && phase != EventPhase.FINAL_RITUAL
                    || !boss.isValid()) {
                holder[0].cancel();
                if (finalRitualVisualTask == holder[0]) {
                    finalRitualVisualTask = null;
                }
                return;
            }
            ticks[0] += 5;
            Location target = boss.getLocation().add(0.0D, 0.8D, 0.0D);
            Location core = coreLocation();
            for (Player player : activeLivingPlayers()) {
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        player.getLocation().add(0.0D, 1.0D, 0.0D), 4, 0.15D, 0.25D, 0.15D, 0.01D);
                if (core != null) {
                    spawnParticleLine(player.getLocation().add(0.0D, 1.0D, 0.0D), core, 3);
                }
            }
            if (core != null) {
                spawnParticleLine(core.clone().add(0.0D, 1.0D, 0.0D), target, 6);
            }
            if (ticks[0] >= config.finalRitualTelegraphTicks()) {
                holder[0].cancel();
                if (finalRitualVisualTask == holder[0]) {
                    finalRitualVisualTask = null;
                }
                if ((phase == EventPhase.FINAL_DRAIN || phase == EventPhase.FINAL_RITUAL)
                        && taskRegistry.owns(callbackGeneration)) {
                    if (transition(EventPhase.FINAL_WAVE, "final ritual visual complete", eventId + ":final-wave")) {
                        spawnWave(4, false);
                    }
                }
            }
        }, 1L, 5L);
        finalRitualVisualTask = holder[0];
        taskRegistry.register(holder[0]);
    }

    private void spawnParticleLine(Location from, Location to, int points) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector()).multiply(1.0D / Math.max(1, points));
        Location current = from.clone();
        for (int index = 0; index < points; index++) {
            current.add(delta);
            current.getWorld().spawnParticle(Particle.END_ROD, current, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void castBossSpell(LivingEntity boss, boolean forced) {
        boolean testBossAi = testCombatAiMode && isTestBoss(boss);
        if (boss == null || isTestBoss(boss) && !forced && !testBossAi
                || phase != EventPhase.BOSS_ACTIVE && !forced && !testBossAi) {
            return;
        }
        List<EndRiftAiPolicy.BossSpell> available = new ArrayList<>(List.of(
                EndRiftAiPolicy.BossSpell.VOID_BLAST,
                EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                EndRiftAiPolicy.BossSpell.VOID_MARK,
                EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS));
        if (controlSpellUnlocked && controlInstances.isEmpty()) {
            available.add(EndRiftAiPolicy.BossSpell.WILL_DISTORTION);
        }
        if (!servantSummonWindow(boss)) {
            available.remove(EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS);
        }
        EndRiftAiPolicy.BossSpell spell = EndRiftAiPolicy.chooseBossSpell(
                available, previousBossSpell, bossSpellCursor++);
        if (spell != null) {
            castBossSpell(boss, spell, forced);
        }
    }

    private void castBossSpell(LivingEntity boss, EndRiftAiPolicy.BossSpell spell, boolean forced) {
        boolean testBossAi = testCombatAiMode && isTestBoss(boss);
        if (boss == null || spell == null || isTestBoss(boss) && !forced && !testBossAi
                || phase != EventPhase.BOSS_ACTIVE && !forced && !testBossAi) {
            return;
        }
        Player target = selectBossSpellTarget(boss);
        if (target == null) {
            return;
        }
        previousBossSpell = spell;
        getLogger().info("BOSS_AI_SPELL_SELECTED boss=" + boss.getUniqueId()
                + " spell=" + spell.id() + " target=" + target.getUniqueId());
        telegraphBossSpell(boss, target, spell, forced);
    }

    private Player selectBossSpellTarget(LivingEntity boss) {
        List<Player> candidates = activeLivingPlayers();
        if (candidates.isEmpty()) {
            return null;
        }
        UUID current = boss instanceof Mob mob && mob.getTarget() != null
                ? mob.getTarget().getUniqueId() : null;
        EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                candidates.stream().map(Player::getUniqueId).sorted(Comparator.comparing(UUID::toString)).toList(),
                current, new ArrayList<>(recentBossTargets), bossTargetCursor++);
        Player target = choice.target() == null ? null : Bukkit.getPlayer(choice.target());
        return target != null && isCombatTarget(target) ? target : candidates.get(0);
    }

    private void telegraphBossSpell(LivingEntity boss, Player target,
                                    EndRiftAiPolicy.BossSpell spell, boolean forced) {
        if (taskRegistry == null || boss == null || target == null) {
            return;
        }
        if (!forced && phase != EventPhase.BOSS_ACTIVE
                && !(testCombatAiMode && isTestBoss(boss))) {
            return;
        }
        long callbackGeneration = generation;
        UUID bossId = boss.getUniqueId();
        Location mark = target.getLocation().clone();
        getLogger().info("BOSS_SPELL_TELEGRAPH boss=" + bossId + " spell=" + spell.id()
                + " target=" + target.getUniqueId() + " generation=" + generation);
        target.sendActionBar(Component.text("Хранитель готовит: " + spell.displayName(), NamedTextColor.LIGHT_PURPLE));
        final int[] ticks = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean allowedPhase = phase == EventPhase.BOSS_ACTIVE
                    || (forced || testCombatAiMode) && isTestBoss(boss);
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration) || !allowedPhase
                    || !boss.isValid() || boss.isDead() || !bossId.equals(bossUuid)) {
                holder[0].cancel();
                return;
            }
            ticks[0] += 5;
            Particle particle = spell == EndRiftAiPolicy.BossSpell.VOID_BLAST
                    ? Particle.DRAGON_BREATH : Particle.REVERSE_PORTAL;
            Location effect = spell == EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS
                    ? boss.getLocation().add(0.0D, 1.0D, 0.0D) : mark;
            boss.getWorld().spawnParticle(particle, effect, 12, 0.9D, 0.2D, 0.9D, 0.02D);
            if (ticks[0] >= config.bossSpellTelegraphTicks()) {
                holder[0].cancel();
                if (taskRegistry.owns(callbackGeneration) && allowedPhase
                        && boss.isValid() && !boss.isDead() && bossId.equals(bossUuid)) {
                    executeBossSpell(boss, target, mark, spell, forced, callbackGeneration);
                }
            }
        }, 0L, 5L);
        taskRegistry.register(holder[0]);
    }

    private void executeBossSpell(LivingEntity boss, Player target, Location mark,
                                   EndRiftAiPolicy.BossSpell spell, boolean forced,
                                   long callbackGeneration) {
        if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                || target == null
                || (phase != EventPhase.BOSS_ACTIVE
                && !(isTestBoss(boss) && (testCombatAiMode || phase != EventPhase.VICTORY_PROCESSING)))) {
            return;
        }
        launchSpellFlight(boss, mark,
                "BOSS_SPELL_FLIGHT", spell.id(), target.getUniqueId(), forced,
                callbackGeneration, () -> {
                    if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                            || !isSpellFlightAllowed(boss, forced)
                            || !boss.getUniqueId().equals(bossUuid)
                            || (spell != EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS
                            && !isCombatTarget(target))) {
                        return;
                    }
                    getLogger().info("BOSS_SPELL_CAST boss=" + boss.getUniqueId() + " spell=" + spell.id()
                            + " target=" + target.getUniqueId() + " generation=" + callbackGeneration);
                    switch (spell) {
                        case VOID_BLAST -> voidBlast(boss, target);
                        case RIFT_PROJECTILE -> riftProjectile(boss, target);
                        case VOID_MARK -> voidMark(boss, target);
                        case SUMMON_SERVANTS -> summonServants(boss);
                        case WILL_DISTORTION -> sendControlStart(target);
                    }
                });
    }

    private void voidBlast(LivingEntity boss, Player target) {
        Location center = target.getLocation();
        boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 24, 1.0D, 0.4D, 1.0D, 0.04D);
        boss.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_STARE, 0.8F, 0.7F);
        for (Player player : activeLivingPlayers()) {
            if (player.getLocation().distanceSquared(center) <= 16.0D) {
                player.damage(8.0D, boss);
                Vector push = player.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() < 0.01D) {
                    push = new Vector(0.0D, 0.3D, 0.0D);
                }
                player.setVelocity(push.normalize().multiply(0.45D).setY(0.3D));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
            }
        }
    }

    private void riftProjectile(LivingEntity boss, Player target) {
        if (activeRiftProjectiles.size() >= MAX_ACTIVE_RIFT_PROJECTILES
                || boss == null || target == null || boss.getWorld() == null) {
            return;
        }
        Location start = boss.getLocation().add(0.0D, 1.0D, 0.0D);
        Vector offset = target.getEyeLocation().toVector().subtract(start.toVector());
        if (offset.lengthSquared() < 0.01D) {
            return;
        }
        Snowball projectile = boss.getWorld().spawn(start, Snowball.class);
        projectile.setGravity(false);
        projectile.setInvisible(true);
        projectile.setVisibleByDefault(false);
        projectile.setShooter(boss);
        projectile.setVelocity(offset.normalize().multiply(RIFT_PROJECTILE_SPEED));
        tag(projectile, EVENT_KIND_PROJECTILE, 0, isOfficialEntity(boss));
        activeRiftProjectiles.add(projectile.getUniqueId());
        long callbackGeneration = generation;
        Location anchor = coreLocation();
        final int[] age = {0};
        UUID projectileId = projectile.getUniqueId();
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean allowed = (phase == EventPhase.BOSS_ACTIVE
                    || (testCombatAiMode && isTestBoss(boss)))
                    && boss.isValid() && !boss.isDead() && boss.getUniqueId().equals(bossUuid)
                    && projectile.isValid() && !projectile.isDead()
                    && activeRiftProjectiles.contains(projectileId);
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration) || !allowed
                    || age[0]++ >= RIFT_PROJECTILE_MAX_TICKS
                    || anchor == null || projectile.getLocation().getWorld() == null
                    || !projectile.getLocation().getWorld().equals(anchor.getWorld())
                    || projectile.getLocation().distanceSquared(anchor) > config.bossRadius()
                    * config.bossRadius()) {
                cleanupRiftProjectile(projectileId);
                return;
            }
            spawnRiftProjectileTrail(projectile.getLocation(), projectile.getVelocity(), age[0]);
        }, 1L, 1L);
        riftProjectileTasks.put(projectileId, holder[0]);
        taskRegistry.register(holder[0]);
        getLogger().info("BOSS_PROJECTILE_SPAWN entity=" + projectileId
                + " visual=particle-only pattern=rift_projectile"
                + " target=" + target.getUniqueId() + " max_ticks=" + RIFT_PROJECTILE_MAX_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiftProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball projectile)
                || !activeRiftProjectiles.contains(projectile.getUniqueId())) {
            return;
        }
        LivingEntity boss = liveBoss();
        Entity hit = event.getHitEntity();
        if (hit instanceof Player player && boss != null && isCombatTarget(player)
                && (phase == EventPhase.BOSS_ACTIVE || testCombatAiMode && isTestBoss(boss))) {
            player.damage(7.0D, boss);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    80, 1, false, true, true));
            getLogger().info("BOSS_PROJECTILE_HIT entity=" + projectile.getUniqueId()
                    + " target=" + player.getUniqueId());
        }
        cleanupRiftProjectile(projectile.getUniqueId());
    }

    private void cleanupRiftProjectile(UUID projectileId) {
        if (projectileId == null) {
            return;
        }
        BukkitTask task = riftProjectileTasks.remove(projectileId);
        if (task != null) {
            task.cancel();
        }
        activeRiftProjectiles.remove(projectileId);
        Entity projectile = ownedEntities.remove(projectileId);
        if (projectile != null && projectile.isValid()) {
            projectile.remove();
        }
    }

    private void clearActiveRiftProjectiles() {
        for (UUID projectileId : new HashSet<>(activeRiftProjectiles)) {
            cleanupRiftProjectile(projectileId);
        }
        activeRiftProjectiles.clear();
        riftProjectileTasks.clear();
    }

    private void voidMark(LivingEntity boss, Player target) {
        if (taskRegistry == null || activeVoidMarkTasks.size() >= MAX_ACTIVE_VOID_MARKS) {
            return;
        }
        Location mark = target.getLocation().clone();
        UUID zoneId = UUID.randomUUID();
        long callbackGeneration = generation;
        activeVoidMarkCenters.put(zoneId, mark);
        boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mark.clone().add(0.0D, 0.2D, 0.0D),
                18, 1.0D, 0.1D, 1.0D, 0.02D);
        final int[] elapsedSeconds = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean testBossAi = testCombatAiMode && isTestBoss(boss);
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || phase != EventPhase.BOSS_ACTIVE && !testBossAi || !boss.isValid() || boss.isDead()
                    || !boss.getUniqueId().equals(bossUuid)) {
                cancelVoidMark(zoneId);
                return;
            }
            Location center = activeVoidMarkCenters.get(zoneId);
            if (center == null || elapsedSeconds[0] >= VOID_MARK_DURATION_SECONDS) {
                cancelVoidMark(zoneId);
                return;
            }
            boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    center.clone().add(0.0D, 0.12D, 0.0D), 20,
                    1.0D, 0.08D, 1.0D, 0.02D);
            for (Player player : activeLivingPlayers()) {
                if (player.getLocation().distanceSquared(center)
                        <= VOID_MARK_RADIUS_BLOCKS * VOID_MARK_RADIUS_BLOCKS) {
                    player.damage(2.0D, boss);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                            25, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            25, 0, false, true, true));
                }
            }
            elapsedSeconds[0]++;
            if (elapsedSeconds[0] >= VOID_MARK_DURATION_SECONDS) {
                cancelVoidMark(zoneId);
            }
        }, 0L, 20L);
        activeVoidMarkTasks.put(zoneId, holder[0]);
        taskRegistry.register(holder[0]);
    }

    private void cancelVoidMark(UUID zoneId) {
        BukkitTask task = activeVoidMarkTasks.remove(zoneId);
        if (task != null) {
            task.cancel();
        }
        activeVoidMarkCenters.remove(zoneId);
    }

    private void clearVoidMarkZones() {
        if (activeVoidMarkTasks.isEmpty() && activeVoidMarkCenters.isEmpty()) {
            return;
        }
        int count = activeVoidMarkTasks.size();
        for (BukkitTask task : activeVoidMarkTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeVoidMarkTasks.clear();
        activeVoidMarkCenters.clear();
        getLogger().info("BOSS_VOID_MARK_CLEANUP count=" + count);
    }

    private boolean servantSummonWindow(LivingEntity boss) {
        if (boss == null || boss.getMaxHealth() <= 0.0D) {
            return false;
        }
        double fraction = boss.getHealth() / boss.getMaxHealth();
        return (fraction <= 0.70D && fraction > 0.35D && !servantsSummonedAt70)
                || (fraction <= 0.35D && !servantsSummonedAt35);
    }

    private void summonServants(LivingEntity boss) {
        if (spellServants.size() >= config.maxSummonedServants()) {
            return;
        }
        double fraction = boss.getMaxHealth() <= 0.0D ? 0.0D : boss.getHealth() / boss.getMaxHealth();
        boolean at70 = fraction <= 0.70D && fraction > 0.35D && !servantsSummonedAt70;
        boolean at35 = fraction <= 0.35D && !servantsSummonedAt35;
        if (!at70 && !at35) {
            return;
        }
        if (at35) {
            servantsSummonedAt35 = true;
        } else {
            servantsSummonedAt70 = true;
        }
        Location location = boss.getLocation();
        int toSpawn = Math.min(2, config.maxSummonedServants() - spellServants.size());
        for (int index = 0; index < toSpawn; index++) {
            Entity servant = spawnOwnedMob(location.getWorld(), location, EntityType.SPIDER,
                    0, EVENT_KIND_WAVE_MOB, false, index + spellServants.size());
            spellServants.add(servant.getUniqueId());
        }
        getLogger().info("BOSS_SERVANTS_SUMMON boss=" + boss.getUniqueId()
                + " threshold=" + (at35 ? "35" : "70") + " count=" + toSpawn);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnedEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!ownedEntities.containsKey(entity.getUniqueId())) {
            return;
        }
        if (!lootIssuedEntityUuids.add(entity.getUniqueId())) {
            return;
        }
        String kind = readString(entity, keyKind);
        event.getDrops().clear();
        if (EVENT_KIND_BOSS.equals(kind) && entity.getUniqueId().equals(bossUuid)) {
            if (entity instanceof LivingEntity living && living.getKiller() != null) {
                bossKillerUuid = living.getKiller().getUniqueId();
            }
            event.setDroppedExp(0);
            ownedEntities.remove(entity.getUniqueId());
            bossUuid = null;
            if (isTestBoss(entity)) {
                addConfiguredDrops(event, config.lootProfile("test"), "test");
                clearBossOnly();
                getLogger().info("TEST_BOSS_DEFEATED event=" + eventId);
                return;
            }
            if (!officialBossDeathCommitted && phase == EventPhase.BOSS_FINISH
                    && isOfficialEntity(entity)) {
                officialBossDeathCommitted = true;
                victoryStep = VICTORY_BOSS_DEATH;
                saveStateSync();
                getLogger().info("BOSS_DEFEATED event=" + eventId + " boss=" + entity.getUniqueId());
                beginVictory();
            }
        } else {
            String profile = readString(entity, keyLootProfile);
            if (profile.isBlank()) {
                profile = isOfficialEntity(entity) ? "final-wave" : "test";
            }
            addConfiguredDrops(event, config.lootProfile(profile), profile);
            finalWaveEntities.remove(entity.getUniqueId());
            spellServants.remove(entity.getUniqueId());
            miniBossSpells.remove(entity.getUniqueId());
            nextMiniBossSpellMillis.remove(entity.getUniqueId());
            ownedEntities.remove(entity.getUniqueId());
        }
    }

    private void addConfiguredDrops(EntityDeathEvent event,
                                    Map<String, EventConfig.LootEntry> configured,
                                    String profileId) {
        if (event == null || configured == null || configured.isEmpty()) {
            return;
        }
        UUID entityUuid = event.getEntity().getUniqueId();
        long seed = 31L * eventId.hashCode() + generation;
        seed = 31L * seed + entityUuid.getMostSignificantBits();
        seed = 31L * seed + entityUuid.getLeastSignificantBits();
        seed = 31L * seed + (profileId == null ? 0L : profileId.hashCode());
        SplittableRandom rng = new SplittableRandom(seed);
        for (Map.Entry<String, EventConfig.LootEntry> entry : configured.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Material material = Material.matchMaterial(entry.getKey());
            int remaining = entry.getValue() == null ? 0 : entry.getValue().roll(rng);
            if (material == null || remaining < 1) {
                continue;
            }
            int stackLimit = Math.max(1, material.getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(stackLimit, remaining);
                event.getDrops().add(new ItemStack(material, amount));
                remaining -= amount;
            }
        }
    }

    private void beginVictory() {
        clearClientEffects();
        clearActiveRiftProjectiles();
        if (bossBar != null) {
            bossBar.removeAll();
        }
        if (phase == EventPhase.BOSS_FINISH) {
            transition(EventPhase.VICTORY_PROCESSING, "official boss death committed", eventId + ":victory-processing");
        }
        issueVictoryRewards();
    }

    private void unlockEnd(CommandSender sender, String cause) {
        if (worldAccessService == null) {
            message(sender, "&cWorldCore service ещё не готов.");
            return;
        }
        if (!officialBossDeathCommitted && !VICTORY_BOSS_DEATH.equals(victoryStep)) {
            message(sender, "&cEnd открывается только после смерти настоящего official Rift Guardian.");
            return;
        }
        if (!bossLootCommitted || !returnStoneAccepted() || !allShardRewardsAccepted()) {
            message(sender, "&cСначала завершите durable-выдачу наград босса и Осколков Разлома.");
            return;
        }
        if (endUnlocked || worldAccessService.isEndEnabled()) {
            endUnlocked = true;
            victoryStep = VICTORY_UNLOCKED;
            saveStateSync();
            issueVictoryRewards();
            return;
        }
        victoryStep = VICTORY_UNLOCK_PENDING;
        if (!saveStateSync()) {
            message(sender, "&cUnlock не выполнен: durable state недоступен.");
            return;
        }
        WorldAccessResult result = worldAccessService.setEndEnabled(
                true, "CopiMineEndEvent:" + cause, "end-event:" + eventId + ":unlock");
        if (result == null || !result.success()) {
            getLogger().warning("END unlock failed code=" + (result == null ? "NULL" : result.code()));
            message(sender, "&cEnd unlock не выполнен; victory saga останется на recovery.");
            return;
        }
        endUnlocked = true;
        victoryStep = VICTORY_UNLOCKED;
        saveStateSync();
        getLogger().info("END_UNLOCKED event=" + eventId + " code=" + result.code());
        issueVictoryRewards();
    }

    private void issueVictoryRewards() {
        if (rewardService == null || officialRewardRoster.isEmpty()) {
            return;
        }
        if (!VICTORY_COMPLETE.equals(victoryStep)
                && !VICTORY_REWARDS_PENDING.equals(victoryStep)
                && !VICTORY_REWARDS_DELIVERED.equals(victoryStep)) {
            victoryStep = VICTORY_REWARDS_PENDING;
            saveStateAsync();
        }
        for (UUID playerUuid : new LinkedHashSet<>(officialRewardRoster)) {
            String status = rewardStatuses.getOrDefault(playerUuid, "PENDING");
            if ("DELIVERED".equals(status) || "ALREADY_ISSUED".equals(status)
                    || "PENDING_DELIVERY".equals(status)) {
                continue;
            }
            if (!rewardRequestsInFlight.add(playerUuid)) {
                continue;
            }
            String key = "end-event:" + eventId + ":participant:" + playerUuid + ":rift-core-shard";
            EventArtifactRewardRequest request = EventArtifactRewardRequest.toPlayer(
                    eventId, key, config.shardItemId(), playerUuid, offlineName(playerUuid));
            rewardStatuses.put(playerUuid, "REQUESTED");
            CompletableFuture<RewardIssueResult> future = rewardService.issueToPlayer(request);
            future.whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                rewardRequestsInFlight.remove(playerUuid);
                if (error != null || result == null || !result.accepted()) {
                    rewardStatuses.put(playerUuid, "PENDING_RETRY");
                    getLogger().log(Level.WARNING, "Rift Shard reward remains pending for " + playerUuid, error);
                } else {
                    rewardStatuses.put(playerUuid, result.status());
                    getLogger().info("RIFT_SHARD_REWARD event=" + eventId + " player=" + playerUuid
                            + " status=" + result.status());
                }
                saveStateAsync();
                checkVictoryRewardCompletion();
            }));
        }
        if ("PENDING".equals(returnStoneStatus) || "PENDING_RETRY".equals(returnStoneStatus)) {
            Location drop = liveBoss() == null ? coreLocation() : liveBoss().getLocation();
            if (drop != null) {
                String key = "end-event:" + eventId + ":boss:return-stone";
                EventArtifactRewardRequest request = EventArtifactRewardRequest.worldDrop(
                        eventId, key, config.returnStoneItemId());
                returnStoneStatus = "REQUESTED";
                rewardService.issueWorldDrop(request, drop).whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null || result == null || !result.accepted()) {
                        returnStoneStatus = "PENDING_RETRY";
                        getLogger().log(Level.WARNING, "Return Stone reward remains pending", error);
                    } else {
                        returnStoneStatus = result.status();
                    }
                    saveStateAsync();
                    checkVictoryRewardCompletion();
                }));
            }
        }
        applyBossLootOnce();
        checkVictoryRewardCompletion();
    }

    private void applyBossLootOnce() {
        if (bossLootCommitted || BOSS_REWARDS_DELIVERED.equals(bossRewardStatus)) {
            bossLootCommitted = true;
            bossRewardStatus = BOSS_REWARDS_DELIVERED;
            return;
        }
        if (BOSS_REWARDS_REVIEW.equals(bossRewardStatus)) {
            getLogger().severe("BOSS_REWARDS_REVIEW_REQUIRED event=" + eventId
                    + " recipient=" + bossRewardRecipientUuid);
            return;
        }
        Player recipient = BOSS_REWARDS_RESERVED.equals(bossRewardStatus)
                && bossRewardRecipientUuid != null
                ? Bukkit.getPlayer(bossRewardRecipientUuid) : bossLootRecipient();
        if (recipient == null) {
            bossRewardStatus = BOSS_REWARDS_RETRY;
            saveStateSync();
            getLogger().info("BOSS_LOOT_WAITING event=" + eventId + " reason=no official recipient online");
            return;
        }
        if (!canFitBossBundle(recipient)) {
            bossRewardStatus = BOSS_REWARDS_RETRY;
            saveStateSync();
            getLogger().info("BOSS_LOOT_WAITING event=" + eventId + " player=" + recipient.getUniqueId()
                    + " reason=inventory-full");
            return;
        }
        if (!BOSS_REWARDS_RESERVED.equals(bossRewardStatus)) {
            bossRewardRecipientUuid = recipient.getUniqueId();
            bossRewardStatus = BOSS_REWARDS_RESERVED;
            bossLootCommitted = false;
            // The recipient and exact bundle are durable before any physical
            // mutation.  A restart can therefore retry only this reserved
            // bundle instead of choosing a different player or issuing a
            // second logical reward.
            if (!saveStateSync()) {
                bossRewardStatus = BOSS_REWARDS_RETRY;
                bossRewardRecipientUuid = null;
                return;
            }
        }
        recipient.giveExp(config.bossXp());
        for (Map.Entry<String, Integer> entry : config.resourceBundle().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material != null && entry.getValue() > 0) {
                Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(
                        new ItemStack(material, entry.getValue()));
                if (!leftovers.isEmpty()) {
                    // Never mark a partially applied bundle delivered.  Stop
                    // the saga for operator review rather than retrying and
                    // duplicating the part that already entered inventory.
                    bossRewardStatus = BOSS_REWARDS_REVIEW;
                    bossLootCommitted = false;
                    saveStateSync();
                    getLogger().severe("BOSS_REWARDS_REVIEW_REQUIRED event=" + eventId
                            + " reason=bundle-partially-applied recipient=" + recipient.getUniqueId());
                    return;
                }
            }
        }
        bossLootCommitted = true;
        bossRewardStatus = BOSS_REWARDS_DELIVERED;
        if (!saveStateSync()) {
            // Do not blindly retry an already applied physical bundle after a
            // failed commit.  Keep the durable reservation and fail closed;
            // an operator can reconcile the single recipient without opening
            // the End or issuing shards automatically.
            bossLootCommitted = false;
            bossRewardStatus = BOSS_REWARDS_REVIEW;
            saveStateSync();
            getLogger().severe("BOSS_REWARDS_REVIEW_REQUIRED event=" + eventId
                    + " reason=delivery-commit-failed recipient=" + recipient.getUniqueId());
            return;
        }
        getLogger().info("BOSS_REWARDS_DELIVERED event=" + eventId
                + " recipient=" + recipient.getUniqueId() + " xp=" + config.bossXp()
                + " bundle=" + config.resourceBundle());
    }

    private Player bossLootRecipient() {
        if (bossKillerUuid != null && officialRewardRoster.contains(bossKillerUuid)) {
            Player killer = Bukkit.getPlayer(bossKillerUuid);
            if (killer != null && killer.isOnline()) {
                return killer;
            }
        }
        return officialRewardRoster.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .findFirst()
                .orElse(null);
    }

    private boolean canFitBossBundle(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack[] simulated = player.getInventory().getStorageContents();
        for (Map.Entry<String, Integer> entry : config.resourceBundle().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            int remaining = entry.getValue() == null ? 0 : entry.getValue();
            if (material == null || remaining < 1) {
                continue;
            }
            ItemStack probe = new ItemStack(material);
            for (ItemStack stack : simulated) {
                if (remaining < 1) {
                    break;
                }
                if (stack != null && stack.isSimilar(probe)) {
                    int capacity = Math.max(0, stack.getMaxStackSize() - stack.getAmount());
                    int added = Math.min(capacity, remaining);
                    stack.setAmount(stack.getAmount() + added);
                    remaining -= added;
                }
            }
            for (int index = 0; index < simulated.length && remaining > 0; index++) {
                if (simulated[index] == null || simulated[index].getType() == Material.AIR) {
                    int added = Math.min(material.getMaxStackSize(), remaining);
                    simulated[index] = new ItemStack(material, added);
                    remaining -= added;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void checkVictoryRewardCompletion() {
        if (VICTORY_COMPLETE.equals(victoryStep)) {
            return;
        }
        if (!bossLootCommitted || !returnStoneAccepted() || !allShardRewardsAccepted()) {
            return;
        }
        if (!endUnlocked) {
            victoryStep = VICTORY_REWARDS_DELIVERED;
            saveStateSync();
            unlockEnd(null, "official-victory");
            return;
        }
        if (isGateConfigured() && !"OPENED".equalsIgnoreCase(layoutState.gateStatus())) {
            if (!victoryGatePending) {
                openGate(null, DEFAULT_GATE_TICKS_PER_LAYER, "official-victory", true);
            }
            return;
        }
        if (isGateConfigured()) {
            victoryStep = VICTORY_GATE_OPENED;
            saveStateSync();
        } else {
            getLogger().info("END_EVENT_GATE_OPENED event=" + eventId
                    + " code=NO_CONFIGURED_GATE reason=official-victory");
        }
        victoryStep = VICTORY_REWARDS_DELIVERED;
        saveStateSync();
        announceVictory();
        restoreCoreAndPads();
        endUnlocked = true;
        victoryStep = VICTORY_COMPLETE;
        forcePhase(EventPhase.UNLOCKED, "victory saga complete");
        saveStateSync();
    }

    private void resumeVictorySaga() {
        if (!officialBossDeathCommitted || officialRewardRoster.isEmpty()) {
            return;
        }
        if (worldAccessService != null && worldAccessService.isEndEnabled()) {
            endUnlocked = true;
        }
        issueVictoryRewards();
    }

    private boolean returnStoneAccepted() {
        return "DELIVERED".equals(returnStoneStatus)
                || "PENDING_DELIVERY".equals(returnStoneStatus)
                || "ALREADY_ISSUED".equals(returnStoneStatus)
                || "WORLD_PENDING".equals(returnStoneStatus);
    }

    private boolean allShardRewardsAccepted() {
        return officialRewardRoster.stream().allMatch(playerUuid -> {
            String status = rewardStatuses.get(playerUuid);
            return "DELIVERED".equals(status) || "ALREADY_ISSUED".equals(status)
                    || "PENDING_DELIVERY".equals(status);
        });
    }

    private String offlineName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null || name.isBlank() ? "Participant" : name;
    }

    private void announceVictory() {
        String names = participantUuids.stream().map(this::offlineName).sorted()
                .reduce((left, right) -> left + ", " + right).orElse("участники");
        String rewardNames = officialRewardRoster.stream().map(this::offlineName).sorted()
                .reduce((left, right) -> left + ", " + right).orElse("никто");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§5ЭНД ОТКРЫТ", "§dХранитель Разлома пал", 10, 80, 20);
            player.sendMessage("§5Энд открыт. Все участники: §f" + names
                    + " §7| §5Наградный roster: §f" + rewardNames);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            player.playSound(player.getLocation(), config.victoryMusic().soundId(), SoundCategory.MUSIC,
                    (float) config.musicVolume(), 1.0F);
        }
        getLogger().info("END_EVENT_MUSIC track=" + config.victoryMusic().soundId() + " phase=VICTORY_PROCESSING");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recoverUnresolvedDepositsFor(player);
        resumeVictorySaga();
        if (isCombatPhase()) {
            bindBossClient(player);
            bindEventEntitiesClient(player);
            syncEventMusic(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stopControl(uuid);
        cancelShardChannel(uuid);
        padOccupants.values().removeIf(uuid::equals);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        stopControl(uuid);
        cancelShardChannel(uuid);
        padOccupants.values().removeIf(uuid::equals);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onShardChannelDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelShardChannel(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stopControl(uuid);
        cancelShardChannel(uuid);
        padOccupants.values().removeIf(uuid::equals);
        if (isCombatPhase()) {
            bindBossClient(event.getPlayer());
            bindEventEntitiesClient(event.getPlayer());
            syncEventMusic(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShardInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!config.shardItemId().equalsIgnoreCase(readArtifactItemId(stack))) {
            return;
        }
        if (rewardService == null || !rewardService.isAuthenticArtifact(stack, player, "rift_shard_use")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Подлинность Осколка Разлома не подтверждена.");
            return;
        }
        event.setCancelled(true);
        startShardChannel(player);
    }

    private void recoverUnresolvedDepositsFor(Player player) {
        for (DepositJournal.Entry entry : depositJournal.unresolved()) {
            if (!entry.playerUuid().equals(player.getUniqueId())) {
                continue;
            }
            int progress = depositedResources.getOrDefault(entry.material().name(), 0);
            if (progress >= entry.afterProgress()) {
                depositJournal.commit(entry);
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(entry.material(), entry.amount()));
            if (leftovers.isEmpty()) {
                depositJournal.refund(entry);
            }
        }
    }

    private String readArtifactItemId(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return "";
        }
        NamespacedKey key = keyArtifactItemId;
        if (key == null) {
            return "";
        }
        return stack.getItemMeta().getPersistentDataContainer().getOrDefault(
                key, PersistentDataType.STRING, "");
    }

    private void startShardChannel(Player player) {
        if (!endUnlocked || worldAccessService == null || !worldAccessService.isEndEnabled()) {
            player.sendMessage(ChatColor.RED + "Осколок работает только после открытия Энда.");
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownUntil = shardCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (cooldownUntil > now) {
            long seconds = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            player.sendMessage(ChatColor.YELLOW + "Разлом ещё нестабилен. Осталось: " + formatDuration(seconds));
            return;
        }
        if (shardChannelTasks.containsKey(player.getUniqueId())) {
            return;
        }
        Location destination = safePortalDestination();
        if (destination == null) {
            player.sendMessage(ChatColor.RED + "Портальная комната сейчас недоступна; cooldown не начат.");
            getLogger().warning("Rift Shard destination is invalid; teleport refused.");
            return;
        }
        UUID uuid = player.getUniqueId();
        Location start = player.getLocation().clone();
        shardChannelStarts.put(uuid, start);
        final int[] elapsed = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || player.isDead() || !player.getWorld().equals(start.getWorld())
                    || player.getLocation().distanceSquared(start) > 0.36D) {
                holder[0].cancel();
                shardChannelTasks.remove(uuid);
                shardChannelStarts.remove(uuid);
                return;
            }
            elapsed[0] += 5;
            int total = config.shardChannelSeconds() * 20;
            player.sendActionBar(Component.text("Осколок: " + Math.min(100, elapsed[0] * 100 / total) + "%", NamedTextColor.LIGHT_PURPLE));
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 5, 0.25D, 0.4D, 0.25D, 0.02D);
            if (elapsed[0] >= total) {
                holder[0].cancel();
                shardChannelTasks.remove(uuid);
                shardChannelStarts.remove(uuid);
                finishShardChannel(player, start, destination);
            }
        }, 1L, 5L);
        shardChannelTasks.put(uuid, holder[0]);
    }

    private void finishShardChannel(Player player, Location start, Location destination) {
        if (!player.isOnline() || player.isDead() || destination == null
                || !endUnlocked || worldAccessService == null || !worldAccessService.isEndEnabled()) {
            return;
        }
        Location currentDestination = safePortalDestination();
        if (currentDestination == null) {
            player.sendMessage(ChatColor.RED + "Портальная комната стала небезопасной; cooldown не начат.");
            return;
        }
        destination = currentDestination;
        if (!player.teleport(destination)) {
            player.sendMessage(ChatColor.RED + "Teleport не выполнен; cooldown не начат.");
            return;
        }
        long cooldownUntil = System.currentTimeMillis() + config.shardCooldownSeconds() * 1000L;
        shardCooldowns.put(player.getUniqueId(), cooldownUntil);
        if (!saveStateSync()) {
            shardCooldowns.remove(player.getUniqueId());
            if (start != null) {
                player.teleport(start);
            }
            saveStateSync();
            player.sendMessage(ChatColor.RED + "Cooldown не сохранён; teleport отменён.");
            return;
        }
        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Осколок перенёс тебя в комнату портала.");
    }

    private Location safePortalDestination() {
        EventLayoutState.Portal portal = portalRoom();
        World world = Bukkit.getWorld(portal.world());
        if (world == null) {
            return null;
        }
        Location destination = new Location(world, portal.x(), portal.y(), portal.z(), portal.yaw(), portal.pitch());
        Block feet = destination.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        if (!feet.isPassable() || !head.isPassable() || !floor.getType().isSolid()) {
            return null;
        }
        return destination;
    }

    private void cancelShardChannel(UUID uuid) {
        BukkitTask task = shardChannelTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        shardChannelStarts.remove(uuid);
    }

    private void tickShardChannels() {
        // Channel progress is owned by its individual task.  This method is a
        // bounded hook for diagnostics and deliberately performs no world scan.
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes + "м " + remainder + "с";
    }

    private void tag(Entity entity, String kind, int wave, boolean official) {
        if (entity == null || entity.getPersistentDataContainer() == null) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(keyEventId, PersistentDataType.STRING, eventId);
        data.set(keyGeneration, PersistentDataType.LONG, generation);
        data.set(keyKind, PersistentDataType.STRING, kind);
        data.set(keyWave, PersistentDataType.INTEGER, wave);
        data.set(keyEventSessionId, PersistentDataType.STRING, eventId);
        data.set(keyEventRole, PersistentDataType.STRING, kind);
        data.set(keyEventWave, PersistentDataType.INTEGER, wave);
        data.set(keyEventGeneration, PersistentDataType.LONG, generation);
        data.set(keyLootProfile, PersistentDataType.STRING, official ? "END_RIFT_OFFICIAL" : "END_RIFT_TEST");
        data.set(keyOfficial, PersistentDataType.BYTE, official ? (byte) 1 : (byte) 0);
        ownedEntities.put(entity.getUniqueId(), entity);
    }

    private void setLootProfile(Entity entity, String profileId) {
        if (entity == null || keyLootProfile == null || profileId == null || profileId.isBlank()) {
            return;
        }
        entity.getPersistentDataContainer().set(keyLootProfile, PersistentDataType.STRING, profileId);
    }

    private void tagTestBoss(Entity entity) {
        entity.getPersistentDataContainer().set(keyBossTest, PersistentDataType.BYTE, (byte) 1);
    }

    private void tagMiniBossSpell(Entity entity, EndRiftAiPolicy.MiniBossSpell spell) {
        if (entity == null || spell == null || keyMiniBossSpell == null) {
            return;
        }
        entity.getPersistentDataContainer().set(keyMiniBossSpell, PersistentDataType.STRING, spell.id());
    }

    private boolean isTestBoss(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(keyBossTest, PersistentDataType.BYTE)
                && entity.getPersistentDataContainer().get(keyBossTest, PersistentDataType.BYTE) == (byte) 1;
    }

    private boolean isOfficialEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().getOrDefault(
                keyOfficial, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private String readString(Entity entity, NamespacedKey key) {
        if (entity == null || key == null) {
            return "";
        }
        return entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, "");
    }

    private int readInt(Entity entity, NamespacedKey key, int fallback) {
        if (entity == null || key == null) {
            return fallback;
        }
        return entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }

    private boolean ownedBySession(Entity entity, String expectedEventId, long expectedGeneration) {
        if (!ownedByEvent(entity, expectedEventId)) {
            return false;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        long legacyGeneration = data.getOrDefault(keyGeneration, PersistentDataType.LONG, Long.MIN_VALUE);
        long taggedGeneration = data.getOrDefault(keyEventGeneration, PersistentDataType.LONG, legacyGeneration);
        return expectedGeneration == taggedGeneration;
    }

    private boolean ownedByEvent(Entity entity, String expectedEventId) {
        if (entity == null || expectedEventId == null || expectedEventId.isBlank()) {
            return false;
        }
        String legacySession = readString(entity, keyEventId);
        String session = readString(entity, keyEventSessionId);
        return Objects.equals(expectedEventId, session.isBlank() ? legacySession : session);
    }

    private void cleanupOwnedEntitiesForEvent(String expectedEventId) {
        if (expectedEventId == null || expectedEventId.isBlank()) {
            return;
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                // A previous server process may have persisted a different
                // session id on the mob. Core removal is the hard boundary,
                // so every entity carrying one of this plugin's event roles
                // must disappear, independent of its old generation/session.
                if (ownedByEvent(entity, expectedEventId) || isEndEventOwnedRole(entity)) {
                    entity.remove();
                    ownedEntities.remove(entity.getUniqueId());
                    finalWaveEntities.remove(entity.getUniqueId());
                    spellServants.remove(entity.getUniqueId());
                    removed++;
                }
            }
        }
        if (expectedEventId.equals(eventId)) {
            ownedEntities.clear();
            finalWaveEntities.clear();
            spellServants.clear();
        }
        getLogger().info("END_EVENT_OWNED_CLEANUP event=" + expectedEventId + " generations=all removed=" + removed);
    }

    private boolean isEndEventOwnedRole(Entity entity) {
        String kind = readString(entity, keyKind);
        return EVENT_KIND_CORE.equals(kind) || EVENT_KIND_PAD.equals(kind)
                || EVENT_KIND_DISPLAY.equals(kind) || EVENT_KIND_WAVE_MOB.equals(kind)
                || EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_BOSS.equals(kind)
                || EVENT_KIND_FINAL_WAVE.equals(kind) || EVENT_KIND_PROJECTILE.equals(kind);
    }

    private void cleanupOwnedEntities(String expectedEventId, long expectedGeneration) {
        if (expectedEventId == null || expectedEventId.isBlank()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (ownedBySession(entity, expectedEventId, expectedGeneration)) {
                    entity.remove();
                    ownedEntities.remove(entity.getUniqueId());
                    finalWaveEntities.remove(entity.getUniqueId());
                    spellServants.remove(entity.getUniqueId());
                }
            }
        }
        if (expectedEventId.equals(eventId) && expectedGeneration == generation) {
            ownedEntities.clear();
            finalWaveEntities.clear();
            spellServants.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnedEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && ownedEntities.containsKey(entity.getUniqueId())) {
            unbindEventEntityClient(entity.getUniqueId());
            ownedEntities.remove(entity.getUniqueId());
            finalWaveEntities.remove(entity.getUniqueId());
            spellServants.remove(entity.getUniqueId());
            miniBossSpells.remove(entity.getUniqueId());
            nextMiniBossSpellMillis.remove(entity.getUniqueId());
            if (bossUuid != null && bossUuid.equals(entity.getUniqueId()) && !officialBossDeathCommitted) {
                bossUuid = null;
                bossKillerUuid = null;
                clearClientEffects();
            }
        }
    }

    private void bindBossClientForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isCombatTarget(player)) {
                bindBossClient(player);
            }
        }
    }

    private void bindEventEntitiesClient(Player player) {
        if (player == null || !player.isOnline() || !isCombatTarget(player)) {
            return;
        }
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            bindEventEntityClient(player, entity);
        }
    }

    private void bindEventEntityClientForOnlinePlayers(Entity entity) {
        if (clientVisualId(entity).isBlank()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isCombatTarget(player)) {
                bindEventEntityClient(player, entity);
            }
        }
    }

    private void bindEventEntityClient(Player player, Entity entity) {
        if (player == null || entity == null || !player.isOnline()) {
            return;
        }
        String visualId = clientVisualId(entity);
        if (visualId.isBlank()) {
            return;
        }
        String instance = entityBindingInstances.computeIfAbsent(entity.getUniqueId(), uuid ->
                eventId + ":" + generation + ":" + uuid);
        sendClientPacket(player, "END_ENTITY_BIND", instance, 0L,
                entity.getUniqueId().toString(), visualId);
    }

    private void unbindEventEntityClient(UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        String instance = entityBindingInstances.remove(entityUuid);
        if (instance == null || instance.isBlank()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendClientPacket(player, "END_ENTITY_UNBIND", instance, 0L, entityUuid.toString(), "");
        }
    }

    private String clientVisualId(Entity entity) {
        if (entity == null || !isVisualEventMob(entity)) {
            return "";
        }
        String kind = readString(entity, keyKind);
        if (entity.getType() == EntityType.SPIDER) {
            return CLIENT_VISUAL_SPIDER;
        }
        if (entity.getType() == EntityType.SHULKER) {
            return CLIENT_VISUAL_SHULKER;
        }
        if (entity.getType() == EntityType.ENDERMAN) {
            if (EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_FINAL_WAVE.equals(kind)) {
                return CLIENT_VISUAL_ELITE;
            }
            if (EVENT_KIND_WAVE_MOB.equals(kind)) {
                return CLIENT_VISUAL_ENDERMAN;
            }
        }
        return "";
    }

    private boolean isVisualEventMob(Entity entity) {
        if (entity == null || entity.getType() == EntityType.ARMOR_STAND
                || EVENT_KIND_BOSS.equals(readString(entity, keyKind))) {
            return false;
        }
        String kind = readString(entity, keyKind);
        return EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind)
                || EVENT_KIND_FINAL_WAVE.equals(kind);
    }

    private void bindBossClient(Player player) {
        if (player == null || !player.isOnline() || bossUuid == null || !isCombatTarget(player)) {
            return;
        }
        if (bossBindingInstanceId.isBlank()) {
            bossBindingInstanceId = eventId + ":" + generation + ":" + bossUuid;
        }
        sendClientPacket(player, "END_BOSS_BIND", bossBindingInstanceId, 0L,
                bossUuid.toString(), config.clientBossId());
    }

    private void sendControlStart(Player player) {
        if (player == null || !player.isOnline() || !controlSpellUnlocked || controlInstances.containsKey(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (controlCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        String instance = UUID.randomUUID().toString();
        controlInstances.put(player.getUniqueId(), instance);
        controlEnds.put(player.getUniqueId(), now + config.controlDurationSeconds() * 1000L);
        controlCooldowns.put(player.getUniqueId(), now + config.controlCooldownSeconds() * 1000L);
        sendClientPacket(player, "END_CONTROL_START", instance,
                config.controlDurationSeconds() * 1000L, "");
    }

    private void startFairControlTarget() {
        if (!controlSpellUnlocked || !controlInstances.isEmpty()) {
            return;
        }
        List<Player> candidates = activeLivingPlayers().stream()
                .filter(player -> controlCooldowns.getOrDefault(player.getUniqueId(), 0L) <= System.currentTimeMillis())
                .toList();
        if (!candidates.isEmpty()) {
            sendControlStart(candidates.get(random.nextInt(candidates.size())));
        }
    }

    private void expireControlEffects() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new HashSet<>(controlInstances.keySet())) {
            if (controlEnds.getOrDefault(uuid, 0L) <= now) {
                stopControl(uuid);
            }
        }
    }

    private void stopControl(UUID uuid) {
        String instance = controlInstances.remove(uuid);
        controlEnds.remove(uuid);
        if (instance != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendControlStop(player, instance);
            }
        }
    }

    private void sendControlStop(Player player, String instance) {
        if (player != null && instance != null) {
            sendClientPacket(player, "END_CONTROL_STOP", instance, 0L, "");
        }
    }

    private void sendClientPacket(Player player, String type, String instanceId, long durationMillis, String subjectId) {
        sendClientPacket(player, type, instanceId, durationMillis, subjectId, config.clientBossId());
    }

    private void sendClientPacket(Player player, String type, String instanceId, long durationMillis,
                                  String subjectId, String visualId) {
        if (player == null || !player.isOnline() || config.bridgeChannel().isBlank()) {
            return;
        }
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream output = new java.io.DataOutputStream(bytes);
            // Keep End Rift messages inside the normal bridge-v2 envelope.
            // Older CopiMineClient builds can decode this shape and ignore the
            // END_EVENT:* type, while newer builds route it to EndEventClientState.
            // A private magic envelope made older clients fail the whole
            // clientbound custom_payload packet instead of ignoring the event.
            output.writeUTF("END_EVENT:" + type);
            output.writeInt(2);
            output.writeLong(generation);
            output.writeLong(System.currentTimeMillis());
            output.writeUTF(eventId);
            output.writeUTF(instanceId == null ? "" : instanceId);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeInt(0);
            output.writeUTF("");
            output.writeUTF(visualId == null ? "" : visualId);
            output.writeInt((int) Math.max(0L, Math.min(Integer.MAX_VALUE, durationMillis)));
            output.writeFloat(0.0F);
            output.writeInt(0);
            output.writeInt(0);
            output.writeUTF(subjectId == null ? "" : subjectId);
            output.writeUTF(visualId == null ? "" : visualId);
            output.writeUTF(config.clientControlId());
            output.writeUTF("");
            output.writeUTF("");
            output.flush();
            player.sendPluginMessage(this, config.bridgeChannel(), bytes.toByteArray());
        } catch (java.io.IOException error) {
            getLogger().log(Level.FINE, "End client bridge packet could not be encoded", error);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!"cmend".equalsIgnoreCase(command.getName())) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("status", "debug", "recovery", "core", "arena", "gate", "portalroom", "resources", "ritual", "wave", "boss", "client", "test", "cleanup", "reset", "unlock").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "core" -> List.of("set", "info", "rebuild", "remove");
                case "arena" -> List.of("pos1", "pos2", "info", "clear", "border", "boundary");
            case "gate" -> List.of("pos1", "pos2", "info", "preview", "open", "restore");
                case "portalroom" -> List.of("set", "info");
                case "resources" -> List.of("status", "add", "reset");
                case "ritual" -> List.of("start", "cancel", "cleanup", "reset", "unlock");
                case "wave" -> List.of("spawn", "clear");
                case "boss" -> List.of("spawn", "official", "info", "damage", "phase", "kill", "spell");
                case "client" -> List.of("status", "bindboss", "clear");
                case "test" -> List.of("run", "wave", "boss", "music");
                default -> List.of();
            };
        }
        if (args.length == 3 && "wave".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])) {
            return List.of("1", "2", "3", "final");
        }
        if (args.length == 3 && "test".equalsIgnoreCase(args[0]) && "wave".equalsIgnoreCase(args[1])) {
            return List.of("1", "2", "3", "final");
        }
        if (args.length == 3 && "test".equalsIgnoreCase(args[0]) && "run".equalsIgnoreCase(args[1])) {
            return List.of("creative");
        }
        if (args.length == 4 && "test".equalsIgnoreCase(args[0]) && "run".equalsIgnoreCase(args[1])
                && "creative".equalsIgnoreCase(args[2])) {
            return List.of("cancel");
        }
        if (args.length == 3 && "test".equalsIgnoreCase(args[0]) && "music".equalsIgnoreCase(args[1])) {
            return List.of("waves", "boss", "half", "final", "victory");
        }
        if (args.length == 3 && "gate".equalsIgnoreCase(args[0]) && "restore".equalsIgnoreCase(args[1])) {
            return List.of("confirm");
        }
        if (args.length == 4 && "test".equalsIgnoreCase(args[0]) && "music".equalsIgnoreCase(args[1])) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[3].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 3 && ("cleanup".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0])
                || "unlock".equalsIgnoreCase(args[0]))) {
            return List.of("confirm");
        }
        if (args.length == 3 && "resources".equalsIgnoreCase(args[0]) && "reset".equalsIgnoreCase(args[1])) {
            return List.of("confirm");
        }
        if (args.length == 3 && "ritual".equalsIgnoreCase(args[0])
                && List.of("cancel", "cleanup", "reset", "unlock").contains(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("confirm");
        }
        if (args.length == 3 && "boss".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])) {
            return List.of("official");
        }
        if (args.length == 4 && "boss".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])
                && "official".equalsIgnoreCase(args[2])) {
            return List.of("confirm");
        }
        if (args.length == 3 && "boss".equalsIgnoreCase(args[0]) && "phase".equalsIgnoreCase(args[1])) {
            return List.of("normal", "half", "final");
        }
        if (args.length == 3 && "boss".equalsIgnoreCase(args[0]) && "kill".equalsIgnoreCase(args[1])) {
            return List.of("cleanup", "simulate-victory");
        }
        if (args.length == 4 && "boss".equalsIgnoreCase(args[0]) && "kill".equalsIgnoreCase(args[1])
                && "simulate-victory".equalsIgnoreCase(args[2])) {
            return List.of("confirm");
        }
        if (args.length == 3 && "boss".equalsIgnoreCase(args[0]) && "spell".equalsIgnoreCase(args[1])) {
            return List.of("void_blast", "rift_projectile", "void_mark", "summon", "control_reverse");
        }
        return List.of();
    }

    private static final class CoreRemovalConfirmHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private final String eventId;
        private final long generation;
        private Inventory inventory;

        private CoreRemovalConfirmHolder(UUID ownerUuid, String eventId, long generation) {
            this.ownerUuid = ownerUuid;
            this.eventId = eventId;
            this.generation = generation;
        }

        private UUID ownerUuid() {
            return ownerUuid;
        }

        private String eventId() {
            return eventId;
        }

        private long generation() {
            return generation;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
