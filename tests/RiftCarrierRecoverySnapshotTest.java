import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.RiftCarrierPolicy;
import me.copimine.endevent.domain.RiftCarrierRecoverySnapshot;

public final class RiftCarrierRecoverySnapshotTest {
    public static void main(String[] args) {
        String event = UUID.randomUUID().toString();
        long generation = 18L;
        UUID carrier = UUID.randomUUID();
        UUID charge = UUID.randomUUID();
        UUID holder = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        RiftCarrierPolicy.State active = new RiftCarrierPolicy.State(generation, 1,
                null, charge, holder, 250L, RiftCarrierPolicy.Phase.CHARGE_CARRIED);
        Map<String, String> encoded = RiftCarrierRecoverySnapshot.encode(
                event, generation, active, token, 100L, 1_900_000_000_000L);
        var restored = RiftCarrierRecoverySnapshot.decode(
                encoded, event, generation, 1_900_000_000_500L);
        check(restored != null && restored.inventoryToken().equals(token)
                        && restored.state().delivered() == 1
                        && restored.state().phase() == RiftCarrierPolicy.Phase.CHARGE_CARRIED
                        && restored.state().charge().equals(charge)
                        && restored.state().holder().equals(holder)
                        && restored.state().deadlineTick() == 150L,
                "restart must preserve the same carried token, owner, charge, progress, and paused timeout");

        var waiting = RiftCarrierRecoverySnapshot.decode(RiftCarrierRecoverySnapshot.encode(
                event, generation, RiftCarrierPolicy.initial(generation), null, 0L,
                1_900_000_000_000L), event, generation, 1_900_000_000_000L);
        check(waiting.state().phase() == RiftCarrierPolicy.Phase.WAITING_FOR_CARRIER,
                "a fresh Wave 1 checkpoint must restore without synthesizing a charge");

        expectInvalid(() -> RiftCarrierRecoverySnapshot.decode(encoded, event, generation + 1,
                1_900_000_000_500L), "wrong generation must be rejected");
        expectInvalid(() -> RiftCarrierRecoverySnapshot.decode(encoded, UUID.randomUUID().toString(), generation,
                1_900_000_000_500L), "wrong event must be rejected");

        Map<String, String> missingToken = new LinkedHashMap<>(encoded);
        missingToken.put(RiftCarrierRecoverySnapshot.PREFIX + "inventory-token", "");
        expectInvalid(() -> RiftCarrierRecoverySnapshot.decode(missingToken, event, generation,
                1_900_000_000_500L), "carried charge without its inventory token must fail closed");
        Map<String, String> extra = new LinkedHashMap<>(encoded);
        extra.put(RiftCarrierRecoverySnapshot.PREFIX + "unexpected", "true");
        expectInvalid(() -> RiftCarrierRecoverySnapshot.decode(extra, event, generation,
                1_900_000_000_500L), "unknown checkpoint fields must fail closed");
        expectInvalid(() -> RiftCarrierRecoverySnapshot.decode(encoded, event, generation,
                1_899_999_999_999L), "backwards timestamp must be rejected");
        check(RiftCarrierRecoverySnapshot.decode(Map.of("unrelated", "1"), event,
                generation, 1_900_000_000_500L) == null,
                "unrelated snapshot progress must not be misread as a Wave 1 checkpoint");
    }

    private static void expectInvalid(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
