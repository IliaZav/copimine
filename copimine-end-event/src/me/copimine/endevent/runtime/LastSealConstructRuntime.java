package me.copimine.endevent.runtime;

import java.util.*;
import me.copimine.protocol.KaguneSpellFrame;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** One finite, non-destructible spell; no guardian HP, AI mobs, grab or respawn owner. */
public final class LastSealConstructRuntime {
    public record Scope(String event, long generation, UUID boss) { }
    public interface Host {
        Scope scope(LivingEntity boss);
        boolean active(LivingEntity boss);
        List<Player> participants();
        Location floor(Location near);
        boolean traversable(Location feet);
        ItemDisplay create(Location origin, KaguneSpellFrame initial);
        void publish(ItemDisplay carrier, KaguneSpellFrame frame);
        default void presentation(Location origin, KaguneSpellFrame frame) { }
        default void started(Location origin, int kind, List<KaguneSpellFrame.Point> points) { }
        void warning(Location origin, List<KaguneSpellFrame.Point> points, int kind);
        void impact(Location origin, int kind);
        boolean hit(LivingEntity boss, Player player, double damage);
        void remove(ItemDisplay carrier);
        void finished(Scope scope, String reason);
        void failed(String reason);
    }
    private record FallLease(UUID world, long generation, long expiresNanos) { }
    private final Host host;
    private final Set<UUID> hits = new HashSet<>();
    // Damage listeners may reenter the encounter. A reservation is not an
    // accepted-hit receipt, and belongs to this exact synchronous contact only.
    private final Map<UUID, Object> contactReservations = new HashMap<>();
    private final Map<UUID, Location> previousPlayers = new HashMap<>();
    private final Map<UUID, FallLease> falls = new HashMap<>();
    private Scope scope;
    private ItemDisplay carrier;
    private Location origin;
    private List<KaguneSpellFrame.Point> points = List.of();
    private int kind;
    private double heading;
    private long started, lastTick = Long.MIN_VALUE, nextCleanup;
    private boolean active, impactPlayed;
    public LastSealConstructRuntime(Host host) { this.host = Objects.requireNonNull(host); }
    public boolean active() { return active; }
    /** True once this lane is cancelled and its carrier is fully retired. */
    public boolean done() { return !active && carrier == null; }

    public boolean start(LivingEntity boss, Player target, int kind, long tick) {
        if (active || boss == null || target == null || !host.active(boss)
                || kind < KaguneSpellFrame.STAB || kind > KaguneSpellFrame.ERUPTION) return false;
        if (!retire()) return false;
        try {
            Scope owner = host.scope(boss);
            if (owner == null || owner.event() == null || owner.event().isBlank() || owner.generation() <= 0) return false;
            // Lock the chosen ground before the warning. Movement can dodge either spell.
            Location ground = host.floor(target.getLocation());
            if (ground == null || !host.traversable(ground)) return false;
            Location at = ground.clone();
            double angle;
            if (kind == KaguneSpellFrame.STAB) {
                Vector approach = target.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                if (approach.lengthSquared() < .01D) approach = new Vector(0, 0, 1);
                approach.normalize();
                at = host.floor(ground.clone().subtract(approach.clone().multiply(2.5D)));
                if (at == null || !host.traversable(at)) return false;
                angle = Math.atan2(approach.getZ(), approach.getX());
                // Do not thrust through a wall/Core to reach the locked point.
                for (int i = 0; i <= 6; i++) {
                    Location along = at.clone().add(approach.clone().multiply(i * .5D));
                    if (!host.traversable(along)) return false;
                }
            } else angle = 0D;
            List<KaguneSpellFrame.Point> planned = kind == KaguneSpellFrame.STAB
                    ? List.of(new KaguneSpellFrame.Point(0, 0, 0)) : eruptionFootprints(at, target, boss.getLocation());
            scope = owner; origin = at; points = List.copyOf(planned); this.kind = kind; heading = angle;
            started = tick; lastTick = Long.MIN_VALUE; hits.clear(); previousPlayers.clear(); impactPlayed = false;
            falls.values().removeIf(lease -> System.nanoTime() >= lease.expiresNanos());
            carrier = host.create(origin, frame(0));
            if (carrier == null || !carrier.isValid()) throw new IllegalStateException("construct-carrier-refused");
            host.publish(carrier, frame(0));
            active = true;
            host.started(origin, kind, points);
            host.warning(origin, points, kind);
            return true;
        } catch (RuntimeException failure) { cancel("admission-failed"); host.failed(failure.toString()); return false; }
    }

    private List<KaguneSpellFrame.Point> eruptionFootprints(Location root, Player primary, Location boss) {
        List<KaguneSpellFrame.Point> result = new ArrayList<>(5);
        result.add(new KaguneSpellFrame.Point(0, 0, 0));
        List<Location> teammates = new ArrayList<>();
        for (Player member : host.participants()) {
            if (member != null && !member.getUniqueId().equals(primary.getUniqueId())
                    && member.isOnline() && !member.isDead() && root.getWorld().equals(member.getWorld())) {
                teammates.add(member.getLocation());
            }
        }
        teammates.sort(Comparator.comparingDouble(root::distanceSquared));
        // Keep roots apart so a cluster reads as one threat and still has an exit.
        for (Location teammate : teammates) {
            addEruptionFootprint(root, teammate, result);
            if (result.size() == 4) break;
        }
        double rotation = Math.atan2(root.getZ() - boss.getZ(), root.getX() - boss.getX());
        for (int i = 0; i < 4 && result.size() < 5; i++) {
            double angle = rotation + i * Math.PI / 2D;
            addEruptionFootprint(root,
                    root.clone().add(Math.cos(angle) * 2.6D, 0, Math.sin(angle) * 2.6D), result);
        }
        return result;
    }

    private void addEruptionFootprint(Location root, Location candidate, List<KaguneSpellFrame.Point> result) {
        Location feet = host.floor(candidate);
        if (feet == null || !root.getWorld().equals(feet.getWorld()) || !host.traversable(feet)) return;
        double x = feet.getX() - root.getX();
        double y = feet.getY() - root.getY();
        double z = feet.getZ() - root.getZ();
        if (x * x + z * z > 56.25D || Math.abs(y) > 1D) return;
        for (var existing : result) {
            double dx = x - existing.x(), dz = z - existing.z();
            if (dx * dx + dz * dz < 4D) return;
        }
        result.add(new KaguneSpellFrame.Point(x, y, z));
    }

    public void tick(LivingEntity boss, long tick) {
        if (!active) {
            if (carrier != null && tick >= nextCleanup) { nextCleanup = tick + 20L; retire(); }
            return;
        }
        if (tick == lastTick) return;
        if (boss == null || !host.active(boss) || !Objects.equals(scope, host.scope(boss))
                || carrier == null || !carrier.isValid() || tick < started || tick < lastTick) {
            cancel("owner-or-clock-lost"); return;
        }
        lastTick = tick;
        int age = (int) Math.min(1000L, tick - started);
        if (age >= 76) { cancel("complete"); return; }
        try {
            KaguneSpellFrame current = frame(age);
            host.publish(carrier, current); // Never damage from a frame that failed to publish.
            if (age % 4 == 0 && current.stage() != KaguneSpellFrame.WARNING)
                host.presentation(origin, current);
            if (current.stage() == KaguneSpellFrame.WARNING && age % 8 == 0) host.warning(origin, points, kind);
            if (current.canDamage() && !impactPlayed) { impactPlayed = true; host.impact(origin, kind); }
            for (Player player : host.participants()) {
                if (!active || !host.active(boss) || !Objects.equals(scope, host.scope(boss))) {
                    cancel("owner-changed-during-contact"); return;
                }
                if (player == null || !player.isOnline() || player.isDead() || !origin.getWorld().equals(player.getWorld())) continue;
                Location at = player.getLocation(), before = previousPlayers.put(player.getUniqueId(), at.clone());
                if (!current.canDamage() || hits.contains(player.getUniqueId())) continue;
                if (before == null || !before.getWorld().equals(at.getWorld()) || before.distanceSquared(at) > 25D) before = at;
                if (!current.contacts(relative(before), relative(at), player.getWidth() / 2D, player.getHeight())) continue;
                UUID playerId = player.getUniqueId();
                Object reservation = new Object();
                if (contactReservations.putIfAbsent(playerId, reservation) != null) continue;
                Scope contactScope = scope;
                ItemDisplay contactCarrier = carrier;
                long contactStarted = started;
                boolean accepted;
                try {
                    accepted = host.hit(boss, player, kind == KaguneSpellFrame.STAB ? 8D : 7D);
                } finally {
                    contactReservations.remove(playerId, reservation);
                }
                // A callback may finish this cast and admit another. Never write
                // its predecessor's receipt or cancel the newly admitted cast.
                if (!active || scope != contactScope || carrier != contactCarrier || started != contactStarted) return;
                if (!active || !host.active(boss) || !Objects.equals(scope, host.scope(boss))) {
                    cancel("owner-changed-after-contact"); return;
                }
                if (!accepted) continue;
                hits.add(playerId);
                if (kind == KaguneSpellFrame.ERUPTION) {
                    falls.put(player.getUniqueId(), new FallLease(player.getWorld().getUID(), scope.generation(), System.nanoTime()+6_000_000_000L));
                    player.setFallDistance(0F); player.setVelocity(new Vector(0D, .98D, 0D));
                } else {
                    player.setVelocity(new Vector(Math.cos(heading)*.32D, .2D, Math.sin(heading)*.32D));
                }
            }
        } catch (RuntimeException failure) { cancel("frame-failed"); host.failed(failure.toString()); }
    }

    private KaguneSpellFrame.Point relative(Location at) {
        return new KaguneSpellFrame.Point(at.getX()-origin.getX(), at.getY()-origin.getY(), at.getZ()-origin.getZ());
    }

    private KaguneSpellFrame frame(int age) {
        int stage = age < 30 ? KaguneSpellFrame.WARNING : age < 46 ? KaguneSpellFrame.EMERGING
                : age < 58 ? KaguneSpellFrame.STRIKING : KaguneSpellFrame.RETRACTING;
        int start = stage == 0 ? 0 : stage == 1 ? 30 : stage == 2 ? 46 : 58;
        int length = stage == 0 ? 30 : stage == 1 ? 16 : stage == 2 ? 12 : 18;
        return new KaguneSpellFrame(kind, stage, Math.min(1D, (age-start)/(double)length),
                Math.max(0D, (age-start-1)/(double)length), heading, points);
    }

    public boolean consumeFall(Player player, long generation) {
        FallLease lease = falls.remove(player.getUniqueId());
        falls.values().removeIf(value -> System.nanoTime() >= value.expiresNanos());
        return lease != null && lease.generation() == generation && System.nanoTime() < lease.expiresNanos()
                && lease.world().equals(player.getWorld().getUID());
    }

    private boolean retire() {
        if (carrier == null) return true;
        try { host.remove(carrier); if (!carrier.isValid()) { carrier = null; return true; } }
        catch (RuntimeException ignored) { /* Keep the UUID for bounded cleanup retries. */ }
        return false;
    }

    public void cancel(String reason) {
        boolean running = active; active = false; retire(); hits.clear(); previousPlayers.clear();
        if (running) host.finished(scope, reason);
    }
}
