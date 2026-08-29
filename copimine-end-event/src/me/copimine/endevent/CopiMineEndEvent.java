package me.copimine.endevent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
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
import me.copimine.endevent.domain.BossCastState;
import me.copimine.endevent.domain.BossCastPolicy;
import me.copimine.endevent.domain.BossDamagePolicy;
import me.copimine.endevent.domain.BossMovementPolicy;
import me.copimine.endevent.domain.BossStage;
import me.copimine.endevent.domain.BossStagePolicy;
import me.copimine.endevent.domain.BossStatsPolicy;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;
import me.copimine.endevent.domain.CombatMovementPolicy;
import me.copimine.endevent.domain.CombatTacticsPolicy;
import me.copimine.endevent.domain.CoreDepositMath;
import me.copimine.endevent.domain.CoreInteractionGuard;
import me.copimine.endevent.domain.EndEventStateMachine;
import me.copimine.endevent.domain.EndRiftAiPolicy;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.FinalDrainMath;
import me.copimine.endevent.domain.GateOpeningPlan;
import me.copimine.endevent.domain.HazardPlanner;
import me.copimine.endevent.domain.PadLayout;
import me.copimine.endevent.domain.PortalCapturePolicy;
import me.copimine.endevent.domain.RewardRoster;
import me.copimine.endevent.domain.ResourceProgressFormatter;
import me.copimine.endevent.domain.StormPatternPolicy;
import me.copimine.endevent.domain.SkeletonCombatPolicy;
import me.copimine.endevent.domain.SpellVisualPolicy;
import me.copimine.endevent.domain.TowerDefensePolicy;
import me.copimine.endevent.domain.WaveMechanicsPolicy;
import me.copimine.endevent.domain.WaveObjectivePolicy;
import me.copimine.endevent.domain.WaveRewardPolicy;
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
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
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
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
import org.bukkit.plugin.Plugin;
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
    private static final String EVENT_KIND_WAVE_REWARD = "WAVE_REWARD";
    private static final String EVENT_KIND_TOWER_PROXY = "TOWER_PROXY";
    private static final String CLIENT_VISUAL_ENDERMAN = "END_RIFT_ENDERMAN_V1";
    private static final String CLIENT_VISUAL_ELITE = "END_RIFT_ELITE_V1";
    private static final String CLIENT_VISUAL_SPIDER = "END_RIFT_SPIDER_V1";
    private static final String CLIENT_VISUAL_SKELETON = "END_RIFT_SKELETON_V1";
    private static final String CLIENT_VISUAL_ELITE_SKELETON = "END_RIFT_ELITE_SKELETON_V1";
    private static final Material EVENT_OVERLAY_ITEM = Material.PAPER;
    private static final int MODEL_CORE_OVERLAY = 830001;
    private static final int MODEL_CORE_CHARGED_OVERLAY = 830002;
    private static final int MODEL_RUNE_OVERLAY = 830003;
    private static final int MODEL_RUNE_OVERLAY_OCCUPIED = 830005;
    private static final double MAX_COMBAT_RADIUS_BLOCKS = 20.0D;
    private static final double MIN_BOSS_CORE_DISTANCE_BLOCKS = 3.5D;
    private static final double MIN_WAVE_CORE_DISTANCE_BLOCKS = 2.5D;
    private static final double MAX_COMBAT_STEP_BLOCKS = CombatMovementPolicy.MAX_COMBAT_STEP_BLOCKS;
    private static final int DEFAULT_ARENA_PREVIEW_SECONDS = 10;
    private static final int MAX_ARENA_PREVIEW_SECONDS = 300;
    private static final double ARENA_BOUNDARY_STEP = 0.5D;
    private static final long MAX_GATE_VOLUME = 16_384L;
    private static final int DEFAULT_GATE_TICKS_PER_LAYER = 5;
    private static final int MIN_GATE_TICKS_PER_LAYER = 1;
    private static final int MAX_GATE_TICKS_PER_LAYER = 200;
    private static final int DEFAULT_GATE_SELECTION_PREVIEW_SECONDS = 10;
    private static final int VOID_MARK_RADIUS_BLOCKS = 3;
    private static final int VOID_MARK_DURATION_SECONDS = 10;
    // Bukkit/Paper rejects a LivingEntity health value above 2048.  The event
    // still exposes the requested 2500 HP as an authoritative virtual pool;
    // the physical value is only a safe projection used by the server entity.
    private static final double BOSS_PHYSICAL_HEALTH_LIMIT = 2048.0D;
    private static final int DEBUFF_DURATION_MULTIPLIER = 3;
    private static final int BASE_BOSS_DEBUFF_TICKS = 80;
    private static final int BASE_VOID_MARK_DEBUFF_TICKS = 75;
    private static final int BASE_JUDGMENT_WITHER_DEBUFF_TICKS = 120;
    private static final int BASE_JUDGMENT_WEAKNESS_DEBUFF_TICKS = 200;
    private static final int BASE_ARENA_INFERNO_DEBUFF_TICKS = 160;
    private static final int BOSS_BLAST_DEBUFF_TICKS =
            BASE_BOSS_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int BOSS_PROJECTILE_DEBUFF_TICKS =
            BASE_BOSS_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int VOID_MARK_DEBUFF_TICKS =
            BASE_VOID_MARK_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int BOSS_JUDGMENT_WITHER_DEBUFF_TICKS =
            BASE_JUDGMENT_WITHER_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int BOSS_JUDGMENT_WEAKNESS_DEBUFF_TICKS =
            BASE_JUDGMENT_WEAKNESS_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int ARENA_INFERNO_DEBUFF_TICKS =
            BASE_ARENA_INFERNO_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final double BOSS_BLAST_DAMAGE = 12.0D;
    private static final double BOSS_PROJECTILE_DAMAGE = 12.0D;
    private static final double VOID_MARK_DAMAGE = 4.0D;
    private static final int BASE_MINI_RIFT_STEP_DEBUFF_TICKS = 50;
    private static final int BASE_MINI_VOID_SNARE_DEBUFF_TICKS = 80;
    private static final int BASE_MINI_ECHO_PULSE_DEBUFF_TICKS = 60;
    private static final int MINI_RIFT_STEP_DEBUFF_TICKS =
            BASE_MINI_RIFT_STEP_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int MINI_VOID_SNARE_DEBUFF_TICKS =
            BASE_MINI_VOID_SNARE_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    private static final int MINI_ECHO_PULSE_DEBUFF_TICKS =
            BASE_MINI_ECHO_PULSE_DEBUFF_TICKS * DEBUFF_DURATION_MULTIPLIER;
    // Amplifier 255 is not a valid gameplay balance and can break client-side
    // effect handling.  Level IV is the strongest bounded value used here.
    private static final int MAX_POTION_AMPLIFIER = 3;
    private static final int ABSORPTION_CHANNEL_TICKS = 100;
    private static final int EXHAUSTED_WINDOW_TICKS = 120;
    private static final int BOSS_SPAWN_DELAY_TICKS = 200;
    private static final int BOSS_CINEMATIC_DURATION_TICKS = BOSS_SPAWN_DELAY_TICKS;
    private static final int ARENA_INFERNO_DURATION_TICKS = 100;
    private static final int WAVE_ONE_PULSE_TELEGRAPH_TICKS = 50;
    private static final double WAVE_ONE_PULSE_DAMAGE = 6.0D;
    private static final int SLOWNESS_DEBUFF_TICKS = 60;
    private static final long WAVE_PATH_REQUEST_INTERVAL_MILLIS = 500L;
    private static final long BOSS_TACTIC_REFRESH_MILLIS = 2_500L;
    private static final long COMBAT_TELEPORT_PERMIT_MILLIS = 1_000L;
    private static final int WAVE_TWO_INITIAL_MARK_MIN_SECONDS = 2;
    private static final int WAVE_TWO_INITIAL_MARK_MAX_SECONDS = 4;
    private static final int WAVE_TWO_MARK_REVEAL_TICKS = 30;
    private static final int WAVE_TWO_MARK_DURATION_TICKS = 220;
    private static final long WAVE_REWARD_OWNER_WINDOW_MILLIS = 30_000L;
    private static final int FINAL_WAVE_NUMBER = 6;
    private static final int MAX_ACTIVE_VOID_MARKS = 2;
    private static final int MAX_ACTIVE_RIFT_PROJECTILES = 8;
    private static final int MAX_ACTIVE_EVENT_ARROWS = 24;
    private static final int JUDGMENT_SAFE_ZONE_BLOCK_HEIGHT = 2;
    private static final int SPELL_FLIGHT_TICKS = 8;
    private static final int SPELL_FLIGHT_RENDER_INTERVAL_TICKS = 2;
    private static final String SPELL_FLIGHT_EFFECT = "spell-flight";
    private static final int RIFT_PROJECTILE_MAX_TICKS = 80;
    private static final double RIFT_PROJECTILE_SPEED = 0.65D;
    private static final int EVENT_ARROW_MAX_TICKS = 100;
    private static final int EVENT_ARROW_TRAIL_INTERVAL_TICKS = 2;
    private static final long RUNTIME_DIAGNOSTICS_WINDOW_MILLIS = 5_000L;
    private static final String PACKET_QUALITY_FULL = "FULL";
    private static final String PACKET_QUALITY_REDUCED = "REDUCED";
    private static final String PACKET_QUALITY_MINIMAL_SAFE = "MINIMAL_SAFE";
    private static final String ARROW_SPELL_SKELETON = "skeleton";
    private static final long TOWER_PLAYER_AGGRO_MILLIS = 4_000L;
    private static final double TOWER_PLAYER_ALERT_RADIUS = 8.0D;
    // Small deterministic steering beats keep movement readable without
    // creating another repeating task per mob.
    private static final long SKELETON_MANEUVER_CYCLE_MILLIS = 4_000L;
    private static final long BOSS_FEINT_COOLDOWN_MILLIS = 8_000L;
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
    // This map drives only the visible rune state.  It deliberately remains
    // separate from the official survival-only roster so a local OP can see
    // the occupied texture while testing in Creative without starting the
    // real ritual or changing reward eligibility.
    private final Map<String, UUID> runeVisualOccupants = new LinkedHashMap<>();
    private final Map<UUID, String> playerCategories = new HashMap<>();
    private final Set<UUID> combatHelpers = new LinkedHashSet<>();
    private final Set<UUID> finalWaveEntities = new HashSet<>();
    private final Set<Block> arenaInfernoBlocks = new LinkedHashSet<>();
    private final Set<UUID> lootIssuedEntityUuids = new HashSet<>();
    private final Set<UUID> spellServants = new HashSet<>();
    private final Set<UUID> judgmentVisuals = new LinkedHashSet<>();
    private final Map<UUID, CombatTacticsPolicy.MobTactic> waveMobTactics = new HashMap<>();
    private final Map<UUID, BossTeleportPermitPolicy.Permit> combatTeleportPermits = new HashMap<>();
    private final Map<UUID, Long> blockedTeleportLogAt = new HashMap<>();
    private final Set<UUID> waveObjectiveVisuals = new LinkedHashSet<>();
    private final Map<UUID, String> waveObjectiveVisualTexts = new HashMap<>();
    private final Map<HazardPlanner.Point, String> arenaInfernoOriginalBlocks = new LinkedHashMap<>();
    private final Map<Integer, List<Location>> wavePortals = new LinkedHashMap<>();
    private final Map<Integer, List<PortalCapturePolicy.PortalState>> portalCaptureStates = new LinkedHashMap<>();
    private final Set<HazardPlanner.Point> riftStormHazards = new LinkedHashSet<>();
    private final Set<HazardPlanner.Point> riftStormSafeCells = new LinkedHashSet<>();
    private final Map<HazardPlanner.Point, String> riftStormOriginalBlocks = new LinkedHashMap<>();
    private final Map<HazardPlanner.Point, String> riftStormOriginalWebBlocks = new LinkedHashMap<>();
    private final Map<UUID, Integer> riftStormLastDamageSecond = new HashMap<>();
    private final Map<UUID, Long> towerNextAttackAt = new HashMap<>();
    private final Map<UUID, Integer> towerAttackSequences = new HashMap<>();
    private final Map<UUID, Long> towerAggroUntil = new HashMap<>();
    private TowerDefensePolicy.CoreState towerDefenseState;
    /** Failed Tower Defense state held until the owned retry task respawns the wave. */
    private TowerDefensePolicy.CoreState pendingTowerDefenseRetry;
    private long waveObjectiveStartedMillis;
    private boolean waveObjectiveComplete;
    private int waveObjectiveLastSecond = -1;
    private int waveObjectiveMobCount;
    private final Map<UUID, Long> objectiveActionBarAt = new HashMap<>();
    private long waveOneNextPulseMillis;
    private long waveOnePulseDeadlineMillis;
    private int waveOnePulseIndex;
    private UUID waveTwoMarkedPlayerUuid;
    private long waveTwoNextMarkMillis;
    private long waveTwoMarkRevealDeadlineMillis;
    private long waveTwoMarkDeadlineMillis;
    private StormPatternPolicy.Pattern lastStormPattern;
    private int stormPatternPhase;
    private long nextRiftStormPullMillis;
    private UUID towerDefenseVisualUuid;
    private int towerAttackSequence;
    private final Map<UUID, String> entityBindingInstances = new HashMap<>();
    private final Map<UUID, EndRiftAiPolicy.MiniBossSpell> miniBossSpells = new HashMap<>();
    private final Map<UUID, Long> nextMiniBossSpellMillis = new HashMap<>();
    private final Map<UUID, Long> nextWavePathRequestMillis = new HashMap<>();
    private final Map<UUID, Long> lastWavePathLogMillis = new HashMap<>();
    private final Map<UUID, BukkitTask> activeVoidMarkTasks = new LinkedHashMap<>();
    private final Map<UUID, Location> activeVoidMarkCenters = new LinkedHashMap<>();
    private final Set<UUID> activeRiftProjectiles = new HashSet<>();
    private final Map<UUID, BukkitTask> riftProjectileTasks = new HashMap<>();
    private final Map<UUID, Integer> activeEventArrowAges = new LinkedHashMap<>();
    private final Map<UUID, Long> nextSkeletonArrowMillis = new HashMap<>();
    private final Deque<UUID> recentBossTargets = new ArrayDeque<>();
    private long runtimeDiagnosticsWindowStartedAtMillis;
    private long runtimeDiagnosticsLastSampleMillis;
    private long runtimeDiagnosticsParticlePackets;
    private long runtimeDiagnosticsParticleBatches;
    private long runtimeDiagnosticsPluginMessages;
    private long runtimeDiagnosticsLastGcCollections = -1L;
    private long runtimeDiagnosticsLastGcPauseMillis = -1L;
    private long runtimeDiagnosticsLastThreadCpuNanos = -1L;
    private long runtimeDiagnosticsLastThreadCpuWallMillis = -1L;
    private RuntimeDiagnosticsSnapshot runtimeDiagnosticsSnapshot = RuntimeDiagnosticsSnapshot.empty();
    /** PlayerJoinEvent can run before AuthMe finishes; bindings are retried after eligibility changes. */
    private final Set<UUID> clientBindingReadyPlayers = new HashSet<>();
    private final CoreInteractionGuard coreInteractionGuard = new CoreInteractionGuard();

    private EventConfig config;
    private EventStateStore stateStore;
    private EventLayoutStore layoutStore;
    private HazardMutationJournal hazardJournal;
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
    private BukkitTask bossSpawnTask;
    private BukkitTask arenaInfernoTask;
    private BukkitTask bossCastTask;
    private BukkitTask towerRetryTask;
    private BukkitTask towerSpawnTask;
    private List<WaveMechanicsPolicy.WaveCounts> towerSpawnSchedule = List.of();
    private int towerSpawnGroupIndex;
    private int towerSpawnEntityOffset;
    private long towerNextSpawnAtMillis;
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
    private final Set<Integer> waveRewardsIssued = new LinkedHashSet<>();
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
    private int lastCountdownAnnouncement = -1;
    private int lastCountdownTitleSecond = -1;
    private long nextRitualVisualRepairMillis;
    private int bossTargetCursor;
    private int bossSpellCursor;
    private int waveTargetCursor;
    private UUID bossProgressEntity;
    private Location bossLastProgressLocation;
    private long bossLastProgressAt;
    private long nextBossStuckTeleportMillis;
    private long lastBossPathRequestMillis;
    private long lastBossFeintMillis;
    private int bossTacticCycle;
    private long nextBossTacticMillis;
    private long nextClientBindingRefreshMillis;
    private EndRiftAiPolicy.BossSpell previousBossSpell;
    private BossStage bossStage = BossStage.AWAKENING;
    private BossCastState bossCastState = BossCastState.NONE;
    private long bossCastDeadlineMillis;
    private boolean absorptionTriggered;
    private boolean absorptionCompleted;
    private boolean absorptionAttackEmpowered;
    private boolean judgmentTriggered;
    private boolean judgmentCompleted;
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
    private double bossVirtualHealthValue;
    private String bossBindingInstanceId = "";
    private String activeMusicTrackId = "";
    private long bossBarLastUpdateMillis;
    private double bossBarLastProgress = -1.0D;
    private String bossBarLastTitle = "";
    private BarColor bossBarLastColor;
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
    private NamespacedKey keyBossVirtualHealth;
    private NamespacedKey keyMiniBossSpell;
    private NamespacedKey keyRewardOwner;
    private NamespacedKey keyRewardExpiresAt;
    private NamespacedKey keyRewardShared;
    private NamespacedKey keyRewardAmount;
    private NamespacedKey keyArtifactItemId;
    private NamespacedKey keyArtifactUniqueId;
    private NamespacedKey keyTowerRole;
    private NamespacedKey keyTowerAttackAt;
    private NamespacedKey keyTowerAttackSequence;
    private NamespacedKey keyCombatTactic;
    private NamespacedKey keyArrowSpell;

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
            hazardJournal = new HazardMutationJournal(getDataFolder().toPath());
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
            keyBossVirtualHealth = new NamespacedKey(this, "end_event_boss_virtual_health");
            keyMiniBossSpell = new NamespacedKey(this, "end_event_miniboss_spell");
            keyRewardOwner = new NamespacedKey(this, "end_event_reward_owner");
            keyRewardExpiresAt = new NamespacedKey(this, "end_event_reward_expires_at");
            keyRewardShared = new NamespacedKey(this, "end_event_reward_shared");
            keyRewardAmount = new NamespacedKey(this, "end_event_reward_amount");
            keyTowerRole = new NamespacedKey(this, "end_event_tower_role");
            keyTowerAttackAt = new NamespacedKey(this, "end_event_tower_attack_at");
            keyTowerAttackSequence = new NamespacedKey(this, "end_event_tower_attack_sequence");
            keyCombatTactic = new NamespacedKey(this, "end_event_combat_tactic");
            keyArrowSpell = new NamespacedKey(this, "end_event_arrow_spell");
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
        Plugin worldCorePlugin = Bukkit.getPluginManager().getPlugin("CopiMineWorldCore");
        Plugin artifactsPlugin = Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
        if (worldCorePlugin == null || !worldCorePlugin.isEnabled()
                || artifactsPlugin == null || !artifactsPlugin.isEnabled()) {
            getLogger().severe("End Rift dependencies are unavailable; disabling to prevent partial startup.");
            if (bootstrapTask != null) {
                bootstrapTask.cancel();
                bootstrapTask = null;
            }
            getServer().getPluginManager().disablePlugin(this);
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
        recoverHazardJournal();
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
        restorePersistedCombatRuntime();
        recoverUnresolvedDeposits();
        resumeVictorySaga();
        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 5L);
        playEventMusic(musicForPhase());
        getLogger().info("CopiMineEndEvent services ready; phase=" + phase + " event=" + eventId);
    }

    /**
     * Rebuild the transient combat indexes after a clean server restart.
     * Bukkit entities and their PDC survive the restart, while Java maps and
     * scheduler tasks do not.  Without this boundary an active phase could be
     * durable but its mobs, boss UUID, objective and timers would disappear
     * from the controller.  The scan happens once during bootstrap and is
     * restricted to the configured event world; no full-world scan is used by
     * the five-tick combat loop.
     */
    private void restorePersistedCombatRuntime() {
        if (!bootstrapped || !isConfigured()) {
            return;
        }
        reindexPersistedCombatEntities();
        long now = System.currentTimeMillis();
        if (phase == EventPhase.COUNTDOWN && phaseDeadlineMillis <= 0L) {
            phaseDeadlineMillis = now + config.countdownSeconds() * 1000L;
            saveStateAsync();
            getLogger().warning("END_EVENT_TIMER_REPAIRED event=" + eventId
                    + " phase=COUNTDOWN reason=legacy-snapshot-missing-deadline");
        }
        int resumedWave = waveForPhase(phase);
        if (resumedWave > 0) {
            activeWave = resumedWave;
            getLogger().info("END_EVENT_COMBAT_REHYDRATED event=" + eventId
                    + " phase=" + phase + " wave=" + activeWave
                    + " entities=" + ownedEntities.size());
        } else {
            int intermissionWave = completedWaveForIntermission(phase);
            if (intermissionWave > 0) {
                activeWave = intermissionWave;
                if (phaseDeadlineMillis <= 0L) {
                    phaseDeadlineMillis = now + config.intermissionSeconds() * 1000L;
                    saveStateAsync();
                    getLogger().warning("END_EVENT_TIMER_REPAIRED event=" + eventId
                            + " phase=" + phase + " reason=legacy-snapshot-missing-deadline");
                }
                getLogger().info("END_EVENT_INTERMISSION_REHYDRATED event=" + eventId
                        + " phase=" + phase + " completed_wave=" + intermissionWave
                        + " deadline=" + phaseDeadlineMillis);
            }
        }
        if (phase == EventPhase.BOSS_CINEMATIC) {
            if (phaseDeadlineMillis <= 0L) {
                phaseDeadlineMillis = now + BOSS_CINEMATIC_DURATION_TICKS * 50L;
                saveStateAsync();
                getLogger().warning("END_EVENT_TIMER_REPAIRED event=" + eventId
                        + " phase=BOSS_CINEMATIC reason=legacy-snapshot-missing-deadline");
            }
            if (bossSpawnTask == null) {
                scheduleOfficialBossSpawn();
            }
            return;
        }
        if (!isPersistedBossPhase(phase)) {
            return;
        }
        LivingEntity boss = liveBoss();
        if (boss == null) {
            forcePhase(EventPhase.RECOVERY_REQUIRED, "official boss missing after restart");
            getLogger().severe("BOSS_REHYDRATION_FAILED event=" + eventId
                    + " phase=" + phase + " reason=official-boss-not-found");
            return;
        }
        bossVirtualHealth(boss);
        ensureBossBar();
        bindBossClientForOnlinePlayers();
        getLogger().info("BOSS_REHYDRATED event=" + eventId + " boss=" + boss.getUniqueId()
                + " phase=" + phase + " stage=" + bossStage
                + " cast=" + bossCastState);
    }

    /** Reattach PDC-owned entities to the in-memory controller exactly once. */
    private void reindexPersistedCombatEntities() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || eventId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        int restored = 0;
        int staleProjectiles = 0;
        int duplicateBosses = 0;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (!isEndEventOwnedRole(entity)
                    || !ownedBySession(entity, eventId, generation)) {
                continue;
            }
            String kind = readString(entity, keyKind);
            if (EVENT_KIND_PROJECTILE.equals(kind)) {
                // Projectile scheduler tasks cannot survive a process stop;
                // remove their persisted visual shell instead of leaving an
                // untracked projectile with no expiry watchdog.
                entity.remove();
                staleProjectiles++;
                continue;
            }
            if (EVENT_KIND_BOSS.equals(kind) && isTestBoss(entity)) {
                entity.remove();
                continue;
            }
            ownedEntities.put(entity.getUniqueId(), entity);
            restored++;
            if (EVENT_KIND_BOSS.equals(kind) && entity instanceof LivingEntity living
                    && isOfficialEntity(entity) && !living.isDead() && living.isValid()) {
                if (bossUuid == null) {
                    bossUuid = entity.getUniqueId();
                } else if (!bossUuid.equals(entity.getUniqueId())) {
                    entity.remove();
                    ownedEntities.remove(entity.getUniqueId());
                    duplicateBosses++;
                    continue;
                }
            }
            int wave = readInt(entity, keyWave, 0);
            if (EVENT_KIND_FINAL_WAVE.equals(kind) && isOfficialEntity(entity)) {
                finalWaveEntities.add(entity.getUniqueId());
            }
            if ((EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_FINAL_WAVE.equals(kind))
                    && keyMiniBossSpell != null) {
                EndRiftAiPolicy.MiniBossSpell spell = parseMiniBossSpell(entity
                        .getPersistentDataContainer().getOrDefault(
                                keyMiniBossSpell, PersistentDataType.STRING, ""));
                if (spell != null) {
                    miniBossSpells.put(entity.getUniqueId(), spell);
                    nextMiniBossSpellMillis.put(entity.getUniqueId(), now + 3_000L);
                }
            }
            if (wave == 4 && isWaveCombatKind(kind)) {
                long nextAttack = entity.getPersistentDataContainer().getOrDefault(
                        keyTowerAttackAt, PersistentDataType.LONG, now + 1_000L);
                int attackSequence = entity.getPersistentDataContainer().getOrDefault(
                        keyTowerAttackSequence, PersistentDataType.INTEGER, 0);
                towerNextAttackAt.put(entity.getUniqueId(), Math.max(now, nextAttack));
                towerAttackSequences.put(entity.getUniqueId(), Math.max(0, attackSequence));
                combatTactic(entity, entity.getUniqueId().hashCode());
            }
        }
        if (staleProjectiles > 0 || duplicateBosses > 0) {
            getLogger().warning("END_EVENT_COMBAT_REINDEX_CLEANUP event=" + eventId
                    + " stale_projectiles=" + staleProjectiles
                    + " duplicate_bosses=" + duplicateBosses);
        }
        if (restored > 0) {
            getLogger().info("END_EVENT_COMBAT_REINDEX event=" + eventId
                    + " generation=" + generation + " restored=" + restored);
        }
    }

    private EndRiftAiPolicy.MiniBossSpell parseMiniBossSpell(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (EndRiftAiPolicy.MiniBossSpell spell : EndRiftAiPolicy.MiniBossSpell.values()) {
            if (spell.id().equalsIgnoreCase(value) || spell.name().equalsIgnoreCase(value)) {
                return spell;
            }
        }
        return null;
    }

    private int waveForPhase(EventPhase current) {
        return switch (current) {
            case WAVE_1 -> 1;
            case WAVE_2 -> 2;
            case WAVE_3 -> 3;
            case WAVE_4 -> 4;
            case WAVE_5 -> 5;
            default -> 0;
        };
    }

    private int completedWaveForIntermission(EventPhase current) {
        return switch (current) {
            case INTERMISSION_1 -> 1;
            case INTERMISSION_2 -> 2;
            case INTERMISSION_3 -> 3;
            case INTERMISSION_4 -> 4;
            default -> 0;
        };
    }

    private boolean isPersistedBossPhase(EventPhase current) {
        return current == EventPhase.BOSS_ACTIVE || current == EventPhase.FINAL_DRAIN
                || current == EventPhase.FINAL_RITUAL || current == EventPhase.FINAL_WAVE
                || current == EventPhase.BOSS_FINISH;
    }

    /** Restore a temporary Wave V mutation before any resumed gameplay tick. */
    private void recoverHazardJournal() {
        if (hazardJournal == null) {
            return;
        }
        HazardMutationJournal.Snapshot journal = hazardJournal.load();
        if (!journal.valid() || journal.status() == HazardMutationJournal.Status.EMPTY
                || journal.status() == HazardMutationJournal.Status.RESTORED
                || journal.entries().isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(journal.world());
        if (world == null) {
            getLogger().severe("WAVE_HAZARD_RECOVERY_BLOCKED event=" + journal.eventId()
                    + " reason=world-not-loaded journal=" + hazardJournal.path());
            return;
        }
        int restored = 0;
        int skipped = 0;
        for (HazardMutationJournal.Entry entry : journal.entries()) {
            if (entry.isFireMutation()) {
                Block fire = world.getBlockAt(entry.x(), entry.floorY(), entry.z());
                if (fire.getType() == Material.FIRE) {
                    restoreBlock(fire, entry.floorOriginal());
                    restored++;
                } else {
                    skipped++;
                }
                continue;
            }
            Block floor = world.getBlockAt(entry.x(), entry.floorY(), entry.z());
            if (floor.getType() == Material.MAGMA_BLOCK) {
                restoreBlock(floor, entry.floorOriginal());
                restored++;
            } else {
                skipped++;
            }
            if (entry.hasWebMutation()) {
                Block web = world.getBlockAt(entry.x(), entry.floorY() + 1, entry.z());
                if (web.getType() == Material.COBWEB) {
                    restoreBlock(web, entry.webOriginal());
                    restored++;
                } else {
                    skipped++;
                }
            }
        }
        if (hazardJournal.markRestored()) {
            getLogger().warning("WAVE_HAZARD_RECOVERED event=" + journal.eventId()
                    + " generation=" + journal.generation() + " status=" + journal.status()
                    + " restored=" + restored + " skipped=" + skipped);
        } else {
            getLogger().severe("WAVE_HAZARD_RECOVERY_UNCOMMITTED event=" + journal.eventId()
                    + " restored=" + restored + " skipped=" + skipped);
        }
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
        waveRewardsIssued.clear();
        waveRewardsIssued.addAll(snapshot.waveRewardsIssued());
        bossStage = parseBossStage(snapshot.bossStage());
        bossCastState = parseBossCastState(snapshot.bossCastState());
        bossCastDeadlineMillis = snapshot.bossCastDeadlineMillis();
        absorptionTriggered = snapshot.absorptionTriggered();
        absorptionCompleted = snapshot.absorptionCompleted();
        absorptionAttackEmpowered = snapshot.absorptionAttackEmpowered();
        judgmentTriggered = snapshot.judgmentTriggered();
        judgmentCompleted = snapshot.judgmentCompleted();
        if (bossCastState != BossCastState.NONE && bossCastDeadlineMillis <= System.currentTimeMillis()) {
            bossCastState = BossCastState.NONE;
            bossCastDeadlineMillis = 0L;
        }
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
        phaseDeadlineMillis = snapshot.phaseDeadlineMillis();
    }

    private BossStage parseBossStage(String value) {
        try {
            return BossStage.valueOf(value == null ? "AWAKENING" : value);
        } catch (IllegalArgumentException invalid) {
            getLogger().warning("Unknown persisted boss stage; using AWAKENING: " + value);
            return BossStage.AWAKENING;
        }
    }

    private BossCastState parseBossCastState(String value) {
        try {
            return BossCastState.valueOf(value == null ? "NONE" : value);
        } catch (IllegalArgumentException invalid) {
            getLogger().warning("Unknown persisted boss cast state; clearing it: " + value);
            return BossCastState.NONE;
        }
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
                bossRewardRecipientUuid, returnStoneStatus, victoryStep, updatedAt, phaseDeadlineMillis, recoveryReason,
                participantUuids, finalDrainTargets,
                finalDrainAppliedPlayers, waveRewardsIssued, bossStage.name(), bossCastState.name(),
                bossCastDeadlineMillis, absorptionTriggered, absorptionCompleted,
                absorptionAttackEmpowered, judgmentTriggered, judgmentCompleted);
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
        if (!isEventMusicPhase() && !isVictoryMusicTail(current, next)) {
            stopEventMusic();
        } else if (isEventMusicPhase()) {
            playEventMusic(musicForPhase());
        }
        if (persist) {
            saveStateAsync();
        }
        return true;
    }

    private void forcePhase(EventPhase next, String reason) {
        EventPhase previous = phase;
        phase = next;
        stateMachine = new EndEventStateMachine(next);
        if (next == EventPhase.UNLOCKED) {
            releaseOverlayChunkTickets();
        }
        if (!isEventMusicPhase() && !isVictoryMusicTail(previous, next)) {
            stopEventMusic();
        } else if (isEventMusicPhase()) {
            playEventMusic(musicForPhase());
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
        runeVisualOccupants.clear();
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
        cancelBossSpawnTask();
        clearArenaInferno();
        clearWaveObjectiveState();
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
        clearActiveEventArrows();
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
        clearActiveEventArrows();
        clearJudgmentVisuals();
        testCombatAiMode = false;
        miniBossSpells.clear();
        waveMobTactics.clear();
        combatTeleportPermits.clear();
        blockedTeleportLogAt.clear();
        nextSkeletonArrowMillis.clear();
        nextMiniBossSpellMillis.clear();
        nextWavePathRequestMillis.clear();
        lastWavePathLogMillis.clear();
        combatHelpers.clear();
        recentBossTargets.clear();
        previousBossSpell = null;
        bossTargetCursor = 0;
        bossSpellCursor = 0;
        bossProgressEntity = null;
        bossLastProgressLocation = null;
        bossLastProgressAt = 0L;
        nextBossStuckTeleportMillis = 0L;
        waveTargetCursor = 0;
        bossTacticCycle = 0;
        nextBossTacticMillis = 0L;
        nextTargetMillis = 0L;
        nextSpellMillis = 0L;
        nextWaveTargetMillis = 0L;
        bossSpellPauseUntilMillis = 0L;
        lastBossTeleportMillis = 0L;
        lastBossPathRequestMillis = 0L;
        lastBossFeintMillis = 0L;
        bossTacticCycle = 0;
        nextBossTacticMillis = 0L;
        servantsSummonedAt70 = false;
        servantsSummonedAt35 = false;
        absorptionCompleted = false;
        absorptionAttackEmpowered = false;
    }

    private void clearClientEffects() {
        stopEventMusic();
        clientBindingReadyPlayers.clear();
        nextClientBindingRefreshMillis = 0L;
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
        clientBindingReadyPlayers.remove(player.getUniqueId());
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
            case WAVE_1 -> phaseMusicOrLegacy("wave-1", config.wavesMusic());
            case WAVE_2 -> phaseMusicOrLegacy("wave-2", config.wavesMusic());
            case WAVE_3 -> phaseMusicOrLegacy("wave-3", config.wavesMusic());
            case WAVE_4 -> phaseMusicOrLegacy("wave-4", config.wavesMusic());
            case WAVE_5 -> phaseMusicOrLegacy("wave-5", config.wavesMusic());
            case INTERMISSION_1 -> phaseMusicOrLegacy("intermission-1", config.wavesMusic());
            case INTERMISSION_2 -> phaseMusicOrLegacy("intermission-2", config.wavesMusic());
            case INTERMISSION_3 -> phaseMusicOrLegacy("intermission-3", config.wavesMusic());
            case INTERMISSION_4 -> phaseMusicOrLegacy("intermission-4", config.wavesMusic());
            case BOSS_CINEMATIC -> phaseMusicOrLegacy("boss-cinematic", config.bossMusic());
            case BOSS_ACTIVE -> halfHealthTriggered ? config.bossHalfMusic() : config.bossMusic();
            case FINAL_DRAIN -> phaseMusicOrLegacy("final-drain", config.bossFinalMusic());
            case FINAL_RITUAL -> phaseMusicOrLegacy("final-ritual", config.bossFinalMusic());
            case FINAL_WAVE -> phaseMusicOrLegacy("final-wave", config.bossFinalMusic());
            case BOSS_FINISH -> phaseMusicOrLegacy("boss-finish", config.bossFinalMusic());
            case VICTORY_PROCESSING, VICTORY -> config.victoryMusic();
            default -> null;
        };
    }

    private EventConfig.MusicTrack phaseMusicOrLegacy(String key, EventConfig.MusicTrack fallback) {
        EventConfig.MusicTrack phaseTrack = config == null ? null : config.phaseMusic(key);
        return phaseTrack == null ? fallback : phaseTrack;
    }

    private boolean isEventMusicPhase() {
        return switch (phase) {
            case WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3,
                    INTERMISSION_3, WAVE_4, INTERMISSION_4, WAVE_5,
                    BOSS_CINEMATIC,
                    BOSS_ACTIVE, FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH,
                    VICTORY_PROCESSING, VICTORY -> true;
            default -> false;
        };
    }

    private boolean isVictoryMusicTail(EventPhase previous, EventPhase next) {
        return next == EventPhase.UNLOCKED
                && (previous == EventPhase.VICTORY_PROCESSING || previous == EventPhase.VICTORY)
                && config != null
                && config.victoryMusic() != null
                && !config.victoryMusic().soundId().isBlank();
    }

    private void playEventMusic(EventConfig.MusicTrack track) {
        if (!isEventMusicPhase() || track == null || track.soundId().isBlank()) {
            stopEventMusic();
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
                if (!isEventMusicPhase()) {
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
        List<EventConfig.MusicTrack> tracks = new ArrayList<>(List.of(
                config.wavesMusic(), config.bossMusic(), config.bossHalfMusic(),
                config.bossFinalMusic(), config.victoryMusic()));
        tracks.addAll(config.allMusicTracks());
        for (EventConfig.MusicTrack track : new LinkedHashSet<>(tracks)) {
            player.stopSound(track.soundId(), SoundCategory.MUSIC);
        }
    }

    private void syncEventMusic(Player player) {
        EventConfig.MusicTrack track = musicForPhase();
        if (!isEventMusicPhase() || track == null || player == null || !isActiveArenaParticipant(player)) {
            if (!isEventMusicPhase() && player != null) {
                stopEventMusic(player);
            }
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
        if (config == null || !"local".equalsIgnoreCase(config.environment())) {
            message(player, "&cЛокальная проверка музыки разрешена только при environment=local.");
            return;
        }
        String normalized = requested.toLowerCase(Locale.ROOT);
        EventConfig.MusicTrack track = switch (normalized) {
            case "waves", "wave" -> config.wavesMusic();
            case "boss" -> config.bossMusic();
            case "half", "boss-half" -> config.bossHalfMusic();
            case "final", "boss-final" -> config.bossFinalMusic();
            case "victory" -> config.victoryMusic();
            default -> config.phaseMusic(normalized);
        };
        if (track == null) {
            message(player, "&e/cmend test music <waves|boss|half|final|victory|wave-1|wave-2|wave-3|wave-4|wave-5|intermission-1|...|boss-finish>");
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
            case "debug" -> handleDebug(sender, args);
            case "recovery" -> handleStatus(sender);
            case "wave" -> handleWave(sender, args);
            case "boss" -> handleBoss(sender, args);
            case "client" -> handleClient(sender, args);
            default -> message(sender, "&eИспользование: /cmend status|debug|recovery|core|arena|gate|portalroom|resources|ritual|wave|boss|client|test|cleanup|reset|unlock");
        }
        return true;
    }

    private void handleDebug(CommandSender sender, String[] args) {
        String section = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        if (!List.of("all", "packets", "objectives", "hazards", "perf", "ai").contains(section)) {
            message(sender, "&e/cmend debug packets|objectives|hazards|perf|ai");
            return;
        }
        if ("all".equals(section) || "packets".equals(section)) {
            sampleRuntimeDiagnostics();
            RuntimeDiagnosticsSnapshot diagnostics = runtimeDiagnosticsSnapshot;
            message(sender, "&7CLIENT_BINDINGS audience=" + eventAudience().size()
                    + " entityBindings=" + entityBindingInstances.size()
                    + " bossBinding=" + (!bossBindingInstanceId.isBlank())
                    + " lastPlayerPings=" + (diagnostics.lastPlayerPings().isBlank()
                    ? "none" : diagnostics.lastPlayerPings()));
            message(sender, "&7RUNTIME_DIAGNOSTICS sampleIntervalMs=" + diagnostics.sampleIntervalMillis()
                    + " packetQualityMode=" + diagnostics.packetQualityMode()
                    + " pluginMessagesPerSecond=" + formatRate(diagnostics.pluginMessagesPerSecond())
                    + " estimatedParticlePacketsPerSecond="
                    + formatRate(diagnostics.estimatedParticlePacketsPerSecond())
                    + " particleBatchesPerSecond=" + formatRate(diagnostics.particleBatchesPerSecond()));
            message(sender, "&7RUNTIME_DIAGNOSTICS ownedLiving=" + diagnostics.ownedLivingEntities()
                    + " temporaryDisplays=" + diagnostics.temporaryDisplays()
                    + " activeProjectiles=" + diagnostics.activeProjectiles()
                    + " gcCollections=" + diagnostics.gcCollections()
                    + " gcPauseMs=" + diagnostics.gcPauseMillis()
                    + " serverThreadCpuPercent=" + formatRate(diagnostics.serverThreadCpuPercent())
                    + " tps=" + diagnostics.tps()
                    + " mspt=" + formatRate(diagnostics.mspt()));
        }
        if ("all".equals(section) || "objectives".equals(section)) {
            int liveObjectiveMobs = activeWave >= 1 && activeWave <= 5
                    ? countLiveWaveEntitiesForWave(activeWave) : 0;
            String tower = towerDefenseState == null
                    ? "tower=none"
                    : "tower=" + towerDefenseState.outcome()
                    + " towerHealth=" + Math.round(towerDefenseState.currentHealth())
                    + "/" + Math.round(towerDefenseState.maxHealth())
                    + " towerAttempt=" + towerDefenseState.attempt()
                    + " towerDeadline=" + towerDefenseState.deadlineMillis();
            message(sender, "&7OBJECTIVE_DIAGNOSTICS phase=" + phase
                    + " wave=" + activeWave + " complete=" + waveObjectiveComplete
                    + " trackedMobs=" + waveObjectiveMobCount + " liveMobs=" + liveObjectiveMobs
                    + " visuals=" + waveObjectiveVisuals.size()
                    + " marked=" + (waveTwoMarkedPlayerUuid == null ? "none" : waveTwoMarkedPlayerUuid)
                    + " pulseDeadline=" + waveOnePulseDeadlineMillis + " " + tower);
        }
        if ("all".equals(section) || "hazards".equals(section)) {
            HazardMutationJournal.Snapshot journal = hazardJournal == null
                    ? HazardMutationJournal.Snapshot.empty() : hazardJournal.load();
            World hazardWorld = Bukkit.getWorld(worldName);
            long infernoMagma = 0L;
            if (hazardWorld != null) {
                for (HazardPlanner.Point point : arenaInfernoOriginalBlocks.keySet()) {
                    if (hazardWorld.getBlockAt(point.x(), combatLevelY() - 1, point.z()).getType()
                            == Material.MAGMA_BLOCK) {
                        infernoMagma++;
                    }
                }
            }
            message(sender, "&7HAZARD_DIAGNOSTICS storm=" + riftStormHazards.size()
                    + " stormSafe=" + riftStormSafeCells.size()
                    + " inferno=" + arenaInfernoBlocks.size()
                    + " realFire=0 infernoMagma=" + infernoMagma
                    + " journal=" + journal.status() + " entries=" + journal.entries().size()
                    + " journalPath=" + (hazardJournal == null ? "none" : hazardJournal.path()));
        }
        if ("all".equals(section) || "perf".equals(section)) {
            double[] tps = getServer().getTPS();
            long[] tickTimes = getServer().getTickTimes();
            long totalNanos = 0L;
            for (long tickTime : tickTimes) {
                totalNanos += Math.max(0L, tickTime);
            }
            long averageTickNanos = tickTimes.length == 0 ? 0L : totalNanos / tickTimes.length;
            message(sender, "&7PERF_DIAGNOSTICS tps=" + formatTps(tps)
                    + " avgTickMs=" + String.format(Locale.ROOT, "%.2f", averageTickNanos / 1_000_000.0D)
                    + " online=" + Bukkit.getOnlinePlayers().size()
                    + " owned=" + ownedEntities.size() + " projectiles=" + activeRiftProjectiles.size()
                    + " marks=" + activeVoidMarkCenters.size());
        }
        if ("all".equals(section) || "ai".equals(section)) {
            Location anchor = coreCombatAnchorLocation();
            int mobile = 0;
            int aiEnabled = 0;
            int targeted = 0;
            int coreObjective = 0;
            int outside = 0;
            int onCore = 0;
            long diagnosticNow = System.currentTimeMillis();
            List<String> samples = new ArrayList<>();
            List<String> outsideSamples = new ArrayList<>();
            for (Entity entity : new ArrayList<>(ownedEntities.values())) {
                String kind = readString(entity, keyKind);
                if (!(entity instanceof Mob mob) || (!EVENT_KIND_BOSS.equals(kind) && !isWaveCombatKind(kind))
                        || !isLiveOwnedEntity(entity.getUniqueId())) {
                    continue;
                }
                mobile++;
                if (mob.hasAI()) {
                    aiEnabled++;
                }
                if (mob.getTarget() instanceof Player player && isCombatTarget(player)) {
                    targeted++;
                }
                if (isTowerDefenseMob(entity) && !hasTowerPlayerAggro(entity, diagnosticNow)) {
                    coreObjective++;
                }
                if (anchor != null) {
                    double radius = boundedCombatRadius(config == null ? MAX_COMBAT_RADIUS_BLOCKS
                            : EVENT_KIND_BOSS.equals(kind) ? config.bossRadius()
                            : config.containmentRadius());
                    boolean outsideHorizontal = horizontalDistanceSquared(entity.getLocation(), anchor)
                            > radius * radius;
                    boolean outsideVertical = outsideCombatVertical(entity.getLocation(), anchor);
                    if (outsideHorizontal || outsideVertical) {
                        outside++;
                        if (outsideSamples.size() < 8) {
                            outsideSamples.add(kind + ":" + entity.getUniqueId()
                                    + "=" + locationText(entity.getLocation())
                                    + ",horizontal=" + String.format(Locale.ROOT, "%.2f",
                                    Math.sqrt(horizontalDistanceSquared(entity.getLocation(), anchor)))
                                    + ",vertical=" + Math.abs(entity.getLocation().getBlockY()
                                    - anchor.getBlockY())
                                    + ",radius=" + String.format(Locale.ROOT, "%.2f", radius));
                        }
                    }
                    if (isCoreBlockPosition(entity.getLocation())) {
                        onCore++;
                    }
                }
                if (samples.size() < 6) {
                    samples.add(kind + ":" + entity.getUniqueId().toString().substring(0, 8)
                            + "=" + locationText(entity.getLocation())
                            + ",target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getType()));
                }
            }
            message(sender, "&7AI_DIAGNOSTICS phase=" + phase + " stage=" + bossStage
                    + " mobile=" + mobile + " aiEnabled=" + aiEnabled + " targeted=" + targeted
                    + " coreObjective=" + coreObjective
                    + " outside=" + outside + " onCore=" + onCore
                    + " bossCast=" + bossCastState + " stepCap=" + MAX_COMBAT_STEP_BLOCKS);
            BossStagePolicy.CombatProfile profile = currentBossCombatProfile();
            message(sender, "&7AI_PROFILE stage=" + bossStage
                    + " absorptionCompleted=" + absorptionCompleted
                    + " movementSpeed=" + String.format(Locale.ROOT, "%.3f", profile.movementSpeed())
                    + " spellCooldownMultiplier=" + String.format(Locale.ROOT, "%.3f", profile.spellCooldownMultiplier())
                    + " teleportCooldownMultiplier=" + String.format(Locale.ROOT, "%.3f", profile.teleportCooldownMultiplier())
                    + " targetRotationMultiplier=" + String.format(Locale.ROOT, "%.3f", profile.targetRotationMultiplier())
                    + " meleeDamageBonus=" + String.format(Locale.ROOT, "%.1f", profile.meleeDamageBonus())
                    + " nextMeleeAttackBonus=" + String.format(Locale.ROOT, "%.1f", profile.nextMeleeAttackBonus())
                    + " summonCap=" + profile.summonCap());
            if (!outsideSamples.isEmpty()) {
                message(sender, "&7AI_OUTSIDE " + String.join(";", outsideSamples));
            }
            if (!samples.isEmpty()) {
                message(sender, "&7AI_TARGETS " + String.join(";", samples));
            }
        }
    }

    private String formatTps(double[] tps) {
        if (tps == null || tps.length == 0) {
            return "unavailable";
        }
        return java.util.Arrays.stream(tps)
                .mapToObj(value -> String.format(Locale.ROOT, "%.2f", value))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    /**
     * Roll one bounded five-second window on the existing event tick.  The
     * sampler deliberately uses only the already-owned entity map and the
     * online-player list; it never starts a second repeating task or scans a
     * world.  The resulting snapshot is both human-readable through RCON and
     * useful as evidence for the two-player local run.
     */
    private void sampleRuntimeDiagnostics() {
        long now = System.currentTimeMillis();
        if (runtimeDiagnosticsWindowStartedAtMillis <= 0L) {
            runtimeDiagnosticsWindowStartedAtMillis = now;
            runtimeDiagnosticsLastThreadCpuWallMillis = now;
            return;
        }
        long elapsed = now - runtimeDiagnosticsWindowStartedAtMillis;
        if (now - runtimeDiagnosticsWindowStartedAtMillis < RUNTIME_DIAGNOSTICS_WINDOW_MILLIS) {
            return;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        String playerPings = onlinePlayers.stream()
                .map(player -> player.getName() + "=" + Math.max(0, player.getPing()) + "ms")
                .collect(java.util.stream.Collectors.joining(","));
        int maxPing = onlinePlayers.stream().mapToInt(player -> Math.max(0, player.getPing()))
                .max().orElse(0);
        int averagePing = onlinePlayers.isEmpty() ? 0
                : (int) Math.round(onlinePlayers.stream()
                .mapToInt(player -> Math.max(0, player.getPing()))
                .average().orElse(0.0D));

        int ownedLiving = 0;
        int temporaryDisplays = 0;
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            if (entity instanceof LivingEntity && isLiveOwnedEntity(entity.getUniqueId())) {
                ownedLiving++;
            }
            if (entity instanceof Display && entity.isValid() && !entity.isDead()) {
                temporaryDisplays++;
            }
        }

        double particlePacketsPerSecond = runtimeDiagnosticsParticlePackets * 1000.0D / elapsed;
        double particleBatchesPerSecond = runtimeDiagnosticsParticleBatches * 1000.0D / elapsed;
        double pluginMessagesPerSecond = runtimeDiagnosticsPluginMessages * 1000.0D / elapsed;
        double mspt = averageTickMillis();
        long gcCollections = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0L, bean.getCollectionCount()))
                .sum();
        long gcPauseMillis = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0L, bean.getCollectionTime()))
                .sum();
        long gcDelta = runtimeDiagnosticsLastGcCollections < 0L ? 0L
                : Math.max(0L, gcCollections - runtimeDiagnosticsLastGcCollections);
        long gcPauseDelta = runtimeDiagnosticsLastGcPauseMillis < 0L ? 0L
                : Math.max(0L, gcPauseMillis - runtimeDiagnosticsLastGcPauseMillis);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long threadCpuNanos = threadBean.isCurrentThreadCpuTimeSupported()
                ? Math.max(0L, threadBean.getCurrentThreadCpuTime()) : -1L;
        double serverThreadCpuPercent = 0.0D;
        if (threadCpuNanos >= 0L && runtimeDiagnosticsLastThreadCpuNanos >= 0L
                && runtimeDiagnosticsLastThreadCpuWallMillis > 0L) {
            long cpuDeltaNanos = Math.max(0L, threadCpuNanos - runtimeDiagnosticsLastThreadCpuNanos);
            long wallDeltaMillis = Math.max(1L, now - runtimeDiagnosticsLastThreadCpuWallMillis);
            serverThreadCpuPercent = Math.min(999.0D,
                    cpuDeltaNanos / 1_000_000.0D / wallDeltaMillis * 100.0D);
        }

        String quality = packetQualityMode(mspt, particlePacketsPerSecond, pluginMessagesPerSecond);
        runtimeDiagnosticsSnapshot = new RuntimeDiagnosticsSnapshot(
                now, elapsed, quality, playerPings, averagePing, maxPing,
                onlinePlayers.size(), ownedLiving, temporaryDisplays, activeRiftProjectiles.size(),
                particlePacketsPerSecond, particleBatchesPerSecond, pluginMessagesPerSecond,
                gcDelta, gcPauseDelta, serverThreadCpuPercent, formatTps(getServer().getTPS()), mspt);
        runtimeDiagnosticsLastSampleMillis = now;
        runtimeDiagnosticsWindowStartedAtMillis = now;
        runtimeDiagnosticsParticlePackets = 0L;
        runtimeDiagnosticsParticleBatches = 0L;
        runtimeDiagnosticsPluginMessages = 0L;
        runtimeDiagnosticsLastGcCollections = gcCollections;
        runtimeDiagnosticsLastGcPauseMillis = gcPauseMillis;
        runtimeDiagnosticsLastThreadCpuNanos = threadCpuNanos;
        runtimeDiagnosticsLastThreadCpuWallMillis = now;

        if (bootstrapped && isConfigured()) {
            getLogger().info("END_EVENT_RUNTIME_DIAGNOSTICS event=" + eventId
                    + " phase=" + phase + " sample_interval_ms=" + elapsed
                    + " packet_quality=" + quality
                    + " pings=" + (playerPings.isBlank() ? "none" : playerPings)
                    + " avg_ping_ms=" + averagePing + " max_ping_ms=" + maxPing
                    + " owned_living=" + ownedLiving + " displays=" + temporaryDisplays
                    + " projectiles=" + activeRiftProjectiles.size()
                    + " particle_packets_sec=" + formatRate(particlePacketsPerSecond)
                    + " plugin_messages_sec=" + formatRate(pluginMessagesPerSecond)
                    + " gc_collections=" + gcDelta + " gc_pause_ms=" + gcPauseDelta
                    + " server_thread_cpu_percent=" + formatRate(serverThreadCpuPercent));
        }
    }

    private double averageTickMillis() {
        long[] tickTimes = getServer().getTickTimes();
        if (tickTimes == null || tickTimes.length == 0) {
            return 0.0D;
        }
        long totalNanos = 0L;
        for (long tickTime : tickTimes) {
            totalNanos += Math.max(0L, tickTime);
        }
        return totalNanos / (double) tickTimes.length / 1_000_000.0D;
    }

    private String packetQualityMode(double mspt, double particlePacketsPerSecond,
                                     double pluginMessagesPerSecond) {
        if (mspt >= 40.0D || particlePacketsPerSecond >= 4_000.0D
                || pluginMessagesPerSecond >= 40.0D) {
            return PACKET_QUALITY_MINIMAL_SAFE;
        }
        if (mspt >= 25.0D || particlePacketsPerSecond >= 1_800.0D
                || pluginMessagesPerSecond >= 20.0D) {
            return PACKET_QUALITY_REDUCED;
        }
        return PACKET_QUALITY_FULL;
    }

    private String formatRate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "unavailable";
        }
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0D, value));
    }

    private int estimatedSpellFlightParticles(String spellId) {
        SpellVisualPolicy.VisualProfile profile = SpellVisualPolicy.profile(spellId);
        return profile == null ? 8 : profile.estimatedParticles();
    }

    private void recordParticleEmission(int estimatedParticles) {
        runtimeDiagnosticsParticleBatches++;
        runtimeDiagnosticsParticlePackets += Math.max(0, estimatedParticles);
    }

    private void recordPluginMessage() {
        runtimeDiagnosticsPluginMessages++;
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
        LivingEntity currentBoss = liveBoss();
        String bossStatus = currentBoss == null ? "none"
                : currentBoss.getUniqueId() + " hp=" + Math.round(bossVirtualHealth(currentBoss))
                + "/" + Math.round(config.bossHealth())
                + " physical=" + Math.round(currentBoss.getHealth())
                + "/" + Math.round(currentBoss.getMaxHealth());
        message(sender, "&7wave=&f" + activeWave + " &7event-mobs=&f" + countLiveOwnedMobs()
                + " &7boss=&f" + bossStatus);
        message(sender, "&7half=&f" + halfHealthTriggered + " &7final=&f" + finalDrainTriggered
                + " &7endUnlocked=&f" + endUnlocked + " &7victory=&f" + victoryStep);
        if (!recoveryReason.isBlank()) {
            message(sender, "&cRecovery reason: " + recoveryReason);
        }
    }

    private void handleCore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "&e/cmend core set <N> | setat <x> <y> <z> <N> | info | rebuild | remove confirm");
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
            case "setat" -> setCoreAt(sender, args);
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
            default -> message(sender, "&e/cmend core set <N> | setat <x> <y> <z> <N> | info | rebuild | remove confirm");
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
                message(sender, "&e/cmend gate pos1|pos2|setat <x1> <y1> <z1> <x2> <y2> <z2>|info|preview|open [ticks-per-layer]|restore confirm");
                return;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "setat" -> setGateAt(sender, args);
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
            default -> message(sender, "&e/cmend gate pos1|pos2|setat <x1> <y1> <z1> <x2> <y2> <z2>|info|preview|open [ticks-per-layer]|restore confirm");
            }
        }
    }

    /**
     * Stores an exact Gate cuboid for the disposable local scene.  It only
     * changes durable layout metadata; the setup script owns the bounded
     * vanilla obsidian placement and verifies it independently.
     */
    private void setGateAt(CommandSender sender, String[] args) {
        if (!isConsoleSetupSender(sender)) {
            message(sender, "&cТочная привязка Gate доступна только локальной консоли.");
            return;
        }
        if (!"local".equalsIgnoreCase(config.environment())) {
            message(sender, "&cТочная привязка Gate разрешена только при environment: local.");
            return;
        }
        if (!isConfigured()) {
            message(sender, "&cСначала настрой Core.");
            return;
        }
        if (args.length < 8) {
            message(sender, "&e/cmend gate setat <x1> <y1> <z1> <x2> <y2> <z2>");
            return;
        }
        int x1;
        int y1;
        int z1;
        int x2;
        int y2;
        int z2;
        try {
            x1 = Integer.parseInt(args[2]);
            y1 = Integer.parseInt(args[3]);
            z1 = Integer.parseInt(args[4]);
            x2 = Integer.parseInt(args[5]);
            y2 = Integer.parseInt(args[6]);
            z2 = Integer.parseInt(args[7]);
        } catch (NumberFormatException invalid) {
            message(sender, "&cКоординаты Gate должны быть целыми числами.");
            return;
        }
        EventLayoutState.Point first = new EventLayoutState.Point(worldName, x1, y1, z1);
        EventLayoutState.Point second = new EventLayoutState.Point(worldName, x2, y2, z2);
        try {
            GateOpeningPlan.from(
                    new GateOpeningPlan.Point(first.world(), first.x(), first.y(), first.z()),
                    new GateOpeningPlan.Point(second.world(), second.x(), second.y(), second.z()),
                    MAX_GATE_VOLUME);
        } catch (IllegalArgumentException invalid) {
            message(sender, "&cGate bounded validation failed: " + invalid.getMessage());
            return;
        }
        EventLayoutState previous = layoutState;
        layoutState = withGatePoints(first, second);
        if (saveStateSync()) {
            message(sender, "&aGate сохранён: &f" + pointText(first) + " .. " + pointText(second));
        } else {
            layoutState = previous;
            message(sender, "&cGate не сохранён durable; изменение отменено.");
        }
    }

    private EventLayoutState.Point pointAt(Location location) {
        return new EventLayoutState.Point(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Paper's RCON bridge can expose a remote console façade that is not the
     * Bukkit ConsoleCommandSender implementation.  Treat every non-player
     * command source as console-like, while still preventing an in-game player
     * from using the exact-coordinate local setup commands.
     */
    private boolean isConsoleSetupSender(CommandSender sender) {
        return sender != null && (sender instanceof ConsoleCommandSender || !(sender instanceof Player));
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
        Location point = new Location(world, x, y, z);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(177, 70, 255), 1.0F);
        world.spawnParticle(Particle.DUST, point, 1,
                0.0D, 0.0D, 0.0D, 0.0D, dust);
        // Keep the wireframe readable with reduced-particle settings and
        // shader packs: the colored dust carries the identity, while the
        // bright rod makes each half-block edge unmissable in-game.
        world.spawnParticle(Particle.END_ROD, point, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
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
            rebuildPersistedVisuals();
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
            message(sender, "&e/cmend test run creative | wave <1|2|3|4|5|final> | tower fail | ai | boss | teleport <wave|boss> | visuals <mobs|boss> | music <phase> [player]");
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
        if ("visuals".equalsIgnoreCase(args[1])) {
            handleTestVisuals(sender, args);
            return;
        }
        if ("teleport".equalsIgnoreCase(args[1])) {
            handleTestTeleport(sender, args);
            return;
        }
        if ("tower".equalsIgnoreCase(args[1])) {
            handleTestTowerFailure(sender, args);
            return;
        }
        if ("music".equalsIgnoreCase(args[1])) {
            String requested = args.length > 2 ? args[2] : "";
            Player player = sender instanceof Player current
                    ? current
                    : args.length > 3 ? Bukkit.getPlayerExact(args[3]) : null;
            if (player == null) {
                message(sender, "&cУкажи онлайн-игрока: /cmend test music <phase> <player>.");
                return;
            }
            playTestMusic(player, requested);
            return;
        }
        if ("wave".equalsIgnoreCase(args[1])) {
            int wave = "final".equalsIgnoreCase(args.length > 2 ? args[2] : "")
                    ? FINAL_WAVE_NUMBER : parseInt(args, 2, 0);
            if (wave < 1 || wave > 5 && wave != FINAL_WAVE_NUMBER) {
                message(sender, "&cВолна должна быть 1, 2, 3, 4, 5 или final.");
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
        message(sender, "&e/cmend test run creative | wave <1|2|3|4|5|final> | tower fail | ai | boss | teleport <wave|boss> | visuals <mobs|boss> | music <phase> [player]");
    }

    /**
     * Local-only failure injection for the official Tower Defense retry test.
     * It uses the same immutable damage transition as a real Core attack and
     * then enters the production cleanup/retry boundary.  This is deliberately
     * not exposed outside environment=local, and it never grants rewards or
     * skips a wave on its own.
     */
    private void handleTestTowerFailure(CommandSender sender, String[] args) {
        if (config == null || !"local".equalsIgnoreCase(config.environment())) {
            message(sender, "&cWAVE_TEST_FAILURE_GUARD разрешён только при environment=local.");
            return;
        }
        if (args.length < 3 || !("fail".equalsIgnoreCase(args[2])
                || "failure".equalsIgnoreCase(args[2]))) {
            message(sender, "&e/cmend test tower fail");
            return;
        }
        if (phase != EventPhase.WAVE_4 || activeWave != 4
                || towerDefenseState == null
                || towerDefenseState.outcome() != TowerDefensePolicy.Outcome.ACTIVE) {
            message(sender, "&cWAVE_TEST_FAILURE_GUARD доступен только во время активной official Wave 4.");
            return;
        }
        double healthBefore = towerDefenseState.currentHealth();
        String attackId = "test:tower-failure:" + eventId + ":" + generation;
        long now = System.currentTimeMillis();
        towerDefenseState = TowerDefensePolicy.damage(towerDefenseState, attackId, healthBefore);
        // A real objective tick finalizes a depleted Core before entering the
        // failure handler.  Keep the local injection on that same immutable
        // ACTIVE -> FAILURE boundary so retry() cannot silently restart as
        // attempt one.
        towerDefenseState = TowerDefensePolicy.finish(towerDefenseState, now);
        getLogger().warning("WAVE_TEST_FAILURE_INJECTED event=" + eventId
                + " wave=4 attempt=" + towerDefenseState.attempt()
                + " core_health_before=" + healthBefore
                + " core_health_after=" + towerDefenseState.currentHealth()
                + " attack_id=" + attackId);
        if (towerDefenseState.outcome() == TowerDefensePolicy.Outcome.FAILURE) {
            handleTowerDefenseFailure();
        }
        message(sender, "&eWAVE_TEST_FAILURE_INJECTED: Core Wave 4 помечен разрушенным; ожидается clean retry.");
    }

    /**
     * Local-only probe for the hard EntityTeleportEvent boundary.  The
     * command deliberately calls Bukkit's real entity teleport API without
     * the internal permit used by AI movement.  This catches a regression in
     * the listener itself; a server-console selector cannot do that reliably
     * on every Paper build because some selector command paths only accept
     * players.
     */
    private void handleTestTeleport(CommandSender sender, String[] args) {
        if (config == null || !"local".equalsIgnoreCase(config.environment())) {
            message(sender, "&cTEST_TELEPORT_GUARD разрешён только при environment=local.");
            return;
        }
        if (args.length < 3 || !("wave".equalsIgnoreCase(args[2]) || "boss".equalsIgnoreCase(args[2]))) {
            message(sender, "&e/cmend test teleport wave|boss");
            return;
        }
        String requestedKind = args[2].toLowerCase(Locale.ROOT);
        Entity entity;
        if ("boss".equals(requestedKind)) {
            entity = liveBoss();
        } else {
            entity = ownedEntities.values().stream()
                    .filter(candidate -> candidate instanceof LivingEntity
                            && isWaveCombatKind(readString(candidate, keyKind))
                            && isLiveOwnedEntity(candidate.getUniqueId()))
                    .findFirst()
                    .orElse(null);
        }
        if (entity == null) {
            message(sender, "&eTEST_TELEPORT_GUARD kind=" + requestedKind + " no_entity");
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        Location before = entity.getLocation().clone();
        if (anchor == null || anchor.getWorld() == null) {
            message(sender, "&cTEST_TELEPORT_GUARD kind=" + requestedKind + " no_anchor");
            return;
        }
        Location requested = anchor.clone().add(100.0D, 20.0D, 100.0D);
        // Intentionally no teleportCombatEntity(): this is the external
        // teleport case that onOwnedEntityTeleport must cancel.
        boolean moved = entity.teleport(requested);
        Location after = entity.getLocation();
        double radius = "boss".equals(requestedKind)
                ? boundedCombatRadius(config.bossRadius())
                : boundedCombatRadius(config.containmentRadius());
        boolean outside = after != null && after.getWorld() != null
                && anchor.getWorld().equals(after.getWorld())
                && (horizontalDistanceSquared(after, anchor) > radius * radius
                || outsideCombatVertical(after, anchor));
        String result = "TEST_TELEPORT_GUARD kind=" + requestedKind
                + " entity=" + entity.getUniqueId()
                + " moved=" + moved
                + " outside=" + outside
                + " before=" + formatLocation(before)
                + " after=" + formatLocation(after);
        getLogger().info(result);
        message(sender, "&7" + result);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "none";
        }
        return location.getWorld().getName() + ":"
                + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", location.getX(), location.getY(), location.getZ());
    }

    private void handleTestVisuals(CommandSender sender, String[] args) {
        if (args.length < 3) {
            message(sender, "&e/cmend test visuals mobs|boss [phase]");
            return;
        }
        String requested = args[2].toLowerCase(Locale.ROOT);
        if ("mobs".equals(requested)) {
            int reported = 0;
            for (Entity entity : new ArrayList<>(ownedEntities.values())) {
                String visual = clientVisualId(entity);
                if (visual.isBlank()) {
                    continue;
                }
                message(sender, "&7MOB_VISUAL uuid=" + entity.getUniqueId()
                        + " role=" + readString(entity, keyKind)
                        + " clientVisual=" + visual
                        + " boundViewers=" + eventAudience().size()
                        + " resource=assets/copimineclient/textures/entity/"
                        + visual.toLowerCase(Locale.ROOT) + ".png");
                reported++;
            }
            message(sender, "&7MOB_VISUAL_TOTAL=" + reported
                    + " &8(official phase/roster/victory не изменены)");
            return;
        }
        if ("boss".equals(requested)) {
            LivingEntity live = liveBoss();
            if (live == null) {
                message(sender, "&eBOSS_VISUAL отсутствует. Создай /cmend test boss.");
                return;
            }
            String requestedPhase = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : bossStage.name().toLowerCase(Locale.ROOT);
            message(sender, "&7BOSS_VISUAL uuid=" + live.getUniqueId()
                    + " bossPhase=" + bossStage.name()
                    + " requestedPhase=" + requestedPhase
                    + " bossBinding=" + (bossBindingInstanceId.isBlank() ? "none" : bossBindingInstanceId)
                    + " boundViewers=" + eventAudience().size()
                    + " resource=assets/copimineclient/textures/entity/rift_guardian_"
                    + requestedPhase + ".png"
                    + " &8(official phase/roster/victory не изменены)");
            return;
        }
        message(sender, "&e/cmend test visuals mobs|boss [phase]");
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
        if (phase != EventPhase.COLLECTING || !officialRewardRoster.isEmpty()
                || officialCombatStateActive()) {
            message(sender, "&cCreative full-run остановлен: активная official session или ивент уже не в COLLECTING.");
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
                    INTERMISSION_3, WAVE_4, INTERMISSION_4, WAVE_5, BOSS_CINEMATIC,
                    BOSS_ACTIVE, FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH,
                    VICTORY_PROCESSING, VICTORY, UNLOCKED, RECOVERY_REQUIRED -> true;
            default -> false;
        };
    }

    private void tickCreativeTest() {
        Player player = creativeTestPlayer();
        if (player == null || player.getGameMode() != GameMode.CREATIVE
                || !isArenaLocation(player.getLocation()) || creativeTestGeneration != generation
                || phase != EventPhase.COLLECTING || !officialRewardRoster.isEmpty()) {
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
                    setBossVirtualHealth(boss, config.bossHalfHealth());
                }
                getLogger().info("CREATIVE_TEST_HALF event=" + eventId + " generation=" + generation
                        + " threshold=" + config.bossHalfHealth() + " health="
                        + (boss == null ? "missing" : bossVirtualHealth(boss)));
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
                    setBossVirtualHealth(boss, config.bossFinalHealth());
                }
                getLogger().info("CREATIVE_TEST_FINAL_DRAIN event=" + eventId + " generation=" + generation
                        + " threshold=" + config.bossFinalThreshold() + " finalHealth=" + config.bossFinalHealth()
                        + " drainFraction=" + config.finalDrainFraction());
            }
            case 16 -> {
                spawnWave(FINAL_WAVE_NUMBER, true);
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
        Location spawn = safeBossSpawnLocation();
        if (spawn == null) {
            return;
        }
        Enderman boss = (Enderman) core.getWorld().spawnEntity(spawn, EntityType.ENDERMAN);
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
        clearCombatAiState();
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
            message(sender, "&e/cmend wave spawn <1|2|3|4|5|final> | clear");
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            clearWaveEntities();
            message(sender, "&aУдалены только event-owned wave entities.");
            return;
        }
        if ("spawn".equalsIgnoreCase(args[1])) {
            String requested = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
            int wave = "final".equals(requested) ? FINAL_WAVE_NUMBER : parseInt(args, 2, 0);
            if (wave < 1 || wave > 5 && wave != FINAL_WAVE_NUMBER) {
                message(sender, "&cВолна должна быть 1, 2, 3, 4, 5 или final.");
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
                    setBossVirtualHealth(boss, 0.0D);
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
                } else if (List.of("void_blast", "rift_projectile", "rift_arrows", "void_mark", "summon", "summon_servants",
                        "arena_inferno")
                        .contains(spell)) {
                    EndRiftAiPolicy.BossSpell requestedSpell = switch (spell) {
                        case "void_blast" -> EndRiftAiPolicy.BossSpell.VOID_BLAST;
                        case "rift_projectile" -> EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE;
                        case "rift_arrows" -> EndRiftAiPolicy.BossSpell.RIFT_ARROWS;
                        case "void_mark" -> EndRiftAiPolicy.BossSpell.VOID_MARK;
                        case "arena_inferno" -> EndRiftAiPolicy.BossSpell.ARENA_INFERNO;
                        default -> EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS;
                    };
                    castBossSpell(boss, requestedSpell, true);
                    message(sender, "&aBoss spell test requested: &f" + requestedSpell.id());
                } else {
                    message(sender, "&e/cmend boss spell <void_blast|rift_projectile|void_mark|summon|arena_inferno|control_reverse>");
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
        configureCore(player, block, players);
    }

    /**
     * Rebinds a Core to an exact block for the disposable local scene setup.
     * This path is deliberately console-only and refuses every non-local
     * configuration, so a stale local event cannot make the setup script
     * target an arbitrary live server or a player-selected block.
     */
    private void setCoreAt(CommandSender sender, String[] args) {
        if (!isConsoleSetupSender(sender)) {
            message(sender, "&cТочная привязка Core доступна только локальной консоли.");
            return;
        }
        if (!"local".equalsIgnoreCase(config.environment())) {
            message(sender, "&cТочная привязка Core разрешена только при environment: local.");
            return;
        }
        if (args.length < 6) {
            message(sender, "&e/cmend core setat <x> <y> <z> <N>");
            return;
        }
        int x;
        int y;
        int z;
        int players;
        try {
            x = Integer.parseInt(args[2]);
            y = Integer.parseInt(args[3]);
            z = Integer.parseInt(args[4]);
            players = Integer.parseInt(args[5]);
        } catch (NumberFormatException invalid) {
            message(sender, "&cКоординаты и количество игроков должны быть целыми числами.");
            return;
        }
        if (players < config.minPlayers() || players > config.maxPlayers()) {
            message(sender, "&cТребуется число игроков от " + config.minPlayers() + " до " + config.maxPlayers() + ".");
            return;
        }
        if (isConfigured() && phase != EventPhase.UNCONFIGURED) {
            message(sender, "&cCore уже настроен. Сначала используй /cmend core remove confirm.");
            return;
        }
        World world = Bukkit.getWorld(config.arenaWorld());
        if (world == null) {
            message(sender, "&cEvent world не загружен: " + config.arenaWorld());
            return;
        }
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            message(sender, "&cКоордината Y вне границ мира.");
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType().isAir() || block.isPassable() || block.isLiquid()) {
            message(sender, "&cТочный Core должен быть установлен на реальном твёрдом блоке.");
            return;
        }
        configureCore(sender, block, players);
    }

    private void configureCore(CommandSender feedback, Block block, int players) {
        World targetWorld = block.getWorld();
        String originalBlockData = block.getBlockData().getAsString();
        boolean endWasAlreadyUnlocked = endUnlocked;
        EventSnapshot previousSnapshot = snapshot();
        EventLayoutState previousLayout = layoutState;
        eventId = UUID.randomUUID().toString();
        generation = Math.max(1L, generation + 1L);
        worldName = targetWorld.getName();
        coreX = block.getX();
        coreY = block.getY();
        coreZ = block.getZ();
        coreBlockData = originalBlockData;
        requiredPlayers = players;
        arenaMinX = coreX - (int) Math.ceil(config.arenaRadius());
        arenaMaxX = coreX + (int) Math.ceil(config.arenaRadius());
        int verticalRadius = (int) Math.ceil(config.arenaVerticalRadius());
        arenaMinY = Math.max(targetWorld.getMinHeight(), coreY - verticalRadius);
        arenaMaxY = Math.min(targetWorld.getMaxHeight() - 1, coreY + verticalRadius);
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
        waveRewardsIssued.clear();
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
        runeVisualOccupants.clear();
        stateMachine = new EndEventStateMachine(EventPhase.UNCONFIGURED);
        phase = EventPhase.UNCONFIGURED;
        taskRegistry = new EventTaskRegistry(generation);
        try {
            calculateAndPlacePads(targetWorld);
        } catch (RuntimeException invalidLayout) {
            restoreCoreAndPads();
            restoreBlock(block, originalBlockData);
            applySnapshot(previousSnapshot);
            layoutState = previousLayout;
            message(feedback, "&cCore не создан: layout рун не прошёл bounded-проверки; мир восстановлен.");
            getLogger().log(Level.WARNING, "Rift Core pad preflight rejected", invalidLayout);
            return;
        }
        if (!saveStateSync()) {
            restoreCoreAndPads();
            restoreBlock(block, originalBlockData);
            applySnapshot(previousSnapshot);
            layoutState = previousLayout;
            message(feedback, "&cСостояние не удалось durable-сохранить; мир оставлен без Core.");
            return;
        }
        forcePhase(EventPhase.COLLECTING, "core configured");
        rebuildPersistedVisuals();
        if (feedback instanceof Player player) {
            showArenaBoundary(player, DEFAULT_ARENA_PREVIEW_SECONDS);
        }
        message(feedback, "&aRift Core настроен: &f" + players + " игроков, event=" + eventId);
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
        waveRewardsIssued.clear();
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
                || phase == EventPhase.INTERMISSION_2 || phase == EventPhase.WAVE_3
                || phase == EventPhase.INTERMISSION_3 || phase == EventPhase.WAVE_4
                || phase == EventPhase.INTERMISSION_4 || phase == EventPhase.WAVE_5
                || phase == EventPhase.BOSS_ACTIVE
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
        waveRewardsIssued.clear();
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

    private Location coreCombatAnchorLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        // setCore() deliberately replaces whichever solid block the operator
        // targeted.  That block may itself have been the arena floor, so
        // coreY is not a reliable entity-feet level.  The persisted rune
        // coordinates are the authoritative playable floor; using their most
        // common Y keeps both a floor Core and an elevated Core compatible.
        return new Location(world, coreX + 0.5D, combatLevelY(), coreZ + 0.5D);
    }

    private int combatLevelY() {
        if (pads.isEmpty()) {
            // Before pad layout is persisted, the only safe default for an
            // arbitrary targeted block is its top face.  Normal event ticks
            // always run after pads have been calculated or loaded.
            return coreY + 1;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (EventSnapshot.PadSnapshot pad : pads) {
            counts.merge(pad.y(), 1, Integer::sum);
        }
        int selected = coreY + 1;
        int selectedCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int y = entry.getKey();
            int count = entry.getValue();
            if (count > selectedCount
                    || count == selectedCount && Math.abs(y - coreY) < Math.abs(selected - coreY)) {
                selected = y;
                selectedCount = count;
            }
        }
        return selected;
    }

    /** The solid block immediately below the feet-level used by runes/mobs. */
    private int combatFloorY() {
        return combatLevelY() - 1;
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
    public void onArenaIgnite(BlockIgniteEvent event) {
        if (isProtectedEventLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArenaFireSpread(BlockSpreadEvent event) {
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
    public void onWaveRewardPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item item = event.getItem();
        if (item == null || !EVENT_KIND_WAVE_REWARD.equals(readString(item, keyKind))
                || !ownedByEvent(item, eventId)) {
            return;
        }
        PersistentDataContainer data = item.getPersistentDataContainer();
        boolean shared = data.getOrDefault(keyRewardShared, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        long expiresAt = data.getOrDefault(keyRewardExpiresAt, PersistentDataType.LONG, 0L);
        String ownerText = data.getOrDefault(keyRewardOwner, PersistentDataType.STRING, "");
        if (!shared && expiresAt > System.currentTimeMillis()
                && !player.getUniqueId().toString().equals(ownerText)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Эта награда временно предназначена другому участнику",
                    NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOwnedDisplayDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TextDisplay || entity instanceof ItemDisplay
                || entity instanceof BlockDisplay) {
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

    /**
     * Vanilla skeletons can acquire animals and other monsters as targets.
     * Event skeletons are explicitly player-only: every target transition is
     * checked here and the controller rechecks it before every shot.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEventSkeletonTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton) || !isEventSkeleton(skeleton)) {
            return;
        }
        LivingEntity target = event.getTarget();
        if (target == null) {
            skeleton.setTarget(null);
            return;
        }
        if (!(target instanceof Player player)
                || !SkeletonCombatPolicy.canTargetPlayersOnly("PLAYER", isCombatTarget(player))) {
            event.setCancelled(true);
            skeleton.setTarget(null);
            getLogger().info("SKELETON_TARGET_BLOCKED entity=" + skeleton.getUniqueId()
                    + " target=" + (target == null ? "none" : target.getType())
                    + " reason=PLAYER_ONLY");
            return;
        }
        skeleton.setTarget(player);
    }

    /** Tag every ordinary skeleton arrow so its trail and cleanup are bounded. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEventSkeletonShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton) || !isEventSkeleton(skeleton)
                || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        boolean miniBoss = isSkeletonMiniBoss(skeleton);
        SkeletonCombatPolicy.ArrowProfile profile = SkeletonCombatPolicy.arrowProfile(miniBoss);
        long now = System.currentTimeMillis();
        long nextAllowed = nextSkeletonArrowMillis.getOrDefault(skeleton.getUniqueId(), 0L);
        if (now < nextAllowed) {
            event.setCancelled(true);
            if (arrow.isValid() && !arrow.isDead()) {
                arrow.remove();
            }
            getLogger().info("SKELETON_ARROW_COOLDOWN_BLOCKED entity=" + skeleton.getUniqueId()
                    + " variant=" + (miniBoss ? "MINIBOSS" : "COMMON")
                    + " retry_ms=" + (nextAllowed - now)
                    + " cooldown_ticks=" + profile.cooldownTicks());
            return;
        }
        nextSkeletonArrowMillis.put(skeleton.getUniqueId(),
                now + profile.cooldownTicks() * 50L);
        tag(arrow, EVENT_KIND_PROJECTILE, readInt(skeleton, keyWave, 0), isOfficialEntity(skeleton));
        tagArrowSpell(arrow, miniBoss ? EndRiftAiPolicy.MiniBossSpell.ARROW_SALVO.id()
                : ARROW_SPELL_SKELETON);
        arrow.setDamage(profile.damage());
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setColor(miniBoss ? Color.fromRGB(244, 60, 255) : Color.fromRGB(228, 228, 198));
        trackEventArrow(arrow);
        getLogger().info("SKELETON_ARROW_SHOT entity=" + skeleton.getUniqueId()
                + " arrow=" + arrow.getUniqueId() + " variant=" + (miniBoss ? "MINIBOSS" : "COMMON")
                + " target=PLAYER_ONLY damage=" + profile.damage()
                + " trail=" + profile.particlePattern());
    }

    /** Ordinary skeleton arrows may hurt eligible players, never event mobs. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEventSkeletonArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)
                || !(arrow.getShooter() instanceof Skeleton skeleton)
                || !isEventSkeleton(skeleton) || !isEventArrow(arrow)) {
            return;
        }
        String spell = readString(arrow, keyArrowSpell);
        if (!ARROW_SPELL_SKELETON.equals(spell)) {
            onCustomEventArrowDamage(event, arrow, spell);
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !isCombatTarget(player)) {
            event.setCancelled(true);
            getLogger().info("SKELETON_ARROW_NON_PLAYER_BLOCKED arrow=" + arrow.getUniqueId()
                    + " target=" + event.getEntity().getType());
            cleanupEventArrow(arrow.getUniqueId());
            return;
        }
        getLogger().info("SKELETON_ARROW_PLAYER_HIT arrow=" + arrow.getUniqueId()
                + " shooter=" + skeleton.getUniqueId() + " target=" + player.getUniqueId()
                + " damage=" + event.getFinalDamage());
    }

    /** Boss and miniboss spell arrows use one authoritative player-only hit. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCustomEventArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !isEventArrow(arrow)) {
            return;
        }
        String spell = readString(arrow, keyArrowSpell);
        if (ARROW_SPELL_SKELETON.equals(spell)) {
            return;
        }
        onCustomEventArrowDamage(event, arrow, spell);
    }

    private void onCustomEventArrowDamage(EntityDamageByEntityEvent event, Arrow arrow, String spell) {
        boolean alreadyCancelled = event.isCancelled();
        if (alreadyCancelled) {
            cleanupEventArrow(arrow.getUniqueId());
            return;
        }
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player) || !isCombatTarget(player)) {
            cleanupEventArrow(arrow.getUniqueId());
            getLogger().info("EVENT_ARROW_NON_PLAYER_BLOCKED arrow=" + arrow.getUniqueId()
                    + " spell=" + spell + " target=" + event.getEntity().getType());
            return;
        }
        LivingEntity shooter = arrow.getShooter() instanceof LivingEntity living ? living : null;
        boolean miniBoss = EndRiftAiPolicy.MiniBossSpell.ARROW_SALVO.id().equals(spell);
        double damage = miniBoss ? SkeletonCombatPolicy.arrowProfile(true).damage()
                : BOSS_PROJECTILE_DAMAGE;
        if (shooter != null && shooter.isValid() && !shooter.isDead()) {
            player.damage(damage, shooter);
        } else {
            player.damage(damage);
        }
        int debuffTicks = miniBoss ? SLOWNESS_DEBUFF_TICKS : BOSS_PROJECTILE_DEBUFF_TICKS;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                debuffTicks, configuredDebuffAmplifier(), false, true, true));
        getLogger().info("EVENT_ARROW_PLAYER_HIT arrow=" + arrow.getUniqueId()
                + " spell=" + spell + " target=" + player.getUniqueId()
                + " damage=" + damage + " slowness_ticks=" + debuffTicks);
        cleanupEventArrow(arrow.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEventArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow && isEventArrow(arrow)) {
            cleanupEventArrow(arrow.getUniqueId());
        }
    }

    /** A tower mob may be distracted by the player who hit it, but only briefly. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTowerMobDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getDamager() instanceof Player attacker)
                || !isTowerDefenseMob(mob)
                || !isCombatTarget(attacker)) {
            return;
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + TOWER_PLAYER_AGGRO_MILLIS;
        towerAggroUntil.put(mob.getUniqueId(), expiresAt);
        mob.setTarget(attacker);
        getLogger().info("WAVE_TOWER_AGGRO entity=" + mob.getUniqueId()
                + " role=" + towerRole(mob) + " target=" + attacker.getUniqueId()
                + " duration_ms=" + TOWER_PLAYER_AGGRO_MILLIS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalWaveMobAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !(event.getDamager() instanceof LivingEntity attacker)
                || !isWaveCombatKind(readString(attacker, keyKind))) {
            return;
        }
        getLogger().info("WAVE_MOB_DAMAGE event=" + eventId
                + " attacker=" + attacker.getType() + ":" + attacker.getUniqueId()
                + " wave=" + readInt(attacker, keyWave, 0)
                + " victim=" + victim.getUniqueId()
                + " cause=" + event.getCause()
                + " final=" + event.getFinalDamage()
                + " cancelled=" + event.isCancelled());
        if (readInt(attacker, keyWave, 0) != 3) {
            return;
        }
        Vector push = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (push.lengthSquared() < 0.01D) {
            push = new Vector(0.0D, 0.0D, 1.0D);
        }
        // Knockback II-equivalent, capped so a lag spike cannot launch a
        // player through the arena boundary or into the Core.
        victim.setVelocity(push.normalize().multiply(0.55D).setY(0.24D));
    }

    /** Apply the stage profile to the boss's outgoing melee, not only its path. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossMeleeAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)
                || !(event.getEntity() instanceof Player victim)
                || bossUuid == null || !bossUuid.equals(attacker.getUniqueId())
                || !EVENT_KIND_BOSS.equals(readString(attacker, keyKind))
                || !isCombatTarget(victim)) {
            return;
        }
        boolean disposableTest = testCombatAiMode && isTestBoss(attacker);
        boolean active = phase == EventPhase.BOSS_ACTIVE
                || phase == EventPhase.BOSS_FINISH && finalDrainTriggered;
        if (!active && !disposableTest) {
            return;
        }
        BossStagePolicy.CombatProfile profile = currentBossCombatProfile();
        double stageBonus = profile.meleeDamageBonus();
        double empoweredBonus = absorptionAttackEmpowered
                ? profile.nextMeleeAttackBonus() : 0.0D;
        double totalBonus = stageBonus + empoweredBonus;
        if (totalBonus <= 0.0D) {
            return;
        }
        event.setDamage(Math.max(0.0D, event.getDamage()) + totalBonus);
        if (empoweredBonus > 0.0D) {
            absorptionAttackEmpowered = false;
            if (!disposableTest && !saveStateSync()) {
                // Do not re-arm a one-shot effect if its consumption could not
                // be made durable.  The hit has already been bounded and the
                // fight remains damageable; recovery will not duplicate it.
                getLogger().warning("BOSS_ABSORPTION_BUFF_CONSUME_UNPERSISTED event=" + eventId
                        + " boss=" + attacker.getUniqueId());
            }
            getLogger().info("BOSS_ABSORPTION_BUFF_CONSUMED event=" + eventId
                    + " boss=" + attacker.getUniqueId() + " target=" + victim.getUniqueId()
                    + " bonus=" + empoweredBonus);
        }
        getLogger().info("BOSS_MELEE_ATTACK event=" + eventId
                + " boss=" + attacker.getUniqueId() + " target=" + victim.getUniqueId()
                + " stage=" + bossStage + " stage_bonus=" + stageBonus
                + " empowered_bonus=" + empoweredBonus + " total=" + event.getDamage());
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
        boolean occupied = runeVisualOccupants.containsKey(padKey(pad));
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
        sampleRuntimeDiagnostics();
        expireControlEffects();
        updatePadOccupancy();
        updateCombatHelpers();
        tickWaveMobAi();
        tickMiniBosses();
        tickEventArrowProjectiles();
        refreshClientBindingsForOnlinePlayers();
        // Official boss phases are handled by the switch below.  The extra
        // call is only for the disposable local AI harness, which deliberately
        // keeps the official phase outside BOSS_ACTIVE.
        if (testCombatAiMode && liveBoss() != null
                && phase != EventPhase.BOSS_ACTIVE && phase != EventPhase.BOSS_FINISH
                && phase != EventPhase.BOSS_CINEMATIC) {
            tickBoss();
        }
        switch (phase) {
            case COUNTDOWN -> tickCountdown();
            case INTERMISSION_1, INTERMISSION_2, INTERMISSION_3, INTERMISSION_4 -> tickIntermission();
            case WAVE_1, WAVE_2, WAVE_3, WAVE_4, WAVE_5, FINAL_WAVE -> tickWaveCompletion();
            case BOSS_CINEMATIC -> tickBossCinematic();
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
        Map<String, UUID> previousVisualOccupants = new LinkedHashMap<>(runeVisualOccupants);
        Map<String, UUID> previousOccupants = new LinkedHashMap<>(padOccupants);
        runeVisualOccupants.clear();
        padOccupants.clear();
        if (!coreCharged) {
            if (!previousVisualOccupants.isEmpty() || !previousOccupants.isEmpty()) {
                refreshRuneOverlayVisuals();
            }
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (!previousVisualOccupants.isEmpty() || !previousOccupants.isEmpty()) {
                refreshRuneOverlayVisuals();
            }
            return;
        }
        // Visual occupancy accepts Creative operators but still rejects dead
        // and Spectator players.  This is presentation-only and cannot start
        // the ritual or enter the official reward roster.
        runeVisualOccupants.putAll(detectRuneOccupants(world, false));
        boolean visualChanged = !previousVisualOccupants.equals(runeVisualOccupants);
        if (visualChanged) {
            refreshRuneOverlayVisuals();
        }
        if (phase != EventPhase.READY_FOR_PLAYERS && phase != EventPhase.COUNTDOWN) {
            if (visualChanged) {
                getLogger().info("RUNE_OCCUPANCY_CHANGED event=" + eventId
                        + " visual=" + runeVisualOccupants.size() + " official=0");
            }
            return;
        }
        padOccupants.putAll(detectRuneOccupants(world, true));
        boolean officialChanged = !previousOccupants.equals(padOccupants);
        if (officialChanged) {
            refreshRuneOverlayVisuals();
        }
        if (visualChanged || officialChanged) {
            getLogger().info("RUNE_OCCUPANCY_CHANGED event=" + eventId
                    + " visual=" + runeVisualOccupants.size()
                    + " official=" + padOccupants.size());
        }
        if (padOccupants.size() == requiredPlayers) {
            beginCountdownIfReady();
        } else if (phase == EventPhase.COUNTDOWN) {
            cancelRitual("pad occupancy changed");
        }
    }

    private Map<String, UUID> detectRuneOccupants(World world, boolean officialRoster) {
        Map<String, UUID> occupants = new LinkedHashMap<>();
        Set<UUID> assigned = new HashSet<>();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(player -> player.getUniqueId().toString()));
        for (EventSnapshot.PadSnapshot pad : pads) {
            Location padLocation = new Location(world, pad.x() + 0.5D, pad.y(), pad.z() + 0.5D);
            Player closest = players.stream()
                    .filter(player -> player.getWorld().equals(world) && !player.isDead()
                            && player.getHealth() > 0.0D
                            && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                            && (!officialRoster || player.getGameMode() != org.bukkit.GameMode.CREATIVE)
                            && !assigned.contains(player.getUniqueId()))
                    .filter(player -> player.getLocation().distanceSquared(padLocation)
                            <= config.padOccupancyRadius() * config.padOccupancyRadius())
                    .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(padLocation)))
                    .orElse(null);
            if (closest != null) {
                assigned.add(closest.getUniqueId());
                occupants.put(padKey(pad), closest.getUniqueId());
                if (officialRoster) {
                    registerParticipant(closest);
                }
            }
        }
        return occupants;
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
        if (!coreCharged) {
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
            lastCountdownAnnouncement = config.countdownSeconds();
            lastCountdownTitleSecond = -1;
            announceEventTitle("§5РИТУАЛ НАЧАТ", "§dСоберите игроков на рунах — "
                    + config.countdownSeconds() + " сек.", true);
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
            // A title is a full-screen milestone, not a high-frequency status
            // channel.  Sending it every five ticks caused flicker and an
            // unnecessary packet stream for both players in the ritual.
            if (seconds != lastCountdownTitleSecond) {
                for (UUID playerUuid : padOccupants.values()) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        player.sendTitle("§5РИТУАЛ НАЧАТ", "§fДо открытия Разлома: "
                                + seconds + " сек.", 0, 20, 0);
                    }
                }
                lastCountdownTitleSecond = seconds;
            }
            if (seconds != lastCountdownAnnouncement) {
                if (seconds <= 5 || seconds % 10 == 0) {
                    announceEventTitle("§5РИТУАЛ НАЧАТ", "§fДо открытия Разлома: "
                            + seconds + " сек.", true);
                }
                lastCountdownAnnouncement = seconds;
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
            runeVisualOccupants.clear();
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
            lastCountdownAnnouncement = -1;
            lastCountdownTitleSecond = -1;
            padOccupants.clear();
            runeVisualOccupants.clear();
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
        boolean hasTestWave = false;
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            String kind = readString(entity, keyKind);
            if (!isWaveCombatKind(kind) || !isLiveOwnedEntity(entity.getUniqueId())) {
                continue;
            }
            ensureEventCombatAi(entity);
            hasTestWave |= !isOfficialEntity(entity);
        }
        // Test waves deliberately do not enter the official state machine, but
        // their event-owned mobs still need the exact same containment policy.
        enforceWaveMobContainment();
        if (!isCombatPhase()) {
            if (!hasTestWave) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        List<Player> candidates = activeLivingPlayers();
        List<UUID> candidateIds = candidates.stream()
                .map(Player::getUniqueId)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        boolean rotateTargets = now >= nextWaveTargetMillis;
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            String kind = readString(entity, keyKind);
            if (!(entity instanceof Mob mob) || !isWaveCombatKind(kind)
                    || !isLiveOwnedEntity(entity.getUniqueId())
                    || !isWaveAiCombatEntity(entity)) {
                continue;
            }
            boolean towerMob = isTowerDefenseMob(entity);
            boolean playerAggro = towerMob && hasTowerPlayerAggro(entity, now);
            Player currentTarget = mob.getTarget() instanceof Player player && isCombatTarget(player)
                    ? player : null;
            if (towerMob && !playerAggro) {
                Player alertTarget = findNearestCombatPlayer(entity, TOWER_PLAYER_ALERT_RADIUS);
                if (alertTarget != null) {
                    // A tower mob that reaches the Core while a player is
                    // already nearby must engage that player instead of
                    // silently free-casting its objective attack.  Refreshing
                    // the same bounded aggro window makes the decision
                    // readable to players and gives the objective a fair
                    // counterplay loop.
                    currentTarget = alertTarget;
                    playerAggro = true;
                    towerAggroUntil.put(entity.getUniqueId(), now + TOWER_PLAYER_AGGRO_MILLIS);
                    mob.setTarget(alertTarget);
                    getLogger().info("WAVE_TOWER_ALERT entity=" + entity.getUniqueId()
                            + " role=" + towerRole(entity) + " target=" + alertTarget.getUniqueId()
                            + " radius=" + TOWER_PLAYER_ALERT_RADIUS
                            + " duration_ms=" + TOWER_PLAYER_AGGRO_MILLIS);
                }
            }
            if (towerMob && currentTarget != null && !playerAggro) {
                // Tower mobs return to their Core job after the short player
                // aggro window instead of following a stale target forever.
                mob.setTarget(null);
                mob.getPathfinder().stopPathfinding();
                currentTarget = null;
            }
            if (mob.getTarget() != null && currentTarget == null) {
                // A player can leave the arena, disconnect, die, or be changed
                // by another plugin between controller ticks.  Clear both the
                // vanilla target and its navigation goal immediately instead
                // of waiting for the next 5-8 second rotation window.
                mob.setTarget(null);
                mob.getPathfinder().stopPathfinding();
                nextWavePathRequestMillis.remove(mob.getUniqueId());
                lastWavePathLogMillis.remove(mob.getUniqueId());
            }

            Player target = currentTarget;
            boolean keepTowerAggro = towerMob && playerAggro && target != null;
            int entityWave = readInt(entity, keyWave, activeWave);
            Player markedTarget = entityWave == 2 && waveTwoMarkedPlayerUuid != null
                    ? Bukkit.getPlayer(waveTwoMarkedPlayerUuid) : null;
            boolean markedTargetEligible = markedTarget != null && isCombatTarget(markedTarget);
            boolean markedTargetPriority = SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(
                    entityWave, entityWave == 2, markedTargetEligible);
            boolean markedTargetNeedsRefresh = markedTargetPriority
                    && (target == null || !target.getUniqueId().equals(markedTarget.getUniqueId()));
            if (!keepTowerAggro && (rotateTargets || target == null || markedTargetNeedsRefresh)
                    && !candidateIds.isEmpty()) {
                if (markedTargetPriority) {
                    target = markedTarget;
                    if (entity instanceof Skeleton) {
                        getLogger().info("WAVE_SKELETON_MARKED_TARGET entity=" + entity.getUniqueId()
                                + " target=" + target.getUniqueId() + " wave=" + entityWave
                                + " priority=immediate");
                    }
                } else {
                    UUID current = target == null ? null : target.getUniqueId();
                    EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                            candidateIds, current, List.of(), waveTargetCursor++);
                    target = choice.target() == null ? null : Bukkit.getPlayer(choice.target());
                }
                if (target != null && isCombatTarget(target)) {
                    if (!towerMob || playerAggro) {
                        mob.setTarget(target);
                    } else {
                        // The selected player is only a deterministic input
                        // for the controller.  Do not hand it to vanilla AI:
                        // until a player actually hits the tower mob, it must
                        // keep advancing toward its Core objective.
                        mob.setTarget(null);
                    }
                    getLogger().info("WAVE_AI_TARGET entity=" + entity.getUniqueId()
                            + " role=" + kind + " target=" + target.getUniqueId()
                            + " rotation=" + (markedTargetPriority ? "marked" : rotateTargets ? "scheduled" : "reacquired")
                            + " objective=" + (towerMob && !playerAggro ? "core" : "player"));
                } else {
                    mob.setTarget(null);
                    mob.getPathfinder().stopPathfinding();
                    target = null;
                }
            }
            if (target != null && isCombatTarget(target)) {
                boolean towerCoreJob = towerMob && !playerAggro;
                if (towerCoreJob) {
                    // The deterministic player choice above is only a
                    // diagnostic input for a tower mob.  Until a player hits
                    // it, a skeleton must hold its artillery ring around the
                    // Core instead of silently kiting toward that player.
                    if (mob instanceof Skeleton skeleton) {
                        maintainSkeletonTowerPosture(skeleton, kind, now);
                    } else {
                        maintainWaveMobPath(mob, null, kind, now);
                    }
                    continue;
                }
                // Re-request navigation on every controller tick.  The request
                // itself is throttled per mob, so a large wave gets reliable
                // movement without flooding Paper with path computations.
                if (mob instanceof Skeleton skeleton) {
                    maintainSkeletonCombatPosture(skeleton, target, kind, now);
                } else {
                    maintainWaveMobPath(mob, target, kind, now);
                }
            } else if (towerMob && !playerAggro) {
                // Tower mobs keep a Core objective even when there is no player
                // target.  Passing null is intentional: the path controller
                // resolves the role's safe attack ring without handing a fake
                // player target to vanilla AI.
                maintainWaveMobPath(mob, null, kind, now);
            }
        }
        if (rotateTargets && !candidateIds.isEmpty()) {
            nextWaveTargetMillis = now
                    + randomSeconds(config.bossTargetMinSeconds(), config.bossTargetMaxSeconds()) * 1000L;
        }
    }

    /**
     * Ranged skeletons keep a readable firing lane instead of pathing into
     * melee range. The destination is still resolved by the common collision,
     * height, Core and arena-bound checks, so this posture cannot escape the
     * event box or walk through hazards.
     */
    private void maintainSkeletonCombatPosture(Skeleton skeleton, Player target,
                                                String kind, long now) {
        if (skeleton == null || target == null || !isCombatTarget(target)
                || now < nextWavePathRequestMillis.getOrDefault(skeleton.getUniqueId(), 0L)) {
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        if (anchor == null) {
            return;
        }
        boolean miniBoss = isSkeletonMiniBoss(skeleton);
        int wave = readInt(skeleton, keyWave, 1);
        SkeletonCombatPolicy.WaveBehavior behavior = SkeletonCombatPolicy.behaviorForWave(wave, miniBoss);
        int maneuverCycle = (int) Math.floorMod(
                now / SKELETON_MANEUVER_CYCLE_MILLIS, 4L);
        int maneuverSlot = Math.floorMod(skeleton.getUniqueId().hashCode(), 4);
        SkeletonCombatPolicy.Maneuver maneuver = SkeletonCombatPolicy.maneuverForWave(
                wave, miniBoss, maneuverCycle, maneuverSlot);
        double distance = Math.sqrt(horizontalDistanceSquared(skeleton.getLocation(), target.getLocation()));
        double minimum = behavior.minimumRange();
        double maximum = behavior.maximumRange();
        if (distance >= minimum && distance <= maximum) {
            skeleton.getPathfinder().stopPathfinding();
            nextWavePathRequestMillis.put(skeleton.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            return;
        }
        Location destination = skeletonRangedDestination(anchor, skeleton, target,
                minimum + 2.0D, behavior, maneuver);
        if (destination == null) {
            nextWavePathRequestMillis.put(skeleton.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            return;
        }
        double speed = miniBoss ? 1.15D : 1.05D;
        boolean moved = skeleton.getPathfinder().moveTo(destination, speed);
        if (!moved) {
            moved = requestBoundedCombatMovement(skeleton, destination, speed, anchor,
                    boundedCombatRadius(config.containmentRadius()), MIN_WAVE_CORE_DISTANCE_BLOCKS,
                    "WAVE_SKELETON_PATH");
        }
        nextWavePathRequestMillis.put(skeleton.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
        if (moved) {
            long lastLog = lastWavePathLogMillis.getOrDefault(skeleton.getUniqueId(), 0L);
            if (now - lastLog >= 2_000L) {
                lastWavePathLogMillis.put(skeleton.getUniqueId(), now);
                getLogger().info("WAVE_SKELETON_KITE entity=" + skeleton.getUniqueId()
                        + " variant=" + (miniBoss ? "MINIBOSS" : "COMMON")
                        + " behavior=" + behavior.id()
                        + " target=" + target.getUniqueId() + " distance=" + String.format(Locale.ROOT, "%.2f", distance)
                        + " firing_lane=" + String.format(Locale.ROOT, "%.1f", minimum)
                        + "-" + String.format(Locale.ROOT, "%.1f", maximum)
                        + " maneuver=" + maneuver
                        + " destination=" + locationText(destination));
            }
        }
    }

    /** Hold the Wave IV artillery ring while the Core remains the objective. */
    private void maintainSkeletonTowerPosture(Skeleton skeleton, String kind, long now) {
        if (skeleton == null || !isEventSkeleton(skeleton)
                || now < nextWavePathRequestMillis.getOrDefault(skeleton.getUniqueId(), 0L)) {
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        if (anchor == null) {
            return;
        }
        Location destination = waveTacticalDestination(skeleton, null, kind, now);
        if (destination == null || isCoreBlockPosition(destination)) {
            nextWavePathRequestMillis.put(skeleton.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            return;
        }
        double speed = isSkeletonMiniBoss(skeleton) ? 1.15D : 1.05D;
        if (horizontalDistanceSquared(skeleton.getLocation(), destination) <= 4.0D) {
            skeleton.getPathfinder().stopPathfinding();
            nextWavePathRequestMillis.put(skeleton.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            return;
        }
        boolean moved = skeleton.getPathfinder().moveTo(destination, speed);
        if (!moved) {
            moved = requestBoundedCombatMovement(skeleton, destination, speed, anchor,
                    boundedCombatRadius(config.containmentRadius()), MIN_WAVE_CORE_DISTANCE_BLOCKS,
                    "WAVE_SKELETON_TOWER_PATH");
        }
        nextWavePathRequestMillis.put(skeleton.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
        if (moved) {
            long lastLog = lastWavePathLogMillis.getOrDefault(skeleton.getUniqueId(), 0L);
            if (now - lastLog >= 2_000L) {
                lastWavePathLogMillis.put(skeleton.getUniqueId(), now);
                getLogger().info("WAVE_SKELETON_TOWER entity=" + skeleton.getUniqueId()
                        + " variant=" + (isSkeletonMiniBoss(skeleton) ? "MINIBOSS" : "COMMON")
                        + " behavior=tower_artillery target=CORE_ONLY destination="
                        + locationText(destination));
            }
        }
    }

    private Location skeletonRangedDestination(Location anchor, Skeleton skeleton,
                                               Player target, double desiredDistance,
                                               SkeletonCombatPolicy.WaveBehavior behavior,
                                               SkeletonCombatPolicy.Maneuver maneuver) {
        if (anchor == null || skeleton == null || target == null) {
            return null;
        }
        Location tacticalTarget = target.getLocation();
        if (behavior != null && behavior.guardsObjective()
                && readInt(skeleton, keyWave, 0) == 3) {
            Location portal = nearestOpenPortal(skeleton.getLocation());
            if (portal != null && horizontalDistanceSquared(target.getLocation(), portal) > 36.0D) {
                // Keep a portal sentinel on a safe firing station around the
                // objective while its Mob target remains the player. This
                // prevents the skeleton from abandoning the portal whenever
                // somebody crosses the arena.
                tacticalTarget = portal;
            }
        }
        Vector radial = skeleton.getLocation().toVector().subtract(tacticalTarget.toVector());
        radial.setY(0.0D);
        if (radial.lengthSquared() < 0.04D) {
            radial = tacticalTarget.toVector().subtract(anchor.toVector());
            radial.setY(0.0D);
        }
        if (radial.lengthSquared() < 0.04D) {
            radial = new Vector(1.0D, 0.0D, 0.0D);
        }
        radial.normalize();
        Location preferred = tacticalTarget.clone().add(radial.multiply(desiredDistance));
        Vector side = new Vector(-radial.getZ(), 0.0D, radial.getX());
        long stableBits = skeleton.getUniqueId().getLeastSignificantBits();
        double sideSign = (stableBits & 1L) == 0L ? 1.0D : -1.0D;
        if (maneuver != null) {
            switch (maneuver) {
                case SIDE_STEP -> preferred.add(side.multiply(sideSign * 2.0D));
                case CROSS_FIRE -> preferred.add(side.multiply(-sideSign * 2.75D));
                case FALLBACK -> preferred.add(radial.multiply(2.0D));
                case HOLD_LINE -> { }
            }
        }
        return findSafeCombatLocation(anchor, preferred,
                boundedCombatRadius(config.containmentRadius()) - 1.0D,
                MIN_WAVE_CORE_DISTANCE_BLOCKS);
    }

    private Location nearestOpenPortal(Location from) {
        if (from == null || from.getWorld() == null) {
            return null;
        }
        List<Location> portals = wavePortals.getOrDefault(3, List.of());
        List<PortalCapturePolicy.PortalState> states = portalCaptureStates.getOrDefault(3, List.of());
        Location nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < portals.size(); index++) {
            Location portal = portals.get(index);
            if (portal == null || !from.getWorld().equals(portal.getWorld())
                    || index < states.size() && states.get(index).completed()) {
                continue;
            }
            double distance = horizontalDistanceSquared(from, portal);
            if (distance < nearestDistance) {
                nearest = portal;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void enforceWaveMobContainment() {
        Location anchor = coreCombatAnchorLocation();
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

    /**
     * Select a combat destination from the mob's stable job.  The target is
     * still authoritative, but each wave role gets a different battlefield
     * intention: breakers orbit the Core, artillery holds a rear ring, and
     * hunters attack from a moving flank.  The final location is always
     * passed through the same collision/height/Core safety resolver.
     */
    private Location waveTacticalDestination(Mob mob, Player target, String kind, long now) {
        Location anchor = coreCombatAnchorLocation();
        WaveMechanicsPolicy.TowerRole towerRole = towerRole(mob);
        boolean towerPlayerAggro = isTowerDefenseMob(mob) && hasTowerPlayerAggro(mob, now);
        boolean towerCoreJob = towerRole != null && isTowerDefenseMob(mob) && !towerPlayerAggro;
        if (anchor == null || mob == null
                || (!towerCoreJob && (target == null || !isCombatTarget(target)))) {
            return null;
        }
        CombatTacticsPolicy.MobTactic tactic = combatTactic(
                mob, Math.floorMod(mob.getUniqueId().hashCode(), 128));
        Location preferred;
        if (towerCoreJob) {
            // Every tower role has a reachable, safe Core stance.  The old
            // breaker route used a 5.5-8 block ring while its attack check
            // only accepted 2.25 blocks, making the role cosmetic rather than
            // functional.  The ranges live in the pure policy and are now
            // mirrored by updateTowerObjective().
            preferred = switch (towerRole) {
                case RAIDER -> stableCombatRingLocation(anchor, mob, 3.0D, 3.75D);
                case BREAKER -> stableCombatRingLocation(anchor, mob, 3.0D, 4.0D);
                case ARTILLERY -> stableCombatRingLocation(anchor, mob, 9.0D, 12.0D);
            };
        } else {
            switch (tactic) {
                case CORE_BREAKER -> preferred = stableCombatRingLocation(anchor, mob, 3.0D, 4.0D);
                case ARTILLERY_SCREEN -> preferred = stableCombatRingLocation(anchor, mob, 9.0D, 12.0D);
                case PORTAL_GUARD -> preferred = isCoreBlockPosition(target.getLocation())
                        ? stableCombatRingLocation(anchor, mob, 6.0D, 9.0D) : target.getLocation();
                case STORM_HUNTER, ELITE_HUNTER -> preferred = flankTargetLocation(anchor, mob, target);
                case MARKED_HUNTER, RAIDER_RUSH, ASSAULT -> preferred = target.getLocation();
                default -> preferred = target.getLocation();
            }
        }
        if (preferred == null) {
            return null;
        }
        String maneuverRole = towerRole != null ? towerRole.name()
                : (EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_FINAL_WAVE.equals(kind)
                ? "ELITE" : "MOB");
        int maneuverWave = Math.max(1, readInt(mob, keyWave, activeWave));
        int maneuverCycle = (int) Math.floorMod(
                now / SKELETON_MANEUVER_CYCLE_MILLIS, 4L);
        int maneuverSlot = Math.floorMod(mob.getUniqueId().hashCode(), 4);
        CombatTacticsPolicy.MobManeuver maneuver = CombatTacticsPolicy.waveManeuver(
                maneuverWave, maneuverRole, maneuverCycle, maneuverSlot);
        preferred = applyMobManeuver(preferred, anchor, mob, target, maneuver);
        Location destination = findSafeCombatLocation(anchor, preferred,
                boundedCombatRadius(config.containmentRadius()) - 1.0D,
                MIN_WAVE_CORE_DISTANCE_BLOCKS);
        if (destination != null) {
            getLogger().fine("WAVE_AI_TACTIC_DESTINATION entity=" + mob.getUniqueId()
                    + " role=" + kind + " tactic=" + tactic
                    + " maneuver=" + maneuver
                    + " destination=" + locationText(destination));
        }
        return destination;
    }

    /**
     * Apply one small lateral or retreat beat before the common safety
     * resolver.  The offset is intentionally tiny and deterministic: it
     * gives players a readable dodge window without making a mob teleport or
     * turning pathfinding into an expensive steering simulation.
     */
    private Location applyMobManeuver(Location preferred, Location anchor, Mob mob,
                                      Player target, CombatTacticsPolicy.MobManeuver maneuver) {
        if (preferred == null || anchor == null || mob == null || maneuver == null
                || maneuver == CombatTacticsPolicy.MobManeuver.HOLD_LINE) {
            return preferred;
        }
        Location origin = target == null ? anchor : target.getLocation();
        Vector away = preferred.toVector().subtract(origin.toVector());
        away.setY(0.0D);
        if (away.lengthSquared() < 0.04D) {
            away = mob.getLocation().toVector().subtract(origin.toVector());
            away.setY(0.0D);
        }
        if (away.lengthSquared() < 0.04D) {
            away = new Vector(1.0D, 0.0D, 0.0D);
        }
        away.normalize();
        Vector side = new Vector(-away.getZ(), 0.0D, away.getX());
        long stableBits = mob.getUniqueId().getLeastSignificantBits();
        double sideSign = (stableBits & 1L) == 0L ? 1.0D : -1.0D;
        Location moved = preferred.clone();
        switch (maneuver) {
            case SIDE_STEP -> moved.add(side.multiply(sideSign * 1.5D));
            case CROSS_FIRE -> moved.add(side.multiply(-sideSign * 2.25D));
            case FALLBACK -> moved.add(away.multiply(1.5D));
            case PINCH -> moved.add(side.multiply(sideSign * 1.25D));
            case HOLD_LINE -> { }
        }
        return moved;
    }

    private Location stableCombatRingLocation(Location anchor, Entity entity,
                                               double minimum, double maximum) {
        if (anchor == null || entity == null) {
            return null;
        }
        long bits = entity.getUniqueId().getMostSignificantBits()
                ^ Long.rotateLeft(entity.getUniqueId().getLeastSignificantBits(), 17);
        double angle = Math.floorMod((int) (bits ^ (bits >>> 32)), 3600) / 3600.0D
                * Math.PI * 2.0D;
        double distance = minimum + Math.floorMod((int) (bits >>> 19), 1000) / 1000.0D
                * Math.max(0.0D, maximum - minimum);
        return anchor.clone().add(Math.cos(angle) * distance, 0.0D,
                Math.sin(angle) * distance);
    }

    private Location flankTargetLocation(Location anchor, Mob mob, Player target) {
        if (anchor == null || mob == null || target == null) {
            return null;
        }
        Location targetLocation = target.getLocation();
        if (isCoreBlockPosition(targetLocation)) {
            return stableCombatRingLocation(anchor, mob, 6.0D, 9.0D);
        }
        Vector radial = targetLocation.toVector().subtract(anchor.toVector());
        radial.setY(0.0D);
        if (radial.lengthSquared() < 0.01D) {
            radial = new Vector(1.0D, 0.0D, 0.0D);
        }
        radial.normalize();
        Vector side = new Vector(-radial.getZ(), 0.0D, radial.getX());
        long bits = mob.getUniqueId().getLeastSignificantBits();
        double direction = (bits & 1L) == 0L ? 1.0D : -1.0D;
        double distance = 2.75D + Math.floorMod((int) (bits >>> 13), 3) * 0.65D;
        return targetLocation.clone().add(side.multiply(direction * distance));
    }

    /**
     * Endermen and skeletons do not reliably start useful navigation from a
     * Bukkit target assignment alone. Request a bounded path for mobile wave
     * mobs so the target controller produces visible movement as well as combat
     * intent. Skeletons use the ranged posture below and never receive a mob
     * target.
     */
    private void maintainWaveMobPath(Mob mob, Player target, String kind, long now) {
        boolean towerJob = mob != null && isTowerDefenseMob(mob)
                && !hasTowerPlayerAggro(mob, now);
        if (mob == null || (!towerJob && (target == null || !isCombatTarget(target)))
                || now < nextWavePathRequestMillis.getOrDefault(mob.getUniqueId(), 0L)) {
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        if (anchor == null) {
            return;
        }
        Location destination = waveTacticalDestination(mob, target, kind, now);
        if (destination == null || isCoreBlockPosition(destination)) {
            // A player standing on the Core is a valid combat target, but it
            // is not a valid mob destination.  Give each mob a stable flank
            // so a whole wave surrounds the Core instead of collapsing onto
            // one fallback cell or repeatedly pushing against the block.
            destination = findSafeCombatLocation(anchor, waveCoreFlankDestination(anchor, mob),
                    boundedCombatRadius(config.containmentRadius()) - 1.0D,
                    MIN_WAVE_CORE_DISTANCE_BLOCKS);
        }
        if (destination == null
                || horizontalDistanceSquared(destination, anchor)
                > boundedCombatRadius(config.containmentRadius()) * boundedCombatRadius(config.containmentRadius())
                || outsideCombatVertical(destination, anchor)) {
            mob.setTarget(null);
            nextWavePathRequestMillis.put(mob.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            return;
        }
        if (horizontalDistanceSquared(mob.getLocation(), destination) <= 4.0D) {
            mob.getPathfinder().stopPathfinding();
            nextWavePathRequestMillis.put(mob.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            return;
        }
        double speed = mob.getType() == EntityType.SPIDER
                ? 1.20D : EVENT_KIND_ELITE.equals(kind) ? 1.15D : 1.05D;
        boolean moved = mob.getPathfinder().moveTo(destination, speed);
        if (!moved) {
            moved = requestBoundedCombatMovement(mob, destination, speed, anchor,
                    boundedCombatRadius(config.containmentRadius()), MIN_WAVE_CORE_DISTANCE_BLOCKS,
                    "WAVE_AI_PATH");
        }
        if (moved) {
            nextWavePathRequestMillis.put(mob.getUniqueId(), now + WAVE_PATH_REQUEST_INTERVAL_MILLIS);
            long lastLog = lastWavePathLogMillis.getOrDefault(mob.getUniqueId(), 0L);
            if (now - lastLog >= 2_000L) {
                lastWavePathLogMillis.put(mob.getUniqueId(), now);
                getLogger().info("WAVE_AI_PATH entity=" + mob.getUniqueId()
                        + " role=" + kind + " target=" + (target == null ? "core" : target.getUniqueId())
                        + " tactic=" + combatTactic(mob, mob.getUniqueId().hashCode())
                        + " speed=" + speed + " destination=" + locationText(destination));
            }
        }
    }

    /**
     * Apply one collision-checked horizontal nudge when Paper cannot produce a
     * path.  This is deliberately not a teleport and never changes Y, so a
     * transient navigation failure cannot make a mob fly through the arena.
     */
    private boolean requestBoundedCombatMovement(Mob mob, Location destination, double speed,
                                                 Location anchor, double radius,
                                                 double minimumCoreDistance, String logMarker) {
        if (mob == null || destination == null || anchor == null
                || !mob.isValid() || mob.isDead()) {
            return false;
        }
        Location current = mob.getLocation();
        CombatMovementPolicy.Step step = CombatMovementPolicy.stepTowards(
                current.getX(), current.getY(), current.getZ(),
                destination.getX(), destination.getY(), destination.getZ(), speed);
        if (step.equals(CombatMovementPolicy.Step.ZERO)) {
            return false;
        }
        if (step.horizontalLength() > MAX_COMBAT_STEP_BLOCKS + 0.0001D) {
            return false;
        }
        Location next = current.clone().add(step.x(), step.y(), step.z());
        if (!isSafeCombatStep(anchor, next, radius, minimumCoreDistance)) {
            return false;
        }
        Vector velocity = mob.getVelocity().clone();
        double vertical = velocity.getY();
        if (!Double.isFinite(vertical)) {
            vertical = 0.0D;
        }
        velocity.setX(step.x()).setY(Math.max(-0.20D, Math.min(0.20D, vertical))).setZ(step.z());
        mob.setVelocity(velocity);
        getLogger().info(logMarker + " entity=" + mob.getUniqueId()
                + " fallback=velocity step=" + String.format(Locale.ROOT, "%.3f,%.3f", step.x(), step.z())
                + " destination=" + locationText(destination));
        return true;
    }

    private boolean isSafeCombatStep(Location anchor, Location candidate,
                                     double radius, double minimumCoreDistance) {
        if (anchor == null || candidate == null || candidate.getWorld() == null
                || !anchor.getWorld().equals(candidate.getWorld())
                || !CombatMovementPolicy.withinBounds(anchor.getX(), anchor.getY(), anchor.getZ(),
                candidate.getX(), candidate.getY(), candidate.getZ(), radius,
                configuredCombatVerticalRadius(), minimumCoreDistance)) {
            return false;
        }
        Block feet = candidate.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        return feet.isPassable() && head.isPassable() && floor.getType().isSolid()
                && !feet.isLiquid() && !head.isLiquid() && !floor.isLiquid()
                && !isFireBlock(feet) && !isFireBlock(head) && !isFireBlock(floor)
                && !isWebBlock(feet) && !isWebBlock(head) && !isWebBlock(floor)
                && !isTemporaryMovementHazard(feet) && !isTemporaryMovementHazard(head)
                && !isTemporaryMovementHazard(floor) && !isCoreBlockPosition(candidate);
    }

    private Location waveCoreFlankDestination(Location anchor, Mob mob) {
        if (anchor == null || mob == null || mob.getUniqueId() == null) {
            return null;
        }
        long bits = mob.getUniqueId().getMostSignificantBits()
                ^ Long.rotateLeft(mob.getUniqueId().getLeastSignificantBits(), 21);
        double angle = Math.floorMod((int) (bits ^ (bits >>> 32)), 3600) / 3600.0D
                * Math.PI * 2.0D;
        Vector radial = new Vector(Math.cos(angle), 0.0D, Math.sin(angle));
        double ring = MIN_WAVE_CORE_DISTANCE_BLOCKS + 1.0D
                + Math.floorMod((int) (bits >>> 17), 4) * 1.25D;
        double maximum = Math.max(MIN_WAVE_CORE_DISTANCE_BLOCKS + 0.5D,
                boundedCombatRadius(config.containmentRadius()) - 2.0D);
        return anchor.clone().add(radial.multiply(Math.min(ring, maximum)));
    }

    private boolean isWaveCombatKind(String kind) {
        return EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind)
                || EVENT_KIND_FINAL_WAVE.equals(kind);
    }

    private boolean isEventSkeleton(Entity entity) {
        return entity instanceof Skeleton
                && isWaveCombatKind(readString(entity, keyKind));
    }

    private boolean isSkeletonMiniBoss(Entity entity) {
        return isEventSkeleton(entity) && !readString(entity, keyMiniBossSpell).isBlank();
    }

    private boolean isEventArrow(Entity entity) {
        return entity instanceof Arrow
                && EVENT_KIND_PROJECTILE.equals(readString(entity, keyKind))
                && activeEventArrowAges.containsKey(entity.getUniqueId());
    }

    private void tagArrowSpell(Arrow arrow, String spellId) {
        if (arrow != null && keyArrowSpell != null && spellId != null && !spellId.isBlank()) {
            arrow.getPersistentDataContainer().set(keyArrowSpell,
                    PersistentDataType.STRING, spellId);
        }
    }

    private void trackEventArrow(Arrow arrow) {
        if (arrow == null || !arrow.isValid()) {
            return;
        }
        while (activeEventArrowAges.size() >= MAX_ACTIVE_EVENT_ARROWS) {
            UUID oldest = activeEventArrowAges.keySet().iterator().next();
            cleanupEventArrow(oldest);
        }
        activeEventArrowAges.put(arrow.getUniqueId(), 0);
    }

    /** One generation-owned controller renders and expires all event arrows. */
    private void tickEventArrowProjectiles() {
        if (activeEventArrowAges.isEmpty()) {
            return;
        }
        int processed = 0;
        for (UUID arrowId : new ArrayList<>(activeEventArrowAges.keySet())) {
            if (++processed > MAX_ACTIVE_EVENT_ARROWS) {
                break;
            }
            Entity entity = Bukkit.getEntity(arrowId);
            if (!(entity instanceof Arrow arrow) || !arrow.isValid() || arrow.isDead()
                    || !isEventArrowPhaseAllowed(arrow)) {
                cleanupEventArrow(arrowId);
                continue;
            }
            int age = activeEventArrowAges.getOrDefault(arrowId, 0) + 1;
            if (age >= EVENT_ARROW_MAX_TICKS) {
                cleanupEventArrow(arrowId);
                continue;
            }
            activeEventArrowAges.put(arrowId, age);
            if (age % EVENT_ARROW_TRAIL_INTERVAL_TICKS == 0) {
                spawnEventArrowTrail(arrow, readString(arrow, keyArrowSpell), age);
            }
        }
    }

    private boolean isEventArrowPhaseAllowed(Arrow arrow) {
        if (arrow == null || !(arrow.getShooter() instanceof Entity shooter)) {
            return false;
        }
        String kind = readString(shooter, keyKind);
        if (EVENT_KIND_BOSS.equals(kind)) {
            return phase == EventPhase.BOSS_ACTIVE
                    || testCombatAiMode && isTestBoss(shooter);
        }
        return isEventSkeleton(shooter)
                && (isCombatPhase() || testCombatAiMode || hasLiveTestWaveEntities());
    }

    private void spawnEventArrowTrail(Arrow arrow, String spell, int age) {
        if (arrow == null || arrow.getWorld() == null) {
            return;
        }
        Location point = arrow.getLocation().clone().add(0.0D, 0.04D, 0.0D);
        for (Player viewer : eventAudience()) {
            if (!isEventParticleViewer(viewer, point)) {
                continue;
            }
            if (PACKET_QUALITY_MINIMAL_SAFE.equals(runtimeDiagnosticsSnapshot.packetQualityMode())
                    && age % 4 != 0) {
                continue;
            }
            recordParticleEmission(switch (spell) {
                case "arrow_salvo" -> 4;
                case "rift_arrows" -> 4;
                default -> 2;
            });
            if (EndRiftAiPolicy.MiniBossSpell.ARROW_SALVO.id().equals(spell)) {
                viewer.spawnParticle(Particle.END_ROD, point, 2,
                        0.04D, 0.04D, 0.04D, 0.01D);
                viewer.spawnParticle(Particle.DUST, point, 2,
                        0.03D, 0.03D, 0.03D, 0.0D,
                        new Particle.DustOptions(Color.fromRGB(244, 60, 255), 1.0F));
            } else if (EndRiftAiPolicy.BossSpell.RIFT_ARROWS.id().equals(spell)) {
                viewer.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 2,
                        0.04D, 0.04D, 0.04D, 0.005D);
                viewer.spawnParticle(Particle.CRIT, point, 2,
                        0.03D, 0.03D, 0.03D, 0.01D);
            } else {
                viewer.spawnParticle(Particle.CRIT, point, 2,
                        0.035D, 0.035D, 0.035D, 0.01D);
            }
        }
    }

    private void cleanupEventArrow(UUID arrowId) {
        if (arrowId == null) {
            return;
        }
        activeEventArrowAges.remove(arrowId);
        Entity arrow = ownedEntities.remove(arrowId);
        if (arrow != null && arrow.isValid() && !arrow.isDead()) {
            arrow.remove();
        }
    }

    private void clearActiveEventArrows() {
        for (UUID arrowId : new ArrayList<>(activeEventArrowAges.keySet())) {
            cleanupEventArrow(arrowId);
        }
        activeEventArrowAges.clear();
    }

    /**
     * The official boss death is the hard boundary for every wave combat
     * entity.  Do this synchronously before the boss EntityDeathEvent is
     * emitted: a delayed victory cleanup used to leave final-wave mobs and
     * boss servants visible for one or more status ticks after victory.
     * Wave reward item entities are deliberately excluded so already-created
     * loot remains recoverable during the durable victory saga.
     */
    private int clearWaveCombatEntities(String reason) {
        Set<UUID> combatIds = new LinkedHashSet<>();
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            if (isWaveCombatKind(readString(entity, keyKind))) {
                combatIds.add(entity.getUniqueId());
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isWaveCombatKind(readString(entity, keyKind))
                        && ownedByEvent(entity, eventId)) {
                    combatIds.add(entity.getUniqueId());
                }
            }
        }
        int removed = 0;
        for (UUID entityId : combatIds) {
            Entity entity = ownedEntities.get(entityId);
            if (entity == null) {
                entity = Bukkit.getEntity(entityId);
            }
            if (entity != null && isWaveCombatKind(readString(entity, keyKind))) {
                unbindEventEntityClient(entityId);
                if (entity.isValid() && !entity.isDead()) {
                    entity.remove();
                    removed++;
                }
            }
            ownedEntities.remove(entityId);
            finalWaveEntities.remove(entityId);
            spellServants.remove(entityId);
            miniBossSpells.remove(entityId);
            nextMiniBossSpellMillis.remove(entityId);
            nextSkeletonArrowMillis.remove(entityId);
            nextWavePathRequestMillis.remove(entityId);
            lastWavePathLogMillis.remove(entityId);
            waveMobTactics.remove(entityId);
            combatTeleportPermits.remove(entityId);
            blockedTeleportLogAt.remove(entityId);
        }
        finalWaveEntities.clear();
        spellServants.clear();
        miniBossSpells.clear();
        nextMiniBossSpellMillis.clear();
        nextSkeletonArrowMillis.clear();
        nextWavePathRequestMillis.clear();
        lastWavePathLogMillis.clear();
        waveMobTactics.clear();
        combatTeleportPermits.clear();
        blockedTeleportLogAt.clear();
        towerNextAttackAt.clear();
        towerAttackSequences.clear();
        towerAggroUntil.clear();
        towerAttackSequence = 0;
        getLogger().info("END_EVENT_WAVE_COMBAT_CLEANUP event=" + eventId
                + " reason=" + reason + " removed=" + removed);
        return removed;
    }

    /**
     * AI is a runtime invariant for event combat entities.  A stale local
     * harness, another plugin, or a recovered entity may leave NoAI enabled;
     * restoring it here prevents an otherwise valid wave from becoming a
     * motionless and apparently un-hittable display.
     */
    private void ensureEventCombatAi(Entity entity) {
        if (entity == null) {
            return;
        }
        String kind = readString(entity, keyKind);
        if (!EVENT_KIND_BOSS.equals(kind) && !isWaveCombatKind(kind)) {
            return;
        }
        if (entity instanceof Mob mob) {
            boolean wasDisabled = !mob.hasAI();
            mob.setAI(true);
            mob.setAware(true);
            if (wasDisabled) {
                getLogger().info("EVENT_AI_RESTORED entity=" + entity.getUniqueId()
                        + " kind=" + kind + " phase=" + phase);
            }
        }
    }

    /** Each elite owns exactly one spell; there is no shared repeating task per mob. */
    private void tickMiniBosses() {
        if (!isMiniBossCombatPhase()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, EndRiftAiPolicy.MiniBossSpell> entry : new HashMap<>(miniBossSpells).entrySet()) {
            Entity entity = ownedEntities.get(entry.getKey());
            if (!(entity instanceof LivingEntity miniBoss) || !isLiveOwnedEntity(entity.getUniqueId())
                    || !isMiniBossCombatEntity(miniBoss)) {
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
            Player current = miniBoss instanceof Mob mob && mob.getTarget() instanceof Player player
                    && isCombatTarget(player)
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
            if (miniBoss instanceof Mob mob) {
                mob.setTarget(target);
            }
            nextMiniBossSpellMillis.put(entity.getUniqueId(), now
                    + randomSeconds(config.miniBossTuning().spellMinSeconds(), config.miniBossTuning().spellMaxSeconds()) * 1000L);
            telegraphMiniBossSpell(miniBoss, target, entry.getValue());
        }
    }

    private void telegraphMiniBossSpell(LivingEntity miniBoss, Player target, EndRiftAiPolicy.MiniBossSpell spell) {
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
            Particle particle = switch (spell) {
                case VOID_SNARE -> Particle.REVERSE_PORTAL;
                case ARROW_SALVO -> Particle.CRIT;
                default -> Particle.END_ROD;
            };
            spawnEventParticle(effect, particle, 8, 0.65D, 0.15D, 0.65D, 0.01D);
            renderSpellTelegraphVisual(miniBoss, mark, spell.id(), ticks[0],
                    config.miniBossTuning().spellTelegraphTicks());
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

    private void executeMiniBossSpell(LivingEntity miniBoss, Player target, Location mark,
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
                    renderSpellImpactVisual(miniBoss,
                            spell == EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE
                                    ? miniBoss.getLocation() : mark,
                            spell.id());
                    switch (spell) {
                        case RIFT_STEP -> miniBossRiftStep(miniBoss, target);
                        case VOID_SNARE -> miniBossVoidSnare(miniBoss, target, mark);
                        case ECHO_PULSE -> miniBossEchoPulse(miniBoss);
                        case ARROW_SALVO -> miniBossArrowSalvo(miniBoss, target);
                    }
                });
    }

    /** Render one short impact burst; unlike the telegraph this is one bounded
     * batch per cast, so a busy arena cannot accumulate visual tasks. */
    private void renderSpellImpactVisual(LivingEntity caster, Location mark, String spellId) {
        SpellVisualPolicy.VisualProfile profile = SpellVisualPolicy.profile(spellId);
        if (caster == null || mark == null || profile == null || caster.getWorld() == null
                || mark.getWorld() == null || !caster.getWorld().equals(mark.getWorld())) {
            return;
        }
        Location center = mark.clone().add(0.0D, 0.12D, 0.0D);
        Location origin = caster.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Vector forward = center.toVector().subtract(origin.toVector());
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        forward.normalize();
        Vector horizontal = forward.clone();
        horizontal.setY(0.0D);
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = new Vector(1.0D, 0.0D, 0.0D);
        }
        horizontal.normalize();
        Vector side = new Vector(-horizontal.getZ(), 0.0D, horizontal.getX()).normalize();
        Vector up = new Vector(0.0D, 1.0D, 0.0D);
        Particle.DustOptions dust = new Particle.DustOptions(
                Color.fromRGB(190, 76, 255), 1.25F);
        for (Player viewer : eventAudience()) {
            if (!isEventParticleViewer(viewer, center)) {
                continue;
            }
            switch (spellId) {
                case "void_blast" -> {
                    spawnPatternRing(viewer, center, side, horizontal, 0.85D, 24,
                            0.0D, Particle.DRAGON_BREATH);
                    spawnPatternRing(viewer, center, side, horizontal, 1.65D, 32,
                            0.25D, Particle.FLAME);
                }
                case "rift_projectile" -> {
                    spawnPatternRing(viewer, center, side, up, 0.48D, 18,
                            0.0D, Particle.REVERSE_PORTAL);
                    viewer.spawnParticle(Particle.END_ROD, center, 12,
                            0.18D, 0.18D, 0.18D, 0.02D);
                }
                case "rift_arrows", "arrow_salvo" -> {
                    Particle trail = "arrow_salvo".equals(spellId)
                            ? Particle.CRIT : Particle.SOUL_FIRE_FLAME;
                    for (int lane = -1; lane <= 1; lane++) {
                        Vector offset = side.clone().multiply(lane * 0.28D);
                        spawnPatternSegment(viewer, origin.clone().add(offset),
                                center.clone().add(offset), trail, dust);
                    }
                }
                case "void_mark" -> {
                    Location[] corners = new Location[4];
                    double radius = 1.45D;
                    for (int index = 0; index < corners.length; index++) {
                        double angle = Math.PI / 4.0D + index * Math.PI / 2.0D;
                        corners[index] = center.clone()
                                .add(side.clone().multiply(Math.cos(angle) * radius))
                                .add(horizontal.clone().multiply(Math.sin(angle) * radius));
                    }
                    for (int index = 0; index < corners.length; index++) {
                        spawnPatternSegment(viewer, corners[index], corners[(index + 1) % corners.length],
                                Particle.REVERSE_PORTAL, dust);
                    }
                }
                case "summon", "summon_servants" -> {
                    spawnPatternRing(viewer, origin, side, horizontal, 1.05D, 24,
                            0.0D, Particle.SOUL_FIRE_FLAME);
                    viewer.spawnParticle(Particle.WITCH, origin, 18,
                            0.25D, 0.75D, 0.25D, 0.02D);
                }
                case "will_distortion" -> {
                    Location first = origin.clone().add(side.clone().multiply(0.45D));
                    Location second = center.clone().subtract(side.clone().multiply(0.45D));
                    spawnPatternSegment(viewer, first, second, Particle.ELECTRIC_SPARK, dust);
                    spawnPatternSegment(viewer, second, center.clone().add(side.clone().multiply(0.45D)),
                            Particle.ELECTRIC_SPARK, dust);
                }
                case "arena_inferno" -> {
                    spawnPatternRing(viewer, center, side, horizontal, 2.5D, 36,
                            0.0D, Particle.SOUL_FIRE_FLAME);
                    spawnPatternRing(viewer, center, side, horizontal, 3.4D, 48,
                            0.18D, Particle.FLAME);
                }
                case "rift_step" -> {
                    spawnPatternRing(viewer, origin, side, up, 0.75D, 20,
                            0.0D, Particle.PORTAL);
                    spawnPatternRing(viewer, center, side, up, 0.75D, 20,
                            0.35D, Particle.END_ROD);
                }
                case "void_snare" -> {
                    spawnPatternRing(viewer, center, side, up, 1.15D, 20,
                            0.0D, Particle.REVERSE_PORTAL);
                    spawnPatternRing(viewer, center, side, up, 0.55D, 12,
                            0.4D, Particle.SMOKE);
                }
                case "echo_pulse" -> {
                    spawnPatternRing(viewer, center, side, horizontal, 2.2D, 36,
                            0.0D, Particle.SCULK_SOUL);
                    viewer.spawnParticle(Particle.SONIC_BOOM, center, 1,
                            0.0D, 0.0D, 0.0D, 0.0D);
                }
                default -> viewer.spawnParticle(Particle.END_ROD, center, 8,
                        0.18D, 0.18D, 0.18D, 0.02D);
            }
        }
        getLogger().info("SPELL_IMPACT_VISUAL spell=" + spellId
                + " profile=" + profile.displayName());
    }

    /**
     * Render the readable wind-up for both boss and elite spells.  Every
     * spell has a different silhouette: rings, glyphs, lanes, chains or a
     * pulse.  The effect is audience-scoped and particle-only, so it never
     * replaces a vanilla texture or creates a hidden gameplay entity.
     */
    private void renderSpellTelegraphVisual(LivingEntity caster, Location mark,
                                            String spellId, int elapsedTicks,
                                            int totalTelegraphTicks) {
        SpellVisualPolicy.VisualProfile profile = SpellVisualPolicy.profile(spellId);
        if (caster == null || mark == null || spellId == null || profile == null
                || caster.getWorld() == null || mark.getWorld() == null
                || !caster.getWorld().equals(mark.getWorld())) {
            return;
        }
        Location origin = caster.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Location destination = mark.clone().add(0.0D, 0.12D, 0.0D);
        Vector direction = destination.toVector().subtract(origin.toVector());
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        double travelDistance = Math.max(1.0D, direction.length());
        direction.normalize();
        Vector horizontal = direction.clone();
        horizontal.setY(0.0D);
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = new Vector(1.0D, 0.0D, 0.0D);
        }
        horizontal.normalize();
        Vector side = new Vector(-horizontal.getZ(), 0.0D, horizontal.getX()).normalize();
        Vector up = new Vector(0.0D, 1.0D, 0.0D);
        double progress = Math.max(0.0D, Math.min(1.0D,
                elapsedTicks / (double) Math.max(1, totalTelegraphTicks)));
        double phaseAngle = elapsedTicks * 0.24D;
        for (Player viewer : eventAudience()) {
            if (!isEventParticleViewer(viewer, destination)
                    && !isEventParticleViewer(viewer, origin)) {
                continue;
            }
            switch (spellId) {
                case "void_blast" -> {
                    double radius = 0.65D + progress * 1.15D;
                    spawnPatternRing(viewer, destination, side, horizontal, radius, 18,
                            phaseAngle, Particle.DRAGON_BREATH);
                    spawnPatternSegment(viewer, origin, destination, Particle.END_ROD,
                            new Particle.DustOptions(Color.fromRGB(244, 39, 125), 1.15F));
                }
                case "rift_projectile" -> {
                    Location moving = origin.clone().add(direction.clone().multiply(progress));
                    spawnPatternRing(viewer, moving, side, up, 0.26D + progress * 0.10D,
                            14, phaseAngle, Particle.REVERSE_PORTAL);
                    spawnPatternSegment(viewer, origin, moving, Particle.DUST,
                            new Particle.DustOptions(Color.fromRGB(69, 218, 255), 1.20F));
                }
                case "rift_arrows", "arrow_salvo" -> {
                    Particle trail = "arrow_salvo".equals(spellId) ? Particle.CRIT : Particle.SOUL_FIRE_FLAME;
                    Color color = "arrow_salvo".equals(spellId)
                            ? Color.fromRGB(244, 60, 255) : Color.fromRGB(255, 72, 72);
                    Particle.DustOptions dust = new Particle.DustOptions(color, 1.10F);
                    for (int lane = -1; lane <= 1; lane++) {
                        double offset = lane * (0.22D + progress * 0.10D);
                        Location laneStart = origin.clone().add(side.clone().multiply(offset));
                        Location laneEnd = destination.clone().add(side.clone().multiply(offset * 1.4D));
                        spawnPatternSegment(viewer, laneStart, laneEnd, trail, dust);
                    }
                }
                case "void_mark" -> {
                    double radius = 0.80D + progress * 1.35D;
                    Location[] corners = new Location[4];
                    for (int index = 0; index < corners.length; index++) {
                        double angle = Math.PI / 4.0D + index * Math.PI / 2.0D + phaseAngle * 0.35D;
                        corners[index] = destination.clone()
                                .add(side.clone().multiply(Math.cos(angle) * radius))
                                .add(horizontal.clone().multiply(Math.sin(angle) * radius));
                        viewer.spawnParticle(Particle.END_ROD, corners[index], 2,
                                0.015D, 0.015D, 0.015D, 0.0D);
                    }
                    Particle.DustOptions dust = new Particle.DustOptions(
                            Color.fromRGB(190, 76, 255), 1.30F);
                    for (int index = 0; index < corners.length; index++) {
                        spawnPatternSegment(viewer, corners[index], corners[(index + 1) % corners.length],
                                Particle.REVERSE_PORTAL, dust);
                    }
                }
                case "summon_servants" -> {
                    Location center = origin.clone().add(0.0D, 0.25D, 0.0D);
                    spawnPatternRing(viewer, center, side, horizontal, 0.70D + progress * 0.50D,
                            16, phaseAngle, Particle.SOUL_FIRE_FLAME);
                    spawnPatternSegment(viewer, center.clone().subtract(up.clone().multiply(0.45D)),
                            center.clone().add(up.clone().multiply(0.85D)),
                            Particle.WITCH, new Particle.DustOptions(Color.fromRGB(125, 47, 255), 1.0F));
                }
                case "will_distortion" -> {
                    Location previous = origin;
                    for (int index = 1; index <= 6; index++) {
                        double along = Math.min(1.0D, progress + index * 0.04D);
                        double sway = (index % 2 == 0 ? -1.0D : 1.0D) * 0.35D;
                        Location next = origin.clone().add(direction.clone().multiply(
                                        travelDistance * along))
                                .add(side.clone().multiply(sway));
                        spawnPatternSegment(viewer, previous, next, Particle.ELECTRIC_SPARK,
                                new Particle.DustOptions(Color.fromRGB(84, 236, 255), 1.0F));
                        previous = next;
                    }
                }
                case "arena_inferno" -> {
                    double radius = 1.4D + progress * 2.5D;
                    spawnPatternRing(viewer, destination, side, horizontal, radius, 24,
                            phaseAngle, Particle.SOUL_FIRE_FLAME);
                    spawnPatternRing(viewer, destination, side, horizontal, radius * 0.72D,
                            18, -phaseAngle, Particle.FLAME);
                }
                case "rift_step" -> {
                    for (int lane = -1; lane <= 1; lane += 2) {
                        Location laneStart = origin.clone().add(side.clone().multiply(lane * 0.18D));
                        Location laneEnd = destination.clone().add(side.clone().multiply(lane * 0.30D));
                        spawnPatternSegment(viewer, laneStart, laneEnd, Particle.PORTAL,
                                new Particle.DustOptions(Color.fromRGB(69, 218, 255), 1.0F));
                    }
                }
                case "void_snare" -> {
                    double outer = Math.max(0.22D, 1.20D - progress * 0.65D);
                    spawnPatternRing(viewer, destination, side, up, outer, 16,
                            phaseAngle, Particle.REVERSE_PORTAL);
                    spawnPatternRing(viewer, destination, side, up, outer * 0.58D, 10,
                            -phaseAngle, Particle.SMOKE);
                }
                case "echo_pulse" -> {
                    Location center = origin.clone();
                    double radius = 0.25D + progress * 2.20D;
                    spawnPatternRing(viewer, center, side, horizontal, radius, 24,
                            phaseAngle, Particle.SCULK_SOUL);
                    viewer.spawnParticle(Particle.SONIC_BOOM, center, 1,
                            0.0D, 0.0D, 0.0D, 0.0D);
                }
                default -> viewer.spawnParticle(Particle.END_ROD, destination, 4,
                        0.08D, 0.08D, 0.08D, 0.01D);
            }
        }
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
            if (ticks[0] % SPELL_FLIGHT_RENDER_INTERVAL_TICKS == 0 || progress >= 1.0D) {
                spawnSpellFlightPattern(caster.getWorld(), point, delta, spellId, ticks[0]);
            }
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
        double maxDistanceSquared = 64.0D * 64.0D;
        for (Player player : eventAudience()) {
            if (!player.isOnline() || !player.getWorld().equals(world)
                    || player.getLocation().distanceSquared(point) > maxDistanceSquared) {
                continue;
            }
            spawnSpellFlightPattern(player, point, direction, spellId, tick);
        }
    }

    private void spawnSpellFlightPattern(Player player, Location point, Vector direction,
                                          String spellId, int tick) {
        if (player == null || point == null || direction == null || spellId == null
                || !player.isOnline() || player.getWorld() == null) {
            return;
        }
        World world = player.getWorld();
        if (!world.equals(point.getWorld()) || player.getLocation().distanceSquared(point) > 64.0D * 64.0D) {
            return;
        }
        if (PACKET_QUALITY_MINIMAL_SAFE.equals(runtimeDiagnosticsSnapshot.packetQualityMode())
                && tick % 4 != 0) {
            return;
        }
        recordParticleEmission(estimatedSpellFlightParticles(spellId));
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
                player.spawnParticle(Particle.DRAGON_BREATH, point, 12,
                        0.14D, 0.14D, 0.14D, 0.015D);
                player.spawnParticle(Particle.DUST, point, 7,
                        0.04D, 0.04D, 0.04D, 0.0D,
                        new Particle.DustOptions(Color.fromRGB(244, 39, 125), 1.35F));
                spawnPatternRing(player, point, side, vertical, 0.18D + (tick % 3) * 0.05D,
                        10, phase, Particle.FLAME);
            }
            case "rift_projectile" -> spawnRiftProjectileTrail(player, point, direction, tick);
            case "arrow_salvo" -> {
                Particle.DustOptions salvoDust = new Particle.DustOptions(
                        Color.fromRGB(244, 60, 255), 1.15F);
                player.spawnParticle(Particle.END_ROD, point, 5,
                        0.05D, 0.05D, 0.05D, 0.01D);
                player.spawnParticle(Particle.DUST, point, 5,
                        0.04D, 0.04D, 0.04D, 0.0D, salvoDust);
                spawnPatternRing(player, point, side, vertical,
                        0.13D + (tick % 3) * 0.04D, 8, phase, Particle.CRIT);
            }
            case "rift_arrows" -> {
                Particle.DustOptions riftArrowDust = new Particle.DustOptions(
                        Color.fromRGB(255, 72, 72), 1.25F);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 7,
                        0.06D, 0.06D, 0.06D, 0.01D);
                player.spawnParticle(Particle.DUST, point, 5,
                        0.04D, 0.04D, 0.04D, 0.0D, riftArrowDust);
                Vector arrowSide = side.clone().multiply(0.22D);
                spawnPatternSegment(player, point.clone().subtract(arrowSide),
                        point.clone().add(arrowSide), Particle.CRIT, riftArrowDust);
            }
            case "void_mark" -> {
                Particle.DustOptions markDust = new Particle.DustOptions(Color.fromRGB(190, 76, 255), 1.25F);
                double halfDiagonal = 0.31D + (tick % 2) * 0.04D;
                Location[] corners = new Location[4];
                for (int i = 0; i < corners.length; i++) {
                    double angle = phase * 0.35D + Math.PI / 4.0D + i * Math.PI / 2.0D;
                    corners[i] = point.clone()
                            .add(side.clone().multiply(Math.cos(angle) * halfDiagonal))
                            .add(vertical.clone().multiply(Math.sin(angle) * halfDiagonal));
                    player.spawnParticle(Particle.END_ROD, corners[i], 2,
                            0.015D, 0.015D, 0.015D, 0.0D);
                    player.spawnParticle(Particle.DUST, corners[i], 2,
                            0.015D, 0.015D, 0.015D, 0.0D, markDust);
                }
                for (int i = 0; i < corners.length; i++) {
                    spawnPatternSegment(player, corners[i], corners[(i + 1) % corners.length],
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
                    player.spawnParticle(Particle.SOUL_FIRE_FLAME, spiral, 2,
                            0.015D, 0.015D, 0.015D, 0.005D);
                    player.spawnParticle(Particle.WITCH, spiral, 1,
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
                    player.spawnParticle(Particle.ELECTRIC_SPARK, zig, 2,
                            0.02D, 0.02D, 0.02D, 0.01D);
                    player.spawnParticle(Particle.WITCH, zig, 1,
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
                    player.spawnParticle(Particle.PORTAL, first, 2,
                            0.015D, 0.015D, 0.015D, 0.01D);
                    player.spawnParticle(Particle.END_ROD, second, 1,
                            0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            case "void_snare" -> {
                double radius = Math.max(0.16D, 0.44D - tick * 0.035D);
                spawnPatternRing(player, point, side, vertical, radius, 12, phase,
                        Particle.REVERSE_PORTAL);
                for (int i = 0; i < 6; i++) {
                    double angle = phase + i * Math.PI / 3.0D;
                    Location chain = point.clone()
                            .add(side.clone().multiply(Math.cos(angle) * radius))
                            .add(vertical.clone().multiply(Math.sin(angle) * radius));
                    player.spawnParticle(Particle.SMOKE, chain, 1,
                            0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            case "echo_pulse" -> {
                double radius = 0.10D + tick * 0.075D;
                spawnPatternRing(player, point, side, vertical, radius, 14, phase,
                        Particle.SCULK_SOUL);
                player.spawnParticle(Particle.SONIC_BOOM, point, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
            default -> player.spawnParticle(Particle.END_ROD, point, 4,
                    0.06D, 0.06D, 0.06D, 0.0D);
        }
    }

    private void spawnRiftProjectileTrail(Player player, Location point, Vector direction, int tick) {
        if (player == null || point == null || point.getWorld() == null || direction == null
                || !player.isOnline() || !player.getWorld().equals(point.getWorld())
                || player.getLocation().distanceSquared(point) > 64.0D * 64.0D) {
            return;
        }
        recordParticleEmission(29);
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
            player.spawnParticle(Particle.REVERSE_PORTAL, spiral, 2,
                    0.015D, 0.015D, 0.015D, 0.01D);
            player.spawnParticle(Particle.DUST, spiral, 1,
                    0.01D, 0.01D, 0.01D, 0.0D, riftDust);
        }
        player.spawnParticle(Particle.DRAGON_BREATH, point, 5,
                0.04D, 0.04D, 0.04D, 0.01D);
    }

    private void spawnPatternRing(Player player, Location center, Vector axisA, Vector axisB,
                                  double radius, int points, double phase, Particle particle) {
        if (player == null || center == null || center.getWorld() == null || !player.isOnline()
                || !player.getWorld().equals(center.getWorld())
                || player.getLocation().distanceSquared(center) > 64.0D * 64.0D) {
            return;
        }
        recordParticleEmission(Math.max(0, points));
        for (int i = 0; i < points; i++) {
            double angle = phase + (Math.PI * 2.0D * i / points);
            Location ringPoint = center.clone()
                    .add(axisA.clone().multiply(Math.cos(angle) * radius))
                    .add(axisB.clone().multiply(Math.sin(angle) * radius));
            player.spawnParticle(particle, ringPoint, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnPatternSegment(Player player, Location start, Location end, Particle particle,
                                     Particle.DustOptions dust) {
        if (player == null || start == null || end == null || start.getWorld() == null
                || !player.isOnline() || !player.getWorld().equals(start.getWorld())
                || player.getLocation().distanceSquared(start) > 64.0D * 64.0D) {
            return;
        }
        Vector delta = end.toVector().subtract(start.toVector());
        int steps = 4;
        recordParticleEmission((steps - 1) * (dust == null ? 1 : 2));
        for (int i = 1; i < steps; i++) {
            Location linePoint = start.clone().add(delta.clone().multiply(i / (double) steps));
            if (particle == Particle.DUST) {
                // Particle.DUST requires DustOptions as its data payload;
                // sending it through the generic overload throws on Paper
                // and aborts the repeating telegraph task.
                Particle.DustOptions safeDust = dust != null
                        ? dust : new Particle.DustOptions(Color.WHITE, 1.0F);
                player.spawnParticle(Particle.DUST, linePoint, 1,
                        0.0D, 0.0D, 0.0D, 0.0D, safeDust);
            } else {
                player.spawnParticle(particle, linePoint, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
                if (dust != null) {
                    player.spawnParticle(Particle.DUST, linePoint, 1,
                            0.0D, 0.0D, 0.0D, 0.0D, dust);
                }
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

    private boolean isMiniBossCombatEntity(Entity entity) {
        return entity instanceof LivingEntity
                && (EVENT_KIND_ELITE.equals(readString(entity, keyKind))
                || EVENT_KIND_FINAL_WAVE.equals(readString(entity, keyKind)))
                && !readString(entity, keyMiniBossSpell).isBlank();
    }

    private void miniBossRiftStep(LivingEntity miniBoss, Player target) {
        Location anchor = coreCombatAnchorLocation();
        Location destination = findSafeCombatLocation(anchor, target.getLocation(), config.containmentRadius());
        if (destination != null) {
            teleportCombatEntity(miniBoss, destination);
        }
        target.damage(config.miniBossTuning().riftStepDamage(), miniBoss);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                SLOWNESS_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
        spawnEventParticle(target.getLocation().add(0.0D, 1.0D, 0.0D), Particle.PORTAL,
                24, 0.5D, 0.8D, 0.5D, 0.02D);
    }

    private void miniBossVoidSnare(LivingEntity miniBoss, Player target, Location mark) {
        target.damage(config.miniBossTuning().voidSnareDamage(), miniBoss);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                SLOWNESS_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                MINI_VOID_SNARE_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
        spawnEventParticle(mark, Particle.REVERSE_PORTAL, 28, 1.2D, 0.1D, 1.2D, 0.02D);
    }

    private void miniBossEchoPulse(LivingEntity miniBoss) {
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                    MINI_ECHO_PULSE_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
        }
        spawnEventParticle(center.add(0.0D, 1.0D, 0.0D), Particle.END_ROD,
                32, 1.0D, 0.6D, 1.0D, 0.03D);
    }

    private void miniBossArrowSalvo(LivingEntity miniBoss, Player target) {
        if (miniBoss == null || target == null || !isCombatTarget(target)) {
            return;
        }
        riftArrowVolley(miniBoss, target, EndRiftAiPolicy.MiniBossSpell.ARROW_SALVO.id(),
                SkeletonCombatPolicy.arrowProfile(true));
        spawnEventParticle(target.getLocation().add(0.0D, 1.0D, 0.0D), Particle.END_ROD,
                18, 0.45D, 0.7D, 0.45D, 0.02D);
        miniBoss.getWorld().playSound(miniBoss.getLocation(), Sound.ENTITY_SKELETON_SHOOT,
                0.9F, 1.35F);
    }

    private void enforceCombatLeash(Entity entity, Location anchor, double radius, String logMarker) {
        if (entity == null || anchor == null || entity.getWorld() == null
                || !entity.getWorld().equals(anchor.getWorld())) {
            return;
        }
        Location current = entity.getLocation();
        boolean standingOnCore = isCoreBlockPosition(current);
        boolean outsideHorizontalRadius = horizontalDistanceSquared(current, anchor) > radius * radius;
        boolean outsideVerticalRadius = outsideCombatVertical(current, anchor);
        if (!standingOnCore && !outsideHorizontalRadius && !outsideVerticalRadius) {
            return;
        }
        // The anchor is the solid Core block.  Never prefer it as an entity
        // destination: resolve to a nearby passable floor position instead.
        double minCoreDistance = EVENT_KIND_BOSS.equals(readString(entity, keyKind))
                ? MIN_BOSS_CORE_DISTANCE_BLOCKS : MIN_WAVE_CORE_DISTANCE_BLOCKS;
        Location from = current.clone();
        Location safe = findSafeCombatLocation(anchor, null, radius - 0.75D, minCoreDistance);
        if (safe != null && teleportCombatEntity(entity, safe)) {
            if (EVENT_KIND_BOSS.equals(readString(entity, keyKind))) {
                getLogger().info("BOSS_MOVE_TELEPORT boss=" + entity.getUniqueId()
                        + " reason=leash from=" + locationText(from) + " to=" + locationText(safe)
                        + " target=" + (entity instanceof Mob mob && mob.getTarget() != null
                        ? mob.getTarget().getUniqueId() : "none") + " phase=" + phase);
            } else {
                getLogger().info(logMarker + " entity=" + entity.getUniqueId()
                        + " location=" + locationText(safe));
            }
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

    private int configuredDebuffAmplifier() {
        return config == null ? MAX_POTION_AMPLIFIER
                : Math.max(0, Math.min(MAX_POTION_AMPLIFIER, config.debuffAmplifier()));
    }

    private double configuredCombatVerticalRadius() {
        return config == null ? 3.0D : Math.max(0.0D,
                Math.min(3.0D, config.arenaVerticalRadius()));
    }

    /** Compare block levels, not entity fractional feet, for a block radius. */
    private boolean outsideCombatVertical(Location location, Location anchor) {
        if (location == null || anchor == null) {
            return true;
        }
        return Math.abs(location.getBlockY() - anchor.getBlockY())
                > Math.ceil(configuredCombatVerticalRadius());
    }

    private Location findSafeCombatLocation(Location anchor, Location preferred, double radius) {
        return findSafeCombatLocation(anchor, preferred, radius, MIN_WAVE_CORE_DISTANCE_BLOCKS);
    }

    /**
     * Resolve a combat destination on the real arena floor.  The Core block is
     * an anchor, never a valid entity destination: every caller supplies a
     * minimum horizontal separation so a failed path or teleport cannot stack
     * an event mob on the Core.  Candidate inspection is bounded and the pure
     * BossMovementPolicy owns the deterministic choice.
     */
    private Location findSafeCombatLocation(Location anchor, Location preferred, double radius,
                                            double minCoreDistance) {
        if (anchor == null || anchor.getWorld() == null) {
            return null;
        }
        double safeRadius = Math.max(1.0D, radius);
        double safeMinDistance = Math.max(0.0D, Math.min(minCoreDistance, safeRadius - 0.25D));
        Location center = anchor.clone();
        List<BossMovementPolicy.Candidate> candidates = new ArrayList<>();
        Map<String, Location> candidateLocations = new LinkedHashMap<>();
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = attempt * 2.399963229728653D;
            double distance = attempt == 0 ? 0.0D : safeMinDistance + 0.75D + (attempt % 7) * 1.65D;
            Location candidate = attempt < 4 && preferred != null
                    ? preferred.clone().add(Math.cos(angle) * (attempt * 0.25D), 0.0D,
                    Math.sin(angle) * (attempt * 0.25D))
                    : center.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(BlockFace.UP);
            Block floor = feet.getRelative(BlockFace.DOWN);
            Location resolved = new Location(candidate.getWorld(), feet.getX() + 0.5D,
                    feet.getY(), feet.getZ() + 0.5D);
            String id = "candidate-" + attempt;
            BossMovementPolicy.Candidate policyCandidate = new BossMovementPolicy.Candidate(
                    id, resolved.getX(), resolved.getY(), resolved.getZ(),
                    feet.isPassable(), head.isPassable(), floor.getType().isSolid(),
                    feet.isLiquid() || head.isLiquid() || floor.isLiquid(),
                    isFireBlock(feet) || isFireBlock(head) || isFireBlock(floor),
                    isWebBlock(feet) || isWebBlock(head) || isWebBlock(floor),
                    isTemporaryMovementHazard(feet) || isTemporaryMovementHazard(head)
                            || isTemporaryMovementHazard(floor),
                    isCoreBlockPosition(resolved), floor.getType().name());
            candidates.add(policyCandidate);
            candidateLocations.put(id, resolved);
        }
        BossMovementPolicy.Candidate selected = BossMovementPolicy.chooseSafeDestination(
                new BossMovementPolicy.Anchor(center.getX(), center.getY(), center.getZ()),
                preferred == null ? null : new BossMovementPolicy.Target(
                        preferred.getX(), preferred.getY(), preferred.getZ()),
                candidates, safeRadius, configuredCombatVerticalRadius(), safeMinDistance,
                Set.of("COBWEB", "POWDER_SNOW", "SWEET_BERRY_BUSH", "FIRE", "SOUL_FIRE"));
        if (selected == null) {
            selected = BossMovementPolicy.chooseStuckFallback(
                    new BossMovementPolicy.Anchor(center.getX(), center.getY(), center.getZ()),
                    candidates, safeRadius, configuredCombatVerticalRadius(), safeMinDistance,
                    Set.of("COBWEB", "POWDER_SNOW", "SWEET_BERRY_BUSH", "FIRE", "SOUL_FIRE"));
        }
        return selected == null ? null : candidateLocations.get(selected.id());
    }

    private boolean isFireBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material material = block.getType();
        return material == Material.FIRE || material == Material.SOUL_FIRE;
    }

    private boolean isWebBlock(Block block) {
        return block != null && block.getType() == Material.COBWEB;
    }

    private boolean isTemporaryMovementHazard(Block block) {
        if (block == null) {
            return true;
        }
        Material material = block.getType();
        return material == Material.POWDER_SNOW || material == Material.SWEET_BERRY_BUSH
                || material == Material.MAGMA_BLOCK
                || material == Material.POINTED_DRIPSTONE;
    }

    private boolean isCombatPhase() {
        if (testCombatAiMode) {
            return true;
        }
        return switch (phase) {
            case WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3,
                    INTERMISSION_3, WAVE_4, INTERMISSION_4, WAVE_5,
                    BOSS_CINEMATIC,
                    BOSS_ACTIVE, FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH -> true;
            default -> false;
        };
    }

    /**
     * Keep stale wave entities from fighting during an intermission or the
     * boss fight, while explicitly keeping the final post-Judgment wave on
     * the same controller as waves 1-5.  Disposable local AI tests are the
     * sole exception because they intentionally run outside the official
     * state machine.
     */
    private boolean isWaveAiCombatPhase(int wave) {
        if (testCombatAiMode) {
            return true;
        }
        return switch (wave) {
            case 1 -> phase == EventPhase.WAVE_1;
            case 2 -> phase == EventPhase.WAVE_2;
            case 3 -> phase == EventPhase.WAVE_3;
            case 4 -> phase == EventPhase.WAVE_4;
            case 5 -> phase == EventPhase.WAVE_5;
            case FINAL_WAVE_NUMBER -> phase == EventPhase.FINAL_WAVE;
            default -> false;
        };
    }

    /**
     * Wave zero is reserved for boss-servant summons.  They are not an
     * official numbered wave, but while the boss is active they still belong
     * to this generation's bounded AI controller and must chase players.
     */
    private boolean isWaveAiCombatEntity(Entity entity) {
        if (entity == null || !isWaveCombatKind(readString(entity, keyKind))) {
            return false;
        }
        if (isWaveAiCombatPhase(readInt(entity, keyWave, 0))) {
            return true;
        }
        // Disposable `/cmend test wave` entities intentionally live while
        // the official state machine is READY_FOR_PLAYERS.  They are still
        // event-owned and must exercise the same path/target controller; a
        // natural mob can never reach this method because tickWaveMobAi()
        // iterates only ownedEntities.
        if (!isOfficialEntity(entity)
                && "local".equalsIgnoreCase(config == null ? "" : config.environment())) {
            return true;
        }
        return spellServants.contains(entity.getUniqueId())
                && (phase == EventPhase.BOSS_ACTIVE || testCombatAiMode);
    }

    private boolean isMiniBossCombatPhase() {
        return testCombatAiMode || phase == EventPhase.WAVE_1 || phase == EventPhase.WAVE_2
                || phase == EventPhase.WAVE_3 || phase == EventPhase.WAVE_4
                || phase == EventPhase.WAVE_5 || phase == EventPhase.FINAL_WAVE
                || hasLiveTestWaveEntities();
    }

    private boolean hasLiveTestWaveEntities() {
        for (Entity entity : ownedEntities.values()) {
            if (isWaveCombatKind(readString(entity, keyKind))
                    && !isOfficialEntity(entity) && isLiveOwnedEntity(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
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
        EventPhase nextPhase = switch (phase) {
            case INTERMISSION_1 -> EventPhase.WAVE_2;
            case INTERMISSION_2 -> EventPhase.WAVE_3;
            case INTERMISSION_3 -> EventPhase.WAVE_4;
            case INTERMISSION_4 -> EventPhase.WAVE_5;
            default -> null;
        };
        if (nextPhase != null) {
            int nextWave = switch (nextPhase) {
                case WAVE_2 -> 2;
                case WAVE_3 -> 3;
                case WAVE_4 -> 4;
                case WAVE_5 -> 5;
                default -> 0;
            };
            activeWave = nextWave;
            if (transition(nextPhase, "intermission complete", eventId + ":wave:" + nextWave)) {
                spawnWave(nextWave, false);
            }
        }
        phaseDeadlineMillis = 0L;
    }

    /**
     * The cinematic has no combat entity yet.  This watchdog makes the
     * durable phase self-healing if an admin command or a delayed scheduler
     * callback leaves the spawn task absent; a restart still recovers the
     * transient phase to READY_FOR_PLAYERS before this method can run.
     */
    private void tickBossCinematic() {
        if (phase != EventPhase.BOSS_CINEMATIC || liveBoss() != null) {
            return;
        }
        if (bossSpawnTask == null) {
            scheduleOfficialBossSpawn();
        }
    }

    private void tickWaveCompletion() {
        if (phase == EventPhase.FINAL_WAVE) {
            if (finalWaveEntities.stream().noneMatch(this::isLiveOwnedEntity)) {
                finalWaveEntities.clear();
                LivingEntity boss = liveBoss();
                if (boss != null) {
                    // FINAL_WAVE is the hard boundary between the cinematic
                    // absorption sequence and the real damageable boss.  A
                    // cancelled/reloaded cast task must not carry its
                    // invulnerability state into BOSS_FINISH.
                    cancelBossCastTask();
                    bossCastState = BossCastState.NONE;
                    bossCastDeadlineMillis = 0L;
                    bossSpellPauseUntilMillis = 0L;
                    boss.setInvulnerable(false);
                    setBossVirtualHealth(boss, config.bossFinalHealth());
                    getLogger().info("BOSS_DAMAGE_WINDOW_OPEN event=" + eventId
                            + " boss=" + boss.getUniqueId() + " reason=final-wave-complete");
                    if (bossBar != null) {
                        bossBar.setTitle("Хранитель Разлома");
                    }
                    if (!transition(EventPhase.BOSS_FINISH, "final wave defeated", eventId + ":final-wave-complete")) {
                        getLogger().severe("FINAL_WAVE_HANDOFF_FAILED event=" + eventId
                                + " target=BOSS_FINISH reason=state-transition-rejected");
                        forcePhase(EventPhase.RECOVERY_REQUIRED, "final wave boss release failed");
                        return;
                    }
                } else {
                    // The official sequence creates the final wave after the
                    // cinematic and only materializes the boss once that wave
                    // is defeated.  This keeps the final wave a real combat
                    // gate instead of a decorative phase with an invisible
                    // boss waiting underneath it.
                    if (!transition(EventPhase.BOSS_ACTIVE,
                            "final wave defeated; boss awakens", eventId + ":boss-active-after-final-wave")) {
                        getLogger().severe("FINAL_WAVE_HANDOFF_FAILED event=" + eventId
                                + " target=BOSS_ACTIVE reason=state-transition-rejected");
                        forcePhase(EventPhase.RECOVERY_REQUIRED, "final wave boss spawn failed");
                        return;
                    }
                    phaseDeadlineMillis = 0L;
                    saveStateAsync();
                    announceEventTitle("§dХРАНИТЕЛЬ РАЗЛОМА ПРОБУЖДАЕТСЯ",
                            "§5Пустота требует последнюю жертву", true);
                    spawnOfficialBoss(null);
                }
                getLogger().info("WAVE_COMPLETED event=" + eventId + " wave=FINAL");
            }
            return;
        }
        if (activeWave < 1 || activeWave > 5) {
            return;
        }
        if (!tickWaveObjective()) {
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
        int completedWave = activeWave;
        if (!spawnWaveCompletionLoot(completedWave)) {
            return;
        }
        announceWaveComplete(completedWave);
        getLogger().info("WAVE_COMPLETED event=" + eventId + " wave=" + completedWave);
        if (completedWave == 1) {
            phaseDeadlineMillis = System.currentTimeMillis() + config.intermissionSeconds() * 1000L;
            transition(EventPhase.INTERMISSION_1, "wave 1 defeated", eventId + ":intermission:1");
        } else if (completedWave == 2) {
            phaseDeadlineMillis = System.currentTimeMillis() + config.intermissionSeconds() * 1000L;
            transition(EventPhase.INTERMISSION_2, "wave 2 defeated", eventId + ":intermission:2");
        } else if (completedWave == 3) {
            phaseDeadlineMillis = System.currentTimeMillis() + config.intermissionSeconds() * 1000L;
            transition(EventPhase.INTERMISSION_3, "wave 3 defeated", eventId + ":intermission:3");
        } else if (completedWave == 4) {
            phaseDeadlineMillis = System.currentTimeMillis() + config.intermissionSeconds() * 1000L;
            transition(EventPhase.INTERMISSION_4, "wave 4 defeated", eventId + ":intermission:4");
        } else if (completedWave == 5) {
            activeWave = 0;
            if (transition(EventPhase.BOSS_CINEMATIC, "wave 5 defeated; boss cinematic", eventId + ":boss-cinematic")) {
                phaseDeadlineMillis = System.currentTimeMillis() + BOSS_CINEMATIC_DURATION_TICKS * 50L;
                scheduleOfficialBossSpawn();
            }
        }
    }

    /**
     * Drops the guaranteed reward bundle physically on the Core after a wave.
     * The wave number is committed after the item entities are created, while
     * the persistent item tags make a retry after a process interruption
     * discover an already-created bundle instead of duplicating it.
     */
    private boolean spawnWaveCompletionLoot(int wave) {
        if (waveRewardsIssued.contains(wave)) {
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        Location drop = coreLocation();
        Map<String, Integer> configured = config.waveReward(wave);
        List<UUID> recipients = new ArrayList<>(officialRewardRoster);
        if (world == null || drop == null || configured.isEmpty() || recipients.isEmpty()) {
            getLogger().severe("WAVE_REWARD_FAILED event=" + eventId + " wave=" + wave
                    + " reason=missing-world-core-config-or-roster");
            return false;
        }

        List<Item> spawned = new ArrayList<>();
        long ownerExpiresAt = System.currentTimeMillis() + WAVE_REWARD_OWNER_WINDOW_MILLIS;
        Material sharedRare = sharedRareMaterial(wave);
        boolean sharedRareAwarded = sharedRare != null
                && WaveRewardPolicy.sharedRareRoll(eventId, wave,
                config.waveRewardSharedRareChance(wave));
        try {
            for (int recipientIndex = 0; recipientIndex < recipients.size(); recipientIndex++) {
                UUID recipient = recipients.get(recipientIndex);
                WaveRewardPolicy.RewardBundle bundle = WaveRewardPolicy.bundle(
                        wave, recipientIndex, recipients.size(), configured);
                int materialIndex = 0;
                for (WaveRewardPolicy.RewardStack stack : bundle.stacks()) {
                    Material material = Material.matchMaterial(stack.material());
                    int amount = stack.amount();
                    if (material == null || amount < 1
                            || hasExistingWaveReward(world, wave, recipient, material, amount)) {
                        materialIndex++;
                        continue;
                    }
                    double angle = (recipientIndex * 2.399963229728653D + materialIndex * 0.37D)
                            % (Math.PI * 2.0D);
                    Location itemDrop = drop.clone().add(Math.cos(angle) * 0.35D, 0.15D,
                            Math.sin(angle) * 0.35D);
                    Item item = world.dropItem(itemDrop, new ItemStack(material, amount));
                    item.setPickupDelay(20);
                    item.setVelocity(new Vector(0.0D, 0.18D, 0.0D));
                    tag(item, EVENT_KIND_WAVE_REWARD, wave, true);
                    tagWaveReward(item, recipient, ownerExpiresAt, false, amount);
                    setLootProfile(item, "wave-reward");
                    spawned.add(item);
                    materialIndex++;
                }
            }
            int sharedRareAmount = wave >= 5 ? 2 : 1;
            if (sharedRareAwarded && !hasExistingSharedWaveReward(world, wave)) {
                Item item = world.dropItem(drop.clone().add(0.0D, 0.15D, 0.0D),
                        new ItemStack(sharedRare, sharedRareAmount));
                item.setPickupDelay(20);
                item.setVelocity(new Vector(0.0D, 0.28D, 0.0D));
                tag(item, EVENT_KIND_WAVE_REWARD, wave, true);
                tagWaveReward(item, null, 0L, true, sharedRareAmount);
                setLootProfile(item, "wave-reward-shared-rare");
                spawned.add(item);
            }
            if (!allWaveRewardItemsPresent(world, wave, recipients, configured)
                    || sharedRareAwarded && !hasExistingSharedWaveReward(world, wave)) {
                getLogger().severe("WAVE_REWARD_FAILED event=" + eventId + " wave=" + wave
                        + " reason=recipient-bundles-not-materialized");
                return false;
            }
            waveRewardsIssued.add(wave);
            if (!saveStateSync()) {
                waveRewardsIssued.remove(wave);
                for (Item item : spawned) {
                    ownedEntities.remove(item.getUniqueId());
                    if (item.isValid()) {
                        item.remove();
                    }
                }
                getLogger().severe("WAVE_REWARD_FAILED event=" + eventId + " wave=" + wave
                        + " reason=state-commit-failed");
                return false;
            }
        } catch (RuntimeException error) {
            waveRewardsIssued.remove(wave);
            for (Item item : spawned) {
                ownedEntities.remove(item.getUniqueId());
                if (item.isValid()) {
                    item.remove();
                }
            }
            getLogger().log(Level.SEVERE, "WAVE_REWARD_FAILED event=" + eventId + " wave=" + wave,
                    error);
            return false;
        }

        spawnEventParticle(drop.clone().add(0.0D, 0.6D, 0.0D), Particle.TOTEM_OF_UNDYING,
                32, 0.55D, 0.45D, 0.55D, 0.05D);
        spawnEventParticle(drop.clone().add(0.0D, 0.8D, 0.0D), Particle.END_ROD,
                20, 0.35D, 0.35D, 0.35D, 0.03D);
        world.playSound(drop, Sound.ENTITY_PLAYER_LEVELUP, 0.85F, 1.15F);
        getLogger().info("WAVE_REWARD_SPAWNED event=" + eventId + " wave=" + wave
                + " location=" + locationText(drop) + " recipients=" + recipients.size()
                + " owner_window_ms=" + WAVE_REWARD_OWNER_WINDOW_MILLIS
                + " shared_rare_awarded=" + sharedRareAwarded
                + " shared_rare_key=end-event:" + eventId + ":wave:" + wave + ":shared-rare");
        return true;
    }

    private void tagWaveReward(Item item, UUID owner, long expiresAt, boolean shared) {
        tagWaveReward(item, owner, expiresAt, shared,
                item == null || item.getItemStack() == null ? 0 : item.getItemStack().getAmount());
    }

    private void tagWaveReward(Item item, UUID owner, long expiresAt, boolean shared, int expectedAmount) {
        if (item == null) {
            return;
        }
        PersistentDataContainer data = item.getPersistentDataContainer();
        data.set(keyRewardShared, PersistentDataType.BYTE, shared ? (byte) 1 : (byte) 0);
        data.set(keyRewardExpiresAt, PersistentDataType.LONG, Math.max(0L, expiresAt));
        data.set(keyRewardAmount, PersistentDataType.INTEGER, Math.max(0, expectedAmount));
        if (owner == null) {
            data.remove(keyRewardOwner);
        } else {
            data.set(keyRewardOwner, PersistentDataType.STRING, owner.toString());
        }
    }

    private boolean hasExistingWaveReward(World world, int wave, UUID owner, Material material, int expectedAmount) {
        if (world == null || eventId.isBlank() || owner == null || material == null || expectedAmount < 1) {
            return false;
        }
        return world.getEntitiesByClass(Item.class).stream()
                .anyMatch(item -> item.isValid()
                        && EVENT_KIND_WAVE_REWARD.equals(readString(item, keyKind))
                        && wave == readInt(item, keyWave, 0)
                        && ownedByEvent(item, eventId)
                        && owner.toString().equals(item.getPersistentDataContainer()
                        .getOrDefault(keyRewardOwner, PersistentDataType.STRING, ""))
                        && item.getItemStack().getType() == material
                        && item.getPersistentDataContainer().getOrDefault(
                        keyRewardAmount, PersistentDataType.INTEGER, item.getItemStack().getAmount())
                        >= expectedAmount
                        && item.getPersistentDataContainer().getOrDefault(
                        keyRewardShared, PersistentDataType.BYTE, (byte) 0) == (byte) 0);
    }

    private boolean hasExistingSharedWaveReward(World world, int wave) {
        if (world == null || eventId.isBlank()) {
            return false;
        }
        return world.getEntitiesByClass(Item.class).stream()
                .anyMatch(item -> item.isValid()
                        && EVENT_KIND_WAVE_REWARD.equals(readString(item, keyKind))
                        && wave == readInt(item, keyWave, 0)
                        && ownedByEvent(item, eventId)
                        && item.getPersistentDataContainer().getOrDefault(
                        keyRewardShared, PersistentDataType.BYTE, (byte) 0) == (byte) 1);
    }

    private boolean allWaveRewardItemsPresent(World world, int wave, List<UUID> recipients,
                                              Map<String, Integer> configured) {
        if (world == null || recipients == null || recipients.isEmpty() || configured == null
                || configured.isEmpty()) {
            return false;
        }
        for (UUID recipient : recipients) {
            int recipientIndex = recipients.indexOf(recipient);
            WaveRewardPolicy.RewardBundle bundle = WaveRewardPolicy.bundle(
                    wave, recipientIndex, recipients.size(), configured);
            for (WaveRewardPolicy.RewardStack stack : bundle.stacks()) {
                Material material = Material.matchMaterial(stack.material());
                if (material != null && !hasExistingWaveReward(world, wave, recipient, material, stack.amount())) {
                    return false;
                }
            }
        }
        return true;
    }

    private Material sharedRareMaterial(int wave) {
        // One shared physical rare drop is separate from each participant's
        // bundle and is guarded by its own idempotency key.  Early waves stay
        // personal; the team roll begins with the tower-defense reward.
        return wave >= 4 && wave <= 5 ? Material.NETHERITE_SCRAP : null;
    }

    private void spawnWave(int wave, boolean test) {
        World world = Bukkit.getWorld(worldName);
        Location core = coreLocation();
        if (world == null || core == null) {
            getLogger().warning("Cannot spawn End Event wave without configured world/core.");
            return;
        }
        boolean finalWave = wave == FINAL_WAVE_NUMBER;
        EventConfig.WaveDefinition definition = switch (wave) {
            case 1 -> config.wave1();
            case 2 -> config.wave2();
            case 3 -> config.wave3();
            case 4 -> config.wave4();
            case 5 -> config.wave5();
            case FINAL_WAVE_NUMBER -> config.finalWave();
            default -> null;
        };
        if (definition == null) {
            return;
        }
        int scalePlayers = Math.max(config.minPlayers(), officialRewardRoster.size());
        double scale = Math.max(0.8D, Math.min(2.0D, scalePlayers / 5.0D));
        int endermen = scaled(definition.endermen(), scale);
        int spiders = scaled(definition.spiders(), scale);
        int skeletons = scaled(definition.skeletons(), scale);
        int elites = scaled(definition.eliteEndermen(), scale);
        int eliteSkeletons = scaled(definition.eliteSkeletons(), scale);
        int total = endermen + spiders + skeletons + elites + eliteSkeletons;
        if (total > config.waveHardCap()) {
            int overflow = total - config.waveHardCap();
            int[] counts = {endermen, spiders, skeletons, elites, eliteSkeletons};
            for (int index = 0; index < counts.length && overflow > 0; index++) {
                int remove = Math.min(overflow, Math.max(0, counts[index] - 1));
                counts[index] -= remove;
                overflow -= remove;
            }
            endermen = counts[0];
            spiders = counts[1];
            skeletons = counts[2];
            elites = counts[3];
            eliteSkeletons = counts[4];
        }
        if (!finalWave && wave == 4) {
            WaveMechanicsPolicy.WaveCounts capped = WaveMechanicsPolicy.capTowerCounts(
                    new WaveMechanicsPolicy.WaveCounts(endermen, spiders, skeletons, elites, eliteSkeletons),
                    scalePlayers);
            endermen = capped.endermen();
            spiders = capped.spiders();
            skeletons = capped.skeletons();
            elites = capped.eliteEndermen();
            eliteSkeletons = capped.eliteSkeletons();
            getLogger().info("WAVE_TOWER_COMPOSITION_CAP event=" + eventId
                    + " players=" + scalePlayers + " cap=" + capped.total()
                    + " composition=endermen:" + endermen + ",spiders:" + spiders
                    + ",skeletons:" + skeletons + ",elite_endermen:" + elites
                    + ",elite_skeletons:" + eliteSkeletons);
        }
        if (!test) {
            activeWave = wave;
            playEventMusic(finalWave
                    ? phaseMusicOrLegacy("final-wave", config.bossFinalMusic())
                    : phaseMusicOrLegacy("wave-" + wave, config.wavesMusic()));
            announceWaveStart(wave, finalWave);
            spawnWaveArrivalEffect(world, core, wave, finalWave);
            startWaveObjective(wave, world, core);
        }
        boolean pacedTowerWave = !test && wave == 4;
        if (pacedTowerWave) {
            towerSpawnSchedule = WaveMechanicsPolicy.towerSpawnGroups(
                    new WaveMechanicsPolicy.WaveCounts(endermen, spiders, skeletons, elites, eliteSkeletons), scalePlayers);
            towerSpawnGroupIndex = 0;
            towerSpawnEntityOffset = 0;
            towerNextSpawnAtMillis = 0L;
            spawnNextTowerGroup(world, core);
            scheduleTowerSpawnGroups(world, core);
        } else {
            spawnWaveGroup(world, core, wave, finalWave, test,
                    new WaveMechanicsPolicy.WaveCounts(endermen, spiders, skeletons, elites, eliteSkeletons), 0);
        }
        if (finalWave && !test) {
                finalWaveEntities.addAll(ownedEntities.keySet().stream()
                    .filter(id -> {
                        Entity entity = ownedEntities.get(id);
                        return entity != null && isOfficialEntity(entity) && readInt(entity, keyWave, 0) == FINAL_WAVE_NUMBER;
                    }).toList());
            getLogger().info("FINAL_WAVE_STARTED event=" + eventId + " count=" + finalWaveEntities.size());
        } else if (!test) {
            if (wave <= 2) {
                waveObjectiveMobCount = countLiveWaveEntitiesForWave(wave);
            }
            if (pacedTowerWave) {
                getLogger().info("WAVE_STARTED event=" + eventId + " wave=" + wave
                        + " count=" + countLiveWaveEntitiesForWave(wave)
                        + " planned=" + towerSpawnSchedule.stream()
                        .mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum()
                        + " groups=" + towerSpawnSchedule.size());
            } else {
                getLogger().info("WAVE_STARTED event=" + eventId + " wave=" + wave
                        + " count=" + (endermen + elites + spiders + skeletons + eliteSkeletons));
            }
        }
    }

    private void spawnWaveGroup(World world, Location core, int wave, boolean finalWave, boolean test,
                                WaveMechanicsPolicy.WaveCounts group, int baseIndex) {
        if (world == null || core == null || group == null) {
            return;
        }
        String kind = finalWave ? EVENT_KIND_FINAL_WAVE : EVENT_KIND_WAVE_MOB;
        int nextIndex = baseIndex;
        for (int index = 0; index < group.endermen(); index++) {
            spawnEnderman(world, core, wave, false, finalWave, test, nextIndex, nextIndex);
            nextIndex++;
        }
        for (int index = 0; index < group.eliteEndermen(); index++) {
            spawnEnderman(world, core, wave, true, finalWave, test, nextIndex, nextIndex);
            nextIndex++;
        }
        for (int index = 0; index < group.eliteSkeletons(); index++) {
            spawnSkeleton(world, core, wave, true, finalWave, test, nextIndex, nextIndex);
            nextIndex++;
        }
        for (int index = 0; index < group.spiders(); index++) {
            spawnOwnedMob(world, core, EntityType.SPIDER, wave, kind, test, nextIndex);
            nextIndex++;
        }
        for (int index = 0; index < group.skeletons(); index++) {
            spawnSkeleton(world, core, wave, false, finalWave, test, nextIndex, nextIndex);
            nextIndex++;
        }
    }

    private void spawnNextTowerGroup(World world, Location core) {
        if (towerSpawnGroupIndex >= towerSpawnSchedule.size()) {
            return;
        }
        int groupNumber = towerSpawnGroupIndex + 1;
        WaveMechanicsPolicy.WaveCounts group = towerSpawnSchedule.get(towerSpawnGroupIndex);
        spawnWaveGroup(world, core, 4, false, false, group, towerSpawnEntityOffset);
        towerSpawnEntityOffset += group.total();
        towerSpawnGroupIndex++;
        long now = System.currentTimeMillis();
        if (towerSpawnGroupIndex < towerSpawnSchedule.size()) {
            List<Integer> cadence = WaveMechanicsPolicy.towerGroupCadenceSeconds();
            int cadenceIndex = Math.min(groupNumber - 1, cadence.size() - 1);
            towerNextSpawnAtMillis = now + cadence.get(cadenceIndex) * 1000L;
        } else {
            towerNextSpawnAtMillis = 0L;
        }
        getLogger().info("WAVE_TOWER_GROUP_SPAWN event=" + eventId
                + " wave=4 group=" + groupNumber + "/" + towerSpawnSchedule.size()
                + " spawned=" + group.total() + " total_spawned=" + towerSpawnEntityOffset
                + " composition=endermen:" + group.endermen()
                + ",spiders:" + group.spiders() + ",skeletons:" + group.skeletons()
                + ",elite_endermen:" + group.eliteEndermen()
                + ",elite_skeletons:" + group.eliteSkeletons()
                + " next_ms=" + towerNextSpawnAtMillis);
    }

    private void scheduleTowerSpawnGroups(World world, Location core) {
        cancelTowerSpawnTask();
        if (towerSpawnGroupIndex >= towerSpawnSchedule.size() || taskRegistry == null) {
            return;
        }
        long callbackGeneration = generation;
        towerSpawnTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || phase != EventPhase.WAVE_4 || activeWave != 4
                    || towerDefenseState == null
                    || towerDefenseState.outcome() != TowerDefensePolicy.Outcome.ACTIVE) {
                cancelTowerSpawnTask();
                return;
            }
            if (towerNextSpawnAtMillis > 0L && System.currentTimeMillis() >= towerNextSpawnAtMillis) {
                spawnNextTowerGroup(world, core);
                if (towerSpawnGroupIndex >= towerSpawnSchedule.size()) {
                    cancelTowerSpawnTask();
                }
            }
        }, 20L, 20L);
        taskRegistry.register(towerSpawnTask);
    }

    private void cancelTowerSpawnTask() {
        if (towerSpawnTask != null) {
            towerSpawnTask.cancel();
            towerSpawnTask = null;
        }
    }

    private void startWaveObjective(int wave, World world, Location core) {
        clearWaveObjectiveState();
        waveObjectiveStartedMillis = System.currentTimeMillis();
        waveObjectiveLastSecond = -1;
        waveObjectiveComplete = false;
        if (world == null || core == null || wave > 5) {
            return;
        }
        if (wave == 1) {
            waveOneNextPulseMillis = waveObjectiveStartedMillis
                    + randomSeconds(18, 22) * 1000L;
            announceEventTitle("§dПУЛЬС ЯДРА", "§fЧерез несколько секунд выберите безопасный сектор", true);
            getLogger().info("WAVE_OBJECTIVE_STARTED event=" + eventId
                    + " wave=1 type=CORE_PULSE interval_seconds=18-22 telegraph_ticks="
                    + WAVE_ONE_PULSE_TELEGRAPH_TICKS);
            return;
        }
        if (wave == 2) {
            waveTwoNextMarkMillis = waveObjectiveStartedMillis
                    + randomSeconds(WAVE_TWO_INITIAL_MARK_MIN_SECONDS, WAVE_TWO_INITIAL_MARK_MAX_SECONDS) * 1000L;
            getLogger().info("WAVE_OBJECTIVE_STARTED event=" + eventId
                    + " wave=2 type=MARKED_HUNT interval_seconds=12-16 mark_ticks="
                    + WAVE_TWO_MARK_DURATION_TICKS + " initial_mark_delay_seconds=2-4");
            return;
        }
        String objective = WaveObjectivePolicy.objective(wave).name();
        announceEventTitle("§dЦЕЛЬ: " + objective, "§fВыполните механику волны", true);
        if (wave == 3) {
            int portalCount = WaveMechanicsPolicy.portalCount(Math.max(2, officialRewardRoster.size()));
            Location portalAnchor = coreCombatAnchorLocation();
            if (portalAnchor == null) {
                getLogger().warning("WAVE_OBJECTIVE_REFUSED event=" + eventId
                        + " wave=3 reason=playable-floor-anchor-missing");
                return;
            }
            List<Location> portals = new ArrayList<>();
            List<PortalCapturePolicy.PortalState> states = new ArrayList<>();
            for (int index = 0; index < portalCount; index++) {
                double angle = Math.PI * 2.0D * index / portalCount;
                // coreLocation() is the Core's upper face.  On the elevated
                // local scene it is one block above the playable floor, so a
                // portal centered there makes players fall instead of being
                // counted inside the capture ring.
                portals.add(portalAnchor.clone().add(Math.cos(angle) * 8.0D,
                        0.0D, Math.sin(angle) * 8.0D));
                states.add(PortalCapturePolicy.initial());
            }
            wavePortals.put(wave, portals);
            portalCaptureStates.put(wave, states);
            spawnPortalObjectiveVisuals(world, portals);
            getLogger().info("WAVE_OBJECTIVE_STARTED event=" + eventId
                    + " wave=3 type=PORTALS portals=" + portalCount
                    + " floor_y=" + portalAnchor.getBlockY()
                    + " capture_ms=" + PortalCapturePolicy.CAPTURE_MILLIS
                    + " grace_ms=" + PortalCapturePolicy.GRACE_MILLIS);
        } else if (wave == 4) {
            long now = System.currentTimeMillis();
            TowerDefensePolicy.CoreState retry = pendingTowerDefenseRetry;
            if (retry != null && retry.outcome() == TowerDefensePolicy.Outcome.FAILURE) {
                towerDefenseState = TowerDefensePolicy.retry(retry, now);
                pendingTowerDefenseRetry = null;
                getLogger().info("WAVE_RETRY_OBJECTIVE_RESET event=" + eventId
                        + " wave=4 previous_attempt=" + retry.attempt()
                        + " attempt=" + towerDefenseState.attempt()
                        + " core_hp=" + towerDefenseState.maxHealth()
                        + " deadline=" + towerDefenseState.deadlineMillis());
            } else {
                towerDefenseState = TowerDefensePolicy.start(
                        Math.max(2, officialRewardRoster.size()), now);
            }
            towerAttackSequence = 0;
            towerNextAttackAt.clear();
            towerAttackSequences.clear();
            towerAggroUntil.clear();
            getLogger().info("WAVE_OBJECTIVE_STARTED event=" + eventId
                    + " wave=4 type=TOWER_DEFENSE core_hp=" + towerDefenseState.maxHealth()
                    + " deadline=" + towerDefenseState.deadlineMillis());
        } else if (wave == 5) {
            planRiftStorm(world);
            nextRiftStormPullMillis = waveObjectiveStartedMillis + 3_000L;
            getLogger().info("WAVE_OBJECTIVE_STARTED event=" + eventId
                    + " wave=5 type=RIFT_STORM hazards=" + riftStormHazards.size()
                    + " safe=" + riftStormSafeCells.size());
        }
    }

    private void spawnPortalObjectiveVisuals(World world, List<Location> portals) {
        if (world == null || portals == null) {
            return;
        }
        for (int index = 0; index < portals.size(); index++) {
            Location location = portals.get(index);
            TextDisplay display = world.spawn(location.clone().add(0.0D, 0.15D, 0.0D), TextDisplay.class);
            display.setText("§5✦ ВРАТА РАЗЛОМА " + (index + 1) + "\n§7Захват: 0%\n§8Встаньте внутрь кольца");
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(48.0F);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setDefaultBackground(false);
            display.setLineWidth(180);
            display.setPersistent(true);
            display.setInvulnerable(true);
            tag(display, EVENT_KIND_DISPLAY, 3, false);
            waveObjectiveVisuals.add(display.getUniqueId());
            waveObjectiveVisualTexts.put(display.getUniqueId(), display.getText());
        }
    }

    private void refreshPortalObjectiveVisuals(List<PortalCapturePolicy.PortalState> states) {
        if (states == null || states.isEmpty()) {
            return;
        }
        for (int index = 0; index < states.size(); index++) {
            PortalCapturePolicy.PortalState state = states.get(index);
            if (index >= wavePortals.getOrDefault(3, List.of()).size()) {
                break;
            }
            UUID visualId = waveObjectiveVisuals.stream().skip(index).findFirst().orElse(null);
            if (visualId == null) {
                continue;
            }
            Entity entity = ownedEntities.get(visualId);
            if (!(entity instanceof TextDisplay display) || !entity.isValid()) {
                continue;
            }
            int percent = (int) Math.min(100L,
                    state.progressMillis() * 100L / Math.max(1L, PortalCapturePolicy.CAPTURE_MILLIS));
            String text = state.completed()
                    ? "§a✦ ВРАТА РАЗЛОМА " + (index + 1) + "\n§aЗАПЕЧАТАНО"
                    : "§5✦ ВРАТА РАЗЛОМА " + (index + 1) + "\n§dЗахват: " + percent + "%\n§8Встаньте внутрь кольца";
            if (!text.equals(waveObjectiveVisualTexts.get(visualId))) {
                display.setText(text);
                waveObjectiveVisualTexts.put(visualId, text);
            }
        }
    }

    private void planRiftStorm(World world) {
        riftStormHazards.clear();
        riftStormSafeCells.clear();
        riftStormOriginalBlocks.clear();
        riftStormOriginalWebBlocks.clear();
        riftStormLastDamageSecond.clear();
        int minX = Math.max(arenaMinX, coreX - (int) Math.floor(config.arenaRadius()));
        int maxX = Math.min(arenaMaxX, coreX + (int) Math.floor(config.arenaRadius()));
        int minZ = Math.max(arenaMinZ, coreZ - (int) Math.floor(config.arenaRadius()));
        int maxZ = Math.min(arenaMaxZ, coreZ + (int) Math.floor(config.arenaRadius()));
        int stormFloorY = combatFloorY();
        Map<HazardPlanner.Point, String> originals = new LinkedHashMap<>();
        Set<HazardPlanner.Point> protectedPoints = new LinkedHashSet<>();
        protectedPoints.add(new HazardPlanner.Point(coreX, coreZ));
        for (EventSnapshot.PadSnapshot pad : pads) {
            protectedPoints.add(new HazardPlanner.Point(pad.x(), pad.z()));
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block floor = world.getBlockAt(x, stormFloorY, z);
                if (floor.getType().isSolid() && !floor.isLiquid()) {
                    originals.put(new HazardPlanner.Point(x, z), floor.getBlockData().getAsString());
                }
            }
        }
        if (originals.isEmpty()) {
            return;
        }
        long patternSeed = eventId.hashCode() * 31L + generation + stormPatternPhase++;
        lastStormPattern = StormPatternPolicy.nextPattern(lastStormPattern, patternSeed);
        StormPatternPolicy.Bounds stormBounds = new StormPatternPolicy.Bounds(minX, maxX, minZ, maxZ);
        Set<HazardPlanner.Point> patternCells = StormPatternPolicy.patternCells(
                stormBounds, lastStormPattern, stormPatternPhase, patternSeed);
        Map<HazardPlanner.Point, String> patternOriginals = new LinkedHashMap<>();
        for (Map.Entry<HazardPlanner.Point, String> entry : originals.entrySet()) {
            if (patternCells.contains(entry.getKey())) {
                patternOriginals.put(entry.getKey(), entry.getValue());
            }
        }
        if (patternOriginals.isEmpty()) {
            // A very small custom arena may not contain a full pattern.  The
            // bounded planner remains the safe fallback, while the pattern
            // name is still recorded for diagnostics.
            patternOriginals.putAll(originals);
        }
        HazardPlanner.Plan plan = HazardPlanner.plan(minX, maxX, minZ, maxZ, protectedPoints,
                new HazardPlanner.Pattern(patternOriginals),
                Math.max(1, WaveMechanicsPolicy.floorMutationCap(originals.size())),
                StormPatternPolicy.minimumSafeRatio(), patternSeed);
        riftStormHazards.addAll(plan.hazardCells());
        riftStormSafeCells.addAll(plan.safeCells());
        riftStormOriginalBlocks.putAll(plan.originalBlocks());
        applyRiftStormPlan(world);
        getLogger().info("WAVE_STORM_PATTERN event=" + eventId + " wave=5 pattern=" + lastStormPattern
                + " candidates=" + patternOriginals.size() + " hazards=" + riftStormHazards.size()
                + " safe=" + riftStormSafeCells.size());
    }

    /**
     * Apply the already-planned, bounded Wave V mutation.  The floor and the
     * one-block cobweb layer are journaled before changing them.  The event
     * never creates a spreading FIRE block and never mutates outside the
     * planner's arena rectangle.
     */
    private void applyRiftStormPlan(World world) {
        if (world == null || riftStormHazards.isEmpty()) {
            return;
        }
        Set<HazardPlanner.Point> webCells = WaveMechanicsPolicy.selectWebCells(
                riftStormHazards, protectedStormPoints(), occupiedStormPoints(),
                eventId.hashCode() * 31L + generation + stormPatternPhase);
        int stormFloorY = combatFloorY();
        List<HazardMutationJournal.Entry> journalEntries = new ArrayList<>();
        for (HazardPlanner.Point point : riftStormHazards) {
            Block floor = world.getBlockAt(point.x(), stormFloorY, point.z());
            String originalFloor = riftStormOriginalBlocks.get(point);
            if (originalFloor == null || !floor.getBlockData().getAsString().equals(originalFloor)) {
                continue;
            }
            Block web = world.getBlockAt(point.x(), stormFloorY + 1, point.z());
            String originalWeb = "";
            if (webCells.contains(point) && web.isPassable() && !web.isLiquid()
                    && !isCoreBlockPosition(web.getLocation())) {
                originalWeb = web.getBlockData().getAsString();
                riftStormOriginalWebBlocks.put(point, originalWeb);
            }
            journalEntries.add(new HazardMutationJournal.Entry(
                    point.x(), floor.getY(), point.z(), originalFloor, originalWeb));
        }
        if (journalEntries.isEmpty()) {
            return;
        }
        if (hazardJournal == null || !hazardJournal.prepare(eventId, generation, world.getName(), journalEntries)) {
            getLogger().severe("WAVE_HAZARDS_REFUSED event=" + eventId
                    + " wave=5 reason=journal-prepare-failed");
            return;
        }
        try {
            for (HazardMutationJournal.Entry entry : journalEntries) {
                Block floor = world.getBlockAt(entry.x(), entry.floorY(), entry.z());
                floor.setType(Material.MAGMA_BLOCK, false);
                if (entry.hasWebMutation()) {
                    world.getBlockAt(entry.x(), entry.floorY() + 1, entry.z())
                            .setType(Material.COBWEB, false);
                }
            }
            if (!hazardJournal.markApplied()) {
                getLogger().severe("WAVE_HAZARDS_APPLIED_JOURNAL_FAILED event=" + eventId
                        + " wave=5 journal=" + hazardJournal.path());
            }
        } catch (RuntimeException error) {
            getLogger().log(Level.SEVERE, "WAVE_HAZARDS_APPLY_FAILED event=" + eventId, error);
            restoreRiftStormBlocks();
            return;
        }
        getLogger().info("WAVE_HAZARDS_APPLIED event=" + eventId
                + " wave=5 floor_blocks=" + journalEntries.size()
                + " webs=" + riftStormOriginalWebBlocks.size()
                + " web_cap=" + WaveMechanicsPolicy.webCap()
                + " floor_y=" + stormFloorY
                + " safe_ratio=" + (riftStormSafeCells.size()
                / (double) Math.max(1, riftStormHazards.size() + riftStormSafeCells.size())));
    }

    private Set<HazardPlanner.Point> protectedStormPoints() {
        Set<HazardPlanner.Point> protectedPoints = new LinkedHashSet<>();
        protectedPoints.add(new HazardPlanner.Point(coreX, coreZ));
        for (EventSnapshot.PadSnapshot pad : pads) {
            protectedPoints.add(new HazardPlanner.Point(pad.x(), pad.z()));
        }
        return protectedPoints;
    }

    private Set<HazardPlanner.Point> occupiedStormPoints() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Set.of();
        }
        Set<HazardPlanner.Point> occupied = new LinkedHashSet<>();
        for (Player player : activeLivingPlayers()) {
            if (player.getWorld().equals(world)) {
                occupied.add(new HazardPlanner.Point(
                        player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
            }
        }
        return occupied;
    }

    /** Restore only blocks that still contain our temporary marker material. */
    private void restoreRiftStormBlocks() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        int restoredFloors = 0;
        int restoredWebs = 0;
        int stormFloorY = combatFloorY();
        for (Map.Entry<HazardPlanner.Point, String> entry : riftStormOriginalBlocks.entrySet()) {
            Block floor = world.getBlockAt(entry.getKey().x(), stormFloorY, entry.getKey().z());
            if (floor.getType() == Material.MAGMA_BLOCK) {
                restoreBlock(floor, entry.getValue());
                restoredFloors++;
            }
        }
        for (Map.Entry<HazardPlanner.Point, String> entry : riftStormOriginalWebBlocks.entrySet()) {
            Block web = world.getBlockAt(entry.getKey().x(), stormFloorY + 1, entry.getKey().z());
            if (web.getType() == Material.COBWEB) {
                restoreBlock(web, entry.getValue());
                restoredWebs++;
            }
        }
        if (restoredFloors > 0 || restoredWebs > 0) {
            getLogger().info("WAVE_HAZARDS_RESTORED event=" + eventId
                    + " wave=5 floor_blocks=" + restoredFloors + " webs=" + restoredWebs);
        }
        if (hazardJournal != null) {
            if (!hazardJournal.markRestored()) {
                getLogger().severe("WAVE_HAZARDS_RESTORE_JOURNAL_FAILED event=" + eventId
                        + " journal=" + hazardJournal.path());
            }
        }
    }

    private boolean tickWaveObjective() {
        if (testCombatAiMode || activeWave < 1 || activeWave > 5) {
            return true;
        }
        if (waveObjectiveComplete) {
            return true;
        }
        if (waveObjectiveStartedMillis <= 0L) {
            // A failed Tower Defense clears the objective before scheduling
            // its respawn. Do not eagerly create an empty replacement here:
            // that would cancel the owned retry task in startWaveObjective.
            if (towerRetryTask != null) {
                return false;
            }
            World world = Bukkit.getWorld(worldName);
            Location core = coreLocation();
            if (world != null && core != null) {
                startWaveObjective(activeWave, world, core);
            }
        }
        long now = System.currentTimeMillis();
        int second = (int) Math.max(0L, (now - waveObjectiveStartedMillis) / 1000L);
        waveObjectiveLastSecond = second;
        switch (activeWave) {
            case 1 -> updateCorePulseObjective(now);
            case 2 -> updateMarkedTargetObjective(now);
            case 3 -> updatePortalObjective(now);
            case 4 -> updateTowerObjective(now);
            case 5 -> updateRiftStormObjective(now);
            default -> {
            }
        }
        if (activeWave <= 2) {
            if (waveObjectiveMobCount < 1) {
                waveObjectiveMobCount = countLiveWaveEntitiesForWave(activeWave);
            }
            waveObjectiveComplete = waveObjectiveMobCount > 0
                    && countLiveWaveEntitiesForWave(activeWave) == 0;
        }
        return waveObjectiveComplete;
    }

    private int countLiveWaveEntitiesForWave(int wave) {
        int count = 0;
        for (Entity entity : ownedEntities.values()) {
            String kind = readString(entity, keyKind);
            if ((EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind))
                    && readInt(entity, keyWave, 0) == wave
                    && isLiveOwnedEntity(entity.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    private void updateCorePulseObjective(long now) {
        Location core = coreCombatAnchorLocation();
        if (core == null) {
            return;
        }
        if (waveOnePulseDeadlineMillis <= 0L && now >= waveOneNextPulseMillis) {
            waveOnePulseDeadlineMillis = now + WAVE_ONE_PULSE_TELEGRAPH_TICKS * 50L;
            announceEventTitle("§4ПУЛЬС ЯДРА", "§fЗаймите безопасный сектор — удар через 2,5 сек.", true);
            getLogger().info("WAVE_OBJECTIVE_TELEGRAPH event=" + eventId
                    + " wave=1 type=CORE_PULSE pulse=" + waveOnePulseIndex);
        }
        if (waveOnePulseDeadlineMillis > 0L) {
            boolean impact = now >= waveOnePulseDeadlineMillis;
            for (Player player : eventAudience()) {
                if (!player.getWorld().equals(core.getWorld())
                        || horizontalDistanceSquared(player.getLocation(), core)
                        > config.arenaRadius() * config.arenaRadius()) {
                    continue;
                }
                boolean safe = isWaveOneSafeSector(player.getLocation(), core, waveOnePulseIndex);
                player.spawnParticle(safe ? Particle.END_ROD : Particle.DRAGON_BREATH,
                        player.getLocation().add(0.0D, 0.15D, 0.0D), impact ? 16 : 6,
                        0.45D, 0.1D, 0.45D, 0.02D);
                sendObjectiveActionBar(player, impact ? "Пульс ядра: УДАР" :
                        "Пульс ядра: " + (safe ? "БЕЗОПАСНЫЙ СЕКТОР" : "БЕГИТЕ К СВЕТУ"),
                        safe ? NamedTextColor.AQUA : NamedTextColor.RED, now);
                if (impact && !safe) {
                    player.damage(WAVE_ONE_PULSE_DAMAGE);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            SLOWNESS_DEBUFF_TICKS, 1, false, true, true));
                }
            }
            if (impact) {
                core.getWorld().playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9F, 0.65F);
                waveOnePulseDeadlineMillis = 0L;
                waveOnePulseIndex++;
                waveOneNextPulseMillis = now + randomSeconds(18, 22) * 1000L;
                getLogger().info("WAVE_OBJECTIVE_IMPACT event=" + eventId
                        + " wave=1 type=CORE_PULSE damage=" + WAVE_ONE_PULSE_DAMAGE
                        + " next_ms=" + waveOneNextPulseMillis);
            }
        }
    }

    private boolean isWaveOneSafeSector(Location player, Location core, int pulse) {
        double angle = Math.atan2(player.getZ() - core.getZ(), player.getX() - core.getX());
        int sector = Math.floorMod((int) Math.floor((angle + Math.PI) / (Math.PI / 2.0D)), 4);
        return sector == Math.floorMod(pulse, 4);
    }

    private void updateMarkedTargetObjective(long now) {
        if (waveTwoMarkedPlayerUuid == null || now >= waveTwoMarkDeadlineMillis) {
            if (now >= waveTwoNextMarkMillis) {
                List<Player> candidates = activeLivingPlayers();
                if (!candidates.isEmpty()) {
                    Player marked = candidates.get(Math.floorMod(waveTargetCursor++, candidates.size()));
                    waveTwoMarkedPlayerUuid = marked.getUniqueId();
                    waveTwoMarkRevealDeadlineMillis = now + WAVE_TWO_MARK_REVEAL_TICKS * 50L;
                    waveTwoMarkDeadlineMillis = now + WAVE_TWO_MARK_DURATION_TICKS * 50L;
                    marked.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                            WAVE_TWO_MARK_REVEAL_TICKS, 0, false, true, true));
                    marked.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                            WAVE_TWO_MARK_REVEAL_TICKS, 0, false, true, true));
                    marked.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                            WAVE_TWO_MARK_REVEAL_TICKS, 0, false, true, true));
                    marked.playSound(marked.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.8F, 1.35F);
                    marked.sendActionBar(Component.text("Вас отметил Разлом — бегите и выживайте 11 секунд",
                            NamedTextColor.LIGHT_PURPLE));
                    getLogger().info("WAVE_OBJECTIVE_MARK event=" + eventId + " wave=2 player="
                            + marked.getUniqueId() + " deadline=" + waveTwoMarkDeadlineMillis);
                }
                waveTwoNextMarkMillis = now + randomSeconds(12, 16) * 1000L;
            }
            return;
        }
        Player marked = Bukkit.getPlayer(waveTwoMarkedPlayerUuid);
        if (marked == null || !isCombatTarget(marked)) {
            waveTwoMarkedPlayerUuid = null;
            return;
        }
        marked.sendActionBar(Component.text("Метка Разлома: "
                + Math.max(0L, (waveTwoMarkDeadlineMillis - now + 999L) / 1000L) + " сек.",
                NamedTextColor.LIGHT_PURPLE));
        if (now < waveTwoMarkRevealDeadlineMillis) {
            marked.spawnParticle(Particle.END_ROD, marked.getLocation().add(0.0D, 1.0D, 0.0D),
                    8, 0.35D, 0.6D, 0.35D, 0.02D);
        }
    }

    private void sendObjectiveActionBar(Player player, String text, NamedTextColor color, long now) {
        if (player != null && now - objectiveActionBarAt.getOrDefault(player.getUniqueId(), 0L) >= 200L) {
            player.sendActionBar(Component.text(text, color));
            objectiveActionBarAt.put(player.getUniqueId(), now);
        }
    }

    private void updatePortalObjective(long now) {
        boolean wasComplete = waveObjectiveComplete;
        List<Location> portals = wavePortals.getOrDefault(3, List.of());
        List<PortalCapturePolicy.PortalState> states = portalCaptureStates.getOrDefault(3, List.of());
        if (portals.size() != states.size() || portals.isEmpty()) {
            waveObjectiveComplete = false;
            return;
        }
        List<PortalCapturePolicy.PortalState> updated = new ArrayList<>();
        int completed = 0;
        for (int index = 0; index < portals.size(); index++) {
            Location portal = portals.get(index);
            boolean occupied = activeLivingPlayers().stream().anyMatch(player ->
                    horizontalDistanceSquared(player.getLocation(), portal) <= 2.5D * 2.5D
                            && Math.abs(player.getLocation().getY() - portal.getY()) <= 1.5D);
            PortalCapturePolicy.PortalState state = PortalCapturePolicy.tick(states.get(index), occupied, now);
            updated.add(state);
            if (state.completed()) {
                completed++;
            }
            for (Player player : eventAudience()) {
                if (player.getWorld().equals(portal.getWorld())) {
                    player.spawnParticle(state.completed() ? Particle.END_ROD : Particle.REVERSE_PORTAL,
                            portal.clone().add(0.0D, 0.2D, 0.0D), occupied ? 8 : 3,
                            0.45D, 0.15D, 0.45D, 0.02D);
                }
            }
        }
        portalCaptureStates.put(3, updated);
        refreshPortalObjectiveVisuals(updated);
        for (Player player : eventAudience()) {
            sendObjectiveActionBar(player, "Порталы Разлома: " + completed + "/" + portals.size()
                    + " · удерживайте портал 5 сек.", NamedTextColor.LIGHT_PURPLE, now);
        }
        waveObjectiveComplete = completed == portals.size();
        if (waveObjectiveComplete && !wasComplete) {
            announceEventTitle("§aПОРТАЛЫ ЗАПЕЧАТАНЫ", "§fПобедите оставшихся слуг Разлома", true);
            getLogger().info("WAVE_OBJECTIVE_COMPLETE event=" + eventId
                    + " wave=3 type=PORTALS completed=" + completed
                    + " portals=" + portals.size()
                    + " capture_ms=" + PortalCapturePolicy.CAPTURE_MILLIS);
        }
    }

    private void updateTowerObjective(long now) {
        if (towerDefenseState == null || towerDefenseState.outcome() != TowerDefensePolicy.Outcome.ACTIVE) {
            return;
        }
        Location core = coreCombatAnchorLocation();
        if (core == null) {
            return;
        }
        TowerDefensePolicy.CoreState state = towerDefenseState;
        int attackers = 0;
        int attacksApplied = 0;
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            if (!isWaveCombatKind(readString(entity, keyKind))
                    || !isLiveOwnedEntity(entity.getUniqueId())
                    || entity.getWorld() == null || !entity.getWorld().equals(core.getWorld())) {
                continue;
            }
            WaveMechanicsPolicy.TowerRole role = towerRole(entity);
            if (role == null) {
                continue;
            }
            // A player who has just damaged a tower mob has successfully
            // pulled its attention away from the Core.  Do not let the
            // objective controller keep applying Core damage while vanilla
            // navigation is honoring that short, visible aggro window.
            if (hasTowerPlayerAggro(entity, now)
                    || findNearestCombatPlayer(entity, TOWER_PLAYER_ALERT_RADIUS) != null) {
                continue;
            }
            double range = role.coreAttackRange();
            if (horizontalDistanceSquared(entity.getLocation(), core) > range * range
                    || Math.abs(entity.getLocation().getY() - core.getY()) > 2.0D) {
                continue;
            }
            attackers++;
            long nextAttack = towerNextAttackAt.computeIfAbsent(entity.getUniqueId(), ignored ->
                    now + role.attackIntervalMillis());
            if (now < nextAttack) {
                continue;
            }
            int attackSequence = towerAttackSequences.merge(entity.getUniqueId(), 1, Integer::sum);
            String attackId = entity.getUniqueId() + ":tower:" + state.attempt() + ":" + attackSequence;
            double damage = WaveMechanicsPolicy.roleDamage(role, entity.getUniqueId().toString(), attackSequence);
            state = TowerDefensePolicy.damage(state, attackId, damage);
            towerNextAttackAt.put(entity.getUniqueId(), now + role.attackIntervalMillis());
            attacksApplied++;
            entity.getPersistentDataContainer().set(keyTowerAttackAt, PersistentDataType.LONG,
                    now + role.attackIntervalMillis());
            entity.getPersistentDataContainer().set(keyTowerAttackSequence, PersistentDataType.INTEGER,
                    attackSequence);
            getLogger().info("WAVE_OBJECTIVE_TOWER_ATTACK event=" + eventId
                    + " entity=" + entity.getUniqueId() + " role=" + role
                    + " damage=" + damage + " sequence=" + attackSequence
                    + " range=" + range + " attempt=" + state.attempt());
        }
        towerDefenseState = state;
        if (state.currentHealth() <= 0.0D) {
            towerDefenseState = TowerDefensePolicy.finish(state, now);
            handleTowerDefenseFailure();
            return;
        }
        if (now >= state.deadlineMillis()) {
            // A server tick normally lands after the wall-clock deadline.
            // Evaluate the durable defense at the deadline itself so healthy
            // cores are not falsely marked as failed because of tick drift.
            towerDefenseState = TowerDefensePolicy.completeAtDeadline(state, now);
            if (towerDefenseState.outcome() == TowerDefensePolicy.Outcome.FAILURE) {
                handleTowerDefenseFailure();
                return;
            }
            waveObjectiveComplete = true;
            announceEventTitle("§aБАШНЯ УДЕРЖАНА", "§fРазлом отступает — добейте оставшихся слуг", true);
            getLogger().info("WAVE_OBJECTIVE_COMPLETE event=" + eventId
                    + " wave=4 type=TOWER_DEFENSE attackers=" + attackers
                    + " attacks=" + attacksApplied + " attempt=" + towerDefenseState.attempt()
                    + " observed_late=true");
            return;
        }
        int remaining = (int) Math.max(0L, (state.deadlineMillis() - now + 999L) / 1000L);
        for (Player player : eventAudience()) {
            sendObjectiveActionBar(player, "Защита ядра: "
                    + Math.round(state.currentHealth()) + "/" + Math.round(state.maxHealth())
                    + " · " + remaining + " сек.", NamedTextColor.YELLOW, now);
            if (player.getWorld().equals(core.getWorld())) {
                player.spawnParticle(Particle.END_ROD, core.clone().add(0.0D, 0.8D, 0.0D),
                        attackers > 0 ? 10 : 3, 0.65D, 0.45D, 0.65D, 0.02D);
            }
        }
    }

    private void handleTowerDefenseFailure() {
        TowerDefensePolicy.CoreState failed = towerDefenseState;
        waveObjectiveComplete = false;
        cancelTowerSpawnTask();
        getLogger().warning("WAVE_OBJECTIVE_FAILED event=" + eventId
                + " wave=4 type=TOWER_DEFENSE attempt="
                + (failed == null ? "unknown" : failed.attempt())
                + " reason=core-destroyed retry=true blindness_ticks=100");
        announceEventTitle("§4ЯДРО ПРОРВАНО", "§fВолна 4 будет перезапущена", true);
        for (Player player : eventAudience()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, true, true));
        }
        clearWaveEntities();
        pendingTowerDefenseRetry = failed;
        if (taskRegistry == null) {
            return;
        }
        long callbackGeneration = generation;
        towerRetryTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            towerRetryTask = null;
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || phase != EventPhase.WAVE_4 || activeWave != 4) {
                return;
            }
            World world = Bukkit.getWorld(worldName);
            Location core = coreLocation();
            if (world != null && core != null) {
                announceEventTitle("§eВОЛНА 4: НОВАЯ ПОПЫТКА", "§fТеперь удержите ядро", true);
                getLogger().info("WAVE_RETRY_STARTED event=" + eventId
                        + " wave=4 previous_attempt="
                        + (failed == null ? "unknown" : failed.attempt()));
                spawnWave(4, false);
            }
        }, 100L);
        taskRegistry.register(towerRetryTask);
    }

    private void updateRiftStormObjective(long now) {
        if (waveObjectiveStartedMillis <= 0L) {
            return;
        }
        int elapsed = Math.max(0, (int) ((now - waveObjectiveStartedMillis) / 1000L));
        Location core = coreCombatAnchorLocation();
        if (core != null && now >= nextRiftStormPullMillis) {
            int pulled = 0;
            for (Player player : activeLivingPlayers()) {
                if (player.getWorld().equals(core.getWorld())
                        && horizontalDistanceSquared(player.getLocation(), core)
                        <= config.arenaRadius() * config.arenaRadius()) {
                    Vector pull = core.toVector().subtract(player.getLocation().toVector());
                    if (pull.lengthSquared() > 0.25D) {
                        player.setVelocity(pull.normalize().multiply(0.18D).setY(0.12D));
                        pulled++;
                    }
                }
            }
            if (pulled > 0) {
                spawnEventParticle(core.clone().add(0.0D, 0.7D, 0.0D), Particle.REVERSE_PORTAL,
                        24, 1.2D, 0.35D, 1.2D, 0.02D);
                core.getWorld().playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                        0.45F, 0.65F);
            }
            getLogger().info("WAVE_STORM_PULL event=" + eventId + " players=" + pulled
                    + " next_ms=" + (now + 5_000L));
            nextRiftStormPullMillis = now + 5_000L;
        }
        for (Player player : eventAudience()) {
            if (!isActiveArenaParticipant(player)) {
                continue;
            }
            HazardPlanner.Point point = new HazardPlanner.Point(
                    player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            boolean danger = riftStormHazards.contains(point);
            player.spawnParticle(danger ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD,
                    player.getLocation().add(0.0D, 0.15D, 0.0D), danger ? 12 : 5,
                    0.45D, 0.05D, 0.45D, 0.01D);
            player.sendActionBar(Component.text(danger
                    ? "Шторм Разлома: ОПАСНАЯ зона · ищите безопасный свет"
                    : "Шторм Разлома: безопасная зона · " + Math.max(0, 30 - elapsed) + " сек.",
                    danger ? NamedTextColor.RED : NamedTextColor.AQUA));
            if (danger && riftStormLastDamageSecond.getOrDefault(player.getUniqueId(), -1) != elapsed) {
                player.damage(4.0D);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOWNESS_DEBUFF_TICKS,
                        configuredDebuffAmplifier(), false, true, true));
                riftStormLastDamageSecond.put(player.getUniqueId(), elapsed);
            }
        }
        if (elapsed >= 30) {
            waveObjectiveComplete = true;
            restoreRiftStormBlocks();
            riftStormHazards.clear();
            riftStormSafeCells.clear();
            riftStormOriginalBlocks.clear();
            riftStormOriginalWebBlocks.clear();
            riftStormLastDamageSecond.clear();
            announceEventTitle("§bШТОРМ РАССЕЯН", "§fДобейте оставшихся слуг Разлома", true);
            getLogger().info("WAVE_OBJECTIVE_COMPLETE event=" + eventId
                    + " wave=5 type=RIFT_STORM duration_seconds=" + elapsed);
        }
    }

    private void clearWaveObjectiveState() {
        cancelTowerSpawnTask();
        if (towerRetryTask != null) {
            towerRetryTask.cancel();
            towerRetryTask = null;
        }
        restoreRiftStormBlocks();
        wavePortals.clear();
        portalCaptureStates.clear();
        riftStormHazards.clear();
        riftStormSafeCells.clear();
        riftStormOriginalBlocks.clear();
        riftStormOriginalWebBlocks.clear();
        riftStormLastDamageSecond.clear();
        nextRiftStormPullMillis = 0L;
        towerDefenseState = null;
        towerSpawnSchedule = List.of();
        towerSpawnGroupIndex = 0;
        towerSpawnEntityOffset = 0;
        towerNextSpawnAtMillis = 0L;
        waveObjectiveStartedMillis = 0L;
        waveObjectiveComplete = false;
        waveObjectiveLastSecond = -1;
        waveObjectiveMobCount = 0;
        objectiveActionBarAt.clear();
        for (UUID visualId : new HashSet<>(waveObjectiveVisuals)) {
            Entity visual = ownedEntities.remove(visualId);
            if (visual != null && visual.isValid()) {
                visual.remove();
            }
        }
        waveObjectiveVisuals.clear();
        waveObjectiveVisualTexts.clear();
        waveOneNextPulseMillis = 0L;
        waveOnePulseDeadlineMillis = 0L;
        waveOnePulseIndex = 0;
        waveTwoMarkedPlayerUuid = null;
        waveTwoNextMarkMillis = 0L;
        waveTwoMarkRevealDeadlineMillis = 0L;
        waveTwoMarkDeadlineMillis = 0L;
    }

    private void scheduleOfficialBossSpawn() {
        if (phase != EventPhase.BOSS_CINEMATIC) {
            getLogger().warning("BOSS_SPAWN_DELAY_REFUSED event=" + eventId
                    + " reason=phase-not-cinematic phase=" + phase);
            return;
        }
        cancelBossSpawnTask();
        long callbackGeneration = generation;
        long now = System.currentTimeMillis();
        if (phaseDeadlineMillis <= 0L) {
            phaseDeadlineMillis = now + BOSS_CINEMATIC_DURATION_TICKS * 50L;
            saveStateAsync();
        }
        announceEventTitle("§5ПОСЛЕДНИЙ РУБЕЖ ПАЛ", "§dРазлом раскрывает своё сердце...", true);
        playEventMusic(phaseMusicOrLegacy("boss-cinematic", config.bossMusic()));
        getLogger().info("BOSS_CINEMATIC_STARTED event=" + eventId + " ticks=" + BOSS_CINEMATIC_DURATION_TICKS
                + " generation=" + callbackGeneration);
        int elapsedAtSchedule = (int) Math.max(0L, Math.min(BOSS_CINEMATIC_DURATION_TICKS,
                BOSS_CINEMATIC_DURATION_TICKS - Math.max(0L,
                        (phaseDeadlineMillis - now) / 50L)));
        final int[] elapsedTicks = {elapsedAtSchedule};
        bossSpawnTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || phase != EventPhase.BOSS_CINEMATIC || liveBoss() != null) {
                bossSpawnTask.cancel();
                bossSpawnTask = null;
                return;
            }
            renderBossCinematic(elapsedTicks[0]);
            if (elapsedTicks[0] == 80) {
                announceEventTitle("§4РАЗЛОМ ДЫШИТ", "§fПол под ногами уже не ваш", false);
            } else if (elapsedTicks[0] == 140) {
                announceEventTitle("§dОН СЛЫШИТ ВАС", "§5Не отступайте от ядра", false);
            }
            if (elapsedTicks[0] >= BOSS_CINEMATIC_DURATION_TICKS) {
                bossSpawnTask.cancel();
                bossSpawnTask = null;
                if (!transition(EventPhase.FINAL_WAVE, "boss cinematic complete; final wave", eventId + ":final-wave")) {
                    getLogger().severe("BOSS_CINEMATIC_HANDOFF_FAILED event=" + eventId
                            + " generation=" + callbackGeneration);
                    forcePhase(EventPhase.RECOVERY_REQUIRED, "boss cinematic handoff failed");
                    return;
                }
                phaseDeadlineMillis = 0L;
                saveStateAsync();
                announceEventTitle("§4ФИНАЛЬНАЯ ВОЛНА",
                        "§fПоследняя стража прикрывает пробуждение Хранителя", true);
                spawnWave(FINAL_WAVE_NUMBER, false);
                return;
            }
            elapsedTicks[0] += 20;
        }, 0L, 20L);
        if (taskRegistry != null) {
            taskRegistry.register(bossSpawnTask);
        }
    }

    private void renderBossCinematic(int elapsedTicks) {
        Location core = coreCombatAnchorLocation();
        if (core == null) {
            return;
        }
        double radius = Math.min(8.0D, 1.5D + elapsedTicks / 35.0D);
        for (Player player : eventAudience()) {
            if (!player.getWorld().equals(core.getWorld())) {
                continue;
            }
            player.sendActionBar(Component.text("Разлом раскрывается... "
                    + Math.max(0, (BOSS_SPAWN_DELAY_TICKS - elapsedTicks) / 20) + " сек.",
                    NamedTextColor.DARK_PURPLE));
            for (int point = 0; point < 16; point++) {
                double angle = Math.PI * 2.0D * point / 16.0D + elapsedTicks * 0.015D;
                Location ring = core.clone().add(Math.cos(angle) * radius,
                        0.15D + Math.sin(elapsedTicks * 0.08D + point) * 0.2D,
                        Math.sin(angle) * radius);
                player.spawnParticle(Particle.REVERSE_PORTAL, ring, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
            player.spawnParticle(Particle.DRAGON_BREATH, core.clone().add(0.0D, 0.8D, 0.0D),
                    8, 0.4D, 0.5D, 0.4D, 0.02D);
        }
    }

    private void cancelBossSpawnTask() {
        if (bossSpawnTask != null) {
            bossSpawnTask.cancel();
            bossSpawnTask = null;
        }
    }

    private void announceWaveStart(int wave, boolean finalWave) {
        String subtitle = finalWave ? "Финальная стража уже внутри" : switch (wave) {
            case 1 -> "Тени собираются у ядра";
            case 2 -> "Пустота режет пространство";
            case 3 -> "Охотники Разлома вышли на след";
            case 4 -> "Арена больше не подчиняется миру";
            case 5 -> "Последний рубеж перед Хранителем";
            default -> "Разлом выпускает своих слуг";
        };
        announceEventTitle(finalWave ? "§4ФИНАЛЬНАЯ ВОЛНА" : "§dВОЛНА " + wave,
                "§f" + subtitle, true);
    }

    private void announceWaveComplete(int wave) {
        announceEventTitle("§aВОЛНА " + wave + " ПОВЕРЖЕНА",
                "§eНаграда появилась у ядра", true);
    }

    private void announceEventTitle(String title, String subtitle, boolean chat) {
        for (Player player : eventAudience()) {
            player.sendTitle(title, subtitle, 5, 45, 10);
            if (chat) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + title.replace("§", "")
                        + ChatColor.GRAY + " — " + ChatColor.WHITE + subtitle.replace("§", ""));
            }
        }
    }

    private List<Player> eventAudience() {
        Set<UUID> audience = new LinkedHashSet<>();
        audience.addAll(padOccupants.values());
        audience.addAll(officialRewardRoster);
        audience.addAll(combatHelpers);
        return Bukkit.getOnlinePlayers().stream()
                .map(player -> (Player) player)
                .filter(player -> audience.contains(player.getUniqueId()) || isArenaLocation(player.getLocation()))
                .toList();
    }

    /** Send combat particles only to players who can actually see the arena effect. */
    private void spawnEventParticle(Location point, Particle particle, int count,
                                    double offsetX, double offsetY, double offsetZ, double extra) {
        if (point == null || point.getWorld() == null || particle == null) {
            return;
        }
        for (Player viewer : eventAudience()) {
            if (isEventParticleViewer(viewer, point)) {
                viewer.spawnParticle(particle, point, count, offsetX, offsetY, offsetZ, extra);
            }
        }
    }

    private void spawnEventParticle(Location point, Particle particle, int count,
                                    double offsetX, double offsetY, double offsetZ, double extra,
                                    Particle.DustOptions data) {
        if (point == null || point.getWorld() == null || particle == null || data == null) {
            return;
        }
        for (Player viewer : eventAudience()) {
            if (isEventParticleViewer(viewer, point)) {
                viewer.spawnParticle(particle, point, count, offsetX, offsetY, offsetZ, extra, data);
            }
        }
    }

    private boolean isEventParticleViewer(Player viewer, Location point) {
        return viewer != null && viewer.isOnline() && viewer.getWorld().equals(point.getWorld())
                && viewer.getLocation().distanceSquared(point) <= 64.0D * 64.0D;
    }

    private void spawnWaveArrivalEffect(World world, Location core, int wave, boolean finalWave) {
        if (world == null || core == null) {
            return;
        }
        Particle particle = finalWave ? Particle.DRAGON_BREATH
                : switch (wave) {
                    case 1 -> Particle.END_ROD;
                    case 2 -> Particle.REVERSE_PORTAL;
                    case 3 -> Particle.FLAME;
                    case 4, 5 -> Particle.DRAGON_BREATH;
                    default -> Particle.PORTAL;
                };
        Location center = core.clone().add(0.0D, 0.5D, 0.0D);
        spawnEventParticle(center, particle, finalWave ? 64 : 36, 1.2D, 0.6D, 1.2D, 0.03D);
        world.playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, finalWave ? 1.0F : 0.65F,
                finalWave ? 0.45F : 0.8F);
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
        if (location == null) {
            getLogger().warning("Cannot find a safe floor spawn for End Rift wave mob.");
            return;
        }
        Enderman enderman = (Enderman) world.spawnEntity(location, EntityType.ENDERMAN);
        String kind = finalWave ? EVENT_KIND_FINAL_WAVE : elite ? EVENT_KIND_ELITE : EVENT_KIND_WAVE_MOB;
        tag(enderman, kind, wave, !test);
        setLootProfile(enderman, test ? "test"
                : elite ? "elite-enderman" : finalWave ? "final-wave" : "common-enderman");
        enderman.setPersistent(true);
        enderman.setRemoveWhenFarAway(false);
        enderman.setCanPickupItems(false);
        enderman.setAI(true);
        enderman.setAware(true);
        if (elite) {
            EndRiftAiPolicy.MiniBossSpell miniBossSpell = EndRiftAiPolicy.miniBossSpell(wave, abilityIndex);
            AttributeInstance max = enderman.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(40.0D);
                enderman.setHealth(40.0D);
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
            spawnEventParticle(enderman.getLocation().add(0.0D, 1.0D, 0.0D), Particle.REVERSE_PORTAL,
                    10, 0.35D, 0.55D, 0.35D, 0.02D);
        }
        applyWaveThreeModifiers(enderman, wave);
        ownedEntities.put(enderman.getUniqueId(), enderman);
        tagTowerRole(enderman, index);
        assignCombatTactic(enderman, index);
        bindEventEntityClientForOnlinePlayers(enderman);
        if (finalWave && !test) {
            finalWaveEntities.add(enderman.getUniqueId());
        }
    }

    private void spawnSkeleton(World world, Location core, int wave, boolean miniBoss,
                               boolean finalWave, boolean test, int index, int abilityIndex) {
        String kind = finalWave ? EVENT_KIND_FINAL_WAVE
                : miniBoss ? EVENT_KIND_ELITE : EVENT_KIND_WAVE_MOB;
        Entity entity = spawnOwnedMob(world, core, EntityType.SKELETON, wave, kind, test, index);
        if (!(entity instanceof Skeleton skeleton)) {
            return;
        }
        skeleton.setPersistent(true);
        skeleton.setRemoveWhenFarAway(false);
        skeleton.setCanPickupItems(false);
        skeleton.setAI(true);
        skeleton.setAware(true);
        skeleton.setTarget(null);
        if (skeleton.getEquipment() != null) {
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            skeleton.getEquipment().setItemInMainHandDropChance(0.0F);
        }
        if (miniBoss) {
            AttributeInstance max = skeleton.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(config.skeletonEliteHealth());
                skeleton.setHealth(config.skeletonEliteHealth());
            }
            AttributeInstance attack = skeleton.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attack != null) {
                attack.setBaseValue(attack.getBaseValue() + config.skeletonEliteAttackDamageBonus());
            }
            EndRiftAiPolicy.MiniBossSpell spell = EndRiftAiPolicy.MiniBossSpell.ARROW_SALVO;
            tagMiniBossSpell(skeleton, spell);
            miniBossSpells.put(skeleton.getUniqueId(), spell);
            nextMiniBossSpellMillis.put(skeleton.getUniqueId(), 0L);
            skeleton.setCustomName(ChatColor.GOLD + (finalWave
                    ? "Элитный костяной стрелок" : "Костяной стрелок Разлома")
                    + ChatColor.DARK_PURPLE + " · " + spell.displayName());
            skeleton.setCustomNameVisible(true);
            spawnEventParticle(skeleton.getLocation().add(0.0D, 1.0D, 0.0D), Particle.END_ROD,
                    12, 0.35D, 0.65D, 0.35D, 0.02D);
        } else {
            skeleton.setCustomName(ChatColor.AQUA + "Стрелок Разлома");
            skeleton.setCustomNameVisible(true);
        }
        applyWaveThreeModifiers(skeleton, wave);
        bindEventEntityClientForOnlinePlayers(skeleton);
        SkeletonCombatPolicy.WaveBehavior behavior = SkeletonCombatPolicy.behaviorForWave(wave, miniBoss);
        getLogger().info("WAVE_SKELETON_BEHAVIOR entity=" + skeleton.getUniqueId()
                + " wave=" + wave + " variant=" + (miniBoss ? "MINIBOSS" : "COMMON")
                + " behavior=" + behavior.id() + " target=PLAYER_ONLY"
                + " spell=" + (miniBoss ? "ARROW_SALVO" : "VANILLA_BOW")
                + " arrow_profile=" + SkeletonCombatPolicy.arrowProfile(miniBoss).particlePattern()
                + " focus_marked=" + behavior.focusMarkedPlayer()
                + " guards_objective=" + behavior.guardsObjective()
                + " hazard_aware=" + behavior.hazardAware()
                + " tactic=" + behavior.tactic()
                + " combat_role=" + combatTactic(skeleton, abilityIndex));
    }

    private Entity spawnOwnedMob(World world, Location core, EntityType type, int wave,
                                 String kind, boolean test, int index) {
        Location location = safeSpawnLocation(core, index, 3.0D);
        if (location == null) {
            getLogger().warning("Cannot find a safe floor spawn for End Rift wave mob type=" + type);
            return null;
        }
        Entity entity = world.spawnEntity(location, type);
        tag(entity, kind, wave, !test);
        setLootProfile(entity, test ? "test"
                : type == EntityType.SPIDER ? "spider"
                : type == EntityType.SKELETON
                ? EVENT_KIND_ELITE.equals(kind) ? "elite-skeleton"
                : EVENT_KIND_FINAL_WAVE.equals(kind) ? "final-wave" : "skeleton"
                : "final-wave");
        if (entity instanceof LivingEntity living) {
            living.setPersistent(true);
            configureEventMobStats(living, type);
            applyWaveThreeModifiers(living, wave);
            if (living instanceof Mob mob) {
                mob.setAI(true);
                mob.setAware(true);
            }
        }
        ownedEntities.put(entity.getUniqueId(), entity);
        tagTowerRole(entity, index);
        assignCombatTactic(entity, index);
        bindEventEntityClientForOnlinePlayers(entity);
        if (wave == FINAL_WAVE_NUMBER && !test) {
            finalWaveEntities.add(entity.getUniqueId());
        }
        return entity;
    }

    /** Wave III makes the portal defenders visibly faster and hit with bounded knockback. */
    private void applyWaveThreeModifiers(LivingEntity living, int wave) {
        if (living == null || wave != 3) {
            return;
        }
        living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                20 * 240, 0, false, true, true));
        getLogger().info("WAVE_PORTAL_MOB_MODIFIERS entity=" + living.getUniqueId()
                + " speed=I attack_knockback=II-equivalent");
    }

    private void configureEventMobStats(LivingEntity living, EntityType type) {
        if (living == null || (type != EntityType.SPIDER && type != EntityType.SKELETON)) {
            return;
        }
        AttributeInstance health = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        AttributeInstance attack = living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        boolean skeleton = type == EntityType.SKELETON;
        double healthBonus = skeleton ? config.skeletonHealthBonus() : config.spiderHealthBonus();
        double attackBonus = skeleton ? config.skeletonAttackDamageBonus() : config.spiderAttackDamageBonus();
        if (health != null) {
            double maxHealth = health.getBaseValue() + healthBonus;
            health.setBaseValue(maxHealth);
            living.setHealth(maxHealth);
        }
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() + attackBonus);
        }
        getLogger().info((skeleton ? "SKELETON_STATS" : "SPIDER_STATS") + " entity=" + living.getUniqueId()
                + " health=" + (health == null ? "unknown" : health.getBaseValue())
                + " attack=" + (attack == null ? "unknown" : attack.getBaseValue()));
    }

    private void tagTowerRole(Entity entity, int slot) {
        if (entity == null || readInt(entity, keyWave, 0) != 4 || keyTowerRole == null
                || !isWaveCombatKind(readString(entity, keyKind))) {
            return;
        }
        // The role is part of the mob's job, not a random slot.  This keeps
        // spiders rushing players, endermen breaking the Core and skeletons
        // covering the arena from a fixed artillery ring on every restart.
        WaveMechanicsPolicy.TowerRole role = switch (entity.getType()) {
            case SPIDER -> WaveMechanicsPolicy.TowerRole.RAIDER;
            case ENDERMAN -> WaveMechanicsPolicy.TowerRole.BREAKER;
            case SKELETON -> WaveMechanicsPolicy.TowerRole.ARTILLERY;
            default -> switch (Math.floorMod(slot, 3)) {
                case 0 -> WaveMechanicsPolicy.TowerRole.RAIDER;
                case 1 -> WaveMechanicsPolicy.TowerRole.BREAKER;
                default -> WaveMechanicsPolicy.TowerRole.ARTILLERY;
            };
        };
        entity.getPersistentDataContainer().set(keyTowerRole, PersistentDataType.STRING, role.name());
        long nextAttack = System.currentTimeMillis() + role.attackIntervalMillis();
        entity.getPersistentDataContainer().set(keyTowerAttackAt, PersistentDataType.LONG, nextAttack);
        entity.getPersistentDataContainer().set(keyTowerAttackSequence, PersistentDataType.INTEGER, 0);
        towerNextAttackAt.put(entity.getUniqueId(), nextAttack);
        towerAttackSequences.put(entity.getUniqueId(), 0);
        if (entity instanceof LivingEntity living) {
            String displayName = switch (role) {
                case RAIDER -> "Налётчик Разлома";
                case BREAKER -> "Разрушитель ядра";
                case ARTILLERY -> "Артиллерист Разлома";
            };
            living.setCustomName(ChatColor.DARK_RED + displayName);
            living.setCustomNameVisible(true);
        }
        getLogger().info("WAVE_TOWER_ROLE entity=" + entity.getUniqueId()
                + " role=" + role + " attack_interval_ms=" + role.attackIntervalMillis());
    }

    /** Give every event mob one deterministic job for the whole generation. */
    private CombatTacticsPolicy.MobTactic assignCombatTactic(Entity entity, int slot) {
        if (entity == null || keyCombatTactic == null) {
            return CombatTacticsPolicy.MobTactic.ASSAULT;
        }
        int wave = readInt(entity, keyWave, 0);
        WaveMechanicsPolicy.TowerRole tower = towerRole(entity);
        String role = tower == null
                ? (EVENT_KIND_ELITE.equals(readString(entity, keyKind))
                || EVENT_KIND_FINAL_WAVE.equals(readString(entity, keyKind)) ? "ELITE" : "MOB")
                : tower.name();
        CombatTacticsPolicy.MobTactic tactic = CombatTacticsPolicy.waveTactic(wave, role, slot);
        entity.getPersistentDataContainer().set(keyCombatTactic,
                PersistentDataType.STRING, tactic.name());
        waveMobTactics.put(entity.getUniqueId(), tactic);
        getLogger().info("WAVE_AI_TACTIC entity=" + entity.getUniqueId()
                + " wave=" + wave + " role=" + role + " tactic=" + tactic);
        return tactic;
    }

    private CombatTacticsPolicy.MobTactic combatTactic(Entity entity, int slot) {
        if (entity == null) {
            return CombatTacticsPolicy.MobTactic.ASSAULT;
        }
        CombatTacticsPolicy.MobTactic cached = waveMobTactics.get(entity.getUniqueId());
        if (cached != null) {
            return cached;
        }
        String stored = readString(entity, keyCombatTactic);
        if (!stored.isBlank()) {
            try {
                CombatTacticsPolicy.MobTactic tactic = CombatTacticsPolicy.MobTactic.valueOf(stored);
                waveMobTactics.put(entity.getUniqueId(), tactic);
                return tactic;
            } catch (IllegalArgumentException ignored) {
                // A stale/corrupt tag is repaired below from the stable role.
            }
        }
        return assignCombatTactic(entity, slot);
    }

    private WaveMechanicsPolicy.TowerRole towerRole(Entity entity) {
        if (entity == null || keyTowerRole == null) {
            return null;
        }
        String role = entity.getPersistentDataContainer()
                .getOrDefault(keyTowerRole, PersistentDataType.STRING, "");
        if (role.isBlank()) {
            return null;
        }
        try {
            return WaveMechanicsPolicy.TowerRole.valueOf(role);
        } catch (IllegalArgumentException invalid) {
            getLogger().warning("WAVE_TOWER_ROLE_INVALID entity=" + entity.getUniqueId()
                    + " value=" + role);
            return null;
        }
    }

    private boolean isTowerDefenseMob(Entity entity) {
        return entity != null
                && readInt(entity, keyWave, 0) == 4
                && isWaveCombatKind(readString(entity, keyKind))
                && towerRole(entity) != null;
    }

    private boolean hasTowerPlayerAggro(Entity entity, long now) {
        if (entity == null || !isTowerDefenseMob(entity)) {
            return false;
        }
        long expiresAt = towerAggroUntil.getOrDefault(entity.getUniqueId(), 0L);
        if (expiresAt > now) {
            return true;
        }
        towerAggroUntil.remove(entity.getUniqueId());
        return false;
    }

    private Player findNearestCombatPlayer(Entity entity, double radius) {
        if (entity == null || entity.getWorld() == null || !Double.isFinite(radius) || radius < 0.0D) {
            return null;
        }
        double radiusSquared = radius * radius;
        Player nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Player player : activeLivingPlayers()) {
            if (!isCombatTarget(player) || !entity.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = horizontalDistanceSquared(entity.getLocation(), player.getLocation());
            if (distance > radiusSquared) {
                continue;
            }
            if (nearest == null || distance < nearestDistance
                    || (Math.abs(distance - nearestDistance) < 0.0001D
                    && player.getUniqueId().toString().compareTo(nearest.getUniqueId().toString()) < 0)) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
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
        // coreCombatAnchorLocation() is the saved rune floor level.  Do not
        // derive it by subtracting one from coreLocation(): when the operator
        // turns an ordinary floor block into Core, the surrounding floor is
        // at coreY and entity feet must be at coreY + 1.
        Location floorAnchor = coreCombatAnchorLocation();
        if (floorAnchor == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            Location candidate = spawnLocation(floorAnchor, index + attempt, offset);
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(BlockFace.UP);
            Block floor = feet.getRelative(BlockFace.DOWN);
            if (feet.isPassable() && head.isPassable() && floor.getType().isSolid()
                    && core.distanceSquared(candidate) <= config.arenaRadius() * config.arenaRadius()) {
                return candidate;
            }
        }
        return null;
    }

    private Location safeBossSpawnLocation() {
        Location anchor = coreCombatAnchorLocation();
        if (anchor == null || config == null) {
            return null;
        }
        double radius = Math.min(6.0D, boundedCombatRadius(config.bossRadius()) - 1.0D);
        return findSafeCombatLocation(anchor, null, Math.max(1.0D, radius),
                MIN_BOSS_CORE_DISTANCE_BLOCKS);
    }

    /**
     * The only permitted teleport path for event combat entities.  Paper fires
     * EntityTeleportEvent synchronously, so the short-lived token is visible
     * to the listener only for this one call and is removed even on failure.
     */
    private boolean teleportCombatEntity(Entity entity, Location destination) {
        if (entity == null || destination == null || !entity.isValid()
                || !ownedEntities.containsKey(entity.getUniqueId())) {
            return false;
        }
        long issuedAt = System.currentTimeMillis();
        UUID entityId = entity.getUniqueId();
        combatTeleportPermits.put(entityId, BossTeleportPermitPolicy.issue(
                entityId.toString(), issuedAt, issuedAt + COMBAT_TELEPORT_PERMIT_MILLIS));
        try {
            return entity.teleport(destination);
        } finally {
            combatTeleportPermits.remove(entityId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOwnedEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !ownedEntities.containsKey(entity.getUniqueId())) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        long now = System.currentTimeMillis();
        boolean internalTeleport = BossTeleportPermitPolicy.accept(
                combatTeleportPermits.get(entityId), entityId.toString(), now);
        if (!internalTeleport) {
            long previousLog = blockedTeleportLogAt.getOrDefault(entityId, 0L);
            if (now - previousLog >= 5_000L) {
                blockedTeleportLogAt.put(entityId, now);
                String kind = readString(entity, keyKind);
                getLogger().warning((EVENT_KIND_BOSS.equals(kind)
                        ? "BOSS_TELEPORT_BLOCKED" : "WAVE_TELEPORT_BLOCKED")
                        + " entity=" + entityId + " kind=" + kind
                        + " reason=no-internal-permit");
            }
            event.setCancelled(true);
            return;
        }
        String kind = readString(entity, keyKind);
        Location anchor = (isWaveCombatKind(kind) || EVENT_KIND_BOSS.equals(kind))
                ? coreCombatAnchorLocation() : coreLocation();
        Location target = event.getTo();
        if (anchor == null || target == null || !anchor.getWorld().equals(target.getWorld())) {
            event.setCancelled(true);
            return;
        }
        double radius = EVENT_KIND_BOSS.equals(kind)
                ? boundedCombatRadius(config.bossRadius())
                : boundedCombatRadius(config.containmentRadius());
        if (isCoreBlockPosition(target)) {
            // A teleport request may name the solid Core block itself.  Never
            // put a combat entity inside/on top of the Core: resolve it to a
            // nearby floor position on the Core's combat level instead.
            double minimum = EVENT_KIND_BOSS.equals(kind)
                    ? MIN_BOSS_CORE_DISTANCE_BLOCKS : MIN_WAVE_CORE_DISTANCE_BLOCKS;
            Location safe = findSafeCombatLocation(anchor, null, radius - 0.75D, minimum);
            if (safe == null) {
                event.setCancelled(true);
            } else {
                event.setTo(safe);
            }
            return;
        }
        boolean outsideHorizontalRadius = horizontalDistanceSquared(target, anchor) > radius * radius;
        boolean outsideVerticalRadius = outsideCombatVertical(target, anchor);
        if (outsideHorizontalRadius || outsideVerticalRadius) {
            double minimum = EVENT_KIND_BOSS.equals(kind)
                    ? MIN_BOSS_CORE_DISTANCE_BLOCKS : MIN_WAVE_CORE_DISTANCE_BLOCKS;
            Location safe = findSafeCombatLocation(anchor, target, radius - 0.75D, minimum);
            if (safe == null) {
                event.setCancelled(true);
            } else {
                event.setTo(safe);
            }
        }
    }

    private void clearWaveEntities() {
        clearWaveObjectiveState();
        clearActiveEventArrows();
        for (Entity entity : new ArrayList<>(ownedEntities.values())) {
            String kind = readString(entity, keyKind);
            if (EVENT_KIND_WAVE_MOB.equals(kind) || EVENT_KIND_ELITE.equals(kind)
                    || EVENT_KIND_FINAL_WAVE.equals(kind) || EVENT_KIND_WAVE_REWARD.equals(kind)) {
                entity.remove();
                ownedEntities.remove(entity.getUniqueId());
                finalWaveEntities.remove(entity.getUniqueId());
                nextWavePathRequestMillis.remove(entity.getUniqueId());
                lastWavePathLogMillis.remove(entity.getUniqueId());
                waveMobTactics.remove(entity.getUniqueId());
                combatTeleportPermits.remove(entity.getUniqueId());
                blockedTeleportLogAt.remove(entity.getUniqueId());
                towerAggroUntil.remove(entity.getUniqueId());
                nextSkeletonArrowMillis.remove(entity.getUniqueId());
            }
        }
        spellServants.clear();
        miniBossSpells.clear();
        nextMiniBossSpellMillis.clear();
        waveMobTactics.clear();
        combatTeleportPermits.clear();
        blockedTeleportLogAt.clear();
        nextSkeletonArrowMillis.clear();
        towerNextAttackAt.clear();
        towerAttackSequences.clear();
        towerAggroUntil.clear();
        towerAttackSequence = 0;
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
        Location spawn = safeBossSpawnLocation();
        if (spawn == null) {
            message(sender, "&cНе найдена свободная площадка для тестового босса рядом с ядром.");
            return;
        }
        Enderman boss = (Enderman) world.spawnEntity(spawn, EntityType.ENDERMAN);
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
            Location spawn = safeBossSpawnLocation();
            if (spawn == null) {
                return;
            }
            Enderman boss = (Enderman) core.getWorld().spawnEntity(spawn, EntityType.ENDERMAN);
            configureBoss(boss, true);
        }
        List<Player> eligiblePlayers = activeLivingPlayers();
        getLogger().info("TEST_AI_STARTED event=" + eventId
                + " phase=" + phase + " official_phase_unchanged=true"
                + " eligible_players=" + eligiblePlayers.size());
        message(sender, "&aTest AI запущен: реальные wave/boss controllers, official phase/roster/victory не изменены.");
    }

    private void spawnOfficialBoss(CommandSender sender) {
        if (!isConfigured() || (!endUnlocked && phase != EventPhase.BOSS_ACTIVE
                && phase != EventPhase.BOSS_CINEMATIC && phase != EventPhase.WAVE_5)) {
            if (sender != null) {
                message(sender, "&cОфициальный boss доступен только после Wave 5 или в BOSS_ACTIVE.");
            }
            return;
        }
        if (liveBoss() != null) {
            if (sender != null) {
                message(sender, "&eОфициальный boss уже активен.");
            }
            return;
        }
        if (phase == EventPhase.WAVE_5) {
            if (transition(EventPhase.BOSS_CINEMATIC, "admin confirmed boss cinematic", eventId + ":boss-cinematic-admin")) {
                phaseDeadlineMillis = System.currentTimeMillis() + BOSS_CINEMATIC_DURATION_TICKS * 50L;
                scheduleOfficialBossSpawn();
            }
            if (sender != null) {
                message(sender, "&eЗапущена кинематографическая фаза босса; появление через 10 секунд.");
            }
            return;
        }
        if (phase == EventPhase.BOSS_CINEMATIC) {
            if (bossSpawnTask == null) {
                scheduleOfficialBossSpawn();
            }
            if (sender != null) {
                message(sender, "&eКинематографическая фаза уже идёт.");
            }
            return;
        }
        // A recovered local world may retain endUnlocked=true while its
        // combat phase is COLLECTING.  The explicit official spawn command is
        // also the local survival harness; make that harness enter the real
        // BOSS_ACTIVE controller before creating the authoritative entity.
        // This prevents a boss that appears successfully but never acquires a
        // target, moves, casts, or accepts player damage because tickBoss and
        // onBossDamage are phase-gated.
        if (endUnlocked && phase != EventPhase.BOSS_ACTIVE) {
            forcePhase(EventPhase.BOSS_ACTIVE, "official boss local harness");
        }
        Location core = safeBossSpawnLocation();
        if (core == null) {
            return;
        }
        Enderman boss = (Enderman) core.getWorld().spawnEntity(core, EntityType.ENDERMAN);
        configureBoss(boss, false);
        getLogger().info("BOSS_SPAWNED event=" + eventId + " boss=" + boss.getUniqueId());
        if (sender != null) {
            message(sender, "&aОфициальный Rift Guardian создан.");
        }
    }

    private void configureBoss(Enderman boss, boolean test) {
        bossUuid = boss.getUniqueId();
        bossKillerUuid = null;
        bossStage = BossStage.AWAKENING;
        bossCastState = BossCastState.NONE;
        bossCastDeadlineMillis = 0L;
        absorptionTriggered = false;
        absorptionCompleted = false;
        absorptionAttackEmpowered = false;
        judgmentTriggered = false;
        judgmentCompleted = false;
        tag(boss, EVENT_KIND_BOSS, 0, !test);
        if (keyCombatTactic != null) {
            boss.getPersistentDataContainer().set(keyCombatTactic,
                    PersistentDataType.STRING, CombatTacticsPolicy.BossTactic.RING_ORBIT.name());
        }
        setLootProfile(boss, test ? "test" : "boss");
        if (test) {
            tagTestBoss(boss);
        } else {
            testCombatAiMode = false;
        }
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        boss.setCanPickupItems(false);
        boss.setAI(true);
        boss.setAware(true);
        boss.setCustomName("§5Хранитель Разлома");
        boss.setCustomNameVisible(true);
        AttributeInstance maxHealth = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.min(config.bossHealth(), BOSS_PHYSICAL_HEALTH_LIMIT));
        }
        setBossVirtualHealth(boss, config.bossHealth());
        AttributeInstance attack = boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            double totalAttack = BossStatsPolicy.attackDamage(attack.getBaseValue(),
                    config.bossAttackDamageBonus());
            attack.setBaseValue(totalAttack);
            getLogger().info("BOSS_STATS boss=" + boss.getUniqueId()
                    + " vanilla_base=" + (totalAttack - config.bossAttackDamageBonus())
                    + " configured_bonus=" + config.bossAttackDamageBonus()
                    + " total_attack=" + totalAttack);
        }
        boss.setInvulnerable(false);
        ownedEntities.put(boss.getUniqueId(), boss);
        if (!test) {
            ensureBossBar();
            bindBossClientForOnlinePlayers();
            playEventMusic(musicForPhase());
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

    /**
     * Return the boss health that gameplay, stages, rewards and the BossBar
     * use. The value is persisted on the entity so a plugin reload cannot
     * silently reset a 2500 HP boss to Paper's physical cap.
     */
    private double bossVirtualHealth(LivingEntity boss) {
        if (boss == null) {
            return 0.0D;
        }
        double configuredMax = Math.max(1.0D, config.bossHealth());
        double fallback = bossVirtualHealthValue > 0.0D && bossUuid != null
                && bossUuid.equals(boss.getUniqueId())
                ? bossVirtualHealthValue
                : Math.max(0.0D, Math.min(configuredMax, boss.getHealth()));
        Double persisted = keyBossVirtualHealth == null ? null
                : boss.getPersistentDataContainer().get(keyBossVirtualHealth, PersistentDataType.DOUBLE);
        double value = persisted != null && Double.isFinite(persisted) ? persisted : fallback;
        value = Math.max(0.0D, Math.min(configuredMax, Double.isFinite(value) ? value : fallback));
        bossVirtualHealthValue = value;
        if (persisted == null && keyBossVirtualHealth != null) {
            boss.getPersistentDataContainer().set(keyBossVirtualHealth, PersistentDataType.DOUBLE, value);
        }
        synchronizeBossPhysicalHealth(boss, value);
        return value;
    }

    /**
     * Persist virtual HP and project it onto the legal physical health range.
     * A value above 2048 remains visible in the event UI and damage model;
     * Paper only sees the capped projection.
     */
    private void setBossVirtualHealth(LivingEntity boss, double health) {
        if (boss == null) {
            return;
        }
        double configuredMax = Math.max(1.0D, config.bossHealth());
        double value = Math.max(0.0D, Math.min(configuredMax, Double.isFinite(health) ? health : 0.0D));
        bossVirtualHealthValue = value;
        if (keyBossVirtualHealth != null) {
            boss.getPersistentDataContainer().set(keyBossVirtualHealth, PersistentDataType.DOUBLE, value);
        }
        synchronizeBossPhysicalHealth(boss, value);
    }

    private void synchronizeBossPhysicalHealth(LivingEntity boss, double virtualHealth) {
        if (boss == null || boss.isDead()) {
            return;
        }
        double configuredPhysicalMax = Math.min(Math.max(1.0D, config.bossHealth()), BOSS_PHYSICAL_HEALTH_LIMIT);
        AttributeInstance maxHealth = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - configuredPhysicalMax) > 0.001D) {
            maxHealth.setBaseValue(configuredPhysicalMax);
        }
        double physicalMax = maxHealth == null
                ? configuredPhysicalMax
                : Math.min(BOSS_PHYSICAL_HEALTH_LIMIT, Math.max(1.0D, boss.getMaxHealth()));
        double physicalHealth = Math.max(0.0D, Math.min(physicalMax, virtualHealth));
        if (physicalHealth <= 0.0D) {
            // Keep a one-heart physical projection until the authoritative
            // damage path explicitly commits EntityDeathEvent.  Calling
            // setBossVirtualHealth(0) recursively here used to leave the
            // entity alive/immune (and could recurse forever).
            if (boss.getHealth() > 1.0D) {
                boss.setHealth(1.0D);
            }
            return;
        }
        double target = Math.max(1.0D, physicalHealth);
        if (Math.abs(boss.getHealth() - target) > 0.001D) {
            boss.setHealth(target);
        }
    }

    private void clearBossServants() {
        for (UUID servantId : new HashSet<>(spellServants)) {
            Entity servant = ownedEntities.remove(servantId);
            finalWaveEntities.remove(servantId);
            miniBossSpells.remove(servantId);
            nextMiniBossSpellMillis.remove(servantId);
            nextWavePathRequestMillis.remove(servantId);
            lastWavePathLogMillis.remove(servantId);
            waveMobTactics.remove(servantId);
            combatTeleportPermits.remove(servantId);
            blockedTeleportLogAt.remove(servantId);
            unbindEventEntityClient(servantId);
            if (servant != null && servant.isValid() && !servant.isDead()) {
                servant.remove();
            }
        }
        spellServants.clear();
    }

    private void clearBossOnly() {
        clearBossServants();
        LivingEntity boss = liveBoss();
        boolean disposableTest = testCombatAiMode || (boss != null && isTestBoss(boss));
        if (boss != null) {
            boss.remove();
        }
        if (bossUuid != null) {
            ownedEntities.remove(bossUuid);
        }
        bossUuid = null;
        bossKillerUuid = null;
        bossVirtualHealthValue = 0.0D;
        testCombatAiMode = false;
        recentBossTargets.clear();
        previousBossSpell = null;
        bossTargetCursor = 0;
        bossSpellCursor = 0;
        bossTacticCycle = 0;
        nextBossTacticMillis = 0L;
        nextTargetMillis = 0L;
        nextSpellMillis = 0L;
        bossSpellPauseUntilMillis = 0L;
        lastBossTeleportMillis = 0L;
        lastBossFeintMillis = 0L;
        cancelBossCastTask();
        bossStage = BossStage.AWAKENING;
        bossCastState = BossCastState.NONE;
        bossCastDeadlineMillis = 0L;
        absorptionTriggered = false;
        absorptionCompleted = false;
        absorptionAttackEmpowered = false;
        judgmentTriggered = false;
        judgmentCompleted = false;
        servantsSummonedAt70 = false;
        servantsSummonedAt35 = false;
        if (disposableTest) {
            halfHealthTriggered = false;
            controlSpellUnlocked = false;
            finalDrainTriggered = false;
            finalDrainApplied = false;
            finalDrainTargets.clear();
            finalDrainAppliedPlayers.clear();
            if (disposableTest && !saveStateSync()) {
                getLogger().warning("Disposable test cleanup could not persist cleared phase markers event=" + eventId);
            }
        }
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        clearActiveEventArrows();
        clearJudgmentVisuals();
        combatTeleportPermits.clear();
        blockedTeleportLogAt.clear();
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
        ensureEventCombatAi(boss);
        long now = System.currentTimeMillis();
        synchronizeBossStage(boss);
        reconcileBossCastProjection(boss, now);
        BossStagePolicy.CombatProfile profile = BossStagePolicy.combatProfile(
                bossStage, absorptionCompleted);
        Location core = coreCombatAnchorLocation();
        // Containment is a safety invariant, not part of the spell state.  Run
        // it before a bounded cast can return early, otherwise a boss that was
        // pushed onto the Core during Absorption/Judgment could remain there
        // until the cast ended.
        enforceCombatLeash(boss, core, config.bossRadius(), "BOSS_AI_LEASH");
        if (bossCastState == BossCastState.ABSORPTION_CHANNEL
                || bossCastState == BossCastState.JUDGMENT_CAST) {
            if (bossCastDeadlineMillis > 0L && now >= bossCastDeadlineMillis) {
                finishBossCast(boss);
            } else {
                renderBossCastState(boss);
                return;
            }
        }
        if (bossCastState == BossCastState.EXHAUSTED) {
            if (bossCastDeadlineMillis > 0L && now >= bossCastDeadlineMillis) {
                bossCastState = BossCastState.NONE;
                bossCastDeadlineMillis = 0L;
                getLogger().info("BOSS_CAST_STATE boss=" + boss.getUniqueId()
                        + " state=NONE reason=exhausted-window-complete");
            } else {
                updateBossBar(boss);
                return;
            }
        }
        updateBossBar(boss);
        boolean testBossAi = testCombatAiMode && isTestBoss(boss);
        if (phase != EventPhase.BOSS_ACTIVE && !testBossAi) {
            return;
        }
        if (nextTargetMillis <= now) {
            if (boss instanceof Mob mob) {
                rotateBossTarget(mob);
            }
            nextTargetMillis = now + scaledBossDelayMillis(
                    randomSeconds(config.bossTargetMinSeconds(), config.bossTargetMaxSeconds()),
                    profile.targetRotationMultiplier(), 1_000L);
        }
        maintainBossPath(boss, now);
        maintainBossTeleport(boss, now);
        detectBossStuck(boss, now);
        if (now >= bossSpellPauseUntilMillis && nextSpellMillis <= now) {
            if (!activeLivingPlayers().isEmpty()) {
                castBossSpell(boss, false);
                nextSpellMillis = now + scaledBossDelayMillis(
                        randomSeconds(config.bossSpellMinSeconds(), config.bossSpellMaxSeconds()),
                        profile.spellCooldownMultiplier(), 3_000L);
            } else {
                // No eligible target is not a real cast; retry soon instead of
                // consuming the whole spell cooldown while the arena is empty.
                nextSpellMillis = now + 1000L;
            }
        }
    }

    private BossStagePolicy.CombatProfile currentBossCombatProfile() {
        return BossStagePolicy.combatProfile(bossStage, absorptionCompleted);
    }

    private long scaledBossDelayMillis(int seconds, double multiplier, long minimumMillis) {
        long baseMillis = Math.max(1L, seconds) * 1_000L;
        long scaled = Math.round(baseMillis * multiplier);
        return Math.max(minimumMillis, scaled);
    }

    /** Keep the entity flag derived from one bounded cast deadline. */
    private void reconcileBossCastProjection(LivingEntity boss, long now) {
        if (boss == null) {
            return;
        }
        BossCastState before = bossCastState;
        BossCastPolicy.Reconciled projection = BossCastPolicy.reconcile(
                before, now, bossCastDeadlineMillis);
        if ((before == BossCastState.ABSORPTION_CHANNEL || before == BossCastState.JUDGMENT_CAST)
                && projection.state() == BossCastState.NONE) {
            // Preserve the one-shot Judgment transition while still repairing
            // an expired deadline.  An expired, validly persisted Absorption
            // channel completes its post-channel buff during recovery; a
            // missing deadline fails closed without granting immunity.
            if (before == BossCastState.JUDGMENT_CAST
                    || before == BossCastState.ABSORPTION_CHANNEL && bossCastDeadlineMillis > 0L) {
                finishBossCast(boss);
            } else {
                bossCastState = BossCastState.NONE;
                bossCastDeadlineMillis = 0L;
                boss.setInvulnerable(false);
                getLogger().warning("BOSS_CAST_RECONCILED event=" + eventId
                        + " boss=" + boss.getUniqueId() + " from=" + before
                        + " to=NONE reason=expired-or-missing-deadline damageable=true");
            }
            return;
        }
        bossCastState = projection.state();
        bossCastDeadlineMillis = projection.deadlineMillis();
        boss.setInvulnerable(projection.invulnerable());
    }

    private void synchronizeBossStage(LivingEntity boss) {
        if (boss == null) {
            return;
        }
        double virtualHealth = bossVirtualHealth(boss);
        BossStage requestedStage = BossStagePolicy.stageFor(virtualHealth, judgmentTriggered);
        if (requestedStage.ordinal() < bossStage.ordinal()) {
            getLogger().warning("BOSS_STAGE_REGRESSION_BLOCKED event=" + eventId
                    + " boss=" + boss.getUniqueId() + " from=" + bossStage
                    + " requested=" + requestedStage + " health=" + virtualHealth);
        }
        BossStagePolicy.StageTransition transition = BossStagePolicy.transition(
                bossStage, virtualHealth, judgmentTriggered);
        if (transition.current() != bossStage) {
            BossStage previous = bossStage;
            bossStage = transition.current();
            getLogger().info("BOSS_STAGE_TRANSITION event=" + eventId
                    + " boss=" + boss.getUniqueId() + " from=" + previous
                    + " to=" + bossStage + " crossed=" + transition.entered()
                    + " health=" + virtualHealth);
            announceEventTitle(bossStage.bossBarTitle().toUpperCase(Locale.ROOT),
                    "§f" + Math.round(virtualHealth) + " / " + Math.round(config.bossHealth()) + " HP", true);
            sendBossPhaseVisualUpdate(boss, bossStage);
        }
        boolean bossControllerActive = phase == EventPhase.BOSS_ACTIVE
                || testCombatAiMode && isTestBoss(boss);
        boolean crossedAbsorption = bossStage == BossStage.ABSORPTION
                || transition.entered().contains(BossStage.ABSORPTION);
        if (crossedAbsorption && !absorptionTriggered && bossControllerActive) {
            startAbsorptionChannel(boss);
        }
        if (transition.triggerJudgment() && !judgmentTriggered && bossControllerActive) {
            startJudgment(boss);
        }
    }

    private void updateBossBar(LivingEntity boss) {
        if (bossBar == null || boss == null) {
            return;
        }
        double max = Math.max(1.0D, config.bossHealth());
        double virtualHealth = bossVirtualHealth(boss);
        double progress = Math.max(0.0D, Math.min(1.0D, virtualHealth / max));
        String title = switch (bossCastState) {
            case JUDGMENT_CAST -> "Страж Разлома — СУД РАЗЛОМА";
            case EXHAUSTED -> "Страж Разлома — Истощён";
            default -> bossStage.bossBarTitle();
        };
        String renderedTitle = title + " — " + Math.round(virtualHealth) + "/" + Math.round(max) + " HP";
        BarColor color = switch (bossCastState) {
            case JUDGMENT_CAST -> BarColor.RED;
            case EXHAUSTED -> BarColor.WHITE;
            default -> switch (bossStage) {
                case AWAKENING -> BarColor.PURPLE;
                case HUNTER -> BarColor.BLUE;
                case DISTORTION -> BarColor.PINK;
                case ABSORPTION -> BarColor.YELLOW;
                case CATASTROPHE -> BarColor.RED;
            };
        };
        Set<UUID> desiredAudience = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isCombatTarget(player)) {
                desiredAudience.add(player.getUniqueId());
            }
        }
        Set<UUID> currentAudience = bossBar.getPlayers().stream()
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        boolean audienceChanged = !currentAudience.equals(desiredAudience);
        long now = System.currentTimeMillis();
        // The controller ticks every five ticks, but BossBar packets are
        // capped at 5 Hz and skipped when the visible health/title/color did
        // not change.  Audience membership is still reconciled at the same
        // bounded cadence.
        if (!audienceChanged && now - bossBarLastUpdateMillis < 200L
                && Math.abs(progress - bossBarLastProgress) < 0.0001D
                && renderedTitle.equals(bossBarLastTitle) && color == bossBarLastColor) {
            return;
        }
        bossBar.setProgress(progress);
        bossBar.setTitle(renderedTitle);
        bossBar.setColor(color);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (desiredAudience.contains(player.getUniqueId()) && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            } else if (!desiredAudience.contains(player.getUniqueId()) && bossBar.getPlayers().contains(player)) {
                bossBar.removePlayer(player);
            }
        }
        bossBarLastUpdateMillis = now;
        bossBarLastProgress = progress;
        bossBarLastTitle = renderedTitle;
        bossBarLastColor = color;
    }

    private void startAbsorptionChannel(LivingEntity boss) {
        if (boss == null || absorptionTriggered || bossCastState != BossCastState.NONE) {
            return;
        }
        absorptionTriggered = true;
        absorptionCompleted = false;
        absorptionAttackEmpowered = false;
        bossCastState = BossCastState.ABSORPTION_CHANNEL;
        bossCastDeadlineMillis = System.currentTimeMillis() + ABSORPTION_CHANNEL_TICKS * 50L;
        boss.setInvulnerable(true);
        bossSpellPauseUntilMillis = bossCastDeadlineMillis;
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        if (!isTestBoss(boss) && !saveStateSync()) {
            // The deadline and cast state must cross the durable boundary
            // before the channel can become an authoritative immunity window.
            // Failing closed here also guarantees that a storage outage never
            // strands the live entity as an unkillable boss.
            boss.setInvulnerable(false);
            bossCastState = BossCastState.NONE;
            bossCastDeadlineMillis = 0L;
            bossSpellPauseUntilMillis = 0L;
            forcePhase(EventPhase.RECOVERY_REQUIRED,
                    "absorption channel could not be persisted");
            return;
        }
        announceEventTitle("§eПОГЛОЩЕНИЕ", "§fСтраж впитывает энергию ядра", true);
        getLogger().info("BOSS_CAST_STATE event=" + eventId + " boss=" + boss.getUniqueId()
                + " state=ABSORPTION_CHANNEL deadline=" + bossCastDeadlineMillis);
        cancelBossCastTask();
        long callbackGeneration = generation;
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || !boss.isValid() || boss.isDead() || !boss.getUniqueId().equals(bossUuid)
                    || bossCastState != BossCastState.ABSORPTION_CHANNEL) {
                if (bossCastState == BossCastState.ABSORPTION_CHANNEL) {
                    boss.setInvulnerable(false);
                    bossCastState = BossCastState.NONE;
                    bossCastDeadlineMillis = 0L;
                }
                holder[0].cancel();
                if (bossCastTask == holder[0]) {
                    bossCastTask = null;
                }
                return;
            }
            renderBossCastState(boss);
            if (System.currentTimeMillis() >= bossCastDeadlineMillis) {
                finishBossCast(boss);
                holder[0].cancel();
                if (bossCastTask == holder[0]) {
                    bossCastTask = null;
                }
            }
        }, 0L, 5L);
        bossCastTask = holder[0];
        if (taskRegistry != null) {
            taskRegistry.register(holder[0]);
        }
    }

    private void startJudgment(LivingEntity boss) {
        if (boss == null || judgmentTriggered || bossCastState != BossCastState.NONE) {
            return;
        }
        judgmentTriggered = true;
        bossCastState = BossCastState.JUDGMENT_CAST;
        bossCastDeadlineMillis = System.currentTimeMillis() + 15_000L;
        setBossVirtualHealth(boss, BossStagePolicy.judgmentThreshold());
        boss.setInvulnerable(true);
        bossSpellPauseUntilMillis = bossCastDeadlineMillis;
        clearVoidMarkZones();
        clearActiveRiftProjectiles();
        clearJudgmentVisuals();
        announceEventTitle("§4СУД РАЗЛОМА", "§fНайдите безопасную область", true);
        getLogger().info("BOSS_CAST_STATE event=" + eventId + " boss=" + boss.getUniqueId()
                + " state=JUDGMENT_CAST threshold=" + BossStagePolicy.judgmentThreshold());
        cancelBossCastTask();
        long callbackGeneration = generation;
        final int[] pulse = {0};
        final long[] nextPulse = {System.currentTimeMillis() + 1_000L};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (taskRegistry == null || !taskRegistry.owns(callbackGeneration)
                    || !boss.isValid() || boss.isDead() || !boss.getUniqueId().equals(bossUuid)
                    || bossCastState != BossCastState.JUDGMENT_CAST) {
                boss.setInvulnerable(false);
                bossCastState = BossCastState.NONE;
                bossCastDeadlineMillis = 0L;
                clearJudgmentVisuals();
                holder[0].cancel();
                if (bossCastTask == holder[0]) {
                    bossCastTask = null;
                }
                return;
            }
            renderBossCastState(boss);
            long now = System.currentTimeMillis();
            if (pulse[0] < 3 && now >= nextPulse[0]) {
                applyJudgmentPulse(boss, pulse[0]++);
                nextPulse[0] = now + switch (pulse[0]) {
                    case 1 -> 4_000L;
                    case 2 -> 3_500L;
                    default -> 3_000L;
                };
            }
            if (pulse[0] >= 3 && now >= nextPulse[0]) {
                finishBossCast(boss);
                holder[0].cancel();
                if (bossCastTask == holder[0]) {
                    bossCastTask = null;
                }
            }
        }, 0L, 5L);
        bossCastTask = holder[0];
        if (taskRegistry != null) {
            taskRegistry.register(holder[0]);
        }
    }

    /**
     * Render safe areas as transient display geometry.  No arena block is
     * replaced: the floor remains player-owned terrain while the client sees
     * a full-size luminous plate, a label and a particle pillar for each safe
     * zone.  The tracked UUID set makes every pulse/restart cleanup exact.
     */
    private void spawnJudgmentSafeZoneVisual(Location zone, double radius, int pulse) {
        if (zone == null || zone.getWorld() == null) {
            return;
        }
        World world = zone.getWorld();
        Location plateLocation = zone.clone().add(-radius, 0.02D, -radius);
        BlockDisplay plate = world.spawn(plateLocation, BlockDisplay.class);
        plate.setBlock((pulse % 2 == 0 ? Material.LIME_STAINED_GLASS : Material.SEA_LANTERN)
                .createBlockData());
        plate.setBrightness(new Display.Brightness(15, 15));
        plate.setBillboard(Display.Billboard.FIXED);
        plate.setViewRange(64.0F);
        plate.setDisplayWidth((float) Math.max(1.0D, radius * 2.0D));
        plate.setDisplayHeight(0.12F);
        plate.setPersistent(false);
        plate.setInvulnerable(true);
        plate.setShadowRadius(0.0F);
        plate.setTransformation(new Transformation(
                new Vector3f(), new AxisAngle4f(),
                new Vector3f((float) Math.max(1.0D, radius * 2.0D),
                        JUDGMENT_SAFE_ZONE_BLOCK_HEIGHT * 0.04F,
                        (float) Math.max(1.0D, radius * 2.0D)),
                new AxisAngle4f()));
        tag(plate, EVENT_KIND_DISPLAY, 0, false);
        judgmentVisuals.add(plate.getUniqueId());

        TextDisplay label = world.spawn(zone.clone().add(0.0D, 2.0D, 0.0D), TextDisplay.class);
        label.setText("§aБЕЗОПАСНАЯ ЗОНА");
        label.setBillboard(TextDisplay.Billboard.CENTER);
        label.setBrightness(new Display.Brightness(15, 15));
        label.setViewRange(64.0F);
        label.setLineWidth(180);
        label.setPersistent(false);
        label.setInvulnerable(true);
        label.setShadowed(true);
        tag(label, EVENT_KIND_DISPLAY, 0, false);
        judgmentVisuals.add(label.getUniqueId());

        for (int index = 0; index < 32; index++) {
            double angle = Math.PI * 2.0D * index / 32.0D;
            Location edge = zone.clone().add(Math.cos(angle) * radius, 0.18D,
                    Math.sin(angle) * radius);
            spawnEventParticle(edge, Particle.END_ROD, 1,
                    0.02D, 0.03D, 0.02D, 0.0D);
            if (index % 4 == 0) {
                spawnEventParticle(edge, Particle.REVERSE_PORTAL, 2,
                        0.02D, 0.10D, 0.02D, 0.01D);
            }
        }
        for (int level = 0; level <= JUDGMENT_SAFE_ZONE_BLOCK_HEIGHT; level++) {
            spawnEventParticle(zone.clone().add(0.0D, level * 0.65D, 0.0D), Particle.END_ROD,
                    8, radius * 0.35D, 0.08D, radius * 0.35D, 0.01D);
        }
        world.playSound(zone, Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.2F + pulse * 0.08F);
        getLogger().info("BOSS_JUDGMENT_SAFE_ZONE event=" + eventId
                + " boss=" + (bossUuid == null ? "none" : bossUuid)
                + " pulse=" + (pulse + 1) + " center=" + locationText(zone)
                + " radius=" + radius + " display=true");
    }

    private void clearJudgmentVisuals() {
        for (UUID visualId : new HashSet<>(judgmentVisuals)) {
            Entity visual = ownedEntities.remove(visualId);
            if (visual == null) {
                visual = Bukkit.getEntity(visualId);
            }
            if (visual != null && visual.isValid()) {
                visual.remove();
            }
        }
        judgmentVisuals.clear();
    }

    private void applyJudgmentPulse(LivingEntity boss, int pulse) {
        Location core = coreCombatAnchorLocation();
        if (core == null || boss == null) {
            return;
        }
        clearJudgmentVisuals();
        double radius = Math.max(3.0D, 4.5D - pulse * 0.5D);
        List<Location> safeZones = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            double angle = (Math.PI * 2.0D * index / 3.0D) + pulse * 0.55D;
            safeZones.add(core.clone().add(Math.cos(angle) * 6.0D, 0.0D, Math.sin(angle) * 6.0D));
        }
        for (Player player : activeLivingPlayers()) {
            boolean safe = safeZones.stream().anyMatch(zone ->
                    horizontalDistanceSquared(player.getLocation(), zone) <= radius * radius
                            && Math.abs(player.getLocation().getY() - zone.getY()) <= 1.5D);
            player.spawnParticle(safe ? Particle.END_ROD : Particle.DRAGON_BREATH,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), safe ? 10 : 32,
                    0.35D, 0.55D, 0.35D, 0.02D);
            if (!safe) {
                player.damage(12.0D, boss);
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                        BOSS_JUDGMENT_WITHER_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                        BOSS_JUDGMENT_WEAKNESS_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
            }
        }
        for (Location zone : safeZones) {
            spawnJudgmentSafeZoneVisual(zone, radius, pulse);
            for (Player player : activeLivingPlayers()) {
                if (player.getWorld().equals(zone.getWorld())) {
                    player.spawnParticle(Particle.END_ROD, zone.clone().add(0.0D, 0.15D, 0.0D),
                            24, radius, 0.1D, radius, 0.0D);
                }
            }
        }
        getLogger().info("BOSS_JUDGMENT_PULSE event=" + eventId + " boss=" + boss.getUniqueId()
                + " pulse=" + (pulse + 1) + " safe_zones=3");
    }

    private void finishBossCast(LivingEntity boss) {
        if (boss == null) {
            return;
        }
        boss.setInvulnerable(false);
        bossCastDeadlineMillis = 0L;
        if (bossCastState == BossCastState.JUDGMENT_CAST) {
            judgmentCompleted = true;
            bossCastState = BossCastState.EXHAUSTED;
            bossCastDeadlineMillis = System.currentTimeMillis() + EXHAUSTED_WINDOW_TICKS * 50L;
            bossSpellPauseUntilMillis = bossCastDeadlineMillis;
            bossStage = BossStage.CATASTROPHE;
            announceEventTitle("§4СТРАЖ ИСТОЩЁН", "§fСейчас он уязвим", true);
            clearJudgmentVisuals();
        } else if (bossCastState == BossCastState.ABSORPTION_CHANNEL) {
            absorptionCompleted = true;
            absorptionAttackEmpowered = true;
            BossStagePolicy.CombatProfile profile = currentBossCombatProfile();
            bossCastState = BossCastState.NONE;
            bossSpellPauseUntilMillis = System.currentTimeMillis() + 1_500L;
            if (!isTestBoss(boss) && !saveStateSync()) {
                // The physical flag is already cleared above.  If the durable
                // post-channel marker cannot be saved, stop the official fight
                // in recovery rather than replaying or losing the one-shot
                // empowered attack on restart.
                absorptionAttackEmpowered = false;
                forcePhase(EventPhase.RECOVERY_REQUIRED,
                        "absorption completion could not be persisted");
                return;
            }
            getLogger().info("BOSS_ABSORPTION_BUFF event=" + eventId
                    + " boss=" + boss.getUniqueId()
                    + " movement_speed=" + profile.movementSpeed()
                    + " spell_cooldown_multiplier=" + profile.spellCooldownMultiplier()
                    + " next_melee_bonus=" + profile.nextMeleeAttackBonus()
                    + " damageable=true");
        } else {
            bossCastState = BossCastState.NONE;
            bossSpellPauseUntilMillis = System.currentTimeMillis() + 1_000L;
        }
        getLogger().info("BOSS_CAST_STATE event=" + eventId + " boss=" + boss.getUniqueId()
                + " state=" + bossCastState + " damageable=true");
    }

    private void renderBossCastState(LivingEntity boss) {
        if (boss == null) {
            return;
        }
        Location core = coreCombatAnchorLocation();
        for (Player player : eventAudience()) {
            if (player.getWorld().equals(boss.getWorld())) {
                player.spawnParticle(bossCastState == BossCastState.JUDGMENT_CAST
                                ? Particle.REVERSE_PORTAL : Particle.END_ROD,
                        boss.getLocation().add(0.0D, 1.0D, 0.0D), 8,
                        0.35D, 0.7D, 0.35D, 0.02D);
                if (core != null && bossCastState == BossCastState.ABSORPTION_CHANNEL) {
                    spawnParticleLineForPlayer(player, core.clone().add(0.0D, 1.0D, 0.0D),
                            boss.getLocation().add(0.0D, 1.0D, 0.0D), 6);
                }
            }
        }
    }

    private void spawnParticleLineForPlayer(Player player, Location from, Location to, int points) {
        if (player == null || from == null || to == null || from.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        recordParticleEmission(Math.max(0, points));
        Vector delta = to.toVector().subtract(from.toVector()).multiply(1.0D / Math.max(1, points));
        Location current = from.clone();
        for (int index = 0; index < points; index++) {
            current.add(delta);
            player.spawnParticle(Particle.END_ROD, current, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void cancelBossCastTask() {
        if (bossCastTask != null) {
            bossCastTask.cancel();
            bossCastTask = null;
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
        bossTacticCycle++;
        nextBossTacticMillis = 0L;
        recentBossTargets.addLast(target.getUniqueId());
        while (recentBossTargets.size() > config.bossRecentTargetMemory()) {
            recentBossTargets.removeFirst();
        }
        combatHelpers.add(target.getUniqueId());
        getLogger().info("BOSS_AI_TARGET boss=" + boss.getUniqueId() + " target=" + target.getUniqueId()
                + " recent=" + recentBossTargets.size());
    }

    private void maintainBossTeleport(LivingEntity boss, long now) {
        BossStagePolicy.CombatProfile profile = currentBossCombatProfile();
        long teleportCooldownMillis = scaledBossDelayMillis(
                config.bossTeleportCooldownSeconds(), profile.teleportCooldownMultiplier(), 2_500L);
        if (!(boss instanceof Mob mob) || now - lastBossTeleportMillis < teleportCooldownMillis) {
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        if (anchor == null || !(mob.getTarget() instanceof Player target)
                || !isCombatTarget(target)) {
            return;
        }
        boolean targetTooFar = horizontalDistanceSquared(boss.getLocation(), target.getLocation()) > 144.0D;
        boolean arenaTooWide = horizontalDistanceSquared(boss.getLocation(), anchor)
                > Math.max(1.0D, config.bossRadius() - 2.0D) * Math.max(1.0D, config.bossRadius() - 2.0D);
        boolean targetOnCore = isCoreBlockPosition(target.getLocation());
        if (!targetTooFar && !arenaTooWide && !targetOnCore) {
            return;
        }
        Location from = boss.getLocation().clone();
        Location safe = findSafeCombatLocation(anchor, target.getLocation(), config.bossRadius() - 1.0D,
                MIN_BOSS_CORE_DISTANCE_BLOCKS);
        if (safe != null && teleportCombatEntity(boss, safe)) {
            lastBossTeleportMillis = now;
            spawnEventParticle(safe.add(0.0D, 1.0D, 0.0D), Particle.PORTAL,
                    20, 0.45D, 0.7D, 0.45D, 0.02D);
            getLogger().info("BOSS_MOVE_TELEPORT boss=" + boss.getUniqueId()
                    + " reason=" + (targetOnCore ? "target-on-core" : targetTooFar ? "target-too-far" : "arena-too-wide")
                    + " from=" + locationText(from) + " to=" + locationText(safe)
                    + " target=" + target.getUniqueId() + " phase=" + phase);
        }
    }

    /**
     * Vanilla Enderman pathing can settle on the Core edge while a target is
     * alive.  After a bounded observation window, choose one safe flank and
     * use the same movement controller as every other combat teleport.
     */
    private void detectBossStuck(LivingEntity boss, long now) {
        if (!(boss instanceof Mob mob) || !(mob.getTarget() instanceof Player target)
                || !isCombatTarget(target)) {
            bossProgressEntity = boss == null ? null : boss.getUniqueId();
            bossLastProgressLocation = boss == null ? null : boss.getLocation().clone();
            bossLastProgressAt = now;
            return;
        }
        if (!boss.getUniqueId().equals(bossProgressEntity) || bossLastProgressLocation == null) {
            bossProgressEntity = boss.getUniqueId();
            bossLastProgressLocation = boss.getLocation().clone();
            bossLastProgressAt = now;
            return;
        }
        if (now - bossLastProgressAt < 4_000L) {
            return;
        }
        Location current = boss.getLocation().clone();
        double moved = current.distance(bossLastProgressLocation);
        bossLastProgressLocation = current;
        bossLastProgressAt = now;
        if (moved >= 0.5D || now < nextBossStuckTeleportMillis) {
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        Location safe = anchor == null ? null : findSafeCombatLocation(anchor, target.getLocation(),
                config.bossRadius() - 1.0D, MIN_BOSS_CORE_DISTANCE_BLOCKS);
        if (safe != null && teleportCombatEntity(boss, safe)) {
            nextBossStuckTeleportMillis = now
                    + Math.max(1L, config.bossTeleportCooldownSeconds()) * 1000L;
            getLogger().info("BOSS_MOVE_TELEPORT boss=" + boss.getUniqueId()
                    + " reason=stuck from=" + locationText(current)
                    + " to=" + locationText(safe) + " target=" + target.getUniqueId()
                    + " phase=" + phase);
        }
    }

    private void releaseExpiredBossCastBeforeDamage(LivingEntity boss, long now) {
        if (boss == null) {
            return;
        }
        // The scheduler normally closes this state.  The damage path is also
        // an authoritative recovery boundary for a delayed tick, plugin
        // reload, or cancelled task.  Use the same deadline policy here so an
        // expired cast can never leave the boss permanently immune.
        reconcileBossCastProjection(boss, now);
        boolean damageablePhase = phase == EventPhase.BOSS_ACTIVE
                || phase == EventPhase.BOSS_FINISH && finalDrainTriggered;
        boolean activeCast = bossCastState == BossCastState.ABSORPTION_CHANNEL
                || bossCastState == BossCastState.JUDGMENT_CAST;
        if (damageablePhase && !activeCast && boss.isInvulnerable()) {
            boss.setInvulnerable(false);
            getLogger().warning("BOSS_DAMAGE_IMMUNITY_RECOVERED event=" + eventId
                    + " boss=" + boss.getUniqueId() + " cast=" + bossCastState);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)
                || bossUuid == null || !bossUuid.equals(boss.getUniqueId())
                || !EVENT_KIND_BOSS.equals(readString(boss, keyKind))) {
            return;
        }
        EntityDamageByEntityEvent damageSourceEvent = event instanceof EntityDamageByEntityEvent damageByEntity
                ? damageByEntity : null;
        Entity source = damageSourceEvent == null ? null : damageSourceEvent.getDamager();
        getLogger().info("BOSS_DAMAGE_EVENT event=" + eventId + " boss=" + boss.getUniqueId()
                + " source=" + (source == null ? "environment" : source.getType() + ":" + source.getUniqueId())
                + " cause=" + event.getCause() + " raw=" + event.getDamage()
                + " final=" + event.getFinalDamage() + " cancelled=" + event.isCancelled()
                + " phase=" + phase + " cast=" + bossCastState);
        if (isTestBoss(boss)) {
            return;
        }
        releaseExpiredBossCastBeforeDamage(boss, System.currentTimeMillis());
        if (phase == EventPhase.BOSS_FINISH && finalDrainTriggered
                && BossDamagePolicy.damageAllowed(bossStage, bossCastState,
                System.currentTimeMillis(), bossCastDeadlineMillis)) {
            // FINAL_WAVE has released the boss.  Keep the final damage in the
            // same authoritative path so the exhausted window can apply its
            // 50% incoming-damage bonus and the official EntityDeathEvent
            // victory transaction remains the only completion trigger.
            if (boss.isInvulnerable()) {
                // A cancelled task or a reconnect must never leave the boss
                // permanently immune after a bounded cast has ended.
                boss.setInvulnerable(false);
            }
            // Capture the final event damage before cancelling it.  Mutating
            // event damage and then reading getFinalDamage() made the
            // exhausted 1.5x bonus get applied a second time by Bukkit's
            // recalculation path.
            double incomingDamage = Math.max(0.0D, event.getFinalDamage());
            double effectiveDamage = BossDamagePolicy.applyIncomingDamage(
                    incomingDamage, bossCastState);
            event.setCancelled(true);
            applyBossDamage(boss, effectiveDamage,
                    event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null);
            return;
        }
        if (phase != EventPhase.BOSS_ACTIVE) {
            event.setCancelled(true);
            return;
        }
        if (!BossDamagePolicy.damageAllowed(bossStage, bossCastState,
                System.currentTimeMillis(), bossCastDeadlineMillis)) {
            event.setCancelled(true);
            getLogger().info("BOSS_DAMAGE_BLOCKED event=" + eventId + " boss=" + boss.getUniqueId()
                    + " cast=" + bossCastState + " deadline=" + bossCastDeadlineMillis);
            return;
        }
        if (boss.isInvulnerable()) {
            // Watchdog rule: invulnerability is a projection of the bounded
            // state, never an independent sticky flag.
            boss.setInvulnerable(false);
        }
        double incomingDamage = Math.max(0.0D, event.getFinalDamage());
        double effectiveDamage = BossDamagePolicy.applyIncomingDamage(
                incomingDamage, bossCastState);
        event.setCancelled(true);
        applyBossDamage(boss, effectiveDamage, event instanceof EntityDamageByEntityEvent byEntity
                ? byEntity.getDamager() : null);
    }

    private void applyBossDamage(LivingEntity boss, double damage, Entity source) {
        if (boss == null || !boss.isValid() || boss.isDead()
                || isTestBoss(boss) && !testCombatAiMode) {
            return;
        }
        double safeDamage = Math.max(0.0D, damage);
        double currentHealth = bossVirtualHealth(boss);
        double projectedHealth = Math.max(0.0D, currentHealth - safeDamage);
        // Do not let one hit skip the bounded Absorption phase. Pin the
        // authoritative virtual HP to its upper boundary so the phase is
        // visible and damageable again after the five-second channel; the
        // next ordinary hits can then carry the fight into Catastrophe.
        if (!absorptionTriggered && !finalDrainTriggered
                && projectedHealth <= BossStage.ABSORPTION.upperInclusive()) {
            setBossVirtualHealth(boss, BossStage.ABSORPTION.upperInclusive());
            synchronizeBossStage(boss);
            if (bossCastState == BossCastState.ABSORPTION_CHANNEL) {
                return;
            }
        }
        // Judgment begins at exactly 250 HP.  Pinning the health prevents a
        // lethal hit from bypassing the cast and creates a deterministic
        // damageable window after it ends.
        if (!judgmentTriggered && !finalDrainTriggered
                && projectedHealth <= BossStagePolicy.judgmentThreshold()) {
            setBossVirtualHealth(boss, BossStagePolicy.judgmentThreshold());
            synchronizeBossStage(boss);
            return;
        }
        if (finalDrainTriggered) {
            if (!boss.isInvulnerable()) {
                setBossVirtualHealth(boss, projectedHealth);
                if (source instanceof Player player) {
                    bossKillerUuid = player.getUniqueId();
                }
                synchronizeBossStage(boss);
                if (projectedHealth <= 0.0D) {
                    commitOfficialBossDefeat(boss, source);
                }
            }
            return;
        }
        BossThresholdPolicy.Decision decision = BossThresholdPolicy.evaluate(
                currentHealth, safeDamage, config.bossHealth(), config.bossHalfHealth(),
                config.bossFinalThreshold(), config.bossFinalHealth(), halfHealthTriggered, finalDrainTriggered);
        if (decision.triggerHalf()) {
            triggerHalfPhase(boss);
        }
        if (decision.triggerFinal()) {
            if (isOfficialEntity(boss)) {
                // The new canonical fight has one Judgment cast at 10% and
                // no extra final wave.  Once Judgment has completed, damage
                // is applied normally down to zero.
                setBossVirtualHealth(boss, projectedHealth);
                synchronizeBossStage(boss);
                if (projectedHealth <= 0.0D) {
                    commitOfficialBossDefeat(boss, source);
                }
            } else {
                triggerFinalPhase(boss, false);
            }
            return;
        }
        if (!boss.isInvulnerable()) {
            setBossVirtualHealth(boss, decision.appliedHealth());
            synchronizeBossStage(boss);
            if (decision.appliedHealth() <= 0.0D) {
                commitOfficialBossDefeat(boss, source);
            }
        }
        if (source instanceof Player player) {
            bossKillerUuid = player.getUniqueId();
            if (isActiveArenaParticipant(player)) {
                combatHelpers.add(player.getUniqueId());
            }
        }
    }

    private void triggerHalfPhase(LivingEntity boss) {
        if (halfHealthTriggered) {
            return;
        }
        halfHealthTriggered = true;
        controlSpellUnlocked = true;
        // Persist the threshold marker before healing/control side effects.
        if (!isTestBoss(boss) && !saveStateSync()) {
            forcePhase(EventPhase.RECOVERY_REQUIRED, "half phase could not be persisted");
            return;
        }
        for (Player player : activeLivingPlayers()) {
            player.setHealth(player.getMaxHealth());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Печать слабеет... Энергия Разлома возвращает вам силы.");
            player.spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D),
                    12, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        bossSpellPauseUntilMillis = System.currentTimeMillis() + 3_000L;
        nextSpellMillis = bossSpellPauseUntilMillis;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.0F, 0.6F);
        playEventMusic(musicForPhase());
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

    private void commitOfficialBossDefeat(LivingEntity boss, Entity source) {
        if (boss == null || boss.isDead() || isTestBoss(boss)) {
            return;
        }
        if (source instanceof Player player) {
            bossKillerUuid = player.getUniqueId();
        }
        if (phase == EventPhase.BOSS_ACTIVE) {
            if (!transition(EventPhase.BOSS_FINISH, "Judgment completed; boss defeated",
                    eventId + ":boss-defeat")) {
                getLogger().severe("BOSS_DEFEAT_PHASE_COMMIT_FAILED event=" + eventId
                        + " boss=" + boss.getUniqueId());
                return;
            }
        }
        int waveCombatRemoved = clearWaveCombatEntities("official-boss-defeat");
        activeWave = 0;
        clearActiveRiftProjectiles();
        clearVoidMarkZones();
        clearJudgmentVisuals();
        boss.setInvulnerable(false);
        getLogger().info("BOSS_DEFEAT_COMMITTED event=" + eventId
                + " boss=" + boss.getUniqueId() + " judgment_completed=" + judgmentCompleted
                + " wave_combat_removed=" + waveCombatRemoved);
        boss.setHealth(0.0D);
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
            boss.setInvulnerable(false);
            bossCastState = BossCastState.NONE;
            bossCastDeadlineMillis = 0L;
            forcePhase(EventPhase.RECOVERY_REQUIRED, "final phase could not be persisted before side effects");
            return;
        }
        clearClientEffects();
        Location anchor = coreCombatAnchorLocation();
        if (anchor != null) {
            Location safe = findSafeCombatLocation(anchor, null, config.bossRadius() - 1.0D,
                    MIN_BOSS_CORE_DISTANCE_BLOCKS);
            if (safe != null) {
                teleportCombatEntity(boss, safe);
            }
        }
        setBossVirtualHealth(boss, config.bossFinalHealth());
        if (!transition(EventPhase.FINAL_DRAIN, "boss crossed final threshold", eventId + ":final-drain")) {
            boss.setInvulnerable(false);
            bossCastState = BossCastState.NONE;
            bossCastDeadlineMillis = 0L;
            return;
        }
        playEventMusic(musicForPhase());
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
                player.spawnParticle(Particle.REVERSE_PORTAL,
                        player.getLocation().add(0.0D, 1.0D, 0.0D), 4, 0.15D, 0.25D, 0.15D, 0.01D);
                if (core != null) {
                    spawnParticleLine(player, player.getLocation().add(0.0D, 1.0D, 0.0D), core, 3);
                }
            }
            if (core != null) {
                for (Player player : activeLivingPlayers()) {
                    spawnParticleLine(player, core.clone().add(0.0D, 1.0D, 0.0D), target, 6);
                }
            }
            if (ticks[0] >= config.finalRitualTelegraphTicks()) {
                holder[0].cancel();
                if (finalRitualVisualTask == holder[0]) {
                    finalRitualVisualTask = null;
                }
                if ((phase == EventPhase.FINAL_DRAIN || phase == EventPhase.FINAL_RITUAL)
                        && taskRegistry.owns(callbackGeneration)) {
                    if (transition(EventPhase.FINAL_WAVE, "final ritual visual complete", eventId + ":final-wave")) {
                        spawnWave(FINAL_WAVE_NUMBER, false);
                    }
                }
            }
        }, 1L, 5L);
        finalRitualVisualTask = holder[0];
        taskRegistry.register(holder[0]);
    }

    private void spawnParticleLine(Player viewer, Location from, Location to, int points) {
        if (viewer == null || !viewer.isOnline() || from == null || to == null
                || from.getWorld() == null || !from.getWorld().equals(to.getWorld())
                || !viewer.getWorld().equals(from.getWorld())
                || viewer.getLocation().distanceSquared(from) > 64.0D * 64.0D) {
            return;
        }
        recordParticleEmission(Math.max(0, points));
        Vector delta = to.toVector().subtract(from.toVector()).multiply(1.0D / Math.max(1, points));
        Location current = from.clone();
        for (int index = 0; index < points; index++) {
            current.add(delta);
            viewer.spawnParticle(Particle.END_ROD, current, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void castBossSpell(LivingEntity boss, boolean forced) {
        boolean testBossAi = testCombatAiMode && isTestBoss(boss);
        if (boss == null || isTestBoss(boss) && !forced && !testBossAi
                || phase != EventPhase.BOSS_ACTIVE && !forced && !testBossAi
                || bossCastState == BossCastState.ABSORPTION_CHANNEL
                || bossCastState == BossCastState.JUDGMENT_CAST
                || bossCastState == BossCastState.EXHAUSTED) {
            return;
        }
        List<EndRiftAiPolicy.BossSpell> available = new ArrayList<>(BossStagePolicy.spellPool(bossStage));
        // WILL_DISTORTION is present in late-stage static pools for balance
        // documentation, but it is a one-shot unlock.  Never select a no-op
        // control cast before 50% HP or while another player is controlled.
        if (controlSpellUnlocked && controlInstances.isEmpty()) {
            if (!available.contains(EndRiftAiPolicy.BossSpell.WILL_DISTORTION)) {
                available.add(EndRiftAiPolicy.BossSpell.WILL_DISTORTION);
            }
        } else {
            available.remove(EndRiftAiPolicy.BossSpell.WILL_DISTORTION);
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

    /** Request role-aware pathing without ever steering the boss at Core. */
    private void maintainBossPath(LivingEntity boss, long now) {
        if (!(boss instanceof Mob mob) || now - lastBossPathRequestMillis < 500L
                || !(mob.getTarget() instanceof Player target) || !isCombatTarget(target)) {
            return;
        }
        Location anchor = coreCombatAnchorLocation();
        if (anchor == null) {
            return;
        }
        if (now >= nextBossTacticMillis) {
            bossTacticCycle++;
            nextBossTacticMillis = now + BOSS_TACTIC_REFRESH_MILLIS;
        }
        boolean targetOnCore = isCoreBlockPosition(target.getLocation());
        double targetDistance = Math.sqrt(horizontalDistanceSquared(boss.getLocation(), target.getLocation()));
        CombatTacticsPolicy.BossPlan plan = CombatTacticsPolicy.bossPlan(
                bossStage, bossTacticCycle, targetDistance, targetOnCore);
        if (keyCombatTactic != null) {
            boss.getPersistentDataContainer().set(keyCombatTactic,
                    PersistentDataType.STRING, plan.tactic().name());
        }
        if (plan.tactic() == CombatTacticsPolicy.BossTactic.PHANTOM_FEINT
                && !targetOnCore
                && now - lastBossFeintMillis >= BOSS_FEINT_COOLDOWN_MILLIS) {
            Location feint = bossFlankTargetLocation(anchor, mob, target,
                    plan.preferredDistance(), -plan.orbitDirection());
            feint = findSafeCombatLocation(anchor, feint,
                    boundedCombatRadius(config.bossRadius()) - 1.0D,
                    MIN_BOSS_CORE_DISTANCE_BLOCKS);
            if (feint != null && horizontalDistanceSquared(mob.getLocation(), feint) >= 9.0D) {
                Location from = mob.getLocation().clone();
                if (teleportCombatEntity(mob, feint)) {
                    lastBossFeintMillis = now;
                    lastBossPathRequestMillis = now;
                    // The destination is already safe; this is only a visual
                    // marker for the short tactical reposition.
                    spawnEventParticle(from.add(0.0D, 1.0D, 0.0D), Particle.REVERSE_PORTAL,
                            28, 0.8D, 0.6D, 0.8D, 0.02D);
                    spawnEventParticle(feint.clone().add(0.0D, 1.0D, 0.0D), Particle.END_ROD,
                            20, 0.45D, 0.75D, 0.45D, 0.02D);
                    mob.getWorld().playSound(feint, Sound.ENTITY_ENDERMAN_TELEPORT,
                            0.8F, 1.15F);
                    getLogger().info("BOSS_AI_FEINT boss=" + mob.getUniqueId()
                            + " target=" + target.getUniqueId()
                            + " phase=" + bossStage + " destination=" + locationText(feint));
                    return;
                }
            }
        }
        Location destination = bossTacticalDestination(anchor, mob, target, plan);
        if (destination == null) {
            return;
        }
        double distance = horizontalDistanceSquared(boss.getLocation(), destination);
        if (distance <= 9.0D) {
            return;
        }
        // Paper's Pathfinder is bounded to the current world and respects the
        // mob's normal collision/navigation rules.  The leash below remains
        // the hard safety boundary if navigation chooses a bad route.
        BossStagePolicy.CombatProfile profile = currentBossCombatProfile();
        double speed = profile.movementSpeed();
        boolean moved = mob.getPathfinder().moveTo(destination, speed);
        if (!moved) {
            moved = requestBoundedCombatMovement(mob, destination, speed, anchor,
                    boundedCombatRadius(config.bossRadius()), MIN_BOSS_CORE_DISTANCE_BLOCKS,
                    "BOSS_AI_PATH");
        }
        if (moved) {
            lastBossPathRequestMillis = now;
            getLogger().info("BOSS_AI_PATH boss=" + boss.getUniqueId()
                    + " target=" + target.getUniqueId()
                    + " phase=" + bossStage + " tactic=" + plan.tactic()
                    + " speed=" + speed
                    + " destination=" + locationText(destination));
            getLogger().fine("BOSS_TACTIC boss=" + boss.getUniqueId()
                    + " tactic=" + plan.tactic() + " cycle=" + bossTacticCycle
                    + " preferred_distance=" + plan.preferredDistance()
                    + " target_on_core=" + targetOnCore);
        }
    }

    private Location bossTacticalDestination(Location anchor, Mob boss, Player target,
                                              CombatTacticsPolicy.BossPlan plan) {
        if (anchor == null || boss == null || target == null || plan == null) {
            return null;
        }
        Location preferred;
        if (plan.preferOuterRing() || isCoreBlockPosition(target.getLocation())) {
            preferred = stableCombatRingLocation(anchor, boss, 7.5D, 10.0D);
        } else {
            preferred = switch (plan.tactic()) {
                case RING_ORBIT -> stableCombatRingLocation(anchor, boss,
                        plan.preferredDistance(), plan.preferredDistance() + 2.0D);
                case FLANK, CROSSCUT -> bossFlankTargetLocation(anchor, boss, target,
                        plan.preferredDistance(), plan.orbitDirection());
                case PHANTOM_FEINT -> bossFlankTargetLocation(anchor, boss, target,
                        plan.preferredDistance(), -plan.orbitDirection());
                case ABSORPTION_RETREAT -> stableCombatRingLocation(anchor, boss, 8.0D, 10.5D);
                case CATASTROPHE_PRESSURE -> target.getLocation();
            };
        }
        return findSafeCombatLocation(anchor, preferred,
                boundedCombatRadius(config.bossRadius()) - 1.0D,
                MIN_BOSS_CORE_DISTANCE_BLOCKS);
    }

    private Location bossFlankTargetLocation(Location anchor, Mob boss, Player target,
                                              double distance, double direction) {
        if (anchor == null || boss == null || target == null) {
            return null;
        }
        Vector radial = target.getLocation().toVector().subtract(anchor.toVector());
        radial.setY(0.0D);
        if (radial.lengthSquared() < 0.01D) {
            radial = new Vector(1.0D, 0.0D, 0.0D);
        }
        radial.normalize();
        Vector side = new Vector(-radial.getZ(), 0.0D, radial.getX());
        double sign = direction < 0.0D ? -1.0D : 1.0D;
        return target.getLocation().clone().add(side.multiply(sign * Math.max(
                MIN_BOSS_CORE_DISTANCE_BLOCKS, distance)));
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
            Particle particle = switch (spell) {
                case VOID_BLAST -> Particle.DRAGON_BREATH;
                case RIFT_ARROWS -> Particle.CRIT;
                default -> Particle.REVERSE_PORTAL;
            };
            Location effect = spell == EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS
                    ? boss.getLocation().add(0.0D, 1.0D, 0.0D) : mark;
            spawnEventParticle(effect, particle, 12, 0.9D, 0.2D, 0.9D, 0.02D);
            renderSpellTelegraphVisual(boss, mark, spell.id(), ticks[0],
                    config.bossSpellTelegraphTicks());
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
                    renderSpellImpactVisual(boss,
                            spell == EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS
                                    ? boss.getLocation() : mark,
                            spell.id());
                    switch (spell) {
                        case VOID_BLAST -> voidBlast(boss, target);
                        case RIFT_PROJECTILE -> riftProjectile(boss, target);
                        case RIFT_ARROWS -> riftArrowVolley(boss, target,
                                EndRiftAiPolicy.BossSpell.RIFT_ARROWS.id(),
                                new SkeletonCombatPolicy.ArrowProfile(3, BOSS_PROJECTILE_DAMAGE, 60, "rift_salvo"));
                        case VOID_MARK -> voidMark(boss, target);
                        case SUMMON_SERVANTS -> summonServants(boss);
                        case WILL_DISTORTION -> sendControlStart(target);
                        case ARENA_INFERNO -> arenaInferno(boss);
                    }
                });
    }

    private void voidBlast(LivingEntity boss, Player target) {
        Location center = target.getLocation();
        spawnEventParticle(center, Particle.DRAGON_BREATH, 24,
                1.0D, 0.4D, 1.0D, 0.04D);
        boss.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_STARE, 0.8F, 0.7F);
        for (Player player : activeLivingPlayers()) {
            if (player.getLocation().distanceSquared(center) <= 16.0D) {
                player.damage(BOSS_BLAST_DAMAGE, boss);
                Vector push = player.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() < 0.01D) {
                    push = new Vector(0.0D, 0.3D, 0.0D);
                }
                player.setVelocity(push.normalize().multiply(0.45D).setY(0.3D));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                        BOSS_BLAST_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
            }
        }
    }

    private void arenaInferno(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null || coreCombatAnchorLocation() == null) {
            return;
        }
        clearArenaInferno();
        World world = boss.getWorld();
        int minX = Math.max(arenaMinX, coreX - (int) Math.floor(config.arenaRadius()));
        int maxX = Math.min(arenaMaxX, coreX + (int) Math.floor(config.arenaRadius()));
        int minZ = Math.max(arenaMinZ, coreZ - (int) Math.floor(config.arenaRadius()));
        int maxZ = Math.min(arenaMaxZ, coreZ + (int) Math.floor(config.arenaRadius()));
        int fireY = combatLevelY();
        List<HazardMutationJournal.Entry> journalEntries = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block floor = world.getBlockAt(x, fireY - 1, z);
                boolean safeLane = Math.floorMod(x * 31 + z * 17, 100) < 35;
                boolean protectedCell = isProtectedInfernoCell(x, z);
                if (!safeLane && !protectedCell && floor.getType() != Material.MAGMA_BLOCK
                        && floor.getType().isSolid() && !floor.isLiquid()
                        && isArenaLocation(floor.getLocation())) {
                    arenaInfernoBlocks.add(floor);
                    HazardPlanner.Point point = new HazardPlanner.Point(x, z);
                    String original = floor.getBlockData().getAsString();
                    arenaInfernoOriginalBlocks.put(point, original);
                    journalEntries.add(new HazardMutationJournal.Entry(
                            x, fireY - 1, z, original, "", "MAGMA"));
                }
            }
        }
        if (journalEntries.isEmpty()) {
            return;
        }
        if (hazardJournal == null || !hazardJournal.prepare(eventId, generation, world.getName(), journalEntries)) {
            arenaInfernoBlocks.clear();
            arenaInfernoOriginalBlocks.clear();
            getLogger().severe("BOSS_SPELL_ARENA_INFERNO_REFUSED event=" + eventId
                    + " reason=journal-prepare-failed");
            return;
        }
        try {
            for (HazardMutationJournal.Entry entry : journalEntries) {
                world.getBlockAt(entry.x(), entry.floorY(), entry.z()).setType(Material.MAGMA_BLOCK, false);
            }
            if (!hazardJournal.markApplied()) {
                getLogger().severe("BOSS_SPELL_ARENA_INFERNO_JOURNAL_FAILED event=" + eventId
                        + " journal=" + hazardJournal.path());
            }
        } catch (RuntimeException error) {
            getLogger().log(Level.SEVERE, "BOSS_SPELL_ARENA_INFERNO_APPLY_FAILED event=" + eventId, error);
            restoreArenaInfernoBlocks();
            return;
        }
        Location center = coreCombatAnchorLocation().add(0.0D, 0.8D, 0.0D);
        world.playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, 1.0F, 0.5F);
        announceEventTitle("§4ПЛАМЯ РАЗЛОМА", "§cИщите безопасные сектора — пять секунд", true);
        getLogger().info("BOSS_SPELL_ARENA_INFERNO event=" + eventId
                + " logical_hazards=" + arenaInfernoBlocks.size()
                + " safe_floor_ratio>=0.35"
                + " real_fire=false visual=particles+magma"
                + " damage_interval_ticks=20"
                + " journal_entries=" + journalEntries.size()
                + " duration_ticks=" + ARENA_INFERNO_DURATION_TICKS);
        final int[] elapsedSeconds = {0};
        arenaInfernoTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            LivingEntity live = liveBoss();
            if (live == null || elapsedSeconds[0] >= ARENA_INFERNO_DURATION_TICKS / 20) {
                clearArenaInferno();
                return;
            }
            renderArenaInferno(live);
            elapsedSeconds[0]++;
        }, 0L, 20L);
        if (taskRegistry != null) {
            taskRegistry.register(arenaInfernoTask);
        }
    }

    private void renderArenaInferno(LivingEntity boss) {
        if (boss == null) {
            return;
        }
        for (Player player : eventAudience()) {
            if (!player.getWorld().equals(boss.getWorld())) {
                continue;
            }
            boolean danger = arenaInfernoBlocks.stream().anyMatch(block ->
                    block.getX() == player.getLocation().getBlockX()
                            && block.getZ() == player.getLocation().getBlockZ());
            player.spawnParticle(danger ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD,
                    player.getLocation().add(0.0D, 0.15D, 0.0D), danger ? 16 : 8,
                    0.45D, 0.1D, 0.45D, 0.02D);
            if (danger) {
                player.damage(6.0D, boss);
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                        ARENA_INFERNO_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
            }
        }
    }

    private void clearArenaInferno() {
        if (arenaInfernoTask != null) {
            arenaInfernoTask.cancel();
            arenaInfernoTask = null;
        }
        if (restoreArenaInfernoBlocks()) {
            arenaInfernoBlocks.clear();
        }
    }

    private boolean isProtectedInfernoCell(int x, int z) {
        if (x == coreX && z == coreZ) {
            return true;
        }
        for (EventSnapshot.PadSnapshot pad : pads) {
            if (pad.x() == x && pad.z() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean restoreArenaInfernoBlocks() {
        if (arenaInfernoOriginalBlocks.isEmpty()) {
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().severe("BOSS_SPELL_ARENA_INFERNO_RESTORE_BLOCKED event=" + eventId
                    + " reason=world-not-loaded");
            return false;
        }
        int restored = 0;
        int skipped = 0;
        int fireY = combatLevelY();
        for (Map.Entry<HazardPlanner.Point, String> entry : arenaInfernoOriginalBlocks.entrySet()) {
            HazardPlanner.Point point = entry.getKey();
            Block hazard = world.getBlockAt(point.x(), fireY - 1, point.z());
            if (hazard.getType() == Material.MAGMA_BLOCK) {
                restoreBlock(hazard, entry.getValue());
                restored++;
            } else {
                skipped++;
            }
        }
        if (hazardJournal != null && !hazardJournal.markRestored()) {
            getLogger().severe("BOSS_SPELL_ARENA_INFERNO_RESTORE_JOURNAL_FAILED event=" + eventId
                    + " journal=" + hazardJournal.path());
        }
        getLogger().info("BOSS_SPELL_ARENA_INFERNO_RESTORED event=" + eventId
                + " restored=" + restored + " skipped=" + skipped);
        arenaInfernoOriginalBlocks.clear();
        return true;
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
            if (age[0] % SPELL_FLIGHT_RENDER_INTERVAL_TICKS == 0) {
                Location projectilePoint = projectile.getLocation();
                for (Player viewer : eventAudience()) {
                    if (viewer.isOnline() && viewer.getWorld().equals(projectilePoint.getWorld())
                            && viewer.getLocation().distanceSquared(projectilePoint) <= 64.0D * 64.0D) {
                        spawnRiftProjectileTrail(viewer, projectilePoint,
                                projectile.getVelocity(), age[0]);
                    }
                }
            }
        }, 1L, 1L);
        riftProjectileTasks.put(projectileId, holder[0]);
        taskRegistry.register(holder[0]);
        getLogger().info("BOSS_PROJECTILE_SPAWN entity=" + projectileId
                + " visual=particle-only pattern=rift_projectile"
                + " target=" + target.getUniqueId() + " max_ticks=" + RIFT_PROJECTILE_MAX_TICKS);
    }

    /**
     * Fire a bounded, player-only arrow volley. The server arrow supplies
     * collision and hit timing; it is hidden from the vanilla renderer and
     * given a distinct particle trail so every cast remains readable without
     * changing any vanilla texture.
     */
    private void riftArrowVolley(LivingEntity caster, Player target, String spellId,
                                 SkeletonCombatPolicy.ArrowProfile profile) {
        if (caster == null || target == null || !isCombatTarget(target)
                || caster.getWorld() == null || profile == null
                || activeEventArrowAges.size() >= MAX_ACTIVE_EVENT_ARROWS) {
            return;
        }
        Location start = caster.getEyeLocation().clone();
        Vector base = target.getEyeLocation().toVector().subtract(start.toVector());
        if (base.lengthSquared() < 0.01D) {
            return;
        }
        base.normalize();
        Vector side = base.clone().crossProduct(new Vector(0.0D, 1.0D, 0.0D));
        if (side.lengthSquared() < 0.0001D) {
            side = new Vector(1.0D, 0.0D, 0.0D);
        }
        side.normalize();
        int count = Math.min(profile.arrowCount(),
                MAX_ACTIVE_EVENT_ARROWS - activeEventArrowAges.size());
        for (int index = 0; index < count; index++) {
            double lateral = (index - (count - 1) / 2.0D) * 0.12D;
            double vertical = (index - (count - 1) / 2.0D) * 0.035D;
            Vector direction = base.clone()
                    .add(side.clone().multiply(lateral))
                    .add(new Vector(0.0D, vertical, 0.0D))
                    .normalize();
            Arrow arrow = caster.getWorld().spawn(start, Arrow.class);
            arrow.setGravity(false);
            arrow.setVisibleByDefault(false);
            arrow.setShooter(caster);
            arrow.setVelocity(direction.multiply(0.78D));
            arrow.setDamage(0.0D);
            arrow.setCritical(true);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setColor(Color.fromRGB(244, 60, 255));
            tag(arrow, EVENT_KIND_PROJECTILE, readInt(caster, keyWave, 0), isOfficialEntity(caster));
            tagArrowSpell(arrow, spellId);
            trackEventArrow(arrow);
        }
        spawnEventParticle(start, Particle.END_ROD, 20, 0.25D, 0.25D, 0.25D, 0.02D);
        getLogger().info("EVENT_ARROW_VOLLEY_SPAWN caster=" + caster.getUniqueId()
                + " spell=" + spellId + " target=" + target.getUniqueId()
                + " arrows=" + count + " pattern=" + profile.particlePattern()
                + " max_ticks=" + EVENT_ARROW_MAX_TICKS);
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
            player.damage(BOSS_PROJECTILE_DAMAGE, boss);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    SLOWNESS_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
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
        spawnEventParticle(mark.clone().add(0.0D, 0.2D, 0.0D), Particle.REVERSE_PORTAL,
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
            spawnEventParticle(center.clone().add(0.0D, 0.12D, 0.0D), Particle.REVERSE_PORTAL,
                    20, 1.0D, 0.08D, 1.0D, 0.02D);
            for (Player player : activeLivingPlayers()) {
                if (player.getLocation().distanceSquared(center)
                        <= VOID_MARK_RADIUS_BLOCKS * VOID_MARK_RADIUS_BLOCKS) {
                    player.damage(VOID_MARK_DAMAGE, boss);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                            VOID_MARK_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            SLOWNESS_DEBUFF_TICKS, configuredDebuffAmplifier(), false, true, true));
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
        if (boss == null || config.bossHealth() <= 0.0D) {
            return false;
        }
        double fraction = bossVirtualHealth(boss) / Math.max(1.0D, config.bossHealth());
        return (fraction <= 0.70D && fraction > 0.35D && !servantsSummonedAt70)
                || (fraction <= 0.35D && !servantsSummonedAt35);
    }

    private void summonServants(LivingEntity boss) {
        BossStagePolicy.CombatProfile profile = currentBossCombatProfile();
        int summonCap = Math.min(config.maxSummonedServants(), profile.summonCap());
        if (spellServants.size() >= summonCap) {
            return;
        }
        double fraction = config.bossHealth() <= 0.0D
                ? 0.0D : bossVirtualHealth(boss) / Math.max(1.0D, config.bossHealth());
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
        int toSpawn = Math.min(2, summonCap - spellServants.size());
        int spawned = 0;
        for (int index = 0; index < toSpawn; index++) {
            Entity servant = spawnOwnedMob(location.getWorld(), location, EntityType.SPIDER,
                    0, EVENT_KIND_WAVE_MOB, false, index + spellServants.size());
            if (servant != null) {
                spellServants.add(servant.getUniqueId());
                spawned++;
            }
        }
        getLogger().info("BOSS_SERVANTS_SUMMON boss=" + boss.getUniqueId()
                + " threshold=" + (at35 ? "35" : "70") + " count=" + spawned
                + " cap=" + summonCap + " stage=" + bossStage);
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
                    && isOfficialEntity(entity)
                    && (judgmentCompleted || finalDrainTriggered)) {
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
            nextSkeletonArrowMillis.remove(entity.getUniqueId());
            towerAggroUntil.remove(entity.getUniqueId());
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
        boolean victoryMusicAllowed = phase == EventPhase.VICTORY_PROCESSING || phase == EventPhase.VICTORY;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§5ЭНД ОТКРЫТ", "§dХранитель Разлома пал", 10, 80, 20);
            player.sendMessage("§5Энд открыт. Все участники: §f" + names
                    + " §7| §5Наградный roster: §f" + rewardNames);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            if (victoryMusicAllowed) {
                player.playSound(player.getLocation(), config.victoryMusic().soundId(), SoundCategory.MUSIC,
                        (float) config.musicVolume(), 1.0F);
            }
        }
        if (victoryMusicAllowed) {
            activeMusicTrackId = config.victoryMusic().soundId();
            getLogger().info("END_EVENT_MUSIC track=" + config.victoryMusic().soundId()
                    + " phase=" + phase);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recoverUnresolvedDepositsFor(player);
        resumeVictorySaga();
        if (isCombatPhase()) {
            refreshClientBindingsForPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clientBindingReadyPlayers.remove(uuid);
        stopControl(uuid);
        cancelShardChannel(uuid);
        padOccupants.values().removeIf(uuid::equals);
        runeVisualOccupants.values().removeIf(uuid::equals);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        clientBindingReadyPlayers.remove(uuid);
        stopControl(uuid);
        cancelShardChannel(uuid);
        padOccupants.values().removeIf(uuid::equals);
        runeVisualOccupants.values().removeIf(uuid::equals);
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
        clientBindingReadyPlayers.remove(uuid);
        stopControl(uuid);
        cancelShardChannel(uuid);
        padOccupants.values().removeIf(uuid::equals);
        runeVisualOccupants.values().removeIf(uuid::equals);
        if (isCombatPhase()) {
            refreshClientBindingsForPlayer(event.getPlayer());
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
            player.spawnParticle(Particle.REVERSE_PORTAL,
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
        // PDC remains the authority; this short vanilla tag only lets local
        // diagnostics select an event entity without ever touching a natural
        // mob with the same type near the arena.
        entity.addScoreboardTag("copimine_end_event");
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

    private Double readDouble(Entity entity, NamespacedKey key, Double fallback) {
        if (entity == null || key == null) {
            return fallback;
        }
        return entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.DOUBLE, fallback);
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
                    waveMobTactics.remove(entity.getUniqueId());
                    combatTeleportPermits.remove(entity.getUniqueId());
                    blockedTeleportLogAt.remove(entity.getUniqueId());
                    towerAggroUntil.remove(entity.getUniqueId());
                    removed++;
                }
            }
        }
        if (expectedEventId.equals(eventId)) {
            ownedEntities.clear();
            finalWaveEntities.clear();
            spellServants.clear();
            waveMobTactics.clear();
            combatTeleportPermits.clear();
            blockedTeleportLogAt.clear();
            towerAggroUntil.clear();
        }
        getLogger().info("END_EVENT_OWNED_CLEANUP event=" + expectedEventId + " generations=all removed=" + removed);
    }

    private boolean isEndEventOwnedRole(Entity entity) {
        String kind = readString(entity, keyKind);
        return EVENT_KIND_CORE.equals(kind) || EVENT_KIND_PAD.equals(kind)
                || EVENT_KIND_DISPLAY.equals(kind) || EVENT_KIND_WAVE_MOB.equals(kind)
                || EVENT_KIND_ELITE.equals(kind) || EVENT_KIND_BOSS.equals(kind)
                || EVENT_KIND_FINAL_WAVE.equals(kind) || EVENT_KIND_PROJECTILE.equals(kind)
                || EVENT_KIND_WAVE_REWARD.equals(kind);
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
                    waveMobTactics.remove(entity.getUniqueId());
                    combatTeleportPermits.remove(entity.getUniqueId());
                    blockedTeleportLogAt.remove(entity.getUniqueId());
                    towerAggroUntil.remove(entity.getUniqueId());
                }
            }
        }
        if (expectedEventId.equals(eventId) && expectedGeneration == generation) {
            ownedEntities.clear();
            finalWaveEntities.clear();
            spellServants.clear();
            waveMobTactics.clear();
            combatTeleportPermits.clear();
            blockedTeleportLogAt.clear();
            towerAggroUntil.clear();
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
            nextSkeletonArrowMillis.remove(entity.getUniqueId());
            nextWavePathRequestMillis.remove(entity.getUniqueId());
            lastWavePathLogMillis.remove(entity.getUniqueId());
            judgmentVisuals.remove(entity.getUniqueId());
            waveMobTactics.remove(entity.getUniqueId());
            combatTeleportPermits.remove(entity.getUniqueId());
            blockedTeleportLogAt.remove(entity.getUniqueId());
            towerAggroUntil.remove(entity.getUniqueId());
            activeEventArrowAges.remove(entity.getUniqueId());
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

    /**
     * AuthMe can complete after PlayerJoinEvent, and a player can walk into
     * the arena after joining. Retry once per second until the viewer is
     * eligible; this keeps entity textures and boss phase visuals scoped to
     * the correct player without sending a packet every tick.
     */
    private void refreshClientBindingsForOnlinePlayers() {
        if (!isCombatPhase() && !testCombatAiMode) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextClientBindingRefreshMillis) {
            return;
        }
        nextClientBindingRefreshMillis = now + 1_000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!isCombatTarget(player)) {
                clientBindingReadyPlayers.remove(uuid);
                continue;
            }
            if (!clientBindingReadyPlayers.contains(uuid)) {
                refreshClientBindingsForPlayer(player);
            }
        }
    }

    private void refreshClientBindingsForPlayer(Player player) {
        if (player == null || !player.isOnline() || !isCombatTarget(player)) {
            if (player != null) {
                clientBindingReadyPlayers.remove(player.getUniqueId());
            }
            return;
        }
        bindBossClient(player);
        bindEventEntitiesClient(player);
        syncEventMusic(player);
        clientBindingReadyPlayers.add(player.getUniqueId());
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
        if (entity.getType() == EntityType.SKELETON) {
            return isSkeletonMiniBoss(entity) ? CLIENT_VISUAL_ELITE_SKELETON : CLIENT_VISUAL_SKELETON;
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
        sendBossPhaseVisualUpdate(player, Bukkit.getEntity(bossUuid), bossStage);
    }

    private void sendBossPhaseVisualUpdate(Player player, Entity boss, BossStage stage) {
        if (player == null || boss == null || stage == null || bossUuid == null
                || bossBindingInstanceId.isBlank()) {
            return;
        }
        // Optional packet: older client builds ignore the unknown event type;
        // the server-side fight remains fully authoritative without it.
        sendClientPacket(player, "END_BOSS_PHASE", bossBindingInstanceId,
                0L, bossUuid.toString(), stage.name());
    }

    private void sendBossPhaseVisualUpdate(Entity boss, BossStage stage) {
        for (Player player : eventAudience()) {
            sendBossPhaseVisualUpdate(player, boss, stage);
        }
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
            recordPluginMessage();
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
                case "debug" -> List.of("packets", "objectives", "hazards", "perf", "ai");
                case "core" -> List.of("set", "setat", "info", "rebuild", "remove");
                case "arena" -> List.of("pos1", "pos2", "info", "clear", "border", "boundary");
            case "gate" -> List.of("pos1", "pos2", "setat", "info", "preview", "open", "restore");
                case "portalroom" -> List.of("set", "info");
                case "resources" -> List.of("status", "add", "reset");
                case "ritual" -> List.of("start", "cancel", "cleanup", "reset", "unlock");
                case "wave" -> List.of("spawn", "clear");
                case "boss" -> List.of("spawn", "official", "info", "damage", "phase", "kill", "spell");
                case "client" -> List.of("status", "bindboss", "clear");
                 case "test" -> List.of("run", "wave", "boss", "teleport", "visuals", "music");
                 default -> List.of();
            };
        }
        if (args.length == 3 && "wave".equalsIgnoreCase(args[0]) && "spawn".equalsIgnoreCase(args[1])) {
            return List.of("1", "2", "3", "4", "5", "final");
        }
        if (args.length == 3 && "test".equalsIgnoreCase(args[0]) && "wave".equalsIgnoreCase(args[1])) {
            return List.of("1", "2", "3", "4", "5", "final");
        }
        if (args.length == 3 && "test".equalsIgnoreCase(args[0]) && "teleport".equalsIgnoreCase(args[1])) {
            return List.of("wave", "boss");
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
        if (args.length == 3 && "test".equalsIgnoreCase(args[0]) && "visuals".equalsIgnoreCase(args[1])) {
            return List.of("mobs", "boss");
        }
        if (args.length == 4 && "test".equalsIgnoreCase(args[0]) && "visuals".equalsIgnoreCase(args[1])
                && "boss".equalsIgnoreCase(args[2])) {
            return List.of("awakening", "hunter", "distortion", "absorption", "catastrophe");
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
            return List.of("void_blast", "rift_projectile", "void_mark", "summon", "arena_inferno", "control_reverse");
        }
        return List.of();
    }

    private record RuntimeDiagnosticsSnapshot(long sampledAtMillis, long sampleIntervalMillis,
                                              String packetQualityMode, String lastPlayerPings,
                                              int averagePing, int maxPing, int onlinePlayers,
                                              int ownedLivingEntities, int temporaryDisplays,
                                              int activeProjectiles,
                                              double estimatedParticlePacketsPerSecond,
                                              double particleBatchesPerSecond,
                                              double pluginMessagesPerSecond,
                                              long gcCollections, long gcPauseMillis,
                                              double serverThreadCpuPercent, String tps,
                                              double mspt) {
        private static RuntimeDiagnosticsSnapshot empty() {
            return new RuntimeDiagnosticsSnapshot(0L, 0L, PACKET_QUALITY_FULL, "", 0, 0,
                    0, 0, 0, 0, 0.0D, 0.0D, 0.0D, 0L, 0L, 0.0D,
                    "unavailable", 0.0D);
        }
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
