import java.util.List;
import me.copimine.endevent.domain.BossStage;
import me.copimine.endevent.domain.BossStagePolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;

public final class BossStagePolicyTest {
    public static void main(String[] args) {
        testExactThresholdsAndTitles();
        testLargeHitReportsEveryCrossedStage();
        testStageNeverRegressesAfterTransientHealthRecovery();
        testJudgmentIsOneShotAtFiveHundred();
        testStageSpellPoolsAreProgressiveAndNamed();
        testThresholdSummonsAreReachableAndAbsorptionHasPressureTools();
        testBossMovementProfileEscalatesByStage();
        testPostAbsorptionProfileAddsBoundedEnrage();
        System.out.println("BossStagePolicyTest OK");
    }

    private static void testExactThresholdsAndTitles() {
        check(BossStagePolicy.stageFor(5000.0D, false) == BossStage.AWAKENING, "5000 must be Awakening");
        check(BossStagePolicy.stageFor(4000.0D, false) == BossStage.HUNTER, "4000 must be Hunter");
        check(BossStagePolicy.stageFor(3000.0D, false) == BossStage.DISTORTION, "3000 must be Distortion");
        check(BossStagePolicy.stageFor(2000.0D, false) == BossStage.ABSORPTION, "2000 must be Absorption");
        check(BossStagePolicy.stageFor(1000.0D, false) == BossStage.CATASTROPHE, "1000 must be Catastrophe");
        check(BossStage.AWAKENING.bossBarTitle().equals("Страж Разлома — Пробуждение"), "title must be Russian");
        check(BossStage.values().length == 5, "there must be five named stages");
    }

    private static void testLargeHitReportsEveryCrossedStage() {
        BossStagePolicy.StageTransition transition = BossStagePolicy.transition(
                BossStage.AWAKENING, 450.0D, false);
        check(transition.current() == BossStage.CATASTROPHE, "large hit must land in Catastrophe");
        check(transition.entered().equals(List.of(
                BossStage.HUNTER, BossStage.DISTORTION, BossStage.ABSORPTION, BossStage.CATASTROPHE)),
                "large hit must report all crossed stages in order");
    }

    private static void testJudgmentIsOneShotAtFiveHundred() {
        check(BossStagePolicy.transition(BossStage.CATASTROPHE, 500.0D, false).triggerJudgment(),
                "500 HP must trigger Judgment");
        check(!BossStagePolicy.transition(BossStage.CATASTROPHE, 100.0D, true).triggerJudgment(),
                "a persisted Judgment marker must suppress a second trigger");
    }

    private static void testStageNeverRegressesAfterTransientHealthRecovery() {
        BossStagePolicy.StageTransition recovery = BossStagePolicy.transition(
                BossStage.CATASTROPHE, 800.0D, false);
        check(recovery.current() == BossStage.CATASTROPHE,
                "a transient health recovery must not move the fight back to Absorption");
        check(recovery.entered().isEmpty(),
                "a blocked stage regression must not report a fake crossed stage");

        BossStagePolicy.StageTransition judgment = BossStagePolicy.transition(
                BossStage.CATASTROPHE, 500.0D, false);
        check(judgment.current() == BossStage.CATASTROPHE,
                "Judgment must remain in the terminal named stage");
        check(judgment.triggerJudgment(),
                "blocking a stage regression must not suppress Judgment");
    }

    private static void testStageSpellPoolsAreProgressiveAndNamed() {
        check(BossStagePolicy.spellPool(BossStage.AWAKENING).size() == 3, "Awakening must have the base arrow pool");
        check(BossStagePolicy.spellPool(BossStage.CATASTROPHE).contains(EndRiftAiPolicy.BossSpell.ARENA_INFERNO),
                "Catastrophe must unlock Arena Inferno");
        for (BossStage stage : BossStage.values()) {
            check(!BossStagePolicy.spellPool(stage).isEmpty(), "every stage must have a spell pool");
            for (EndRiftAiPolicy.BossSpell spell : BossStagePolicy.spellPool(stage)) {
                check(!spell.displayName().equals(spell.id()), "spell display name must not be an internal id");
            }
        }
    }

    private static void testThresholdSummonsAreReachableAndAbsorptionHasPressureTools() {
        check(BossStagePolicy.spellPool(BossStage.HUNTER)
                        .contains(EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS),
                "the 70 percent summon window must be reachable in Hunter");
        check(BossStagePolicy.spellPool(BossStage.ABSORPTION)
                        .contains(EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS),
                "the 35 percent summon window must be reachable in Absorption");
        check(BossStagePolicy.spellPool(BossStage.ABSORPTION)
                        .contains(EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE),
                "Absorption must keep a moving projectile pressure tool");
        check(BossStagePolicy.spellPool(BossStage.ABSORPTION)
                        .contains(EndRiftAiPolicy.BossSpell.VOID_MARK),
                "Absorption must keep an area denial pressure tool");
    }

    private static void testBossMovementProfileEscalatesByStage() {
        double awakening = BossStagePolicy.movementSpeed(BossStage.AWAKENING);
        double hunter = BossStagePolicy.movementSpeed(BossStage.HUNTER);
        double distortion = BossStagePolicy.movementSpeed(BossStage.DISTORTION);
        double absorption = BossStagePolicy.movementSpeed(BossStage.ABSORPTION);
        double catastrophe = BossStagePolicy.movementSpeed(BossStage.CATASTROPHE);
        check(awakening < hunter && hunter < distortion,
                "the opening stages must teach the fight before accelerating");
        check(distortion > absorption,
                "Absorption must create a readable channel window");
        check(absorption < catastrophe,
                "Catastrophe must be the fastest pressure stage");
        check(awakening >= 0.8D && catastrophe <= 1.4D,
                "movement speeds must stay inside the bounded arena budget");
    }

    private static void testPostAbsorptionProfileAddsBoundedEnrage() {
        BossStagePolicy.CombatProfile channel = BossStagePolicy.combatProfile(
                BossStage.ABSORPTION, false);
        BossStagePolicy.CombatProfile recovered = BossStagePolicy.combatProfile(
                BossStage.ABSORPTION, true);
        BossStagePolicy.CombatProfile catastrophe = BossStagePolicy.combatProfile(
                BossStage.CATASTROPHE, true);

        check(recovered.movementSpeed() >= channel.movementSpeed() * 1.15D,
                "absorption completion must grant at least a bounded 15 percent movement buff");
        check(recovered.movementSpeed() <= channel.movementSpeed() * 1.20D + 0.001D,
                "absorption movement buff must stay below the 20 percent design cap");
        check(recovered.spellCooldownMultiplier() <= 0.85D,
                "absorption completion must shorten spell cooldowns");
        check(recovered.nextMeleeAttackBonus() > 0.0D,
                "absorption completion must arm one empowered next melee attack");
        check(catastrophe.movementSpeed() > recovered.movementSpeed(),
                "catastrophe must remain faster than the recovered absorption stage");
        check(catastrophe.summonCap() < channel.summonCap(),
                "catastrophe must reduce summon cap to protect the server budget");
        check(catastrophe.teleportCooldownMultiplier() < 1.0D,
                "catastrophe must use a shorter but bounded teleport cooldown");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
