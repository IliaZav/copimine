import me.copimine.endevent.domain.RiftRamPolicy;

/**
 * Wave 6 pillar roster: four ordinary pillars, then one three-impact finale.
 *
 * <p>SUPERSEDES W6-03, which specified "two or three accepted column impacts" on
 * a single pillar. The 2026-11-11 master rebuild asks for four ordinary pillars
 * followed by one very large final pillar that survives three separate Ram
 * collisions, so the encounter totals seven accepted impacts across five
 * pillars. The roster is derived from named parts, and this test holds that the
 * derivation, the pillar numbering, the armour ladder and completion all agree.
 */
public final class RiftRamPillarRosterPolicyTest {
    private static int checks;

    private RiftRamPillarRosterPolicyTest() {
    }

    public static void main(String[] args) {
        rosterShape();
        pillarNumbering();
        finalPillarStages();
        armourLadder();
        completion();
        eliteSkeletonPlacement();
        System.out.println("RiftRamPillarRosterPolicyTest: PASS (" + checks + " checks)");
    }

    private static void rosterShape() {
        check("four ordinary pillars", RiftRamPolicy.MINOR_PILLARS == 4);
        check("the final pillar takes three collisions", RiftRamPolicy.FINAL_PILLAR_IMPACTS == 3);
        check("five pillars in total", RiftRamPolicy.PILLAR_COUNT == 5);
        check("seven accepted impacts in total", RiftRamPolicy.REQUIRED_IMPACTS == 7);
        check("the roster adds up",
                RiftRamPolicy.MINOR_PILLARS + RiftRamPolicy.FINAL_PILLAR_IMPACTS
                        == RiftRamPolicy.REQUIRED_IMPACTS);
        check("one armour plate per accepted impact",
                RiftRamPolicy.ARMOUR_PLATES == RiftRamPolicy.REQUIRED_IMPACTS);
    }

    private static void pillarNumbering() {
        // The first four impacts each retire one ordinary pillar.
        for (int impacts = 0; impacts < RiftRamPolicy.MINOR_PILLARS; impacts++) {
            check("ordinary pillar ordinal follows the impact count",
                    RiftRamPolicy.pillarOrdinal(impacts) == impacts + 1);
            check("the first four pillars are not the finale",
                    !RiftRamPolicy.isFinalPillar(RiftRamPolicy.pillarOrdinal(impacts)));
            check("an ordinary pillar takes one collision",
                    RiftRamPolicy.impactsRequiredForPillar(impacts + 1) == 1);
            // After n impacts the next ordinary pillar already stands and still
            // needs its own single collision, so the live pillar owes exactly one.
            check("a fresh ordinary pillar needs one collision",
                    RiftRamPolicy.impactsRemainingOnPillar(impacts) == 1);
        }
        // Impacts four, five and six all address the same final pillar.
        for (int impacts = RiftRamPolicy.MINOR_PILLARS;
                impacts < RiftRamPolicy.REQUIRED_IMPACTS; impacts++) {
            check("the final impact range addresses pillar five",
                    RiftRamPolicy.pillarOrdinal(impacts) == RiftRamPolicy.PILLAR_COUNT);
            check("pillar five is the finale",
                    RiftRamPolicy.isFinalPillar(RiftRamPolicy.pillarOrdinal(impacts)));
        }
        check("the final pillar takes three collisions",
                RiftRamPolicy.impactsRequiredForPillar(RiftRamPolicy.PILLAR_COUNT) == 3);
        // The final pillar is spent by the seventh impact, so nothing follows it.
        check("the final pillar needs no further collision once spent",
                RiftRamPolicy.impactsRemainingOnPillar(RiftRamPolicy.REQUIRED_IMPACTS) == 0);
        check("nothing rises after the seventh impact",
                !RiftRamPolicy.mayRaisePillar(RiftRamPolicy.REQUIRED_IMPACTS));
        check("the finale may still stand at six impacts",
                RiftRamPolicy.mayRaisePillar(RiftRamPolicy.REQUIRED_IMPACTS - 1));
        check("pillar one is active from the start", RiftRamPolicy.activePillarNumber(0) == 1);
        check("pillar five is active for the finale",
                RiftRamPolicy.activePillarNumber(RiftRamPolicy.MINOR_PILLARS)
                        == RiftRamPolicy.PILLAR_COUNT);
        check("no pillar remains after the seventh",
                RiftRamPolicy.activePillarNumber(RiftRamPolicy.REQUIRED_IMPACTS) == 0);
        check("a negative count never raises a pillar", !RiftRamPolicy.mayRaisePillar(-1));
    }

    private static void finalPillarStages() {
        check("untouched before the first collision",
                RiftRamPolicy.finalPillarStage(RiftRamPolicy.MINOR_PILLARS) == 0);
        check("the shell cracks on the first",
                RiftRamPolicy.finalPillarStage(RiftRamPolicy.MINOR_PILLARS + 1) == 1);
        check("a section breaks on the second",
                RiftRamPolicy.finalPillarStage(RiftRamPolicy.MINOR_PILLARS + 2) == 2);
        check("it collapses on the third",
                RiftRamPolicy.finalPillarStage(RiftRamPolicy.REQUIRED_IMPACTS) == 3);
        check("a fresh final pillar still needs three",
                RiftRamPolicy.impactsRemainingOnPillar(RiftRamPolicy.MINOR_PILLARS) == 3);
        check("a cracked one still needs two",
                RiftRamPolicy.impactsRemainingOnPillar(RiftRamPolicy.MINOR_PILLARS + 1) == 2);
        check("an exposed core still needs one",
                RiftRamPolicy.impactsRemainingOnPillar(RiftRamPolicy.MINOR_PILLARS + 2) == 1);
        check("a collapsed pillar needs none",
                RiftRamPolicy.impactsRemainingOnPillar(RiftRamPolicy.REQUIRED_IMPACTS) == 0);
    }

    private static void armourLadder() {
        for (int i = 0; i <= RiftRamPolicy.REQUIRED_IMPACTS; i++) {
            check("one plate leaves per accepted impact",
                    RiftRamPolicy.remainingPlates(i) == RiftRamPolicy.REQUIRED_IMPACTS - i);
            double expected = (i + 1.0D) / (RiftRamPolicy.REQUIRED_IMPACTS + 1.0D);
            check("the damage ladder is a clean ramp",
                    Math.abs(RiftRamPolicy.damageMultiplier(i) - expected) < 1.0E-9D);
            check("hits always register",
                    RiftRamPolicy.damageMultiplier(i) > 0.0D);
            check("armour holds until the seventh",
                    RiftRamPolicy.armourBroken(i) == (i == RiftRamPolicy.REQUIRED_IMPACTS));
        }
        check("full damage once every plate is gone",
                RiftRamPolicy.damageMultiplier(RiftRamPolicy.REQUIRED_IMPACTS) == 1.0D);
        check("an out-of-range count clamps high",
                RiftRamPolicy.damageMultiplier(99) == 1.0D);
        check("plate counts never go negative",
                RiftRamPolicy.remainingPlates(-4) == RiftRamPolicy.ARMOUR_PLATES);
    }

    private static void completion() {
        for (int i = 0; i < RiftRamPolicy.REQUIRED_IMPACTS; i++) {
            check("only the seventh impact completes on death",
                    !RiftRamPolicy.isComplete(i, false));
        }
        check("seven impacts and a dead Ram complete the encounter",
                RiftRamPolicy.isComplete(RiftRamPolicy.REQUIRED_IMPACTS, false));
        check("a living Ram is never complete",
                !RiftRamPolicy.isComplete(RiftRamPolicy.REQUIRED_IMPACTS, true));
    }

    private static void eliteSkeletonPlacement() {
        check("the marksman stands on the final pillar",
                RiftRamPolicy.ELITE_SKELETON_PILLAR == RiftRamPolicy.PILLAR_COUNT);
        check("no skeleton on an ordinary pillar",
                !RiftRamPolicy.eliteSkeletonAliveOn(0, true));
        check("the skeleton appears with the final pillar",
                RiftRamPolicy.eliteSkeletonAliveOn(RiftRamPolicy.MINOR_PILLARS, true));
        check("a dead skeleton stays dead",
                !RiftRamPolicy.eliteSkeletonAliveOn(RiftRamPolicy.MINOR_PILLARS, false));
        check("a survivor falls when the pillar collapses",
                RiftRamPolicy.eliteSkeletonFallsOn(RiftRamPolicy.REQUIRED_IMPACTS));
        check("the skeleton does not fall before the collapse",
                !RiftRamPolicy.eliteSkeletonFallsOn(RiftRamPolicy.REQUIRED_IMPACTS - 1));
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + name);
        checks++;
    }
}
