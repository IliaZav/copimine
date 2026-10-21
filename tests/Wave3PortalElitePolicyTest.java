import me.copimine.endevent.domain.Wave3PortalElitePolicy;
import me.copimine.endevent.domain.Wave3PortalElitePolicy.Action;
import me.copimine.endevent.domain.Wave3PortalElitePolicy.PortalProgress;

public final class Wave3PortalElitePolicyTest {
    public static void main(String[] args) {
        check(Wave3PortalElitePolicy.decide(PortalProgress.NOT_REACHED, true, true, false).action()
                        == Action.WAIT,
                "the encounter must wait until the second portal has actually opened");
        check(Wave3PortalElitePolicy.decide(PortalProgress.OPEN, false, true, false).action()
                        == Action.WAIT,
                "the elite must not spawn before its scheduled due point");

        Wave3PortalElitePolicy.Decision onOpen = Wave3PortalElitePolicy.decide(
                PortalProgress.OPEN, true, true, false);
        check(onOpen.action() == Action.EMERGE_FROM_SECOND_PORTAL
                        && onOpen.portalIndex() == Wave3PortalElitePolicy.SECOND_PORTAL_INDEX,
                "the elite must emerge only from portal two while it is open");

        check(Wave3PortalElitePolicy.decide(PortalProgress.CAPTURED, false, true, false).action()
                        == Action.WAIT,
                "closing portal two early must wait for the scheduled due point");
        Wave3PortalElitePolicy.Decision fallback = Wave3PortalElitePolicy.decide(
                PortalProgress.CAPTURED, true, true, false);
        check(fallback.action() == Action.FALLBACK_AT_SECOND_PORTAL_ANCHOR
                        && fallback.portalIndex() == Wave3PortalElitePolicy.SECOND_PORTAL_INDEX,
                "if portal two closes before the elite is due, its deterministic fallback stays at portal two");

        check(Wave3PortalElitePolicy.decide(PortalProgress.OPEN, true, false, false).action()
                        == Action.WAIT,
                "an inactive wave cannot start the elite encounter");
        check(Wave3PortalElitePolicy.decide(PortalProgress.CAPTURED, true, true, true).action()
                        == Action.ALREADY_SPAWNED,
                "an already spawned elite cannot be duplicated after portal transitions");
        check(Wave3PortalElitePolicy.decide(null, true, true, false).action() == Action.WAIT,
                "missing portal state must fail closed");
        System.out.println("Wave3PortalElitePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
