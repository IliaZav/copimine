import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.WaveTwoEntityRecoveryPolicy;

public final class WaveTwoEntityRecoveryPolicyTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void rejects(String encoded, int cap, String message) {
        boolean rejected = false;
        try {
            WaveTwoEntityRecoveryPolicy.decode(encoded, cap);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    public static void main(String[] args) {
        check(WaveTwoEntityRecoveryPolicy.shouldSuspendRuntimeAi(true, true, false),
                "Wave 2 entities stay suspended while the encounter requires recovery");
        check(WaveTwoEntityRecoveryPolicy.shouldSuspendRuntimeAi(true, false, true),
                "an explicitly fenced entity stays suspended until valid rehydration");
        check(!WaveTwoEntityRecoveryPolicy.shouldSuspendRuntimeAi(true, false, false),
                "a valid active Wave 2 entity may restore its AI");
        check(!WaveTwoEntityRecoveryPolicy.shouldSuspendRuntimeAi(false, true, false),
                "the Wave 2 recovery fence does not suspend unrelated waves");
        check("RECOVERY_REQUIRED".equals(
                        WaveTwoEntityRecoveryPolicy.phaseForCleanupFenceSnapshot(
                                "WAVE_2", "WAVE_TWO_CLEANUP_FENCE_REQUIRED")),
                "the cleanup-fence checkpoint durably closes Wave 2 before runtime transition");
        check("WAVE_2".equals(
                        WaveTwoEntityRecoveryPolicy.phaseForCleanupFenceSnapshot(
                                "WAVE_2", "OTHER_RECOVERY_REASON")),
                "an unrelated recovery reason does not rewrite a configured Wave 2 phase");
        check("WAVE_1".equals(
                        WaveTwoEntityRecoveryPolicy.phaseForCleanupFenceSnapshot(
                                "WAVE_1", "WAVE_TWO_CLEANUP_FENCE_REQUIRED")),
                "the Wave 2 cleanup intent does not alter another phase");
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Set<UUID> roster = new LinkedHashSet<>(List.of(second, first));
        String encoded = WaveTwoEntityRecoveryPolicy.encode(roster,
                WaveTwoEntityRecoveryPolicy.MAX_OUTSTANDING_UUIDS);
        check(encoded.equals(first + "," + second), "UUID roster encoding is deterministic");
        check(WaveTwoEntityRecoveryPolicy.decode(encoded,
                WaveTwoEntityRecoveryPolicy.MAX_OUTSTANDING_UUIDS).equals(Set.of(first, second)),
                "durable roster round-trips across restart");
        rejects(first + "," + first, 4, "duplicate persisted UUIDs are rejected");
        rejects("00000000-0000-0000-0000-00000000000g", 4, "malformed UUID is rejected");
        rejects("00000000-0000-0000-0000-000000000001,", 4, "empty roster entries are rejected");
        rejects(first + "," + second, 1, "overflow roster is rejected before mutation");

        Set<UUID> live = Set.of(first, second);
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        check(WaveTwoEntityRecoveryPolicy.coordinatorRoster(
                        List.of(first, third), Set.of(first, second, third))
                        .equals(List.of(first, second, third)),
                "the coordinator roster restores unloaded outstanding fighters alongside loaded mobs");
        check(WaveTwoEntityRecoveryPolicy.afterConfirmedDeath(live, first).equals(Set.of(second)),
                "confirmed death retires exactly one outstanding UUID");
        check(WaveTwoEntityRecoveryPolicy.afterConfirmedDeath(live,
                UUID.fromString("00000000-0000-0000-0000-000000000003")).equals(live),
                "duplicate or unknown death cannot decrement the roster");
        check(!WaveTwoEntityRecoveryPolicy.groupDefeated(live),
                "unloaded UUID remains outstanding until a confirmed death");

        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false, 8, 8, 2, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.REHYDRATE,
                "current outstanding member rehydrates");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false, 8, 8, 1, 2,
                true, false, false, false)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "stale prior-group load is removed");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false, 7, 8, 2, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "stale-generation load is removed");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, true, 8, 8, 2, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "cleanup tombstone prevents a reset mob from being resurrected");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, true, 8, 8, 2, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "exact cleanup tombstone removes a stale W2 mob after event ID rotation");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, false, 8, 8, 2, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.IGNORE,
                "a foreign-event W2 mob without a cleanup tombstone remains untouched");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, false, 7, 8, 2, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.IGNORE,
                "a foreign-event W2 mob remains untouched even when its generation is stale");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false,
                        4L, 4L, 8L, 8L,
                        2, 2, true, false, false, true)
                        == WaveTwoEntityRecoveryPolicy.LoadDisposition.REHYDRATE,
                "a current cleanup-epoch member still rehydrates");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, false,
                        3L, 4L, 8L, 8L,
                        1, 1, false, false, true, false)
                        == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "a stale cleanup epoch is removed before event-ID ownership after Core rotation");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false,
                        5L, 4L, 8L, 8L,
                        2, 2, true, false, false, true)
                        == WaveTwoEntityRecoveryPolicy.LoadDisposition.RECOVERY_REQUIRED,
                "a future cleanup epoch fails closed for recovery");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, false,
                        0L, 1L, 8L, 8L,
                        1, 1, false, false, true, false)
                        == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "legacy entities default to epoch zero and are removed after a durable fence");
        check(WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 8L, 8L,
                        3, 3, 1L, true, false, false, true),
                "an outstanding active group-three legacy elite can adopt the durable epoch");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        false, true, true, false, 8L, 8L,
                        3, 3, 1L, true, false, false, true),
                "only the explicitly marked final elite can migrate a missing epoch");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, false, true, false, 8L, 8L,
                        3, 3, 1L, true, false, false, true),
                "an entity that already carries an epoch cannot use the legacy migration");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, true, 8L, 8L,
                        3, 3, 1L, true, false, false, true),
                "cleanup tombstones always override legacy epoch migration");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 7L, 8L,
                        3, 3, 1L, true, false, false, true),
                "legacy epoch migration rejects a stale generation");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 8L, 8L,
                        2, 3, 1L, true, false, false, true),
                "legacy epoch migration rejects an earlier group");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 8L, 8L,
                        3, 3, 1L, false, false, false, true),
                "legacy epoch migration only applies to the active Wave2");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 8L, 8L,
                        3, 3, 1L, true, false, true, true),
                "legacy epoch migration fails closed during recovery");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 8L, 8L,
                        3, 3, 1L, true, false, false, false),
                "legacy epoch migration requires the UUID in the outstanding roster");
        check(!WaveTwoEntityRecoveryPolicy.canMigrateLegacyFinalEliteEpoch(
                        true, true, true, false, 8L, 8L,
                        3, 2, 1L, true, false, false, true),
                "legacy epoch migration requires the active third group");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, false,
                        4L, 4L, 8L, 8L,
                        2, 2, true, false, false, false)
                        == WaveTwoEntityRecoveryPolicy.LoadDisposition.IGNORE,
                "a foreign event at the current cleanup epoch remains untouched");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, true,
                        4L, 4L, 8L, 8L,
                        2, 2, true, false, false, false)
                        == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                "a durable legacy UUID tombstone remains a removal instruction");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false, 8, 8, 2, 2,
                true, false, false, false)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.RECOVERY_REQUIRED,
                "unlisted current-group entity cannot be accepted as a newcomer");
        check(WaveTwoEntityRecoveryPolicy.classifyLoad(true, false, 8, 8, 3, 2,
                true, false, false, true)
                == WaveTwoEntityRecoveryPolicy.LoadDisposition.RECOVERY_REQUIRED,
                "future-group entity fails closed");

        Set<UUID> archived = WaveTwoEntityRecoveryPolicy.archiveForCleanup(Set.of(first), Set.of(second));
        check(archived.equals(Set.of(first, second)), "explicit cleanup preserves UUID tombstones");
        boolean cleanupOverflowRejected = false;
        try {
            Set<UUID> tooMany = new LinkedHashSet<>();
            for (int index = 0; index <= WaveTwoEntityRecoveryPolicy.MAX_CLEANUP_UUIDS; index++) {
                tooMany.add(new UUID(1L, index));
            }
            WaveTwoEntityRecoveryPolicy.archiveForCleanup(Set.of(), tooMany);
        } catch (IllegalArgumentException expected) {
            cleanupOverflowRejected = true;
        }
        check(cleanupOverflowRejected, "cleanup roster overflow is rejected");

        long cleanupEpoch = 0L;
        for (int cycle = 0; cycle < 1000; cycle++) {
            long previousEpoch = cleanupEpoch;
            cleanupEpoch = WaveTwoEntityRecoveryPolicy.nextCleanupEpoch(cleanupEpoch);
            check(cleanupEpoch == previousEpoch + 1L,
                    "each interrupted-group cleanup advances a durable monotonic epoch");
            check(WaveTwoEntityRecoveryPolicy.classifyLoad(false, false,
                            previousEpoch, cleanupEpoch, 8L, 8L, 1, 1,
                            false, false, true, false)
                            == WaveTwoEntityRecoveryPolicy.LoadDisposition.REMOVE,
                    "an old group is removed after a cleanup epoch even when its event ID rotated");
        }
        boolean cleanupEpochOverflowRejected = false;
        try {
            WaveTwoEntityRecoveryPolicy.nextCleanupEpoch(Long.MAX_VALUE);
        } catch (IllegalArgumentException expected) {
            cleanupEpochOverflowRejected = true;
        }
        check(cleanupEpochOverflowRejected, "cleanup epoch overflow fails closed before cleanup mutation");
        System.out.println("WaveTwoEntityRecoveryPolicyTest OK");
    }
}
