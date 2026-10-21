import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.RiftCarrierCustodyPolicy;
import me.copimine.endevent.domain.RiftCarrierCustodyPolicy.Slot;
import me.copimine.endevent.domain.RiftCarrierCustodyPolicy.TokenLocation;

public final class RiftCarrierCustodyPolicyTest {
    public static void main(String[] args) {
        UUID holder = UUID.fromString("00000000-0000-0000-0000-000000000123");
        RiftCarrierCustodyPolicy.Decision cursor = RiftCarrierCustodyPolicy.resolve(List.of(
                new TokenLocation(holder, Slot.CURSOR, 1, true, true)));
        check(!cursor.valid() && cursor.copies() == 1,
                "a cursor-held charge is not in an inventory slot and releases custody");

        RiftCarrierCustodyPolicy.Decision duplicate = RiftCarrierCustodyPolicy.resolve(List.of(
                new TokenLocation(holder, Slot.CURSOR, 1, true, true),
                new TokenLocation(holder, Slot.STORAGE, 1, true, true)));
        check(!duplicate.valid(), "a cursor token plus an inventory copy is duplicate custody");

        RiftCarrierCustodyPolicy.Decision external = RiftCarrierCustodyPolicy.resolve(List.of(
                new TokenLocation(null, Slot.OBSERVED_INVENTORY, 1, true, false)));
        check(!external.valid(), "an observed container token has no active player custodian");

        RiftCarrierCustodyPolicy.Decision amountTwo = RiftCarrierCustodyPolicy.resolve(List.of(
                new TokenLocation(holder, Slot.CURSOR, 2, true, true)));
        check(!amountTwo.valid(), "a stacked carrier token cannot be treated as one charge");
        System.out.println("RiftCarrierCustodyPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
