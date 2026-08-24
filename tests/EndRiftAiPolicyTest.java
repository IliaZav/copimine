import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.EndRiftAiPolicy;

public final class EndRiftAiPolicyTest {
    public static void main(String[] args) {
        testBossPhaseBoundaries();
        testFairTargetRotationAvoidsRecentTargets();
        testBossSpellRotationAvoidsImmediateRepeat();
        testEveryBossSpellHasRussianDisplayName();
        testEveryEliteHasExactlyOneDeterministicSpell();
        System.out.println("EndRiftAiPolicyTest OK");
    }

    private static void testBossPhaseBoundaries() {
        check(EndRiftAiPolicy.bossPhase(1200.0D, 1200.0D, 600.0D, 120.0D, false, false)
                        == EndRiftAiPolicy.BossPhase.NORMAL,
                "full-health boss must start in normal phase");
        check(EndRiftAiPolicy.bossPhase(600.0D, 1200.0D, 600.0D, 120.0D, false, false)
                        == EndRiftAiPolicy.BossPhase.HALF,
                "boss must enter half phase at exactly 50 percent");
        check(EndRiftAiPolicy.bossPhase(120.0D, 1200.0D, 600.0D, 120.0D, true, false)
                        == EndRiftAiPolicy.BossPhase.FINAL,
                "boss must enter final phase at exactly 10 percent");
    }

    private static void testFairTargetRotationAvoidsRecentTargets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        EndRiftAiPolicy.TargetChoice choice = EndRiftAiPolicy.chooseFairTarget(
                List.of(first, second, third), first, List.of(second), 0);
        check(choice.target().equals(third), "boss must prefer a target outside current and recent memory");
        check(choice.nextCursor() == 1, "target cursor must advance after a successful choice");
    }

    private static void testBossSpellRotationAvoidsImmediateRepeat() {
        EndRiftAiPolicy.BossSpell first = EndRiftAiPolicy.chooseBossSpell(
                List.of(EndRiftAiPolicy.BossSpell.VOID_BLAST, EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE), null, 0);
        EndRiftAiPolicy.BossSpell second = EndRiftAiPolicy.chooseBossSpell(
                List.of(EndRiftAiPolicy.BossSpell.VOID_BLAST, EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE), first, 0);
        check(first == EndRiftAiPolicy.BossSpell.VOID_BLAST, "spell rotation must be deterministic for the first cast");
        check(second == EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE, "boss must not immediately repeat a spell");
    }

    private static void testEveryBossSpellHasRussianDisplayName() {
        String[] expected = {
            "Взрыв Бездны",
            "Снаряд Разлома",
            "Клеймо Пустоты",
            "Призыв слуг Разлома",
            "Искажение воли"
        };
        EndRiftAiPolicy.BossSpell[] spells = EndRiftAiPolicy.BossSpell.values();
        check(spells.length == expected.length, "boss spell list changed without updating display names");
        for (int index = 0; index < spells.length; index++) {
            check(spells[index].displayName().equals(expected[index]),
                    "boss spell must expose the agreed Russian display name");
            check(!spells[index].displayName().equals(spells[index].id()),
                    "player-facing spell name must not be the internal spell ID");
        }
    }

    private static void testEveryEliteHasExactlyOneDeterministicSpell() {
        for (int index = 0; index < 12; index++) {
            EndRiftAiPolicy.MiniBossSpell spell = EndRiftAiPolicy.miniBossSpell(3, index);
            check(spell != null && !spell.id().isBlank(), "every elite must have one named spell");
            check(EndRiftAiPolicy.miniBossSpell(3, index) == spell,
                    "the same elite slot must keep the same spell after a rebuild");
        }
        check(EndRiftAiPolicy.miniBossSpell(3, 0) != EndRiftAiPolicy.miniBossSpell(3, 1),
                "adjacent elite mini-bosses must not all share one spell");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
