import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.HazardPlanner;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

public final class WaveMechanicsPolicyTest {
    public static void main(String[] args) {
        check(WaveMechanicsPolicy.portalCount(2) == 3, "two players must get three portals");
        check(WaveMechanicsPolicy.portalCount(3) == 4, "three players must get four portals");
        check(WaveMechanicsPolicy.portalCount(5) == 4, "five players must stay at four portals");
        check(WaveMechanicsPolicy.portalCount(6) == 5, "six players must get five portals");
        check(WaveMechanicsPolicy.portalCount(10) == 5, "ten players must stay at five portals");
        check(WaveMechanicsPolicy.portalCount(11) == 6, "more than ten players must cap at six portals");
        check(WaveMechanicsPolicy.portalCount(0) == 3, "invalid local roster must fail to the minimum portal plan");

        check(WaveMechanicsPolicy.towerMobCap(2) == 33, "two-player tower cap");
        check(WaveMechanicsPolicy.towerMobCap(5) == 40, "five-player tower cap");
        check(WaveMechanicsPolicy.towerMobCap(20) == 56, "large tower cap");
        WaveMechanicsPolicy.WaveCounts capped = WaveMechanicsPolicy.capTowerCounts(
                new WaveMechanicsPolicy.WaveCounts(24, 16, 12, 6, 4), 20);
        check(capped.total() == 56, "scaled Wave IV composition must obey the 56-mob cap");
        check(capped.endermen() >= 0 && capped.spiders() >= 0
                        && capped.skeletons() >= 0 && capped.eliteEndermen() >= 0
                        && capped.eliteSkeletons() >= 0,
                "tower cap must never create negative mob counts");
        WaveMechanicsPolicy.WaveCounts eliteSkeletonHeavy = WaveMechanicsPolicy.capTowerCounts(
                new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 40), 2);
        check(eliteSkeletonHeavy.total() == 33,
                "elite skeletons must be included in the tower hard cap");
        check(List.of(14, 12, 10).equals(WaveMechanicsPolicy.towerGroupCadenceSeconds()),
                "tower groups must use the bounded cadence");
        List<WaveMechanicsPolicy.WaveCounts> towerGroups = WaveMechanicsPolicy.towerSpawnGroups(
                new WaveMechanicsPolicy.WaveCounts(6, 5, 2, 2, 1), 2);
        check(towerGroups.size() == 4, "two-player tower must arrive in four groups");
        check(towerGroups.get(0).total() <= 4, "the first tower group must be bounded");
        check(towerGroups.stream().mapToInt(WaveMechanicsPolicy.WaveCounts::total).sum() == 16,
                "tower groups must preserve the capped composition total");
        check(towerGroups.stream().allMatch(group -> group.total() > 0),
                "tower schedule must not contain empty groups");
        check(towerGroups.get(0).endermen() > 0 && towerGroups.get(0).spiders() > 0
                        && towerGroups.get(0).skeletons() > 0 && towerGroups.get(0).eliteEndermen() > 0,
                "the opening tower group must demonstrate the four core roles");
        check(towerGroups.stream().anyMatch(group -> group.eliteSkeletons() > 0),
                "the tower schedule must demonstrate the elite skeleton role");
        check(towerGroups.stream().allMatch(group -> group.total() <= 4),
                "each tower arrival group must remain at or below four mobs");
        check(WaveMechanicsPolicy.TowerRole.RAIDER.minDamage() == 12.0D
                        && WaveMechanicsPolicy.TowerRole.RAIDER.maxDamage() == 18.0D,
                "raider damage range");
        check(WaveMechanicsPolicy.TowerRole.BREAKER.minDamage() == 28.0D
                        && WaveMechanicsPolicy.TowerRole.BREAKER.maxDamage() == 38.0D,
                "breaker damage range");
        check(WaveMechanicsPolicy.TowerRole.ARTILLERY.minDamage() == 15.0D
                        && WaveMechanicsPolicy.TowerRole.ARTILLERY.maxDamage() == 22.0D,
                "artillery damage range");
        check(WaveMechanicsPolicy.TowerRole.RAIDER.coreAttackRange() == 3.75D,
                "raider must be able to reach the Core from its safe ring");
        check(WaveMechanicsPolicy.TowerRole.BREAKER.coreAttackRange() == 4.25D,
                "breaker must be able to reach the Core from its safe ring");
        check(WaveMechanicsPolicy.TowerRole.BREAKER.coreAttackRange()
                        > WaveMechanicsPolicy.TowerRole.RAIDER.coreAttackRange(),
                "breaker must occupy a distinct outer stance");
        check(WaveMechanicsPolicy.TowerRole.ARTILLERY.coreAttackRange() == 12.0D,
                "artillery must keep its bounded ranged stance");
        double damageA = WaveMechanicsPolicy.roleDamage(WaveMechanicsPolicy.TowerRole.BREAKER, "mob-a", 1);
        double damageB = WaveMechanicsPolicy.roleDamage(WaveMechanicsPolicy.TowerRole.BREAKER, "mob-a", 1);
        check(damageA == damageB && damageA >= 28.0D && damageA <= 38.0D,
                "tower role damage must be deterministic and bounded");

        check(WaveMechanicsPolicy.floorMutationCap(1_000) == 160, "storm floor mutation cap");
        check(WaveMechanicsPolicy.floorMutationCap(100) == 100, "small storm floor stays unchanged");
        check(WaveMechanicsPolicy.webCap() == 12, "storm web cap");
        Set<HazardPlanner.Point> hazards = new HashSet<>();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            hazards.add(new HazardPlanner.Point(x, z));
        }
        Set<HazardPlanner.Point> webs = WaveMechanicsPolicy.selectWebCells(
                hazards, Set.of(new HazardPlanner.Point(0, 0)), Set.of(new HazardPlanner.Point(1, 1)), 7L);
        check(webs.size() <= WaveMechanicsPolicy.webCap(), "web selection must be capped");
        check(!webs.contains(new HazardPlanner.Point(0, 0)) && !webs.contains(new HazardPlanner.Point(1, 1)),
                "webs must not cover protected or occupied points");
        System.out.println("WaveMechanicsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
