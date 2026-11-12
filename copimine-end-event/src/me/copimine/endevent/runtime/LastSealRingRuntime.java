package me.copimine.endevent.runtime;

import java.util.*;
import me.copimine.endevent.domain.KaguneRingGapPolicy;
import me.copimine.endevent.domain.KaguneGuardianFightPolicy;
import me.copimine.protocol.KaguneRingFrame;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Owns a complete ring cast; Paper supplies IO, never its timeline or geometry. */
public final class LastSealRingRuntime {
    public static final int EMERGE_TICKS = 20, TRAVEL_TICKS = 40, RETRACT_TICKS = 18, GAP_TICKS = 8;
    public static final int ROW_TICKS = EMERGE_TICKS + TRAVEL_TICKS + RETRACT_TICKS + GAP_TICKS;
    public static final int CAST_TICKS = ROW_TICKS * KaguneRingGapPolicy.ROW_COUNT;
    public record Scope(String event, long generation, UUID boss) { }
    public interface Host {
        Scope scope(LivingEntity boss);
        boolean active(LivingEntity boss);
        Location floorOrigin(LivingEntity boss);
        List<Player> participants();
        boolean traversable(Location feet);
        ItemDisplay createCarrier(Location origin);
        void publish(ItemDisplay carrier, KaguneRingFrame frame);
        void remove(ItemDisplay carrier);
        boolean hit(LivingEntity boss, Player player, double damage);
        void finished(Scope scope, String reason);
        void failed(String reason);
    }
    private record FallLease(UUID world, long generation, long expiresNanos) { }
    private final Host host;
    private final Set<UUID> hitPlayers = new HashSet<>();
    private final Map<UUID, Location> previousPlayers = new HashMap<>();
    private final Map<UUID, FallLease> safeFalls = new HashMap<>();
    private Scope scope;
    private Location origin;
    private KaguneRingGapPolicy.Layout layout;
    private ItemDisplay carrier;
    private long startedTick, lastTick = Long.MIN_VALUE;
    private boolean active, cleanupFailureReported;
    private long nextCleanupTick;

    public LastSealRingRuntime(Host host) { this.host = Objects.requireNonNull(host); }
    public boolean active() { return active; }

    public boolean start(LivingEntity boss, int admittedPlayers, long tick, long seed) {
        if (active) return false;
        try { return startChecked(boss, admittedPlayers, tick, seed); }
        catch (RuntimeException failure) {
            cancel("admission-failed"); host.failed(failure.getMessage()); return false;
        }
    }

    private boolean startChecked(LivingEntity boss, int admittedPlayers, long tick, long seed) {
        if (active || boss == null || !host.active(boss)) return false;
        safeFalls.values().removeIf(value -> System.nanoTime() >= value.expiresNanos());
        if (!retireCarrier()) return false;
        Location at = host.floorOrigin(boss);
        if (at == null || at.getWorld() == null) return false;
        List<Double> players = new ArrayList<>(), blocked = new ArrayList<>();
        for (Player player : host.participants()) {
            Location feet = player.getLocation();
            if (at.getWorld().equals(feet.getWorld()))
                players.add(Math.atan2(feet.getZ() - at.getZ(), feet.getX() - at.getX()));
        }
        if (players.isEmpty()) return false;
        // Escape passages must remain usable over each row's entire travel.
        for (int step = 0; step < 96; step++) {
            double angle = Math.PI * 2D * step / 96;
            boolean clear = true;
            for (double radius = 7.5D; radius <= 19.5D; radius += .75D) {
                if (!host.traversable(at.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius))) {
                    clear = false; break;
                }
            }
            if (!clear) blocked.add(KaguneRingFrame.wrap(angle));
        }
        var planned = KaguneRingGapPolicy.layout(seed, admittedPlayers, players, blocked);
        if (planned == null || !planned.feasible()) return false;
        scope = host.scope(boss);
        if (scope == null || scope.event() == null || scope.event().isBlank() || scope.generation() <= 0) return false;
        origin = at.clone(); layout = planned; startedTick = tick; lastTick = Long.MIN_VALUE;
        hitPlayers.clear(); previousPlayers.clear();
        try {
            carrier = host.createCarrier(origin.clone());
            if (carrier == null || !carrier.isValid()) throw new IllegalStateException("carrier-refused");
            host.publish(carrier, frame(0, 0));
            active = true;
            return true;
        } catch (RuntimeException failure) {
            cancel("presentation-refused"); host.failed(failure.getMessage()); return false;
        }
    }

    public void tick(LivingEntity boss, long tick) {
        if (!active) {
            if (carrier != null && tick >= nextCleanupTick) {
                nextCleanupTick = tick + 20L;
                retireCarrier();
            }
            return;
        }
        if (lastTick == tick) return;
        if (boss == null || !host.active(boss) || !Objects.equals(scope, host.scope(boss))
                || carrier == null || !carrier.isValid()) { cancel("owner-or-carrier-lost"); return; }
        if (tick < startedTick || tick < lastTick) { cancel("clock-rewound"); return; }
        lastTick = tick;
        long elapsed = tick - startedTick;
        if (elapsed >= CAST_TICKS) { cancel("complete"); return; }
        int row = (int) (elapsed / ROW_TICKS), age = (int) (elapsed % ROW_TICKS);
        KaguneRingFrame frame = frame(row, age);
        try {
            // Publish first. A failed frame cannot deal invisible damage.
            host.publish(carrier, frame);
            for (Player player : host.participants()) {
                if (player == null || !player.isOnline() || player.isDead()
                        || !origin.getWorld().equals(player.getWorld())) continue;
                Location at = player.getLocation(), before = previousPlayers.put(player.getUniqueId(), at.clone());
                if (!frame.canDamage() || hitPlayers.contains(player.getUniqueId())) continue;
                if (before == null || !before.getWorld().equals(at.getWorld()) || before.distanceSquared(at) > 25D) before = at;
                if (!contacts(frame, before, at)) continue;
                hitPlayers.add(player.getUniqueId());
                if (!host.hit(boss, player, KaguneGuardianFightPolicy.BASE_STRIKE_DAMAGE)) {
                    hitPlayers.remove(player.getUniqueId()); continue;
                }
                // About five blocks vertically under vanilla gravity; no horizontal fling into walls.
                safeFalls.put(player.getUniqueId(), new FallLease(player.getWorld().getUID(),
                        scope.generation(), System.nanoTime() + 6_000_000_000L));
                player.setFallDistance(0F);
                player.setVelocity(new Vector(0D, .98D, 0D));
            }
        } catch (RuntimeException failure) { cancel("frame-failed"); host.failed(failure.getMessage()); }
    }

    private KaguneRingFrame frame(int rowIndex, int age) {
        var row = layout.row(rowIndex);
        double progress = Math.max(0D, Math.min(1D, (age - EMERGE_TICKS) / (double) TRAVEL_TICKS));
        double prior = Math.max(0D, Math.min(1D, (age - EMERGE_TICKS - 1D) / TRAVEL_TICKS));
        int stage = age < EMERGE_TICKS ? KaguneRingFrame.EMERGE
                : age < EMERGE_TICKS + TRAVEL_TICKS ? KaguneRingFrame.TRAVEL : KaguneRingFrame.RETRACT;
        double rise = stage == KaguneRingFrame.EMERGE ? age / (double) EMERGE_TICKS
                : stage == KaguneRingFrame.RETRACT ? Math.max(0D, 1D - (age - EMERGE_TICKS - TRAVEL_TICKS) / (double) RETRACT_TICKS) : 1D;
        List<Double> gaps = new ArrayList<>();
        for (double angle : row.gapCentres()) gaps.add(KaguneRingFrame.wrap(angle));
        return new KaguneRingFrame(rowIndex, stage,
                row.emergeRadius() + KaguneRingGapPolicy.ROW_TRAVEL_BLOCKS * progress,
                row.emergeRadius() + KaguneRingGapPolicy.ROW_TRAVEL_BLOCKS * prior, rise, progress, gaps);
    }

    private boolean contacts(KaguneRingFrame frame, Location before, Location at) {
        double x = at.getX() - origin.getX(), z = at.getZ() - origin.getZ();
        if (frame.insideGap(Math.atan2(z, x))) return false;
        double dy = at.getY() - origin.getY();
        if (dy < -.3D || dy > 3.5D) return false;
        double bx = before.getX() - origin.getX(), bz = before.getZ() - origin.getZ();
        for (int i = 0; i < frame.instances(); i++) {
            if (!frame.drawsInstance(i)) continue;
            double angle = frame.direction(i), cos = Math.cos(angle), sin = Math.sin(angle);
            double sx = bx - cos * frame.previousRadius(), sz = bz - sin * frame.previousRadius();
            double ex = x - cos * frame.radius(), ez = z - sin * frame.radius();
            double vx = ex - sx, vz = ez - sz, squared = vx * vx + vz * vz;
            double t = squared < 1E-9D ? 0D : Math.max(0D, Math.min(1D, -(sx * vx + sz * vz) / squared));
            double cx = sx + vx * t, cz = sz + vz * t;
            if (cx * cx + cz * cz <= KaguneRingFrame.CONTACT_RADIUS * KaguneRingFrame.CONTACT_RADIUS) return true;
        }
        return false;
    }

    /** Accepted launch protection survives visual retirement until one landing. */
    public boolean consumeFall(Player player, long generation) {
        FallLease lease = safeFalls.remove(player.getUniqueId());
        safeFalls.values().removeIf(value -> System.nanoTime() >= value.expiresNanos());
        return lease != null && lease.generation() == generation && System.nanoTime() < lease.expiresNanos()
                && lease.world().equals(player.getWorld().getUID());
    }

    private boolean retireCarrier() {
        if (carrier == null) return true;
        try {
            host.remove(carrier);
            if (!carrier.isValid()) {
                carrier = null; cleanupFailureReported = false; return true;
            }
        } catch (RuntimeException unavailable) {
            // Retain the real UUID for bounded retries and encounter cleanup.
        }
        if (!cleanupFailureReported) {
            cleanupFailureReported = true; host.failed("carrier-cleanup-pending");
        }
        return false;
    }

    public void cancel(String reason) {
        boolean wasActive = active;
        active = false;
        retireCarrier();
        layout = null; previousPlayers.clear(); hitPlayers.clear();
        if (wasActive) host.finished(scope, reason);
    }
}
