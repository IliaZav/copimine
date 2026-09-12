import java.util.UUID;
import me.copimine.endevent.domain.RiftCarrierPolicy;

public final class RiftCarrierPolicyTest {
    public static void main(String[] args) {
        long generation = 7L;
        UUID carrier = UUID.randomUUID();
        UUID charge = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID nextCarrier = UUID.randomUUID();
        RiftCarrierPolicy.State state = RiftCarrierPolicy.initial(generation);
        state = RiftCarrierPolicy.selectCarrier(state, generation, carrier);
        state = RiftCarrierPolicy.carrierDied(state, generation, carrier, charge, 100L);
        check(state.phase() == RiftCarrierPolicy.Phase.CHARGE_DROPPED,
                "carrier death must create a dropped charge");
        check(state.delivered() == 0, "carrier death must not count as delivery");
        check(RiftCarrierPolicy.pickUp(state, generation, player, charge, 299L).holder() != null,
                "a participant may pick up the charge before timeout");
        state = RiftCarrierPolicy.pickUp(state, generation, player, charge, 120L);
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
        state = RiftCarrierPolicy.expireForReplacement(state, generation, 401L);
        check(state.phase() == RiftCarrierPolicy.Phase.READY_FOR_NEXT_CARRIER
                        && state.charge() == null && state.delivered() == 2,
                "an expired undelivered charge must be cleared before a replacement carrier is spawned");
        state = RiftCarrierPolicy.selectCarrier(state, generation, carrier);
        state = RiftCarrierPolicy.carrierDied(state, generation, carrier, UUID.randomUUID(), 500L);
        state = RiftCarrierPolicy.timeoutTransfer(state, generation, nextCarrier, 401L);
        check(state.phase() == RiftCarrierPolicy.Phase.CHARGE_DROPPED,
                "an unexpired charge must remain available rather than transferring early");
        state = RiftCarrierPolicy.timeoutTransfer(state, generation, nextCarrier, 701L);
        check(state.phase() == RiftCarrierPolicy.Phase.CARRIER_ACTIVE
                        && nextCarrier.equals(state.carrier()),
                "expired charge must transfer to another carrier");
        check(RiftCarrierPolicy.timeoutTransfer(state, generation, null, 702L).carrier()
                        .equals(nextCarrier), "invalid transfer must not mutate state");
        System.out.println("RiftCarrierPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
