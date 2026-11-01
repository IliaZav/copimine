import me.copimine.endevent.domain.RitualGuardAggroPolicy;

public final class RitualGuardAggroPolicyTest {
    public static void main(String[] args) {
        // W6-013: guards freely engage legal outside participants. They wake on
        // their own radius, on a caster hit, on their own hit, or on ranged fire.
        check(RitualGuardAggroPolicy.shouldWake(5.9D, false, false, false),
                "a nearby participant wakes the guard");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, true, false, false),
                "a hit on the caster wakes every guard regardless of distance");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, false, true, false),
                "a hit on the guard itself wakes it regardless of distance");
        check(RitualGuardAggroPolicy.shouldWake(20.0D, false, false, true),
                "ranged fire wakes the guard regardless of distance");
        check(!RitualGuardAggroPolicy.shouldWake(6.1D, false, false, false),
                "a distant, unprovoked participant does not wake the guard");

        check(RitualGuardAggroPolicy.shouldWake(9.9D, false, false, false, true),
                "an already engaged guard holds its target out to the wider radius");
        check(!RitualGuardAggroPolicy.shouldWake(10.1D, false, false, false, true),
                "even an engaged guard does not hold beyond the wider radius");

        // The former six- and fourteen-block leashes are gone with the post
        // system: the type no longer exposes any leash predicate at all.
        check(!hasPublicMember("withinLeash"),
                "no leash predicate survives on the guard policy");
        check(!hasPublicMember("stance"),
                "no GUARD/ENGAGE/RETURN stance survives on the guard policy");
        check(!hasPublicMember("postOffset"),
                "no post assignment survives on the guard policy");
        check(!hasPublicMember("mayRequestReturnPath"),
                "no return-to-mage timer survives on the guard policy");
        check(!hasPublicMember("nextReturnPathAttempt"),
                "no return-path attempt budget survives on the guard policy");

        // Placement is a spawn ring only: every offset sits on the ring radius.
        for (int count = 1; count <= 5; count++) {
            for (int slot = 0; slot < count; slot++) {
                var offset = RitualGuardAggroPolicy.spawnRingOffset(slot, count, 0.0D);
                double radius = Math.sqrt(offset.x() * offset.x() + offset.z() * offset.z());
                check(Math.abs(radius - RitualGuardAggroPolicy.SPAWN_RING_RADIUS_BLOCKS) < 1e-9D,
                        "guard " + slot + " of " + count + " spawns on the ring radius");
            }
        }

        // A single guard still spawns in front of its mage.
        var single = RitualGuardAggroPolicy.spawnRingOffset(0, 1, 0.0D);
        check(Math.abs(single.x() - RitualGuardAggroPolicy.SPAWN_RING_RADIUS_BLOCKS) < 1e-9D
                        && Math.abs(single.z()) < 1e-9D,
                "a lone guard spawns in front of its mage");

        // Distinct slots of one ring are never at the same place.
        var slotZero = RitualGuardAggroPolicy.spawnRingOffset(0, 3, 0.0D);
        var slotOne = RitualGuardAggroPolicy.spawnRingOffset(1, 3, 0.0D);
        check(Math.hypot(slotZero.x() - slotOne.x(), slotZero.z() - slotOne.z()) > 1e-6D,
                "two guards of one mage never share a spawn point");

        // Reject impossible slot/count combinations instead of inventing a place.
        checkThrows(() -> RitualGuardAggroPolicy.spawnRingOffset(5, 5, 0.0D),
                "a slot outside the ring is rejected");
        checkThrows(() -> RitualGuardAggroPolicy.spawnRingOffset(0, 0, 0.0D),
                "an empty ring is rejected");
        checkThrows(() -> RitualGuardAggroPolicy.spawnRingOffset(0, 6, 0.0D),
                "a sixth guard slot is rejected: the contract caps guards at five");
        checkThrows(() -> RitualGuardAggroPolicy.spawnRingOffset(0, 1, Double.NaN),
                "a non-finite outward angle is rejected");

        System.out.println("RitualGuardAggroPolicyTest OK");
    }

    private static boolean hasPublicMember(String name) {
        for (var method : RitualGuardAggroPolicy.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) return true;
        }
        return false;
    }

    private static void checkThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}