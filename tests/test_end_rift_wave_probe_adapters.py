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
    restart_policy = disable[disable.index("        boolean preserveWave6ForRestart ="):
                             disable.index("        if (diagnosticsWatchdogTask != null)")]
    methods.append("private boolean preserveWaveForRestart() {\n" + restart_policy
                   + "return preserveWave6ForRestart || preserveWave7ForRestart;\n}")
    harness = r"""
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import me.copimine.endevent.domain.SandboxWaveSessionSnapshot;

public final class WaveProbeAdapterHarness {
    String eventId = "event", worldName = "world";
    EventPhase phase = EventPhase.READY_FOR_PLAYERS;
    long generation = 4L, creativeTestGeneration = 4L, phaseDeadlineMillis;
    UUID creativeTestPlayerUuid;
    int creativeTestStage = 3, creativeTestStageTicks, activeWave, waveSpawnGroupIndex,
            waveSpawnEntityOffset, sharedInitializations, initializedWave, saveCalls,
            registryGeneration, sessionCancellations, scopeCloses;
    boolean officialActive, officialCurrent, diagnosticsFailureInjection, cleanupSucceeds = true,
            testCombatAiMode, testWaveFrontVisualMode, testPortalVisualMode,
            testWave4ObeliskMode, testRitualCombatMode, testWaveInspectionMode, preserveRequested;
    Set<UUID> sandboxWaveRoster = Set.of(), officialRoster = Set.of(), initializedRoster = Set.of();
    List<Object> waveSpawnSchedule = List.of();
    Task creativeTestTask;
    Diagnostics diagnostics = new Diagnostics();
    Controller encounterController = new Controller();
    Object ritualSphereState = new Object();
    Set<UUID> ritualCasterUuids = Set.of(), ritualGuardUuids = Set.of();
    Set<Object> realitySplitBarrierCells = Set.of();
    Chambers realitySplitChamberController = new Chambers();
    Trials realitySplitTrialController = new Trials();
    static final Logger LOGGER = Logger.getLogger("detached-wave-probe");
    static { LOGGER.setLevel(Level.OFF); }
    Logger getLogger() { return LOGGER; }
    Location coreCombatAnchorLocation() { return new Location(Bukkit.world, true); }
    boolean isArenaLocation(Location location) { return location.inArena; }
    boolean isInsideOwnedPrisonerAnchor(Player player) { return false; }
    boolean isOfficialAttemptActive() { return officialActive; }
    boolean isOfficialCurrentAttempt() { return officialCurrent; }
    boolean isRitualCombatSandbox() { return testRitualCombatMode; }
    Set<UUID> authoritativeAttemptRoster() { return officialRoster; }
    int activeEventTaskCount() { return 0; }
    boolean cancelSessionTasks() {
        return cancelSessionTasks(false);
    }
    boolean cancelSessionTasks(boolean preserve) {
        sessionCancellations++;
        preserveRequested = preserve;
        cancelCreativeTestTask();
        if (!preserve) clearWaveObjectiveState();
        return cleanupSucceeds;
    }
    boolean closeEncounterResourceScope(String reason) { scopeCloses++; return cleanupSucceeds; }
    boolean resetEncounterTaskRegistry(long value) { registryGeneration = (int) value; return cleanupSucceeds; }
    void clearWaveEntities() { clearWaveEntities(true); }
    void clearWaveEntities(boolean includeRewards) { clearWaveObjectiveState(); }
    void clearWaveObjectiveState() {
        testWaveFrontVisualMode = false;
        testWaveInspectionMode = false;
        testPortalVisualMode = false;
        testWave4ObeliskMode = false;
        testRitualCombatMode = false;
        sandboxWaveRoster = Set.of();
    }
    void saveStateAsync() { saveCalls++; }
    boolean finalSealBarrierContext() { return false; }
    void spawnWaveForObjective(int wave, World world, Location core) {
        spawnWaveForObjectiveInternal(wave, world, core);
    }
    int eligibleWaveTargets() {
        return (int) Bukkit.players.stream().filter(this::isPhysicallyEligibleWaveParticipant)
                .filter(player -> sandboxWaveRoster.contains(player.getUniqueId())).count();
    }
    enum GameMode { SURVIVAL, CREATIVE, SPECTATOR }
    enum EventPhase { READY_FOR_PLAYERS, WAVE_6, WAVE_7 }
    static final class Chambers {
        boolean owns(long generation) { return true; }
        Assignment assignment() { return new Assignment(); }
        Set<Object> completedChambers() { return Set.of(); }
        Set<Object> openPassages() { return Set.of(); }
    }
    static final class Assignment { int chamberCount() { return 1; } }
    static final class Trials {
        boolean owns(long generation) { return true; }
        Map<Object, Object> snapshot() { return Map.of(); }
    }
    static final class RitualSphereEncounterSnapshot {
        static Map<String, String> encode(Object state) { return Map.of("ritual-wave", "6"); }
    }
    static final class RealitySplitChamberSnapshot {
        static Map<String, String> encodeDisposable(Object... args) { return Map.of("chambers", "1"); }
        static Map<String, String> encode(Object... args) { return Map.of("chambers", "1"); }
    }
    static final class RealitySplitTrialSnapshot {
        static Map<String, String> encode(Object... args) { return Map.of(); }
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
}
"""
    harness = harness.replace("    public static void main(String[] args)",
                              "\n".join(methods) + "\n    public static void main(String[] args)")
    output = tmp_path_factory.mktemp("wave-probe-adapters")
    java_file = output / "WaveProbeAdapterHarness.java"
    java_file.write_text(harness, encoding="utf-8")
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java_file), str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SandboxWaveSessionSnapshot.java")],
                              capture_output=True, text=True, check=False)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario,wave", [
    ("creative-only", 1), ("creative-only", 2), ("creative-only", 3),
    ("creative-with-survivor", 1), ("creative-with-survivor", 2), ("creative-with-survivor", 3),
    ("empty-inspection", 3), ("empty-ai", 3), ("strict-creative", 3),
    ("strict-survival", 3), ("official", 3), ("official-active", 3),
    ("strict-after-inspection", 3),
    ("creative-cleanup-failure", 3),
    ("empty-wave7", 7), ("empty-inspection-snapshot", 6),
    ("rostered-inspection-snapshot", 6), ("rostered-inspection-snapshot", 7),
    ("playable-snapshot", 6), ("empty-playable-snapshot", 6),
    ("inspection-shutdown", 6), ("inspection-shutdown", 7),
    ("playable-shutdown", 6), ("playable-shutdown", 7),
])
def test_wave_probe_session_boundaries(probe_harness, scenario, wave):
    result = subprocess.run(["java", "-cp", str(probe_harness), "WaveProbeAdapterHarness",
                             scenario, str(wave)], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr
