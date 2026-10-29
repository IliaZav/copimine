import java.util.List;
import java.util.Map;

import me.copimine.endevent.domain.Wave4ObeliskRecoveryPolicy;
import me.copimine.endevent.domain.Wave4ObeliskRecoverySnapshot;

/**
 * Canon 11: Wave 4 must persist distinct obelisk HP receipts, and a restart
 * must never read a checkpoint that belongs to another event generation.
 */
public final class Wave4ObeliskRecoverySnapshotTest {
    public static void main(String[] args) {
        String eventId = "event-a";
        Wave4ObeliskRecoverySnapshot.Data data = new Wave4ObeliskRecoverySnapshot.Data(
                eventId, 7L,
                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "ACTIVE"),
                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 0, 3, "DESTROYED")),
                false, true, 2);
        Map<String, String> encoded = data.encode();
        require(Wave4ObeliskRecoverySnapshot.hasCheckpoint(encoded),
                "an encoded checkpoint must be recognized by its prefix");
        Wave4ObeliskRecoverySnapshot.Data decoded =
                Wave4ObeliskRecoverySnapshot.decode(encoded, eventId, 7L);
        require(decoded.generation() == 7L, "the generation must survive the round trip");
        require(decoded.obelisks().size() == 2, "both obelisk receipts must survive the round trip");
        require(decoded.obelisks().get(0).health() == 2
                        && decoded.obelisks().get(0).maxHealth() == 3
                        && "ACTIVE".equals(decoded.obelisks().get(0).stage()),
                "a damaged obelisk must keep its distinct HP receipt");
        require(decoded.obelisks().get(1).health() == 0,
                "a destroyed obelisk must stay destroyed across a restart");
        require("2".equals(encoded.get(Wave4ObeliskRecoverySnapshot.PREFIX + "version")),
                "Wave 4 checkpoints must include the restart-timing schema");
        require(decoded.mobsStarted() && !decoded.assaultComplete()
                        && decoded.pressurePackAttempts() == 2,
                "wave counters must survive the round trip");

        Wave4ObeliskRecoverySnapshot.ObeliskReceipt emergence =
                new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 3, 3,
                        "EMERGE_CORE", 35L, 0L, 0L, 0L);
        Map<String, String> timingEncoded = new Wave4ObeliskRecoverySnapshot.Data(
                eventId, 7L, List.of(emergence), false, false, 0).encode();
        Wave4ObeliskRecoverySnapshot.ObeliskReceipt timingDecoded =
                Wave4ObeliskRecoverySnapshot.decode(timingEncoded, eventId, 7L).obelisks().get(0);
        require(timingDecoded.emergenceElapsedTicks() == 35L,
                "elapsed emergence ticks must survive checkpoint encoding");
        Wave4ObeliskRecoveryPolicy.RestoredTiming emergenceTiming =
                Wave4ObeliskRecoveryPolicy.restoreTiming(3L, timingDecoded);
        require(emergenceTiming.emergenceStartTick() == -32L
                        && emergenceTiming.stageStartedTick() == -2L
                        && emergenceTiming.activationTick() == 38L,
                "a reset event tick counter must resume the exact emergence and activation countdowns");

        Wave4ObeliskRecoverySnapshot.ObeliskReceipt activeTimingReceipt =
                new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(
                        0, 2, 3, "ACTIVE", 70L, 9L, 4L, 0L);
        Wave4ObeliskRecoveryPolicy.RestoredTiming activeTiming =
                Wave4ObeliskRecoveryPolicy.restoreTiming(3L, activeTimingReceipt);
        require(activeTiming.nextPulseTick() == 12L && activeTiming.nextFireTick() == 7L,
                "active pulse and fire cooldowns must resume from their remaining ticks");
        Wave4ObeliskRecoverySnapshot.ObeliskReceipt collapseTimingReceipt =
                new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(
                        0, 0, 3, "COLLAPSING", 70L, 0L, 0L, 3L);
        Wave4ObeliskRecoveryPolicy.RestoredTiming collapseTiming =
                Wave4ObeliskRecoveryPolicy.restoreTiming(3L, collapseTimingReceipt);
        require(collapseTiming.destroyAtTick() == 6L,
                "collapse must resume only its remaining animation ticks");

        Wave4ObeliskRecoverySnapshot.Data activeAndDestroyed =
                new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                        List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "ACTIVE"),
                                new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 0, 3, "DESTROYED"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 0, 3, "COLLAPSING")),
                        false, true, 2);
        Map<Integer, Wave4ObeliskRecoverySnapshot.ObeliskReceipt> reconciled =
                Wave4ObeliskRecoveryPolicy.reconcile(activeAndDestroyed, 3, 3,
                        java.util.Set.of("ACTIVE", "COLLAPSING"));
        require(reconciled.size() == 3 && reconciled.get(1).health() == 0
                        && "DESTROYED".equals(reconciled.get(1).stage()),
                "reconciliation must preserve each ordinal, including completed destructions");

        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 1, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "a missing ordinal must not be respawned at full health");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 1, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 1, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "duplicate ordinals must not select one ambiguous health receipt");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 1, 4, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 1, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "a changed max-health profile must require recovery instead of healing");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 1, 3, "UNKNOWN_STAGE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 1, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "an unknown stage must retain the checkpoint and fail closed");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 0, 3, "DESTROYED"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 0, 3, "DESTROYED"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 0, 3, "DESTROYED")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "an all-destroyed checkpoint must not silently lose its completion receipt");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 0, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 1, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 1, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "an active obelisk with zero health must be in its collapse stage");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 1, 3, "COLLAPSING"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 1, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 1, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "a collapsing obelisk must not be restored with health remaining");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 2, 3, "EMERGE_CORE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 3, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 3, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING", "EMERGE_CORE")),
                "a pre-active obelisk cannot carry combat damage");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(
                                        0, 3, 3, "EMERGE_CORE", 40L, 0L, 0L, 0L)),
                                false, false, 0),
                        1, 3, java.util.Set.of("EMERGE_CORE")),
                "a stage/timer mismatch must fail closed rather than replay an incorrect stage");
        expectIllegal(() -> Wave4ObeliskRecoveryPolicy.reconcile(
                        new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L,
                                List.of(new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(
                                                0, 0, 3, "DESTROYED", 1L, 0L, 0L, 0L),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(1, 2, 3, "ACTIVE"),
                                        new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(2, 2, 3, "ACTIVE")),
                                false, true, 2),
                        3, 3, java.util.Set.of("ACTIVE", "COLLAPSING")),
                "destroyed obelisks must not retain emergence or combat timers");

        require(!Wave4ObeliskRecoverySnapshot.hasCheckpoint(Map.of()),
                "an unrelated snapshot must not look like a Wave 4 checkpoint");
        require(Wave4ObeliskRecoverySnapshot.extract(Map.of("other", "1")).isEmpty(),
                "extract must keep only Wave 4 keys");

        // A checkpoint from another generation or event must be refused, not applied.
        expectIllegal(() -> Wave4ObeliskRecoverySnapshot.decode(encoded, eventId, 8L),
                "a stale generation must be refused");
        expectIllegal(() -> Wave4ObeliskRecoverySnapshot.decode(encoded, "event-b", 7L),
                "a checkpoint from another event must be refused");
        Map<String, String> legacy = new java.util.LinkedHashMap<>(encoded);
        legacy.put(Wave4ObeliskRecoverySnapshot.PREFIX + "version", "1");
        expectIllegal(() -> Wave4ObeliskRecoverySnapshot.decode(legacy, eventId, 7L),
                "legacy checkpoints without timers must fail closed instead of silently resetting the encounter");
        require(Wave4ObeliskRecoverySnapshot.extract(legacy).equals(legacy),
                "a legacy checkpoint refused for manual recovery must remain intact");
        expectIllegal(() -> Wave4ObeliskRecoverySnapshot.decode(Map.of(), eventId, 7L),
                "a missing checkpoint must be refused");

        // Out-of-bounds receipts are rejected at construction.
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 4, 3, "ACTIVE"),
                "health above maximum must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, -1, 3, "ACTIVE"),
                "negative health must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 1, 0, "ACTIVE"),
                "a zero maximum must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(0, 1, 3, " "),
                "a blank stage must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(
                        0, 3, 3, "EMERGE_CORE", 71L, 0L, 0L, 0L),
                "an emergence timer beyond the stage timeline must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.ObeliskReceipt(
                        0, 0, 3, "COLLAPSING", 70L, 0L, 0L, 9L),
                "a collapse timer beyond the animation duration must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.Data("", 7L, List.of(), false, false, 0),
                "a blank event id must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.Data(eventId, 0L, List.of(), false, false, 0),
                "a non-positive generation must be refused");
        expectIllegal(() -> new Wave4ObeliskRecoverySnapshot.Data(eventId, 7L, List.of(), false, false, -1),
                "a negative attempt count must be refused");

        // A corrupted field must fail closed rather than decode as a healthy obelisk.
        Map<String, String> corrupted = new java.util.LinkedHashMap<>(encoded);
        corrupted.put(Wave4ObeliskRecoverySnapshot.PREFIX + "0.health", "not-a-number");
        expectIllegal(() -> Wave4ObeliskRecoverySnapshot.decode(corrupted, eventId, 7L),
                "an unparseable health value must be refused");

        System.out.println("Wave4ObeliskRecoverySnapshotTest OK");
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private Wave4ObeliskRecoverySnapshotTest() {
    }
}
