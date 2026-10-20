import java.util.HashMap;
import java.util.Map;
import me.copimine.endevent.domain.CarrierSlownessLeasePolicy;

public final class CarrierSlownessLeasePolicyTest {
    private static final String EVENT_KEY = "copimineendevent:wave1_carrier_slowness";

    public static void main(String[] args) {
        check(CarrierSlownessLeasePolicy.reconcile(true, false)
                        == CarrierSlownessLeasePolicy.Action.ADD,
                "start the lease when a carried charge has no event-owned modifier");
        check(CarrierSlownessLeasePolicy.reconcile(true, true)
                        == CarrierSlownessLeasePolicy.Action.KEEP,
                "keep the existing event modifier without stacking duplicates");
        check(CarrierSlownessLeasePolicy.reconcile(false, true)
                        == CarrierSlownessLeasePolicy.Action.REMOVE,
                "remove the event-owned modifier when the lease ends");
        check(CarrierSlownessLeasePolicy.reconcile(false, false)
                        == CarrierSlownessLeasePolicy.Action.NONE,
                "do nothing when no event-owned modifier exists");
        check(Math.abs(CarrierSlownessLeasePolicy.MOVEMENT_SPEED_MULTIPLIER + 0.30D) < 0.000001D,
                "preserve Slowness II's 30 percent movement-speed reduction");

        Map<String, Double> modifiers = new HashMap<>();
        modifiers.put("minecraft:generic_slowdown", -0.10D);
        modifiers.put("other_plugin:slow", -0.20D);
        reconcile(modifiers, true);
        check(modifiers.size() == 3 && modifiers.containsKey(EVENT_KEY),
                "add the event modifier without replacing unrelated modifiers");
        reconcile(modifiers, false);
        check(modifiers.size() == 2 && modifiers.containsKey("minecraft:generic_slowdown")
                        && modifiers.containsKey("other_plugin:slow"),
                "release only the event-owned modifier");

        System.out.println("CarrierSlownessLeasePolicyTest OK");
    }

    private static void reconcile(Map<String, Double> modifiers, boolean carrying) {
        boolean owned = modifiers.containsKey(EVENT_KEY);
        switch (CarrierSlownessLeasePolicy.reconcile(carrying, owned)) {
            case ADD -> modifiers.put(EVENT_KEY, CarrierSlownessLeasePolicy.MOVEMENT_SPEED_MULTIPLIER);
            case REMOVE -> modifiers.remove(EVENT_KEY);
            case KEEP, NONE -> { }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
