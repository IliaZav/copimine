import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import me.copimine.endevent.domain.MageSignaturePolicy;
import me.copimine.endevent.domain.MageSignaturePolicy.MageId;
import me.copimine.endevent.domain.MageSignaturePolicy.Signature;
import me.copimine.endevent.domain.RitualCasterTacticsPolicy;

/**
 * Behavioral contract for Wave 6 mage identity and signature ownership.
 *
 * <p>The single property that matters: the sphere acquires a signature because
 * of WHICH mage died, never because of the ordinal of its death. Every test below
 * either proves identity binding or proves that a duplicate/stale death cannot
 * produce a second progression receipt.</p>
 */
public final class MageSignaturePolicyTest {

    private static int checks;

    public static void main(String[] args) {
        signaturasAreIdentityBoundNotOrdinal();
        everyOrderYieldsTheSameSignatureSet();
        duplicateDeathGrantsNoSecondReceipt();
        invalidOrdersAreRejected();
        convergenceIsIndependentlyUsable();
        persistenceRoundTripRestoresIdentity();
        System.out.println("MageSignaturePolicyTest: " + checks + " checks passed");
    }

    /**
     * Two different mages dying "first" must hand the sphere two different
     * signatures. If binding were ordinal, both would yield RIFT_BARRAGE.
     */
    private static void signaturasAreIdentityBoundNotOrdinal() {
        List<MageId> orderA = new ArrayList<>(List.of(
                MageId.VESPERAL, MageId.ORRINTH, MageId.MALZURETH, MageId.KAILEN, MageId.NOCTURN));
        List<MageId> orderB = new ArrayList<>(List.of(
                MageId.MALZURETH, MageId.VESPERAL, MageId.NOCTURN, MageId.ORRINTH, MageId.KAILEN));

        MageId firstA = MageSignaturePolicy.mageAt(orderA, 0);
        MageId firstB = MageSignaturePolicy.mageAt(orderB, 0);
        check(firstA != firstB, "the two runs must start with different identities");

        Signature signatureA = MageSignaturePolicy.signatureFor(firstA);
        Signature signatureB = MageSignaturePolicy.signatureFor(firstB);
        check(signatureA == Signature.RIFT_BARRAGE,
                "identity VESPERAL owns RIFT_BARRAGE, got " + signatureA);
        check(signatureB == Signature.SOUL_BRAND,
                "identity MALZURETH owns SOUL_BRAND regardless of dying first, got " + signatureB);
        check(signatureA != signatureB,
                "same ordinal must not imply the same signature");
    }

    /** Any persisted shuffle must expose exactly the five canonical signatures. */
    private static void everyOrderYieldsTheSameSignatureSet() {
        RandomGenerator random = new Random(20261031L);
        for (int attempt = 0; attempt < 200; attempt++) {
            List<MageId> order = MageSignaturePolicy.selectOrder(random);
            MageSignaturePolicy.requireValidOrder(order);
            List<Signature> owned = MageSignaturePolicy.ownedSignatures(order);
            check(owned.size() == MageSignaturePolicy.MAGE_COUNT,
                    "every order owns exactly five signatures, got " + owned.size());
            check(owned.containsAll(List.of(Signature.values())),
                    "every order owns the full signature set, got " + owned);
        }
    }

    /** A repeated death callback must not hand out a second receipt. */
    private static void duplicateDeathGrantsNoSecondReceipt() {
        List<MageId> order = MageSignaturePolicy.selectOrder(new Random(7L));
        List<MageId> defeated = List.of();

        MageId first = MageSignaturePolicy.nextUndefeated(order, defeated);
        defeated = MageSignaturePolicy.recordDefeat(order, defeated, first);
        check(defeated != null && defeated.size() == 1, "first defeat is recorded once");

        List<MageId> again = MageSignaturePolicy.recordDefeat(order, defeated, first);
        check(again == null, "a duplicate death of the same identity grants no receipt");

        check(!MageSignaturePolicy.allDefeated(order, defeated),
                "one defeat must never complete the run");

        MageId second = MageSignaturePolicy.nextUndefeated(order, defeated);
        check(second != null && second != first, "the next stage is a different identity");

        List<MageId> all = defeated;
        for (MageId mage : order) {
            List<MageId> next = MageSignaturePolicy.recordDefeat(order, all, mage);
            if (next != null) {
                all = next;
            }
        }
        check(MageSignaturePolicy.allDefeated(order, all),
                "all five distinct identities complete the run");
        check(MageSignaturePolicy.nextUndefeated(order, all) == null,
                "no sixth mage may exist");
    }

    /** A malformed persisted order must fail loudly, never silently repair. */
    private static void invalidOrdersAreRejected() {
        expectReject(() -> MageSignaturePolicy.requireValidOrder(
                List.of(MageId.VESPERAL, MageId.VESPERAL, MageId.KAILEN, MageId.NOCTURN, MageId.ORRINTH)),
                "duplicate identity");
        expectReject(() -> MageSignaturePolicy.requireValidOrder(
                List.of(MageId.VESPERAL, MageId.ORRINTH)),
                "short order");
        expectReject(() -> MageSignaturePolicy.requireValidOrder(null),
                "null order");
        expectReject(() -> MageSignaturePolicy.mageAt(
                List.of(MageId.VESPERAL, MageId.ORRINTH, MageId.MALZURETH, MageId.KAILEN, MageId.NOCTURN), 5),
                "stage index out of range");
        expectReject(() -> MageSignaturePolicy.parseMage("NOT_A_MAGE"),
                "unknown identity token");
        expectReject(() -> MageSignaturePolicy.recordDefeat(
                List.of(MageId.VESPERAL, MageId.ORRINTH, MageId.MALZURETH, MageId.KAILEN, MageId.NOCTURN),
                List.of(), null),
                "null mage identity");
    }

    /**
     * The fifth identity's combination signature must not depend on the two
     * standalone parents having been acquired first.
     */
    private static void convergenceIsIndependentlyUsable() {
        List<MageId> order = List.of(
                MageId.NOCTURN, MageId.VESPERAL, MageId.ORRINTH, MageId.MALZURETH, MageId.KAILEN);
        List<MageId> defeated = MageSignaturePolicy.recordDefeat(order, List.of(), MageId.NOCTURN);
        check(defeated != null, "the convergence mage can be defeated first");

        List<Signature> owned = MageSignaturePolicy.ownedSignatures(defeated);
        check(owned.equals(List.of(Signature.CONVERGENCE)),
                "defeating only NOCTURN acquires CONVERGENCE alone, got " + owned);
        check(!owned.contains(Signature.RIFT_BARRAGE) && !owned.contains(Signature.GRAVITY_ANCHOR),
                "CONVERGENCE does not require its parent signatures");

        check(MageSignaturePolicy.signatureFor(MageId.NOCTURN) == Signature.CONVERGENCE,
                "the fifth identity owns the combined cast as its own signature");
    }

    /** Persisted bindings must round-trip by name, never by ordinal. */
    private static void persistenceRoundTripRestoresIdentity() {
        for (MageId mage : MageId.values()) {
            String encoded = MageSignaturePolicy.encodeBinding(mage);
            int separator = encoded.indexOf('=');
            check(separator > 0, "binding has a separator: " + encoded);

            MageId parsed = MageSignaturePolicy.parseMage(encoded.substring(0, separator));
            Signature parsedSignature =
                    MageSignaturePolicy.parseSignature(encoded.substring(separator + 1));
            check(parsed == mage, "identity round-trips: " + mage);
            check(parsedSignature == MageSignaturePolicy.signatureFor(mage),
                    "signature round-trips for " + mage);
        }

        check(MageSignaturePolicy.roleFor(MageId.NOCTURN)
                        == RitualCasterTacticsPolicy.Role.FINAL_SEAL,
                "the fifth identity keeps its existing combat role");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectReject(Runnable action, String label) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection for " + label);
    }
}
