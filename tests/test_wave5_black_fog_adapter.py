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
        "boolean spawnCurrentSafeZoneVisuals(World world, Location core)",
        "boolean restoreCurrentSafeZoneBlocks()",
        "void restoreBlackFogBlock(Block block, String originalBlockData)",
    ])
    java = output / "BlackFogAdapterProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import me.copimine.endevent.domain.BlackFogTimingPolicy;
import me.copimine.endevent.domain.BlackFogSafeZonePolicy;
import me.copimine.endevent.domain.BlackFogSafeZoneCapacityPolicy;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.Wave5PreFogRefillPolicy;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

public final class BlackFogAdapterProbe {
    enum EventPhase { WAVE_5, RECOVERY_REQUIRED }
    enum BlackFogPhase { COMBAT, SAFE, FOG }
    enum Sound { BLOCK_BEACON_POWER_SELECT, BLOCK_AMETHYST_BLOCK_CHIME }
    enum PotionEffectType { REGENERATION }
    enum Material {
        AIR, EMERALD_BLOCK, BARRIER, STONE;
        boolean isSolid() { return this != AIR; }
    }
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
        final Set<String> blockedFloorCells = new HashSet<>();
        final Map<String, Integer> floorCandidateReads = new HashMap<>();
        int blockMutationCount, itemDisplaySpawns;
        boolean floorsBlocked;
        Block getBlockAt(int x, int y, int z) {
            String key = x+":"+y+":"+z;
            if (y == 0) floorCandidateReads.merge(x+":"+z, 1, Integer::sum);
            return blocks.computeIfAbsent(key, ignored -> new Block(this, x, y, z,
                    y == 0 && (floorsBlocked || blockedFloorCells.contains(x+":"+z))));
        }
        void seedEmeraldFloor(int x, int z) {
            Block floor = getBlockAt(x, 0, z);
            floor.type = Material.EMERALD_BLOCK;
            floor.data = new BlockData("minecraft:emerald_block");
        }
        String getName() { return "world"; }
        UUID getUID() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }
        <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> initializer) {
            T entity = type.cast(new ItemDisplay());
            itemDisplaySpawns++;
            initializer.accept(entity);
            return entity;
        }
    }
    static final class Location {
        World world;
        double x, y, z;
        Location() { this(new World(), 0.0D, 0.0D, 0.0D); }
        Location(World world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        World getWorld() { return world; }
        double getX() { return x; }
        double getZ() { return z; }
    }
    static final class Block {
        final World world;
        final int x, y, z;
        Material type;
        BlockData data;
        Block(World world, int x, int y, int z, boolean blocked) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            type = y == 0 && !blocked ? Material.STONE : Material.AIR;
            data = new BlockData("minecraft:" + type.name().toLowerCase(Locale.ROOT));
        }
        Material getType() { return type; }
        boolean isLiquid() { return false; }
        boolean isPassable() { return type == Material.AIR; }
        Location getLocation() { return new Location(world, x, y, z); }
        BlockData getBlockData() { return data; }
        void setType(Material value, boolean physics) {
            world.blockMutationCount++;
            type = value;
            data = new BlockData("minecraft:" + value.name().toLowerCase(Locale.ROOT));
        }
        void setBlockData(BlockData value, boolean physics) {
            Bukkit.restoreBlockAttempts++;
            if (Bukkit.restoreThrows) throw new IllegalStateException("injected block restore failure");
            world.blockMutationCount++;
            data = value;
            type = switch (value.value) {
                case "minecraft:air" -> Material.AIR;
                case "minecraft:emerald_block" -> Material.EMERALD_BLOCK;
                case "minecraft:barrier" -> Material.BARRIER;
                default -> Material.STONE;
            };
        }
    }
    static class Entity {
        final UUID id = UUID.randomUUID();
        UUID getUniqueId() { return id; }
    }
    static final class ItemDisplay extends Entity {
        enum ItemDisplayTransform { NONE }
        void setItemStack(ItemStack item) { }
        void setItemDisplayTransform(ItemDisplayTransform transform) { }
        void setBillboard(Display.Billboard billboard) { }
        void setBrightness(Display.Brightness brightness) { }
        void setGravity(boolean gravity) { }
        void setInvulnerable(boolean invulnerable) { }
        void setPersistent(boolean persistent) { }
        void setTransformation(Transformation transformation) { }
    }
    static final class Display {
        enum Billboard { FIXED }
        record Brightness(int block, int sky) { }
    }
    static final class ItemStack { }
    static final class Vector3f {
        Vector3f(float x, float y, float z) { }
    }
    static final class AxisAngle4f {
        AxisAngle4f() { }
    }
    static final class Transformation {
        Transformation(Vector3f translation, AxisAngle4f leftRotation,
                       Vector3f scale, AxisAngle4f rightRotation) { }
    }
    static final class Config {
        double radius = 20.0D;
        double arenaRadius() { return radius; }
    }
    static final String MODEL_SAFE_ZONE_FLOOR = "safe-floor";
    static final String EVENT_KIND_DISPLAY = "display";
    static final class Player {
        void addPotionEffect(PotionEffect effect) { }
    }
    static final class Bukkit {
        static World world;
        static boolean restoreThrows;
        static int restoreBlockAttempts;
        static World getWorld(String name) { return world; }
        static Entity getEntity(UUID id) { return null; }
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
        record Entry(int x, int floorY, int z, String floorOriginal, String webOriginal,
                     String mutation, String world, String eventId, long generation) {
            Entry(int x, int floorY, int z, String floorOriginal, String webOriginal,
                  boolean emerald, boolean barrier) {
                this(x, floorY, z, floorOriginal, webOriginal,
                        emerald ? "EMERALD_BARRIER" : barrier ? "BARRIER" : "NONE",
                        "world", "event", 4L);
            }
            boolean isEmeraldBarrierMutation() { return "EMERALD_BARRIER".equals(mutation); }
            boolean isBarrierMutation() { return "BARRIER".equals(mutation); }
        }
        record Snapshot(boolean valid, String eventId, long generation, Status status,
                        String world, List<Entry> entries) {
            static Snapshot empty() { return new Snapshot(false, "", 0L, Status.EMPTY, "", List.of()); }
        }
        Snapshot snapshot = new Snapshot(false, "", 0L, Status.EMPTY, "", List.of());
        boolean markRestored = true;
        boolean prepareSucceeds = true, markAppliedSucceeds = true;
        int prepareCalls, markAppliedCalls, markRestoredCalls;
        Snapshot load() { return snapshot; }
        boolean prepare(String eventId, long generation, String world, List<Entry> entries) {
            prepareCalls++;
            if (!prepareSucceeds) return false;
            snapshot = new Snapshot(true, eventId, generation, Status.PREPARED, world, List.copyOf(entries));
            return true;
        }
        boolean markApplied() {
            markAppliedCalls++;
            if (!markAppliedSucceeds) return false;
            snapshot = new Snapshot(true, snapshot.eventId(), snapshot.generation(), Status.APPLIED,
                    snapshot.world(), snapshot.entries());
            return true;
        }
        boolean markRestored() {
            markRestoredCalls++;
            if (!markRestored) return false;
            snapshot = new Snapshot(true, snapshot.eventId(), snapshot.generation(), Status.RESTORED,
                    snapshot.world(), snapshot.entries());
            return true;
        }
        String path() { return "fog-journal.yml"; }
    }

    EventPhase phase = EventPhase.WAVE_5;
    BlackFogPhase blackFogPhase = BlackFogPhase.COMBAT;
    boolean fogSafeZoneTelegraphActive;
    static final long FOG_SAFE_ZONE_TELEGRAPH_MILLIS = 3_000L;
    String eventId = "event", recoveryReason = "";
    long generation = 4L, blackFogPhaseDeadlineMillis, currentFogZoneSetupRetryAtMillis;
    int activeWave = 5, currentFogCycle, currentFogZoneCount, currentFogZoneSide, currentFogZoneSetupAttempts;
    int waveSpawnGroupIndex = 1, waveSpawnEntityOffset, liveWaveMobCount, refillPacks;
    long wave5PreFogRefillAtMillis;
    boolean testWaveFrontVisualMode;
    String worldName = "world";
    List<WaveMechanicsPolicy.WaveCounts> waveSpawnSchedule = List.of(
            new WaveMechanicsPolicy.WaveCounts(2, 1, 0, 0, 0));
    boolean frozen, restoreThrows;
    int setupCalls, fogEffects, packetRefreshes, cleanupCalls;
    String sentPacketSubject = "", sentPacketStatus = "", sentPacketVisual = "";
    long sentPacketDuration;
    float sentPacketIntensity;
    Location core = new Location();
    Config config = new Config();
    int arenaMinX = -100, arenaMaxX = 100, arenaMinZ = -100, arenaMaxZ = 100;
    HazardMutationJournal hazardJournal = new HazardMutationJournal();
    final List<Player> eligiblePlayers = new ArrayList<>(List.of(
            new Player(), new Player(), new Player(), new Player()));
    Map<UUID, Long> currentFogLastPacketAt = new HashMap<>();
    Map<HazardPlanner.Point, String> currentTemporaryBlockOriginals = new LinkedHashMap<>();
    Set<HazardPlanner.Point> currentSafeZoneCells = new LinkedHashSet<>();
    Set<HazardPlanner.Point> currentBarrierCells = new LinkedHashSet<>();
    Map<Integer, HazardPlanner.Point> currentSafeZoneCenters = new LinkedHashMap<>();
    Set<UUID> currentSafeZoneVisuals = new LinkedHashSet<>();
    Set<UUID> waveObjectiveVisuals = new LinkedHashSet<>();

    BlackFogAdapterProbe() { Bukkit.world = core.world; }

    Location coreCombatAnchorLocation() { return core; }
    String worldVfxDimension(Location location) { return "the_end"; }
    int waveScalePlayers() { return 4; }
    boolean clearCurrentSafeZoneVisuals() {
        setupCalls++;
        cleanupCalls++;
        boolean restored = restoreCurrentSafeZoneBlocks();
        currentSafeZoneVisuals.clear();
        currentSafeZoneCells.clear();
        currentSafeZoneCenters.clear();
        currentBarrierCells.clear();
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
    List<Player> activeWaveParticipants() { return eligiblePlayers; }
    boolean isCurrentSafeZonePlayer(Player player) { return false; }
    void restoreBlock(Block block, String original) {
        if (restoreThrows) throw new IllegalStateException("injected block restore failure");
        block.type = Material.STONE;
    }
    int combatFloorY() { return 0; }
    boolean isCoreBlockPosition(Location location) { return false; }
    ItemStack overlayItem(String model, String name) { return new ItemStack(); }
    void tag(ItemDisplay display, String kind, int wave, boolean objective) { }
    void registerOwnedEntity(Entity entity) { }
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
        core.world.floorsBlocked = false;
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
                    check(p.blackFogPhase == BlackFogPhase.COMBAT
                                    && p.fogSafeZoneTelegraphActive,
                            "due combat must telegraph the safe area first");
                    long safeAt = now + 3_000L;
                    p.tickBlackFogObjective(safeAt);
                    check(p.blackFogPhase == BlackFogPhase.SAFE, "telegraph must reveal a safe area");
                    int expectedSide = cycle == 0 ? 2 : 1;
                    check(p.currentFogZoneSide == expectedSide,
                            "four-player safe zones must shrink by the player-count policy");
                    long safeDeadline = safeAt + 8_000L;
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
                p.core.world.floorsBlocked = true;
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
            case "capacity-20" -> {
                p.eligiblePlayers.clear();
                for (int i = 0; i < 20; i++) p.eligiblePlayers.add(new Player());
                p.liveWaveMobCount = 3;
                p.blackFogPhaseDeadlineMillis = 40_000L;
                p.tickBlackFogObjective(40_000L);
                check(p.blackFogPhase == BlackFogPhase.COMBAT
                                && p.fogSafeZoneTelegraphActive,
                        "twenty-player setup must telegraph the safe area first");
                p.tickBlackFogObjective(43_000L);
                check(p.blackFogPhase == BlackFogPhase.SAFE
                                && p.currentFogZoneSide == 1 && p.currentFogZoneCount == 20,
                        "twenty eligible players must receive twenty one-cell safe zones before fog");
                check(p.currentSafeZoneCells.size() == 20 && p.currentSafeZoneCenters.size() == 20,
                        "the production ring layout must place twenty distinct usable cells and centers");
                check(p.hazardJournal.prepareCalls == 1 && p.hazardJournal.snapshot.entries().size() == 20
                                && p.hazardJournal.markAppliedCalls == 1,
                        "all twenty cells must be journaled before the setup is accepted");
                check(p.core.world.blockMutationCount == 20 && p.core.world.itemDisplaySpawns == 20,
                        "accepted twenty-player layout must mutate only its twenty safe floors and visuals");
            }
            case "reconnected-roster-refresh" -> {
                p.eligiblePlayers.clear();
                for (int i = 0; i < 2; i++) p.eligiblePlayers.add(new Player());
                p.liveWaveMobCount = 3;
                p.blackFogPhaseDeadlineMillis = 40_000L;
                p.tickBlackFogObjective(40_000L);
                check(p.blackFogPhase == BlackFogPhase.COMBAT
                                && p.fogSafeZoneTelegraphActive
                                && p.currentSafeZoneCells.size() == 9,
                        "initial safe-zone setup must fit the two currently eligible players");
                p.tickBlackFogObjective(43_000L);
                check(p.blackFogPhase == BlackFogPhase.SAFE,
                        "the initial telegraph must activate the safe area");
                long firstSafeDeadline = p.blackFogPhaseDeadlineMillis;
                for (int i = 0; i < 8; i++) p.eligiblePlayers.add(new Player());
                p.tickBlackFogObjective(firstSafeDeadline);
                check(p.blackFogPhase == BlackFogPhase.SAFE
                                && p.currentFogZoneSide == 1 && p.currentFogZoneCount == 10
                                && p.currentSafeZoneCells.size() >= 10,
                        "reconnected eligible players must receive enough distinct safe cells before fog");
                check(p.blackFogPhaseDeadlineMillis > firstSafeDeadline
                                && p.hazardJournal.snapshot.entries().size() >= 10,
                        "expanding for the returning roster must restart the safe warning and journal capacity");
            }
            case "empty-eligible-retries" -> {
                p.eligiblePlayers.clear();
                p.liveWaveMobCount = 3;
                p.blackFogPhaseDeadlineMillis = 40_000L;
                p.tickBlackFogObjective(40_000L);
                check(p.blackFogPhase == BlackFogPhase.COMBAT
                                && p.currentFogZoneSetupAttempts == 0
                                && p.currentFogZoneSetupRetryAtMillis == 42_000L,
                        "an empty eligible roster must defer setup without consuming an attempt");
                p.tickBlackFogObjective(41_999L);
                check(p.setupCalls == 0 && p.hazardJournal.prepareCalls == 0,
                        "empty-roster retry must not attempt placement before its deadline");
                p.tickBlackFogObjective(42_000L);
                check(p.currentFogZoneSetupAttempts == 0
                                && p.currentFogZoneSetupRetryAtMillis == 44_000L
                                && p.blackFogPhase == BlackFogPhase.COMBAT,
                        "a still-empty roster must stay fail-closed and re-arm the retry");
            }
            case "blocked-capacity-no-mutation" -> {
                p.eligiblePlayers.clear();
                for (int i = 0; i < 20; i++) p.eligiblePlayers.add(new Player());
                p.liveWaveMobCount = 3;
                p.core.world.floorsBlocked = true;
                p.blackFogPhaseDeadlineMillis = 40_000L;
                for (int attempt = 0; attempt < 3; attempt++) {
                    long now = 40_000L + attempt * 2_000L;
                    p.tickBlackFogObjective(now);
                    check(p.core.world.blockMutationCount == 0
                                    && p.hazardJournal.prepareCalls == 0
                                    && p.hazardJournal.markAppliedCalls == 0
                                    && p.core.world.itemDisplaySpawns == 0,
                            "blocked-cell capacity refusal must not journal, mutate blocks, or spawn visuals");
                    check(p.currentSafeZoneCells.isEmpty()
                                    && p.currentTemporaryBlockOriginals.isEmpty()
                                    && p.blackFogPhase != BlackFogPhase.FOG,
                            "failed capacity retries must discard candidates without entering fog");
                }
                check(p.currentFogZoneSetupAttempts == 3 && p.phase == EventPhase.RECOVERY_REQUIRED,
                        "three blocked-capacity attempts must stop in recovery without world mutation");
            }
            case "duplicate-safe-cells" -> {
                p.currentFogZoneCount = 20;
                p.currentFogZoneSide = 3;
                p.config.radius = 0.0D;
                p.arenaMinX = 1;
                p.arenaMaxX = 1;
                p.arenaMinZ = -8;
                p.arenaMaxZ = -6;
                boolean placed = p.spawnCurrentSafeZoneVisuals(p.core.world, p.core);
                check(p.core.world.floorCandidateReads.getOrDefault("1:-8", 0) == 2
                                && p.core.world.floorCandidateReads.getOrDefault("1:-7", 0) == 2
                                && p.core.world.floorCandidateReads.getOrDefault("1:-6", 0) == 2,
                        "two real production zone footprints must visit the same three candidate cells");
                check(!placed && p.hazardJournal.prepareCalls == 0
                                && p.core.world.blockMutationCount == 0
                                && p.core.world.itemDisplaySpawns == 0,
                        "duplicate candidates must not satisfy four-player capacity or reach journal/world mutation");
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
                Bukkit.world.seedEmeraldFloor(1, 1);
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
                Bukkit.world.seedEmeraldFloor(1, 1);
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
                Bukkit.world.seedEmeraldFloor(1, 1);
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
        str(domain / "BlackFogSafeZoneCapacityPolicy.java"),
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


def test_wave5_safe_zone_layout_reserves_one_usable_cell_per_eligible_player():
    source = SOURCE.read_text("utf-8")
    tick = extract(source, "void tickBlackFogObjective(long now)")
    setup = extract(source, "boolean spawnCurrentSafeZoneVisuals(World world, Location core)")

    assert "int eligiblePlayerCount = activeWaveParticipants().size()" in tick
    assert "BlackFogSafeZoneCapacityPolicy.zoneCountForCapacity" in tick
    assert "BlackFogSafeZoneCapacityPolicy.hasCapacity" in setup
    assert setup.index("BlackFogSafeZoneCapacityPolicy.hasCapacity") < setup.index("hazardJournal.prepare")
    assert "currentSafeZoneCells.size()" in setup


@pytest.mark.parametrize("scenario", [
    "capacity-20", "empty-eligible-retries", "blocked-capacity-no-mutation",
    "duplicate-safe-cells", "reconnected-roster-refresh",
])
def test_wave5_safe_zone_placement_capacity_scenarios(probe, scenario):
    result = subprocess.run(["java", "-cp", str(probe), "BlackFogAdapterProbe", scenario],
                            capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stdout + result.stderr
