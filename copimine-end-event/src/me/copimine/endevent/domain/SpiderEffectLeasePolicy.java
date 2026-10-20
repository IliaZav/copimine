package me.copimine.endevent.domain;

/** Pure ownership policy for the temporary potion effects applied by a Spider Web Shot. */
public final class SpiderEffectLeasePolicy {
    private SpiderEffectLeasePolicy() { }

    private static final long DURATION_TOLERANCE_TICKS = 2L;

    public record Owner(String eventId, long generation) {
        public Owner {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("eventId must not be blank");
            }
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must not be negative");
            }
        }
    }

    /** A potion effect snapshot. Duration -1 represents an infinite effect. */
    public record Effect(int amplifier, int duration, boolean ambient, boolean particles, boolean icon) {
        public Effect {
            if (amplifier < 0) throw new IllegalArgumentException("amplifier must not be negative");
            if (duration == 0 || duration < -1) {
                throw new IllegalArgumentException("duration must be positive or -1 for infinite");
            }
        }
    }

    /** The original effect is captured once; overlapping hits only refresh the applied effect. */
    public record Lease(Owner owner, Effect original, long capturedAtTick,
                        long appliedAtTick, Effect applied) {
        public Lease {
            if (owner == null || applied == null) throw new IllegalArgumentException("owner and applied are required");
            if (capturedAtTick < 0L) throw new IllegalArgumentException("capturedAtTick must not be negative");
            if (appliedAtTick < capturedAtTick) {
                throw new IllegalArgumentException("appliedAtTick must not precede capturedAtTick");
            }
            if (applied.duration() < 1) throw new IllegalArgumentException("applied effect must be finite");
        }
    }

    public enum ApplyAction {
        APPLIED,
        REFRESHED,
        PRESERVED_EXISTING,
        OWNER_MISMATCH,
        FOREIGN_EFFECT_CHANGED
    }

    public record ApplyResult(ApplyAction action, Lease lease) {
        public boolean shouldApplyEffect() {
            return action == ApplyAction.APPLIED || action == ApplyAction.REFRESHED;
        }
    }

    public enum ReleaseAction {
        NO_LEASE,
        OWNER_MISMATCH,
        FOREIGN_EFFECT_RETAINED,
        REMOVED_OWNED_EFFECT
    }

    public record ReleaseResult(ReleaseAction action, Lease lease, Effect restoredEffect,
                                boolean removeCurrentEffect) { }

    /**
     * Decide whether to apply a temporary effect or refresh the current lease.
     * Callers keep one lease per player and potion type.
     */
    public static ApplyResult applyOrRefresh(Owner owner, Lease lease, Effect current,
                                             long nowTick, Effect applied) {
        if (owner == null || applied == null) throw new IllegalArgumentException("owner and applied are required");
        if (nowTick < 0L) throw new IllegalArgumentException("nowTick must not be negative");
        if (applied.duration() < 1) throw new IllegalArgumentException("applied effect must be finite");

        if (lease != null) {
            if (!owner.equals(lease.owner())) {
                return new ApplyResult(ApplyAction.OWNER_MISMATCH, lease);
            }
            if (!matchesApplied(current, lease, nowTick)) {
                return new ApplyResult(ApplyAction.FOREIGN_EFFECT_CHANGED, null);
            }
            return new ApplyResult(ApplyAction.REFRESHED,
                    new Lease(owner, lease.original(), lease.capturedAtTick(), nowTick, applied));
        }

        if (!mayReplace(current, applied)) {
            return new ApplyResult(ApplyAction.PRESERVED_EXISTING, null);
        }
        return new ApplyResult(ApplyAction.APPLIED, new Lease(owner, current, nowTick, nowTick, applied));
    }

    /** Remove only a still-matching effect owned by this event generation. */
    public static ReleaseResult release(Owner owner, Lease lease, Effect current,
                                        long nowTick, boolean living) {
        if (owner == null) throw new IllegalArgumentException("owner is required");
        if (nowTick < 0L) throw new IllegalArgumentException("nowTick must not be negative");
        if (lease == null) {
            return new ReleaseResult(ReleaseAction.NO_LEASE, null, null, false);
        }
        if (!owner.equals(lease.owner())) {
            return new ReleaseResult(ReleaseAction.OWNER_MISMATCH, lease, null, false);
        }
        if (!matchesApplied(current, lease, nowTick)) {
            return new ReleaseResult(ReleaseAction.FOREIGN_EFFECT_RETAINED, null, null, false);
        }

        Effect restore = null;
        if (living && lease.original() != null) {
            int remaining = BlackFogEffectLeasePolicy.remainingDuration(
                    lease.original().duration(), lease.capturedAtTick(), nowTick);
            if (remaining != 0) {
                Effect original = lease.original();
                restore = new Effect(original.amplifier(), remaining,
                        original.ambient(), original.particles(), original.icon());
            }
        }
        return new ReleaseResult(ReleaseAction.REMOVED_OWNED_EFFECT, null, restore, true);
    }

    /** Match the signature and remaining duration expected from the lease's last application. */
    public static boolean matchesApplied(Effect current, Lease lease, long nowTick) {
        if (current == null || lease == null || nowTick < lease.appliedAtTick()
                || current.amplifier() != lease.applied().amplifier()
                || current.ambient() != lease.applied().ambient()
                || current.particles() != lease.applied().particles()
                || current.icon() != lease.applied().icon()
                || current.duration() <= 0) {
            return false;
        }
        long elapsed = nowTick - lease.appliedAtTick();
        if (elapsed > (long) lease.applied().duration() + DURATION_TOLERANCE_TICKS) {
            return false;
        }
        long expectedDuration = Math.max(0L, (long) lease.applied().duration() - elapsed);
        return Math.abs((long) current.duration() - expectedDuration) <= DURATION_TOLERANCE_TICKS;
    }

    private static boolean mayReplace(Effect current, Effect applied) {
        return current == null
                || current.duration() != -1 && current.amplifier() < applied.amplifier();
    }
}
