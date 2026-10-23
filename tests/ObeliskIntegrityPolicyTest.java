import me.copimine.endevent.domain.ObeliskIntegrityPolicy;
import me.copimine.endevent.domain.ObeliskProjectilePolicy;

public final class ObeliskIntegrityPolicyTest {
    public static void main(String[] args) {
        ObeliskIntegrityPolicy.HitDecision accepted = hit(3, true, false);
        check(accepted.accepted(), "a reflected event projectile from the source obelisk must damage it");
        check(accepted.remainingHealth() == 2, "one accepted hit removes exactly one HP");
        check(accepted.reason() == ObeliskIntegrityPolicy.RejectReason.ACCEPTED,
                "the source obelisk hit must be accepted");

        ObeliskIntegrityPolicy.HitDecision wrongSource = hit(3, false, false);
        check(!wrongSource.accepted(), "a reflected projectile must not damage a different obelisk");
        check(wrongSource.remainingHealth() == 3 && !wrongSource.destroyed(),
                "a wrong-source hit must not change health or destroy the target");
        check(wrongSource.reason() == ObeliskIntegrityPolicy.RejectReason.WRONG_SOURCE_OBELISK,
                "wrong-source provenance must be reported explicitly");

        ObeliskIntegrityPolicy.HitDecision lastHpWrongSource = hit(1, false, false);
        check(!lastHpWrongSource.accepted() && lastHpWrongSource.remainingHealth() == 1
                        && !lastHpWrongSource.destroyed(),
                "a wrong-source hit must not destroy a critical obelisk");

        ObeliskIntegrityPolicy.HitDecision consumed = hit(3, false, true);
        check(consumed.reason() == ObeliskIntegrityPolicy.RejectReason.ALREADY_CONSUMED,
                "duplicate impact is rejected before provenance to avoid a second transaction");

        System.out.println("ObeliskIntegrityPolicyTest OK");
    }

    private static ObeliskIntegrityPolicy.HitDecision hit(int health, boolean sourceMatches,
                                                           boolean consumed) {
        return ObeliskIntegrityPolicy.tryReflectedHit(
                health, 3, true, ObeliskProjectilePolicy.Kind.OBELISK_RIFT_FIREBALL,
                true, true, true, true, sourceMatches, consumed);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}