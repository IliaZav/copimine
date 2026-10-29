package me.copimine.endevent.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exact identity checks for restoring Wave 4 obelisk progress without healing or respawning it. */
public final class Wave4ObeliskRecoveryPolicy {
    private static final String DESTROYED = "DESTROYED";

    public record RestoredTiming(long emergenceStartTick, long stageStartedTick,
                                 long activationTick, long nextPulseTick,
                                 long nextFireTick, long destroyAtTick) {
    }

    private Wave4ObeliskRecoveryPolicy() {
    }

    /**
     * Match the full persisted ordinal set to the current plan. Any missing,
     * duplicate, incompatible or unknown receipt requires explicit recovery.
     */
    public static Map<Integer, Wave4ObeliskRecoverySnapshot.ObeliskReceipt> reconcile(
            Wave4ObeliskRecoverySnapshot.Data checkpoint, int expectedCount,
            int expectedMaxHealth, Set<String> activeStages) {
        if (checkpoint == null || expectedCount < 1
                || expectedCount > 6 || expectedMaxHealth < 1 || activeStages == null) {
            throw new IllegalArgumentException("Wave 4 recovery plan is outside its bounds");
        }
        if (checkpoint.obelisks().size() != expectedCount) {
            throw new IllegalArgumentException("Wave 4 receipt count does not match the current plan");
        }
        if (checkpoint.mobsStarted() && checkpoint.pressurePackAttempts() < 1) {
            throw new IllegalArgumentException("Wave 4 mob-start receipt has no successful attempt");
        }

        Set<String> allowedActiveStages = new HashSet<>(activeStages);
        allowedActiveStages.remove(DESTROYED);
        Map<Integer, Wave4ObeliskRecoverySnapshot.ObeliskReceipt> byOrdinal = new HashMap<>();
        boolean allDestroyed = true;
        for (Wave4ObeliskRecoverySnapshot.ObeliskReceipt receipt : checkpoint.obelisks()) {
            if (receipt.ordinal() >= expectedCount) {
                throw new IllegalArgumentException("Wave 4 receipt ordinal is not in the current plan");
            }
            if (receipt.maxHealth() != expectedMaxHealth) {
                throw new IllegalArgumentException("Wave 4 receipt max health differs from the current plan");
            }
            boolean destroyed = DESTROYED.equals(receipt.stage());
            if (destroyed) {
                if (receipt.health() != 0 || receipt.emergenceElapsedTicks() != 0L
                        || receipt.nextPulseRemainingTicks() != 0L
                        || receipt.nextFireRemainingTicks() != 0L
                        || receipt.collapseRemainingTicks() != 0L) {
                    throw new IllegalArgumentException("Destroyed Wave 4 receipt has remaining health");
                }
            } else if (!allowedActiveStages.contains(receipt.stage())) {
                throw new IllegalArgumentException("Unknown Wave 4 receipt stage");
            } else if ("ACTIVE".equals(receipt.stage()) && receipt.health() == 0) {
                throw new IllegalArgumentException("Active Wave 4 receipt has no remaining health");
            } else if ("COLLAPSING".equals(receipt.stage()) && receipt.health() != 0) {
                throw new IllegalArgumentException("Collapsing Wave 4 receipt still has health");
            } else if (!"ACTIVE".equals(receipt.stage())
                    && !"COLLAPSING".equals(receipt.stage())
                    && receipt.health() != expectedMaxHealth) {
                throw new IllegalArgumentException("Wave 4 telegraph receipt is unexpectedly damaged");
            }
            validateTiming(receipt);
            if (byOrdinal.putIfAbsent(receipt.ordinal(), receipt) != null) {
                throw new IllegalArgumentException("Duplicate Wave 4 receipt ordinal");
            }
            allDestroyed &= destroyed;
        }
        for (int ordinal = 0; ordinal < expectedCount; ordinal++) {
            if (!byOrdinal.containsKey(ordinal)) {
                throw new IllegalArgumentException("Wave 4 checkpoint is missing an obelisk receipt");
            }
        }
        if (checkpoint.assaultComplete() != allDestroyed) {
            throw new IllegalArgumentException("Wave 4 completion and destroyed receipts disagree");
        }
        return Map.copyOf(byOrdinal);
    }

    /** Rebase saved relative timers onto the fresh event tick counter after restart. */
    public static RestoredTiming restoreTiming(long currentEventTick,
                                                Wave4ObeliskRecoverySnapshot.ObeliskReceipt receipt) {
        if (currentEventTick < 0L || receipt == null || DESTROYED.equals(receipt.stage())) {
            throw new IllegalArgumentException("Wave 4 restored timing input is outside its bounds");
        }
        long emergenceStart = currentEventTick - receipt.emergenceElapsedTicks();
        long stageOffset = stageStartOffset(receipt.stage());
        long stageElapsed = Math.max(0L, receipt.emergenceElapsedTicks() - stageOffset);
        long stageStarted = currentEventTick - stageElapsed;
        long activation = Math.addExact(emergenceStart, ObeliskGeometryPolicy.EMERGENCE_TICKS);
        long pulse = Math.addExact(currentEventTick, receipt.nextPulseRemainingTicks());
        long fire = Math.addExact(currentEventTick, receipt.nextFireRemainingTicks());
        long destroy = "COLLAPSING".equals(receipt.stage())
                ? Math.addExact(currentEventTick, receipt.collapseRemainingTicks()) : -1L;
        return new RestoredTiming(emergenceStart, stageStarted, activation, pulse, fire, destroy);
    }

    private static void validateTiming(Wave4ObeliskRecoverySnapshot.ObeliskReceipt receipt) {
        String stage = receipt.stage();
        long elapsed = receipt.emergenceElapsedTicks();
        if (DESTROYED.equals(stage)) {
            if (elapsed != 0L || receipt.nextPulseRemainingTicks() != 0L
                    || receipt.nextFireRemainingTicks() != 0L
                    || receipt.collapseRemainingTicks() != 0L) {
                throw new IllegalArgumentException("Destroyed Wave 4 receipt has remaining timers");
            }
            return;
        }
        if ("ACTIVE".equals(stage)) {
            if (elapsed != ObeliskGeometryPolicy.EMERGENCE_TICKS || receipt.collapseRemainingTicks() != 0L) {
                throw new IllegalArgumentException("Active Wave 4 receipt has an invalid emergence timer");
            }
            return;
        }
        if ("COLLAPSING".equals(stage)) {
            if (elapsed != ObeliskGeometryPolicy.EMERGENCE_TICKS
                    || receipt.nextPulseRemainingTicks() != 0L
                    || receipt.nextFireRemainingTicks() != 0L) {
                throw new IllegalArgumentException("Collapsing Wave 4 receipt has invalid combat timers");
            }
            return;
        }
        if (receipt.nextPulseRemainingTicks() != 0L || receipt.nextFireRemainingTicks() != 0L
                || receipt.collapseRemainingTicks() != 0L
                || !ObeliskGeometryPolicy.stageAt(elapsed).name().equals(stage)) {
            throw new IllegalArgumentException("Wave 4 telegraph receipt has an invalid stage timer");
        }
    }

    private static long stageStartOffset(String stage) {
        return switch (stage) {
            case "GROUND_WARNING" -> 0L;
            case "EMERGE_BASE" -> 10L;
            case "EMERGE_LOWER" -> 20L;
            case "EMERGE_CORE" -> 30L;
            case "EMERGE_UPPER" -> 40L;
            case "EMERGE_CROWN" -> 50L;
            case "AWAKEN" -> 60L;
            case "ACTIVE", "COLLAPSING" -> ObeliskGeometryPolicy.EMERGENCE_TICKS;
            default -> throw new IllegalArgumentException("Unknown Wave 4 receipt stage");
        };
    }
}
