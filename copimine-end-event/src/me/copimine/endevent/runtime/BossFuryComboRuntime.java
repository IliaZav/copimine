package me.copimine.endevent.runtime;

import java.util.Objects;
import java.util.UUID;

/**
 * Owns one bounded final-fury combination, never damage or an independent task.
 * Each strike must enter the normal telegraph/contact/recovery pipeline. A miss
 * consumes a strike too; the controller must never retry damage until it lands.
 */
public final class BossFuryComboRuntime {
    public static final long MAX_COMBO_TICKS = 240L;
    public static final long BETWEEN_STRIKES_TICKS = 4L;
    public static final long REACQUIRE_TICKS = 20L;
    private String event;
    private long generation;
    private UUID boss;
    private UUID target;
    private int total;
    private int committed;
    private long expires;
    private long readySince = -1L;
    private long nextAttempt;

    public boolean active() { return target != null; }
    public UUID target() { return target; }
    public boolean finishingSlam() { return total == 3 && committed == 2; }

    /** Called only after the opening swipe was actually accepted. */
    public void begin(String event, long generation, UUID boss, UUID target, long tick, int variation) {
        if (active() || event == null || boss == null || target == null) return;
        this.event = event;
        this.generation = generation;
        this.boss = boss;
        this.target = target;
        total = 2 + Math.floorMod(variation, 2);
        committed = 1;
        expires = tick + MAX_COMBO_TICKS;
        readySince = -1L;
        nextAttempt = tick;
    }

    public boolean owns(String event, long generation, UUID boss, long tick) {
        return active() && Objects.equals(this.event, event) && this.generation == generation
                && Objects.equals(this.boss, boss) && tick < expires;
    }

    /** Call only between casts: the full authored recovery always finishes. */
    public boolean ready(long tick) {
        if (readySince < 0L) readySince = tick;
        return tick >= nextAttempt && tick - readySince >= BETWEEN_STRIKES_TICKS;
    }

    /** A player may escape a combo; no chase forever and no homing contact. */
    public boolean lostOpening(long tick) {
        return readySince >= 0L && tick - readySince >= REACQUIRE_TICKS;
    }

    public void refused(long tick) { nextAttempt = tick + BETWEEN_STRIKES_TICKS; }

    public void committed() {
        if (!active()) return;
        if (++committed >= total) clear();
        else readySince = -1L;
    }

    public void clear() {
        event = null;
        boss = null;
        target = null;
        generation = 0L;
        total = committed = 0;
        expires = nextAttempt = 0L;
        readySince = -1L;
    }
}
