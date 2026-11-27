import me.copimine.endevent.domain.BossCopyCooperationPolicy;
import me.copimine.endevent.domain.BossCopyCooperationPolicy.AdmissionDecision;
import me.copimine.endevent.domain.BossCopyCooperationPolicy.AdmissionState;
import me.copimine.endevent.domain.BossCopyCooperationPolicy.CopyLoss;
import me.copimine.endevent.domain.BossCopyCooperationPolicy.Technique;
import me.copimine.endevent.domain.BossCopyCooperationPolicy.Timeline;

/**
 * V7-21 / §10.4.2: cooperative techniques are only legal when the party
 * actually has enough living participants and copies, the cast is prepared and
 * fixed, a copy loss is handled without an invisible shot, and no common
 * unavoidable combination is produced.
 *
 * <p>The repertoire is provisional, so every number here is a window
 * (25-35 s, 20-30 s, 40-60 s) rather than an invented constant.</p>
 */
public final class BossCopyCooperationPolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        // ---- the three provisional profiles exist -------------------------
        check("three cooperative techniques are supported",
                BossCopyCooperationPolicy.supportedTechniques().size() == 3);
        check("mirror substitution is addressable by id",
                BossCopyCooperationPolicy.techniqueFor(" mirror_substitution ")
                        == Technique.MIRROR_SUBSTITUTION);
        check("coordinated salvo is addressable by id",
                BossCopyCooperationPolicy.techniqueFor("COORDINATED_SALVO")
                        == Technique.COORDINATED_SALVO);
        check("reflection chain is addressable by id",
                BossCopyCooperationPolicy.techniqueFor("Reflection_Chain")
                        == Technique.REFLECTION_CHAIN);
        check("an unknown id is refused",
                BossCopyCooperationPolicy.techniqueFor("nope") == null
                        && BossCopyCooperationPolicy.techniqueFor(null) == null
                        && BossCopyCooperationPolicy.techniqueFor("  ") == null);

        // ---- cooldown windows are real bands -------------------------------
        check("mirror cooldown clamps to 25-35 s",
                Technique.MIRROR_SUBSTITUTION.clampCooldownTicks(1) == 25 * 20
                        && Technique.MIRROR_SUBSTITUTION.clampCooldownTicks(9_999) == 35 * 20);
        check("salvo cooldown clamps to 20-30 s",
                Technique.COORDINATED_SALVO.clampCooldownTicks(1) == 20 * 20
                        && Technique.COORDINATED_SALVO.clampCooldownTicks(9_999) == 30 * 20);
        check("chain cooldown clamps to 40-60 s",
                Technique.REFLECTION_CHAIN.clampCooldownTicks(1) == 40 * 20
                        && Technique.REFLECTION_CHAIN.clampCooldownTicks(9_999) == 60 * 20);
        check("default cooldown is the mid-point of the band",
                Technique.MIRROR_SUBSTITUTION.defaultCooldownSeconds() == 30
                        && Technique.COORDINATED_SALVO.defaultCooldownSeconds() == 25
                        && Technique.REFLECTION_CHAIN.defaultCooldownSeconds() == 50);

        // ---- preparation before commitment --------------------------------
        for (Technique technique : Technique.values()) {
            Timeline timeline = BossCopyCooperationPolicy.timeline(technique, 1_000L);
            check(technique.id() + " has a timeline",
                    timeline != null && timeline.technique() == technique);
            check(technique.id() + " fixes at or after preparation starts",
                    timeline.fixTick() > timeline.startTick());
            check(technique.id() + " executes at or after fixation",
                    !timeline.executeTicks().isEmpty()
                            && timeline.executeTicks().get(0) >= timeline.fixTick());
            check(technique.id() + " cleanup follows recovery",
                    timeline.cleanupTick() > timeline.recoveryStartTick());
            check(technique.id() + " is inactive before it starts",
                    !timeline.activeAt(timeline.startTick() - 1L));
            check(technique.id() + " is inactive after cleanup",
                    !timeline.activeAt(timeline.cleanupTick() + 1L));
            // No damage tick before fixation: preparation is honest.
            for (long tick = timeline.startTick(); tick < timeline.fixTick(); tick++) {
                check(technique.id() + " cannot damage before fixation at " + tick,
                        !timeline.executesAt(tick));
            }
            // The last execute tick is the only damage window late in the cast.
            check(technique.id() + " last execute tick is ordered",
                    timeline.lastExecuteTick() >= timeline.fixTick());
        }
        check("a null technique has no timeline",
                BossCopyCooperationPolicy.timeline(null, 0L) == null);

        // ---- sufficient living participants and copies --------------------
        AdmissionState ready = new AdmissionState(4, 2, 0,
                true, true, true, true, true, 0);
        AdmissionDecision salvo = BossCopyCooperationPolicy.mayFire(
                Technique.COORDINATED_SALVO, 10_000L, 0L, ready, -1);
        check("a ready party admits the salvo", salvo.accepted());
        check("an admitted salvo carries a timeline",
                salvo.timeline() != null && salvo.technique() == Technique.COORDINATED_SALVO);

        AdmissionDecision tooFew = BossCopyCooperationPolicy.mayFire(
                Technique.COORDINATED_SALVO, 10_000L, 0L,
                new AdmissionState(2, 2, 0, true, true, true, true, true, 0), -1);
        check("two players cannot run the three-body salvo",
                !tooFew.accepted() && "INSUFFICIENT_PARTICIPANTS".equals(tooFew.reason()));

        AdmissionDecision tooFewCopies = BossCopyCooperationPolicy.mayFire(
                Technique.COORDINATED_SALVO, 10_000L, 0L,
                new AdmissionState(4, 1, 0, true, true, true, true, true, 0), -1);
        check("one copy cannot run the salvo",
                !tooFewCopies.accepted() && "INSUFFICIENT_COPIES".equals(tooFewCopies.reason()));

        // ---- cancellation matrix ------------------------------------------
        check("no guardian means no technique",
                "GUARDIAN_DEAD".equals(BossCopyCooperationPolicy.mayFire(
                        Technique.MIRROR_SUBSTITUTION, 10_000L, 0L,
                        new AdmissionState(4, 2, 0, false, true, true, true, true, 0), -1)
                        .reason()));
        check("no route means no technique",
                "NO_ROUTE".equals(BossCopyCooperationPolicy.mayFire(
                        Technique.MIRROR_SUBSTITUTION, 10_000L, 0L,
                        new AdmissionState(4, 2, 0, true, false, true, true, true, 0), -1)
                        .reason()));
        check("a busy heavy action refuses",
                "HEAVY_ACTION_BUSY".equals(BossCopyCooperationPolicy.mayFire(
                        Technique.MIRROR_SUBSTITUTION, 10_000L, 0L,
                        new AdmissionState(4, 2, 0, true, true, false, true, true, 0), -1)
                        .reason()));
        check("no honest window refuses",
                "NO_HONEST_WINDOW".equals(BossCopyCooperationPolicy.mayFire(
                        Technique.MIRROR_SUBSTITUTION, 10_000L, 0L,
                        new AdmissionState(4, 2, 0, true, true, true, false, true, 0), -1)
                        .reason()));
        check("a null technique is refused",
                !BossCopyCooperationPolicy.mayFire(
                        null, 10_000L, 0L, ready, -1).accepted());

        // Cooldown
        AdmissionDecision cooling = BossCopyCooperationPolicy.mayFire(
                Technique.MIRROR_SUBSTITUTION, 10_000L, 9_900L, ready, -1);
        check("cooldown refuses a repeat", !cooling.accepted()
                && "COOLDOWN".equals(cooling.reason()));
        AdmissionDecision readyAgain = BossCopyCooperationPolicy.mayFire(
                Technique.MIRROR_SUBSTITUTION, 10_000L,
                10_000L - Technique.MIRROR_SUBSTITUTION.defaultCooldownTicks(), ready, -1);
        check("the technique may fire after the band", readyAgain.accepted());

        // ---- no common unavoidable combination ----------------------------
        check("one beam at a time is allowed",
                BossCopyCooperationPolicy.mayFire(Technique.REFLECTION_CHAIN,
                        10_000L, 0L,
                        new AdmissionState(4, 2, 1, true, true, true, true, true, 0), -1)
                        .accepted());
        check("a second simultaneous beam is refused",
                "BEAM_CONCURRENCY".equals(BossCopyCooperationPolicy.mayFire(
                        Technique.REFLECTION_CHAIN, 10_000L, 0L,
                        new AdmissionState(4, 2, 2, true, true, true, true, true, 0), -1)
                        .reason()));
        check("a recently hard-controlled target is not grabbed again",
                "HARD_CONTROL_BUSY".equals(BossCopyCooperationPolicy.mayFire(
                        Technique.REFLECTION_CHAIN, 10_000L, 0L,
                        new AdmissionState(4, 2, 0, true, true, true, true, true, 1), -1)
                        .reason()));
        check("heavy techniques never overlap another heavy action",
                !BossCopyCooperationPolicy.allowsConcurrentHeavy(Technique.MIRROR_SUBSTITUTION)
                        && !BossCopyCooperationPolicy.allowsConcurrentHeavy(
                        Technique.COORDINATED_SALVO)
                        && !BossCopyCooperationPolicy.allowsConcurrentHeavy(
                        Technique.REFLECTION_CHAIN));
        check("no friendly fire", !BossCopyCooperationPolicy.friendlyFireAllowed()
                && BossCopyCooperationPolicy.damagesPlayersOnly());
        check("no loot or phase progress from a cooperative technique",
                !BossCopyCooperationPolicy.dropsLoot()
                        && !BossCopyCooperationPolicy.countsTowardPhaseProgress());

        // ---- copy loss -----------------------------------------------------
        check("loss before fixation cancels every technique",
                BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.MIRROR_SUBSTITUTION, false, 2) == CopyLoss.CANCEL
                        && BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.COORDINATED_SALVO, false, 2) == CopyLoss.CANCEL
                        && BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.REFLECTION_CHAIN, false, 2) == CopyLoss.CANCEL);
        check("loss after fixation cancels the mirror swap",
                BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.MIRROR_SUBSTITUTION, true, 1) == CopyLoss.CANCEL);
        check("loss after fixation breaks the reflection chain",
                BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.REFLECTION_CHAIN, true, 1) == CopyLoss.CANCEL);
        check("loss after fixation shortens the salvo while a copy lives",
                BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.COORDINATED_SALVO, true, 1) == CopyLoss.SHORTENED);
        check("loss after fixation with no copy left cancels the salvo",
                BossCopyCooperationPolicy.copyLossOutcome(
                        Technique.COORDINATED_SALVO, true, 0) == CopyLoss.CANCEL);
        check("a dead source may never fire",
                !BossCopyCooperationPolicy.mayFireFrom(false)
                        && BossCopyCooperationPolicy.mayFireFrom(true));

        // ---- miss / damage window ----------------------------------------
        Timeline salvoTimeline = BossCopyCooperationPolicy.timeline(
                Technique.COORDINATED_SALVO, 100L);
        long first = salvoTimeline.executeTicks().get(0);
        check("damage lands only on an execute tick",
                salvoTimeline.executesAt(first)
                        && !salvoTimeline.executesAt(first + 1)
                        && !salvoTimeline.executesAt(first - 1));
        check("the salvo has exactly three volleys",
                salvoTimeline.executeTicks().size()
                        == BossCopyCooperationPolicy.SALVO_VOLLEYS);

        // ---- cleanup is always required ----------------------------------
        check("cleanup is required for every terminal reason",
                BossCopyCooperationPolicy.requiresCleanup("expired")
                        && BossCopyCooperationPolicy.requiresCleanup("copy-lost")
                        && BossCopyCooperationPolicy.requiresCleanup(null));

        // ---- description -------------------------------------------------
        String description = BossCopyCooperationPolicy.describe(
                Technique.COORDINATED_SALVO, salvoTimeline);
        check("description names the technique",
                description.contains("coordinated_salvo")
                        && description.contains("fix="));
        check("description of an unknown technique is explicit",
                BossCopyCooperationPolicy.describe(null, null)
                        .contains("refused=unknown-technique"));

        System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
        if (failures != 0) System.exit(1);
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            System.out.println("FAIL " + name);
            failures++;
        }
    }
}
