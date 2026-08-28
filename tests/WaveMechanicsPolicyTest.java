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

        check(WaveMechanicsPolicy.towerMobCap(2) == 16, "two-player tower cap");
        check(WaveMechanicsPolicy.towerMobCap(5) == 22, "five-player tower cap");
        check(WaveMechanicsPolicy.towerMobCap(20) == 28, "large tower cap");
        WaveMechanicsPolicy.WaveCounts capped = WaveMechanicsPolicy.capTowerCounts(
                new WaveMechanicsPolicy.WaveCounts(16, 12, 8, 4), 20);
        check(capped.total() == 28, "scaled Wave IV composition must obey the 28-mob cap");
        check(capped.endermen() >= 0 && capped.spiders() >= 0
                        && capped.shulkers() >= 0 && capped.eliteEndermen() >= 0,
                "tower cap must never create negative mob counts");
        check(List.of(14, 12, 10).equals(WaveMechanicsPolicy.towerGroupCadenceSeconds()),
                "tower groups must use the bounded cadence");
        check(WaveMechanicsPolicy.TowerRole.RAIDER.minDamage() == 12.0D
                        && WaveMechanicsPolicy.TowerRole.RAIDER.maxDamage() == 18.0D,
                "raider damage range");
        check(WaveMechanicsPolicy.TowerRole.BREAKER.minDamage() == 28.0D
                        && WaveMechanicsPolicy.TowerRole.BREAKER.maxDamage() == 38.0D,
                "breaker damage range");
        check(WaveMechanicsPolicy.TowerRole.ARTILLERY.minDamage() == 15.0D
                        && WaveMechanicsPolicy.TowerRole.ARTILLERY.maxDamage() == 22.0D,
                "artillery damage range");
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
