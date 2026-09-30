import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxProxyMetadataPolicy;

import java.util.UUID;

public final class BossHitboxProxyMetadataPolicyTest {
    public static void main(String[] args) {
        UUID boss = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherBoss = UUID.fromString("00000000-0000-0000-0000-000000000002");
        BossHitboxProxyMetadataPolicy.Expected expected =
                new BossHitboxProxyMetadataPolicy.Expected(
                        "BOSS_HITBOX", "event-1", boss, 7L,
                        BossHitboxProfile.PartId.LEFT_FOREARM, 0);

        BossHitboxProxyMetadataPolicy.Actual valid =
                new BossHitboxProxyMetadataPolicy.Actual(
                        "BOSS_HITBOX", "event-1", boss, 7L,
                        BossHitboxProfile.PartId.LEFT_FOREARM, 0);
        check(BossHitboxProxyMetadataPolicy.matches(valid, expected),
                "all PDC metadata must match the indexed slot");
        check(BossHitboxProxyMetadataPolicy.isCurrentEvent(valid, "BOSS_HITBOX", "event-1"),
                "a valid proxy must be attributable to the current event");

        BossHitboxProxyMetadataPolicy.Actual missingParent =
                new BossHitboxProxyMetadataPolicy.Actual(
                        "BOSS_HITBOX", "event-1", null, 7L,
                        BossHitboxProfile.PartId.LEFT_FOREARM, 0);
        check(BossHitboxProxyMetadataPolicy.isCurrentEvent(
                        missingParent, "BOSS_HITBOX", "event-1"),
                "missing parent must remain attributable by current event tag");
        check(!BossHitboxProxyMetadataPolicy.matches(missingParent, expected),
                "missing parent must be malformed rather than healthy");

        BossHitboxProxyMetadataPolicy.Actual wrongParent =
                new BossHitboxProxyMetadataPolicy.Actual(
                        "BOSS_HITBOX", "event-1", otherBoss, 7L,
                        BossHitboxProfile.PartId.LEFT_FOREARM, 0);
        check(!BossHitboxProxyMetadataPolicy.matches(wrongParent, expected),
                "wrong parent must not satisfy the current slot");

        BossHitboxProxyMetadataPolicy.Actual wrongGeneration =
                new BossHitboxProxyMetadataPolicy.Actual(
                        "BOSS_HITBOX", "event-1", boss, 8L,
                        BossHitboxProfile.PartId.LEFT_FOREARM, 0);
        check(!BossHitboxProxyMetadataPolicy.matches(wrongGeneration, expected),
                "wrong generation must not satisfy the current slot");

        BossHitboxProxyMetadataPolicy.Actual malformedPart =
                new BossHitboxProxyMetadataPolicy.Actual(
                        "BOSS_HITBOX", "event-1", boss, 7L, null, -1);
        check(BossHitboxProxyMetadataPolicy.isCurrentEvent(
                        malformedPart, "BOSS_HITBOX", "event-1"),
                "malformed part metadata must still be attributable to the event");
        check(!BossHitboxProxyMetadataPolicy.matches(malformedPart, expected),
                "malformed part metadata must not satisfy the slot");

        System.out.println("BossHitboxProxyMetadataPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
