import me.copimine.endevent.domain.ObeliskPlacementPolicy;
import me.copimine.endevent.domain.ObeliskRepelPolicy;
import me.copimine.endevent.domain.PortalCaptureIndicatorPolicy;
import me.copimine.endevent.domain.PortalCapturePolicy;

import java.util.List;

/**
 * W3-003/W3-008 portal capture indicators, W3-004 closing window, W4-001/W4-002
 * irregular outer obelisk layout and W4-009/W4-010 close-range repel ladder.
 */
public final class EndRiftWave34IndicatorsTest {
    public static void main(String[] args) {
        portalWindow();
        indicatorAmount();
        indicatorDirection();
        quarterRing();
        obeliskScatter();
        obeliskRepel();
        System.out.println("EndRiftWave34IndicatorsTest OK");
    }

    /** W3-004: closing is substantially longer than the retired five seconds. */
    private static void portalWindow() {
        check(PortalCapturePolicy.CAPTURE_MILLIS >= 18_000L,
                "W3-004: closing must be at least the eighteen-second engineering default");
        PortalCapturePolicy.PortalState state = PortalCapturePolicy.initial();
        state = PortalCapturePolicy.tick(state, true, 0L);
        state = PortalCapturePolicy.tick(state, true, 17_999L);
        check(!state.completed(), "W3-004: seventeen seconds must not close the portal");
        state = PortalCapturePolicy.tick(state, true, 18_000L);
        check(state.completed(), "W3-004: eighteen seconds must close the portal");
    }

    /** W3-003: the ring shows the authoritative amount, never more. */
    private static void indicatorAmount() {
        PortalCaptureIndicatorPolicy.Indicators half = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(false, 9_000L, -1L, -1L), true, -1.0D);
        check(half.visible(), "an active incomplete portal must show indicators");
        check(half.litSegments() == PortalCaptureIndicatorPolicy.RING_SEGMENTS / 2,
                "half progress must light half of the ring: " + half.litSegments());
        check(Math.abs(half.progress() - 0.5D) < 1.0E-9D, "progress must be authoritative");

        PortalCaptureIndicatorPolicy.Indicators justBefore = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(false, 17_990L, -1L, -1L), true, -1.0D);
        check(justBefore.litSegments() < PortalCaptureIndicatorPolicy.RING_SEGMENTS,
                "W3-008: the ring must never be full while the server is still incomplete");

        PortalCaptureIndicatorPolicy.Indicators done = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(true, 18_000L, -1L, -1L), true, -1.0D);
        check(!done.visible(), "a completed portal defers to the authored collapse animation");

        PortalCaptureIndicatorPolicy.Indicators dormant = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(false, 9_000L, -1L, -1L), false, -1.0D);
        check(dormant.visible() && dormant.flow() == PortalCaptureIndicatorPolicy.Flow.IDLE,
                "a dormant portal keeps a dim, static ring");
        check(dormant.veinCount() == 0, "a dormant portal must not draw seal veins");

        check(PortalCaptureIndicatorPolicy.litSegmentAngles(half).size() == half.litSegments(),
                "one angle per lit segment, no uniform full circle");
    }

    /** W3-008: streams flow inward while closing, outward while recovering. */
    private static void indicatorDirection() {
        PortalCapturePolicy.PortalState grew =
                new PortalCapturePolicy.PortalState(false, 4_000L, -1L, -1L);
        PortalCaptureIndicatorPolicy.Indicators closing =
                PortalCaptureIndicatorPolicy.evaluate(grew, true, 3_500L / (double) PortalCapturePolicy.CAPTURE_MILLIS);
        check(closing.flow() == PortalCaptureIndicatorPolicy.Flow.INWARD,
                "W3-008: growth must stream inward");

        PortalCaptureIndicatorPolicy.Indicators recovering = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(false, 3_500L, -1L, -1L), true,
                4_000L / (double) PortalCapturePolicy.CAPTURE_MILLIS);
        check(recovering.flow() == PortalCaptureIndicatorPolicy.Flow.OUTWARD,
                "W3-008: decay must stream outward");
        check(recovering.streamStrength() >= 0.0D && recovering.streamStrength() <= 1.0D,
                "stream strength must stay bounded");

        PortalCaptureIndicatorPolicy.Indicators steady = PortalCaptureIndicatorPolicy.evaluate(
                grew, true, 4_000L / (double) PortalCapturePolicy.CAPTURE_MILLIS);
        check(steady.flow() == PortalCaptureIndicatorPolicy.Flow.IDLE,
                "a repeated timestamp must not fabricate a direction");
        check(steady.streamStrength() == 0.0D, "idle indicators emit no stream");

        check(PortalCaptureIndicatorPolicy.flow(0.5D, -1.0D) == PortalCaptureIndicatorPolicy.Flow.IDLE,
                "an unknown previous sample cannot invent a recovery");

        double inward = PortalCaptureIndicatorPolicy.swirlRadians(closing, 20L);
        double outward = PortalCaptureIndicatorPolicy.swirlRadians(recovering, 20L);
        double none = PortalCaptureIndicatorPolicy.swirlRadians(steady, 20L);
        check(inward > 0.0D && outward < 0.0D && none == 0.0D,
                "W3-008: ring and veins must reverse together with the real progress");
    }

    /** Partial progress must thicken the seal veins, never jump to a full set. */
    private static void quarterRing() {
        PortalCaptureIndicatorPolicy.Indicators quarter = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(false, PortalCapturePolicy.CAPTURE_MILLIS / 4L, -1L, -1L),
                true, -1.0D);
        check(quarter.veinCount() >= 1 && quarter.veinCount() <= PortalCaptureIndicatorPolicy.VEIN_COUNT,
                "veins must stay within the authored budget");
        PortalCaptureIndicatorPolicy.Indicators nearly = PortalCaptureIndicatorPolicy.evaluate(
                new PortalCapturePolicy.PortalState(false, PortalCapturePolicy.CAPTURE_MILLIS - 100L, -1L, -1L),
                true, -1.0D);
        check(nearly.veinCount() >= quarter.veinCount(),
                "more progress must never show fewer seal veins");
        check(nearly.veinBrightness() > quarter.veinBrightness(),
                "a nearly sealed portal burns brighter than a quarter-sealed one");
    }

    /** W4-001/W4-002: outer, irregular, deterministic and spaced. */
    private static void obeliskScatter() {
        List<ObeliskPlacementPolicy.Point> first = ObeliskPlacementPolicy.scatter(6, 4_242L, 20.0D);
        List<ObeliskPlacementPolicy.Point> again = ObeliskPlacementPolicy.scatter(6, 4_242L, 20.0D);
        check(first.equals(again), "W4-003: the same seed must reproduce the same layout");
        List<ObeliskPlacementPolicy.Point> other = ObeliskPlacementPolicy.scatter(6, 9_999L, 20.0D);
        check(!first.equals(other), "W4-001: different seeds must produce different layouts");

        check(first.size() == 6, "every obelisk must be seated");
        for (ObeliskPlacementPolicy.Point point : first) {
            double radius = Math.sqrt(point.x() * (double) point.x() + point.z() * (double) point.z());
            check(radius >= 20.0D * ObeliskPlacementPolicy.MIN_RADIUS_FRACTION - 0.51D,
                    "W4-002: obelisks must sit outside the inner ring: " + radius);
            check(radius <= 20.0D * ObeliskPlacementPolicy.MAX_RADIUS_FRACTION + 0.51D,
                    "W4-002: obelisks must stay inside the usable arena: " + radius);
        }
        // A perfectly predictable circle has one radius; reject that.
        double min = Double.MAX_VALUE;
        double max = 0.0D;
        for (ObeliskPlacementPolicy.Point point : first) {
            double radius = Math.sqrt(point.x() * (double) point.x() + point.z() * (double) point.z());
            min = Math.min(min, radius);
            max = Math.max(max, radius);
        }
        check(max - min >= 1.5D,
                "W4-001: the layout must not be a textbook circle (spread " + (max - min) + ")");

        for (int i = 0; i < first.size(); i++) {
            for (int j = i + 1; j < first.size(); j++) {
                check(Math.hypot(first.get(i).x() - first.get(j).x(),
                                first.get(i).z() - first.get(j).z())
                                >= ObeliskPlacementPolicy.MIN_DISTANCE - 1.0E-9D,
                        "W4-002: obelisks must keep a minimum spacing");
            }
        }

        // Prefix determinism: a smaller roster must be a stable subset, not a reroll.
        List<ObeliskPlacementPolicy.Point> three = ObeliskPlacementPolicy.scatter(3, 4_242L, 20.0D);
        check(three.size() == 3, "a three-obelisk roster must seat three");
        for (ObeliskPlacementPolicy.Point point : three) {
            double radius = Math.sqrt(point.x() * (double) point.x() + point.z() * (double) point.z());
            check(radius >= 20.0D * ObeliskPlacementPolicy.MIN_RADIUS_FRACTION - 0.51D,
                    "smaller rosters obey the same outer band");
        }

        // An unmeasurable arena falls back to the audited ring rather than failing.
        List<ObeliskPlacementPolicy.Point> fallback = ObeliskPlacementPolicy.scatter(4, 1L, 0.0D);
        check(fallback.size() == 4, "an unmeasurable arena must still seat every obelisk");
        check(ObeliskPlacementPolicy.hasReasonableSpacing(fallback), "the fallback ring keeps its spacing");
    }

    /** W4-009/W4-010/W4-012/W4-013: warned impulse, cooldown, no dead-source hit. */
    private static void obeliskRepel() {
        long pressure = ObeliskRepelPolicy.SUSTAINED_PRESSURE_TICKS;
        long warning = ObeliskRepelPolicy.WARNING_TICKS;

        ObeliskRepelPolicy.Result idle = ObeliskRepelPolicy.step(
                ObeliskRepelPolicy.Timers.IDLE_TIMERS, 100L, false, true, true);
        check(idle.decision() == ObeliskRepelPolicy.Decision.IDLE, "an empty ring must stay idle");
        check(idle.timers().pressureSinceTick() == -1L, "an empty ring accrues no pressure");

        // W4-010: a simple entry must not instantly launch anyone.
        ObeliskRepelPolicy.Result touched = ObeliskRepelPolicy.step(
                ObeliskRepelPolicy.Timers.IDLE_TIMERS, 200L, true, true, true);
        check(touched.decision() == ObeliskRepelPolicy.Decision.IDLE,
                "W4-010: entering the ring must not instantly launch the player");
        check(touched.timers().pressureSinceTick() == 200L, "pressure must start accumulating");

        ObeliskRepelPolicy.Result holding = ObeliskRepelPolicy.step(
                touched.timers(), 200L + pressure - 1L, true, true, true);
        check(holding.decision() == ObeliskRepelPolicy.Decision.IDLE,
                "W4-009: pressure below the sustained threshold must not warn");

        ObeliskRepelPolicy.Result warned = ObeliskRepelPolicy.step(
                holding.timers(), 200L + pressure, true, true, true);
        check(warned.decision() == ObeliskRepelPolicy.Decision.WARNING,
                "W4-010: sustained pressure must warn before the impulse");
        check(warned.timers().warningStartedTick() == 200L + pressure,
                "the warning tick must be recorded for the tell");

        ObeliskRepelPolicy.Result stillWarning = ObeliskRepelPolicy.step(
                warned.timers(), 200L + pressure + warning - 1L, true, true, true);
        check(stillWarning.decision() == ObeliskRepelPolicy.Decision.WARNING,
                "the warning must last its whole one-second tell");

        ObeliskRepelPolicy.Result repelled = ObeliskRepelPolicy.step(
                stillWarning.timers(), 200L + pressure + warning, true, true, true);
        check(repelled.decision() == ObeliskRepelPolicy.Decision.REPELLED,
                "W4-009: sustained pressure must actually repel the camper");
        check(repelled.timers().lastRepelledTick() == 200L + pressure + warning,
                "the impulse tick must be recorded for the cooldown");

        ObeliskRepelPolicy.Result cooling = ObeliskRepelPolicy.step(
                repelled.timers(), 200L + pressure + warning + 1L, true, true, true);
        check(cooling.decision() == ObeliskRepelPolicy.Decision.COOLDOWN,
                "W4-010: the obelisk must cool down after a repel");
        ObeliskRepelPolicy.Result stillCooling = ObeliskRepelPolicy.step(
                cooling.timers(),
                200L + pressure + warning + ObeliskRepelPolicy.COOLDOWN_TICKS - 1L,
                true, true, true);
        check(stillCooling.decision() == ObeliskRepelPolicy.Decision.COOLDOWN,
                "the cooldown must not be shortened by re-entry");
        // After the cooldown the pressure restarts from zero, so a real camper is
        // warned again rather than launched on the first post-cooldown tick.
        ObeliskRepelPolicy.Result resumed = ObeliskRepelPolicy.step(
                stillCooling.timers(),
                200L + pressure + warning + ObeliskRepelPolicy.COOLDOWN_TICKS, true, true, true);
        check(resumed.decision() == ObeliskRepelPolicy.Decision.IDLE,
                "W4-010: the first post-cooldown tick must not launch anybody");
        ObeliskRepelPolicy.Result rewarned = ObeliskRepelPolicy.step(
                resumed.timers(),
                200L + pressure + warning + ObeliskRepelPolicy.COOLDOWN_TICKS + pressure,
                true, true, true);
        check(rewarned.decision() == ObeliskRepelPolicy.Decision.WARNING,
                "after the cooldown a real camper is warned again, not launched");

        // Leaving the radius aborts the pending warning.
        ObeliskRepelPolicy.Result left = ObeliskRepelPolicy.step(warned.timers(), 300L, false, true, true);
        check(left.decision() == ObeliskRepelPolicy.Decision.WARNING_ABORTED,
                "W4-010: leaving during the tell must abort it");
        check(left.timers().warningStartedTick() == -1L && left.timers().pressureSinceTick() == -1L,
                "an aborted warning must clear both pressure and the tell");

        // No safe free space means no impulse at all, never a wall slam. Until
        // the sustained threshold is met the obelisk simply keeps accruing
        // pressure; it never starts a tell it cannot honour.
        ObeliskRepelPolicy.Result unsafe = ObeliskRepelPolicy.step(
                ObeliskRepelPolicy.Timers.IDLE_TIMERS, 700L, true, false, true);
        check(unsafe.decision() == ObeliskRepelPolicy.Decision.IDLE,
                "W4-013: without free space the obelisk must not start a tell");
        ObeliskRepelPolicy.Result unsafeLater = ObeliskRepelPolicy.step(
                unsafe.timers(), 700L + ObeliskRepelPolicy.SUSTAINED_PRESSURE_TICKS + 1L,
                true, false, true);
        check(unsafeLater.decision() == ObeliskRepelPolicy.Decision.NO_SAFE_SPACE,
                "W4-013: unsafe knockback must be rejected instead of clipping a player");
        ObeliskRepelPolicy.Result unsafeRecovered = ObeliskRepelPolicy.step(
                unsafeLater.timers(), 700L + ObeliskRepelPolicy.SUSTAINED_PRESSURE_TICKS + 2L,
                true, true, true);
        check(unsafeRecovered.decision() == ObeliskRepelPolicy.Decision.WARNING,
                "once free space exists again the obelisk warns before repelling");

        // A destroyed obelisk can never repel.
        ObeliskRepelPolicy.Result dead = ObeliskRepelPolicy.step(
                ObeliskRepelPolicy.Timers.IDLE_TIMERS, 900L, true, true, false);
        check(dead.decision() == ObeliskRepelPolicy.Decision.OBELISK_DESTROYED,
                "W4-013: a destroyed obelisk must not hit anyone");
        check(dead.timers().equals(ObeliskRepelPolicy.Timers.IDLE_TIMERS),
                "a destroyed obelisk resets its timers so no delayed impulse survives");

        check(ObeliskRepelPolicy.CLOSE_RADIUS_BLOCKS == 3.0D, "W4-010: close radius defaults to three blocks");
        check(ObeliskRepelPolicy.WARNING_TICKS == 20L, "W4-010: the tell is one second");
        check(ObeliskRepelPolicy.COOLDOWN_TICKS == 180L, "W4-010: the cooldown is nine seconds");
        check(PortalCapturePolicy.GRACE_MILLIS == 450L, "W3-006: the boundary grace stays 450 ms");
        check(!ObeliskRepelPolicy.GRANTS_INVULNERABILITY,
                "W4-012: the repel must not add an invulnerability window");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}