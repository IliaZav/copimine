package me.copimine.artifacts;

import java.util.List;

/**
 * Pure selection policy for the Angel Seal death-preservation artifact.
 *
 * <p>The Bukkit event handler is deliberately kept separate from this class:
 * the policy has no inventory or event side effects and can therefore be
 * regression-tested without starting Paper.</p>
 */
public final class AngelSealDeathPolicy {
    private AngelSealDeathPolicy() {
    }

    public enum Surface {
        STORAGE,
        ARMOR,
        OFFHAND
    }

    public record SealCandidate(Surface surface, int slot, String uniqueItemId, boolean authentic) {
    }

    public record Decision(boolean preserveInventory, SealCandidate selectedSeal) {
    }

    public static Decision decide(
            boolean deathCancelled,
            boolean keepInventoryAlreadyEnabled,
            boolean resurrectionAlreadyHandled,
            List<SealCandidate> candidates
    ) {
        if (deathCancelled || keepInventoryAlreadyEnabled || resurrectionAlreadyHandled || candidates == null) {
            return new Decision(false, null);
        }
        for (SealCandidate candidate : candidates) {
            if (candidate != null && candidate.authentic()
                    && candidate.uniqueItemId() != null
                    && !candidate.uniqueItemId().isBlank()) {
                return new Decision(true, candidate);
            }
        }
        return new Decision(false, null);
    }
}
