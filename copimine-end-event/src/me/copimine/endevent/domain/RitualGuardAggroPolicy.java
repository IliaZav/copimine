package me.copimine.endevent.domain;

/**
 * Local wake and spawn-ring rules for the guards assigned to one caster.
 *
 * <p>W6-013: guards freely engage legal outside participants within the arena.
 * There is no GUARD/ENGAGE/RETURN post system, no six-block leash, no
 * return-to-mage timer and no teleport back into formation. What used to be a
 * "post" is only an initial spawn ring: it decides where a guard first appears
 * around its mage and is never used again. Everything else here asks a single
 * question -- should this guard notice that participant at all. Once it has a
 * target it simply fights with the engine's own mob AI.</p>
 */
public final class RitualGuardAggroPolicy {
    public static final double AGGRO_RADIUS_BLOCKS = 6.0D;
    public static final double INTERCEPT_HOLD_RADIUS_BLOCKS = 10.0D;
    /** Radius of the ring a guard is spawned on; not a leash. */
    public static final double SPAWN_RING_RADIUS_BLOCKS = 2.4D;

    private RitualGuardAggroPolicy() {
    }

    public static boolean shouldWake(double distanceToCaster, boolean casterAttacked,
                                     boolean guardAttacked, boolean rangedAttack) {
        return shouldWake(distanceToCaster, casterAttacked, guardAttacked, rangedAttack, false);
    }

    public static boolean shouldWake(double distanceToCaster, boolean casterAttacked,
                                     boolean guardAttacked, boolean rangedAttack, boolean alreadyEngaged) {
        boolean near = Double.isFinite(distanceToCaster)
                && distanceToCaster >= 0.0D
                && distanceToCaster <= (alreadyEngaged ? INTERCEPT_HOLD_RADIUS_BLOCKS : AGGRO_RADIUS_BLOCKS);
        return near || casterAttacked || guardAttacked || rangedAttack;
    }

    /** Return the closest/farthest eligible target, preserving caller order for ties. */
    public static int preferredTargetIndex(double[] distanceBlocks, boolean[] eligible,
                                           boolean preferDistant) {
        if (distanceBlocks == null || eligible == null
                || distanceBlocks.length != eligible.length) {
            return -1;
        }
        int selected = -1;
        double selectedDistance = preferDistant ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int index = 0; index < distanceBlocks.length; index++) {
            double distance = distanceBlocks[index];
            if (!eligible[index] || !Double.isFinite(distance) || distance < 0.0D) continue;
            if (selected < 0 || (preferDistant ? distance > selectedDistance : distance < selectedDistance)) {
                selected = index;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    /**
     * Spawn position of one guard on the ring around its mage.
     *
     * <p>Used once, when the guard is created. Nothing returns the guard here
     * afterwards, so this is placement, not a post.</p>
     */
    public static RingOffset spawnRingOffset(int slot, int count, double outwardAngle) {
        if (count < 1 || count > 5 || slot < 0 || slot >= count || !Double.isFinite(outwardAngle)) {
            throw new IllegalArgumentException("invalid guard spawn ring slot");
        }
        double spread = count == 1 ? 0.0D
                : (slot / (double) count) * Math.PI * 2.0D;
        double angle = outwardAngle + spread;
        return new RingOffset(Math.cos(angle) * SPAWN_RING_RADIUS_BLOCKS,
                Math.sin(angle) * SPAWN_RING_RADIUS_BLOCKS);
    }

    /** A horizontal offset from the mage's spawn point. */
    public record RingOffset(double x, double z) {
    }
}