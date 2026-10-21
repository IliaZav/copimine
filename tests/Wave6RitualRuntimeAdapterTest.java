import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.Phase;
import me.copimine.endevent.domain.GravityAnchorCohortPolicy;
import me.copimine.endevent.domain.GravityAnchorLandingPolicy;
import me.copimine.endevent.runtime.ritual.FallingAnvilImpactConfirmation;
import me.copimine.endevent.runtime.ritual.RitualSpellController;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Stage;
import me.copimine.endevent.runtime.ritual.RitualSpellController.Wave6Spell;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.AnvilCluster;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.AnvilImpactProgress;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.Effects;

public final class Wave6RitualRuntimeAdapterTest {
    public static void main(String[] args) {
        failedAnchorStartupRollsBackAndCanBeRetried();
        staleGenerationStillRetriesOwnedAnchorCleanup();
        gravityAnchorKeepsCrystalVisibleForFullWarningAndCleansAfterLanding();
        elevenPlayerAnvilsWaitForConfirmedImpactBeforeFreshSecondWarning();
        confirmedImpactClockIsNotExtendedByResolverOrCleanupRetries();
        delayedCleanupStartsFullSecondWarningAtActualTick();
        missingSecondVolleyClusterRetriesBoundedlyThenReleasesSlot();
        anvilImpactRequiresSuccessfulMovementOfEveryVisual();
        anvilVisualLossCancelsCastWithoutConfirmingImpact();
        anvilPendingStatusCannotHangPastDropDeadline();
        gravityAnchorLandingRequiresAnAppliedLiftAndSuppressesOnlyItsFall();
        gravityAnchorCapturesOnlyNearbyEligibleFreePlayers();
        gravityAnchorCarriesOneCapturedCohortThroughCleanup();
        System.out.println("Wave6RitualRuntimeAdapterTest OK");
    }

    private static void failedAnchorStartupRollsBackAndCanBeRetried() {
        long generation = 80L;
        UUID caster = id("00000000-0000-0000-0000-000000000001");
        UUID target = id("00000000-0000-0000-0000-000000000002");
        FakeEffects effects = new FakeEffects();
        effects.crystalToSpawn = id("00000000-0000-0000-0000-000000000003");
        effects.anchorWarningAvailable = false;
        RitualSpellController controller = new RitualSpellController(generation);
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(controller, effects);

        var failed = adapter.startGravityAnchor(generation, caster, target,
                new AnchorPoint(0.5D, 70.0D, 0.5D), 0,
                true, true, true, true, 0L);
        check(!failed.accepted() && failed.snapshot().stage() == Stage.IDLE
                        && effects.anchorCleanupCount == 1,
                "failed warning setup removes the partial crystal and releases the shared slot");
        effects.anchorWarningAvailable = true;
        var retried = adapter.startGravityAnchor(generation, caster, target,
                new AnchorPoint(0.5D, 70.0D, 0.5D), 0,
                true, true, true, true, 1L);
        check(retried.accepted() && retried.snapshot().stage() == Stage.TELEGRAPH,
                "a rolled-back startup leaves the encounter recoverable for retry");
    }

    private static void staleGenerationStillRetriesOwnedAnchorCleanup() {
        long generation = 801L;
        UUID caster = id("00000000-0000-0000-0000-000000000011");
        UUID target = id("00000000-0000-0000-0000-000000000012");
        FakeEffects effects = new FakeEffects();
        effects.crystalToSpawn = id("00000000-0000-0000-0000-000000000013");
        effects.anchorCleanupSuccessAttempt = 2;
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startGravityAnchor(generation, caster, target,
                        new AnchorPoint(0.5D, 70.0D, 0.5D), 0,
                        true, true, true, true, 0L).accepted(),
                "the cast owns a crystal before the generation changes");
        adapter.clear(generation);
        check(adapter.cleanupPending() && effects.anchorCleanupCount == 1,
                "failed cancellation cleanup remains visible to the event runtime");
        adapter.tick(generation + 1L, 5L);
        check(!adapter.cleanupPending() && effects.anchorCleanupCount == 2,
                "stale event ticks retry owned cleanup before the generation fence");
    }

    private static void gravityAnchorKeepsCrystalVisibleForFullWarningAndCleansAfterLanding() {
        long generation = 81L;
        UUID caster = id("11111111-1111-1111-1111-111111111111");
        UUID target = id("22222222-2222-2222-2222-222222222222");
        UUID crystal = id("33333333-3333-3333-3333-333333333333");
        FakeEffects effects = new FakeEffects();
        effects.crystalToSpawn = crystal;
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);
        AnchorPoint fixed = new AnchorPoint(6.5D, 74.0D, -2.5D);

        var started = adapter.startGravityAnchor(generation, caster, target, fixed, 2,
                true, true, true, true, 100L);
        check(started.accepted(), "eligible Anchor creates one shared cast");
        check(started.snapshot().stage() == Stage.TELEGRAPH
                        && started.snapshot().wave6Spell() == Wave6Spell.GRAVITY_ANCHOR
                        && started.snapshot().deadlineTick() == 140L
                        && started.snapshot().gravityAnchorState().phase() == Phase.ANCHOR,
                "the fixed End Crystal is already owned during the full 40-tick warning");
        check(effects.calls.equals(List.of(
                        "spawn-crystal:" + fixed,
                        "warning:40:" + crystal,
                        "barrage:2:" + crystal)),
                "crystal, telegraph and optional combined projectiles share one owner");

        check(adapter.tick(generation, 120L).stage() == Stage.TELEGRAPH
                        && effects.anchorPullStarts == 0
                        && effects.anchorWarningFrames == 1,
                "generic 20-tick expiry cannot start Anchor pull");
        var pull = adapter.tick(generation, 140L);
        check(pull.stage() == Stage.ACTIVE
                        && pull.gravityAnchorState().phase() == Phase.PULL
                        && effects.anchorPullStarts == 1,
                "pull starts once the visible crystal warning expires");
        effects.anchorPullFinished = true;
        check(adapter.tick(generation, 141L).gravityAnchorState().phase() == Phase.EXPLOSION,
                "the adapter waits for the host's pull completion signal");
        check(adapter.tick(generation, 142L).gravityAnchorState().phase() == Phase.LANDING
                        && effects.safeExplosionCount == 1,
                "the explosion hook explicitly forbids terrain damage");
        check(adapter.tick(generation, 143L).stage() == Stage.ACTIVE,
                "landing remains active until the host confirms safe resolution");
        effects.anchorLandingFinished = true;
        var completed = adapter.tick(generation, 144L);
        check(completed.stage() == Stage.RECOVERY
                        && completed.gravityAnchorState().phase() == Phase.COMPLETE
                        && effects.anchorCleanupCount == 1,
                "crystal removal and movement release precede the shared recovery");
    }

    private static void elevenPlayerAnvilsWaitForConfirmedImpactBeforeFreshSecondWarning() {
        long generation = 82L;
        UUID caster = id("44444444-4444-4444-4444-444444444444");
        FakeEffects effects = new FakeEffects();
        List<LandingPoint> first = grid(0.0D);
        List<LandingPoint> second = grid(30.0D);
        effects.nextCluster = new AnvilCluster(7, second);
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        var started = adapter.startFallingAnvils(generation, caster, 11,
                8, first, true, true, true, true, 0L);
        check(started.accepted()
                        && started.snapshot().fallingAnvilState().activeVisuals() == 25
                        && started.snapshot().deadlineTick() == 60L,
                "first high-roster warning locks no more than 25 visuals for 60 ticks");
        check(effects.warnedVolleys.size() == 1
                        && effects.warnedVolleys.get(0).landingPositions().equals(first),
                "the warning receives the immutable first cluster");
        check(adapter.tick(generation, 59L).stage() == Stage.TELEGRAPH,
                "first volley cannot drop before its complete warning");
        check(effects.anvilWarningFrames == 1,
                "locked warning visuals are refreshed during the three-second warning");
        check(adapter.tick(generation, 60L).stage() == Stage.ACTIVE
                        && effects.dropStarts == 1,
                "drop starts after the full three-second warning");
        effects.anvilProgress = AnvilImpactProgress.PENDING;
        check(adapter.tick(generation, 64L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.IMPACT,
                "an unresolved visual impact does not start the inter-volley wait");
        effects.anvilProgress = AnvilImpactProgress.CONFIRMED;
        var firstResolved = adapter.tick(generation, 65L);
        check(firstResolved.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.INTERMISSION
                        && firstResolved.fallingAnvilState().deadlineTick() == 85L
                        && effects.damageResolutions == 1
                        && effects.anvilVisualCleanups == 1,
                "one controlled impact resolution starts exactly 20 ticks of post-impact delay");
        check(adapter.tick(generation, 84L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.INTERMISSION
                        && effects.warnedVolleys.size() == 1,
                "volley two has no warning before the full post-impact second");
        var secondWarning = adapter.tick(generation, 85L);
        check(secondWarning.fallingAnvilState().volleyIndex() == 2
                        && secondWarning.fallingAnvilState().activeVisuals() == 25
                        && secondWarning.fallingAnvilState().landingPositions().equals(second)
                        && secondWarning.deadlineTick() == 145L
                        && effects.warnedVolleys.size() == 2,
                "the new cluster receives its own full 60-tick warning and remains locked");
        check(effects.clusterSelections == 1,
                "the second area is selected only after confirmed impact plus 20 ticks");
        check(adapter.tick(generation, 144L).stage() == Stage.TELEGRAPH
                        && effects.dropStarts == 1,
                "second volley cannot drop before its own warning expires");
        check(adapter.tick(generation, 145L).stage() == Stage.ACTIVE
                        && effects.dropStarts == 2,
                "second volley begins dropping after its full warning");
        effects.anvilProgress = AnvilImpactProgress.CONFIRMED;
        var complete = adapter.tick(generation, 151L);
        check(complete.stage() == Stage.RECOVERY
                        && complete.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.COMPLETE
                        && effects.damageResolutions == 2
                        && effects.warnedVolleys.size() == 2,
                "second impact ends the one spell with no third volley");
    }

    private static void confirmedImpactClockIsNotExtendedByResolverOrCleanupRetries() {
        long generation = 83L;
        UUID caster = id("55555555-5555-5555-5555-555555555555");
        FakeEffects effects = new FakeEffects();
        effects.nextCluster = new AnvilCluster(7, grid(30.0D));
        effects.resolveAnvilSuccessAttempt = 3;
        effects.cleanupAnvilSuccessAttempt = 3;
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startFallingAnvils(generation, caster, 11, 8, grid(0.0D),
                        true, true, true, true, 0L).accepted(),
                "high-roster Anvil cast starts before the timing scenario");
        adapter.tick(generation, 60L);
        effects.anvilProgress = AnvilImpactProgress.CONFIRMED;

        check(adapter.tick(generation, 61L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.IMPACT,
                "resolver retry does not mark a volley complete");
        adapter.tick(generation, 62L);
        check(adapter.tick(generation, 63L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.IMPACT,
                "cleanup retry still blocks the next volley");
        adapter.tick(generation, 64L);
        var cleaned = adapter.tick(generation, 65L);
        check(cleaned.fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.INTERMISSION
                        && cleaned.fallingAnvilState().deadlineTick() == 81L,
                "the 20-tick wait is anchored to impact confirmation at tick 61, not cleanup at tick 65");
        check(adapter.tick(generation, 80L).fallingAnvilState().phase()
                        == me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase.INTERMISSION
                        && effects.warnedVolleys.size() == 1,
                "delayed resolver and cleanup do not permit an early second warning");
        check(adapter.tick(generation, 81L).fallingAnvilState().volleyIndex() == 2
                        && effects.warnedVolleys.size() == 2,
                "the next warning can start when the impact-based one-second wait is due");
    }

    private static void delayedCleanupStartsFullSecondWarningAtActualTick() {
        long generation = 84L;
        UUID caster = id("66666666-6666-6666-6666-666666666666");
        FakeEffects effects = new FakeEffects();
        effects.nextCluster = new AnvilCluster(7, grid(30.0D));
        effects.cleanupAnvilSuccessAttempt = 25;
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startFallingAnvils(generation, caster, 11, 8, grid(0.0D),
                        true, true, true, true, 0L).accepted(),
                "high-roster Anvil cast starts before delayed-cleanup warning check");
        adapter.tick(generation, 60L);
        effects.anvilProgress = AnvilImpactProgress.CONFIRMED;
        for (long tick = 61L; tick <= 84L; tick++) adapter.tick(generation, tick);
        check(effects.warnedVolleys.size() == 1,
                "failed cleanup past the intermission deadline still blocks volley two");

        var secondWarning = adapter.tick(generation, 85L);
        check(secondWarning.fallingAnvilState().volleyIndex() == 2
                        && secondWarning.deadlineTick() == 145L
                        && effects.warnedVolleys.size() == 2,
                "the second warning receives all 60 ticks starting when cleanup permits it");
        check(adapter.tick(generation, 144L).stage() == Stage.TELEGRAPH
                        && effects.dropStarts == 1,
                "delayed cleanup cannot make the second volley drop before its full warning");
        check(adapter.tick(generation, 145L).stage() == Stage.ACTIVE
                        && effects.dropStarts == 2,
                "second volley drops only after 60 ticks from its actual warning start");
    }

    private static void missingSecondVolleyClusterRetriesBoundedlyThenReleasesSlot() {
        long generation = 841L;
        UUID caster = id("67676767-6767-6767-6767-676767676767");
        FakeEffects effects = new FakeEffects();
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startFallingAnvils(generation, caster, 11, 8, grid(0.0D),
                        true, true, true, true, 0L).accepted(),
                "high-roster Anvils start with a valid first volley");
        adapter.tick(generation, 60L);
        effects.anvilProgress = AnvilImpactProgress.CONFIRMED;
        adapter.tick(generation, 61L);
        check(adapter.tick(generation, 81L).stage() == Stage.ACTIVE
                        && effects.clusterSelections == 1,
                "the second volley starts searching when its post-impact wait expires");

        adapter.tick(generation, 82L);
        adapter.tick(generation, 90L);
        check(effects.clusterSelections == 1,
                "a missing safe cluster waits until the bounded retry interval");
        adapter.tick(generation, 91L);
        adapter.tick(generation, 101L);
        adapter.tick(generation, 111L);
        adapter.tick(generation, 121L);
        var released = adapter.tick(generation, 131L);
        check(released.stage() == Stage.IDLE && released.wave6Spell() == null
                        && effects.clusterSelections == 6 && !adapter.cleanupPending(),
                "bounded cluster retries release the shared spell slot without inventing a target");
    }

    private static void anvilImpactRequiresSuccessfulMovementOfEveryVisual() {
        List<LandingPoint> locked = grid(0.0D);
        List<FallingAnvilImpactConfirmation.VisualPosition> actual = new ArrayList<>();
        for (LandingPoint point : locked) {
            actual.add(new FallingAnvilImpactConfirmation.VisualPosition(true, true,
                    point.x(), point.y(), point.z()));
        }
        check(FallingAnvilImpactConfirmation.isConfirmed(1.0D, locked, actual),
                "the impact is confirmed after all visuals reach their locked positions");
        check(FallingAnvilImpactConfirmation.evaluate(1.0D, locked, actual)
                        == FallingAnvilImpactConfirmation.Outcome.CONFIRMED,
                "the finite drop resolves to confirmed only when every final position is observed");

        actual.set(12, new FallingAnvilImpactConfirmation.VisualPosition(false, true,
                locked.get(12).x(), locked.get(12).y(), locked.get(12).z()));
        check(!FallingAnvilImpactConfirmation.isConfirmed(1.0D, locked, actual),
                "a failed native teleport keeps the impact pending");
        check(FallingAnvilImpactConfirmation.evaluate(0.99D, locked, actual)
                        == FallingAnvilImpactConfirmation.Outcome.PENDING
                        && FallingAnvilImpactConfirmation.evaluate(1.0D, locked, actual)
                        == FallingAnvilImpactConfirmation.Outcome.FAILED
                        && FallingAnvilImpactConfirmation.evaluate(1.0D, locked, List.of())
                        == FallingAnvilImpactConfirmation.Outcome.FAILED,
                "missing movement stays retryable before but fails at the finite drop deadline");

        actual.set(12, new FallingAnvilImpactConfirmation.VisualPosition(true, true,
                locked.get(12).x(), locked.get(12).y() + 0.25D, locked.get(12).z()));
        check(!FallingAnvilImpactConfirmation.isConfirmed(1.0D, locked, actual),
                "a visual short of its locked point keeps the impact pending");

        actual.set(12, new FallingAnvilImpactConfirmation.VisualPosition(true, false,
                locked.get(12).x(), locked.get(12).y(), locked.get(12).z()));
        check(!FallingAnvilImpactConfirmation.isConfirmed(1.0D, locked, actual),
                "a visual in another world cannot confirm the impact");
        actual.set(12, new FallingAnvilImpactConfirmation.VisualPosition(true, true,
                locked.get(12).x(), locked.get(12).y(), locked.get(12).z()));
        check(!FallingAnvilImpactConfirmation.isConfirmed(0.99D, locked, actual),
                "successful movement before the drop completes remains pending");
    }

    private static void anvilVisualLossCancelsCastWithoutConfirmingImpact() {
        long generation = 87L;
        UUID caster = id("abababab-abab-abab-abab-abababababab");
        FakeEffects effects = new FakeEffects();
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startFallingAnvils(generation, caster, 11, 8, grid(0.0D),
                        true, true, true, true, 0L).accepted(),
                "the bounded visual-loss scenario begins with a valid volley");
        check(adapter.tick(generation, 60L).stage() == Stage.ACTIVE
                        && effects.dropStarts == 1,
                "the warning completes and starts the owned visual drop");
        effects.anvilProgress = AnvilImpactProgress.FAILED;

        var cancelled = adapter.tick(generation, 70L);
        check(cancelled.stage() == Stage.IDLE
                        && effects.anvilResolveAttempts == 0
                        && effects.anvilCleanupAttempts == 1
                        && !adapter.cleanupPending(),
                "a visual that cannot reach its locked point cancels and cleans without an impact receipt");
    }

    private static void anvilPendingStatusCannotHangPastDropDeadline() {
        long generation = 88L;
        UUID caster = id("bcbcbcbc-bcbc-bcbc-bcbc-bcbcbcbcbcbc");
        FakeEffects effects = new FakeEffects();
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startFallingAnvils(generation, caster, 11, 8, grid(0.0D),
                        true, true, true, true, 0L).accepted(),
                "the indefinitely pending scenario begins with a valid volley");
        adapter.tick(generation, 60L);
        check(adapter.tick(generation, 69L).stage() == Stage.ACTIVE
                        && effects.anvilResolveAttempts == 0
                        && effects.anvilCleanupAttempts == 0,
                "the visual may still complete during its ten-tick drop window");

        var cancelled = adapter.tick(generation, 70L);
        check(cancelled.stage() == Stage.IDLE
                        && effects.anvilResolveAttempts == 0
                        && effects.anvilCleanupAttempts == 1
                        && !adapter.cleanupPending(),
                "a persistently pending impact is cancelled and cleaned at the adapter deadline");
    }

    private static void gravityAnchorLandingRequiresAnAppliedLiftAndSuppressesOnlyItsFall() {
        check(GravityAnchorLandingPolicy.shouldApplyImpulse(true, true)
                        && !GravityAnchorLandingPolicy.shouldApplyImpulse(false, true)
                        && !GravityAnchorLandingPolicy.shouldApplyImpulse(true, false),
                "Gravity Anchor impulses require an in-arena projected step and clear movement path");
        check(!GravityAnchorLandingPolicy.shouldRecordLift(false, true, 0.2D),
                "a newly blocked line of sight cannot record a lift");
        check(!GravityAnchorLandingPolicy.shouldRecordLift(true, false, 0.2D),
                "a blocked movement path cannot record a lift");
        check(!GravityAnchorLandingPolicy.shouldRecordLift(true, true, 0.0D),
                "a horizontal or downward velocity is not an Anchor lift");
        check(GravityAnchorLandingPolicy.shouldRecordLift(true, true, 0.2D),
                "an applied upward impulse records the cohort member as lifted");

        check(!GravityAnchorLandingPolicy.shouldApplyLandingDamage(false, true, false),
                "a grounded cohort member who never received an impulse takes no Anchor landing damage");
        check(GravityAnchorLandingPolicy.shouldApplyLandingDamage(true, true, false),
                "a grounded participant who was lifted receives the bounded Anchor landing hit");
        check(!GravityAnchorLandingPolicy.shouldApplyLandingDamage(true, false, false)
                        && !GravityAnchorLandingPolicy.shouldApplyLandingDamage(true, true, true),
                "landing damage requires a grounded, unresolved lifted participant");

        check(GravityAnchorLandingPolicy.shouldSuppressVanillaFallDamage(true, true, false),
                "vanilla Fall is suppressed for a lifted Anchor participant before its landing resolves");
        check(!GravityAnchorLandingPolicy.shouldSuppressVanillaFallDamage(true, false, false)
                        && !GravityAnchorLandingPolicy.shouldSuppressVanillaFallDamage(false, true, false)
                        && !GravityAnchorLandingPolicy.shouldSuppressVanillaFallDamage(true, true, true),
                "unrelated falls and falls after Anchor resolution keep vanilla behavior");
    }

    private static void gravityAnchorCapturesOnlyNearbyEligibleFreePlayers() {
        List<UUID> cohort = GravityAnchorCohortPolicy.capture(new AnchorPoint(0.0D, 70.0D, 0.0D),
                List.of(
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000003"),
                                3.0D, 70.0D, 0.0D, true, true, true),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000001"),
                                0.0D, 70.0D, 0.0D, true, true, true),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000004"),
                                5.01D, 70.0D, 0.0D, true, true, true),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000005"),
                                2.0D, 70.0D, 0.0D, false, true, true),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000006"),
                                2.0D, 70.0D, 0.0D, true, false, true),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000007"),
                                2.0D, 70.0D, 0.0D, true, true, false),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000002"),
                                5.0D, 70.0D, 0.0D, true, true, true),
                        new GravityAnchorCohortPolicy.Candidate(id("00000000-0000-0000-0000-000000000008"),
                                4.5D, 74.0D, 0.0D, true, true, true)));
        check(cohort.equals(List.of(
                        id("00000000-0000-0000-0000-000000000001"),
                        id("00000000-0000-0000-0000-000000000002"),
                        id("00000000-0000-0000-0000-000000000003"),
                        id("00000000-0000-0000-0000-000000000008"))),
                "capture uses the five-block horizontal boundary from the fixed anchor");

        List<GravityAnchorCohortPolicy.Candidate> dense = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            String suffix = String.format(java.util.Locale.ROOT, "%012x", index + 100);
            dense.add(new GravityAnchorCohortPolicy.Candidate(
                    id("00000000-0000-0000-0000-" + suffix),
                    0.0D, 70.0D, 0.0D, true, true, true));
        }
        check(GravityAnchorCohortPolicy.capture(new AnchorPoint(0.0D, 70.0D, 0.0D), dense).size() == 20,
                "cohort capture remains bounded by the Wave 6 roster cap");
    }

    private static void gravityAnchorCarriesOneCapturedCohortThroughCleanup() {
        long generation = 85L;
        UUID caster = id("77777777-7777-7777-7777-777777777777");
        UUID target = id("88888888-8888-8888-8888-888888888888");
        List<UUID> cohort = List.of(target,
                id("99999999-9999-9999-9999-999999999999"));
        FakeEffects effects = new FakeEffects();
        effects.crystalToSpawn = id("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        effects.gravityAnchorCohort = cohort;
        Wave6RitualRuntimeAdapter adapter = new Wave6RitualRuntimeAdapter(
                new RitualSpellController(generation), effects);

        check(adapter.startGravityAnchor(generation, caster, target,
                        new AnchorPoint(0.5D, 70.0D, 0.5D), 0,
                        true, true, true, true, 0L).accepted(),
                "Gravity Anchor begins with an eligible primary target");
        var pulling = adapter.tick(generation, 40L);
        check(pulling.stage() == Stage.ACTIVE
                        && pulling.gravityAnchorState().phase() == Phase.PULL
                        && effects.anchorEffectCohorts.get(0).equals(cohort),
                "the cohort is captured once as pull starts after warning");
        effects.anchorPullFinished = true;
        adapter.tick(generation, 41L);
        adapter.tick(generation, 42L);
        effects.anchorLandingFinished = true;
        var completed = adapter.tick(generation, 43L);
        check(completed.stage() == Stage.RECOVERY
                        && effects.anchorEffectCohorts.size() == 5
                        && effects.anchorEffectCohorts.stream().allMatch(cohort::equals),
                "pull polling, explosion, landing and cleanup all retain the same captured cohort");
    }

    private static List<LandingPoint> grid(double origin) {
        List<LandingPoint> positions = new ArrayList<>(25);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                positions.add(new LandingPoint(origin + x, 70.0D, origin + z));
            }
        }
        return List.copyOf(positions);
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    private static final class FakeEffects implements Effects {
        private UUID crystalToSpawn;
        private List<UUID> gravityAnchorCohort = List.of();
        private final List<List<UUID>> anchorEffectCohorts = new ArrayList<>();
        private boolean anchorWarningAvailable = true;
        private boolean anchorPullFinished;
        private boolean anchorLandingFinished;
        private int anchorPullStarts;
        private int anchorWarningFrames;
        private int safeExplosionCount;
        private int anchorCleanupCount;
        private int anchorCleanupSuccessAttempt = 1;
        private int dropStarts;
        private AnvilImpactProgress anvilProgress = AnvilImpactProgress.PENDING;
        private int damageResolutions;
        private int anvilVisualCleanups;
        private int anvilResolveAttempts;
        private int anvilCleanupAttempts;
        private int resolveAnvilSuccessAttempt = 1;
        private int cleanupAnvilSuccessAttempt = 1;
        private int anvilWarningFrames;
        private int clusterSelections;
        private AnvilCluster nextCluster;
        private final List<String> calls = new ArrayList<>();
        private final List<Wave6RitualRuntimeAdapter.AnvilVolley> warnedVolleys = new ArrayList<>();

        @Override
        public UUID spawnEndCrystal(long generation, UUID caster, UUID target, AnchorPoint fixedPoint) {
            calls.add("spawn-crystal:" + fixedPoint);
            return crystalToSpawn;
        }

        @Override
        public boolean showGravityAnchorWarning(Wave6RitualRuntimeAdapter.GravityAnchorCast cast,
                                                int warningTicks) {
            calls.add("warning:" + warningTicks + ":" + cast.crystalEntity());
            return anchorWarningAvailable;
        }

        @Override
        public boolean renderGravityAnchorWarningFrame(
                Wave6RitualRuntimeAdapter.GravityAnchorCast cast, long ticksRemaining) {
            anchorWarningFrames++;
            return ticksRemaining > 0L;
        }

        @Override
        public boolean fireRiftBarrageIntoCrystal(Wave6RitualRuntimeAdapter.GravityAnchorCast cast,
                                                  int projectileCount) {
            calls.add("barrage:" + projectileCount + ":" + cast.crystalEntity());
            return true;
        }

        @Override
        public boolean beginGravityAnchorPull(Wave6RitualRuntimeAdapter.GravityAnchorCast cast) {
            anchorPullStarts++;
            anchorEffectCohorts.add(cast.affectedPlayers());
            return true;
        }

        @Override
        public Optional<List<UUID>> captureGravityAnchorCohort(
                Wave6RitualRuntimeAdapter.GravityAnchorCast cast) {
            return Optional.of(gravityAnchorCohort);
        }

        @Override
        public boolean isGravityAnchorPullResolved(Wave6RitualRuntimeAdapter.GravityAnchorCast cast) {
            anchorEffectCohorts.add(cast.affectedPlayers());
            return anchorPullFinished;
        }

        @Override
        public boolean explodeGravityAnchorWithoutTerrainDamage(
                Wave6RitualRuntimeAdapter.GravityAnchorCast cast) {
            safeExplosionCount++;
            anchorEffectCohorts.add(cast.affectedPlayers());
            return true;
        }

        @Override
        public boolean resolveGravityAnchorLanding(Wave6RitualRuntimeAdapter.GravityAnchorCast cast) {
            anchorEffectCohorts.add(cast.affectedPlayers());
            return anchorLandingFinished;
        }

        @Override
        public boolean cleanupGravityAnchor(Wave6RitualRuntimeAdapter.GravityAnchorCast cast) {
            anchorCleanupCount++;
            anchorEffectCohorts.add(cast.affectedPlayers());
            return anchorCleanupCount >= anchorCleanupSuccessAttempt;
        }

        @Override
        public boolean showAnvilWarning(Wave6RitualRuntimeAdapter.AnvilVolley volley,
                                        int warningTicks) {
            warnedVolleys.add(volley);
            return warningTicks == 60;
        }

        @Override
        public boolean renderAnvilWarningFrame(
                Wave6RitualRuntimeAdapter.AnvilVolley volley, long ticksRemaining) {
            anvilWarningFrames++;
            return ticksRemaining > 0L;
        }

        @Override
        public boolean beginAnvilDrop(Wave6RitualRuntimeAdapter.AnvilVolley volley) {
            dropStarts++;
            return true;
        }

        @Override
        public AnvilImpactProgress tickAnvilDrop(Wave6RitualRuntimeAdapter.AnvilVolley volley) {
            return anvilProgress;
        }

        @Override
        public boolean resolveAnvilImpact(Wave6RitualRuntimeAdapter.AnvilVolley volley) {
            anvilResolveAttempts++;
            if (anvilResolveAttempts < resolveAnvilSuccessAttempt) return false;
            damageResolutions++;
            return true;
        }

        @Override
        public boolean cleanupAnvilVolley(Wave6RitualRuntimeAdapter.AnvilVolley volley) {
            anvilCleanupAttempts++;
            anvilVisualCleanups++;
            return anvilCleanupAttempts >= cleanupAnvilSuccessAttempt;
        }

        @Override
        public AnvilCluster chooseNextDenseEligibleCluster(
                int participants, Wave6RitualRuntimeAdapter.AnvilVolley previousVolley) {
            clusterSelections++;
            return nextCluster;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
