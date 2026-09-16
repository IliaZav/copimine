import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.domain.RitualSphereEncounterSnapshot;

public final class RitualSphereEncounterSnapshotTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.randomUUID();
        RitualSphereEncounterPolicy.State original = new RitualSphereEncounterPolicy.State(
                9L, prisoner,
                me.copimine.endevent.domain.RitualSphereScalingPolicy.forPlayers(13),
                4, 4, 123_456L);
        Map<String, String> encoded = RitualSphereEncounterSnapshot.encode(original);
        RitualSphereEncounterPolicy.State restored = RitualSphereEncounterSnapshot
                .decode(encoded, 9L).state();
        check(restored != null, "state must decode");
        check(restored.generation() == 9L, "generation must round-trip");
        check(restored.prisoner().equals(prisoner), "prisoner must round-trip");
        check(restored.profile().participants() == 13, "participant count must round-trip");
        check(restored.successfulDrains() == 4 && restored.intensity() == 4,
                "drain intensity must round-trip");
        check(restored.lastDrainMillis() == 123_456L, "drain timestamp must round-trip");
        check(RitualSphereEncounterSnapshot.decode(Map.of(), 9L).state() == null,
                "missing state must decode as absent");
        expectFailure(() -> RitualSphereEncounterSnapshot.decode(encoded, 8L),
                "generation mismatch must fail closed");
        Map<String, String> unknownField = new java.util.LinkedHashMap<>(encoded);
        unknownField.put("ritual-sphere.unknown", "1");
        expectFailure(() -> RitualSphereEncounterSnapshot.decode(unknownField, 9L),
                "unknown ritual fields must fail closed");
        Map<String, String> invalidParticipants = new java.util.LinkedHashMap<>(encoded);
        invalidParticipants.put("ritual-sphere.participants", "1");
        expectFailure(() -> RitualSphereEncounterSnapshot.decode(invalidParticipants, 9L),
                "participant count outside the live range must fail closed");
        Map<String, String> invalidIntensity = new java.util.LinkedHashMap<>(encoded);
        invalidIntensity.put("ritual-sphere.intensity", "0");
        expectFailure(() -> RitualSphereEncounterSnapshot.decode(invalidIntensity, 9L),
                "intensity must match successful drains");
        System.out.println("RitualSphereEncounterSnapshotTest OK");
    }

    private static void expectFailure(Runnable action, String message) {
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
