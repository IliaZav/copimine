import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.Wave7AdmissionPolicy;
import me.copimine.endevent.domain.Wave7AdmissionPolicy.Entry;
import me.copimine.endevent.domain.Wave7AdmissionPolicy.Trial;
import me.copimine.endevent.runtime.RealitySplitChamberController.Passage;

public final class Wave7AdmissionPolicyTest {
    private static final UUID A = id(1), B = id(2), C = id(3), D = id(4), E = id(5);
    private static final long GENERATION = 73L;

    public static void main(String[] args) {
        var assignment = new ChamberIsolationPolicy.Assignment(4,
                Map.of(A, 0, B, 1, C, 2, D, 3, E, 1));
        var state = new Wave7AdmissionPolicy.State(GENERATION, assignment,
                Map.of(0, Trial.WARDEN, 1, Trial.ECHO, 2, Trial.MARKSMAN, 3, Trial.ARCHMAGE),
                Set.of(0, 3), Set.of(), Map.of(2, Set.of(A, D)),
                Map.of(A, 0, B, 1, C, 2, D, 3, E, 1),
                Set.of(new Passage(0, 3), new Passage(2, 3)));
        check(Wave7AdmissionPolicy.mayEnter(state, GENERATION, B, 1, Entry.INITIAL),
                "original owner can begin the private duel");
        check(Wave7AdmissionPolicy.mayEnter(state, GENERATION, B, 1, Entry.OWNER_RETURN),
                "unfinished owner can return to the same claim");
        check(!Wave7AdmissionPolicy.mayEnter(state, GENERATION, A, 1, Entry.HELPER_INTERACTION),
                "completed ordinary owner cannot help Echo directly");
        check(!Wave7AdmissionPolicy.mayEnter(state, GENERATION, D, 1, Entry.WALK),
                "ordinary passage connectivity cannot transit into Echo");
        check(Wave7AdmissionPolicy.mayEnter(state, GENERATION, A, 2, Entry.WALK),
                "explicit helper can reach an ordinary trial through completed ordinary space");
        check(!Wave7AdmissionPolicy.mayEnter(state, GENERATION + 1, A, 2, Entry.WALK),
                "stale generation never grants entry");
        var absentOwner = replacePhysical(state, Map.of(A, 0, B, 1, C, -1, D, 3, E, 1));
        check(!Wave7AdmissionPolicy.mayTarget(absentOwner, GENERATION, 2, 2, null, C),
                "original assignment is not physical presence in a live fight");
        check(Wave7AdmissionPolicy.mayTarget(state, GENERATION, 2, 2, null, A),
                "Marksman may pursue an admitted helper into connected completed ordinary space");
        var inFight = replacePhysical(state, Map.of(A, 2, B, 1, C, 2, D, 3, E, 1));
        check(Wave7AdmissionPolicy.mayTarget(inFight, GENERATION, 2, 2, null, A),
                "admitted helper physically inside the fight is a valid target");
        check(Wave7AdmissionPolicy.mayTarget(state, GENERATION, 1, 1, B, B),
                "Echo targets its own unfinished owner");
        check(!Wave7AdmissionPolicy.mayTarget(state, GENERATION, 1, 1, B, E),
                "two owners sharing Echo space cannot target each other");
        check(!Wave7AdmissionPolicy.mayInteract(state, GENERATION, B, E),
                "foreign healing, aura and interaction cannot affect a private duel");
        var won = new Wave7AdmissionPolicy.State(GENERATION, assignment, state.trials(),
                state.completedRooms(), Set.of(B), state.helpers(), state.physicalRooms(), state.passages());
        check(Wave7AdmissionPolicy.mayExitEcho(won, GENERATION, B),
                "completed personal owner gets one-way exit before the whole room clears");
        check(!Wave7AdmissionPolicy.mayEnter(won, GENERATION, B, 1, Entry.OWNER_RETURN),
                "completed owner cannot re-enter through the return exception");
        check(!Wave7AdmissionPolicy.mayTarget(won, GENERATION, 1, 1, B, B),
                "completed duel cannot resume hostile targeting");
        check(!Wave7AdmissionPolicy.mayExitEcho(won, GENERATION, E),
                "one owner's result cannot win another owner's duel");
        check(!Wave7AdmissionPolicy.mayMoveActor(state, GENERATION, 2, 1),
                "Marksman never expands into private Echo");
        check(Wave7AdmissionPolicy.mayMoveActor(state, GENERATION, 2, 3),
                "Marksman can use physically connected completed ordinary region");
        check(!Wave7AdmissionPolicy.mayMoveActor(state, GENERATION, 0, 3),
                "Warden cannot acquire the Marksman's expansion rule");
        var closedScope = new Wave7AdmissionPolicy.AttackScope(GENERATION, 2, null, Set.of(2));
        check(!Wave7AdmissionPolicy.mayResolveAttack(state, closedScope, 2, D),
                "old projectile cannot acquire a later opened region");
        var outside = replacePhysical(inFight, Map.of(A, -1, B, 1, C, 2, D, 3, E, 1));
        check(!Wave7AdmissionPolicy.mayTarget(outside, GENERATION, 2, 2, null, A),
                "neutral staging is not a shooting or target platform");
        System.out.println("Wave7AdmissionPolicyTest OK");
    }

    private static Wave7AdmissionPolicy.State replacePhysical(Wave7AdmissionPolicy.State state,
                                                               Map<UUID, Integer> physical) {
        return new Wave7AdmissionPolicy.State(state.generation(), state.assignment(), state.trials(),
                state.completedRooms(), state.completedEchoOwners(), state.helpers(), physical, state.passages());
    }
    private static UUID id(int value) { return new UUID(0L, value); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
