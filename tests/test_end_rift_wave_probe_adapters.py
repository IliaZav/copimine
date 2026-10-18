"""Run production wave-entry Java bodies against detached session boundaries."""

from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated production method: {signature}")


@pytest.fixture(scope="module")
def probe_harness(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    methods = [
        _declaration(source, "private void spawnWave(int wave, boolean test)"),
        _declaration(source, "private boolean spawnTestWave("),
        _declaration(source, "private boolean spawnWaveForObjectiveInternal("),
        _declaration(source, "private boolean isPhysicallyEligibleWaveParticipant(Player player)"),
        _declaration(source, "private boolean isPhysicallyEligibleWaveParticipant(Player player, boolean captureTestRoster)"),
        _declaration(source, "private void cancelCreativeTestTask()"),
        _declaration(source, "private Map<String, String> waveObjectiveProgressSnapshot()"),
        _declaration(source, "private Map<String, String> captureWave1CarrierCheckpoint()"),
        _declaration(source, "private Map<String, String> captureBlackFogRecoveryCheckpoint()"),
        _declaration(source, "private void captureBlackFogShutdownCheckpoint()"),
        _declaration(source, "private void restoreBlackFogRecoveryCheckpoint(EventPhase persistedPhase,"),
    ]
    if "private boolean spawnInspectionWave(" in source:
        methods.append(_declaration(source, "private boolean spawnInspectionWave("))
    # Keep the real initializer's admission and session-flag writes. Deep
    # objective/entity creation is the external boundary of this routing test.
    initializer = _declaration(source, "private boolean initializeWaveGameplay(")
    initializer = initializer[:initializer.index("        Location floorCore =")]
    methods.append(initializer + """
        sharedInitializations++;
        initializedRoster = Set.copyOf(roster);
        initializedWave = wave;
        return true;
    }
    """)
    disable = _declaration(source, "public void onDisable()")
    restart_policy = disable[disable.index("        boolean preserveWave1ForRestart ="):
                             disable.index("        if (diagnosticsWatchdogTask != null)")]
    methods.append("private boolean preserveWaveForRestart() {\n" + restart_policy
                   + "return preserveWave1ForRestart || preserveWave2ForRestart "
                     + "|| preserveWave6ForRestart || preserveWave7ForRestart;\n}")
    harness = r"""
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.BlackFogRecoverySnapshot;
import me.copimine.endevent.domain.SandboxWaveSessionSnapshot;
import me.copimine.endevent.domain.WaveTwoEntityRecoveryPolicy;
import me.copimine.endevent.domain.RiftCarrierPolicy;
import me.copimine.endevent.domain.RiftCarrierRecoverySnapshot;
import me.copimine.endevent.runtime.AttemptLifecycleController;
import me.copimine.endevent.runtime.WaveTwoAttemptLifecycleSnapshot;

public final class WaveProbeAdapterHarness {
    String eventId = "event", worldName = "world";
    EventPhase phase = EventPhase.READY_FOR_PLAYERS;
    static final String WAVE_TWO_CYCLES_PROGRESS_KEY = "wave2-hunt-cycles";
    static final String WAVE_TWO_GROUPS_SPAWNED_PROGRESS_KEY = "wave2-groups-spawned";
    static final String WAVE_TWO_GROUP_CLEARED_PROGRESS_KEY = "wave2-group-cleared";
    static final String WAVE_TWO_OUTSTANDING_UUIDS_PROGRESS_KEY = "wave2-outstanding-uuids";
    static final String WAVE_TWO_ATTEMPT_LIFECYCLE_PREFIX = "wave2-attempt-lifecycle.";
    static final long OFFLINE_RECONNECT_GRACE_MILLIS = 25_000L;
    Map<String, String> wave7RecoveryCheckpoint = Map.of();
    String recoveryReason = "";
    long generation = 4L, creativeTestGeneration = 4L, phaseDeadlineMillis, eventTickCounter;
    UUID creativeTestPlayerUuid;
    int creativeTestStage = 3, creativeTestStageTicks, activeWave, waveSpawnGroupIndex,
            waveSpawnEntityOffset, sharedInitializations, initializedWave, saveCalls,
            registryGeneration, sessionCancellations, scopeCloses, currentHuntCycles,
            waveTwoGroupsSpawned, currentCarrierCharges, ritualCasterDeathCount;
        boolean waveTwoGroupCleared;
    Set<UUID> waveTwoOutstandingEntityUuids = Set.of();
        Set<UUID> waveTwoCleanupEntityUuids = Set.of();
    boolean officialActive, officialCurrent, diagnosticsFailureInjection, cleanupSucceeds = true,
            testCombatAiMode, testWaveFrontVisualMode, testPortalVisualMode,
            testWave4ObeliskMode, testRitualCombatMode, testWaveInspectionMode, preserveRequested,
            officialAttempt;
    int currentFogCycle, currentFogZoneCount, currentFogZoneSide, currentFogZoneSetupAttempts;
    long blackFogPhaseDeadlineMillis, currentFogZoneSetupRetryAtMillis;
    BlackFogPhase blackFogPhase = BlackFogPhase.COMBAT;
    Map<String, String> blackFogRecoveryCheckpoint = Map.of();
    Map<String, String> wave1RecoveryCheckpoint = Map.of(), ritualSphereRecoveryCheckpoint = Map.of();
    RiftCarrierPolicy.State currentCarrierState;
    UUID currentCarrierInventoryToken;
    boolean wave1CarrierCheckpointRestored;
    Set<UUID> sandboxWaveRoster = Set.of(), officialRoster = Set.of(), initializedRoster = Set.of();
    Set<UUID> officialRewardRoster = new LinkedHashSet<>();
    Map<UUID, Long> offlineRosterGraceUntilMillis = new HashMap<>();
    List<Object> waveSpawnSchedule = List.of();
    Task creativeTestTask;
    Diagnostics diagnostics = new Diagnostics();
    Controller encounterController = new Controller();
    Object ritualSphereState = new Object();
    Set<UUID> ritualCasterUuids = Set.of(), ritualGuardUuids = Set.of();
    Set<Object> realitySplitBarrierCells = Set.of();
    Chambers realitySplitChamberController = new Chambers();
    Trials realitySplitTrialController = new Trials();
    AttemptLifecycleController attemptLifecycle = new AttemptLifecycleController();
    static final Logger LOGGER = Logger.getLogger("detached-wave-probe");
    static { LOGGER.setLevel(Level.OFF); }
    Logger getLogger() { return LOGGER; }
    Location coreCombatAnchorLocation() { return new Location(Bukkit.world, true); }
    boolean isArenaLocation(Location location) { return location.inArena; }
    boolean isInsideOwnedPrisonerAnchor(Player player) { return false; }
    boolean isOfficialAttemptActive() { return officialActive; }
    boolean isOfficialCurrentAttempt() { return officialCurrent; }
    boolean isOfficialAttempt() { return officialAttempt; }
    boolean isRitualCombatSandbox() { return testRitualCombatMode; }
    Set<UUID> authoritativeAttemptRoster() { return officialRoster; }
    int activeEventTaskCount() { return 0; }
    boolean cancelSessionTasks() {
        return cancelSessionTasks(false);
    }
    boolean cancelSessionTasks(boolean preserve) {
        // Model the new durable roster checkpoint as the first cleanup step:
        // a failed checkpoint must not cancel the creative run or mutate state.
        if (!cleanupSucceeds) return false;
        sessionCancellations++;
        preserveRequested = preserve;
        cancelCreativeTestTask();
        if (!preserve) clearWaveObjectiveState();
        return true;
    }
    boolean closeEncounterResourceScope(String reason) { scopeCloses++; return cleanupSucceeds; }
    boolean resetEncounterTaskRegistry(long value) { registryGeneration = (int) value; return cleanupSucceeds; }
    boolean clearWaveEntities() { return clearWaveEntities(true); }
    boolean clearWaveEntities(boolean includeRewards) { return clearWaveObjectiveState(); }
    boolean clearWaveObjectiveState() {
        testWaveFrontVisualMode = false;
        testWaveInspectionMode = false;
        testPortalVisualMode = false;
        testWave4ObeliskMode = false;
        testRitualCombatMode = false;
        sandboxWaveRoster = Set.of();
        return true;
    }
    void saveStateAsync() { saveCalls++; }
    boolean finalSealBarrierContext() { return false; }
    void spawnWaveForObjective(int wave, World world, Location core) {
        spawnWaveForObjectiveInternal(wave, world, core);
    }
        boolean preflightWave7StartBeforeWorldMutation(World world, Location core,
                Set<UUID> roster, boolean sandbox) {
            // This detached routing harness stops before Wave 7 world mutation;
            // the dedicated Wave 7 preflight tests exercise the production adapter.
            return world != null && core != null && roster != null && !roster.isEmpty();
        }
    int eligibleWaveTargets() {
        return (int) Bukkit.players.stream().filter(this::isPhysicallyEligibleWaveParticipant)
                .filter(player -> sandboxWaveRoster.contains(player.getUniqueId())).count();
    }
    enum GameMode { SURVIVAL, CREATIVE, SPECTATOR }
    enum BlackFogPhase { COMBAT, SAFE, FOG }
    static final class Chambers {
        boolean owns(long generation) { return true; }
        Assignment assignment() { return new Assignment(); }
        Set<Object> completedChambers() { return Set.of(); }
        Set<Object> openPassages() { return Set.of(); }
        Set<Object> openingPassages() { return Set.of(); }
    }
    static final class Assignment { int chamberCount() { return 1; } }
    static final class Trials {
        boolean owns(long generation) { return true; }
        boolean ownsNamed(UUID eventId, long generation) { return false; }
        Map<Object, Object> snapshot() { return Map.of(); }
        Object namedSnapshot() { throw new AssertionError("unnamed Wave 7 probe must not read a named snapshot"); }
    }
    static final class RitualSphereEncounterSnapshot {
        static Map<String, String> encode(Object state) { return Map.of("ritual-wave", "6"); }
            static Map<String, String> encode(Object state, int casterDeaths) {
                return Map.of("ritual-wave", "6", "caster-deaths", Integer.toString(casterDeaths));
            }
    }
    static final class RealitySplitChamberSnapshot {
        static Map<String, String> encodeDisposable(Object... args) { return Map.of("chambers", "1"); }
        static Map<String, String> encode(Object... args) { return Map.of("chambers", "1"); }
    }
    static final class RealitySplitTrialSnapshot {
        static Map<String, String> encode(Object... args) { return Map.of(); }
        static Map<String, String> encodeNamed(Object... args) { return Map.of(); }
    }
    static final class World {
        String getName() { return "world"; }
    }
    static final class Location {
        final World world;
        final boolean inArena;
        Location(World world, boolean inArena) { this.world = world; this.inArena = inArena; }
        World getWorld() { return world; }
    }
    static final class Player {
        final UUID id = UUID.randomUUID();
        final GameMode mode;
        Player(GameMode mode) { this.mode = mode; }
        UUID getUniqueId() { return id; }
        boolean isOnline() { return true; }
        boolean isDead() { return false; }
        double getHealth() { return 20.0; }
        GameMode getGameMode() { return mode; }
        World getWorld() { return Bukkit.world; }
        Location getLocation() { return new Location(Bukkit.world, true); }
    }
    static final class Bukkit {
        static final World world = new World();
        static final List<Player> players = new ArrayList<>();
        static World getWorld(String name) { return world; }
        static List<Player> getOnlinePlayers() { return players; }
    }
    static final class Task {
        boolean cancelled;
        void cancel() { cancelled = true; }
    }
    static final class Diagnostics {
        void recordWaveTransitionStarted(Object... args) { }
        void recordWaveTransitionFailed(Object... args) { }
    }
    static final class Controller { void restore(Object... args) { } }
    static final class EndRiftObjective {
        static final int REQUIRED_FOG_CYCLES = 3;
        static boolean isNumberedWave(int wave) { return wave >= 1 && wave <= 7; }
    }

    public static void main(String[] args) {
        WaveProbeAdapterHarness probe = new WaveProbeAdapterHarness();
        Player operator = new Player(GameMode.CREATIVE);
        Player survivor = new Player(GameMode.SURVIVAL);
        int wave = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        switch (args[0]) {
            case "creative-only", "creative-with-survivor" -> {
                Bukkit.players.add(operator);
                if (args[0].equals("creative-with-survivor")) Bukkit.players.add(survivor);
                Task task = new Task();
                probe.creativeTestTask = task;
                probe.creativeTestPlayerUuid = operator.getUniqueId();
                probe.testCombatAiMode = true;
                probe.spawnWave(wave, true);
                check(probe.sharedInitializations == 1 && probe.initializedWave == wave,
                        "creative stage must reach shared gameplay initialization");
                check(probe.creativeTestTask == task && !task.cancelled,
                        "wave inspection must preserve the creative run's scheduled task");
                check(probe.generation == 4 && probe.creativeTestGeneration == 4
                        && probe.creativeTestStage == 3 && probe.sessionCancellations == 0,
                        "creative stages must preserve their generation and progress guard");
                check(probe.initializedRoster.contains(operator.getUniqueId())
                        && probe.eligibleWaveTargets() == Bukkit.players.size(),
                        "creative operator must remain an eligible inspection target");
            }
            case "empty-inspection", "empty-ai" -> {
                probe.testCombatAiMode = args[0].equals("empty-ai");
                probe.spawnWave(wave, true);
                check(probe.sharedInitializations == 1 && probe.initializedRoster.isEmpty(),
                        "console inspection must initialize without a Survival roster");
                check(probe.generation == 5 && probe.registryGeneration == 5,
                        "standalone inspection needs a fresh disposable generation");
            }
            case "empty-wave7" -> {
                probe.spawnWave(7, true);
                check(probe.sharedInitializations == 0 && probe.generation == 4
                        && probe.sessionCancellations == 0,
                        "Wave 7 inspection needs a real room roster before session mutation");
            }
            case "empty-inspection-snapshot", "rostered-inspection-snapshot" -> {
                probe.activeWave = wave;
                probe.testWaveFrontVisualMode = true;
                probe.testWaveInspectionMode = true;
                if (args[0].equals("rostered-inspection-snapshot")) {
                    probe.sandboxWaveRoster = Set.of(operator.getUniqueId());
                }
                check(probe.waveObjectiveProgressSnapshot().isEmpty(),
                        "inspection scene must never persist a playable sandbox marker or roster");
            }
            case "wave2-snapshot" -> {
                probe.phase = EventPhase.WAVE_2;
                probe.currentHuntCycles = 1;
                probe.waveTwoGroupsSpawned = 2;
                probe.waveTwoGroupCleared = false;
                UUID rosteredPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
                probe.officialRewardRoster.add(rosteredPlayer);
                probe.attemptLifecycle.begin(probe.generation, probe.officialRewardRoster);
                UUID outstanding = UUID.fromString("00000000-0000-0000-0000-000000000002");
                probe.waveTwoOutstandingEntityUuids = Set.of(outstanding);
                Map<String, String> saved = probe.waveObjectiveProgressSnapshot();
                check("1".equals(saved.get(WAVE_TWO_CYCLES_PROGRESS_KEY))
                                && "2".equals(saved.get(WAVE_TWO_GROUPS_SPAWNED_PROGRESS_KEY))
                                && "false".equals(saved.get(WAVE_TWO_GROUP_CLEARED_PROGRESS_KEY))
                                && outstanding.toString().equals(saved.get(WAVE_TWO_OUTSTANDING_UUIDS_PROGRESS_KEY)),
                        "active Wave 2 checkpoint must encode stage, group and durable outstanding UUIDs");
                String lifecyclePrefix = WAVE_TWO_ATTEMPT_LIFECYCLE_PREFIX;
                String ownerPrefix = lifecyclePrefix + rosteredPlayer + ".";
                check("1".equals(saved.get(lifecyclePrefix + "version"))
                                && "event".equals(saved.get(lifecyclePrefix + "event"))
                                && Long.toString(probe.generation).equals(saved.get(lifecyclePrefix + "generation"))
                                && rosteredPlayer.toString().equals(saved.get(lifecyclePrefix + "roster"))
                                && "true".equals(saved.get(ownerPrefix + "active"))
                                && "true".equals(saved.get(ownerPrefix + "alive"))
                                && "0".equals(saved.get(ownerPrefix + "offline-deadline-millis")),
                        "active Wave 2 checkpoint must include the controller's registered participant state");
            }
            case "wave5-snapshot" -> {
                probe.phase = EventPhase.WAVE_5;
                probe.activeWave = 5;
                probe.currentFogCycle = 1;
                probe.blackFogPhase = args[2].equals("TELEGRAPH")
                        ? BlackFogPhase.COMBAT : BlackFogPhase.valueOf(args[2]);
                probe.blackFogPhaseDeadlineMillis = 123_456L;
                probe.currentFogZoneCount = 2;
                probe.currentFogZoneSide = 2;
                probe.currentFogZoneSetupAttempts = 1;
                probe.currentFogZoneSetupRetryAtMillis = 124_000L;
                Map<String, String> saved = probe.waveObjectiveProgressSnapshot();
                check("1".equals(saved.get("wave5-fog.version"))
                                && "event".equals(saved.get("wave5-fog.event"))
                                && Long.toString(probe.generation).equals(saved.get("wave5-fog.generation"))
                                && "1".equals(saved.get("wave5-fog.cycle"))
                                && args[2].equals(saved.get("wave5-fog.stage"))
                                && "123456".equals(saved.get("wave5-fog.deadline-millis"))
                                && "2".equals(saved.get("wave5-fog.zone-count"))
                                && "2".equals(saved.get("wave5-fog.zone-side"))
                                && "1".equals(saved.get("wave5-fog.setup-attempts"))
                                && "124000".equals(saved.get("wave5-fog.retry-at-millis")),
                        "Wave 5 must checkpoint its exact cycle, stage, deadline and zone retry state");
            }
            case "wave5-shutdown-capture" -> {
                probe.phase = EventPhase.WAVE_5;
                probe.activeWave = 5;
                probe.currentFogCycle = 1;
                probe.blackFogPhase = args[2].equals("TELEGRAPH")
                        ? BlackFogPhase.COMBAT : BlackFogPhase.valueOf(args[2]);
                probe.blackFogPhaseDeadlineMillis = 123_456L;
                probe.currentFogZoneCount = 2;
                probe.currentFogZoneSide = 2;
                probe.currentFogZoneSetupAttempts = 1;
                probe.currentFogZoneSetupRetryAtMillis = 124_000L;
                probe.captureBlackFogShutdownCheckpoint();
                check(args[2].equals(probe.blackFogRecoveryCheckpoint.get("wave5-fog.stage")),
                        "shutdown must capture the current Wave 5 stage before cleanup resets it");
                check("1".equals(probe.blackFogRecoveryCheckpoint.get("wave5-fog.cycle"))
                                && "123456".equals(probe.blackFogRecoveryCheckpoint.get("wave5-fog.deadline-millis")),
                        "shutdown checkpoint must preserve the active cycle and absolute deadline");
            }
            case "wave5-restart" -> {
                BlackFogRecoverySnapshot.Stage stage = BlackFogRecoverySnapshot.Stage.valueOf(args[2]);
                Map<String, String> saved = new BlackFogRecoverySnapshot.Data(
                        "event", probe.generation, 1, stage, 123_456L, 2, 2, 1, 124_000L).encode();
                probe.restoreBlackFogRecoveryCheckpoint(EventPhase.WAVE_5, saved);
                check(probe.phase == EventPhase.RECOVERY_REQUIRED
                                && "WAVE5_RESTART_REQUIRES_ADMIN_RECOVERY".equals(probe.recoveryReason),
                        "restart must retain evidence but fence transient Wave 5 combat for admin recovery");
                check(saved.equals(probe.blackFogRecoveryCheckpoint),
                        "restart must preserve the exact saved Wave 5 phase checkpoint");
            }
            case "wave5-invalid-restart" -> {
                Map<String, String> saved = Map.of("wave5-fog.stage", "FOG");
                probe.restoreBlackFogRecoveryCheckpoint(EventPhase.WAVE_5, saved);
                check(probe.phase == EventPhase.RECOVERY_REQUIRED
                                && "INVALID_OR_MISSING_WAVE5_RESTART_CHECKPOINT".equals(probe.recoveryReason),
                        "missing Wave 5 checkpoint fields must be fenced in recovery");
                check(saved.equals(probe.blackFogRecoveryCheckpoint),
                        "invalid Wave 5 snapshot bytes must remain available for diagnosis");
            }
            case "playable-snapshot" -> {
                probe.activeWave = 6;
                probe.testWaveFrontVisualMode = true;
                probe.testRitualCombatMode = true;
                probe.sandboxWaveRoster = Set.of(survivor.getUniqueId());
                Map<String, String> saved = probe.waveObjectiveProgressSnapshot();
                check("6".equals(saved.get("test-wave"))
                        && survivor.getUniqueId().toString().equals(saved.get("test-wave-roster"))
                        && "combat".equals(saved.get("test-wave-mode")),
                        "playable sandbox must retain its durable frozen roster and mode");
            }
            case "empty-playable-snapshot" -> {
                probe.activeWave = 6;
                probe.testWaveFrontVisualMode = true;
                try {
                    probe.waveObjectiveProgressSnapshot();
                    throw new AssertionError("empty playable snapshot must remain invalid");
                } catch (IllegalArgumentException expected) { }
            }
            case "inspection-shutdown", "playable-shutdown" -> {
                probe.activeWave = wave;
                probe.testWaveFrontVisualMode = true;
                probe.testWaveInspectionMode = args[0].equals("inspection-shutdown");
                boolean preserved = probe.preserveWaveForRestart();
                check(preserved == args[0].equals("playable-shutdown")
                        && probe.preserveRequested == preserved,
                        "only playable Wave 6/7 sessions may preserve combat state for restart");
            }
            case "official-wave2-shutdown" -> {
                probe.activeWave = 2;
                probe.phase = EventPhase.WAVE_2;
                probe.officialAttempt = true;
                boolean preserved = probe.preserveWaveForRestart();
                check(preserved && probe.preserveRequested,
                        "official Wave 2 shutdown must preserve its roster for bootstrap recovery");
            }
            case "test-wave2-shutdown" -> {
                probe.activeWave = 2;
                probe.phase = EventPhase.WAVE_2;
                probe.testWaveFrontVisualMode = true;
                boolean preserved = probe.preserveWaveForRestart();
                check(!preserved && !probe.preserveRequested,
                        "disposable Wave 2 test sessions must not be preserved for restart");
            }
            case "strict-creative" -> {
                Bukkit.players.add(operator);
                check(!probe.spawnTestWave(wave, false) && probe.sharedInitializations == 0
                        && probe.generation == 4 && probe.sessionCancellations == 0,
                        "playable sandbox must still reject a Creative-only roster before mutation");
            }
            case "strict-after-inspection" -> {
                Bukkit.players.add(operator);
                probe.testWaveFrontVisualMode = true;
                probe.testWaveInspectionMode = true;
                probe.sandboxWaveRoster = Set.of(operator.getUniqueId());
                check(!probe.spawnTestWave(wave, false) && probe.sharedInitializations == 0
                        && probe.generation == 4 && probe.sessionCancellations == 0,
                        "previous inspection eligibility must not enroll Creative players into a playable sandbox");
            }
            case "strict-survival" -> {
                Bukkit.players.add(operator);
                Bukkit.players.add(survivor);
                check(probe.spawnTestWave(wave, false), "Survival sandbox should start");
                check(probe.sharedInitializations == 1 && probe.generation == 5
                        && probe.initializedRoster.equals(Set.of(survivor.getUniqueId()))
                        && !probe.testWaveInspectionMode && probe.eligibleWaveTargets() == 1,
                        "playable sandbox keeps strict frozen roster and shared initializer");
            }
            case "official" -> {
                Bukkit.players.add(survivor);
                probe.officialCurrent = true;
                probe.officialRoster = Set.of(survivor.getUniqueId());
                probe.spawnWave(wave, false);
                check(probe.sharedInitializations == 1 && probe.generation == 4
                        && probe.initializedRoster.equals(probe.officialRoster)
                        && !probe.testWaveFrontVisualMode && !probe.testWaveInspectionMode,
                        "official adapter keeps its authoritative roster and shared initializer");
            }
            case "official-active" -> {
                Bukkit.players.add(survivor);
                probe.officialActive = true;
                probe.spawnWave(wave, true);
                check(probe.sharedInitializations == 0 && probe.generation == 4
                        && probe.sessionCancellations == 0,
                        "inspection must refuse replacement of an official attempt");
            }
            case "creative-cleanup-failure" -> {
                Bukkit.players.add(operator);
                Bukkit.players.add(survivor);
                Task task = new Task();
                probe.creativeTestTask = task;
                probe.creativeTestPlayerUuid = operator.getUniqueId();
                probe.cleanupSucceeds = false;
                probe.spawnWave(wave, true);
                check(probe.sharedInitializations == 0 && probe.generation == 4
                        && probe.creativeTestTask == task && !task.cancelled,
                        "failed inspection cleanup must preserve the run and block replacement work");
            }
            default -> throw new AssertionError("unknown scenario");
        }
        System.out.println(args[0] + " wave=" + wave + " OK");
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw.trim()); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
"""
    harness = harness.replace("    public static void main(String[] args)",
                              "\n".join(methods) + "\n    public static void main(String[] args)")
    output = tmp_path_factory.mktemp("wave-probe-adapters")
    java_file = output / "WaveProbeAdapterHarness.java"
    java_file.write_text(harness, encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    runtime = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime"
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java_file), str(domain / "EventPhase.java"),
                               str(domain / "BlackFogTimingPolicy.java"),
                               str(domain / "EndRiftObjective.java"),
                               str(domain / "BlackFogRecoverySnapshot.java"),
                               str(domain / "RiftCarrierPolicy.java"),
                               str(domain / "RiftCarrierRecoverySnapshot.java"),
                               str(domain / "SandboxWaveSessionSnapshot.java"),
                               str(domain / "PressureBudgetController.java"),
                               str(domain / "WaveMechanicsPolicy.java"),
                               str(domain / "WaveTwoStagePolicy.java"),
                               str(domain / "WaveTwoEntityRecoveryPolicy.java"),
                               str(runtime / "AttemptLifecycleController.java"),
                               str(runtime / "WaveTwoAttemptLifecycleSnapshot.java")],
                              capture_output=True, text=True, check=False)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario,wave", [
    ("creative-only", 1), ("creative-only", 2), ("creative-only", 3),
    ("creative-with-survivor", 1), ("creative-with-survivor", 2), ("creative-with-survivor", 3),
    ("empty-inspection", 3), ("empty-ai", 3), ("strict-creative", 3),
    ("wave2-snapshot", 2),
    ("strict-survival", 3), ("official", 3), ("official-active", 3),
    ("strict-after-inspection", 3),
    ("creative-cleanup-failure", 3),
    ("empty-wave7", 7), ("empty-inspection-snapshot", 6),
    ("rostered-inspection-snapshot", 6), ("rostered-inspection-snapshot", 7),
    ("playable-snapshot", 6), ("empty-playable-snapshot", 6),
    ("inspection-shutdown", 6), ("inspection-shutdown", 7),
    ("playable-shutdown", 6), ("playable-shutdown", 7),
    ("official-wave2-shutdown", 2), ("test-wave2-shutdown", 2),
])
def test_wave_probe_session_boundaries(probe_harness, scenario, wave):
    result = subprocess.run(["java", "-cp", str(probe_harness), "WaveProbeAdapterHarness",
                             scenario, str(wave)], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("stage", ["TELEGRAPH", "SAFE", "FOG"])
def test_wave5_checkpoint_keeps_the_exact_stage_across_shutdown(probe_harness, stage):
    result = subprocess.run(["java", "-cp", str(probe_harness), "WaveProbeAdapterHarness",
                             "wave5-snapshot", "5", stage], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("stage", ["TELEGRAPH", "SAFE", "FOG"])
def test_wave5_shutdown_captures_current_stage_before_cleanup(probe_harness, stage):
    result = subprocess.run(["java", "-cp", str(probe_harness), "WaveProbeAdapterHarness",
                             "wave5-shutdown-capture", "5", stage], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("stage", ["TELEGRAPH", "SAFE", "FOG"])
def test_wave5_restart_restores_checkpoint_as_admin_recovery(probe_harness, stage):
    result = subprocess.run(["java", "-cp", str(probe_harness), "WaveProbeAdapterHarness",
                             "wave5-restart", "5", stage], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave5_restart_preserves_invalid_checkpoint_and_requires_recovery(probe_harness):
    result = subprocess.run(["java", "-cp", str(probe_harness), "WaveProbeAdapterHarness",
                             "wave5-invalid-restart", "5"], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr
