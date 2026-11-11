import me.copimine.endevent.domain.BossSummonAdmissionPolicy;
import me.copimine.endevent.domain.BossPhase;

/**
 * Master contract 14.6: "There must be NO skeleton towers or skeleton archer
 * turrets in any boss phase. The only approved skeleton-on-pillar mechanic is in
 * Wave 6."
 *
 * <p>This is written as an admission gate rather than a grep, because the defect
 * it prevents is silent: a summon helper reused for the boss would quietly put a
 * skeleton on a pillar back into a phase the player is fighting.</p>
 */
public final class BossSummonAdmissionPolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        for (BossPhase phase : BossPhase.values()) {
            check("no skeleton towers in " + phase,
                    !BossSummonAdmissionPolicy.allowsSkeletonTower(phase));
            check("no skeleton archer turret in " + phase,
                    !BossSummonAdmissionPolicy.allowsSkeletonArcherTurret(phase));
            check("no pillar-mounted skeleton in " + phase,
                    !BossSummonAdmissionPolicy.allowsPillarSkeleton(phase));
            check("no skeleton summon at all in " + phase,
                    !BossSummonAdmissionPolicy.allowsSkeletonSummon(phase, "SKELETON"));
        }
        check("a null phase admits nothing",
                !BossSummonAdmissionPolicy.allowsSkeletonTower(null)
                        && !BossSummonAdmissionPolicy.allowsPillarSkeleton(null)
                        && !BossSummonAdmissionPolicy.allowsSkeletonSummon(null, "SKELETON"));
        check("a null entity admits nothing",
                !BossSummonAdmissionPolicy.allowsSkeletonSummon(BossPhase.RAGE, null));

        // Ordinary pressure summons stay legal, so the gate is not a blanket ban.
        check("a spider servant is still allowed",
                BossSummonAdmissionPolicy.allowsSummon(BossPhase.RIFT, "SPIDER"));
        check("an enderman servant is still allowed",
                BossSummonAdmissionPolicy.allowsSummon(BossPhase.RAGE, "ENDERMAN"));
        check("a decoy is still allowed",
                BossSummonAdmissionPolicy.allowsSummon(BossPhase.RAGE, "BOSS_DECOY"));
        check("skeletons are refused by the general admission",
                !BossSummonAdmissionPolicy.allowsSummon(BossPhase.OVERLOAD, "SKELETON"));
        check("case and padding do not bypass the refusal",
                !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RAGE, "  skeleton  ")
                        && !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RAGE, "Skeleton"));
        // A forbidden ARCHETYPE id must be recognised through padding and case
        // too; otherwise " skeleton_tower " walks straight through the gate.
        check("a forbidden archetype id is normalised before the check",
                !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RIFT, " skeleton_tower ")
                        && !BossSummonAdmissionPolicy.allowsSummon(
                        BossPhase.RIFT, "Skeleton_Archer_Turret")
                        && !BossSummonAdmissionPolicy.allowsSummon(
                        BossPhase.OVERLOAD, "PILLAR_SKELETON"));
        check("stray and wither skeleton variants are refused too",
                !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RIFT, "STRAY")
                        && !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RIFT, "WITHER_SKELETON"));
        // A silhouette is irrelevant: ANY entity whose type says "skeleton"
        // (a skeleton jockey, a future variant) is refused, because the hazard
        // is "a skeleton on a pillar", not one exact entity id.
        check("an unlisted skeleton variant is still refused",
                !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RAGE, "SKELETON_JOCKEY")
                        && !BossSummonAdmissionPolicy.allowsSkeletonSummon(
                        BossPhase.RAGE, "PARched_SKELETON"));
        // Padding and mixed case are normalised on the entity side too.
        check("padding does not bypass the skeleton refusal",
                !BossSummonAdmissionPolicy.allowsSkeletonSummon(BossPhase.OVERLOAD, "  Skeleton  ")
                        && !BossSummonAdmissionPolicy.allowsSkeletonSummon(
                        BossPhase.OVERLOAD, "withER_skeleton"));
        // A null phase must not become a wildcard that admits everything.
        check("a null phase is refused by the general admission",
                !BossSummonAdmissionPolicy.allowsSummon(null, "SPIDER")
                        && !BossSummonAdmissionPolicy.allowsSummon(null, "SKELETON"));
        check("a blank archetype is refused",
                !BossSummonAdmissionPolicy.allowsSummon(BossPhase.RAGE, "   "));

        // Wave 6 owns the only approved skeleton-on-pillar mechanic.
        check("the Wave 6 pillar mechanic is explicitly out of scope",
                BossSummonAdmissionPolicy.WAVE_SIX_PILLAR_MECHANIC_OWNER == 6);
        check("the policy names the forbidden archetypes for the log",
                BossSummonAdmissionPolicy.forbiddenArchetypes().contains("SKELETON_TOWER"));

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