package me.copimine.endevent.runtime.ritual;

import java.util.UUID;

/**
 * One generation-fenced scheduler for Wave 6 major Caster spells.
 *
 * <p>All five Casters share this controller so a telegraph, active effect, or
 * recovery window from one Caster prevents another major spell from starting.
 * Bukkit effects are started by the caller only after the controller reaches
 * {@link Stage#EXECUTE}.</p>
 */
public final class RitualSpellController {
    public enum Spell {
        RIFT_BARRAGE,
        GRAVITY_WELL,
        SOUL_BRAND,
        RIFT_CHAINS,
        FINAL_SEAL
    }

    public enum Stage {
        IDLE,
        TELEGRAPH,
        EXECUTE,
        ACTIVE,
        RECOVERY
    }

    public record Snapshot(long generation,
                           Stage stage,
                           Spell spell,
                           UUID caster,
                           long deadlineTick) {
    }

    public record StartResult(boolean accepted, Snapshot snapshot) {
    }

    private static final long TELEGRAPH_TICKS = 20L;
    private static final long RECOVERY_TICKS = 20L;

    private final long generation;
    private Stage stage = Stage.IDLE;
    private Spell spell;
    private UUID caster;
    private long deadlineTick;

    public RitualSpellController(long generation) {
        this.generation = generation;
    }

    public StartResult start(long eventGeneration,
                             Spell requestedSpell,
                             UUID requestedCaster,
                             boolean waveActive,
                             boolean casterAlive,
                             boolean spellAvailable,
                             boolean validTarget,
                             long nowTick) {
        if (eventGeneration != generation
                || requestedSpell == null
                || requestedCaster == null
                || !waveActive
                || !casterAlive
                || !spellAvailable
                || !validTarget
                || stage != Stage.IDLE) {
            return new StartResult(false, snapshot());
        }
        spell = requestedSpell;
        caster = requestedCaster;
        stage = Stage.TELEGRAPH;
        deadlineTick = nowTick + TELEGRAPH_TICKS;
        return new StartResult(true, snapshot());
    }

    public Snapshot tick(long eventGeneration, long nowTick) {
        if (eventGeneration != generation) {
            return snapshot();
        }
        if (stage == Stage.TELEGRAPH && nowTick >= deadlineTick) {
            stage = Stage.EXECUTE;
        } else if (stage == Stage.RECOVERY && nowTick >= deadlineTick) {
            resetToIdle();
        }
        return snapshot();
    }

    public Snapshot effectStarted(long eventGeneration, long nowTick) {
        if (eventGeneration == generation && stage == Stage.EXECUTE) {
            stage = Stage.ACTIVE;
            deadlineTick = nowTick;
        }
        return snapshot();
    }

    public Snapshot complete(long eventGeneration, long nowTick) {
        if (eventGeneration == generation && stage == Stage.ACTIVE) {
            stage = Stage.RECOVERY;
            deadlineTick = nowTick + RECOVERY_TICKS;
        }
        return snapshot();
    }

    public Snapshot clear(long eventGeneration) {
        if (eventGeneration == generation) {
            resetToIdle();
        }
        return snapshot();
    }

    public Snapshot snapshot() {
        return new Snapshot(generation, stage, spell, caster, deadlineTick);
    }

    private void resetToIdle() {
        stage = Stage.IDLE;
        spell = null;
        caster = null;
        deadlineTick = 0L;
    }
}
