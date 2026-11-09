import me.copimine.endevent.domain.BlackFogRecoverySnapshot;

import java.util.Map;

public final class BlackFogRecoverySnapshotTest {
    public static void main(String[] args) {
        roundTripsEveryInterruptibleStage();
        legacyCycleSchemasAreRefusedNotUpgraded();
        fencesOneShotDecisionsAgainstALiveFight();
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
                    1, 124_000L, 1);
            Map<String, String> encoded = original.encode();
            BlackFogRecoverySnapshot.Data restored = BlackFogRecoverySnapshot.decode(encoded, "event", 4L);
            check(original.equals(restored), "restart checkpoint must preserve " + stage + " exactly");
            check(BlackFogRecoverySnapshot.requiresAdminRecovery(restored),
                    "transient Wave 5 combat must resume through recovery after restart");
        }
    }

    private static void legacyCycleSchemasAreRefusedNotUpgraded() {
        // The brief names this failure explicitly: an old checkpoint must never be
        // silently re-interpreted as a new five-to-ten-cycle fight. A three-cycle
        // record predates the roster-frozen target, so it is refused on the schema
        // version and stays visible to an administrator through extract().
        var threeCycle = new java.util.LinkedHashMap<String, String>();
        threeCycle.put("wave5-fog.version", "1");
        threeCycle.put("wave5-fog.event", "event");
        threeCycle.put("wave5-fog.generation", "4");
        threeCycle.put("wave5-fog.cycle", "1");
        threeCycle.put("wave5-fog.stage", "SAFE");
        threeCycle.put("wave5-fog.deadline-millis", "123456");
        threeCycle.put("wave5-fog.zone-count", "2");
        threeCycle.put("wave5-fog.zone-side", "2");
        threeCycle.put("wave5-fog.setup-attempts", "0");
        threeCycle.put("wave5-fog.retry-at-millis", "0");
        // The REASON matters, not just the refusal: a three-cycle record must be
        // turned away by the SCHEMA gate. If the version check were removed, the
        // record would still throw (it has no frozen target), so asserting only
        // "rejected" would pass on a build with no version gate at all. The
        // message is therefore pinned to the version refusal specifically.
        expectRejectedWith(() -> BlackFogRecoverySnapshot.decode(threeCycle, "event", 4L),
                "Unsupported Wave 5 recovery checkpoint version",
                "a legacy three-cycle record must be refused by the schema gate");
        expectRejectedWith(() -> BlackFogRecoverySnapshot.decode(threeCycle, "event", 4L, 10),
                "Unsupported Wave 5 recovery checkpoint version",
                "the schema gate must run even when the live target is known");
        // And the same schema gate must reject a TWO-cycle record too, so the
        // refusal is about the schema and not about one particular old count.
        var twoCycle = new java.util.LinkedHashMap<>(threeCycle);
        twoCycle.put("wave5-fog.version", "2");
        expectRejectedWith(() -> BlackFogRecoverySnapshot.decode(twoCycle, "event", 4L),
                "Unsupported Wave 5 recovery checkpoint version",
                "every pre-current schema must be refused, not just the oldest one");
        // Failing closed must not destroy the evidence: the raw bytes stay readable
        // so an administrator can see WHY recovery was demanded.
        check(BlackFogRecoverySnapshot.hasCheckpoint(threeCycle),
                "a refused legacy record must still be detected as present");
        check(BlackFogRecoverySnapshot.extract(threeCycle).size() >= 4,
                "a refused legacy record must remain extractable for diagnostics");
        check("1".equals(BlackFogRecoverySnapshot.extract(threeCycle).get("wave5-fog.version")),
                "diagnostics must expose the offending schema version itself");
        // A five-cycle record from a SMALL party is legitimate and must round-trip
        // without loss when the live target agrees, so the fence is not blanket.
        var five = new BlackFogRecoverySnapshot.Data("event", 4L, 2,
                BlackFogRecoverySnapshot.Stage.FOG, 900_000L, 3, 3, 2, 905_000L, 2, 5);
        var restored = BlackFogRecoverySnapshot.decode(five.encode(), "event", 4L, 5);
        check(five.equals(restored), "a legitimate live-generation checkpoint must restore without loss");
    }

    private static void fencesOneShotDecisionsAgainstALiveFight() {
        // Point 12: a checkpoint must not be replayed as a DIFFERENT decision.
        // A five-cycle snapshot opened by a ten-cycle fight (or the reverse) is a
        // different encounter, so the decode has to refuse it rather than let the
        // caller keep playing the stored target under a new roster.
        var fiveCycle = new BlackFogRecoverySnapshot.Data("event", 4L, 2,
                BlackFogRecoverySnapshot.Stage.SAFE, 123_456L, 2, 2, 0, 0L, 2, 5);
        check(BlackFogRecoverySnapshot.decode(fiveCycle.encode(), "event", 4L, 5).targetCycles() == 5,
                "a checkpoint must be readable when the live target agrees with it");
        expectRejected(() -> BlackFogRecoverySnapshot.decode(fiveCycle.encode(), "event", 4L, 10),
                "a five-cycle checkpoint must never be adopted by a ten-cycle fight");
        var tenCycle = new BlackFogRecoverySnapshot.Data("event", 4L, 2,
                BlackFogRecoverySnapshot.Stage.SAFE, 123_456L, 2, 2, 0, 0L, 2, 10);
        expectRejected(() -> BlackFogRecoverySnapshot.decode(tenCycle.encode(), "event", 4L, 5),
                "a ten-cycle checkpoint must never be adopted by a five-cycle fight");
        // Zero means "no frozen opinion yet" and must keep the stored target.
        check(BlackFogRecoverySnapshot.decode(tenCycle.encode(), "event", 4L, 0).targetCycles() == 10,
                "a caller without a frozen target keeps the stored one");
        // The fence must fire BEFORE the record is built: the stored layout is
        // itself legal, so only the comparison knows the live target.
        var complete = new BlackFogRecoverySnapshot.Data("event", 4L, 5,
                BlackFogRecoverySnapshot.Stage.COMPLETE, 0L, 0, 0, 0, 0L, 5, 5);
        expectRejected(() -> BlackFogRecoverySnapshot.decode(complete.encode(), "event", 4L, 6),
                "even a completed checkpoint must not satisfy an unrelated target");
    }

    private static void refusesWrongEventGenerationAndMalformedStage() {
        var checkpoint = new BlackFogRecoverySnapshot.Data("event", 4L, 1,
                BlackFogRecoverySnapshot.Stage.SAFE, 123_456L, 2, 2, 0, 0L, 1).encode();
        var legacy = new java.util.LinkedHashMap<>(checkpoint);
        legacy.put("wave5-fog.version", "1");
        legacy.remove("wave5-fog.packs-spawned");
        expectRejected(() -> BlackFogRecoverySnapshot.decode(legacy, "event", 4L),
                "old cycle contracts must enter recovery, never silently become a new fight");
        var missingTarget = new java.util.LinkedHashMap<>(checkpoint);
        missingTarget.remove("wave5-fog.target-cycles");
        expectRejected(() -> BlackFogRecoverySnapshot.decode(missingTarget, "event", 4L),
                "new snapshots require the frozen encounter target");
        var shortComplete = new BlackFogRecoverySnapshot.Data("event", 4L, 5,
                BlackFogRecoverySnapshot.Stage.COMPLETE, 0L, 0, 0, 0, 0L, 5, 5);
        check(BlackFogRecoverySnapshot.decode(shortComplete.encode(), "event", 4L).targetCycles() == 5,
                "small-party completion must round-trip its own target");
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

    /** Rejection must name the specific guard, so a missing guard cannot pass. */
    private static void expectRejectedWith(Runnable action, String reasonFragment, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            String text = expected.getMessage() == null ? "" : expected.getMessage();
            if (!text.contains(reasonFragment)) {
                throw new AssertionError(message + " (wrong guard fired: " + text + ")");
            }
        }
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
