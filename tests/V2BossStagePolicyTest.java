import me.copimine.endevent.domain.EndRiftAiPolicy;
import me.copimine.endevent.domain.V2BossStage;
import me.copimine.endevent.domain.V2BossStagePolicy;

public final class V2BossStagePolicyTest {
    public static void main(String[] args) {
        check(V2BossStage.forHealth(10000, 10000) == V2BossStage.AWAKENING, "full health is Awakening");
        check(V2BossStage.forHealth(8000, 10000) == V2BossStage.HUNT, "80 percent enters Hunt");
        check(V2BossStage.forHealth(6000, 10000) == V2BossStage.RIFT, "60 percent enters Rift");
        check(V2BossStage.forHealth(4500, 10000) == V2BossStage.OVERLOAD, "45 percent enters Overload");
        check(V2BossStage.forHealth(3000, 10000) == V2BossStage.RAGE, "30 percent enters Rage");
        check(V2BossStage.forHealth(2000, 10000) == V2BossStage.LAST_SEAL, "20 percent enters Last Seal");
        var jump = V2BossStagePolicy.transition(V2BossStage.AWAKENING, 1900, 10000);
        check(jump.current() == V2BossStage.LAST_SEAL, "large hit lands in Last Seal");
        check(jump.entered().size() == 5, "all skipped stages are reported");
        var recovery = V2BossStagePolicy.transition(V2BossStage.LAST_SEAL, 5000, 10000);
        check(recovery.current() == V2BossStage.LAST_SEAL, "stage never regresses");
        check(V2BossStagePolicy.spellPool(V2BossStage.RIFT)
                .contains(EndRiftAiPolicy.BossSpell.RIFT_OBELISKS), "obelisks only unlock in Rift");
        check(!V2BossStagePolicy.spellPool(V2BossStage.OVERLOAD)
                .contains(EndRiftAiPolicy.BossSpell.RIFT_OBELISKS), "obelisks do not repeat after Rift");
        System.out.println("V2BossStagePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
