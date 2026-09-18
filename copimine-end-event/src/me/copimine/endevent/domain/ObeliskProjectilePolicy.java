package me.copimine.endevent.domain;

import java.util.UUID;

/** Immutable identity/state contract for one event-owned Rift Fireball. */
public final class ObeliskProjectilePolicy {
    /**
     * Large Fireballs are launched above a five-block obelisk and the useful
     * return vector points back down toward that obelisk.  A player may
     * therefore look past the projectile toward the return target while the
     * Bukkit attack event remains the authoritative proof of a real hit.
     */
    public static final double DEFAULT_MAX_REFLECTION_AIM_DEGREES = 120.0D;

    private ObeliskProjectilePolicy() {
    }

    public enum Kind {
        OBELISK_RIFT_FIREBALL,
        VANILLA_FIREBALL
    }

    public enum State {
        OUTBOUND,
        REFLECTED,
        CONSUMED
    }

    /**
     * Return true only when the player's aim is inside the finite reflection
     * cone.  This keeps a melee hit from becoming an automatic 180-degree
     * redirect while remaining independent of Bukkit's vector classes.
     */
    public static boolean withinAimCone(double aimX, double aimY, double aimZ,
                                        double targetX, double targetY, double targetZ,
                                        double maxDegrees) {
        if (!Double.isFinite(aimX) || !Double.isFinite(aimY) || !Double.isFinite(aimZ)
                || !Double.isFinite(targetX) || !Double.isFinite(targetY)
                || !Double.isFinite(targetZ) || !Double.isFinite(maxDegrees)
                || maxDegrees < 0.0D || maxDegrees > 180.0D) {
            return false;
        }
        double aimLength = Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ);
        double targetLength = Math.sqrt(targetX * targetX + targetY * targetY + targetZ * targetZ);
        if (aimLength < 1.0E-9D || targetLength < 1.0E-9D) {
            return false;
        }
        double cosine = (aimX * targetX + aimY * targetY + aimZ * targetZ)
                / (aimLength * targetLength);
        double threshold = Math.cos(Math.toRadians(maxDegrees));
        return cosine + 1.0E-9D >= threshold;
    }

    public record Context(UUID projectileId, UUID eventId, long generation,
                          UUID sourceObeliskId, long createdTick, long expiresTick,
                          Kind kind, State state, UUID reflectedBy,
                          long reflectionTick) {
        public Context(UUID projectileId, UUID eventId, long generation,
                       UUID sourceObeliskId, long createdTick, long expiresTick) {
            this(projectileId, eventId, generation, sourceObeliskId, createdTick,
                    expiresTick, Kind.OBELISK_RIFT_FIREBALL, State.OUTBOUND, null, -1L);
        }

        public Context {
            createdTick = Math.max(0L, createdTick);
            expiresTick = Math.max(createdTick + 1L, expiresTick);
            kind = kind == null ? Kind.OBELISK_RIFT_FIREBALL : kind;
            state = state == null ? State.OUTBOUND : state;
            reflectionTick = reflectionTick < 0L ? -1L : reflectionTick;
        }

        public boolean current(UUID expectedEvent, long expectedGeneration) {
            return expectedEvent != null && expectedEvent.equals(eventId)
                    && generation == expectedGeneration;
        }

        public boolean expired(long tick) {
            return tick >= expiresTick;
        }

        public Context reflect(UUID playerId, long tick) {
            if (state != State.OUTBOUND || playerId == null) {
                return this;
            }
            return new Context(projectileId, eventId, generation, sourceObeliskId,
                    createdTick, expiresTick, kind, State.REFLECTED, playerId,
                    Math.max(createdTick, tick));
        }

        public Context consume() {
            if (state == State.CONSUMED) {
                return this;
            }
            return new Context(projectileId, eventId, generation, sourceObeliskId,
                    createdTick, expiresTick, kind, State.CONSUMED, reflectedBy,
                    reflectionTick);
        }
    }
}
