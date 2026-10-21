import java.util.ArrayList;
import java.util.List;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.Phase;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.State;

public final class FallingAnvilVolleyPolicyTest {
    public static void main(String[] args) {
        check(FallingAnvilVolleyPolicy.volleyCountForPlayers(10) == 1,
                "ten participants remain on the single-volley side of the threshold");
        check(FallingAnvilVolleyPolicy.volleyCountForPlayers(11) == 2,
                "the second 5x5 volley starts at eleven participants");
        check(FallingAnvilVolleyPolicy.volleyCountForPlayers(20) == 2,
                "the Wave 6 maximum remains exactly two volleys");
        expectIllegalArgument(() -> FallingAnvilVolleyPolicy.volleyCountForPlayers(1),
                "the Wave 6 roster has a two-player minimum");
        expectIllegalArgument(() -> FallingAnvilVolleyPolicy.volleyCountForPlayers(21),
                "the Wave 6 roster is capped at twenty players");

        check(FallingAnvilVolleyPolicy.visualsFor(3, 3) == 3,
                "2–3 participants receive one anvil per eligible target");
        check(FallingAnvilVolleyPolicy.visualsFor(10, 10) == 90
                        && FallingAnvilVolleyPolicy.maxVisualsForParticipants(10) == 90,
                "4–10 participants may receive one 3x3 group per eligible target");
        check(FallingAnvilVolleyPolicy.visualsFor(20, 20) == 25
                        && FallingAnvilVolleyPolicy.maxVisualsForParticipants(20) == 25,
                "11–20 participant volleys remain one 5x5 group regardless of target count");

        long generation = 71L;
        State idle = FallingAnvilVolleyPolicy.idle(generation);
        List<LandingPoint> firstGrid = grid(0.0D, 5);
        check(!FallingAnvilVolleyPolicy.start(idle, generation, 11, 8, firstGrid,
                        100L, false, true).accepted(),
                "the anvil sequence cannot start outside the active wave");
        check(!FallingAnvilVolleyPolicy.start(idle, generation, 11, 8, firstGrid,
                        100L, true, false).accepted(),
                "the anvil sequence cannot overlap the shared major-spell cast");
        check(!FallingAnvilVolleyPolicy.start(idle, generation, 11, 8, grid(0.0D, 4),
                        100L, true, true).accepted(),
                "11–20 player casts require the complete 5x5 volley");
        check(!FallingAnvilVolleyPolicy.start(idle, generation, 11, 8, firstGrid,
                        Long.MAX_VALUE - 20L, true, true).accepted(),
                "warning deadline overflow fails closed");

        var started = FallingAnvilVolleyPolicy.start(idle, generation, 11, 8, firstGrid,
                100L, true, true);
        check(started.accepted(), "eleven participants start the two-volley sequence");
        State state = started.state();
        check(state.phase() == Phase.WARNING && state.volleyIndex() == 1
                        && state.totalVolleys() == 2 && state.activeVisuals() == 25
                        && state.deadlineTick() == 160L,
                "first 5x5 volley begins with a 60-tick (three-second) warning");
        check(state.landingPositions().equals(firstGrid),
                "the first warning freezes the selected landing positions");
        check(FallingAnvilVolleyPolicy.tick(state, generation - 1L, 160L).equals(state),
                "stale event generations cannot advance the anvil sequence");
        state = FallingAnvilVolleyPolicy.tick(state, generation, 159L);
        check(state.phase() == Phase.WARNING,
                "the first impact cannot occur before all 60 warning ticks");
        state = FallingAnvilVolleyPolicy.tick(state, generation, 160L);
        check(state.phase() == Phase.IMPACT && state.activeVisuals() == 25,
                "warning expiry requests the impact while keeping at most 25 visuals");
        check(FallingAnvilVolleyPolicy.tick(state, generation, 161L).equals(state),
                "impact remains pending until the adapter confirms it resolved");
        check(FallingAnvilVolleyPolicy.impactResolved(state, generation, 159L).equals(state),
                "an impact cannot resolve before the impact phase begins");

        state = FallingAnvilVolleyPolicy.impactResolved(state, generation, 160L);
        check(state.phase() == Phase.INTERMISSION && state.activeVisuals() == 0
                        && state.deadlineTick() == 180L,
                "the first impact clears visuals and starts a one-second intermission");
        check(FallingAnvilVolleyPolicy.beginNextVolley(state, generation, 7,
                        grid(20.0D, 5), 179L).state().equals(state),
                "volley two cannot begin before the full post-impact second");
        List<LandingPoint> secondGrid = grid(20.0D, 5);
        var secondStart = FallingAnvilVolleyPolicy.beginNextVolley(
                state, generation, 7, secondGrid, 180L);
        check(secondStart.accepted(), "the next cluster starts only after confirmed impact plus 20 ticks");
        state = secondStart.state();
        check(state.phase() == Phase.WARNING && state.volleyIndex() == 2
                        && state.totalVolleys() == 2 && state.activeVisuals() == 25
                        && state.deadlineTick() == 240L
                        && state.landingPositions().equals(secondGrid),
                "volley two locks a fresh 5x5 cluster and gets its own three-second warning");
        state = FallingAnvilVolleyPolicy.tick(state, generation, 240L);
        check(state.phase() == Phase.IMPACT && state.volleyIndex() == 2,
                "volley two impacts after its complete warning");
        state = FallingAnvilVolleyPolicy.impactResolved(state, generation, 240L);
        check(state.phase() == Phase.COMPLETE && state.activeVisuals() == 0,
                "the completed second impact clears visuals and ends the sequence");

        State oneVolley = FallingAnvilVolleyPolicy.start(
                FallingAnvilVolleyPolicy.idle(generation), generation, 10, 3,
                groupedGrids(0.0D, 3), 200L, true, true).state();
        check(oneVolley.totalVolleys() == 1 && oneVolley.activeVisuals() == 27,
                "the ten-player case keeps one volley with nine visuals per eligible target");
        oneVolley = FallingAnvilVolleyPolicy.tick(oneVolley, generation, 260L);
        oneVolley = FallingAnvilVolleyPolicy.impactResolved(oneVolley, generation, 260L);
        check(oneVolley.phase() == Phase.COMPLETE && oneVolley.volleyIndex() == 1,
                "the single-volley case completes directly after its impact");

        check(FallingAnvilVolleyPolicy.clear(state, generation).phase() == Phase.IDLE,
                "cleanup returns the anvil policy to idle");
        check(FallingAnvilVolleyPolicy.clear(state, generation - 1L).equals(state),
                "stale cleanup cannot clear a newer generation");
        System.out.println("FallingAnvilVolleyPolicyTest OK");
    }

    private static List<LandingPoint> grid(double origin, int side) {
        List<LandingPoint> points = new ArrayList<>(side * side);
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                points.add(new LandingPoint(origin + x, 70.0D, origin + z));
            }
        }
        return List.copyOf(points);
    }

    private static List<LandingPoint> groupedGrids(double origin, int groups) {
        List<LandingPoint> points = new ArrayList<>(groups * 9);
        for (int group = 0; group < groups; group++) {
            points.addAll(grid(origin + group * 4.0D, 3));
        }
        return List.copyOf(points);
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
