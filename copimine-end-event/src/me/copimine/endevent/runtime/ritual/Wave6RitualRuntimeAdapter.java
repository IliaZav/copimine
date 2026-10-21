package me.copimine.endevent.runtime.ritual;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.UUID;
import me.copimine.endevent.domain.GravityAnchorCohortPolicy;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.Phase;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Snapshot;
import me.copimine.endevent.runtime.ritual.RitualSpellController.StartResult;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Stage;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Wave6Spell;

/**
 * Tick-driven Wave 6 runtime bridge for the shared major-spell controller.
 *
 * <p>The owning {@code CopiMineEndEvent} supplies an {@link Effects} implementation
 * backed by its Bukkit world, player eligibility, projectile and damage helpers.
 * This adapter owns cast ordering, fixed target/landing snapshots, warning windows,
 * the impact-confirmed Anvil intermission and safe resource cleanup. The bridge
 * performs physical operations without inventing unspecified damage or movement
 * values. End Crystal and Anvil visual implementations must remain generation-owned,
 * and the Anvil hit resolver must exclude the prisoner and apply at most one receipt
 * per target in each volley.</p>
 */
public final class Wave6RitualRuntimeAdapter {
    public static final int GRAVITY_ANCHOR_WARNING_TICKS = 40;
    public static final int FALLING_ANVIL_WARNING_TICKS = 60;
    public static final int FALLING_ANVIL_DROP_TICKS = 10;
    public static final int SECOND_VOLLEY_CLUSTER_RETRY_INTERVAL_TICKS = 10;
    public static final int MAX_SECOND_VOLLEY_CLUSTER_ATTEMPTS = 6;

    public enum AnvilImpactProgress {
        PENDING,
        CONFIRMED,
        FAILED
    }

    /** Captured identity for one fixed-position Gravity Anchor cast. */
    public record GravityAnchorCast(long generation,
                                    UUID caster,
                                    UUID target,
                                    AnchorPoint fixedCrystalPoint,
                                    UUID crystalEntity,
                                    int combinedRiftBarrageProjectiles,
                                    List<UUID> affectedPlayers) {
        public GravityAnchorCast {
            if (caster == null || target == null || fixedCrystalPoint == null
                    || crystalEntity == null || combinedRiftBarrageProjectiles < 0
                    || combinedRiftBarrageProjectiles > 2 || affectedPlayers == null) {
                throw new IllegalArgumentException("invalid captured Gravity Anchor cast");
            }
            affectedPlayers = List.copyOf(affectedPlayers);
            if (affectedPlayers.size() > GravityAnchorCohortPolicy.MAX_COHORT_SIZE
                    || new HashSet<>(affectedPlayers).size() != affectedPlayers.size()) {
                throw new IllegalArgumentException("Gravity Anchor cohort must be unique and bounded");
            }
        }

        public GravityAnchorCast(long generation, UUID caster, UUID target,
                                 AnchorPoint fixedCrystalPoint, UUID crystalEntity,
                                 int combinedRiftBarrageProjectiles) {
            this(generation, caster, target, fixedCrystalPoint, crystalEntity,
                    combinedRiftBarrageProjectiles, List.of());
        }

        public GravityAnchorCast withAffectedPlayers(List<UUID> players) {
            return new GravityAnchorCast(generation, caster, target, fixedCrystalPoint,
                    crystalEntity, combinedRiftBarrageProjectiles, players);
        }
    }

    /** Immutable positions for one currently warned or falling Anvil volley. */
    public record AnvilVolley(long generation,
                              UUID caster,
                              int participants,
                              int eligibleTargetCount,
                              int volleyIndex,
                              int totalVolleys,
                              List<LandingPoint> landingPositions) {
        public AnvilVolley {
            if (caster == null || landingPositions == null) {
                throw new IllegalArgumentException("caster and locked Anvil positions required");
            }
            landingPositions = List.copyOf(landingPositions);
        }
    }

    /** Fresh eligible cluster chosen only after the prior impact and wait complete. */
    public record AnvilCluster(int eligibleTargetCount, List<LandingPoint> landingPositions) {
        public AnvilCluster {
            if (landingPositions == null) {
                throw new IllegalArgumentException("locked cluster positions required");
            }
            landingPositions = List.copyOf(landingPositions);
        }
    }

    /**
     * Bukkit-facing effect port. Implementations should use the event's existing
     * ownership, eligibility and damage helpers rather than duplicating those rules.
     * A false result from a start/show/frame hook aborts and clears that cast; a false
     * result from a poll, resolver or cleanup hook means pending and is retried.
     * Operations that can partially act must be idempotent by generation plus cast or
     * volley identity. Cleanup removes visuals/entities and releases movement state.
     */
    public interface Effects {
        /**
         * Spawn one invulnerable vanilla End Crystal at the captured fixed point and
         * return its UUID. Keep it fixed for the cast; do not replace it with a model.
         */
        UUID spawnEndCrystal(long generation, UUID caster, UUID target, AnchorPoint fixedPoint);

        /** Begin and render the full Anchor telegraph while the crystal remains fixed. */
        boolean showGravityAnchorWarning(GravityAnchorCast cast, int warningTicks);

        /** Refresh one warning frame; called on each owner tick before expiry. */
        boolean renderGravityAnchorWarningFrame(GravityAnchorCast cast, long ticksRemaining);

        /** Capture the eligible free-player cohort when the pull starts; an empty list is valid. */
        Optional<List<UUID>> captureGravityAnchorCohort(GravityAnchorCast cast);

        /** Optional existing Rift Barrage projectiles are aimed into the crystal. */
        boolean fireRiftBarrageIntoCrystal(GravityAnchorCast cast, int projectileCount);

        /** Start the host-configured pull after the crystal warning expires. */
        boolean beginGravityAnchorPull(GravityAnchorCast cast);

        /** Return true only after the host-configured pull has resolved. */
        boolean isGravityAnchorPullResolved(GravityAnchorCast cast);

        /** Render/resolve the explosion without changing terrain. */
        boolean explodeGravityAnchorWithoutTerrainDamage(GravityAnchorCast cast);

        /** Resolve bounded fall/landing through the host's approved damage policy. */
        boolean resolveGravityAnchorLanding(GravityAnchorCast cast);

        /** Remove the End Crystal and release every movement state owned by the cast. */
        boolean cleanupGravityAnchor(GravityAnchorCast cast);

        /** Present the locked landing coordinates for the complete warning duration. */
        boolean showAnvilWarning(AnvilVolley volley, int warningTicks);

        /** Refresh one warning frame; the positions must remain the captured positions. */
        boolean renderAnvilWarningFrame(AnvilVolley volley, long ticksRemaining);

        /**
         * Start lightweight, non-block-changing Anvil visuals at the warning deadline.
         * Use display visuals rather than physics-enabled FallingBlock batches.
         */
        boolean beginAnvilDrop(AnvilVolley volley);

        /** Report PENDING until the visuals have actually reached their impact. */
        AnvilImpactProgress tickAnvilDrop(AnvilVolley volley);

        /**
         * Apply controlled hits for the confirmed impact. Exclude prisoner/friendly
         * mobs, avoid terrain changes and deduplicate to one receipt per target/volley.
         */
        boolean resolveAnvilImpact(AnvilVolley volley);

        /** Clear all display visuals for this volley before the next volley can warn. */
        boolean cleanupAnvilVolley(AnvilVolley volley);

        /** Select a fresh dense eligible cluster when practical; null means retry later. */
        AnvilCluster chooseNextDenseEligibleCluster(int participants, AnvilVolley previousVolley);
    }

    private final RitualSpellController controller;
    private final Effects effects;

    private GravityAnchorCast anchorCast;
    private AnvilVolley anvilVolley;
    private AnvilVolley lastResolvedAnvilVolley;
    private boolean anchorLandingResolved;
    private boolean anvilImpactConfirmed;
    private boolean anvilImpactResolved;
    private long anvilImpactConfirmedAtTick = -1L;
    private long anvilDropStartedAtTick = -1L;
    private int secondVolleyClusterAttempts;
    private long nextSecondVolleyClusterRetryTick = -1L;

    public Wave6RitualRuntimeAdapter(RitualSpellController controller, Effects effects) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /**
     * Start and immediately bind the vanilla crystal so it is visible throughout
     * the 40-tick warning. The supplied point is captured once and never retargeted.
     */
    public StartResult startGravityAnchor(long eventGeneration,
                                          UUID caster,
                                          UUID target,
                                          AnchorPoint fixedCrystalPoint,
                                          int combinedProjectiles,
                                          boolean waveActive,
                                          boolean casterAlive,
                                          boolean spellAvailable,
                                          boolean targetEligible,
                                          long nowTick) {
        if (hasPendingCleanup()) {
            return rejectedStart();
        }
        StartResult started = controller.startGravityAnchor(eventGeneration, caster, target,
                fixedCrystalPoint, combinedProjectiles, waveActive, casterAlive,
                spellAvailable, targetEligible, nowTick);
        if (!started.accepted()) {
            return started;
        }

        UUID crystal = effects.spawnEndCrystal(eventGeneration, caster, target, fixedCrystalPoint);
        if (crystal == null) {
            controller.clear(eventGeneration);
            return rejectedStart();
        }
        GravityAnchorCast cast = new GravityAnchorCast(eventGeneration, caster, target,
                fixedCrystalPoint, crystal, combinedProjectiles);
        anchorCast = cast;
        Snapshot bound = controller.gravityAnchorCrystalSpawned(eventGeneration, crystal, nowTick);
        if (bound.gravityAnchorState().phase() != Phase.ANCHOR
                || !effects.showGravityAnchorWarning(cast, GRAVITY_ANCHOR_WARNING_TICKS)
                || combinedProjectiles > 0
                && !effects.fireRiftBarrageIntoCrystal(cast, combinedProjectiles)) {
            abortAnchor(eventGeneration);
            return rejectedStart();
        }
        return new StartResult(true, controller.snapshot());
    }

    /** Start one bounded roster-matrix Anvil cast with already locked landing positions. */
    public StartResult startFallingAnvils(long eventGeneration,
                                          UUID caster,
                                          int participants,
                                          int eligibleTargetCount,
                                          List<LandingPoint> lockedPositions,
                                          boolean waveActive,
                                          boolean casterAlive,
                                          boolean spellAvailable,
                                          boolean eligibleTargetPresent,
                                          long nowTick) {
        if (hasPendingCleanup()) {
            return rejectedStart();
        }
        StartResult started = controller.startFallingAnvils(eventGeneration, caster,
                participants, eligibleTargetCount, lockedPositions, waveActive,
                casterAlive, spellAvailable, eligibleTargetPresent, nowTick);
        if (!started.accepted()) {
            return started;
        }
        anvilVolley = anvilVolleyFrom(started.snapshot());
        lastResolvedAnvilVolley = null;
        anvilImpactConfirmed = false;
        anvilImpactResolved = false;
        anvilImpactConfirmedAtTick = -1L;
        anvilDropStartedAtTick = -1L;
        secondVolleyClusterAttempts = 0;
        nextSecondVolleyClusterRetryTick = -1L;
        if (!effects.showAnvilWarning(anvilVolley, FALLING_ANVIL_WARNING_TICKS)) {
            abortAnvilCast(eventGeneration);
            return rejectedStart();
        }
        return new StartResult(true, controller.snapshot());
    }

    /**
     * Drive one event tick. The host must call this from its existing W6 tick loop
     * and must not separately tick the same controller a second time.
     */
    public Snapshot tick(long eventGeneration, long nowTick) {
        if (hasPendingCleanup()) {
            retryPendingCleanup();
            return controller.snapshot();
        }
        if (eventGeneration != controller.snapshot().generation() || nowTick < 0L) {
            return controller.snapshot();
        }
        Snapshot snapshot = controller.tick(eventGeneration, nowTick);
        if (snapshot.wave6Spell() == Wave6Spell.GRAVITY_ANCHOR && anchorCast != null) {
            tickGravityAnchor(snapshot, nowTick);
        } else if (snapshot.wave6Spell() == Wave6Spell.FALLING_ANVILS) {
            tickFallingAnvils(snapshot, nowTick);
        }
        return controller.snapshot();
    }

    /** Cancel the current W6 cast and release its owned visual/movement state. */
    public Snapshot clear(long eventGeneration) {
        if (eventGeneration != controller.snapshot().generation()) {
            return controller.snapshot();
        }
        if (anchorCast != null && effects.cleanupGravityAnchor(anchorCast)) {
            anchorCast = null;
            anchorLandingResolved = false;
        }
        if (anvilVolley != null && effects.cleanupAnvilVolley(anvilVolley)) {
            anvilVolley = null;
            anvilImpactConfirmed = false;
            anvilImpactResolved = false;
            anvilImpactConfirmedAtTick = -1L;
        }
        Snapshot cleared = controller.clear(eventGeneration);
        secondVolleyClusterAttempts = 0;
        nextSecondVolleyClusterRetryTick = -1L;
        if (anchorCast == null && anvilVolley == null) {
            lastResolvedAnvilVolley = null;
        }
        return cleared;
    }

    public Snapshot snapshot() {
        return controller.snapshot();
    }

    /** True while cancellation or a completed cast still owns resources to release. */
    public boolean cleanupPending() {
        return hasPendingCleanup();
    }

    private void tickGravityAnchor(Snapshot snapshot, long nowTick) {
        Phase phase = snapshot.gravityAnchorState().phase();
        if (snapshot.stage() == Stage.TELEGRAPH && phase == Phase.ANCHOR) {
            long remaining = Math.max(0L, snapshot.deadlineTick() - nowTick);
            if (!effects.renderGravityAnchorWarningFrame(anchorCast, remaining)) {
                abortAnchor(snapshot.generation());
            }
            return;
        }
        if (snapshot.stage() == Stage.EXECUTE && phase == Phase.ANCHOR) {
            Optional<List<UUID>> captured = effects.captureGravityAnchorCohort(anchorCast);
            if (captured == null || captured.isEmpty()) {
                abortAnchor(snapshot.generation());
                return;
            }
            try {
                anchorCast = anchorCast.withAffectedPlayers(captured.get());
            } catch (RuntimeException invalidCohort) {
                abortAnchor(snapshot.generation());
                return;
            }
            if (!effects.beginGravityAnchorPull(anchorCast)) {
                abortAnchor(snapshot.generation());
                return;
            }
            controller.advanceGravityAnchorPhase(snapshot.generation(), Phase.PULL, nowTick);
            return;
        }
        if (snapshot.stage() != Stage.ACTIVE) {
            return;
        }
        switch (phase) {
            case PULL -> {
                if (effects.isGravityAnchorPullResolved(anchorCast)) {
                    controller.advanceGravityAnchorPhase(snapshot.generation(), Phase.EXPLOSION, nowTick);
                }
            }
            case EXPLOSION -> {
                if (effects.explodeGravityAnchorWithoutTerrainDamage(anchorCast)) {
                    controller.advanceGravityAnchorPhase(snapshot.generation(), Phase.LANDING, nowTick);
                }
            }
            case LANDING -> {
                if (!anchorLandingResolved) {
                    anchorLandingResolved = effects.resolveGravityAnchorLanding(anchorCast);
                }
                if (anchorLandingResolved && effects.cleanupGravityAnchor(anchorCast)) {
                    controller.advanceGravityAnchorPhase(snapshot.generation(), Phase.COMPLETE, nowTick);
                    anchorCast = null;
                    anchorLandingResolved = false;
                }
            }
            default -> { }
        }
    }

    private void tickFallingAnvils(Snapshot snapshot, long nowTick) {
        FallingAnvilVolleyPolicy.State state = snapshot.fallingAnvilState();
        if (snapshot.stage() == Stage.TELEGRAPH
                && state.phase() == FallingAnvilVolleyPolicy.Phase.WARNING
                && anvilVolley != null) {
            long remaining = Math.max(0L, state.deadlineTick() - nowTick);
            if (!effects.renderAnvilWarningFrame(anvilVolley, remaining)) {
                abortAnvilCast(snapshot.generation());
            }
            return;
        }
        if (snapshot.stage() == Stage.EXECUTE
                && state.phase() == FallingAnvilVolleyPolicy.Phase.IMPACT
                && anvilVolley != null) {
            if (!effects.beginAnvilDrop(anvilVolley)) {
                abortAnvilCast(snapshot.generation());
                return;
            }
            anvilDropStartedAtTick = nowTick;
            controller.effectStarted(snapshot.generation(), nowTick);
            return;
        }
        if (snapshot.wave6Spell() != Wave6Spell.FALLING_ANVILS) {
            return;
        }
        if (snapshot.stage() == Stage.ACTIVE
                && state.phase() == FallingAnvilVolleyPolicy.Phase.IMPACT
                && anvilVolley != null) {
            resolveAnvilImpact(snapshot.generation(), nowTick);
            return;
        }
        if (snapshot.stage() == Stage.ACTIVE
                && state.phase() == FallingAnvilVolleyPolicy.Phase.INTERMISSION
                && nowTick >= state.deadlineTick()) {
            beginSecondVolley(snapshot, nowTick);
        }
    }

    private void resolveAnvilImpact(long eventGeneration, long nowTick) {
        if (anvilVolley == null) {
            return;
        }
        if (!anvilImpactConfirmed) {
            AnvilImpactProgress progress = effects.tickAnvilDrop(anvilVolley);
            if (progress == AnvilImpactProgress.FAILED) {
                abortAnvilCast(eventGeneration);
                return;
            }
            if (progress == AnvilImpactProgress.PENDING) {
                if (anvilDropDeadlineReached(nowTick)) {
                    abortAnvilCast(eventGeneration);
                }
                return;
            }
            anvilImpactConfirmed = true;
            anvilImpactConfirmedAtTick = nowTick;
        }
        if (!anvilImpactResolved) {
            anvilImpactResolved = effects.resolveAnvilImpact(anvilVolley);
            if (!anvilImpactResolved) {
                return;
            }
        }
        if (!effects.cleanupAnvilVolley(anvilVolley)) {
            return;
        }
        lastResolvedAnvilVolley = anvilVolley;
        Snapshot afterImpact = controller.fallingAnvilImpactResolved(
                eventGeneration, anvilImpactConfirmedAtTick);
        anvilVolley = null;
        anvilImpactConfirmed = false;
        anvilImpactResolved = false;
        anvilImpactConfirmedAtTick = -1L;
        anvilDropStartedAtTick = -1L;
        if (afterImpact.stage() == Stage.ACTIVE
                && afterImpact.fallingAnvilState().phase() == FallingAnvilVolleyPolicy.Phase.INTERMISSION
                && nowTick >= afterImpact.fallingAnvilState().deadlineTick()) {
            beginSecondVolley(afterImpact, nowTick);
        }
    }

    private void beginSecondVolley(Snapshot snapshot, long nowTick) {
        if (lastResolvedAnvilVolley == null) {
            return;
        }
        if (nextSecondVolleyClusterRetryTick >= 0L
                && nowTick < nextSecondVolleyClusterRetryTick) return;
        secondVolleyClusterAttempts++;
        AnvilCluster cluster = effects.chooseNextDenseEligibleCluster(
                snapshot.fallingAnvilState().participants(), lastResolvedAnvilVolley);
        if (cluster == null) {
            deferOrAbortSecondVolley(snapshot.generation(), nowTick);
            return;
        }
        Snapshot next = controller.beginNextFallingAnvilVolley(snapshot.generation(),
                cluster.eligibleTargetCount(), cluster.landingPositions(), nowTick);
        if (next.fallingAnvilState().phase() != FallingAnvilVolleyPolicy.Phase.WARNING
                || next.fallingAnvilState().volleyIndex() != 2) {
            deferOrAbortSecondVolley(snapshot.generation(), nowTick);
            return;
        }
        anvilVolley = anvilVolleyFrom(next);
        lastResolvedAnvilVolley = null;
        secondVolleyClusterAttempts = 0;
        nextSecondVolleyClusterRetryTick = -1L;
        if (!effects.showAnvilWarning(anvilVolley, FALLING_ANVIL_WARNING_TICKS)) {
            abortAnvilCast(snapshot.generation());
        }
    }

    private void deferOrAbortSecondVolley(long eventGeneration, long nowTick) {
        if (secondVolleyClusterAttempts >= MAX_SECOND_VOLLEY_CLUSTER_ATTEMPTS
                || nowTick > Long.MAX_VALUE - SECOND_VOLLEY_CLUSTER_RETRY_INTERVAL_TICKS) {
            abortAnvilCast(eventGeneration);
            return;
        }
        nextSecondVolleyClusterRetryTick = nowTick + SECOND_VOLLEY_CLUSTER_RETRY_INTERVAL_TICKS;
    }

    private boolean anvilDropDeadlineReached(long nowTick) {
        return anvilDropStartedAtTick >= 0L && nowTick >= anvilDropStartedAtTick
                && nowTick - anvilDropStartedAtTick >= FALLING_ANVIL_DROP_TICKS;
    }

    private AnvilVolley anvilVolleyFrom(Snapshot snapshot) {
        FallingAnvilVolleyPolicy.State state = snapshot.fallingAnvilState();
        return new AnvilVolley(snapshot.generation(), snapshot.caster(), state.participants(),
                state.eligibleTargetCount(), state.volleyIndex(), state.totalVolleys(),
                state.landingPositions());
    }

    private void abortAnchor(long eventGeneration) {
        if (anchorCast != null && effects.cleanupGravityAnchor(anchorCast)) {
            anchorCast = null;
            anchorLandingResolved = false;
        }
        controller.clear(eventGeneration);
    }

    private void abortAnvilCast(long eventGeneration) {
        if (anvilVolley != null && effects.cleanupAnvilVolley(anvilVolley)) {
            anvilVolley = null;
            anvilImpactConfirmed = false;
            anvilImpactResolved = false;
            anvilImpactConfirmedAtTick = -1L;
        }
        anvilDropStartedAtTick = -1L;
        secondVolleyClusterAttempts = 0;
        nextSecondVolleyClusterRetryTick = -1L;
        controller.clear(eventGeneration);
        if (anvilVolley == null) lastResolvedAnvilVolley = null;
    }

    private boolean hasPendingCleanup() {
        return anchorCast != null && controller.snapshot().wave6Spell() != Wave6Spell.GRAVITY_ANCHOR
                || anvilVolley != null
                && controller.snapshot().wave6Spell() != Wave6Spell.FALLING_ANVILS;
    }

    private void retryPendingCleanup() {
        if (anchorCast != null && controller.snapshot().wave6Spell() != Wave6Spell.GRAVITY_ANCHOR
                && effects.cleanupGravityAnchor(anchorCast)) {
            anchorCast = null;
            anchorLandingResolved = false;
        }
        if (anvilVolley != null && controller.snapshot().wave6Spell() != Wave6Spell.FALLING_ANVILS
                && effects.cleanupAnvilVolley(anvilVolley)) {
            anvilVolley = null;
            anvilImpactConfirmed = false;
            anvilImpactResolved = false;
            anvilImpactConfirmedAtTick = -1L;
            anvilDropStartedAtTick = -1L;
        }
    }

    private StartResult rejectedStart() {
        return new StartResult(false, controller.snapshot());
    }
}
