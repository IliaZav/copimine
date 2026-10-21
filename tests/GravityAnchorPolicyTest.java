import java.util.UUID;
import me.copimine.endevent.domain.GravityAnchorPolicy;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.GravityAnchorPolicy.Phase;
import me.copimine.endevent.domain.GravityAnchorPolicy.State;

public final class GravityAnchorPolicyTest {
    public static void main(String[] args) {
        long generation = 42L;
        UUID target = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID crystal = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AnchorPoint captured = new AnchorPoint(10.5D, 69.0D, -3.5D);
        State idle = GravityAnchorPolicy.idle(generation);

        check(!GravityAnchorPolicy.start(idle, generation, target, captured,
                        false, true, true).accepted(),
                "Gravity Anchor cannot start outside the active Wave 6");
        check(!GravityAnchorPolicy.start(idle, generation, target, captured,
                        true, false, true).accepted(),
                "Gravity Anchor must fail closed for an ineligible or prisoner target");
        check(!GravityAnchorPolicy.start(idle, generation, target, captured,
                        true, true, false).accepted(),
                "Gravity Anchor cannot overlap another shared major spell");
        check(!GravityAnchorPolicy.start(idle, generation - 1L, target, captured,
                        true, true, true).accepted(),
                "stale event generations cannot start a Gravity Anchor");

        var started = GravityAnchorPolicy.start(idle, generation, target, captured,
                true, true, true);
        check(started.accepted(), "eligible captured target starts the Gravity Anchor");
        State state = started.state();
        check(state.phase() == Phase.TELEGRAPH, "cast begins in the target warning phase");
        check(state.targetPlayer().equals(target) && state.anchor().equals(captured),
                "cast captures one target identity and its ground anchor");
        check(!GravityAnchorPolicy.start(state, generation,
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        new AnchorPoint(20.5D, 69.0D, 9.5D), true, true, true).accepted(),
                "a second Gravity Anchor cannot replace the active target");

        check(GravityAnchorPolicy.advanceTo(state, generation, Phase.EXPLOSION).equals(state),
                "the cast cannot skip crystal anchoring before explosion");
        check(GravityAnchorPolicy.advanceTo(state, generation - 1L, Phase.PULL).equals(state),
                "stale generations cannot advance Gravity Anchor phases");
        check(GravityAnchorPolicy.anchorSpawned(state, generation, null).equals(state),
                "an absent crystal identity cannot advance past telegraph");
        state = GravityAnchorPolicy.anchorSpawned(state, generation, crystal);
        check(state.phase() == Phase.ANCHOR && state.crystalEntity().equals(crystal),
                "the anchor phase binds the one created End Crystal identity");
        state = advance(state, generation, Phase.PULL);
        state = advance(state, generation, Phase.EXPLOSION);
        state = advance(state, generation, Phase.LANDING);
        state = advance(state, generation, Phase.COMPLETE);
        check(state.targetPlayer().equals(target) && state.anchor().equals(captured),
                "target and anchor remain stable through pull, explosion, and landing");
        check(state.crystalEntity().equals(crystal),
                "the owned End Crystal identity remains available through cleanup");
        check(GravityAnchorPolicy.advanceTo(state, generation, Phase.TELEGRAPH).equals(state),
                "a completed cast cannot restart without a new start decision");

        State cleared = GravityAnchorPolicy.clear(state, generation);
        check(cleared.phase() == Phase.IDLE && cleared.targetPlayer() == null
                        && cleared.anchor() == null && cleared.crystalEntity() == null,
                "cleanup clears target ownership and anchor coordinates");
        check(GravityAnchorPolicy.clear(state, generation - 1L).equals(state),
                "stale cleanup cannot clear a newer generation");

        expectIllegalArgument(() -> new AnchorPoint(Double.NaN, 0.0D, 0.0D),
                "non-finite anchor coordinates are rejected");
        System.out.println("GravityAnchorPolicyTest OK");
    }

    private static State advance(State state, long generation, Phase phase) {
        State advanced = GravityAnchorPolicy.advanceTo(state, generation, phase);
        check(advanced.phase() == phase, "expected Gravity Anchor phase " + phase);
        check(advanced.targetPlayer().equals(state.targetPlayer())
                        && advanced.anchor().equals(state.anchor())
                        && java.util.Objects.equals(advanced.crystalEntity(), state.crystalEntity()),
                "phase changes preserve the captured target");
        return advanced;
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
