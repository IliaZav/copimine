import me.copimine.endevent.domain.RitualSphereProjectileProvenancePolicy;

import java.util.UUID;

public final class RitualSphereProjectileProvenancePolicyTest {
    public static void main(String[] args) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RitualSphereProjectileProvenancePolicy.Vec3 origin =
                new RitualSphereProjectileProvenancePolicy.Vec3(4.0D, 12.0D, -3.0D);

        check(RitualSphereProjectileProvenancePolicy.accepts(
                        true, owner, owner, target, target, origin, 200L, 199L),
                "complete current sphere provenance must be accepted");
        check(!RitualSphereProjectileProvenancePolicy.accepts(
                        false, owner, owner, target, target, origin, 200L, 199L),
                "missing ritual marker must fail closed");
        check(!RitualSphereProjectileProvenancePolicy.accepts(
                        true, owner, target, target, target, origin, 200L, 199L),
                "a projectile owner must match its logical shooter");
        check(!RitualSphereProjectileProvenancePolicy.accepts(
                        true, owner, owner, target, owner, origin, 200L, 199L),
                "a sphere projectile must not damage a different target than its tag");
        check(!RitualSphereProjectileProvenancePolicy.accepts(
                        true, owner, owner, target, target,
                        new RitualSphereProjectileProvenancePolicy.Vec3(Double.NaN, 12.0D, -3.0D),
                        200L, 199L),
                "non-finite origin metadata must fail closed");
        check(!RitualSphereProjectileProvenancePolicy.accepts(
                        true, owner, owner, target, target, origin, 200L, 200L),
                "expired sphere provenance must fail closed at the expiry tick");

        System.out.println("RitualSphereProjectileProvenancePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
