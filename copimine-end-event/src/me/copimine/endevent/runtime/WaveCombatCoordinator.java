package me.copimine.endevent.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thin group coordination on the existing server tick; owns no tasks or entities. */
public final class WaveCombatCoordinator {
    private long generation = Long.MIN_VALUE;
    private long nonce;
    private Lease lease;

    public record Lease(long generation, long nonce, UUID owner, UUID target, long expiresTick) { }

    public void begin(long generation) {
        if (generation <= 0) throw new IllegalArgumentException("positive generation required");
        this.generation = generation;
        lease = null;
    }
    public boolean owns(long expected) { return expected > 0 && expected == generation; }

    public Lease reserve(long expected, UUID owner, UUID target, long now, long expires) {
        expire(now);
        if (expected != generation || owner == null || target == null || now < 0
                || expires <= now || expires - now > 160 || lease != null) return null;
        lease = new Lease(expected, ++nonce, owner, target, expires);
        return lease;
    }

    public boolean valid(Lease token, long expected, long now) {
        expire(now);
        return token != null && token == lease && expected == generation
                && token.generation() == expected;
    }

    public boolean busy(long expected, long now) {
        expire(now);
        return expected == generation && lease != null;
    }

    public boolean recovering(UUID owner, long expected, long now) {
        return busy(expected, now) && lease.owner().equals(owner);
    }

    public Lease currentLease(long expected, long now) {
        return busy(expected, now) ? lease : null;
    }

    public void release(Lease token) { if (token != null && token == lease) lease = null; }
    public void remove(UUID owner) { if (lease != null && lease.owner().equals(owner)) lease = null; }
    public void cancelTarget(UUID target) { if (lease != null && lease.target().equals(target)) lease = null; }
    public void clear() { generation = Long.MIN_VALUE; lease = null; }
    private void expire(long now) { if (lease != null && now >= lease.expiresTick()) lease = null; }

    /** Only one third of hunters focus the mark. Other jobs pressure its allies. */
    public static UUID chooseTarget(List<UUID> eligible, UUID current, UUID marked,
                                    int wave, int slot, Map<UUID, Integer> loads) {
        if (eligible == null || eligible.isEmpty()) return null;
        Map<UUID, Integer> pressure = loads == null ? Map.of() : loads;
        boolean hunt = wave == 2 && marked != null && eligible.contains(marked);
        if (hunt && Math.floorMod(slot, 3) == 0) return marked;
        List<UUID> pool = hunt && eligible.size() > 1
                ? eligible.stream().filter(id -> !id.equals(marked)).toList() : eligible;
        int lowest = pool.stream().mapToInt(id -> pressure.getOrDefault(id, 0)).min().orElse(0);
        List<UUID> leastBusy = pool.stream().filter(id -> pressure.getOrDefault(id, 0) == lowest).toList();
        if (leastBusy.contains(current)) return current;
        return leastBusy.get(Math.floorMod(slot, leastBusy.size()));
    }

    public static boolean refreshPath(long now, long next, double destinationDeltaSquared,
                                       boolean intentionChanged, boolean pathActive) {
        if (!Double.isFinite(destinationDeltaSquared) || destinationDeltaSquared < 0) return false;
        return intentionChanged || destinationDeltaSquared >= 6.25D
                || now >= next && !pathActive;
    }
}
