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

    probe = tmp_path / "W6RitualTeleportFailureProbe.java"
    probe.write_text(
        r'''
import java.util.*;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;

public class W6RitualTeleportFailureProbe {
    static final long COMBAT_TELEPORT_PERMIT_MILLIS = 1000L;
    static final double MIN_WAVE_CORE_DISTANCE_BLOCKS = 2.5D;
    static final double MIN_BOSS_CORE_DISTANCE_BLOCKS = 4.0D;
    static final String EVENT_KIND_BOSS = "BOSS";
    static final String EVENT_KIND_ELITE = "ELITE";
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

    static final class World {
        final String name;
        World(String name) { this.name = name; }
        @Override public boolean equals(Object value) {
            return value instanceof World other && name.equals(other.name);
        }
        @Override public int hashCode() { return name.hashCode(); }
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
        public Location clone() { return new Location(world, x, y, z); }
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
    Location coreLocation() { return core.clone(); }
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

''' + placement + "\n" + teleport + "\n" + listener + r'''

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
        var unownedProbe = new W6RitualTeleportFailureProbe();
        var unowned = actor(unownedProbe, false);
        print("unowned", unownedProbe, unowned,
                unownedProbe.placeRitualEntity(unowned, observedDestination(unownedProbe), "GUARD", 0, 0));

        var invalidProbe = new W6RitualTeleportFailureProbe();
        var invalid = actor(invalidProbe, true); invalid.valid = false;
        print("invalid", invalidProbe, invalid,
                invalidProbe.placeRitualEntity(invalid, observedDestination(invalidProbe), "GUARD", 0, 0));

        var permitProbe = new W6RitualTeleportFailureProbe();
        var permitRejected = actor(permitProbe, true); permitRejected.invalidatePermitGeneration = true;
        print("permit", permitProbe, permitRejected,
                permitProbe.placeRitualEntity(permitRejected, observedDestination(permitProbe), "GUARD", 0, 0));

        var externalProbe = new W6RitualTeleportFailureProbe();
        var externallyCancelled = actor(externalProbe, true); externallyCancelled.externalCancellation = true;
        print("external", externalProbe, externallyCancelled,
                externalProbe.placeRitualEntity(externallyCancelled,
                        observedDestination(externalProbe), "GUARD", 0, 0));

        var successProbe = new W6RitualTeleportFailureProbe();
        var placed = actor(successProbe, true);
        print("success", successProbe, placed,
                successProbe.placeRitualEntity(placed, observedDestination(successProbe), "GUARD", 0, 0));
    }
}
''',
        encoding="utf-8",
    )

    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(POLICY), str(probe)],
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
    expected = {
        "unowned": ["unowned", "actor-not-owned", "none", "0", "0", "false"],
        "invalid": ["invalid", "actor-invalid", "none", "0", "0", "false"],
        "permit": ["permit", "permit-rejected", "no-internal-permit", "1", "1", "false"],
        "external": ["external", "external-cancellation-before-validation", "none", "1", "1", "false"],
        "success": ["success", "placed", "none", "1", "0", "true"],
    }
    assert observed == expected, (
        "The probe exercised the real W6 placement, teleport wrapper, and listener bodies. "
        "Current diagnostics collapse distinct teleport failures into `teleport-refused`; "
        "observed rows: " + repr(observed)
    )
