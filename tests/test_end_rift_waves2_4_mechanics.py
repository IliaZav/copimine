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
        "private void applyCurrentReflectedObeliskHit(",
    )]
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
    fields += "\n" + "\n".join(re.findall(
        r"    private final (?:Map<PotionEffectType, PotionEffect>|Set<PotionEffectType>) waveTwo\w*[^;]*;", source))
    cleanup = _declaration(source, "private void clearWaveObjectiveState(")
    cleanup_start = cleanup.index("        clearCurrentHuntMark(") if "        clearCurrentHuntMark(" in cleanup else cleanup.index("        waveTwoMarkedPlayerUuid = null;")
    methods.append("void finishHunt() { " + cleanup[cleanup_start:cleanup.index("        currentCarrierCharges = 0;", cleanup_start)] + "}")
    for signature, name in (("public void onPlayerQuit(", "quitHunt"), ("public void onPlayerDeath(", "deathHunt")):
        lifecycle = _declaration(source, signature)
        calls = [line for line in lifecycle.splitlines() if "releaseCurrentHuntTarget(" in line]
        methods.append(f"void {name}(Player player) {{ UUID uuid = player.getUniqueId(); " + "\n".join(calls) + "}")
    constants = "\n".join(re.findall(
        r"    private static final int WAVE_TWO_\w+[^;]*;", source))
    harness = r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.WaveCombatCoordinator;

public final class Waves24MechanicsHarness {
    __FIELDS__
    __CONSTANTS__
    long generation = 5L, eventTickCounter = 100L, waveObjectiveStartedMillis;
    int waveTargetCursor, visualUpdates, hitReactions, clientActive, clientClears, scalePlayers = 1;
    String eventId = "event";
    Object config = new Object(), keyRiftFireballState = new Object();
    UUID lastLaunchTarget;
    WaveCombatCoordinator waveCombatCoordinator = new WaveCombatCoordinator();
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
    boolean isArenaLocation(Location location) { return location.inArena; }
    Location coreCombatAnchorLocation() { return new Location(); }
    String worldVfxDimension(Location location) { return "the_end"; }
    Collection<Player> eventAudience() { return Bukkit.players; }
    boolean isEventParticleViewer(Player player, Location location) { return player.online; }
    boolean isRealitySplitReflectionProjectile(LargeFireball entity, RiftFireballRuntimeState state) { return state != null && state.wave7; }
    boolean isRealitySplitTrialParticipant(Player player, int chamber) { return player.chamber == chamber; }
    int readInt(Entity entity, Object key, int fallback) { return ((LargeFireball) entity).chamber; }
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
    void sendWorldBeamPacket(Player player, String key, Location from, Location to, int color, float width) {}
    void clearWorldVfxBeamsByPrefix(String key) {}
    void playWaveFeedback(String cue,Location point) {}
    void clearWaveCombatCue(UUID owner) {}
    void renderWave4ObeliskCue(Wave4ObeliskRuntimeState state, String stage, String ability, Location mark, double radius) {}
    void spawnEventParticle(Object... values) {
        for (Player player : Bukkit.players) player.particles++;
    }

    enum EventPhase { WAVE_4, COLLECTING, READY_FOR_PLAYERS }
    enum ObeliskStage { ACTIVE, COLLAPSING }
    enum PotionEffectType { GLOWING, SPEED, RESISTANCE }
    enum Particle { END_ROD, DUST, REVERSE_PORTAL;
        static final class DustOptions { DustOptions(Color color, float size) {} }
    }
    enum Sound { ENTITY_ENDERMAN_STARE, ENTITY_GENERIC_EXPLODE, BLOCK_AMETHYST_BLOCK_RESONATE, BLOCK_RESPAWN_ANCHOR_CHARGE }
    enum SoundCategory { HOSTILE }
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
        UUID getUniqueId() { return id; }
        PersistentDataContainer getPersistentDataContainer() { return new PersistentDataContainer(); }
    }
    static final class PersistentDataContainer { void set(Object key, Object type, Object value) {} }
    static final class PersistentDataType { static Object STRING = new Object(); }
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
    static final class Location {
        boolean inArena = true;
        Location add(double x,double y,double z) { return this; }
        public Location clone() { return new Location(); }
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
        WaveCombatCoordinator.Lease fireLease;
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
        boolean wave4 = true, wave7, consumed;
        long generation = 5L;
        ObeliskProjectilePolicy.State state = ObeliskProjectilePolicy.State.REFLECTED;
        UUID entityId() { return id; }
        UUID reflectorUuid() { return reflector; }
        boolean isWave4Obelisk() { return wave4; }
        boolean consumed() { return consumed; }
        long generation() { return generation; }
        ObeliskProjectilePolicy.State projectileState() { return state; }
        void consume() { consumed = true; state = ObeliskProjectilePolicy.State.CONSUMED; }
    }

    __METHODS__

    void selectAt(long now) { Bukkit.now = now; updateMarkedTargetObjective(now); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        Waves24MechanicsHarness h = new Waves24MechanicsHarness();
        Player player = new Player(); Bukkit.players.add(player);
        String scenario = args[0]; long start = 1_000_000L;
        switch (scenario) {
            case "initial-delay" -> {
                h.startHunt(start); h.selectAt(start);
                check(h.waveTwoMarkedPlayerUuid == null, "first mark must not commit on first tick");
                h.selectAt(start + 1_999L);
                check(h.waveTwoMarkedPlayerUuid == null, "first mark must retain two second readable lead-in");
                h.selectAt(start + 4_000L);
                check(player.id.equals(h.waveTwoMarkedPlayerUuid), "first mark must commit by four seconds");
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
                h.selectAt(start); Bukkit.now = start + 100L; h.finishHunt();
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
            case "three-cycles" -> {
                h.startHunt(start); h.selectAt(start + 3_000L);
                for (int cycle = 1; cycle <= 3; cycle++) {
                    long deadline = h.waveTwoMarkDeadlineMillis; Bukkit.now = deadline;
                    h.tickCurrentHuntObjective(deadline);
                    check(h.currentHuntCycles == cycle && h.waveTwoMarkedPlayerUuid == null,
                            "completed hunt cycle must clear target before incrementing progress");
                    Bukkit.now = deadline + 1_000L; h.tickCurrentHuntObjective(Bukkit.now);
                    check((h.waveTwoMarkedPlayerUuid != null) == (cycle < 3),
                            "exactly three mechanical cycles must be committed");
                }
                check(h.clientClears == 3, "each completed hunt cycle must clear client presentation once");
            }
            case "invalid-at-deadline-death", "invalid-at-deadline-disconnect", "invalid-at-deadline-arena" -> {
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
                h.applyCurrentReflectedObeliskHit(obelisk, fireball);
                check(obelisk.health == 3 && !fireball.consumed, "stale generation cannot consume health");
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
        "ObeliskFireDirectorPolicy", "ObeliskScalingPolicy", "RiftFireballReflectionPolicy",
    )]
    dependencies.append(DOMAIN.parent / 'runtime/WaveCombatCoordinator.java')
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java), *map(str, dependencies)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario", [
    "initial-delay", "full-window", "scaled-full-window", "leaving-roster", "cleanup", "foreign-glow", "reveal-effects-cleanup",
    "infinite-glow-preserved", "infinite-reveal-preserved", "stronger-glow-cleanup",
    "death", "disconnect", "replacement", "three-cycles", "invalid-at-deadline-death", "invalid-at-deadline-disconnect", "invalid-at-deadline-arena",
    "wave-roster-launch", "charge-disconnect", "charge-generation",
    "boss-only-excluded", "wave-reflection", "sandbox-outsider-reflection", "boss-reflection-preserved", "wave7-reflection-boundary",
    "wave-reflected-hit", "stale-hit",
])
def test_wave2_and_wave4_production_decisions(mechanics_harness, scenario):
    result = subprocess.run(["java", "-cp", str(mechanics_harness),
                             "Waves24MechanicsHarness", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
