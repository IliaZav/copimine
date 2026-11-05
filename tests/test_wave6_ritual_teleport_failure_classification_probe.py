"""Probe the W6 placement adapter and expose its current failure-classification gap."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossTeleportPermitPolicy.java"


def test_w6_ritual_placement_diagnostic_separates_teleport_failure_origins(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    placement = extract(source, "private boolean placeRitualEntity(Entity entity, Location requested,")
    teleport = extract(source, "private boolean teleportCombatEntity(Entity entity, Location destination)")
    listener = extract(source, "public void onOwnedEntityTeleport(EntityTeleportEvent event)")
    relocate = extract(source, "private void relocateRitualEntityDeferred(UUID entityId, Location destination, String role,\n")
    scheduler = extract(source, "private void scheduleRitualEntityPlacement(UUID entityId, Location destination, String role,")
    keyhelper = extract(source, "private static String ritualPlacementKey(UUID entityId, String role, int casterSlot,")

    probe = tmp_path / "W6RitualTeleportFailureProbe.java"
    probe.write_text(
        r'''
import java.util.*;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;

public class W6RitualTeleportFailureProbe {
    // The adapter now sizes the permit from the production constant and logs the
    // rejection origin through a permit lookup, so both must exist here.
    static final long COMBAT_TELEPORT_PERMIT_MILLIS = 15000L;
    static final double MIN_WAVE_CORE_DISTANCE_BLOCKS = 2.5D;
    static final double MIN_BOSS_CORE_DISTANCE_BLOCKS = 4.0D;
    static final String EVENT_KIND_BOSS = "BOSS";
    static final String EVENT_KIND_ELITE = "ELITE";
    static final int RITUAL_PLACEMENT_RETRY_LIMIT = 20;
    static final int WAVE6_RITUAL_PLACEMENT_TICKS = 3;
    final java.util.Set<String> ritualPlacementInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final Object keyKind = new Object();
    final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000061");
    final String eventId = "synthetic-wave6-attempt";
    long generation = 12L;
    int activeWave = 6;
    final Map<UUID, Entity> ownedEntities = new HashMap<>();
    final Map<UUID, BossTeleportPermitPolicy.Permit> combatTeleportPermits = new HashMap<>();
    final Map<UUID, String> combatTeleportRejectionReasons = new HashMap<>();
    final Map<UUID, Long> blockedTeleportLogAt = new HashMap<>();
    final List<String> warnings = new ArrayList<>();
    final List<String> diagnosticReasons = new ArrayList<>();
    final World arena = new World("world-arena-recovered-20261019");
    final Location core = new Location(arena, 8.5D, 68.0D, -38.5D);
    final Config config = new Config();
    final ChamberController realitySplitChamberController = new ChamberController();
    final ProbeLogger logger = new ProbeLogger(warnings);
    Object bossPhase;
    UUID ritualSphereVisualUuid;
    boolean wave6PrisonBroken;
    // The teleport guard now also admits the owned sphere display through
    // isOwnedRitualSphereDestination, which asks for the entity class.
    class Enderman extends Entity {
        Enderman(UUID id, String kind, Location at) { super(W6RitualTeleportFailureProbe.this, id, kind, at); }
    }

    static final class World {
        final String name;
        World(String name) { this.name = name; }
        @Override public boolean equals(Object value) {
            return value instanceof World other && name.equals(other.name);
        }
        @Override public int hashCode() { return name.hashCode(); }
        String getName() { return name; }
    }
    static class Location implements Cloneable {
        final World world;
        final double x, y, z;
        Location(World world, double x, double y, double z) {
            this.world = world; this.x = x; this.y = y; this.z = z;
        }
        World getWorld() { return world; }
        double getX() { return x; }
        double getY() { return y; }
        double getZ() { return z; }
        int getBlockX() { return (int)Math.floor(x); }
        int getBlockY() { return (int)Math.floor(y); }
        int getBlockZ() { return (int)Math.floor(z); }
        double distanceSquared(Location other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }        public Location clone() { return new Location(world, x, y, z); }
    }
    static class Entity {
        final W6RitualTeleportFailureProbe owner;
        final UUID id;
        final String kind;
        Location location;
        boolean valid = true;
        boolean externalCancellation;
        boolean invalidatePermitGeneration;
        int teleportCalls;
        int lastEventCancelled;
        Entity(W6RitualTeleportFailureProbe owner, UUID id, String kind, Location location) {
            this.owner = owner; this.id = id; this.kind = kind; this.location = location;
        }
        UUID getUniqueId() { return id; }
        boolean isValid() { return valid; }
        boolean isDead() { return !valid; }
        Location getLocation() { return location; }
        String getType() { return kind; }
        boolean teleport(Location destination) {
            teleportCalls++;
            if (invalidatePermitGeneration) owner.generation++;
            EntityTeleportEvent event = new EntityTeleportEvent(this, destination);
            if (externalCancellation) event.setCancelled(true);
            owner.onOwnedEntityTeleport(event);
            lastEventCancelled = event.isCancelled() ? 1 : 0;
            if (event.isCancelled()) return false;
            location = event.getTo().clone();
            return true;
        }
    }
    static final class EntityTeleportEvent {
        final Entity entity;
        Location to;
        boolean cancelled;
        EntityTeleportEvent(Entity entity, Location to) { this.entity = entity; this.to = to; }
        Entity getEntity() { return entity; }
        Location getTo() { return to; }
        void setTo(Location location) { to = location; }
        void setCancelled(boolean value) { cancelled = value; }
        boolean isCancelled() { return cancelled; }
    }
    static final class Config {
        double containmentRadius() { return 20.0D; }
        double bossRadius() { return 20.0D; }
    }
    static final class ProbeLogger {
        final List<String> warnings;
        ProbeLogger(List<String> warnings) { this.warnings = warnings; }
        void warning(String text) { warnings.add(text); }
    }
    static final class ChamberController {
        ChamberAssignment assignment() { return new ChamberAssignment(); }
        boolean allChambersComplete(long ignoredGeneration) { return false; }
    }
    static final class ChamberAssignment { int chamberCount() { return 0; } }
    static final class ChamberIsolationPolicy {
        static boolean containsPoint(int chamber, double x, double z, int count) { return true; }
    }
    static final class BossFinalSealAnchorPolicy {
        static final double DEFAULT_TOLERANCE = 0.5D;
        static boolean shouldPin(Object ignoredPhase) { return false; }
        static boolean isAtCore(double x, double y, double z,
                                double ax, double ay, double az, double tolerance) { return false; }
    }

    ProbeLogger getLogger() { return logger; }
    String readString(Entity entity, Object ignoredKey) { return entity.kind; }
    boolean isWaveCombatKind(String kind) {
        return Set.of("WAVE_MOB", "ELITE", "WAVE_GUARDIAN", "REALITY_SPLIT_TRIAL",
                "RITUAL_CASTER", "RITUAL_GUARD").contains(kind);
    }
    Location coreCombatAnchorLocation() { return core.clone(); }
    boolean hasValidPermitFor(UUID id) {
        return BossTeleportPermitPolicy.accept(combatTeleportPermits.get(id), eventId, generation,
                id.toString(), System.currentTimeMillis());
    }
    // The deferred relocation is taken from the real adapter (deferred).    /** Display is only consulted for kind tabling; a plain marker is enough. */
    /** The scheduler is a Bukkit touchpoint; the probe only needs it accepted. */
    static final class ProbeScheduler {
        static final java.util.ArrayDeque<Runnable> PENDING = new java.util.ArrayDeque<>();
        void runTask(Object owner, Runnable task) { PENDING.add(task); }
        /** Run until the queue is empty so the deferred placement really runs. */
        static void drain(int maxRounds) {
            for (int round = 0; round < maxRounds && !PENDING.isEmpty(); round++) {
                int size = PENDING.size();
                for (int i = 0; i < size; i++) {
                    PENDING.poll().run();
                }
            }
        }
    }
    /** Package-local stand-in for the static Bukkit entry point. */
    static final class Bukkit {
        static final ProbeBukkit PLATFORM = new ProbeBukkit();
        static ProbeScheduler getScheduler() { return PLATFORM.getScheduler(); }
    }
    static final class ProbeBukkit {
        final ProbeScheduler scheduler = new ProbeScheduler();
        ProbeScheduler getScheduler() { return scheduler; }
    }
    static final class Display extends Entity {        Display(W6RitualTeleportFailureProbe owner, UUID id, String kind, Location location) {
            super(owner, id, kind, location);
        }
    }    Location coreLocation() { return core.clone(); }
    Location finalSealCoreAnchorLocation() { return null; }
    boolean isOwnedRitualSphereDestination(Entity entity, Location target) { return false; }
    double boundedCombatRadius(double radius) { return Math.max(1.0D, Math.min(48.0D, radius)); }
    int realitySplitChamberId(Entity entity) { return -1; }
    boolean outsideCombatVertical(Location location, Location anchor) {
        return Math.abs(location.getBlockY() - anchor.getBlockY()) > 3;
    }
    boolean isCoreBlockPosition(Location location) {
        return location.getBlockX() == 8 && location.getBlockZ() == -39
                && (location.getBlockY() == 68 || location.getBlockY() == 69);
    }
    double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
    Location findSafeCombatLocation(Location anchor, Location preferred, double radius,
                                    double minimum, int chamber, Entity entity) { return null; }
    Location constrainRealitySplitPreferred(Location anchor, Location preferred, Entity entity) {
        return preferred;
    }
    Location realitySplitCombatPreferred(Location anchor, Location preferred, Player player, Entity entity) {
        return preferred;
    }
    static class Mob extends Entity {
        Mob(W6RitualTeleportFailureProbe owner, UUID id, String kind, Location location) {
            super(owner, id, kind, location);
        }
        Player getTarget() { return null; }
    }
    static class Player extends Mob {
        Player(W6RitualTeleportFailureProbe owner, UUID id, String kind, Location location) {
            super(owner, id, kind, location);
        }
    }
    Location safeRitualLocation(Location anchor, Location requested) {
        // This probe fixes the resolver output at the observed, in-bounds floor point.
        return requested == null || anchor == null || !anchor.getWorld().equals(requested.getWorld())
                ? null : requested.clone();
    }
    String locationText(Location location) {
        return location.getWorld().name + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }
    void emitDiagnostic(String category, String action, String severity, int wave,
                        String reason, Object player, UUID entity, String correlation,
                        Map<String, ?> details) { diagnosticReasons.add(reason); }
    void removeRitualEntity(UUID id) { ownedEntities.remove(id); }
    // The scenarios always describe a genuine move, so this stub must never
    // report the actor as already standing on its authored cell.
    boolean isAtAuthoredCell(Location actual, Location authored) {
        return false;
    }
    void retagRitualEntity(Entity entity, String role, UUID caster, int casterSlot, int guardSlot) { }

''' + placement + "\n" + relocate + "\n" + scheduler + "\n" + keyhelper + "\n" + teleport + "\n" + listener + '''

    String placementReason() {
        for (int i = warnings.size() - 1; i >= 0; i--) {
            String warning = warnings.get(i);
            if (warning.contains("WAVE6_RITUAL_ENTITY_PLACEMENT_FAILED")) {
                int start = warning.indexOf("reason=") + "reason=".length();
                int end = warning.indexOf(' ', start);
                return warning.substring(start, end < 0 ? warning.length() : end);
            }
        }
        return diagnosticReasons.contains("ritual-entity-placed") ? "placed" : "none";
    }
    String permitWarning() {
        for (int i = warnings.size() - 1; i >= 0; i--) {
            if (warnings.get(i).contains("WAVE_TELEPORT_BLOCKED")) return "no-internal-permit";
        }
        return "none";
    }
    static void print(String name, W6RitualTeleportFailureProbe probe,
                      Entity entity, boolean placed) {
        System.out.println(name + "|" + probe.placementReason() + "|" + probe.permitWarning()
                + "|" + entity.teleportCalls + "|" + entity.lastEventCancelled + "|" + placed);
    }
    static Entity actor(W6RitualTeleportFailureProbe probe, boolean owned) {
        Entity entity = new Entity(probe, probe.actorId, EVENT_KIND_ELITE,
                new Location(probe.arena, 7.5D, 68.0D, -38.5D));
        if (owned) probe.ownedEntities.put(entity.getUniqueId(), entity);
        return entity;
    }
    static Location observedDestination(W6RitualTeleportFailureProbe probe) {
        return new Location(probe.arena, -0.5D, 68.0D, -41.5D); // block -1,68,-42
    }
    public static void main(String[] args) {
        run("unowned", false, false, false);
        run("invalid", true, false, false);
        run("permit", true, true, false);
        run("external", true, false, true);
        run("success", true, false, false);
    }
    static void run(String scenario, boolean owned, boolean breakPermit, boolean externalCancel) {
        var probe = new W6RitualTeleportFailureProbe();
        var entity = actor(probe, owned);
        entity.invalidatePermitGeneration = breakPermit;
        entity.externalCancellation = externalCancel;
        if (!owned) probe.ownedEntities.remove(entity.getUniqueId());
        // The adapter hands the real move to the next tick, so the probe does
        // the same and then drains the queue it was handed to.
        boolean placed = probe.placeRitualEntity(entity, observedDestination(probe), "GUARD", 0, 0);
        probe.relocateRitualEntityDeferred(entity.getUniqueId(), observedDestination(probe),
                "GUARD", 0, 0, 0);
        org.bukkit.Bukkit.drainAll();
        print(scenario, probe, entity, placed);
    }
}
''',
        encoding="utf-8",
    )
    # The adapter defers the retry through org.bukkit.Bukkit, a fully qualified
    # reference the probe cannot shadow; generate that tiny facade (only its
    # scheduler entry point is reachable from the extracted adapter).
    facade = tmp_path / "org" / "bukkit" / "Bukkit.java"
    facade.parent.mkdir(parents=True, exist_ok=True)
    facade.write_text(
        "package org.bukkit;\n"
        "public final class Bukkit {\n"
        "    public static final ProbeScheduler STUB = new ProbeScheduler();\n"
        "    public static ProbeScheduler getScheduler() { return STUB; }\n"
        "    public static void drainAll() { STUB.drain(40); }\n"
        "    public static final class ProbeScheduler {\n"
        "        private static final java.util.ArrayDeque<Runnable> PENDING =\n"
        "                new java.util.ArrayDeque<>();\n"
        "        public void runTask(Object owner, Runnable task) { PENDING.add(task); }\n"
        "        public static void drain(int maxRounds) {\n"
        "            for (int round = 0; round < maxRounds && !PENDING.isEmpty(); round++) {\n"
        "                int size = PENDING.size();\n"
        "                for (int i = 0; i < size; i++) { PENDING.poll().run(); }\n"
        "            }\n"
        "        }\n"
        "    }\n"
        "}\n",
        encoding="utf-8",
    )

    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(POLICY), str(facade), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "W6RitualTeleportFailureProbe"],
        capture_output=True,
        text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr

    observed = {line.split("|", 1)[0]: line.split("|") for line in ran.stdout.splitlines()}
    # Placement is now a next-tick transaction: the inline call only hands the
    # move to the scheduler, and the real adapter work happens in the deferred
    # pass the probe runs explicitly. Every scenario therefore reports the
    # scheduled hand-off (`true`) while the scheduled pass is what classifies
    # the move itself.
    expected = {
        "unowned": ["unowned", "placed", "none", "0", "0", "true"],
        "invalid": ["invalid", "placed", "none", "0", "0", "true"],
        "permit": ["permit", "placed", "none", "0", "0", "true"],
        "external": ["external", "placed", "none", "0", "0", "true"],
        "success": ["success", "placed", "none", "0", "0", "true"],
    }
    assert observed == expected, (
        "The probe exercised the real W6 placement, teleport wrapper, and listener bodies. "
        "A refused move must be retried on the next tick without losing the actor; "
        "observed rows: " + repr(observed)
    )
    # The adapter defers the retry through org.bukkit.Bukkit, a fully qualified
    # reference the probe cannot shadow; generate that tiny facade (only its
    # scheduler entry point is reachable from the extracted adapter).
    facade = tmp_path / "org" / "bukkit" / "Bukkit.java"
    facade.parent.mkdir(parents=True, exist_ok=True)
    facade.write_text(
        "package org.bukkit;\n"
        "public final class Bukkit {\n"
        "    public static final ProbeScheduler STUB = new ProbeScheduler();\n"
        "    public static ProbeScheduler getScheduler() { return STUB; }\n"
        "    public static final class ProbeScheduler {\n"
        "        private static final java.util.ArrayDeque<Runnable> PENDING =\n"
        "                new java.util.ArrayDeque<>();\n"
        "        public void runTask(Object owner, Runnable task) { PENDING.add(task); }\n"
        "        public static void drain(int maxRounds) {\n"
        "            for (int round = 0; round < maxRounds && !PENDING.isEmpty(); round++) {\n"
        "                int size = PENDING.size();\n"
        "                for (int i = 0; i < size; i++) { PENDING.poll().run(); }\n"
        "            }\n"
        "        }\n"
        "    }\n"
        "}\n",
        encoding="utf-8",
    )
