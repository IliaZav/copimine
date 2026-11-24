package me.copimine.endevent.domain;

import java.util.List;

/**
 * N23-P1-11 (starvation half): which Guardian spells may run while a temporary
 * major-cast owner holds the encounter.
 *
 * <p>{@code tickCurrentBoss} used to {@code return} outright while a Last Seal
 * kagune ring was live, so a multi-row ring timeline left the phase with no
 * ranged pressure at all, even though ordinary melee already runs above that
 * line. The ring carrier owns its own floor presentation, not the Guardian's
 * pose, so one light, telegraphed, dodgeable projectile remains a compatible
 * alternative. Every body- or floor-owning cast still defers, and an ambush
 * grab still pauses every major cast.</p>
 *
 * <p>The policy decides compatibility only. It never removes a safety gate,
 * never shortens a telegraph and never grants invulnerability. Overlap fairness
 * for the one compatible spell stays a measured, native question.</p>
 */
public final class BossCastAvailabilityPolicy {

    /** The temporary owner of the major cast slot, if any. */
    public enum BusySlot { NONE, RING, AMBUSH }

    private BossCastAvailabilityPolicy() {
    }

    /**
     * Spells safe to overlap a live Last Seal ring: ranged, telegraphed and
     * dodgeable, and not body- or floor-owning. This is exactly one spell by
     * design — the ring must not become a second major-cast owner.
     */
    public static boolean compatibleWhileRing(EndRiftAiPolicy.BossSpell spell) {
        return spell == EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE;
    }

    /** True when this slot pauses every major cast rather than a subset. */
    public static boolean blocksAllCasts(BusySlot slot) {
        return slot == BusySlot.AMBUSH;
    }

    /**
     * The dispatchable pool for one slot, preserving the incoming order.
     * {@link BusySlot#AMBUSH} yields an empty list, {@link BusySlot#RING} keeps
     * only ring-compatible spells, and {@link BusySlot#NONE} (or a null slot)
     * keeps everything. The result is always an immutable copy, so a caller
     * cannot mutate the policy's view back into the schedule.
     */
    public static List<EndRiftAiPolicy.BossSpell> filter(List<EndRiftAiPolicy.BossSpell> pool, BusySlot slot) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        if (slot == null || slot == BusySlot.NONE) {
            return List.copyOf(pool);
        }
        if (slot == BusySlot.AMBUSH) {
            return List.of();
        }
        return pool.stream().filter(BossCastAvailabilityPolicy::compatibleWhileRing).toList();
    }
}
