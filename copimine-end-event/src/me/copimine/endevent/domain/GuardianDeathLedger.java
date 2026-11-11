package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Master contract 17.1: "Remove the existing 90-second guardian respawn
 * behavior. Do not merely set an enormous respawn timer."
 *
 * <p>A durable, generation-scoped ledger records which defender slots were
 * destroyed. A restart rebuilds the generation from progress and restores only
 * the slots that were still alive. A destroyed slot can never be handed a new
 * respawn deadline, so no timer - short or enormous - can resurrect it.</p>
 */
public final class GuardianDeathLedger {
    /** Progress key prefix; versioned so an old snapshot cannot be misread. */
    public static final String KEY_PREFIX = "kagune-cocoon-death.";
    public static final String VERSION = "v2";
    public static final String COCOON_BROKEN_KEY = "kagune-cocoon-broken";
    public static final String FALL_KEY = "kagune-cocoon-fall";

    private final long generation;
    private final LinkedHashSet<Integer> destroyed = new LinkedHashSet<>();
    private final LinkedHashSet<Integer> living = new LinkedHashSet<>();
    private boolean cocoonBroken;

    public GuardianDeathLedger(long generation) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("death ledger needs a live generation");
        }
        this.generation = generation;
    }

    public long generation() {
        return generation;
    }

    /** Register a defender as alive at generation start or on restore. */
    public void markAlive(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("guardian slot must be non-negative");
        }
        if (destroyed.contains(slot)) {
            // A destroyed defender is permanent: it can never be marked alive again.
            return;
        }
        living.add(slot);
    }

    /** Record a real kill. Returns false when the slot was already destroyed. */
    public boolean recordDeath(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("guardian slot must be non-negative");
        }
        if (!living.remove(slot)) {
            return false;
        }
        return destroyed.add(slot);
    }

    public boolean isDestroyed(int slot) {
        return destroyed.contains(slot);
    }

    public boolean isLiving(int slot) {
        return living.contains(slot);
    }

    public int livingCount() {
        return living.size();
    }

    public int destroyedCount() {
        return destroyed.size();
    }

    public boolean allRequiredDestroyed(int required) {
        return required > 0 && destroyed.size() >= required;
    }

    /** Marks the cocoon as permanently broken for this generation. */
    public void markCocoonBroken() {
        this.cocoonBroken = true;
    }

    public boolean cocoonBroken() {
        return cocoonBroken;
    }

    /** Snapshot for durable progress storage. */
    public java.util.Map<String, String> encode() {
        java.util.Map<String, String> encoded = new java.util.LinkedHashMap<>();
        encoded.put(KEY_PREFIX + "generation", Long.toString(generation));
        for (int slot : destroyed) {
            encoded.put(KEY_PREFIX + "destroyed." + slot, "DONE");
        }
        for (int slot : living) {
            encoded.put(KEY_PREFIX + "alive." + slot, "DONE");
        }
        if (cocoonBroken) {
            encoded.put(COCOON_BROKEN_KEY, VERSION);
        }
        return java.util.Map.copyOf(encoded);
    }

    /** Restore a ledger from progress, or null when nothing was recorded. */
    public static GuardianDeathLedger decode(java.util.Map<String, String> progress,
                                              long generation) {
        if (progress == null || progress.keySet().stream()
                .noneMatch(key -> key.startsWith(KEY_PREFIX))) {
            return null;
        }
        GuardianDeathLedger ledger = new GuardianDeathLedger(generation);
        for (java.util.Map.Entry<String, String> entry : progress.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX)) {
                continue;
            }
            String suffix = key.substring(KEY_PREFIX.length());
            if (suffix.equals("generation")) {
                continue;
            }
            if (!"DONE".equals(entry.getValue())) {
                throw new IllegalArgumentException("INVALID_GUARDIAN_DEATH_RECEIPT");
            }
            String field = suffix.substring(0, suffix.indexOf('.'));
            int slot;
            try {
                slot = Integer.parseInt(suffix.substring(suffix.indexOf('.') + 1));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("INVALID_GUARDIAN_DEATH_SLOT", invalid);
            }
            if (field.equals("destroyed")) {
                ledger.destroyed.add(slot);
                ledger.living.remove(slot);
            } else if (field.equals("alive")) {
                if (!ledger.destroyed.contains(slot)) {
                    ledger.living.add(slot);
                }
            } else {
                throw new IllegalArgumentException("UNKNOWN_GUARDIAN_DEATH_KEY");
            }
        }
        String broken = progress.get(COCOON_BROKEN_KEY);
        if (broken != null) {
            if (!VERSION.equals(broken)) {
                throw new IllegalArgumentException("INVALID_COCOON_BROKEN_RECEIPT");
            }
            ledger.cocoonBroken = true;
            // A broken cocoon has no defenders left by definition.
            ledger.living.clear();
        }
        return ledger;
    }

    /** Slots a restart is allowed to rebuild. */
    public List<Integer> restorableSlots() {
        List<Integer> slots = new ArrayList<>(living);
        slots.sort(Integer::compareTo);
        return List.copyOf(slots);
    }

    /** Slots that must never reappear. */
    public List<Integer> destroyedSlots() {
        List<Integer> slots = new ArrayList<>(destroyed);
        slots.sort(Integer::compareTo);
        return List.copyOf(slots);
    }

    /** Never returns a respawn deadline: there is no respawn behaviour any more. */
    public Long respawnDeadline(int slot) {
        return null;
    }
}