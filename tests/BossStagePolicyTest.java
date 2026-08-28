import java.util.List;
import me.copimine.endevent.domain.BossStage;
import me.copimine.endevent.domain.BossStagePolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;

public final class BossStagePolicyTest {
    public static void main(String[] args) {
        testExactThresholdsAndTitles();
        testLargeHitReportsEveryCrossedStage();
        testJudgmentIsOneShotAtTwoHundredFifty();
        testStageSpellPoolsAreProgressiveAndNamed();
        System.out.println("BossStagePolicyTest OK");
    }

    private static void testExactThresholdsAndTitles() {
        check(BossStagePolicy.stageFor(2500.0D, false) == BossStage.AWAKENING, "2500 must be Awakening");
        check(BossStagePolicy.stageFor(2000.0D, false) == BossStage.HUNTER, "2000 must be Hunter");
        check(BossStagePolicy.stageFor(1500.0D, false) == BossStage.DISTORTION, "1500 must be Distortion");
        check(BossStagePolicy.stageFor(1000.0D, false) == BossStage.ABSORPTION, "1000 must be Absorption");
        check(BossStagePolicy.stageFor(500.0D, false) == BossStage.CATASTROPHE, "500 must be Catastrophe");
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

    private static void testJudgmentIsOneShotAtTwoHundredFifty() {
        check(BossStagePolicy.transition(BossStage.CATASTROPHE, 250.0D, false).triggerJudgment(),
                "250 HP must trigger Judgment");
        check(!BossStagePolicy.transition(BossStage.CATASTROPHE, 100.0D, true).triggerJudgment(),
                "a persisted Judgment marker must suppress a second trigger");
    }

    private static void testStageSpellPoolsAreProgressiveAndNamed() {
        check(BossStagePolicy.spellPool(BossStage.AWAKENING).size() == 2, "Awakening must have the base pool");
        check(BossStagePolicy.spellPool(BossStage.CATASTROPHE).contains(EndRiftAiPolicy.BossSpell.ARENA_INFERNO),
                "Catastrophe must unlock Arena Inferno");
        for (BossStage stage : BossStage.values()) {
            check(!BossStagePolicy.spellPool(stage).isEmpty(), "every stage must have a spell pool");
            for (EndRiftAiPolicy.BossSpell spell : BossStagePolicy.spellPool(stage)) {
                check(!spell.displayName().equals(spell.id()), "spell display name must not be an internal id");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
