import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.GuardianV5MeleePolicy;
import me.copimine.endevent.domain.GuardianV5MeleePolicy.Branch;
import me.copimine.endevent.domain.GuardianV5MeleePolicy.Move;

/**
 * V5.1 Guardian melee-first contract: fast alternating contacts, a 120..130
 * degree multi-target Fan, a punishable committed Heavy, a bounded Separation
 * and the three post-separation branches.
 */
public final class GuardianV5MeleePolicyTest {
    public static void main(String[] args) {
        basicHandsAlternate();
        fanIsAForwardConeNotACircle();
        heavyIsCommittedAndPunishable();
        separationIsBounded();
        branchesNeverExceedTwoSpellBeats();
        meleeIsTheDefaultOutsideTheCocoon();
        System.out.println("GuardianV5MeleePolicyTest OK");
    }

    private static void basicHandsAlternate() {
        check(GuardianV5MeleePolicy.basicMove(0L) == Move.BASIC_LEFT,
                "the first accepted basic is the left hand");
        check(GuardianV5MeleePolicy.basicMove(1L) == Move.BASIC_RIGHT,
                "the second accepted basic is the right hand");
        check(GuardianV5MeleePolicy.basicMove(2L) == Move.BASIC_LEFT,
                "the pose alternates instead of repeating one animation");
        check(GuardianV5MeleePolicy.BASIC_WINDUP_TICKS >= 4
                        && GuardianV5MeleePolicy.BASIC_WINDUP_TICKS <= 7,
                "the basic windup is 0.2..0.35 s");
        check(GuardianV5MeleePolicy.BASIC_CADENCE_TICKS
                        < GuardianV5MeleePolicy.FAN_COOLDOWN_TICKS,
                "the basic hand is more frequent than the committed Fan");
    }

    private static void fanIsAForwardConeNotACircle() {
        check(GuardianV5MeleePolicy.FAN_TOTAL_DEGREES >= 120.0D
                        && GuardianV5MeleePolicy.FAN_TOTAL_DEGREES <= 130.0D,
                "the Fan is a 120..130 degree front cone");
        check(GuardianV5MeleePolicy.FAN_RANGE_MIN_BLOCKS >= 4.5D
                        && GuardianV5MeleePolicy.FAN_RANGE_MAX_BLOCKS <= 5.0D,
                "the Fan reaches 4.5..5 blocks");
        check(GuardianV5MeleePolicy.fanHits(3.0D, 0.0D),
                "a player directly in front is hit");
        check(GuardianV5MeleePolicy.fanHits(2.0D, 3.4D),
                "a player near the edge of the cone is hit");
        check(!GuardianV5MeleePolicy.fanHits(0.5D, 4.0D),
                "a player at the side is outside the cone");
        check(!GuardianV5MeleePolicy.fanHits(-2.0D, 0.0D),
                "a player behind the boss is never hit by the Fan");
        check(!GuardianV5MeleePolicy.fanHits(5.1D, 0.0D),
                "a player just beyond the range is safe");
        check(!GuardianV5MeleePolicy.fanHits(2.0D, 4.6D),
                "the cone is not secretly a full circle");
    }

    private static void heavyIsCommittedAndPunishable() {
        check(GuardianV5MeleePolicy.HEAVY_WINDUP_TICKS >= 12
                        && GuardianV5MeleePolicy.HEAVY_WINDUP_TICKS <= 20,
                "the Heavy warns for 0.6..1.0 s");
        check(GuardianV5MeleePolicy.HEAVY_RECOVERY_TICKS >= 10,
                "a missed Heavy leaves a real recovery window");
        check(GuardianV5MeleePolicy.punishWindowTicks(false)
                        == GuardianV5MeleePolicy.HEAVY_RECOVERY_TICKS,
                "a miss is the punish window");
        check(GuardianV5MeleePolicy.punishWindowTicks(true)
                        < GuardianV5MeleePolicy.HEAVY_RECOVERY_TICKS,
                "a landed Heavy recovers faster than a whiff");
        check(GuardianV5MeleePolicy.heavyHits(4.0D, 0.0D),
                "a player in the heavy wedge is hit");
        check(!GuardianV5MeleePolicy.heavyHits(4.0D, 3.0D),
                "side-stepping out of the narrow wedge is a genuine miss");
        check(!GuardianV5MeleePolicy.heavyHits(5.2D, 0.0D),
                "the Heavy has a bounded reach");
    }

    private static void separationIsBounded() {
        check(GuardianV5MeleePolicy.separationRadius()
                        >= GuardianV5MeleePolicy.SEPARATION_RADIUS_MIN_BLOCKS
                        && GuardianV5MeleePolicy.separationRadius()
                        <= GuardianV5MeleePolicy.SEPARATION_RADIUS_MAX_BLOCKS,
                "Separation is a 4.5..5 block wave");
        check(GuardianV5MeleePolicy.SEPARATION_PUSH_MIN_BLOCKS >= 4.0D
                        && GuardianV5MeleePolicy.SEPARATION_PUSH_MAX_BLOCKS <= 5.0D,
                "the push is 4..5 blocks");
        check(GuardianV5MeleePolicy.separationHits(4.9D, 0.0D),
                "a close player is displaced");
        check(!GuardianV5MeleePolicy.separationHits(5.1D, 0.0D),
                "a player outside the wave is untouched");
        check(GuardianV5MeleePolicy.SEPARATION_WARNING_TICKS >= 11
                        && GuardianV5MeleePolicy.SEPARATION_WARNING_TICKS <= 16,
                "the warning is 0.55..0.8 s");
        check(GuardianV5MeleePolicy.SEPARATION_COOLDOWN_TICKS >= 240
                        && GuardianV5MeleePolicy.SEPARATION_COOLDOWN_TICKS <= 360,
                "the effective cooldown is 12..18 s");
    }

    private static void branchesNeverExceedTwoSpellBeats() {
        check(GuardianV5MeleePolicy.chooseBranch(true, true) == Branch.TWO_SPELLS,
                "two legal spells choose the two-spell branch");
        check(GuardianV5MeleePolicy.chooseBranch(true, false) == Branch.ONE_RANGED_BEAT,
                "one legal spell chooses the single ranged beat");
        check(GuardianV5MeleePolicy.chooseBranch(false, false) == Branch.IMMEDIATE_RUSH,
                "a refused spell slot still rushes back into melee");
        check(GuardianV5MeleePolicy.maxSpellBeats(Branch.TWO_SPELLS) == 2,
                "the two-spell branch is capped at exactly two beats");
        check(GuardianV5MeleePolicy.maxSpellBeats(Branch.IMMEDIATE_RUSH) == 0,
                "the rush branch casts nothing");
        check(GuardianV5MeleePolicy.distinctBeats("CHEST_STRIKE", "GROUND_SLAM"),
                "the two beats must be distinct actions");
        check(!GuardianV5MeleePolicy.distinctBeats("GROUND_SLAM", "GROUND_SLAM"),
                "the same spell twice is not a two-spell branch");
        check(GuardianV5MeleePolicy.committedRotation().contains(Move.FAN)
                        && GuardianV5MeleePolicy.committedRotation().contains(Move.HEAVY),
                "the committed rotation uses both the Fan and the Heavy");
    }

    private static void meleeIsTheDefaultOutsideTheCocoon() {
        for (BossPhase phase : BossPhase.values()) {
            if (phase == BossPhase.LAST_SEAL) {
                continue;
            }
            check(GuardianV5MeleePolicy.meleeAllowed(phase, false),
                    "grounded melee is legal in " + phase);
            check(!GuardianV5MeleePolicy.meleeAllowed(phase, true),
                    "an intact cocoon blocks grounded melee in " + phase);
        }
        check(GuardianV5MeleePolicy.meleeAllowed(BossPhase.LAST_SEAL, false),
                "after the cocoon falls the final stand returns to melee");
        check(!GuardianV5MeleePolicy.mayUseCommittedMove(1_000L, 2_000L, false, false),
                "a committed move respects its own cooldown");
        check(!GuardianV5MeleePolicy.mayUseCommittedMove(3_000L, 2_000L, true, false),
                "a committed move never interrupts a running cast");
        check(GuardianV5MeleePolicy.mayUseCommittedMove(3_000L, 2_000L, false, false),
                "a free committed move is legal between casts");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
