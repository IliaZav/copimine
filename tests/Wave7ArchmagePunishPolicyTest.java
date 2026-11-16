import me.copimine.endevent.domain.Wave7ArchmagePunishPolicy;

/**
 * MAGE7-005: the Archmage resists permanent melee camping through a bounded
 * warned repel and a safe tactical Step, but a major cast must leave a real
 * window a melee player can actually use.
 */
public final class Wave7ArchmagePunishPolicyTest {
    public static void main(String[] args) {
        majorCastOpensAPunishWindow();
        stepIsRefusedDuringThePunishWindow();
        repelIsBoundedAndWarned();
        approachNeverTriggersAnEndlessTeleportChain();
        rebuildsTheWindowFromItsPersistedCloseTick();
        stepGateCombinesWindowAndCooldown();
        System.out.println("Wave7ArchmagePunishPolicyTest OK");
    }

    /**
     * The runtime persists only the close tick, so the window it reconstructs
     * after a restart must be identical to the one the cast opened - never
     * longer, never reopened.
     */
    private static void rebuildsTheWindowFromItsPersistedCloseTick() {
        long castEnd = 4_000L;
        var opened = Wave7ArchmagePunishPolicy.afterMajorCast(castEnd);
        check(Wave7ArchmagePunishPolicy.punishUntilTick(castEnd) == opened.closesAt(),
                "the persisted close tick is exactly what the cast opened");
        var rebuilt = Wave7ArchmagePunishPolicy.windowEndingAt(opened.closesAt());
        check(rebuilt != null && rebuilt.opensAt() == opened.opensAt()
                        && rebuilt.closesAt() == opened.closesAt(),
                "a restart rebuilds the identical window from the stored close tick");
        check(Wave7ArchmagePunishPolicy.windowEndingAt(0L) == null,
                "no stored close tick means no window, not a fabricated one");
        check(Wave7ArchmagePunishPolicy.windowEndingAt(Wave7ArchmagePunishPolicy.PUNISH_TICKS - 1L)
                        == null,
                "a close tick too early to open a real window is refused");
    }

    private static void stepGateCombinesWindowAndCooldown() {
        long never = Wave7ArchmagePunishPolicy.NEVER;
        long window = Wave7ArchmagePunishPolicy.afterMajorCast(1_000L).closesAt();
        check(Wave7ArchmagePunishPolicy.stepAllowed(500L, never, null),
                "outside any window and never stepped, Step is available");
        check(!Wave7ArchmagePunishPolicy.stepAllowed(1_050L, never,
                        Wave7ArchmagePunishPolicy.windowEndingAt(window)),
                "Step is refused inside the window even before its first use");
        long after = window + 1L;
        check(!Wave7ArchmagePunishPolicy.stepAllowed(after,
                        after - (Wave7ArchmagePunishPolicy.STEP_COOLDOWN_TICKS - 1L), null),
                "a recent Step still owns its cooldown once the window closes");
        check(Wave7ArchmagePunishPolicy.stepAllowed(after,
                        after - Wave7ArchmagePunishPolicy.STEP_COOLDOWN_TICKS, null),
                "Step returns exactly when its cooldown has run");
    }

    private static void majorCastOpensAPunishWindow() {
        check(Wave7ArchmagePunishPolicy.PUNISH_TICKS > 0L,
                "a major cast must leave a usable punish window");
        long castEnd = 1_000L;
        var window = Wave7ArchmagePunishPolicy.afterMajorCast(castEnd);
        check(window.openAt(castEnd), "the window opens the instant the cast resolves");
        check(window.openAt(castEnd + Wave7ArchmagePunishPolicy.PUNISH_TICKS - 1L),
                "the window lasts for its whole advertised length");
        check(!window.openAt(castEnd + Wave7ArchmagePunishPolicy.PUNISH_TICKS),
                "the window closes exactly when it expires");
        check(!window.openAt(castEnd - 1L), "the window does not open before the cast resolves");
        // The window must be long enough for a melee approach to pay off.
        check(Wave7ArchmagePunishPolicy.PUNISH_TICKS >= 40L,
                "a punish window under two seconds is not a real opportunity");
    }

    private static void stepIsRefusedDuringThePunishWindow() {
        var window = Wave7ArchmagePunishPolicy.afterMajorCast(500L);
        long inside = 500L + Wave7ArchmagePunishPolicy.PUNISH_TICKS / 2L;
        check(!Wave7ArchmagePunishPolicy.mayStep(inside, window),
                "the mage cannot Step away during its own punish window");
        check(Wave7ArchmagePunishPolicy.mayStep(500L + Wave7ArchmagePunishPolicy.PUNISH_TICKS, window),
                "Step becomes available again once the window closes");
    }

    private static void repelIsBoundedAndWarned() {
        check(Wave7ArchmagePunishPolicy.REPEL_WARNING_TICKS > 0L,
                "the repel is warned, not instant");
        check(Wave7ArchmagePunishPolicy.REPEL_MAX_DISTANCE > 0.0D
                        && Wave7ArchmagePunishPolicy.REPEL_MAX_DISTANCE <= 12.0D,
                "the repel distance is bounded, so it cannot fling players out of the arena");
        check(Wave7ArchmagePunishPolicy.REPEL_COOLDOWN_TICKS > 0L,
                "the repel has a cooldown of its own");
        var window = Wave7ArchmagePunishPolicy.afterMajorCast(0L);
        check(!Wave7ArchmagePunishPolicy.mayRepel(10L, 0L, window),
                "no second repel while the punish window is open");
    }

    private static void approachNeverTriggersAnEndlessTeleportChain() {
        long stepCooldown = Wave7ArchmagePunishPolicy.STEP_COOLDOWN_TICKS;
        check(stepCooldown > 0L, "Step has a real cooldown");
        int steps = 0;
        long tick = 0L;
        long lastStep = Wave7ArchmagePunishPolicy.NEVER;
        // A player pressing into the mage every tick for ten seconds.
        for (int press = 0; press < 200; press++) {
            if (Wave7ArchmagePunishPolicy.mayStep(tick, null)
                    && Wave7ArchmagePunishPolicy.offCooldown(tick, lastStep)) {
                steps++;
                lastStep = tick;
            }
            tick++;
        }
        check(steps <= 200 / (int) Math.max(1L, stepCooldown) + 1,
                "repeated approaches cannot trigger an unbounded teleport chain");
        check(steps < 200, "an approach on every tick does not teleport on every tick");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}