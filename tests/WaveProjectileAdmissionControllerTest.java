import java.util.UUID;
import java.util.Set;
import me.copimine.endevent.domain.WaveProjectileAdmissionController;

public final class WaveProjectileAdmissionControllerTest {
    public static void main(String[] args) {
        WaveProjectileAdmissionController admission = new WaveProjectileAdmissionController();
        UUID rain = UUID.randomUUID();
        check(!admission.reserveGroup(UUID.randomUUID(), 2, 5),
                "generic groups cannot use the named five-arrow rain exception");
        UUID occupiedSlot = UUID.randomUUID();
        check(admission.reserveGroup(occupiedSlot, 2, 1),
                "an ordinary projectile reserves its configured slot first");
        check(admission.reserveNamedFiveArrowRain(rain, 2),
                "the named rain can coexist with an already reserved ordinary projectile");
        check(admission.activeProjectileCount() == 6,
                "both lanes contribute their physical children to diagnostics");
        check(admission.releaseUnspawnedReservations(occupiedSlot) == 1,
                "releasing the ordinary reservation leaves the named lane intact");
        check(!admission.reserveNamedFiveArrowRain(UUID.randomUUID(), 2),
                "the active named lane cannot admit a second rain");
        check(admission.activeProjectileCount() == 5,
                "the full physical child count is reserved atomically before spawning");

        UUID blockedVolley = UUID.randomUUID();
        check(!admission.reserveGroup(blockedVolley, 2, 5),
                "an ordinary group above its four-child lane cap is denied whole");
        check(admission.projectileIds(blockedVolley).isEmpty()
                        && admission.activeProjectileCount() == 5,
                "a rejected volley cannot leave a partial cast or reserve leaked children");

        UUID[] rainArrows = new UUID[5];
        for (int index = 0; index < rainArrows.length; index++) {
            rainArrows[index] = UUID.randomUUID();
            check(admission.trackProjectile(rain, rainArrows[index]),
                    "every admitted physical child is tracked under the rain group");
        }
        check(admission.activeGroupCount() == 1 && admission.activeProjectileCount() == 5,
                "the diagnostic unit counts five simultaneous physical children, not one group");

        check(admission.releaseProjectile(rainArrows[0]),
                "an impacted child releases its physical projectile slot");
        check(admission.activeProjectileCount() == 4,
                "one cleanup frees exactly one simultaneous entity slot");
        check(admission.reserveGroup(blockedVolley, 2, 3),
                "ordinary projectiles can reserve after a named rain is already active");
        check(admission.activeProjectileCount() == 7,
                "three ordinary and four remaining named children coexist");
        check(admission.releaseUnspawnedReservations(blockedVolley) == 3
                        && admission.activeProjectileCount() == 4,
                "ordinary rollback frees only its own lane while rain children remain tracked");
        for (int index = 1; index < rainArrows.length; index++) {
            check(admission.releaseProjectile(rainArrows[index]),
                    "additional expired children release their physical slots");
        }
        check(admission.activeProjectileCount() == 0,
                "the named exception is released after its last rain arrow expires");

        check(admission.reserveGroup(blockedVolley, 2, 3),
                "the ordinary three-arrow cast fits after the named rain is fully cleared");
        UUID[] volleyChildren = new UUID[3];
        for (int index = 0; index < volleyChildren.length; index++) {
            volleyChildren[index] = UUID.randomUUID();
            check(admission.trackProjectile(blockedVolley, volleyChildren[index]),
                    "the admitted volley tracks all three physical children");
        }
        check(admission.activeProjectileCount() == 3 && admission.activeGroupCount() == 1,
                "simultaneous diagnostics report the three physical children in the admitted group");
        check(!admission.reserveGroup(UUID.randomUUID(), 2, 2),
                "ordinary projectiles cannot extend the two-player profile beyond four children");

        UUID cloud = UUID.randomUUID();
        check(admission.transferProjectile(volleyChildren[0], cloud),
                "Dragon Breath impact transfers one physical lease child to its cloud");
        check(admission.activeProjectileCount() == 3,
                "the cloud replaces the fireball in the count without double-counting");
        check(admission.releaseProjectile(cloud), "cloud cleanup releases its physical slot");
        check(admission.activeProjectileCount() == 2,
                "cloud cleanup returns one projectile slot");
        for (int index = 1; index < volleyChildren.length; index++) {
            admission.releaseProjectile(volleyChildren[index]);
        }
        check(admission.releaseUnspawnedReservations(blockedVolley) == 0,
                "a fully materialized volley has no unused child reservations");

        UUID failedCast = UUID.randomUUID();
        check(admission.reserveGroup(failedCast, 2, 3),
                "released volley slots can be reserved for a later cast");
        UUID onlySpawned = UUID.randomUUID();
        check(admission.trackProjectile(failedCast, onlySpawned),
                "a partially materialized cast tracks its existing child for rollback");
        check(admission.releaseUnspawnedReservations(failedCast) == 2,
                "failed spawn cleanup frees every child slot that was reserved but not materialized");
        check(admission.activeProjectileCount() == 1,
                "rollback retains only the one real child until it is removed");
        check(admission.releaseProjectile(onlySpawned), "rollback cleanup removes the tracked child");
        check(admission.activeProjectileCount() == 0,
                "no reservation leaks remain after a failed partial spawn");

        admission.clear();
        check(admission.activeGroupCount() == 0 && admission.activeProjectileCount() == 0,
                "wave cleanup releases all groups and physical children");
        for (int index = 0; index < 20; index++) {
            UUID sequential = UUID.randomUUID();
            UUID child = UUID.randomUUID();
            check(admission.reserveGroup(sequential, 2, 1),
                    "capacity is simultaneous rather than cumulative over the wave");
            check(admission.trackProjectile(sequential, child), "sequential child is tracked");
            check(admission.releaseProjectile(child), "sequential child cleanup succeeds");
        }

        admission.clear();
        UUID ordinaryProjectileOverflow = UUID.randomUUID();
        check(admission.reserveGroup(ordinaryProjectileOverflow, 2, 4),
                "ordinary two-player projectiles may fill the configured four-child profile");
        check(!admission.reserveGroup(UUID.randomUUID(), 2, 1),
                "ordinary projectiles cannot exceed their independent four-child profile");
        UUID concurrentRain = UUID.randomUUID();
        check(admission.reserveNamedFiveArrowRain(concurrentRain, 2),
                "the named elite rain has a reserved lane even when common arrows occupy all ordinary slots");
        check(admission.activeProjectileCount() == 9
                        && admission.activeProjectileCount() <= admission.effectiveProjectileCap(2),
                "ordinary arrows plus one complete rain stay within the combined physical cap");
        check(!admission.reserveNamedFiveArrowRain(UUID.randomUUID(), 2),
                "only one named rain may occupy the reserved lane at a time");
        check(!admission.reserveGroup(UUID.randomUUID(), 2, 1),
                "ordinary projectiles cannot consume slots from the named rain lane");
        admission.releaseGroup(concurrentRain);
        admission.releaseGroup(ordinaryProjectileOverflow);
        check(admission.activeProjectileCount() == 0,
                "both isolated lanes release all slots during generation cleanup");

        admission.clear();
        UUID failedAfterFirstChild = UUID.randomUUID();
        UUID firstChild = UUID.randomUUID();
        check(admission.reserveNamedFiveArrowRain(failedAfterFirstChild, 2),
                "a new full five-arrow cast reserves before attempting its first child");
        check(admission.trackProjectile(failedAfterFirstChild, firstChild),
                "the cast records its first child before a later spawn/configuration failure");
        Set<UUID> removedChildren = admission.releaseGroup(failedAfterFirstChild);
        check(removedChildren.contains(firstChild),
                "atomic rollback returns every created child for entity cleanup");
        check(!admission.hasGroup(failedAfterFirstChild)
                        && admission.activeProjectileCount() == 0,
                "rollback unregisters the child and all unmaterialized reservation slots");
        check(!admission.releaseProjectile(firstChild),
                "a second cleanup attempt cannot release the same child twice");
        UUID retryAfterFailure = UUID.randomUUID();
        check(admission.reserveNamedFiveArrowRain(retryAfterFailure, 2),
                "the full physical cap is reusable immediately after failed-cast rollback");
        UUID survivingRainChild = UUID.randomUUID();
        check(admission.trackProjectile(retryAfterFailure, survivingRainChild),
                "a partial named cast can retain its one physical child");
        check(admission.releaseUnspawnedReservations(retryAfterFailure) == 4
                        && admission.activeProjectileCount() == 1,
                "named rollback releases unmaterialized children without dropping the live child");
        check(!admission.reserveNamedFiveArrowRain(UUID.randomUUID(), 2),
                "a surviving named child keeps the single named lane occupied");
        check(admission.releaseProjectile(survivingRainChild)
                        && admission.reserveNamedFiveArrowRain(UUID.randomUUID(), 2),
                "the last named child releases the lane for a new rain");
        check(admission.reserveGroup(UUID.randomUUID(), 2, 4),
                "ordinary capacity is reusable after partial rollback");
        admission.clear();
        check(admission.activeGroupCount() == 0 && admission.activeProjectileCount() == 0,
                "clear resets both populated lanes and all physical counters");
        check(admission.reserveGroup(UUID.randomUUID(), 2, 4)
                        && admission.reserveNamedFiveArrowRain(UUID.randomUUID(), 2),
                "both lanes can be filled again immediately after clear");
        admission.clear();
        System.out.println("WaveProjectileAdmissionControllerTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
