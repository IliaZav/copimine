"""Execute the production Wave 5 cycle tick and zone-journal restore adapter."""

from pathlib import Path
import subprocess

import pytest

from tests.test_wave_navigation_adapter import extract


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    output = tmp_path_factory.mktemp("wave5-black-fog")
    source = SOURCE.read_text("utf-8")
    methods = "\n".join(extract(source, signature) for signature in [
        "void tickBlackFogObjective(long now)",
        "void replenishWaveFiveCombatIfNeeded(long now)",
        "void sendCurrentBlackFogPacket(Player player, boolean active, Location core)",
        "boolean restoreCurrentSafeZoneBlocks()",
        "void restoreBlackFogBlock(Block block, String originalBlockData)",
    ])
    java = output / "BlackFogAdapterProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.copimine.endevent.domain.BlackFogTimingPolicy;
import me.copimine.endevent.domain.BlackFogSafeZonePolicy;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.Wave5PreFogRefillPolicy;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

public final class BlackFogAdapterProbe {
    enum EventPhase { WAVE_5, RECOVERY_REQUIRED }
    enum BlackFogPhase { COMBAT, SAFE, FOG }
    enum Sound { BLOCK_BEACON_POWER_SELECT, BLOCK_AMETHYST_BLOCK_CHIME }
    enum PotionEffectType { REGENERATION }
    enum Material { EMERALD_BLOCK, BARRIER, STONE }
    static final class BlockData {
        final String value;
        BlockData(String value) { this.value = value; }
        String getAsString() { return value; }
    }
    static final class PotionEffect {
        PotionEffect(PotionEffectType type, int ticks, int amp, boolean ambient, boolean particles, boolean icon) { }
    }
    static final class World {
        final Map<String, Block> blocks = new HashMap<>();
        Block getBlockAt(int x, int y, int z) { return blocks.computeIfAbsent(x+":"+y+":"+z, k -> new Block()); }
    }
    static final class Location {
        World world = new World();
        World getWorld() { return world; }
    }
    static final class Block {
        Material type = Material.EMERALD_BLOCK;
        BlockData data = new BlockData("minecraft:emerald_block");
        Material getType() { return type; }
        BlockData getBlockData() { return data; }
        void setBlockData(BlockData value, boolean physics) {
            Bukkit.restoreBlockAttempts++;
            if (Bukkit.restoreThrows) throw new IllegalStateException("injected block restore failure");
            data = value;
            type = switch (value.value) {
                case "minecraft:emerald_block" -> Material.EMERALD_BLOCK;
                case "minecraft:barrier" -> Material.BARRIER;
                default -> Material.STONE;
            };
        }
    }
    static final class Player {
        void addPotionEffect(PotionEffect effect) { }
    }
    static final class Bukkit {
        static World world;
        static boolean restoreThrows;
        static int restoreBlockAttempts;
        static World getWorld(String name) { return world; }
        static BlockData createBlockData(String value) {
            if ("invalid:data".equals(value)) throw new IllegalArgumentException("invalid block data");
            return new BlockData(value);
        }
    }
    static final class HazardPlanner {
        record Point(int x, int z) { }
    }
    static final class HazardMutationJournal {
        enum Status { EMPTY, PREPARED, APPLIED, RESTORED }
        record Entry(int x, int floorY, int z, String floorOriginal, String webOriginal, boolean emerald, boolean barrier) {
            boolean isEmeraldBarrierMutation() { return emerald; }
            boolean isBarrierMutation() { return barrier; }
        }
        record Snapshot(boolean valid, String eventId, long generation, Status status, String world, List<Entry> entries) { }
        Snapshot snapshot = new Snapshot(false, "", 0L, Status.EMPTY, "", List.of());
        boolean markRestored = true;
            int markRestoredCalls;
        Snapshot load() { return snapshot; }
            boolean markRestored() { markRestoredCalls++; return markRestored; }
        String path() { return "fog-journal.yml"; }
    }

    EventPhase phase = EventPhase.WAVE_5;
    BlackFogPhase blackFogPhase = BlackFogPhase.COMBAT;
    String eventId = "event", recoveryReason = "";
    long generation = 4L, blackFogPhaseDeadlineMillis, currentFogZoneSetupRetryAtMillis;
    int activeWave = 5, currentFogCycle, currentFogZoneCount, currentFogZoneSide, currentFogZoneSetupAttempts;
    int waveSpawnGroupIndex = 1, waveSpawnEntityOffset, liveWaveMobCount, refillPacks;
    long wave5PreFogRefillAtMillis;
    boolean testWaveFrontVisualMode;
    String worldName = "world";
    List<WaveMechanicsPolicy.WaveCounts> waveSpawnSchedule = List.of(
            new WaveMechanicsPolicy.WaveCounts(2, 1, 0, 0, 0));
    boolean zoneSetupSucceeds = true, frozen, restoreThrows;
    int setupCalls, fogEffects, packetRefreshes, cleanupCalls;
    String sentPacketSubject = "", sentPacketStatus = "", sentPacketVisual = "";
    long sentPacketDuration;
    float sentPacketIntensity;
    Location core = new Location();
    HazardMutationJournal hazardJournal = new HazardMutationJournal();
    Map<UUID, Long> currentFogLastPacketAt = new HashMap<>();
    Map<HazardPlanner.Point, String> currentTemporaryBlockOriginals = new LinkedHashMap<>();
    Set<HazardPlanner.Point> currentSafeZoneCells = new LinkedHashSet<>();
    Map<Integer, HazardPlanner.Point> currentSafeZoneCenters = new LinkedHashMap<>();
    Set<UUID> currentSafeZoneVisuals = new LinkedHashSet<>();

    Location coreCombatAnchorLocation() { return core; }
    String worldVfxDimension(Location location) { return "the_end"; }
    int waveScalePlayers() { return 4; }
    boolean spawnCurrentSafeZoneVisuals(World world, Location location) {
        setupCalls++;
        if (!zoneSetupSucceeds) return false;
        currentSafeZoneCells.add(new HazardPlanner.Point(1, 1));
        currentSafeZoneCenters.put(0, new HazardPlanner.Point(1, 1));
        return true;
    }
    boolean clearCurrentSafeZoneVisuals() {
        cleanupCalls++;
        boolean restored = restoreCurrentSafeZoneBlocks();
        currentSafeZoneVisuals.clear();
        currentSafeZoneCells.clear();
        currentSafeZoneCenters.clear();
        return restored;
    }
    void clearCurrentBlackFogPackets() { currentFogLastPacketAt.clear(); }
    void clearDragonBreathCloudsForFog() { }
    void freezeWaveMobs(boolean value) { frozen = value; }
    boolean isOfficialCurrentAttempt() { return true; }
    void forcePhase(EventPhase next, String reason) { phase = next; recoveryReason = reason; }
    boolean clearWaveEntities() { return true; }
    void saveStateAsync() { }
    void renderWaveMilestone(int wave, Location location, String text, Sound sound) { }
    void applyBlackFogEffects(long now, Location location) { fogEffects++; }
    void refreshCurrentBlackFogPackets(long now, Location location) { packetRefreshes++; }
    void sendClientPacket(Player player, String type, String instanceId, long durationMillis,
                          String subjectId, String visualId, int health, int maxHealth,
                          float progress, String clearPolicy, String status) {
        sentPacketSubject = subjectId;
        sentPacketStatus = status;
        sentPacketVisual = visualId;
        sentPacketDuration = durationMillis;
        sentPacketIntensity = progress;
    }
    List<Player> activeWaveParticipants() { return List.of(); }
    boolean isCurrentSafeZonePlayer(Player player) { return false; }
    void restoreBlock(Block block, String original) {
        if (restoreThrows) throw new IllegalStateException("injected block restore failure");
        block.type = Material.STONE;
    }
    Logger getLogger() { return Logger.getAnonymousLogger(); }
    int countLiveWaveEntitiesForWave(int wave) { return wave == 5 ? liveWaveMobCount : 0; }
    void spawnWaveGroup(World world, Location location, int wave, boolean guardian,
                        boolean test, WaveMechanicsPolicy.WaveCounts group, int offset) {
        check(wave == 5 && !guardian, "pre-fog refill uses the Wave 5 normal mob roster");
        refillPacks++;
        liveWaveMobCount += group.total();
    }
    void markBlackFogRecoveryRequired(String reason) {
        recoveryReason = reason;
        phase = EventPhase.RECOVERY_REQUIRED;
    }

''' + methods + r'''
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    void advanceToFog() {
        zoneSetupSucceeds = true;
        blackFogPhase = BlackFogPhase.SAFE;
        blackFogPhaseDeadlineMillis = 100L;
        currentSafeZoneCells.add(new HazardPlanner.Point(1, 1));
        currentSafeZoneCenters.put(0, new HazardPlanner.Point(1, 1));
        tickBlackFogObjective(100L);
    }
    public static void main(String[] args) {
        var p = new BlackFogAdapterProbe();
        switch (args[0]) {
            case "wire-cycle" -> {
                p.sendCurrentBlackFogPacket(new Player(), true, null);
                check("the_end|wave5|1".equals(p.sentPacketSubject),
                        "first internal zero-based fog cycle must be sent as protocol cycle 1");
                check("ACTIVE".equals(p.sentPacketStatus) && "WAVE5_FOG_V1".equals(p.sentPacketVisual)
                                && p.sentPacketDuration == 8_000L && p.sentPacketIntensity == .88F,
                        "active fog packet must preserve its approved visual fields");
                p.currentFogCycle = 2;
                p.sendCurrentBlackFogPacket(new Player(), true, null);
                check("the_end|wave5|3".equals(p.sentPacketSubject),
                        "last internal zero-based fog cycle must be sent as protocol cycle 3");
                p.sendCurrentBlackFogPacket(new Player(), false, null);
                check(p.sentPacketSubject.isEmpty() && "CLEAR".equals(p.sentPacketStatus)
                                && p.sentPacketDuration == 0L && p.sentPacketIntensity == 0.0F,
                        "clear fog packet must not carry an active cycle");
            }
            case "pre-fog-refill" -> {
                Bukkit.world = p.core.world;
                p.blackFogPhaseDeadlineMillis = 20_000L;
                p.tickBlackFogObjective(10_000L);
                check(p.refillPacks == 1 && p.liveWaveMobCount == 3,
                        "an empty pre-first-fog combat phase starts one bounded Wave 5 refill pack");
                check(p.wave5PreFogRefillAtMillis == 15_000L,
                        "a successful refill stores its retry deadline");
                p.liveWaveMobCount = 0;
                p.tickBlackFogObjective(14_999L);
                check(p.refillPacks == 1, "refill does not repeat before its retry deadline");
                p.tickBlackFogObjective(15_000L);
                check(p.refillPacks == 2, "a second full clear before the first fog receives another pack");
                p.liveWaveMobCount = 0;
                p.currentFogCycle = 1;
                p.tickBlackFogObjective(15_001L);
                check(p.refillPacks == 2, "refills stop after the first fog cycle begins");
            }
            case "three-cycles" -> {
                long now = 40_000L;
                p.blackFogPhaseDeadlineMillis = now;
                for (int cycle = 0; cycle < 3; cycle++) {
                    p.tickBlackFogObjective(now);
                    check(p.blackFogPhase == BlackFogPhase.SAFE, "due combat must reveal a safe area");
                    int expectedSide = cycle == 0 ? 2 : 1;
                    check(p.currentFogZoneSide == expectedSide,
                            "four-player safe zones must shrink by the player-count policy");
                    long safeDeadline = now + 8_000L;
                    p.tickBlackFogObjective(safeDeadline);
                    check(p.blackFogPhase == BlackFogPhase.FOG && p.frozen,
                            "safe-zone deadline must enter fog and hold mob AI");
                    long fogDeadline = safeDeadline + 3_000L;
                    p.tickBlackFogObjective(fogDeadline);
                    check(!p.frozen, "fog end must release its AI hold");
                    if (cycle < 2) {
                        check(p.currentFogCycle == cycle + 1 && p.blackFogPhase == BlackFogPhase.COMBAT,
                                "each of the first two fog windows must resume combat for the next cycle");
                        now = fogDeadline + (cycle == 0 ? 50_000L : 60_000L);
                        p.blackFogPhaseDeadlineMillis = now;
                    }
                }
                check(p.currentFogCycle == 3 && p.blackFogPhase == BlackFogPhase.SAFE
                                && p.blackFogPhaseDeadlineMillis == 0L,
                        "exactly three production cycles must complete the objective");
            }
            case "setup-retry" -> {
                p.zoneSetupSucceeds = false;
                p.blackFogPhaseDeadlineMillis = 40_000L;
                p.tickBlackFogObjective(40_000L);
                check(p.setupCalls == 1 && p.currentFogZoneSetupAttempts == 1
                                && p.currentFogZoneSetupRetryAtMillis == 42_000L
                                && p.blackFogPhase == BlackFogPhase.COMBAT,
                        "first missing zone must schedule the two-second retry without entering fog");
                p.tickBlackFogObjective(41_999L);
                check(p.setupCalls == 1, "retry must not run before its deadline");
                p.tickBlackFogObjective(42_000L);
                check(p.setupCalls == 2 && p.currentFogZoneSetupAttempts == 2
                                && p.blackFogPhase == BlackFogPhase.COMBAT,
                        "second missing zone must remain fail-closed");
                p.tickBlackFogObjective(44_000L);
                check(p.setupCalls == 3 && p.phase == EventPhase.RECOVERY_REQUIRED
                                && p.blackFogPhase != BlackFogPhase.FOG,
                        "third missing zone must abort to recovery without applying fog");
            }
            case "invalid-journal" -> {
                p.currentTemporaryBlockOriginals.put(new HazardPlanner.Point(1, 1), "minecraft:stone");
                Bukkit.world = new World();
                p.restoreCurrentSafeZoneBlocks();
                check(p.currentTemporaryBlockOriginals.containsKey(new HazardPlanner.Point(1, 1)),
                        "invalid journal must retain rollback evidence");
                check(p.phase == EventPhase.RECOVERY_REQUIRED,
                        "invalid journal must fence Wave 5 in recovery");
            }
            case "mark-restored-failure" -> {
                Bukkit.world = new World();
                p.hazardJournal.snapshot = new HazardMutationJournal.Snapshot(true, p.eventId, p.generation,
                        HazardMutationJournal.Status.APPLIED, "world", List.of(
                        new HazardMutationJournal.Entry(1, 0, 1, "minecraft:stone", "", true, false)));
                p.hazardJournal.markRestored = false;
                p.currentTemporaryBlockOriginals.put(new HazardPlanner.Point(1, 1), "minecraft:stone");
                p.restoreCurrentSafeZoneBlocks();
                check(p.currentTemporaryBlockOriginals.containsKey(new HazardPlanner.Point(1, 1)),
                        "failed journal commit must retain rollback evidence for retry");
                check(p.phase == EventPhase.RECOVERY_REQUIRED,
                        "failed journal commit must fence Wave 5 in recovery");
            }
            case "restore-failure-does-not-advance" -> {
                p.blackFogPhase = BlackFogPhase.FOG;
                p.blackFogPhaseDeadlineMillis = 1L;
                p.currentSafeZoneCells.add(new HazardPlanner.Point(1, 1));
                p.currentSafeZoneCenters.put(0, new HazardPlanner.Point(1, 1));
                p.currentTemporaryBlockOriginals.put(new HazardPlanner.Point(1, 1), "minecraft:stone");
                Bukkit.world = new World();
                p.hazardJournal.snapshot = new HazardMutationJournal.Snapshot(true, p.eventId, p.generation,
                        HazardMutationJournal.Status.APPLIED, "world", List.of(
                        new HazardMutationJournal.Entry(1, 0, 1, "minecraft:stone", "", true, false)));
                p.restoreThrows = true;
                Bukkit.restoreThrows = true;
                p.tickBlackFogObjective(1L);
                check(Bukkit.restoreBlockAttempts == 1,
                        "injected failure must reach the actual block restoration boundary");
                check(p.currentFogCycle == 0 && p.phase == EventPhase.RECOVERY_REQUIRED,
                        "failed safe-zone restoration must not count a fog cycle as complete");
                check(p.currentTemporaryBlockOriginals.containsKey(new HazardPlanner.Point(1, 1)),
                        "failed block restoration must retain rollback evidence");
                check(p.hazardJournal.markRestoredCalls == 0,
                        "failed block restoration must leave the durable journal recoverable");
            }
            case "invalid-original-data" -> {
                Bukkit.world = new World();
                p.hazardJournal.snapshot = new HazardMutationJournal.Snapshot(true, p.eventId, p.generation,
                        HazardMutationJournal.Status.APPLIED, "world", List.of(
                        new HazardMutationJournal.Entry(1, 0, 1, "invalid:data", "", true, false)));
                p.currentTemporaryBlockOriginals.put(new HazardPlanner.Point(1, 1), "invalid:data");
                p.restoreCurrentSafeZoneBlocks();
                check(p.currentTemporaryBlockOriginals.containsKey(new HazardPlanner.Point(1, 1)),
                        "unparseable original block data must retain rollback evidence");
                check(p.phase == EventPhase.RECOVERY_REQUIRED && p.hazardJournal.markRestoredCalls == 0,
                        "unparseable original block data must not mark the journal restored");
            }
            default -> throw new AssertionError("unknown scenario: " + args[0]);
        }
        System.out.println(args[0] + " OK");
    }
}
''', encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    compiled = subprocess.run([
        "javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output), str(java),
        str(domain / "BlackFogTimingPolicy.java"), str(domain / "EndRiftObjective.java"),
        str(domain / "BlackFogRecoverySnapshot.java"), str(domain / "BlackFogSafeZonePolicy.java"),
        str(domain / "Wave5PreFogRefillPolicy.java"), str(domain / "WaveMechanicsPolicy.java"),
        str(domain / "PressureBudgetController.java"),
    ], capture_output=True, text=True, check=False)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario", [
    "three-cycles", "pre-fog-refill", "setup-retry", "invalid-journal", "mark-restored-failure",
    "restore-failure-does-not-advance", "invalid-original-data",
])
def test_wave5_black_fog_adapter(probe, scenario):
    result = subprocess.run(["java", "-cp", str(probe), "BlackFogAdapterProbe", scenario],
                            capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave5_fog_packet_uses_one_based_protocol_cycles(probe):
    result = subprocess.run(["java", "-cp", str(probe), "BlackFogAdapterProbe", "wire-cycle"],
                            capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave5_recovery_checkpoint_codec_is_executable(probe):
    root = ROOT / "tests/BlackFogRecoverySnapshotTest.java"
    compiled = subprocess.run([
        "javac", "-proc:none", "-encoding", "UTF-8", "-cp", str(probe),
        "-d", str(probe), str(root),
    ], capture_output=True, text=True, check=False)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(probe), "BlackFogRecoverySnapshotTest"],
                            capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave5_fog_renders_a_bounded_arena_perimeter_curtain():
    source = SOURCE.read_text("utf-8")
    body = extract(source, "void renderWaveObjective(long now)")

    assert "BlackFogCurtainPolicy.perimeter(18.0D, 5.0D)" in body
    policy = (SOURCE.parent / "domain/BlackFogCurtainPolicy.java").read_text(encoding="utf-8")
    assert "POINTS_PER_RING = 96" in policy
    assert "VERTICAL_LAYERS = 8" in policy
    assert "MAX_PARTICLE_POINTS = POINTS_PER_RING * VERTICAL_LAYERS" in policy
    assert "spawnTypedParticle(viewer, Particle.SQUID_INK" in body
    assert "eventTickCounter % 20L == 0L" in body
