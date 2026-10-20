import me.copimine.endevent.domain.BossDefeatCinematicPolicy;

public final class BossDefeatCinematicPolicyTest {
    public static void main(String[] args) {
        testOnlyAPlayerOwnedOfficialKillCanStart();
        testTimelineDelaysTheActualDeathCommit();
        testInterruptedDefeatIsFinalizedAfterRestart();
        System.out.println("BossDefeatCinematicPolicyTest OK");
    }

    private static void testOnlyAPlayerOwnedOfficialKillCanStart() {
        check(BossDefeatCinematicPolicy.canStart(true, false, true, true),
                "official player kill must start the defeat cinematic");
        check(!BossDefeatCinematicPolicy.canStart(false, false, true, true),
                "test or synthetic kills must not start the official cinematic");
        check(!BossDefeatCinematicPolicy.canStart(true, true, true, true),
                "defeat cinematic must be idempotent");
        check(!BossDefeatCinematicPolicy.canStart(true, false, false, true),
                "already dead boss must not start a cinematic");
        check(!BossDefeatCinematicPolicy.canStart(true, false, true, false),
                "environmental or non-player kill must not start the player finisher");
    }

    private static void testTimelineDelaysTheActualDeathCommit() {
        check(BossDefeatCinematicPolicy.phaseAt(0) == BossDefeatCinematicPolicy.Phase.TELEGRAPH,
                "defeat must begin with a telegraph");
        check(BossDefeatCinematicPolicy.phaseAt(BossDefeatCinematicPolicy.COLLAPSE_TICK)
                        == BossDefeatCinematicPolicy.Phase.COLLAPSE,
                "defeat must have a collapse phase");
        check(!BossDefeatCinematicPolicy.shouldCommit(BossDefeatCinematicPolicy.COMMIT_TICK - 1),
                "boss death must not commit before the final flash");
        check(BossDefeatCinematicPolicy.shouldCommit(BossDefeatCinematicPolicy.COMMIT_TICK),
                "boss death must commit at one deterministic tick");
        check(!BossDefeatCinematicPolicy.shouldCommit(BossDefeatCinematicPolicy.COMMIT_TICK + 1),
                "boss death commit must not repeat");
        check(BossDefeatCinematicPolicy.phaseAt(BossDefeatCinematicPolicy.TOTAL_TICKS)
                        == BossDefeatCinematicPolicy.Phase.COMPLETE,
                "defeat cinematic must be bounded");
    }

    private static void testInterruptedDefeatIsFinalizedAfterRestart() {
        check(BossDefeatCinematicPolicy.shouldFinalizeAfterRestart(
                        true, false, true, 0.0D),
                "an interrupted player finisher with zero virtual HP must finalize after restart");
        check(!BossDefeatCinematicPolicy.shouldFinalizeAfterRestart(
                        true, false, true, 25.0D),
                "a live boss with positive virtual HP must remain damageable after restart");
        check(!BossDefeatCinematicPolicy.shouldFinalizeAfterRestart(
                        false, false, true, 0.0D),
                "a non-finish phase must not be treated as an interrupted defeat");
        check(!BossDefeatCinematicPolicy.shouldFinalizeAfterRestart(
                        true, true, true, 0.0D),
                "an already committed victory must not be committed twice");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
