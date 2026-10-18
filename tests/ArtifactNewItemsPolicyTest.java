import me.copimine.artifacts.BerserkerHeartPolicy;
import me.copimine.artifacts.DonationItemIdentityPolicy;
import me.copimine.artifacts.NightCloakPolicy;
import me.copimine.artifacts.SignalBellPolicy;
import me.copimine.artifacts.VeinMinerPolicy;

import java.util.HashMap;
import java.util.Map;

public final class ArtifactNewItemsPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(BerserkerHeartPolicy.shouldTrigger(0.99D, 10.0D, 900L, 900L, false),
                "berserker activates when projected health falls below ten percent and cooldown is ready");
        check(!BerserkerHeartPolicy.shouldTrigger(1.0D, 10.0D, 900L, 900L, false),
                "berserker does not activate at exactly ten percent");
        check(!BerserkerHeartPolicy.shouldTrigger(0.5D, 10.0D, 901L, 900L, false),
                "berserker cooldown suppresses another activation");
        check(!BerserkerHeartPolicy.shouldTrigger(0.5D, 10.0D, 900L, 900L, true),
                "cancelled damage cannot activate berserker");
        check(BerserkerHeartPolicy.nextCooldownUntil(100L) == 520L,
                "berserker cooldown lasts seven minutes");

        check(SignalBellPolicy.matches("world", 10, 64, 10, "world", 10, 64, 10, true),
                "signal bell matches its bound chest block");
        check(!SignalBellPolicy.matches("world", 10, 64, 10, "world", 10, 64, 10, false),
                "signal bell rejects a non-chest substituted at the bound location");
        check(SignalBellPolicy.matches("world", 10, 64, 10, "world", 11, 64, 10, true),
                "signal bell matches an adjacent double chest half");
        check(!SignalBellPolicy.matches("world", 10, 64, 10, "world", 11, 64, 10, false),
                "signal bell does not match an adjacent non-chest");
        check(!SignalBellPolicy.matches("world", 10, 64, 10, "world", 12, 64, 10, true),
                "signal bell does not match a distant container");
        check(!SignalBellPolicy.matches("world", 10, 64, 10, "other", 10, 64, 10, true),
                "signal bell does not cross worlds");
        check(SignalBellPolicy.isChestContainer("CHEST")
                        && SignalBellPolicy.isChestContainer("TRAPPED_CHEST")
                        && !SignalBellPolicy.isChestContainer("BARREL")
                        && !SignalBellPolicy.isChestContainer("HOPPER"),
                "signal bell binds to normal and trapped chests only");
        check(SignalBellPolicy.shouldNotify(null, 42L), "first same-tick bell notification is delivered");
        check(!SignalBellPolicy.shouldNotify(42L, 42L), "duplicate same-tick notification is suppressed");
        check(SignalBellPolicy.shouldNotify(41L, 42L), "a later open emits a new notification");
        Map<String, Long> notificationTicks = new HashMap<>();
        notificationTicks.put("bell:chest-1", 42L);
        check(SignalBellPolicy.beginNotificationTick(notificationTicks, 42L, 42L) == 42L
                        && notificationTicks.containsKey("bell:chest-1"),
                "same tick retains deduplication state");
        check(SignalBellPolicy.beginNotificationTick(notificationTicks, 42L, 43L) == 43L
                        && notificationTicks.isEmpty(),
                "a new tick clears stale bell locations so rebinding cannot grow memory forever");

        check(VeinMinerPolicy.shouldActivate("DIAMOND_ORE", false, false),
                "vein miner activates on a normal ore break");
        check(!VeinMinerPolicy.shouldActivate("DIAMOND_ORE", true, false),
                "cancelled root break does not mine a vein");
        check(!VeinMinerPolicy.shouldActivate("DIAMOND_ORE", false, true),
                "sneaking opts out of vein mining");
        check(!VeinMinerPolicy.shouldActivate("STONE", false, false),
                "non-ore root does not mine a vein");
        check(VeinMinerPolicy.shouldRunDeferred(false, true, true, true),
                "a valid broken root and the same online holder allow deferred vein traversal");
        check(!VeinMinerPolicy.shouldRunDeferred(true, false, true, true),
                "a later listener cancelling the root prevents vein traversal");
        check(!VeinMinerPolicy.shouldRunDeferred(false, false, true, true),
                "a root that was not removed cannot authorize vein traversal");
        check(!VeinMinerPolicy.shouldRunDeferred(false, true, false, true),
                "an offline holder cannot continue a deferred vein traversal");
        check(!VeinMinerPolicy.shouldRunDeferred(false, true, true, false),
                "switching away from the initiating tool prevents deferred vein traversal");
        check(VeinMinerPolicy.shouldExploreNeighbors(true)
                        && !VeinMinerPolicy.shouldExploreNeighbors(false),
                "a cancelled protected vein block cannot be used as a path through to other blocks");
        check(VeinMinerPolicy.canVisitMore(31) && !VeinMinerPolicy.canVisitMore(32),
                "vein traversal never exceeds thirty-two blocks");

        check(NightCloakPolicy.shouldApplyNightEffects(
                "donation_night_cloak", "NIGHT_CLOAK", 13000L, "NORMAL"),
                "donation night cloak activates at the night boundary in the overworld");
        check(!NightCloakPolicy.shouldApplyNightEffects(
                "night_cloak", "NONE", 13000L, "NORMAL"),
                "End Rift reward cloak has no donation gameplay effect");
        check(!NightCloakPolicy.shouldApplyNightEffects(
                "donation_night_cloak", "NIGHT_CLOAK", 23000L, "NORMAL"),
                "donation night cloak is inactive after night");
        check(!NightCloakPolicy.shouldApplyNightEffects(
                "donation_night_cloak", "NIGHT_CLOAK", 13000L, "THE_END"),
                "donation night cloak is inactive outside the overworld");

        check("donation_night_cloak".equals(DonationItemIdentityPolicy.canonicalItemId(
                "night_cloak", "donation_night_cloak", "PHANTOM_MEMBRANE", 20012)),
                "DB-proven legacy donation cloak resolves to its new catalog identity");
        check("night_cloak".equals(DonationItemIdentityPolicy.canonicalItemId(
                "night_cloak", "night_cloak", "LEATHER_CHESTPLATE", 830005)),
                "End Rift reward keeps its separate legacy identity");
        check(DonationItemIdentityPolicy.canonicalItemId(
                "night_cloak", "donation_night_cloak", "LEATHER_CHESTPLATE", 830005) == null,
                "End Rift reward appearance cannot masquerade as a donation cloak");
        check(DonationItemIdentityPolicy.canonicalItemId(
                "night_cloak", "donation_night_cloak", "PHANTOM_MEMBRANE", 20011) == null,
                "wrong custom model cannot resolve as a legacy donation cloak");
        check(DonationItemIdentityPolicy.canonicalItemId(
                "night_cloak", "rift_core_shard", "PHANTOM_MEMBRANE", 20012) == null,
                "a different DB binding cannot resolve as either cloak identity");
        check(!DonationItemIdentityPolicy.isOwnerBound(
                "donation_night_cloak", true, false, "DONATION_SHOP_ITEM", "DONATION_SHOP", false),
                "a transferable donation item stays transferable despite donation tags");
        check(DonationItemIdentityPolicy.isOwnerBound(
                "berserker_heart", true, true, "DONATION_SHOP_ITEM", "DONATION_SHOP", false),
                "configured owner-bound donation items remain owner-bound");
        check(DonationItemIdentityPolicy.isOwnerBound(
                "retired_donation_item", false, false, "DONATION_SHOP_ITEM", "DONATION_SHOP", false),
                "unrecognized donation-tagged identities fail closed as owner-bound");
        check(DonationItemIdentityPolicy.isOwnerBound(
                "night_cloak", false, false, "", "", true),
                "admin-only End Rift rewards remain owner-bound");

        System.out.println("ArtifactNewItemsPolicyTest OK");
    }
}
