package me.copimine.endevent.runtime.ritual;

import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.Phase;

/**
 * One generation-fenced scheduler for Wave 6 major Caster spells.
 *
 * <p>All five Casters share this controller so a telegraph, active effect, or
 * recovery window from one Caster prevents another major spell from starting.
 * Bukkit effects are started by the caller only after the controller reaches
 * {@link Stage#EXECUTE}. The versioned Wave 6 spell identity is separate from
 * {@link Spell} so old saved and switch-based integrations retain their enum
 * contract while Gravity Anchor and Falling Anvils replace their old mechanics.</p>
 */
public final class RitualSpellController {
    /** Gravity Anchor combines with at most two Barrage projectiles. */
    public static final int MAX_COMBINED_BARRAGE_PROJECTILES = 2;

    /** Legacy/persisted spell names; do not rename or remove without migration. */
    public enum Spell {
        RIFT_BARRAGE,
        GRAVITY_WELL,
        SOUL_BRAND,
        RIFT_CHAINS,
        FINAL_SEAL
    }

    /** Current replacement contracts used by the controller adapters. */
    public enum Wave6Spell {
        GRAVITY_ANCHOR,
        FALLING_ANVILS
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
                           long deadlineTick,
                           Wave6Spell wave6Spell,
                           GravityAnchorPolicy.State gravityAnchorState,
                           FallingAnvilVolleyPolicy.State fallingAnvilState,
                           int combinedRiftBarrageProjectiles) {
        /** Retain the original snapshot constructor for existing integrations. */
        public Snapshot(long generation, Stage stage, Spell spell, UUID caster, long deadlineTick) {
            this(generation, stage, spell, caster, deadlineTick, null,
                    GravityAnchorPolicy.idle(generation),
                    FallingAnvilVolleyPolicy.idle(generation), 0);
        }
    }

    public record StartResult(boolean accepted, Snapshot snapshot) {
    }

    private static final long TELEGRAPH_TICKS = 20L;
    /** Fourteen seconds is the center of the master-task's 12–15 second start interval. */
    private static final long MINIMUM_START_INTERVAL_TICKS = 280L;
    private static final long GRAVITY_ANCHOR_WARNING_TICKS = 40L;

    private final long generation;
    private Stage stage = Stage.IDLE;
    private Spell spell;
    private Wave6Spell wave6Spell;
    private UUID caster;
    private long deadlineTick;
    private long lastSpellStartedTick = -1L;
    private GravityAnchorPolicy.State gravityAnchorState;
    private FallingAnvilVolleyPolicy.State fallingAnvilState;
    private int combinedRiftBarrageProjectiles;

    public RitualSpellController(long generation) {
        this.generation = generation;
        this.gravityAnchorState = GravityAnchorPolicy.idle(generation);
        this.fallingAnvilState = FallingAnvilVolleyPolicy.idle(generation);
    }

    /** Start an existing spell with its legacy generic warning. */
    public StartResult start(long eventGeneration,
                             Spell requestedSpell,
                             UUID requestedCaster,
                             boolean waveActive,
                             boolean casterAlive,
                             boolean spellAvailable,
                             boolean validTarget,
                             long nowTick) {
        return start(eventGeneration, requestedSpell, requestedCaster, waveActive,
                casterAlive, spellAvailable, validTarget, nowTick, TELEGRAPH_TICKS);
    }

    /** Legacy generic start. Current Anchor and Anvil spells use their dedicated APIs below. */
    public StartResult start(long eventGeneration,
                             Spell requestedSpell,
                             UUID requestedCaster,
                             boolean waveActive,
                             boolean casterAlive,
                             boolean spellAvailable,
                             boolean validTarget,
                             long nowTick,
                             long telegraphTicks) {
        if (eventGeneration != generation
                || requestedSpell == null
                || requestedCaster == null
                || !waveActive
                || !casterAlive
                || !spellAvailable
                || !validTarget
                || nowTick < 0L
                || telegraphTicks < 1L
                || telegraphTicks > 100L
                || stage != Stage.IDLE) {
            return new StartResult(false, snapshot());
        }
        long warningDeadline = addTicks(nowTick, telegraphTicks);
        if (warningDeadline < 0L) {
            return new StartResult(false, snapshot());
        }
        clearWave6State();
        spell = requestedSpell;
        caster = requestedCaster;
        stage = Stage.TELEGRAPH;
        deadlineTick = warningDeadline;
        lastSpellStartedTick = nowTick;
        return new StartResult(true, snapshot());
    }

    /**
     * Start the replacement Gravity Anchor as one shared major cast. Its explicit
     * forty-tick warning intentionally does not inherit the generic twenty-tick
     * telegraph. Position, force, pull duration, explosion damage, and fall damage
     * remain adapter-owned because the master task does not specify their values.
     */
    public StartResult startGravityAnchor(long eventGeneration,
                                          UUID requestedCaster,
                                          UUID targetPlayer,
                                          AnchorPoint fixedCrystalPoint,
                                          int combinedProjectiles,
                                          boolean waveActive,
                                          boolean casterAlive,
                                          boolean spellAvailable,
                                          boolean targetEligible,
                                          long nowTick) {
        if (eventGeneration != generation
                || requestedCaster == null
                || combinedProjectiles < 0
                || combinedProjectiles > MAX_COMBINED_BARRAGE_PROJECTILES
                || nowTick < 0L
                || !waveActive
                || !casterAlive
                || !spellAvailable
                || !targetEligible
                || stage != Stage.IDLE) {
            return new StartResult(false, snapshot());
        }
        long warningDeadline = addTicks(nowTick, GRAVITY_ANCHOR_WARNING_TICKS);
        if (warningDeadline < 0L) {
            return new StartResult(false, snapshot());
        }
        GravityAnchorPolicy.StartResult anchorStart = GravityAnchorPolicy.start(
                gravityAnchorState, eventGeneration, targetPlayer, fixedCrystalPoint,
                waveActive, targetEligible, true);
        if (!anchorStart.accepted()) {
            return new StartResult(false, snapshot());
        }
        clearFallingAnvilState();
        spell = null;
        wave6Spell = Wave6Spell.GRAVITY_ANCHOR;
        caster = requestedCaster;
        gravityAnchorState = anchorStart.state();
        combinedRiftBarrageProjectiles = combinedProjectiles;
        stage = Stage.TELEGRAPH;
        deadlineTick = warningDeadline;
        lastSpellStartedTick = nowTick;
        return new StartResult(true, snapshot());
    }

    /**
     * Start the replacement Heavenly Judgment as one scheduler-owned cast.
     * The caller supplies already selected and locked world positions; the pure
     * policy validates the roster matrix and does not move those positions later.
     */
    public StartResult startFallingAnvils(long eventGeneration,
                                          UUID requestedCaster,
                                          int participants,
                                          int eligibleTargetCount,
                                          List<LandingPoint> lockedPositions,
                                          boolean waveActive,
                                          boolean casterAlive,
                                          boolean spellAvailable,
                                          boolean eligibleTargetPresent,
                                          long nowTick) {
        if (eventGeneration != generation
                || requestedCaster == null
                || nowTick < 0L
                || !waveActive
                || !casterAlive
                || !spellAvailable
                || !eligibleTargetPresent
                || stage != Stage.IDLE) {
            return new StartResult(false, snapshot());
        }
        FallingAnvilVolleyPolicy.StartResult anvilStart = FallingAnvilVolleyPolicy.start(
                fallingAnvilState, eventGeneration, participants, eligibleTargetCount,
                lockedPositions, nowTick, waveActive, true);
        if (!anvilStart.accepted()) {
            return new StartResult(false, snapshot());
        }
        clearGravityAnchorState();
        spell = null;
        wave6Spell = Wave6Spell.FALLING_ANVILS;
        caster = requestedCaster;
        fallingAnvilState = anvilStart.state();
        combinedRiftBarrageProjectiles = 0;
        stage = Stage.TELEGRAPH;
        deadlineTick = fallingAnvilState.deadlineTick();
        lastSpellStartedTick = nowTick;
        return new StartResult(true, snapshot());
    }

    public Snapshot tick(long eventGeneration, long nowTick) {
        if (eventGeneration != generation || nowTick < 0L) {
            return snapshot();
        }
        if (wave6Spell == Wave6Spell.FALLING_ANVILS) {
            fallingAnvilState = FallingAnvilVolleyPolicy.tick(
                    fallingAnvilState, eventGeneration, nowTick);
            if (stage == Stage.TELEGRAPH
                    && fallingAnvilState.phase() == FallingAnvilVolleyPolicy.Phase.IMPACT) {
                stage = Stage.EXECUTE;
            }
        }
        if (stage == Stage.TELEGRAPH && nowTick >= deadlineTick) {
            stage = Stage.EXECUTE;
        } else if (stage == Stage.RECOVERY && nowTick >= deadlineTick) {
            resetToIdle();
        }
        return snapshot();
    }

    /**
     * Bind the real End Crystal while the Anchor warning is visible. The warning
     * continues under the shared TELEGRAPH stage; ordinary effectStarted cannot
     * skip this crystal-ownership hook or start the pull early.
     */
    public Snapshot gravityAnchorCrystalSpawned(long eventGeneration,
                                                UUID crystalEntity,
                                                long nowTick) {
        if (eventGeneration != generation
                || wave6Spell != Wave6Spell.GRAVITY_ANCHOR
                || stage != Stage.TELEGRAPH
                || nowTick > deadlineTick
                || nowTick < 0L
                || crystalEntity == null) {
            return snapshot();
        }
        GravityAnchorPolicy.State next = GravityAnchorPolicy.anchorSpawned(
                gravityAnchorState, eventGeneration, crystalEntity);
        if (next == gravityAnchorState) {
            return snapshot();
        }
        gravityAnchorState = next;
        return snapshot();
    }

    /** Advance one Anchor phase after its adapter completes that physical phase. */
    public Snapshot advanceGravityAnchorPhase(long eventGeneration,
                                              Phase nextPhase,
                                              long nowTick) {
        if (eventGeneration != generation
                || wave6Spell != Wave6Spell.GRAVITY_ANCHOR
                || stage != Stage.ACTIVE
                && !(stage == Stage.EXECUTE && nextPhase == Phase.PULL
                && gravityAnchorState.phase() == Phase.ANCHOR
                && nowTick >= deadlineTick)
                || nowTick < 0L) {
            return snapshot();
        }
        GravityAnchorPolicy.State next = GravityAnchorPolicy.advanceTo(
                gravityAnchorState, eventGeneration, nextPhase);
        if (next == gravityAnchorState) {
            return snapshot();
        }
        gravityAnchorState = next;
        if (nextPhase == Phase.PULL) {
            stage = Stage.ACTIVE;
        }
        if (nextPhase == Phase.COMPLETE) {
            beginRecovery(nowTick);
        } else {
            deadlineTick = nowTick;
        }
        return snapshot();
    }

    /**
     * Begin Falling Anvil visuals only when the policy requests an impact after
     * its warning. Other spells retain their existing effectStarted transition.
     */
    public Snapshot effectStarted(long eventGeneration, long nowTick) {
        if (eventGeneration != generation || nowTick < 0L || stage != Stage.EXECUTE) {
            return snapshot();
        }
        if (wave6Spell == Wave6Spell.GRAVITY_ANCHOR) {
            return snapshot();
        }
        if (wave6Spell == Wave6Spell.FALLING_ANVILS
                && fallingAnvilState.phase() != FallingAnvilVolleyPolicy.Phase.IMPACT) {
            return snapshot();
        }
        stage = Stage.ACTIVE;
        deadlineTick = nowTick;
        return snapshot();
    }

    /** Confirm the actual end of one falling volley; warnings alone do not advance it. */
    public Snapshot fallingAnvilImpactResolved(long eventGeneration, long impactTick) {
        if (eventGeneration != generation
                || wave6Spell != Wave6Spell.FALLING_ANVILS
                || stage != Stage.ACTIVE
                || impactTick < 0L) {
            return snapshot();
        }
        FallingAnvilVolleyPolicy.State next = FallingAnvilVolleyPolicy.impactResolved(
                fallingAnvilState, eventGeneration, impactTick);
        if (next == fallingAnvilState) {
            return snapshot();
        }
        fallingAnvilState = next;
        deadlineTick = next.deadlineTick();
        if (next.phase() == FallingAnvilVolleyPolicy.Phase.COMPLETE) {
            beginRecovery(impactTick);
        }
        return snapshot();
    }

    /** Lock a freshly selected cluster after the first impact's full one-second wait. */
    public Snapshot beginNextFallingAnvilVolley(long eventGeneration,
                                                int eligibleTargetCount,
                                                List<LandingPoint> newLockedPositions,
                                                long nowTick) {
        if (eventGeneration != generation
                || wave6Spell != Wave6Spell.FALLING_ANVILS
                || stage != Stage.ACTIVE
                || nowTick < 0L) {
            return snapshot();
        }
        FallingAnvilVolleyPolicy.StartResult next = FallingAnvilVolleyPolicy.beginNextVolley(
                fallingAnvilState, eventGeneration, eligibleTargetCount,
                newLockedPositions, nowTick);
        if (!next.accepted()) {
            return snapshot();
        }
        fallingAnvilState = next.state();
        stage = Stage.TELEGRAPH;
        deadlineTick = fallingAnvilState.deadlineTick();
        return snapshot();
    }

    public Snapshot complete(long eventGeneration, long nowTick) {
        if (eventGeneration == generation && stage == Stage.ACTIVE
                && wave6Spell == null && nowTick >= 0L) {
            beginRecovery(nowTick);
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
        return new Snapshot(generation, stage, spell, caster, deadlineTick,
                wave6Spell, gravityAnchorState, fallingAnvilState,
                combinedRiftBarrageProjectiles);
    }

    private void beginRecovery(long nowTick) {
        long cadenceDeadline = lastSpellStartedTick < 0L
                ? addTicks(nowTick, MINIMUM_START_INTERVAL_TICKS)
                : addTicks(lastSpellStartedTick, MINIMUM_START_INTERVAL_TICKS);
        long recoveryDeadline = cadenceDeadline < 0L
                ? -1L : Math.max(nowTick, cadenceDeadline);
        if (recoveryDeadline < 0L) {
            return;
        }
        stage = Stage.RECOVERY;
        deadlineTick = recoveryDeadline;
    }

    private static long addTicks(long nowTick, long ticks) {
        try {
            return Math.addExact(nowTick, ticks);
        } catch (ArithmeticException overflow) {
            return -1L;
        }
    }

    private void clearGravityAnchorState() {
        gravityAnchorState = GravityAnchorPolicy.clear(gravityAnchorState, generation);
        combinedRiftBarrageProjectiles = 0;
    }

    private void clearFallingAnvilState() {
        fallingAnvilState = FallingAnvilVolleyPolicy.clear(fallingAnvilState, generation);
    }

    private void clearWave6State() {
        wave6Spell = null;
        clearGravityAnchorState();
        clearFallingAnvilState();
    }

    private void resetToIdle() {
        stage = Stage.IDLE;
        spell = null;
        caster = null;
        deadlineTick = 0L;
        lastSpellStartedTick = -1L;
        clearWave6State();
    }
}
