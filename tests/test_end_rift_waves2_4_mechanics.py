"""Execute production W2/W4 adapter decisions at detached Bukkit boundaries.

END_RIFT_EVENT_SOURCE selects a saved pre-fix source for red reproduction. The
Java methods are extracted rather than reimplemented; only network/world APIs
are detached. This is server behavior evidence, never native rendering proof.
"""

import os
import re
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
EVENT = Path(os.environ.get("END_RIFT_EVENT_SOURCE", str(
    ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java")))


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


def test_wave_two_edge_spawn_falls_back_to_safe_spawn_when_ring_has_no_floor() -> None:
    source = EVENT.read_text(encoding="utf-8")
    resolver = _declaration(source, "private Location safeSpawnLocationForWave(")

    assert "Location edgeSpawn = safeWaveTwoEdgeSpawnLocation(core, index);" in resolver
    assert "return edgeSpawn != null ? edgeSpawn : safeSpawnLocation(core, index, offset);" in resolver


@pytest.fixture(scope="module")
def mechanics_harness(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    initializer = _declaration(source, "private WaveObjectiveStartResult startCanonicalObjective(")
    branch = _declaration(initializer, "case RIFT_HUNT ->")
    methods = ["void startHunt(long started) { waveObjectiveStartedMillis = started; "
               + branch[branch.index("{") + 1:-1] + "}"]
    methods += [_declaration(source, signature) for signature in (
        "private void tickCurrentHuntObjective(long now)",
        "private void updateMarkedTargetObjective(long now)",
        "private boolean spawnNextWaveGroup(World world, Location core)",
        "private boolean isWaveTwoOrdinaryMob(Entity entity)",
        "private boolean isApprovedWaveTwoFinalElite(Entity entity)",
        "private boolean retireWaveTwoRosterAfterConfirmedDeath(Entity entity)",
        "private void applyCurrentReflectedObeliskHit(",
    )]
    methods.append(_declaration(source, "private void requireWaveTwoMarkAssignmentRecovery("))
    methods.append('Map<String, String> waveObjectiveProgressSnapshot() { return Map.of('
                   + '"wave2-mark-assignment-id", Integer.toString(waveTwoMarkCycle)); }')
    methods.append(_declaration(source, "private Location spawnLocation("))
    if "private Location safeWaveTwoEdgeSpawnLocation(Location core, int index)" in source:
        methods.append(_declaration(source, "private Location safeWaveTwoEdgeSpawnLocation(Location core, int index)"))
        methods.append("Location waveTwoSpawnLocation(int index) { "
                       "return safeWaveTwoEdgeSpawnLocation(new Location(), index); }")
    else:
        methods.append("Location waveTwoSpawnLocation(int index) { "
                       "return spawnLocation(new Location(), index, 3.0D); }")
    # Production roster and validation are intact; projectile construction is
    # the external world boundary of this test.
    launch = _declaration(source, "private boolean launchWave4Fireball(")
    launch = launch[:launch.index("        // The real Wave 4 obelisk")]
    methods.append(launch + "lastLaunchTarget = target.getUniqueId(); return true; }")
    methods.append(_declaration(source, "private boolean prepareWave4FireCast("))
    # Preserve the live reflection admission expressions. Vector/teleport/
    # visual work follows admission and is an external boundary here.
    reflection = _declaration(source, "public void onRiftFireballReflect(")
    begin = reflection.index("        int trialChamber =")
    end = reflection.index("        event.setCancelled(true);")
    methods.append("boolean reflectionAdmission(LargeFireball fireball, "
                   "RiftFireballRuntimeState state, Player player) {\n"
                   + reflection[begin:end] + "return authorizedReflector; }")
    for signature in (
        "private void clearCurrentHuntMark(",
        "private void sendCurrentHuntMarkPacket(",
        "private void releaseCurrentHuntTarget(",
        "private void clearCurrentHuntRevealEffects(",
    ):
        if signature in source:
            methods.append(_declaration(source, signature))
    fields = "\n".join(re.findall(
        r"    private (?:UUID|long|int|boolean|PotionEffect) (?:waveTwo\w*|currentHunt\w*)[^;]*;", source))
    fields += "\n    private String waveTwoControlInstanceId = \"\";"
    fields += "\n    private Map<String, String> waveTwoRecoveryCheckpoint = Map.of();"
    fields += "\n    private int waveSpawnWave = 2, waveSpawnGroupIndex, waveSpawnEntityOffset;"
    fields += "\n    private boolean restoredWaveTwoGroupCleared;"
    fields += "\n    private long waveNextSpawnAtMillis;"
    fields += "\n    private List<WaveMechanicsPolicy.WaveCounts> waveSpawnSchedule = List.of();"
    # These detached scenarios cover combat scheduling, not client rendering.
    fields += "\n    private boolean testWaveFrontVisualMode = true;"
    fields += "\n    private int activeWave;"
    fields += "\n    private WaveTwoPhantomMaterializationPolicy.State waveTwoPhantomActivationState;"
    fields += "\n    private Location waveTwoPhantomActivationLocation;"
    fields += "\n    private BukkitTask waveTwoPhantomWarningTask;"
    fields += "\n    private List<WaveTwoPhantomLayoutPolicy.Phantom> waveTwoPhantomLayout = List.of();"
    fields += "\n" + "\n".join(re.findall(
        r"    private final (?:Map<PotionEffectType, PotionEffect>|Set<PotionEffectType>) waveTwo\w*[^;]*;", source))
    cleanup = _declaration(source, "private boolean clearWaveObjectiveState(")
    cleanup_start = cleanup.index("        clearCurrentHuntMark(") if "        clearCurrentHuntMark(" in cleanup else cleanup.index("        waveTwoMarkedPlayerUuid = null;")
    methods.append("void finishHunt() { " + cleanup[cleanup_start:cleanup.index("        currentCarrierCharges = 0;", cleanup_start)] + "}")
    for signature, name in (("public void onPlayerQuit(", "quitHunt"), ("public void onPlayerDeath(", "deathHunt")):
        lifecycle = _declaration(source, signature)
        calls = [line for line in lifecycle.splitlines() if "releaseCurrentHuntTarget(" in line]
        methods.append(f"void {name}(Player player) {{ UUID uuid = player.getUniqueId(); " + "\n".join(calls) + "}")
    constants = "\n".join(re.findall(
        r"    private static final (?:int|String) (?:MAX_)?WAVE_TWO_\w+[^;]*;", source))
    harness = r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.WaveCombatCoordinator;

public final class Waves24MechanicsHarness {
    __FIELDS__
    __CONSTANTS__
    static final int MAX_EVENT_WAVE = 7;
    long generation = 5L, eventTickCounter = 100L, waveObjectiveStartedMillis;
    int waveTargetCursor, visualUpdates, hitReactions, clientActive, clientClears, scalePlayers = 1;
    int liveWaveEntitiesForWave2 = 1;
    Set<UUID> waveTwoOutstandingEntityUuids = new LinkedHashSet<>();
    List<WaveMechanicsPolicy.WaveCounts> spawnedWaveGroups = new ArrayList<>();
    Map<UUID, Entity> ownedEntities = new LinkedHashMap<>();
    String eventId = "event";
    String recoveryReason = "";
    Config config = new Config();
    Object keyRiftFireballState = new Object();
    UUID lastLaunchTarget;
    WaveCombatCoordinator waveCombatCoordinator = new WaveCombatCoordinator();
    int tagWaveTwoSpawnGroup(int groupNumber, Set<UUID> entitiesBeforeSpawn) {
        waveTwoGroupsSpawned = groupNumber;
        return spawnedWaveGroups.isEmpty() ? 0 : spawnedWaveGroups.get(spawnedWaveGroups.size() - 1).total();
    }
    void checkpointWaveTwoGroupSpawn() { }
    boolean checkpointWaveTwoGroupClear() { return true; }
    boolean testWave4ObeliskMode;
    EventPhase phase = EventPhase.WAVE_4;
    Set<UUID> currentWave4ConsumedFireballs = new HashSet<>();
    Map<UUID, RiftFireballRuntimeState> activeRiftFireballs = new HashMap<>();
    static Logger LOGGER = Logger.getLogger("waves24");
    static { LOGGER.setLevel(Level.OFF); }
    Logger getLogger() { return LOGGER; }
    int eventScalePlayers() { return scalePlayers; }
    int randomSeconds(int minimum, int maximum) { return minimum; }
    boolean isCombatTarget(Player player) { return player != null && player.online && !player.dead; }
    boolean isActiveArenaParticipant(Player player) { return isCombatTarget(player) && player.wave; }
    boolean isActiveBossParticipant(Player player) { return isCombatTarget(player) && player.boss; }
    List<Player> activeWaveParticipants() { return Bukkit.players.stream().filter(this::isActiveArenaParticipant).toList(); }
    List<Player> activeBossParticipants() { return Bukkit.players.stream().filter(this::isActiveBossParticipant).toList(); }
    int arenaMinX = -128, arenaMinY = -128, arenaMinZ = -128;
    int arenaMaxX = 128, arenaMaxY = 128, arenaMaxZ = 128;
    boolean isArenaLocation(Location location) {
        return location.inArena && location.getBlockX() >= arenaMinX && location.getBlockX() <= arenaMaxX
                && location.getBlockY() >= arenaMinY && location.getBlockY() <= arenaMaxY
                && location.getBlockZ() >= arenaMinZ && location.getBlockZ() <= arenaMaxZ;
    }
    Location coreCombatAnchorLocation() { return new Location(); }
    String worldVfxDimension(Location location) { return "the_end"; }
    Collection<Player> eventAudience() { return Bukkit.players; }
    boolean isEventParticleViewer(Player player, Location location) { return player.online; }
    boolean isRealitySplitReflectionProjectile(LargeFireball entity, RiftFireballRuntimeState state) { return state != null && state.wave7; }
    boolean isRealitySplitTrialParticipant(Player player, int chamber) { return player.chamber == chamber; }
    Object keyWave = new Object(), keyKind = new Object(), keyWaveSpawnGroup = new Object();
    Object keyWaveTwoFinalElite = new Object();
    Object keyEventGeneration = new Object(), keyGeneration = new Object();
    static final String EVENT_KIND_WAVE_MOB = "wave_mob", EVENT_KIND_ELITE = "elite";
    int readInt(Entity entity, Object key, int fallback) {
        if (entity instanceof LargeFireball fireball && key == keyChamberId) return fireball.chamber;
        Object value = entity.getPersistentDataContainer().values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
    String readString(Entity entity, Object key) {
        Object value = entity.getPersistentDataContainer().values.get(key);
        return value instanceof String string ? string : "";
    }
    boolean officialAttempt, saveStateSucceeds = true;
    int saveCalls;
    boolean isOfficialCurrentAttempt() { return officialAttempt; }
    boolean saveStateSync() { saveCalls++; return saveStateSucceeds; }
    void forcePhase(EventPhase next, String reason) { phase = next; }
    Object keyChamberId = new Object();
    void updateObeliskHealthVisual(Wave4ObeliskRuntimeState obelisk) { visualUpdates++; }
    void renderRiftObeliskHit(Location base, int remaining) { hitReactions++; }
    void sendClientPacket(Player player, String type, String instance, long duration, String... args) {
        if (duration == 0L) clientClears++; else clientActive++;
    }
    void sendClientPacket(Player player, String type, String instance, long duration,
            String mode, String shaderpack, int width, int height, float intensity, String subject, String status) {
        if ("CLEAR".equals(status)) clientClears++; else clientActive++;
    }
    void sendClientPacket(Player player, String type, String instance, long duration,
            String mode, String shaderpack, int width, int height, float intensity,
            String subject, String viewer, String status) {
        if ("CLEAR".equals(status)) clientClears++; else clientActive++;
    }
    void sendClientPacket(Player player, String type, String instance, long duration,
            String mode, String shaderpack, int width, int height, float intensity,
            String subject, String status, String encodedPosition, String phantomId) {
        if ("CLEAR".equals(status)) clientClears++; else clientActive++;
    }
    void sendWorldBeamPacket(Player player, String key, Location from, Location to, int color, float width) {}
    void clearWorldVfxBeamsByPrefix(String key) {}
    String encodeWaveTwoPosition(WaveTwoPhantomLayoutPolicy.Position position) { return "detached"; }
    void playWaveFeedback(String cue,Location point) {}
    void clearWaveCombatCue(UUID owner) {}
    int countLiveWaveEntitiesForWave(int wave) { return wave == 2 ? liveWaveEntitiesForWave2 : 0; }
    boolean tryBeginWaveTwoPhantomActivation(World world, Location core,
            WaveMechanicsPolicy.WaveCounts group, int groupNumber) { return false; }
    void clearWaveTwoPhantomActivationPresentation() { waveTwoPhantomWarningTask = null; }
    void ensureWaveTwoPhantomLayout(Player player, Location core) { }
    void spawnWaveGroup(World world, Location core, int wave, boolean guardianWave,
                        boolean test, WaveMechanicsPolicy.WaveCounts group, int baseIndex,
                        Location reservedPhantomSpawn) {
        spawnedWaveGroups.add(group);
    }
    void renderWave4ObeliskCue(Wave4ObeliskRuntimeState state, String stage, String ability, Location mark, double radius) {}
    void spawnEventParticle(Object... values) {
        for (Player player : Bukkit.players) player.particles++;
    }

    enum EventPhase { WAVE_2, WAVE_4, COLLECTING, READY_FOR_PLAYERS, RECOVERY_REQUIRED }
    enum ObeliskStage { ACTIVE, COLLAPSING }
    enum PotionEffectType { GLOWING, SPEED, RESISTANCE }
    enum Particle { END_ROD, DUST, REVERSE_PORTAL;
        static final class DustOptions { DustOptions(Color color, float size) {} }
    }
    enum Sound { ENTITY_ENDERMAN_STARE, ENTITY_GENERIC_EXPLODE, BLOCK_AMETHYST_BLOCK_RESONATE, BLOCK_RESPAWN_ANCHOR_CHARGE }
    enum SoundCategory { HOSTILE }
    static final class BukkitTask { void cancel() {} }
    static final class EndRiftObjective { static final int REQUIRED_HUNT_CYCLES = 3; }
    static final class Color { static Color fromRGB(int r,int g,int b) { return new Color(); } }
    static final class DustOptions { DustOptions(Color color, float size) {} }
    record PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        int getDuration() { return duration; }
        int getAmplifier() { return amplifier; }
        boolean isAmbient() { return ambient; }
        boolean hasParticles() { return particles; }
        boolean hasIcon() { return icon; }
    }
    static class Entity {
        UUID id = UUID.randomUUID();
        final PersistentDataContainer pdc = new PersistentDataContainer();
        UUID getUniqueId() { return id; }
        PersistentDataContainer getPersistentDataContainer() { return pdc; }
        boolean isValid() { return true; }
        boolean isDead() { return false; }
    }
    static class Mob extends Entity {}
    static final class Skeleton extends Mob {}
    static final class PersistentDataContainer {
        final Map<Object, Object> values = new HashMap<>();
        void set(Object key, Object type, Object value) { values.put(key, value); }
    }
    static final class PersistentDataType { static Object STRING = new Object(), LONG = new Object(), INTEGER = new Object(); }
    static final class LargeFireball extends Entity { int chamber = 0; }
    static final class Player extends Entity {
        boolean wave = true, boss, online = true, dead;
        int chamber = -1, particles;
        final Map<PotionEffectType, Long> effects = new HashMap<>();
        final Map<PotionEffectType, PotionEffect> applied = new HashMap<>();
        final Map<PotionEffectType, Long> hiddenExpires = new HashMap<>();
        final Map<PotionEffectType, PotionEffect> hidden = new HashMap<>();
        boolean isOnline() { return online; }
        Location getLocation() { return new Location(); }
        Location getEyeLocation() { return new Location(); }
        void addPotionEffect(PotionEffect effect) {
            PotionEffect previous = getPotionEffect(effect.type());
            long expires = effect.duration() < 0 ? Long.MAX_VALUE : Bukkit.now + effect.duration() * 50L;
            // Bukkit/Minecraft keeps a stronger active effect, including a
            // weaker longer hidden lease, and infinite duration sorts longest.
            if (previous != null && previous.amplifier() > effect.amplifier()) {
                if (effect.duration() < 0 || previous.duration() >= 0 && effect.duration() > previous.duration()) {
                    hidden.put(effect.type(), effect); hiddenExpires.put(effect.type(), expires);
                }
                return;
            }
            if (previous != null && previous.amplifier() == effect.amplifier()
                    && (previous.duration() < 0 || effect.duration() >= 0 && effect.duration() < previous.duration())) return;
            effects.put(effect.type(), expires); applied.put(effect.type(), effect);
        }
        void removePotionEffect(PotionEffectType type) {
            effects.remove(type); applied.remove(type); hidden.remove(type); hiddenExpires.remove(type);
        }
        PotionEffect getPotionEffect(PotionEffectType type) {
            PotionEffect value = applied.get(type); long expires = effects.getOrDefault(type, 0L);
            if (value == null || expires <= Bukkit.now) {
                value = hidden.remove(type); expires = hiddenExpires.getOrDefault(type, 0L); hiddenExpires.remove(type);
                if (value == null || expires <= Bukkit.now) return null;
                applied.put(type, value); effects.put(type, expires);
            }
            if (expires == Long.MAX_VALUE) return new PotionEffect(type, -1, value.amplifier(), value.ambient(), value.particles(), value.icon());
            return new PotionEffect(type, (int) ((expires - Bukkit.now + 49L) / 50L), value.amplifier(),
                    value.ambient(), value.particles(), value.icon());
        }
        void playSound(Object... values) {}
        void spawnParticle(Object... values) { particles++; }
        boolean glowing(long now) { return effects.getOrDefault(PotionEffectType.GLOWING, 0L) > now; }
    }
    static final class Config { double arenaRadius() { return 20.0D; } }
    enum BlockFace { UP, DOWN }
    static final class Material { boolean isSolid() { return true; } }
    static final class Block {
        boolean isPassable() { return true; }
        Block getRelative(BlockFace face) { return this; }
        Material getType() { return new Material(); }
    }
    static final class Location {
        boolean inArena = true;
        double x, y, z;
        Location add(double x,double y,double z) {
            this.x += x; this.y += y; this.z += z; return this;
        }
        double getX() { return x; }
        int getBlockX() { return (int) Math.floor(x); }
        int getBlockY() { return (int) Math.floor(y); }
        double getZ() { return z; }
        int getBlockZ() { return (int) Math.floor(z); }
        Block getBlock() { return new Block(); }
        double distanceSquared(Location other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
        public Location clone() {
            Location copy = new Location(); copy.x = x; copy.y = y; copy.z = z; return copy;
        }
        World getWorld() { return new World(); }
    }
    static final class World { void playSound(Object... values) {} }
    static final class Bukkit {
        static long now;
        static List<Player> players = new ArrayList<>();
        static Player getPlayer(UUID id) { return players.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }
        static Entity getEntity(UUID id) { return null; }
    }
    static final class Wave4ObeliskRuntimeState {
        final UUID id = UUID.randomUUID();
        ObeliskFireDirectorPolicy.FireSequence fireLease;
        Location fireAim;
        long fireReleaseTick;
        int health = 3, maximum = 3;
        ObeliskStage stage = ObeliskStage.ACTIVE;
        UUID id() { return id; }
        UUID lastTargetUuid() { return null; }
        ObeliskStage stage() { return stage; }
        int health() { return health; }
        int maxHealth() { return maximum; }
        void health(int value) { health = value; }
        void stage(ObeliskStage value, long tick) { stage = value; }
        void destroyAt(long tick) {}
        Location base() { return new Location(); }
    }
    static final class RiftFireballRuntimeState {
        final UUID id = UUID.randomUUID();
        UUID reflector;
        UUID originObeliskId;
        boolean wave4 = true, wave7, consumed;
        long generation = 5L;
        ObeliskProjectilePolicy.State state = ObeliskProjectilePolicy.State.REFLECTED;
        UUID entityId() { return id; }
        UUID originObeliskId() { return originObeliskId; }
        UUID reflectorUuid() { return reflector; }
        boolean isWave4Obelisk() { return wave4; }
        boolean consumed() { return consumed; }
        long generation() { return generation; }
        ObeliskProjectilePolicy.State projectileState() { return state; }
        void consume() { consumed = true; state = ObeliskProjectilePolicy.State.CONSUMED; }
    }

    __METHODS__

    Mob addWaveTwoFighter(int group) {
        phase = EventPhase.WAVE_2;
        waveTwoGroupsSpawned = group;
        Mob fighter = new Mob();
        fighter.getPersistentDataContainer().set(keyWave, PersistentDataType.INTEGER, 2);
        fighter.getPersistentDataContainer().set(keyKind, PersistentDataType.STRING, EVENT_KIND_WAVE_MOB);
        fighter.getPersistentDataContainer().set(keyWaveSpawnGroup, PersistentDataType.INTEGER, group);
        waveTwoOutstandingEntityUuids.add(fighter.getUniqueId());
        return fighter;
    }
    boolean confirmWaveTwoFighterDeath(Mob fighter) {
        return retireWaveTwoRosterAfterConfirmedDeath(fighter);
    }
    void selectAt(long now) { Bukkit.now = now; updateMarkedTargetObjective(now); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        Waves24MechanicsHarness h = new Waves24MechanicsHarness();
        Player player = new Player(); Bukkit.players.add(player);
        String scenario = args[0]; long start = 1_000_000L;
        switch (scenario) {
            case "immediate-initial-mark" -> {
                h.startHunt(start); h.selectAt(start);
                check(player.id.equals(h.waveTwoMarkedPlayerUuid),
                        "the first mark starts immediately without an empty lead-in");
            }
            case "assignment-id-exhaustion" -> {
                h.phase = EventPhase.WAVE_2;
                h.waveTwoMarkCycle = WaveTwoHuntPolicy.MAX_MARK_ASSIGNMENT_ID;
                h.selectAt(start);
                check(h.phase == EventPhase.RECOVERY_REQUIRED
                                && "WAVE_TWO_MARK_ASSIGNMENT_ID_EXHAUSTED".equals(h.recoveryReason),
                        "exhausted packet identity must leave Wave 2 for explicit recovery");
                check(h.waveTwoMarkCycle == WaveTwoHuntPolicy.MAX_MARK_ASSIGNMENT_ID
                                && h.waveTwoMarkedPlayerUuid == null && h.saveCalls == 1,
                        "exhaustion must neither wrap, create another mark, nor skip its durable checkpoint");
                check(h.waveTwoRecoveryCheckpoint.get("wave2-mark-assignment-id")
                                .equals(Integer.toString(WaveTwoHuntPolicy.MAX_MARK_ASSIGNMENT_ID)),
                        "recovery checkpoint must retain the terminal identity");
                h.selectAt(start + 1_000L);
                check(h.waveTwoMarkCycle == WaveTwoHuntPolicy.MAX_MARK_ASSIGNMENT_ID
                                && h.waveTwoMarkedPlayerUuid == null,
                        "later timer ticks cannot stall in W2 or wrap into stale packet IDs");
            }
            case "full-window", "scaled-full-window" -> {
                if ("scaled-full-window".equals(scenario)) h.scalePlayers = 32;
                h.selectAt(start); long nearEnd = h.waveTwoMarkDeadlineMillis - 1L;
                h.selectAt(nearEnd);
                check(player.id.equals(h.waveTwoMarkedPlayerUuid), "mechanical mark must still be active near its end");
                check(player.glowing(nearEnd), "observer glow must cover the complete mechanical hunt window");
                int firstParticles = player.particles; h.selectAt(nearEnd);
                check(player.particles <= firstParticles + 8, "persistent state must remain bounded");
            }
            case "leaving-roster" -> {
                h.selectAt(start); player.wave = false; h.selectAt(start + 2_000L);
                check(h.waveTwoMarkedPlayerUuid == null, "out-of-arena/roster target must immediately leave hunt state");
                check(!player.glowing(start + 2_000L), "invalid target must lose event glow");
            }
            case "cleanup" -> {
                h.selectAt(start); h.finishHunt();
                check(h.waveTwoMarkedPlayerUuid == null && h.waveTwoMarkDeadlineMillis == 0L,
                        "completion/abort cleanup must release mechanical mark");
                check(!player.glowing(start), "completion/abort must clear event-owned observer glow");
                check(h.clientClears == 1, "completion/abort must explicitly clear marked player's edge state");
            }
            case "foreign-glow" -> {
                h.selectAt(start);
                Bukkit.now = start + 100L;
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1000, 1, false, false, true));
                h.finishHunt();
                check(player.glowing(start + 20_000L), "cleanup must preserve later unrelated glowing effect");
            }
            case "reveal-effects-cleanup" -> {
                h.selectAt(start);
                check(player.getPotionEffect(PotionEffectType.SPEED) == null
                                && player.getPotionEffect(PotionEffectType.RESISTANCE) == null,
                        "the glow-only mark must not add movement or damage-resistance effects");
                Bukkit.now = start + 100L; h.finishHunt();
                check(player.getPotionEffect(PotionEffectType.SPEED) == null,
                        "abort during reveal must remove event-owned speed");
                check(player.getPotionEffect(PotionEffectType.RESISTANCE) == null,
                        "abort during reveal must remove event-owned resistance");
            }
            case "infinite-glow-preserved" -> {
                Bukkit.now = start;
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, -1, 0, false, false, true));
                h.selectAt(start); Bukkit.now = start + 100L; h.finishHunt();
                check(player.getPotionEffect(PotionEffectType.GLOWING) != null
                        && player.getPotionEffect(PotionEffectType.GLOWING).getDuration() < 0,
                        "hunt cleanup cannot remove an unrelated infinite glow lease");
            }
            case "infinite-reveal-preserved" -> {
                Bukkit.now = start;
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, -1, 0, false, true, true));
                h.selectAt(start); Bukkit.now = start + 100L; h.finishHunt();
                check(player.getPotionEffect(PotionEffectType.SPEED).getDuration() < 0
                        && player.getPotionEffect(PotionEffectType.RESISTANCE).getDuration() < 0,
                        "reveal cleanup must preserve infinite foreign buff leases");
            }
            case "stronger-glow-cleanup" -> {
                Bukkit.now = start;
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 1, false, false, true));
                h.selectAt(start); Bukkit.now = start + 100L; h.finishHunt();
                Bukkit.now = start + 1_501L;
                check(player.getPotionEffect(PotionEffectType.GLOWING) == null,
                        "abort must not leave an event-owned hidden glow underneath a stronger previous lease");
            }
            case "death" -> {
                h.selectAt(start); Bukkit.now = start + 100L; player.dead = true; h.deathHunt(player);
                check(h.waveTwoMarkedPlayerUuid == null, "death listener must immediately release mechanical target");
                check(!player.glowing(Bukkit.now) && h.clientClears == 1, "death must clear glow and edge packet before next tick");
            }
            case "disconnect" -> {
                h.selectAt(start); Bukkit.now = start + 100L; h.quitHunt(player); player.online = false;
                check(h.waveTwoMarkedPlayerUuid == null, "quit listener must immediately release mechanical target");
                check(!player.glowing(Bukkit.now) && h.clientClears == 1, "quit must clear glow and edge packet before session removal");
            }
            case "replacement" -> {
                h.selectAt(start); player.wave = false; h.selectAt(start + 100L);
                Player second = new Player(); Bukkit.players.add(second);
                h.selectAt(start + 1_099L);
                check(h.waveTwoMarkedPlayerUuid == null, "replacement retains one second recovery warning");
                h.selectAt(start + 1_100L);
                check(second.id.equals(h.waveTwoMarkedPlayerUuid) && h.currentHuntCycles == 0,
                        "invalid target must be replaced without crediting a completed hunt cycle");
                check(!player.glowing(start + 1_100L) && second.glowing(start + 1_100L),
                        "observer mark must transfer to replacement only");
            }
            case "two-opening-batches-late-gate" -> {
                h.startHunt(start);
                h.waveSpawnSchedule = WaveTwoStagePolicy.split(
                        new WaveMechanicsPolicy.WaveCounts(9, 0, 0, 0, 0));
                h.waveSpawnWave = 2;
                h.spawnNextWaveGroup(new World(), new Location());
                h.selectAt(start);
                Mob fighter = h.addWaveTwoFighter(1);
                long firstClear = start + 10_000L;
                h.liveWaveEntitiesForWave2 = 0; Bukkit.now = firstClear;
                check(h.confirmWaveTwoFighterDeath(fighter), "confirmed opening-group death retires its UUID");
                h.tickCurrentHuntObjective(firstClear);
                check(h.currentHuntCycles == 1 && h.spawnedWaveGroups.size() == 1
                                && h.waveTwoGroupCleared,
                        "the first opening group clears but the second batch waits for mark two");
                check(h.waveTwoMarkedPlayerUuid != null,
                        "clearing a group does not interrupt the independent 45-second mark period");

                Bukkit.now = start + 45_000L;
                h.tickCurrentHuntObjective(Bukkit.now);
                check(h.waveTwoCompletedMarkPeriods == 1 && h.spawnedWaveGroups.size() == 1,
                        "the first completed mark releases the second substantial batch, not late reinforcements");
                Bukkit.now++;
                h.tickCurrentHuntObjective(Bukkit.now);
                check(h.spawnedWaveGroups.size() == 2 && h.waveSpawnGroupIndex == 2,
                        "the second substantial batch starts at the beginning of mark two");

                fighter = h.addWaveTwoFighter(2);
                long secondClear = start + 55_000L;
                Bukkit.now = secondClear;
                check(h.confirmWaveTwoFighterDeath(fighter), "confirmed second opening-group death retires its UUID");
                h.tickCurrentHuntObjective(secondClear);
                check(h.currentHuntCycles == 2 && h.spawnedWaveGroups.size() == 2,
                        "late reinforcements stay locked until two full mark periods finish");
                Bukkit.now = start + 90_000L;
                h.tickCurrentHuntObjective(Bukkit.now);
                check(h.waveTwoCompletedMarkPeriods == 2 && h.spawnedWaveGroups.size() == 2,
                        "the second period is counted without releasing a late group in the same tick");
                Bukkit.now++;
                h.tickCurrentHuntObjective(Bukkit.now);
                check(h.spawnedWaveGroups.size() == 3 && h.waveSpawnGroupIndex == 3,
                        "the first late reinforcement starts only after both complete marks");
                check(h.spawnedWaveGroups.get(2).total() <= WaveTwoStagePolicy.LATE_REINFORCEMENT_BUDGET,
                        "the single final reinforcement stays within its eight-mob budget");
            }
            case "unloaded-group-roster" -> {
                h.startHunt(start); h.selectAt(start);
                h.waveSpawnSchedule = WaveTwoStagePolicy.split(
                        new WaveMechanicsPolicy.WaveCounts(9, 0, 0, 0, 0));
                h.waveSpawnWave = 2;
                h.spawnNextWaveGroup(new World(), new Location());
                UUID outstandingId = UUID.randomUUID();
                h.waveTwoOutstandingEntityUuids.add(outstandingId);
                h.phase = EventPhase.WAVE_2; h.waveTwoGroupsSpawned = 1; h.officialAttempt = true;
                Mob confirmedDead = new Mob(); confirmedDead.id = outstandingId;
                confirmedDead.getPersistentDataContainer().set(h.keyWave, PersistentDataType.INTEGER, 2);
                confirmedDead.getPersistentDataContainer().set(h.keyKind, PersistentDataType.STRING, EVENT_KIND_WAVE_MOB);
                confirmedDead.getPersistentDataContainer().set(h.keyWaveSpawnGroup, PersistentDataType.INTEGER, 1);
                h.liveWaveEntitiesForWave2 = 0;
                long unloadedAt = start + 30_000L;
                h.tickCurrentHuntObjective(unloadedAt);
                check(!h.waveTwoGroupCleared && h.currentHuntCycles == 0,
                        "an unloaded but outstanding group UUID must not count as a cleared group");
                check(h.spawnedWaveGroups.size() == 1,
                        "the next group must not start its post-clear pause while any fighter is unloaded");
                check(h.retireWaveTwoRosterAfterConfirmedDeath(confirmedDead)
                                && h.waveTwoOutstandingEntityUuids.isEmpty() && h.saveCalls == 1,
                        "only a confirmed current-group death removes the final durable UUID");
                check(h.retireWaveTwoRosterAfterConfirmedDeath(confirmedDead) && h.saveCalls == 1,
                        "duplicate death callbacks do not checkpoint or retire the fighter twice");
                h.tickCurrentHuntObjective(unloadedAt + 1L);
                check(h.waveTwoGroupCleared && h.currentHuntCycles == 1
                                && h.spawnedWaveGroups.size() == 1,
                        "the group clears immediately after its final UUID dies, while mark pacing holds the next batch");
                h.tickCurrentHuntObjective(unloadedAt + 2L);
                check(h.spawnedWaveGroups.size() == 1,
                        "an additional tick before mark two cannot start the second opening batch");
                long secondMark = start + 45_000L;
                Bukkit.now = secondMark;
                h.tickCurrentHuntObjective(secondMark);
                Bukkit.now++;
                h.tickCurrentHuntObjective(Bukkit.now);
                check(h.spawnedWaveGroups.size() == 2,
                        "the second opening batch starts after mark one completes");
            }
            case "sandbox-confirmed-death" -> {
                Mob fighter = h.addWaveTwoFighter(1);
                h.phase = EventPhase.READY_FOR_PLAYERS;
                h.activeWave = 2;
                h.testWaveFrontVisualMode = true;
                check(h.retireWaveTwoRosterAfterConfirmedDeath(fighter),
                        "a confirmed sandbox Wave 2 death retires its current-group UUID without changing official phase");
                check(h.waveTwoOutstandingEntityUuids.isEmpty() && h.saveCalls == 0,
                        "sandbox group retirement clears only its tracked UUID and does not persist official state");
            }
            case "forty-five-second-mark-retention" -> {
                Player second = new Player(); Bukkit.players.add(second);
                h.waveSpawnSchedule = List.of(new WaveMechanicsPolicy.WaveCounts(3, 0, 0, 0, 0));
                h.waveSpawnGroupIndex = 1;
                h.liveWaveEntitiesForWave2 = 1;
                h.addWaveTwoFighter(1);
                h.startHunt(start); h.selectAt(start);
                UUID firstTarget = h.waveTwoMarkedPlayerUuid;
                long firstMarkEnd = start + 45_000L;
                h.tickCurrentHuntObjective(firstMarkEnd - 1L);
                check(firstTarget.equals(h.waveTwoMarkedPlayerUuid) && h.currentHuntCycles == 0,
                        "Wave 2 mark must remain on its player for the full forty-five-second period");
            }
            case "forty-five-second-mark-rotation" -> {
                Player second = new Player(); Bukkit.players.add(second);
                h.waveSpawnSchedule = List.of(new WaveMechanicsPolicy.WaveCounts(3, 0, 0, 0, 0));
                h.waveSpawnGroupIndex = 1;
                h.liveWaveEntitiesForWave2 = 1;
                h.addWaveTwoFighter(1);
                h.startHunt(start); h.selectAt(start);
                UUID firstTarget = h.waveTwoMarkedPlayerUuid;
                h.tickCurrentHuntObjective(start + 45_000L - 1L);
                check(firstTarget.equals(h.waveTwoMarkedPlayerUuid) && h.currentHuntCycles == 0,
                        "the first mark remains stable until its exact forty-five-second deadline");
                h.tickCurrentHuntObjective(start + 45_000L);
                UUID secondTarget = h.waveTwoMarkedPlayerUuid;
                check(secondTarget != null && !secondTarget.equals(firstTarget)
                                && h.waveTwoCompletedMarkPeriods == 1,
                        "the first complete mark period rotates to a different eligible player");
                h.tickCurrentHuntObjective(start + 90_000L - 1L);
                check(secondTarget.equals(h.waveTwoMarkedPlayerUuid),
                        "the second mark remains stable for its full forty-five-second period");
                h.tickCurrentHuntObjective(start + 90_000L);
                check(firstTarget.equals(h.waveTwoMarkedPlayerUuid)
                                && h.waveTwoCompletedMarkPeriods == 2 && h.currentHuntCycles == 0,
                        "the second complete period rotates fairly and is tracked separately from kills");
            }
            case "wave2-edge-spawns" -> {
                for (int index = 0; index < 3; index++) {
                    Location spawn = h.waveTwoSpawnLocation(index);
                    double radius = Math.hypot(spawn.getX(), spawn.getZ());
                    check(radius >= 14.0D && radius <= 20.0D,
                            "Wave 2 stage packs must spawn in the outer ring of the twenty-block arena");
                }
            }
            case "wave2-edge-spawns-respect-bounds" -> {
                h.arenaMinX = -1; h.arenaMaxX = 1;
                h.arenaMinY = -1; h.arenaMaxY = 1;
                h.arenaMinZ = -1; h.arenaMaxZ = 1;
                check(h.waveTwoSpawnLocation(0) == null,
                        "Wave 2 must refuse an outer-ring spawn when the configured arena AABB excludes it");
            }
            case "invalid-at-deadline-death", "invalid-at-deadline-disconnect", "invalid-at-deadline-arena" -> {
                h.waveSpawnGroupIndex = 1; h.liveWaveEntitiesForWave2 = 1;
                h.addWaveTwoFighter(1);
                h.selectAt(start); long deadline = h.waveTwoMarkDeadlineMillis;
                if (scenario.endsWith("death")) player.dead = true;
                if (scenario.endsWith("disconnect")) player.online = false;
                if (scenario.endsWith("arena")) player.wave = false;
                Bukkit.now = deadline; h.tickCurrentHuntObjective(deadline);
                check(h.currentHuntCycles == 0, "invalid target must not be credited by expiry before roster validation");
                check(h.waveTwoMarkedPlayerUuid == null, "invalid mark must clear even at the exact deadline");
                Player second = new Player(); Bukkit.players.add(second);
                Bukkit.now = deadline + 1_000L; h.tickCurrentHuntObjective(Bukkit.now);
                check(second.id.equals(h.waveTwoMarkedPlayerUuid), "replacement must get its own full hunt window");
            }
            case "wave-roster-launch" -> {
                var state = new Wave4ObeliskRuntimeState();
                check(!h.launchWave4Fireball(state), "projectile cannot launch before its warning");
                h.eventTickCounter += 19;
                check(!h.launchWave4Fireball(state), "warning must last all twenty ticks");
                h.eventTickCounter++;
                check(h.launchWave4Fireball(state), "wave participant must be targetable despite stale boss roster after warning");
                check(player.id.equals(h.lastLaunchTarget), "launched projectile must use canonical wave participant");
            }
            case "charge-disconnect" -> {
                var state = new Wave4ObeliskRuntimeState();
                check(!h.launchWave4Fireball(state), "first call starts the warning");
                player.online = false; h.eventTickCounter += 20;
                check(!h.launchWave4Fireball(state), "a departed target cannot receive a stale charged shot");
            }
            case "charge-generation" -> {
                var state = new Wave4ObeliskRuntimeState();
                check(!h.launchWave4Fireball(state), "first call starts the warning");
                h.generation++; h.waveCombatCoordinator.begin(h.generation); h.eventTickCounter += 20;
                check(!h.launchWave4Fireball(state), "a stale charge must not fire into the next generation");
            }
            case "boss-only-excluded" -> {
                player.wave = false; player.boss = true;
                check(!h.launchWave4Fireball(new Wave4ObeliskRuntimeState()), "boss roster member outside wave roster must not receive Wave 4 shot");
            }
            case "wave-reflection" -> {
                check(h.reflectionAdmission(new LargeFireball(), new RiftFireballRuntimeState(), player),
                        "wave participant must be allowed to reflect its Wave 4 projectile");
                player.wave = false; player.boss = true;
                check(!h.reflectionAdmission(new LargeFireball(), new RiftFireballRuntimeState(), player),
                        "boss-only participant must not reflect Wave 4 projectile");
            }
            case "sandbox-outsider-reflection" -> {
                h.testWave4ObeliskMode = true; h.phase = EventPhase.READY_FOR_PLAYERS; player.wave = false;
                check(!h.reflectionAdmission(new LargeFireball(), new RiftFireballRuntimeState(), player),
                        "sandbox reflection must use admitted wave roster despite physical arena presence");
            }
            case "boss-reflection-preserved" -> {
                player.wave = false; player.boss = true;
                RiftFireballRuntimeState fireball = new RiftFireballRuntimeState(); fireball.wave4 = false;
                check(h.reflectionAdmission(new LargeFireball(), fireball, player),
                        "non-Wave4 encounter reflection predicate must remain unchanged");
            }
            case "wave7-reflection-boundary" -> {
                RiftFireballRuntimeState fireball = new RiftFireballRuntimeState(); fireball.wave4 = false; fireball.wave7 = true;
                player.boss = true;
                check(!h.reflectionAdmission(new LargeFireball(), fireball, player), "Wave 7 must retain chamber restriction");
                player.chamber = 0;
                check(h.reflectionAdmission(new LargeFireball(), fireball, player), "assigned Wave 7 chamber participant must remain allowed");
            }
            case "wave-reflected-hit" -> {
                RiftFireballRuntimeState fireball = new RiftFireballRuntimeState(); fireball.reflector = player.id;
                Wave4ObeliskRuntimeState obelisk = new Wave4ObeliskRuntimeState();
                fireball.originObeliskId = obelisk.id;
                h.applyCurrentReflectedObeliskHit(obelisk, fireball);
                check(obelisk.health == 2, "current wave reflector must damage obelisk despite stale boss roster");
                h.applyCurrentReflectedObeliskHit(obelisk, fireball);
                check(obelisk.health == 2 && h.hitReactions == 1 && h.visualUpdates == 1,
                        "duplicate impact callbacks must consume only one health transaction");
            }
            case "stale-hit" -> {
                player.boss = true;
                RiftFireballRuntimeState fireball = new RiftFireballRuntimeState(); fireball.reflector = player.id; fireball.generation = 4L;
                Wave4ObeliskRuntimeState obelisk = new Wave4ObeliskRuntimeState();
                fireball.originObeliskId = obelisk.id;
                h.applyCurrentReflectedObeliskHit(obelisk, fireball);
                check(obelisk.health == 3 && !fireball.consumed, "stale generation cannot consume health");
            }
            case "wrong-source-reflected-hit" -> {
                RiftFireballRuntimeState fireball = new RiftFireballRuntimeState(); fireball.reflector = player.id;
                Wave4ObeliskRuntimeState source = new Wave4ObeliskRuntimeState();
                Wave4ObeliskRuntimeState other = new Wave4ObeliskRuntimeState();
                fireball.originObeliskId = source.id;
                h.applyCurrentReflectedObeliskHit(other, fireball);
                check(other.health == 3 && !fireball.consumed,
                        "a reflected projectile must not damage or consume against a non-source obelisk");
                check(h.hitReactions == 0 && h.visualUpdates == 0,
                        "a wrong-source impact must not render a hit or update obelisk visuals");
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        System.out.println(scenario + " OK");
    }
}
'''
    harness = harness.replace("__FIELDS__", fields).replace("__CONSTANTS__", constants)
    # Detach the wall clock alongside Bukkit so expiry/restore decisions see
    # the same controlled time as the potion boundary.
    harness = harness.replace("__METHODS__", "\n".join(methods).replace("System.currentTimeMillis()", "Bukkit.now"))
    output = tmp_path_factory.mktemp("waves24-mechanics")
    java = output / "Waves24MechanicsHarness.java"
    java.write_text(harness, encoding="utf-8")
    dependencies = [DOMAIN / f"{name}.java" for name in (
        "WaveScalingPolicy", "WaveMechanicsPolicy", "PressureBudgetController", "ObeliskIntegrityPolicy", "ObeliskProjectilePolicy",
        "ObeliskFireDirectorPolicy", "ObeliskScalingPolicy", "RiftFireballReflectionPolicy", "WaveTwoStagePolicy",
        "WaveTwoHuntPolicy", "WaveTwoPhantomMaterializationPolicy", "WaveTwoPhantomLayoutPolicy",
        "WaveTwoEntityRecoveryPolicy",
    )]
    dependencies.append(DOMAIN.parent / 'runtime/WaveCombatCoordinator.java')
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java), *map(str, dependencies)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.fixture(scope="module")
def wave2_entity_load_harness(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    methods = [_declaration(source, signature) for signature in (
        "private boolean isObsoleteWaveEliteComposition(Entity entity)",
        "private boolean isApprovedWaveTwoFinalElite(Entity entity)",
        "private boolean isKnownLegacyWave7Elite(Entity entity)",
        "private boolean isOfficialNumberedWaveMob(Entity entity)",
        "private boolean isOfficialWaveLifecycleMob(Entity entity)",
        "private boolean isOfficialWaveSixPressureMob(Entity entity)",
        "private boolean isActiveOfficialWavePhase(int wave)",
        "private boolean isOfficialEntity(Entity entity)",
        "private boolean isWaveCombatKind(String kind)",
        "private long readEntityGeneration(Entity entity)",
        "private void restoreEliteSpellRuntime(Entity entity, long now)",
        "private EndRiftAiPolicy.MiniBossSpell miniBossSpellFor(EliteAbilityPolicy.Ability ability)",
        "private void suspendWaveTwoRecoveryEntity(Entity entity)",
        "public void onPersistedCombatEntitiesLoad(EntitiesLoadEvent event)",
        "private boolean isLiveOwnedEntity(UUID entityUuid)",
        "private boolean ownedBySession(Entity entity, String expectedEventId, long expectedGeneration)",
        "private boolean isWaveTwoOrdinaryMob(Entity entity)",
        "private WaveTwoEntityRecoveryPolicy.LoadDisposition waveTwoEntityLoadDisposition(Entity entity)",
        "private boolean isWaveTwoRecoveryFenced(Entity entity)",
        "private void stampCurrentWaveTwoCleanupEpochIfMissing(Entity entity)",
        "private boolean rehydrateWaveTwoEntity(Entity entity)",
    )]
    harness = r'''
import java.util.*;
import me.copimine.endevent.domain.EliteAbilityPolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;
import me.copimine.endevent.domain.WaveTwoEntityRecoveryPolicy;
import me.copimine.endevent.domain.WaveEliteCompositionPolicy;
import me.copimine.endevent.domain.WaveTwoStagePolicy;
import me.copimine.endevent.runtime.WaveMobLifecycleRegistry;

public final class WaveTwoEntityLoadHarness {
    static final int MAX_EVENT_WAVE = 7;
    static final String EVENT_KIND_WAVE_MOB = "wave_mob", EVENT_KIND_ELITE = "elite";
    static final String EVENT_KIND_WAVE_GUARDIAN = "wave_guardian";
    static final String EVENT_KIND_REALITY_SPLIT_TRIAL = "reality_split_trial";
    static final String EVENT_KIND_RITUAL_CASTER = "ritual_caster", EVENT_KIND_RITUAL_GUARD = "ritual_guard";
    static final Object LONG = new Object(), INTEGER = new Object(), STRING = new Object(), BYTE = new Object();
    static final class PersistentDataType {
        static final Object LONG = WaveTwoEntityLoadHarness.LONG;
        static final Object INTEGER = WaveTwoEntityLoadHarness.INTEGER;
        static final Object STRING = WaveTwoEntityLoadHarness.STRING;
        static final Object BYTE = WaveTwoEntityLoadHarness.BYTE;
    }
    enum EventPhase { WAVE_1, WAVE_2, WAVE_3, WAVE_4, WAVE_5, WAVE_6, WAVE_7, RECOVERY_REQUIRED }
    enum EventPriority { MONITOR }
    @interface EventHandler { EventPriority priority(); }
    static final class EntitiesLoadEvent {
        final List<Entity> entities;
        final Chunk chunk = new Chunk();
        EntitiesLoadEvent(List<Entity> entities) { this.entities = entities; }
        List<Entity> getEntities() { return entities; }
        Chunk getChunk() { return chunk; }
    }
    static final class World { UUID getUID() { return new UUID(0L, 1L); } }
    static final class Chunk {
        final World world = new World();
        World getWorld() { return world; }
        int getX() { return 0; }
        int getZ() { return 0; }
    }
    static final class PersistentDataContainer {
        final Map<Object,Object> values = new HashMap<>();
        final Map<Object,Object> types = new HashMap<>();
        void set(Object key, Object type, Object value) { values.put(key, value); types.put(key, type); }
        void remove(Object key) { values.remove(key); types.remove(key); }
        boolean has(Object key, Object type) { return values.containsKey(key) && types.get(key) == type; }
        Set<Object> getKeys() { return new HashSet<>(values.keySet()); }
        @SuppressWarnings("unchecked") <T> T getOrDefault(Object key, Object type, T fallback) {
            return has(key, type) ? (T) values.get(key) : fallback;
        }
    }
    static class Entity {
        UUID id = UUID.randomUUID();
        final PersistentDataContainer pdc = new PersistentDataContainer();
        boolean valid = true, dead, removed;
        String customName;
        UUID getUniqueId() { return id; }
        PersistentDataContainer getPersistentDataContainer() { return pdc; }
        String getCustomName() { return customName; }
        boolean isValid() { return valid; }
        boolean isDead() { return dead; }
        void remove() { removed = true; valid = false; }
    }
    static final class Pathfinder {
        boolean stopped;
        void stopPathfinding() { stopped = true; }
    }
    static class Mob extends Entity {
        boolean ai = true, aware = true;
        Object target = new Object();
        final Pathfinder pathfinder = new Pathfinder();
        void setTarget(Object next) { target = next; }
        void setAI(boolean enabled) { ai = enabled; }
        void setAware(boolean enabled) { aware = enabled; }
        Pathfinder getPathfinder() { return pathfinder; }
    }
    static final class Skeleton extends Mob {}
    static final class Enderman extends Mob {}
    static final class Spider extends Mob {}
    final Object keyWave = new Object(), keyKind = new Object(), keyEventGeneration = new Object();
    final Object keyGeneration = new Object(), keyWaveSpawnGroup = new Object(), keyEventId = new Object();
    final Object keyOfficial = new Object(), keyRitualConverted = new Object();
    final Object keyWaveTwoFinalElite = new Object();
    final Object keyWaveTwoCleanupEpoch = new Object(), keyTrialRole = new Object();
    final Object keyMiniBossSpell = new Object(), keyLootProfile = new Object();
    String eventId = "event";
    long generation = 10L;
    int activeWave = 2;
    long waveTwoCleanupEpoch;
    int waveTwoGroupsSpawned = 2;
    boolean waveTwoGroupCleared;
    EventPhase phase = EventPhase.WAVE_2;
    String recoveryReason = "";
    Set<UUID> waveTwoOutstandingEntityUuids = new LinkedHashSet<>();
    Set<UUID> waveTwoCleanupEntityUuids = new LinkedHashSet<>();
    Set<UUID> waveTwoRecoveryFencedEntityUuids = new LinkedHashSet<>();
    Map<UUID,Entity> ownedEntities = new LinkedHashMap<>();
    Map<UUID,Long> ritualConvertedMobUntilMillis = new HashMap<>();
    boolean ritualWaveActive() { return activeWave == 6 && phase == EventPhase.WAVE_6; }
    void removeRitualEntity(UUID id) {
        unregisterOwnedEntity(id, "ritual-entity-cleanup");
        ritualConvertedMobUntilMillis.remove(id);
    }
    Map<UUID,EndRiftAiPolicy.MiniBossSpell> miniBossSpells = new LinkedHashMap<>();
    Map<UUID,Long> nextMiniBossSpellMillis = new LinkedHashMap<>();
    final WaveMobLifecycleRegistry unloadedWaveMobs = new WaveMobLifecycleRegistry();
    int registerCalls, tacticCalls, ensureAiCalls, unregisterCalls, removalDiagnostics;
    String readString(Entity entity, Object key) {
        Object value = entity.pdc.values.get(key); return value instanceof String s ? s : "";
    }
    int readInt(Entity entity, Object key, int fallback) {
        Object value = entity.pdc.values.get(key); return value instanceof Number n ? n.intValue() : fallback;
    }
    boolean isWaveCommander(Entity entity) { return false; }
    boolean ownedByEvent(Entity entity, String expected) {
        return expected.equals(readString(entity, keyEventId));
    }
    void emitUntrackedEntityRemovalDiagnostic(Entity entity, String reason) { removalDiagnostics++; }
    void unregisterOwnedEntity(UUID uuid, String reason) { unregisterCalls++; ownedEntities.remove(uuid); }
    void registerLoadedRiftGiantOwners(List<Entity> entities) { }
    boolean preserveRiftGiantOwnerInRecovery(Entity entity) { return false; }
    Set<?> loadedRiftGiantChunkBatch(EntitiesLoadEvent event) { return Set.of(); }
    Set<UUID> retireOrphanedRiftGiantSummons(List<Entity> entities, Set<?> chunks) { return Set.of(); }
    void registerOwnedEntity(Entity entity) { registerCalls++; ownedEntities.put(entity.id, entity); }
    void combatTactic(Entity entity, int seed) { tacticCalls++; }
    void ensureEventCombatAi(Entity entity) {
        ensureAiCalls++;
        if (isWaveTwoRecoveryFenced(entity)) {
            suspendWaveTwoRecoveryEntity(entity);
            return;
        }
        if (entity instanceof Mob mob) { mob.ai = true; mob.aware = true; }
    }
    void tagMiniBossSpell(Entity entity, EndRiftAiPolicy.MiniBossSpell spell) {
        entity.pdc.set(keyMiniBossSpell, STRING, spell.id());
    }
    void forcePhase(EventPhase next, String reason) { phase = next; recoveryReason = reason; }
    Mob taggedMob(int group, long taggedGeneration) {
        Mob mob = new Mob();
        mob.pdc.set(keyWave, INTEGER, 2);
        mob.pdc.set(keyKind, STRING, EVENT_KIND_WAVE_MOB);
        mob.pdc.set(keyEventGeneration, LONG, taggedGeneration);
        mob.pdc.set(keyWaveTwoCleanupEpoch, LONG, waveTwoCleanupEpoch);
        mob.pdc.set(keyWaveSpawnGroup, INTEGER, group);
        mob.pdc.set(keyEventId, STRING, eventId);
        return mob;
    }
    Skeleton taggedFinalElite(long taggedGeneration) {
        Skeleton mob = new Skeleton();
        mob.pdc.set(keyWave, INTEGER, 2);
        mob.pdc.set(keyKind, STRING, EVENT_KIND_ELITE);
        mob.pdc.set(keyWaveTwoFinalElite, STRING, "group-3-skeleton-v1");
        mob.pdc.set(keyEventGeneration, LONG, taggedGeneration);
        mob.pdc.set(keyWaveTwoCleanupEpoch, LONG, waveTwoCleanupEpoch);
        mob.pdc.set(keyWaveSpawnGroup, INTEGER, 3);
        mob.pdc.set(keyEventId, STRING, eventId);
        return mob;
    }
    Skeleton legacyFinalEliteWithoutCleanupEpoch(long taggedGeneration) {
        Skeleton mob = taggedFinalElite(taggedGeneration);
        mob.pdc.values.remove(keyWaveTwoCleanupEpoch);
        mob.pdc.types.remove(keyWaveTwoCleanupEpoch);
        return mob;
    }
    Mob copyChunkEntity(Mob original) {
        Mob copy = new Mob(); copy.id = original.id;
        copy.pdc.values.putAll(original.pdc.values); copy.pdc.types.putAll(original.pdc.types);
        return copy;
    }
    boolean indexPersistedNamedWave7Actor(Entity entity) { return false; }
    __METHODS__
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        String scenario = args[0];
        WaveTwoEntityLoadHarness h = new WaveTwoEntityLoadHarness();
        switch (scenario) {
            case "duplicate-load" -> {
                Mob mob = h.taggedMob(2, h.generation); h.waveTwoOutstandingEntityUuids.add(mob.id);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(mob)));
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(mob)));
                check(h.ownedEntities.get(mob.id) == mob && h.registerCalls == 1,
                        "duplicate chunk load must keep the canonical owned wrapper");
                check(h.tacticCalls == 1 && h.ensureAiCalls == 1,
                        "duplicate chunk load must not initialize combat AI twice");
            }
            case "restart-load" -> {
                Mob mob = h.taggedMob(2, h.generation);
                String snapshotRoster = WaveTwoEntityRecoveryPolicy.encode(Set.of(mob.id),
                        WaveTwoEntityRecoveryPolicy.MAX_OUTSTANDING_UUIDS);
                h.waveTwoOutstandingEntityUuids = new LinkedHashSet<>(WaveTwoEntityRecoveryPolicy.decode(
                        snapshotRoster, WaveTwoEntityRecoveryPolicy.MAX_OUTSTANDING_UUIDS));
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(mob)));
                check(h.ownedEntities.get(mob.id) == mob && h.waveTwoOutstandingEntityUuids.contains(mob.id),
                        "a W2 group member restored from the durable roster re-enters ownership after restart");
            }
            case "approved-group-3-elite-load" -> {
                h.waveTwoGroupsSpawned = 3;
                Skeleton elite = h.taggedFinalElite(h.generation);
                h.waveTwoOutstandingEntityUuids.add(elite.id);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(elite)));
                check(!elite.removed && h.ownedEntities.get(elite.id) == elite,
                        "the approved group-3 skeleton elite survives obsolete cleanup and rehydrates");
            }
            case "legacy-final-elite-missing-epoch-load" -> {
                h.waveTwoCleanupEpoch = 4L;
                h.waveTwoGroupsSpawned = 3;
                Skeleton elite = h.legacyFinalEliteWithoutCleanupEpoch(h.generation);
                h.waveTwoOutstandingEntityUuids.add(elite.id);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(elite)));
                check(!elite.removed && h.ownedEntities.get(elite.id) == elite
                                && elite.pdc.getOrDefault(h.keyWaveTwoCleanupEpoch, LONG, -1L) == 4L,
                        "an outstanding legacy group-3 elite with no epoch migrates to the durable epoch");
            }
            case "legacy-final-elite-wrong-epoch-type-load" -> {
                h.waveTwoCleanupEpoch = 4L;
                h.waveTwoGroupsSpawned = 3;
                Skeleton elite = h.taggedFinalElite(h.generation);
                elite.pdc.set(h.keyWaveTwoCleanupEpoch, INTEGER, 4);
                h.waveTwoOutstandingEntityUuids.add(elite.id);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(elite)));
                check(elite.removed && !h.ownedEntities.containsKey(elite.id),
                        "a malformed typed epoch is stale data, not a missing legacy epoch eligible for migration");
            }
            case "unmarked-wave-2-elite-load" -> {
                Skeleton elite = h.taggedFinalElite(h.generation);
                elite.pdc.set(h.keyWaveTwoFinalElite, STRING, "");
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(elite)));
                check(elite.removed && !h.ownedEntities.containsKey(elite.id),
                        "unmarked Wave 2 elites remain obsolete");
            }
            case "stale-group-load" -> {
                Mob stale = h.taggedMob(1, h.generation);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(stale)));
                check(stale.removed && !h.ownedEntities.containsKey(stale.id),
                        "an earlier W2 group loaded after restart is removed");
            }
            case "stale-cleanup-epoch-load" -> {
                h.waveTwoCleanupEpoch = 4L;
                Mob stale = h.taggedMob(2, h.generation);
                stale.pdc.set(h.keyWaveTwoCleanupEpoch, LONG, 3L);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(stale)));
                check(stale.removed && !h.ownedEntities.containsKey(stale.id),
                        "a W2 entity from a retired cleanup epoch is removed on load");
            }
            case "future-cleanup-epoch-load" -> {
                Mob unexpected = h.taggedMob(2, h.generation);
                unexpected.pdc.set(h.keyWaveTwoCleanupEpoch, LONG, 1L);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(unexpected)));
                check(h.phase == EventPhase.RECOVERY_REQUIRED && !unexpected.removed
                                && !unexpected.ai && !unexpected.aware && unexpected.target == null
                                && unexpected.pathfinder.stopped,
                        "a W2 entity ahead of the durable cleanup epoch is suspended for recovery");
            }
            case "unlisted-current-load" -> {
                Mob unexpected = h.taggedMob(2, h.generation);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(unexpected)));
                check(h.phase == EventPhase.RECOVERY_REQUIRED && !unexpected.removed
                                && !h.ownedEntities.containsKey(unexpected.id)
                                && !unexpected.ai && !unexpected.aware && unexpected.target == null
                                && unexpected.pathfinder.stopped,
                        "an unlisted current-group mob fails closed and stops native AI without rejoining the roster");
            }
            case "mismatch-fence-duplicate-chunk-reload" -> {
                Mob unexpected = h.taggedMob(2, h.generation);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(unexpected)));
                check(h.phase == EventPhase.RECOVERY_REQUIRED
                                && h.waveTwoRecoveryFencedEntityUuids.contains(unexpected.id),
                        "a mismatched current-generation entity enters the explicit recovery fence");
                for (int tick = 0; tick < 5; tick++) {
                    unexpected.ai = true; unexpected.aware = true;
                    h.ensureEventCombatAi(unexpected);
                    check(!unexpected.ai && !unexpected.aware && unexpected.target == null
                                    && unexpected.pathfinder.stopped,
                            "ordinary AI restoration cannot reactivate a rejected fighter during recovery");
                }
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(unexpected)));
                Mob chunkReload = h.copyChunkEntity(unexpected);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(chunkReload)));
                check(h.phase == EventPhase.RECOVERY_REQUIRED && !chunkReload.ai && !chunkReload.aware
                                && chunkReload.target == null && chunkReload.pathfinder.stopped
                                && !h.ownedEntities.containsKey(unexpected.id),
                        "duplicate load and chunk reload keep the mismatched entity fenced and unregistered");

                h.phase = EventPhase.WAVE_2;
                h.generation++;
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(unexpected)));
                check(unexpected.removed && !h.ownedEntities.containsKey(unexpected.id),
                        "an old-generation mismatched entity cannot join a repaired generation");

                Mob legitimate = h.taggedMob(2, h.generation);
                h.waveTwoOutstandingEntityUuids.add(legitimate.id);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(legitimate)));
                Mob duplicateChunkWrapper = h.copyChunkEntity(legitimate);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(duplicateChunkWrapper)));
                check(h.phase == EventPhase.WAVE_2 && !legitimate.removed && legitimate.ai
                                && h.ownedEntities.get(legitimate.id) == legitimate
                                && h.registerCalls == 1 && h.tacticCalls == 1,
                        "a repaired current-generation roster member rehydrates once across a duplicate chunk wrapper");
            }
            case "rotated-event-cleanup-tombstone" -> {
                h.eventId = "old-event";
                Mob stale = h.taggedMob(2, h.generation);
                h.eventId = "new-event";
                h.waveTwoCleanupEntityUuids.add(stale.id);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(stale)));
                check(stale.removed && !h.ownedEntities.containsKey(stale.id),
                        "an exact cleanup tombstone removes its old-event mob after Core ID rotation");
            }
            case "foreign-event-untombstoned" -> {
                h.eventId = "old-event";
                Mob foreign = h.taggedMob(2, h.generation);
                h.eventId = "new-event";
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(foreign)));
                check(!foreign.removed && !h.ownedEntities.containsKey(foreign.id)
                                && h.phase == EventPhase.WAVE_2,
                        "a foreign-event mob without a matching cleanup tombstone is ignored");
            }
            case "foreign-event-stale-generation" -> {
                h.eventId = "old-event";
                Mob foreign = h.taggedMob(2, h.generation - 1L);
                h.eventId = "new-event";
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(foreign)));
                check(!foreign.removed && !h.ownedEntities.containsKey(foreign.id)
                                && h.phase == EventPhase.WAVE_2,
                        "a foreign-event mob with a stale generation is ignored rather than removed");
            }
            case "wave7-ordinary-guardian" -> {
                Entity guardian = new Entity();
                guardian.pdc.set(h.keyWave, INTEGER, 7);
                guardian.pdc.set(h.keyKind, STRING, "wave_guardian");
                guardian.pdc.set(h.keyEventId, STRING, h.eventId);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(guardian)));
                check(!guardian.removed,
                        "an ordinary Wave 7 guardian survives obsolete-composition cleanup");
            }
            case "wave7-marked-legacy-elite" -> {
                Entity elite = new Entity();
                elite.pdc.set(h.keyWave, INTEGER, 7);
                elite.pdc.set(h.keyKind, STRING, "wave_guardian");
                elite.pdc.set(h.keyEventId, STRING, h.eventId);
                elite.pdc.set(h.keyMiniBossSpell, STRING, "DRAGON_BREATH");
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(elite)));
                check(elite.removed,
                        "a marked legacy Wave 7 elite guardian is removed from loaded chunks");
            }
            case "wave7-trial-actor" -> {
                Entity trial = new Entity();
                trial.pdc.set(h.keyWave, INTEGER, 7);
                trial.pdc.set(h.keyKind, STRING, "wave_guardian");
                trial.pdc.set(h.keyEventId, STRING, h.eventId);
                trial.pdc.set(h.keyTrialRole, STRING, "WARDEN");
                trial.pdc.set(h.keyMiniBossSpell, STRING, "DRAGON_BREATH");
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(trial)));
                check(!trial.removed,
                        "a named Wave 7 trial actor is never classified as a generic legacy elite");
            }
            case "wave6-elite-chunk-reload" -> {
                h.activeWave = 6;
                h.phase = EventPhase.WAVE_6;
                Spider elite = new Spider();
                elite.pdc.set(h.keyWave, INTEGER, 6);
                elite.pdc.set(h.keyKind, STRING, EVENT_KIND_ELITE);
                elite.pdc.set(h.keyEventId, STRING, h.eventId);
                elite.pdc.set(h.keyEventGeneration, LONG, h.generation);
                elite.pdc.set(h.keyOfficial, BYTE, (byte) 1);
                h.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(elite)));
                check(!elite.removed && h.ownedEntities.get(elite.id) == elite
                                && elite.ai && elite.aware
                                && h.miniBossSpells.get(elite.id) == EndRiftAiPolicy.MiniBossSpell.WEB_SHOT
                                && "web_shot".equals(elite.pdc.getOrDefault(
                                h.keyMiniBossSpell, STRING, ""))
                                && h.nextMiniBossSpellMillis.containsKey(elite.id),
                        "an owned current-generation Wave 6 web elite rehydrates tracking, AI, spell and cooldown");
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        System.out.println(scenario + " OK");
    }
}
'''.replace("__METHODS__", "\n".join(methods))
    output = tmp_path_factory.mktemp("wave2-entity-load")
    java = output / "WaveTwoEntityLoadHarness.java"
    java.write_text(harness, encoding="utf-8")
    dependencies = [DOMAIN / f"{name}.java" for name in (
            "PressureBudgetController", "WaveTwoEntityRecoveryPolicy", "WaveEliteCompositionPolicy",
            "WaveTwoStagePolicy", "WaveTwoHuntPolicy", "WaveMechanicsPolicy", "EliteAbilityPolicy", "EndRiftAiPolicy",
    )]
    dependencies.append(DOMAIN.parent / "runtime/WaveMobLifecycleRegistry.java")
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java), *map(str, dependencies)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario", [
    "duplicate-load", "restart-load", "stale-group-load", "unlisted-current-load",
    "mismatch-fence-duplicate-chunk-reload",
    "rotated-event-cleanup-tombstone", "foreign-event-untombstoned", "foreign-event-stale-generation",
    "wave7-ordinary-guardian", "wave7-marked-legacy-elite", "wave7-trial-actor",
    "stale-cleanup-epoch-load", "future-cleanup-epoch-load",
    "approved-group-3-elite-load", "unmarked-wave-2-elite-load", "wave6-elite-chunk-reload",
    "legacy-final-elite-missing-epoch-load", "legacy-final-elite-wrong-epoch-type-load",
])
def test_wave2_persisted_entity_load_adapter(wave2_entity_load_harness, scenario):
    result = subprocess.run(["java", "-cp", str(wave2_entity_load_harness),
                             "WaveTwoEntityLoadHarness", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("scenario", [
    "immediate-initial-mark", "full-window", "scaled-full-window", "leaving-roster", "cleanup", "foreign-glow", "reveal-effects-cleanup",
    "infinite-glow-preserved", "infinite-reveal-preserved", "stronger-glow-cleanup",
    "death", "disconnect", "replacement", "invalid-at-deadline-death", "invalid-at-deadline-disconnect", "invalid-at-deadline-arena",
    "wave-roster-launch", "charge-disconnect", "charge-generation",
    "boss-only-excluded", "wave-reflection", "sandbox-outsider-reflection", "boss-reflection-preserved", "wave7-reflection-boundary",
    "wave-reflected-hit", "stale-hit", "wrong-source-reflected-hit",
])
def test_wave2_and_wave4_production_decisions(mechanics_harness, scenario):
    result = subprocess.run(["java", "-cp", str(mechanics_harness),
                             "Waves24MechanicsHarness", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.fixture(scope="module")
def wave2_spawn_harness(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    initializer = _declaration(source, "private boolean initializeWaveGameplay(")
    schedule_start = initializer.index("        if (wave == 4)")
    schedule_end = initializer.index("        if (waveSpawnSchedule.isEmpty())", schedule_start)
    schedule_chain = initializer[schedule_start:schedule_end]
    spawn_next_group = _declaration(source, "private boolean spawnNextWaveGroup(World world, Location core)")
    spawn_next_group = spawn_next_group.replace("System.currentTimeMillis()", "Bukkit.now")
    tag_group = _declaration(source, "private int tagWaveTwoSpawnGroup(")
    ordinary_mob = _declaration(source, "private boolean isWaveTwoOrdinaryMob(Entity entity)")
    approved_elite = _declaration(source, "private boolean isApprovedWaveTwoFinalElite(Entity entity)")
    harness = r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;

public final class Wave2SpawnHarness {
    static final int MAX_EVENT_WAVE = 7;
    static final class EventConfig {
        int waveHardCap() { return 56; }
        record WaveDefinition(int endermen, int spiders, int skeletons,
                              int eliteEndermen, int eliteSkeletons) {}
    }
    static final class RealitySplitTrialController { int activeTrialCount() { return 0; } }
    static final class World {}
    static final class Location { public Location clone() { return new Location(); } }
    static final class Bukkit { static long now; }
    List<WaveMechanicsPolicy.WaveCounts> waveSpawnSchedule = List.of();
    List<WaveMechanicsPolicy.WaveCounts> spawned = new ArrayList<>();
    RealitySplitTrialController realitySplitTrialController = new RealitySplitTrialController();
    boolean currentWave4MobsStarted, currentWave4StartupFailed;
    boolean testWaveFrontVisualMode = true;
    boolean waveTwoGroupCleared;
    WaveTwoPhantomMaterializationPolicy.State waveTwoPhantomActivationState;
    int waveTwoPhantomActivationGroup;
    Location waveTwoPhantomActivationLocation;
    BukkitTask waveTwoPhantomWarningTask;
    int waveSpawnWave, waveSpawnGroupIndex, waveSpawnEntityOffset;
    int waveTwoGroupsSpawned, waveTwoCompletedMarkPeriods, waveTwoLastLateGroupMarkPeriod, saveCalls;
    int waveTwoPhantomActivationCycle;
    Set<String> currentWave4PressureSpawnSlots = new HashSet<>();
    int currentWave4PressurePackAttempts;
    long currentWave4PressureRetryAtMillis;
    long waveNextSpawnAtMillis, waveTwoGroupClearedAtMillis;
    Set<UUID> waveTwoOutstandingEntityUuids = new LinkedHashSet<>();
    Map<UUID,Entity> ownedEntities = new LinkedHashMap<>();
    Object keyWave = new Object(), keyKind = new Object(), keyWaveSpawnGroup = new Object();
    Object keyWaveTwoFinalElite = new Object();
    static final String EVENT_KIND_WAVE_MOB = "wave_mob", EVENT_KIND_ELITE = "elite";
    static final Object STRING = new Object(), INTEGER = new Object();
    static final class PersistentDataType { static final Object STRING = Wave2SpawnHarness.STRING, INTEGER = Wave2SpawnHarness.INTEGER; }
    static class Entity {
        final UUID id = UUID.randomUUID();
        final Map<Object,Object> pdc = new HashMap<>();
        UUID getUniqueId() { return id; }
        void set(Object key, Object value) { pdc.put(key, value); }
        Object get(Object key) { return pdc.get(key); }
        PersistentDataContainer getPersistentDataContainer() { return new PersistentDataContainer(pdc); }
    }
    static class Mob extends Entity {}
    static final class Skeleton extends Mob {}
    static final class DisplayEntity extends Entity {}
    static final class PersistentDataContainer {
        final Map<Object,Object> values;
        PersistentDataContainer(Map<Object,Object> values) { this.values = values; }
        void set(Object key, Object type, Object value) { values.put(key, value); }
    }
    enum EventPhase { RECOVERY_REQUIRED }
    EventPhase phase;
    String eventId = "event", recoveryReason;
    long generation = 5L;
    static final Logger LOGGER = Logger.getLogger("wave2-spawn");
    static { LOGGER.setLevel(Level.OFF); }
    Logger getLogger() { return LOGGER; }
    int readInt(Entity entity, Object key, int fallback) {
        Object value = entity.get(key); return value instanceof Number n ? n.intValue() : fallback;
    }
    String readString(Entity entity, Object key) {
        Object value = entity.get(key); return value instanceof String s ? s : "";
    }
    boolean isWaveCombatKind(String kind) { return EVENT_KIND_WAVE_MOB.equals(kind); }
    void forcePhase(EventPhase next, String reason) { phase = next; }
    boolean isOfficialCurrentAttempt() { return true; }
    boolean saveStateSync() { saveCalls++; return true; }
    WaveMechanicsPolicy.WaveCounts currentWave4MobCounts(int players) {
        return new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 0, 0);
    }
    WaveMechanicsPolicy.WaveCounts ritualWaveCounts(int players) {
        return new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 0, 0);
    }
    WaveMechanicsPolicy.WaveCounts ritualPressureCounts(int players) {
        return new WaveMechanicsPolicy.WaveCounts(0, 1, 0, 0, 0);
    }
    WaveMechanicsPolicy.WaveCounts combineWaveCounts(WaveMechanicsPolicy.WaveCounts left,
                                                      WaveMechanicsPolicy.WaveCounts right) {
        return new WaveMechanicsPolicy.WaveCounts(left.endermen() + right.endermen(),
                left.spiders() + right.spiders(), left.skeletons() + right.skeletons(),
                left.eliteEndermen() + right.eliteEndermen(),
                left.eliteSkeletons() + right.eliteSkeletons());
    }
    void initializeWaveTwoSchedule(int players) {
        int wave = 2;
        EventConfig config = new EventConfig();
        EventConfig.WaveDefinition definition = new EventConfig.WaveDefinition(9, 8, 4, 0, 0);
        WaveMechanicsPolicy.WaveCounts scaled;
        __SCHEDULE_CHAIN__
    }
    boolean tryBeginWaveTwoPhantomActivation(World world, Location core,
            WaveMechanicsPolicy.WaveCounts group, int groupNumber) { return false; }
    void clearWaveTwoPhantomActivationPresentation() { waveTwoPhantomWarningTask = null; }
    void spawnWaveGroup(World world, Location core, int wave, boolean guardianWave,
                        boolean test, WaveMechanicsPolicy.WaveCounts group, int baseIndex,
                        Location reservedPhantomSpawn) {
        spawned.add(group);
    }
    static final class BukkitTask { void cancel() {} }
        void checkpointWaveTwoGroupSpawn() { }
    __SPAWN_NEXT_GROUP__
    __TAG_GROUP__
    __ORDINARY_MOB__
    __APPROVED_ELITE__
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Wave2SpawnHarness h = new Wave2SpawnHarness();
        String scenario = args[0];
        switch (scenario) {
            case "two-openings-final-reserve" -> {
                h.initializeWaveTwoSchedule(2);
                check(h.waveSpawnSchedule.size() == 3,
                        "Wave 2 must prepare exactly three separately spawnable groups");
                check(h.waveSpawnSchedule.stream().allMatch(group -> group.total() > 0),
                        "each Wave 2 group must be bounded and non-empty");
                check(h.waveSpawnSchedule.get(0).eliteEndermen() == 0
                                && h.waveSpawnSchedule.get(0).eliteSkeletons() == 0
                                && h.waveSpawnSchedule.get(1).eliteEndermen() == 0
                                && h.waveSpawnSchedule.get(1).eliteSkeletons() == 0
                                && h.waveSpawnSchedule.get(2).eliteEndermen() == 0
                                && h.waveSpawnSchedule.get(2).eliteSkeletons() == 1,
                        "the first late group contains one replacement skeleton elite");
                check(h.waveSpawnSchedule.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum()
                                == 21,
                        "all 21 configured mobs survive scaling across the finite schedule");
                check(h.waveSpawnSchedule.get(2).total() <= WaveTwoStagePolicy.LATE_REINFORCEMENT_BUDGET,
                        "the single final Wave 2 reserve stays within its mob budget");
            }
            case "scaled-party-rosters" -> {
                for (int players : new int[] {2, 5, 6}) {
                    h.initializeWaveTwoSchedule(players);
                    int expected = players == 2 ? 21 : players == 5 ? 27 : 29;
                    check(h.waveSpawnSchedule.size() == 3
                                    && h.waveSpawnSchedule.stream().mapToInt(
                                    WaveMechanicsPolicy.WaveCounts::total).sum() == expected,
                            "Wave 2 preserves bounded scaled reserves for party size " + players);
                    check(h.waveSpawnSchedule.stream().allMatch(group -> group.total() > 0
                                    && group.total() <= WaveTwoStagePolicy.MAX_ACTIVE_GROUP_MOBS),
                            "each scaled group stays within the explicit simultaneous cap");
                    h.waveSpawnSchedule = List.of();
                }
            }
            case "group3-first-late-elite-only" -> {
                var groups = WaveTwoStagePolicy.split(
                        new WaveMechanicsPolicy.WaveCounts(3, 4, 5, 9, 8));
                check(groups.size() == 3 && groups.get(0).eliteEndermen() == 0
                                && groups.get(0).eliteSkeletons() == 0
                                && groups.get(1).eliteEndermen() == 0
                                && groups.get(1).eliteSkeletons() == 0
                                && groups.get(2).eliteEndermen() == 0
                                && groups.get(2).eliteSkeletons() == 1,
                        "stale elite counts normalize while the first late group gets one skeleton conversion");
                check(groups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == 29,
                        "legacy elite counts do not add pressure");
            }
            case "ordinary-mobs-only-roster" -> {
                Mob fighter = new Mob();
                fighter.set(h.keyWave, 2); fighter.set(h.keyKind, EVENT_KIND_WAVE_MOB);
                DisplayEntity display = new DisplayEntity();
                display.set(h.keyWave, 2); display.set(h.keyKind, EVENT_KIND_WAVE_MOB);
                h.ownedEntities.put(fighter.id, fighter); h.ownedEntities.put(display.id, display);
                int rosterSize = h.tagWaveTwoSpawnGroup(1, Set.of());
                check(rosterSize == 1 && h.waveTwoOutstandingEntityUuids.equals(Set.of(fighter.id)),
                        "display/item entities never enter the outstanding Wave 2 fighter roster");
                check(h.readInt(fighter, h.keyWaveSpawnGroup, 0) == 1
                                && h.readInt(display, h.keyWaveSpawnGroup, 0) == 0,
                        "only the ordinary mob receives the durable group marker");
            }
            case "group-3-elite-roster" -> {
                Skeleton elite = new Skeleton();
                elite.set(h.keyWave, 2); elite.set(h.keyKind, EVENT_KIND_ELITE);
                elite.set(h.keyWaveTwoFinalElite, "group-3-skeleton-v1");
                elite.set(h.keyWaveSpawnGroup, 3);
                Skeleton stale = new Skeleton();
                stale.set(h.keyWave, 2); stale.set(h.keyKind, EVENT_KIND_ELITE);
                Mob ordinary = new Mob();
                ordinary.set(h.keyWave, 2); ordinary.set(h.keyKind, EVENT_KIND_WAVE_MOB);
                h.ownedEntities.put(elite.id, elite); h.ownedEntities.put(stale.id, stale);
                h.ownedEntities.put(ordinary.id, ordinary);
                int rosterSize = h.tagWaveTwoSpawnGroup(3, Set.of(ordinary.id));
                check(rosterSize == 1 && h.waveTwoOutstandingEntityUuids.equals(Set.of(elite.id)),
                        "only the marked group-three elite enters the new group UUID roster");
            }
            case "empty-positive-spawn-fails-closed" -> {
                h.waveSpawnWave = 2;
                h.waveSpawnSchedule = List.of(new WaveMechanicsPolicy.WaveCounts(1, 0, 0, 0, 0));
                h.spawnNextWaveGroup(new World(), new Location());
                check(h.phase == EventPhase.RECOVERY_REQUIRED && h.waveSpawnGroupIndex == 0,
                        "a positive configured group with zero registered fighters cannot be checkpointed as clear");
                check(h.saveCalls == 1,
                        "the incomplete group recovery fence is synchronously checkpointed");
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        System.out.println(scenario + " OK");
    }
}
'''
    harness = harness.replace("__SCHEDULE_CHAIN__", schedule_chain)
    harness = harness.replace("__SPAWN_NEXT_GROUP__", spawn_next_group)
    harness = harness.replace("__TAG_GROUP__", tag_group)
    harness = harness.replace("__ORDINARY_MOB__", ordinary_mob)
    harness = harness.replace("__APPROVED_ELITE__", approved_elite)
    output = tmp_path_factory.mktemp("wave2-spawn")
    java = output / "Wave2SpawnHarness.java"
    java.write_text(harness, encoding="utf-8")
    dependencies = [DOMAIN / f"{name}.java" for name in (
        "WaveScalingPolicy", "WaveMechanicsPolicy", "WavePressurePolicy", "PressureBudgetController", "WaveTwoStagePolicy",
        "WaveTwoHuntPolicy",
        "WaveTwoHuntPolicy", "WaveTwoPhantomMaterializationPolicy",
        "WaveTwoEntityRecoveryPolicy",
    )]
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java), *map(str, dependencies)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario", [
    "two-openings-final-reserve", "scaled-party-rosters", "group3-first-late-elite-only", "ordinary-mobs-only-roster",
    "group-3-elite-roster",
    "empty-positive-spawn-fails-closed",
])
def test_wave2_bounded_group_spawn_contracts(wave2_spawn_harness, scenario):
    result = subprocess.run(["java", "-cp", str(wave2_spawn_harness),
                             "Wave2SpawnHarness", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("scenario", [
    "forty-five-second-mark-retention", "forty-five-second-mark-rotation", "unloaded-group-roster",
    "sandbox-confirmed-death",
    "two-opening-batches-late-gate", "wave2-edge-spawns", "wave2-edge-spawns-respect-bounds",
    "assignment-id-exhaustion",
])
def test_wave2_mark_periods_and_edge_spawn(mechanics_harness, scenario):
    if scenario.startswith("wave2-edge-spawns"):
        source = EVENT.read_text(encoding="utf-8")
        route = _declaration(source, "private Location safeSpawnLocationForWave(")
        assert "if (wave == 2)" in route
        assert "Location edgeSpawn = safeWaveTwoEdgeSpawnLocation(core, index);" in route
        assert "return edgeSpawn != null ? edgeSpawn : safeSpawnLocation(core, index, offset);" in route
    result = subprocess.run(["java", "-cp", str(mechanics_harness),
                             "Waves24MechanicsHarness", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave2_restart_restores_bounded_groups_and_durable_hunt_progress():
    source = EVENT.read_text(encoding="utf-8")
    apply_snapshot = _declaration(source, "private void applySnapshot(")
    restore = _declaration(source, "private void restorePersistedWaveSchedule()")
    spawn = _declaration(source, "private boolean spawnNextWaveGroup(")
    group_tag = _declaration(source, "private int tagWaveTwoSpawnGroup(")
    objective_snapshot = _declaration(source, "private Map<String, String> objectiveProgressSnapshot()")
    wave_snapshot = _declaration(source, "private Map<String, String> waveObjectiveProgressSnapshot()")
    objective_start = _declaration(source, "private WaveObjectiveStartResult startCanonicalObjective(")
    mark_update = _declaration(source, "private void updateMarkedTargetObjective(")
    mark_recovery = _declaration(source, "private void requireWaveTwoMarkAssignmentRecovery(")
    disable = _declaration(source, "public void onDisable()")

    assert 'snapshot.eventPhase() == EventPhase.WAVE_2' in apply_snapshot
    assert 'WAVE_TWO_CYCLES_PROGRESS_KEY' in apply_snapshot
    assert 'WaveTwoStagePolicy.hasConsistentProgress(restoredHuntCycles,' in apply_snapshot
    assert 'waveSpawnSchedule = wave == 2 ? WaveTwoStagePolicy.split(scaled) : List.of(scaled)' in restore
    pressure_gate = restore.index('if (wave != 2)')
    pressure_clamp = restore.index('WavePressurePolicy.clamp(wave, scaled, players)')
    schedule_build = restore.index('waveSpawnSchedule = wave == 2 ? WaveTwoStagePolicy.split(scaled)')
    assert pressure_gate < pressure_clamp < schedule_build
    assert 'WaveTwoStagePolicy.recoveryNextGroupIndex(' in restore
    assert 'waveTwoStageRehydrated = true' in restore
    assert 'waveTwoStageRehydrated = false' in objective_start
    assert 'if (!waveTwoStageRehydrated)' in objective_start
    assert 'PersistentDataType.INTEGER, groupNumber' in group_tag
    assert 'WAVE_TWO_OUTSTANDING_UUIDS_PROGRESS_KEY' in wave_snapshot
    assert 'new LinkedHashMap<>(waveObjectiveProgressSnapshot())' in objective_snapshot
    assert 'restoredWaveTwoOutstanding = WaveTwoEntityRecoveryPolicy.decode(' in apply_snapshot
    assert 'waveTwoOutstandingEntityUuids.addAll(restoredWaveTwoOutstanding)' in apply_snapshot
    assert 'WAVE_TWO_GROUPS_SPAWNED_PROGRESS_KEY' in wave_snapshot
    assert 'WAVE_TWO_GROUP_CLEARED_PROGRESS_KEY' in wave_snapshot
    assert 'WAVE_TWO_MARK_PERIODS_PROGRESS_KEY' in wave_snapshot
    assert 'WAVE_TWO_MARK_ASSIGNMENT_ID_PROGRESS_KEY' in wave_snapshot
    assert 'Integer.toString(waveTwoMarkCycle)' in wave_snapshot
    assert 'WAVE_TWO_LAST_LATE_MARK_PROGRESS_KEY' in wave_snapshot
    assert 'WaveTwoHuntPolicy.restoreMarkAssignmentId(' in apply_snapshot
    assert 'waveTwoMarkCycle = restoredWaveTwoMarkAssignmentId' in apply_snapshot
    assert 'waveTwoMarkCycle = WaveTwoHuntPolicy.nextMarkAssignmentId(waveTwoMarkCycle)' in mark_update
    assert 'WaveTwoHuntPolicy.markAssignmentIdsExhausted(waveTwoMarkCycle)' in mark_update
    assert 'requireWaveTwoMarkAssignmentRecovery(now)' in mark_update
    assert 'waveTwoRecoveryCheckpoint = waveObjectiveProgressSnapshot()' in mark_recovery
    assert 'forcePhase(EventPhase.RECOVERY_REQUIRED, recoveryReason)' in mark_recovery
    assert 'WAVE_TWO_MARK_ASSIGNMENT_ID_EXHAUSTED' in mark_recovery
    assert 'waveTwoRecoveryCheckpoint' in wave_snapshot
    assert 'if (!waveTwoStageRehydrated)' in objective_start
    assert objective_start.index('if (!waveTwoStageRehydrated)') < objective_start.index('waveTwoMarkCycle = 0;')
    assert 'checkpointWaveTwoGroupClear()' in _declaration(source, 'private void tickCurrentHuntObjective(')
    assert 'checkpointWaveTwoGroupSpawn()' in spawn
    assert 'clearCurrentHuntMark(System.currentTimeMillis())' in disable
    assert 'WaveTwoHuntPolicy.MARK_ROTATION_MILLIS' in _declaration(
        source, 'private void updateMarkedTargetObjective(')
    assert 'END_CONTROL_START' not in source
    assert 'END_CONTROL_STOP' not in source


def test_wave2_coordinator_keeps_unloaded_roster_out_of_live_entity_execution():
    source = EVENT.read_text(encoding="utf-8")
    planner = _declaration(source, "private Map<UUID, UUID> planWaveMobSquads(")
    tick = _declaration(source, "private void tickWaveMobAi()")
    assert "WaveTwoEntityRecoveryPolicy.coordinatorRoster(" in planner
    assert "waveTwoOutstandingEntityUuids" in planner
    # Target assignment may carry every outstanding UUID, while live combat
    # still iterates only entities rehydrated into the owned-entity map.
    assert "for (Entity entity : new ArrayList<>(ownedEntities.values()))" in tick
