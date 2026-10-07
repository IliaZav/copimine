import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.RitualSphereEncounterPolicy;
import me.copimine.endevent.domain.RitualSphereEncounterSnapshot;

public final class RitualSphereEncounterSnapshotTest {
    public static void main(String[] args) {
        UUID prisoner = UUID.randomUUID();
        RitualSphereEncounterPolicy.State original = new RitualSphereEncounterPolicy.State(
                9L, prisoner,
                me.copimine.endevent.domain.RitualSphereScalingPolicy.forPlayers(13));
        Map<String, String> encoded = RitualSphereEncounterSnapshot.encode(original);
        RitualSphereEncounterPolicy.State restored = RitualSphereEncounterSnapshot
                .decode(encoded, 9L).state();
        check(restored != null, "state must decode");
        check(restored.generation() == 9L, "generation must round-trip");
        check(restored.prisoner().equals(prisoner), "prisoner must round-trip");
        check(restored.profile().participants() == 13, "participant count must round-trip");
        check(!RitualSphereEncounterSnapshot.decode(encoded, 9L).migratedLegacyState(),
                "current state must not report legacy migration");

        Map<String, String> oldSnapshot = new java.util.LinkedHashMap<>(encoded);
        oldSnapshot.put("ritual-sphere.successful-drains", "not-a-number");
        oldSnapshot.put("ritual-sphere.intensity", "also-not-a-number");
        oldSnapshot.put("ritual-sphere.last-drain-millis", "ignored");
        RitualSphereEncounterSnapshot.Data migrated = RitualSphereEncounterSnapshot.decode(oldSnapshot, 9L);
        check(migrated.migratedLegacyState(), "obsolete drain data must be explicitly migrated");
        check(migrated.state().prisoner().equals(prisoner), "migration preserves prisoner only");
        check(migrated.state().profile().participants() == 13, "migration preserves encounter size");
        check(RitualSphereEncounterSnapshot.encode(migrated.state()).keySet().stream()
                        .noneMatch(key -> key.contains("drain") || key.endsWith("intensity")),
                "migration must never write obsolete values again");

        RitualSphereEncounterPolicy.State waiting = RitualSphereEncounterPolicy.waiting(9L, 13);
        Map<String, String> waitingEncoded = RitualSphereEncounterSnapshot.encode(waiting);
        RitualSphereEncounterPolicy.State waitingRestored = RitualSphereEncounterSnapshot
                .decode(waitingEncoded, 9L).state();
        check(waitingRestored != null && !RitualSphereEncounterPolicy.hasCaptured(waitingRestored),
                "waiting state must round-trip without a prisoner");
        check(waitingRestored.profile().participants() == 13,
                "waiting snapshot must preserve encounter scaling");
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
        Map<String, String> missingParticipantCount = new java.util.LinkedHashMap<>(encoded);
        missingParticipantCount.remove("ritual-sphere.participants");
        expectFailure(() -> RitualSphereEncounterSnapshot.decode(missingParticipantCount, 9L),
                "incomplete current state must fail closed");
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
