import me.copimine.endevent.domain.CollapseRingEncounterPolicy;
import me.copimine.endevent.domain.CollapseRingEncounterSnapshot;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CollapseRingEncounterSnapshotTest {
    public static void main(String[] args) {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CollapseRingEncounterPolicy.State state = CollapseRingEncounterPolicy.guardDown(
                CollapseRingEncounterPolicy.initial(42L, 1, first, second, 700L),
                42L, first, 900L);
        state = new CollapseRingEncounterPolicy.State(state.generation(), state.roomId(),
                state.guardA(), state.guardB(), state.firstDownGuardId(), state.firstDownTick(),
                state.killWindowDeadlineTick(), state.reviveHealthFraction(), state.phase(),
                state.rotationStartedTick(), Set.of(first, second));
        Map<String, String> encoded = CollapseRingEncounterSnapshot.encode(1, state);

        CollapseRingEncounterSnapshot.Data decoded = CollapseRingEncounterSnapshot.decode(encoded, 42L);
        check(decoded.completedRings() == 1, "completed ring count must survive encoding");
        check(decoded.encounter() != null, "active ring encounter must survive encoding");
        check(decoded.encounter().phase() == CollapseRingEncounterPolicy.Phase.FIRST_DOWN,
                "pair phase must survive encoding");
        check(decoded.encounter().guardA().equals(first)
                        && decoded.encounter().guardB().equals(second),
                "guard identities must survive encoding");
        check(decoded.encounter().firstDownTick() == 900L
                        && decoded.encounter().killWindowDeadlineTick() == 1_100L,
                "local coordination timer must survive encoding");
        check(decoded.encounter().rotationStartedTick() == 700L,
                "local rotation origin must survive encoding");
        check(decoded.encounter().assignedPlayers().equals(Set.of(first, second)),
                "participant pair must survive encoding");

        boolean rejected = false;
        try {
            CollapseRingEncounterSnapshot.decode(
                    Map.of("collapse-rings.completed", "1",
                            "collapse-rings.state.generation", "41"), 42L);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "a stale or partial encounter snapshot must fail closed");
        System.out.println("CollapseRingEncounterSnapshotTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
