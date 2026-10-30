import java.util.UUID;
import me.copimine.endevent.domain.RiftCarrierPolicy;

public final class RiftCarrierPolicyTest {
    public static void main(String[] args) {
        long generation = 7L;
        UUID carrier = UUID.randomUUID();
        UUID charge = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        RiftCarrierPolicy.State state = RiftCarrierPolicy.initial(generation);
        state = RiftCarrierPolicy.selectCarrier(state, generation, carrier);
        state = RiftCarrierPolicy.carrierDied(state, generation, carrier, charge, 100L);
        check(state.phase() == RiftCarrierPolicy.Phase.CHARGE_DROPPED,
                "carrier death must create a dropped charge");
        check(state.delivered() == 0, "carrier death must not count as delivery");
        UUID recreatedCharge = UUID.randomUUID();
        RiftCarrierPolicy.State restored = RiftCarrierPolicy.replaceMissingCharge(
                state, generation, recreatedCharge);
        check(restored.phase() == state.phase() && recreatedCharge.equals(restored.charge())
                        && restored.deadlineTick() == state.deadlineTick() && restored.delivered() == 0,
                "lost dropped charge must reconstruct without changing delivery progress");
        check(RiftCarrierPolicy.replaceMissingCharge(restored, generation + 1, charge) == restored,
                "stale generation cannot replace the live charge");
        check(RiftCarrierPolicy.pickUp(state, generation, player, charge, 100_000L).holder() != null,
                "a dropped charge must remain available without a timeout");
        state = RiftCarrierPolicy.pickUp(state, generation, player, charge, 120L);
        UUID recipient = UUID.randomUUID();
        RiftCarrierPolicy.State transferred = RiftCarrierPolicy.transferHolder(state, generation, player, recipient, 121L);
        check(recipient.equals(transferred.holder()) && transferred.deadlineTick() == state.deadlineTick()
                        && transferred.delivered() == state.delivered() && transferred.charge().equals(state.charge()),
                "inventory transfer must preserve charge identity and progress");
        check(RiftCarrierPolicy.transferHolder(state, generation + 1, player, recipient, 121L) == state,
                "stale generation cannot transfer an inventory token");
        check(RiftCarrierPolicy.transferHolder(state, generation, recipient, player, 121L) == state,
                "a non-holder cannot transfer custody");
        check(recipient.equals(RiftCarrierPolicy.transferHolder(state, generation, player, recipient, 100_000L).holder()),
                "a carried charge remains transferable after an arbitrarily long delay");
        check(RiftCarrierPolicy.deliver(transferred, generation, player, true, 122L) == transferred,
                "the former holder cannot deliver after custody transfer");
        check(RiftCarrierPolicy.deliver(state, generation, player, false, 121L).delivered() == 0,
                "charge outside the Core must not be delivered");
        state = RiftCarrierPolicy.deliver(state, generation, player, true, 122L);
        check(state.delivered() == 1 && state.phase() == RiftCarrierPolicy.Phase.READY_FOR_NEXT_CARRIER,
                "delivery at Core must advance exactly one charge");
        state = RiftCarrierPolicy.selectCarrier(state, generation, carrier);
        state = RiftCarrierPolicy.carrierDied(state, generation, carrier, UUID.randomUUID(), 200L);
        state = RiftCarrierPolicy.pickUp(state, generation, player, state.charge(), 201L);
        RiftCarrierPolicy.State released = RiftCarrierPolicy.releaseHolder(
                state, generation, player, 202L);
        check(released.phase() == RiftCarrierPolicy.Phase.CHARGE_DROPPED
                        && released.holder() == null && released.charge() != null,
                "holder death or disconnect must immediately drop the same charge");
        check(RiftCarrierPolicy.releaseHolder(released, generation, player, 203L) == released,
                "releasing a non-holder must not mutate the charge state");
        state = released;
        state = RiftCarrierPolicy.pickUp(state, generation, player, state.charge(), 204L);
        state = RiftCarrierPolicy.deliver(state, generation, player, true, 205L);
        check(state.delivered() == 2, "released charge must remain deliverable exactly once");
        check(RiftCarrierPolicy.shouldSpawnReplacement(state, generation, true, 0),
                "an unfinished objective with no remaining candidates must request a bounded replacement carrier");
        check(!RiftCarrierPolicy.shouldSpawnReplacement(state, generation, false, 0),
                "replacement carriers must not be spawned while scheduled groups can still provide candidates");
        state = RiftCarrierPolicy.selectCarrier(state, generation, carrier);
        state = RiftCarrierPolicy.carrierDied(state, generation, carrier, UUID.randomUUID(), 200L);
        check(RiftCarrierPolicy.expireForReplacement(state, generation, 100_000L) == state,
                "an undelivered charge cannot be discarded by a timer");
        check(RiftCarrierPolicy.timeoutTransfer(state, generation, UUID.randomUUID(), 100_000L) == state,
                "a charge cannot be transferred to another carrier by a timer");
        state = RiftCarrierPolicy.pickUp(state, generation, player, state.charge(), 100_001L);
        state = RiftCarrierPolicy.deliver(state, generation, player, true, 200_000L);
        check(state.delivered() == 3 && RiftCarrierPolicy.isComplete(state),
                "three distinct deliveries complete the objective even after a long delay");
        System.out.println("RiftCarrierPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
