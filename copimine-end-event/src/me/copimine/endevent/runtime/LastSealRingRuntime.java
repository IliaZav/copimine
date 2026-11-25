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
    public static final int EMERGE_TICKS = KaguneRingFrame.EMERGE_TICKS, TRAVEL_TICKS = KaguneRingFrame.TRAVEL_TICKS,
            RETRACT_TICKS = KaguneRingFrame.RETRACT_TICKS, GAP_TICKS = KaguneRingFrame.GAP_TICKS;
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
        default void repelPrelude(Location origin, double progress, boolean release) { }
        default double combatRadius(Location origin) {
            return KaguneRingGapPolicy.ROW_EMERGE_RADIUS[0] + KaguneRingGapPolicy.ROW_TRAVEL_BLOCKS;
        }
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
    private long preludeTick = -1L;
    private boolean repelled;
    private double travelBlocks;
    private int travelTicks, rowTicks;

    public LastSealRingRuntime(Host host) { this.host = Objects.requireNonNull(host); }
    public boolean active() { return active; }

    public boolean start(LivingEntity boss, int admittedPlayers, long tick, long seed) {
        if (active) return false;
        try { return startChecked(boss, admittedPlayers, tick, seed); }
        catch (RuntimeException failure) {
            cancel("admission-failed"); host.failed(failure.getMessage()); return false;
        }
    }

    /** One owned warning -> repel -> pause -> normal three-row timeline. */
    public boolean startWithRepel(LivingEntity boss, int players, long tick, long seed) {
        if (!start(boss, players, tick, seed)) return false;
        preludeTick = tick; startedTick = tick + 30L; repelled = false;
        return true;
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
        // Escape passages must remain usable over each row's entire travel. Rows
        // now share one centre origin, so scan from the boss's feet to the rim.
        double innerRadius = KaguneRingGapPolicy.ROW_EMERGE_RADIUS[0];
        double outerRadius = host.combatRadius(at);
        if (!Double.isFinite(outerRadius) || outerRadius <= innerRadius || outerRadius > KaguneRingFrame.MAX_RADIUS) return false;
        travelBlocks = outerRadius - innerRadius;
        travelTicks = Math.max(TRAVEL_TICKS, (int) Math.ceil(travelBlocks / .275D));
        rowTicks = EMERGE_TICKS + travelTicks + RETRACT_TICKS + GAP_TICKS;
        for (int step = 0; step < 96; step++) {
            double angle = Math.PI * 2D * step / 96;
            boolean clear = true;
            // Sample both endpoints: the final step is clamped to the rim so an
            // obstacle sitting between the last 0.75 sample and outerRadius is
            // still seen, and a fully clear passage is still admitted.
            for (double radius = innerRadius; ; radius += .75D) {
                double sample = Math.min(radius, outerRadius);
                if (!host.traversable(at.clone().add(Math.cos(angle) * sample, 0, Math.sin(angle) * sample))) {
                    clear = false; break;
                }
                if (sample >= outerRadius) break;
            }
            if (!clear) blocked.add(KaguneRingFrame.wrap(angle));
        }
        var planned = KaguneRingGapPolicy.layout(seed, admittedPlayers, players, blocked);
        if (planned == null || !planned.feasible()) return false;
        scope = host.scope(boss);
        if (scope == null || scope.event() == null || scope.event().isBlank() || scope.generation() <= 0) return false;
        origin = at.clone(); layout = planned; startedTick = tick; lastTick = Long.MIN_VALUE;
        hitPlayers.clear(); previousPlayers.clear(); preludeTick = -1L; repelled = false;
        try {
            carrier = host.createCarrier(origin.clone());
            if (carrier == null || !carrier.isValid()) throw new IllegalStateException("carrier-refused");
            host.publish(carrier, frame(0, 0, 0));
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
        if (tick < lastTick || tick < (preludeTick >= 0 ? preludeTick : startedTick)) {
            cancel("clock-rewound"); return;
        }
        long priorTick = lastTick;
        lastTick = tick;
        if (preludeTick >= 0 && tick < startedTick) {
            try {
                long age = tick - preludeTick;
                boolean release = age >= 20L && !repelled;
                if (release) repelled = true;
                host.repelPrelude(origin.clone(), Math.min(1D, age / 20D), release);
            } catch (RuntimeException failure) { cancel("repel-failed"); host.failed(failure.toString()); }
            return;
        }
        long elapsed = tick - startedTick;
        if (elapsed >= rowTicks * KaguneRingGapPolicy.ROW_COUNT) { cancel("complete"); return; }
        int row = (int) (elapsed / rowTicks), age = (int) (elapsed % rowTicks);
        int priorAge = priorTick < startedTick ? age - 1
                : (int) Math.max(0L, priorTick - startedTick - (long) row * rowTicks);
        KaguneRingFrame frame = frame(row, age, priorAge);
        try {
            // Publish first. A failed frame cannot deal invisible damage.
            host.publish(carrier, frame);
            for (Player player : host.participants()) {
                if (!active || !host.active(boss) || !Objects.equals(scope, host.scope(boss))) {
                    cancel("owner-changed-during-contact"); return;
                }
                if (player == null || !player.isOnline() || player.isDead()
                        || !origin.getWorld().equals(player.getWorld())) continue;
                Location at = player.getLocation(), before = previousPlayers.put(player.getUniqueId(), at.clone());
                if (!frame.canDamage() || hitPlayers.contains(player.getUniqueId())) continue;
                if (before == null || !before.getWorld().equals(at.getWorld()) || before.distanceSquared(at) > 25D) before = at;
                if (!frame.contacts(before.getX()-origin.getX(),before.getY()-origin.getY(),before.getZ()-origin.getZ(),
                        at.getX()-origin.getX(),at.getY()-origin.getY(),at.getZ()-origin.getZ(),
                        player.getWidth()/2D,player.getHeight())) continue;
                hitPlayers.add(player.getUniqueId());
                if (!host.hit(boss, player, KaguneGuardianFightPolicy.BASE_STRIKE_DAMAGE)) {
                    hitPlayers.remove(player.getUniqueId()); continue;
                }
                if (!active || !host.active(boss) || !Objects.equals(scope, host.scope(boss))) {
                    cancel("owner-changed-after-contact"); return;
                }
                // About five blocks vertically under vanilla gravity; no horizontal fling into walls.
                safeFalls.put(player.getUniqueId(), new FallLease(player.getWorld().getUID(),
                        scope.generation(), System.nanoTime() + 6_000_000_000L));
                player.setFallDistance(0F);
                player.setVelocity(new Vector(0D, .98D, 0D));
            }
        } catch (RuntimeException failure) { cancel("frame-failed"); host.failed(failure.getMessage()); }
    }

    private KaguneRingFrame frame(int rowIndex, int age, int priorAge) {
        var row = layout.row(rowIndex);
        double progress = Math.clamp((age - EMERGE_TICKS) / (double) travelTicks, 0, 1);
        double prior = Math.clamp((priorAge - EMERGE_TICKS) / (double) travelTicks, 0, 1);
        int stage = age < EMERGE_TICKS ? KaguneRingFrame.EMERGE
                : age <= EMERGE_TICKS + travelTicks ? KaguneRingFrame.TRAVEL : KaguneRingFrame.RETRACT;
        double rise = stage == KaguneRingFrame.EMERGE ? age / (double) EMERGE_TICKS
                : stage == KaguneRingFrame.RETRACT ? Math.max(0D, 1D - (age - EMERGE_TICKS - travelTicks) / (double) RETRACT_TICKS) : 1D;
        List<Double> gaps = new ArrayList<>();
        for (double angle : row.gapCentres()) gaps.add(KaguneRingFrame.wrap(angle));
        return new KaguneRingFrame(rowIndex, stage,
                row.emergeRadius() + travelBlocks * progress,
                row.emergeRadius() + travelBlocks * prior, rise, progress, gaps);
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
