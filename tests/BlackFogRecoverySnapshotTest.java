import me.copimine.endevent.domain.BlackFogRecoverySnapshot;

import java.util.Map;

public final class BlackFogRecoverySnapshotTest {
    public static void main(String[] args) {
        roundTripsEveryInterruptibleStage();
        refusesWrongEventGenerationAndMalformedStage();
        System.out.println("BlackFogRecoverySnapshotTest OK");
    }

    private static void roundTripsEveryInterruptibleStage() {
        for (BlackFogRecoverySnapshot.Stage stage : new BlackFogRecoverySnapshot.Stage[]{
                BlackFogRecoverySnapshot.Stage.TELEGRAPH,
                BlackFogRecoverySnapshot.Stage.SAFE,
                BlackFogRecoverySnapshot.Stage.FOG}) {
            BlackFogRecoverySnapshot.Data original = new BlackFogRecoverySnapshot.Data(
                    "event", 4L, 1, stage, 123_456L,
                    2, 2,
                    1, 124_000L);
            Map<String, String> encoded = original.encode();
            BlackFogRecoverySnapshot.Data restored = BlackFogRecoverySnapshot.decode(encoded, "event", 4L);
            check(original.equals(restored), "restart checkpoint must preserve " + stage + " exactly");
            check(BlackFogRecoverySnapshot.requiresAdminRecovery(restored),
                    "transient Wave 5 combat must resume through recovery after restart");
        }
    }

    private static void refusesWrongEventGenerationAndMalformedStage() {
        var checkpoint = new BlackFogRecoverySnapshot.Data("event", 4L, 1,
                BlackFogRecoverySnapshot.Stage.SAFE, 123_456L, 2, 2, 0, 0L).encode();
        expectRejected(() -> BlackFogRecoverySnapshot.decode(checkpoint, "other-event", 4L),
                "another event must not adopt a Wave 5 checkpoint");
        expectRejected(() -> BlackFogRecoverySnapshot.decode(checkpoint, "event", 5L),
                "another generation must not adopt a Wave 5 checkpoint");
        var malformed = new java.util.LinkedHashMap<>(checkpoint);
        malformed.put("wave5-fog.stage", "UNSAFE");
        expectRejected(() -> BlackFogRecoverySnapshot.decode(malformed, "event", 4L),
                "unknown stages must fail closed");
        var incomplete = new java.util.LinkedHashMap<>(checkpoint);
        incomplete.remove("wave5-fog.zone-count");
        expectRejected(() -> BlackFogRecoverySnapshot.decode(incomplete, "event", 4L),
                "incomplete safe-zone geometry must fail closed");
    }

    private static void expectRejected(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected rejection
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
