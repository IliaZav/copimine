import java.util.ArrayList;
import java.util.List;

import me.copimine.endevent.domain.BossCastAvailabilityPolicy;
import me.copimine.endevent.domain.BossCastAvailabilityPolicy.BusySlot;
import me.copimine.endevent.domain.EndRiftAiPolicy;

/**
 * N23-P1-11 (starvation half): a live Last Seal ring may keep exactly one
 * light, telegraphed, dodgeable spell alive and must defer every body- or
 * floor-owning cast; an ambush grab still pauses all major casts.
 */
public final class BossCastAvailabilityPolicyTest {

    public static void main(String[] args) {
        onlyOneRingCompatibleSpell();
        blocksAllCastsOnlyForAmbush();
        noneKeepsTheWholePoolInOrder();
        nullSlotBehavesLikeNone();
        ringKeepsOnlyCompatibleSpells();
        ambushYieldsNothing();
        emptyAndNullPoolsAreEmpty();
        resultsAreImmutable();
        ringNeverLeaksABodyOwner();
        System.out.println("BossCastAvailabilityPolicyTest OK");
    }

    private static void onlyOneRingCompatibleSpell() {
        int compatible = 0;
        for (EndRiftAiPolicy.BossSpell spell : EndRiftAiPolicy.BossSpell.values()) {
            if (BossCastAvailabilityPolicy.compatibleWhileRing(spell)) {
                compatible++;
                check(spell == EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                        "unexpected ring-compatible spell " + spell);
            }
        }
        check(compatible == 1,
                "exactly one ring-compatible spell is allowed, got " + compatible);
        check(!BossCastAvailabilityPolicy.compatibleWhileRing(null),
                "null must never be ring-compatible");
    }

    private static void blocksAllCastsOnlyForAmbush() {
        check(BossCastAvailabilityPolicy.blocksAllCasts(BusySlot.AMBUSH),
                "an ambush grab must pause every major cast");
        check(!BossCastAvailabilityPolicy.blocksAllCasts(BusySlot.RING),
                "a ring must not pause every major cast");
        check(!BossCastAvailabilityPolicy.blocksAllCasts(BusySlot.NONE),
                "an idle slot must not pause casts");
        check(!BossCastAvailabilityPolicy.blocksAllCasts(null),
                "a null slot must not pause casts");
    }

    private static void noneKeepsTheWholePoolInOrder() {
        List<EndRiftAiPolicy.BossSpell> pool = samplePool();
        List<EndRiftAiPolicy.BossSpell> kept =
                BossCastAvailabilityPolicy.filter(pool, BusySlot.NONE);
        check(kept.equals(pool), "NONE must keep the whole pool in order");
    }

    private static void nullSlotBehavesLikeNone() {
        List<EndRiftAiPolicy.BossSpell> pool = samplePool();
        check(BossCastAvailabilityPolicy.filter(pool, null).equals(pool),
                "a null slot must behave like NONE");
    }

    private static void ringKeepsOnlyCompatibleSpells() {
        List<EndRiftAiPolicy.BossSpell> pool = samplePool();
        List<EndRiftAiPolicy.BossSpell> kept =
                BossCastAvailabilityPolicy.filter(pool, BusySlot.RING);
        check(kept.equals(List.of(EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE)),
                "RING must keep exactly the compatible projectile, got " + kept);
    }

    private static void ambushYieldsNothing() {
        check(BossCastAvailabilityPolicy.filter(samplePool(), BusySlot.AMBUSH).isEmpty(),
                "AMBUSH must yield an empty pool");
    }

    private static void emptyAndNullPoolsAreEmpty() {
        check(BossCastAvailabilityPolicy.filter(null, BusySlot.NONE).isEmpty(),
                "a null pool must be empty");
        check(BossCastAvailabilityPolicy.filter(List.of(), BusySlot.RING).isEmpty(),
                "an empty pool must be empty");
    }

    private static void resultsAreImmutable() {
        List<EndRiftAiPolicy.BossSpell> kept =
                BossCastAvailabilityPolicy.filter(samplePool(), BusySlot.NONE);
        try {
            kept.add(EndRiftAiPolicy.BossSpell.VOID_BLAST);
            throw new AssertionError("the filtered pool must be immutable");
        } catch (UnsupportedOperationException expected) {
            // The schedule receives a read-only view, never the caller's list.
        }
    }

    private static void ringNeverLeaksABodyOwner() {
        for (EndRiftAiPolicy.BossSpell spell : EndRiftAiPolicy.BossSpell.values()) {
            List<EndRiftAiPolicy.BossSpell> single = List.of(spell);
            List<EndRiftAiPolicy.BossSpell> kept =
                    BossCastAvailabilityPolicy.filter(single, BusySlot.RING);
            if (BossCastAvailabilityPolicy.compatibleWhileRing(spell)) {
                check(kept.equals(single), "the compatible spell must survive the ring");
            } else {
                check(kept.isEmpty(), "a body/floor owner leaked through the ring: " + spell);
            }
        }
    }

    private static List<EndRiftAiPolicy.BossSpell> samplePool() {
        // A mixed pool: body owners, floor owners and the one compatible spell,
        // in an order that would expose any silent re-sorting.
        return new ArrayList<>(List.of(
                EndRiftAiPolicy.BossSpell.SMALL_KAGUNE,
                EndRiftAiPolicy.BossSpell.VOID_BLAST,
                EndRiftAiPolicy.BossSpell.KAGUNE_ERUPTION,
                EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                EndRiftAiPolicy.BossSpell.GROUND_SLAM));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
