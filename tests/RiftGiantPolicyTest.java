import me.copimine.endevent.domain.RiftGiantPolicy;

import java.util.List;

public final class RiftGiantPolicyTest {
    public static void main(String[] args) {
        check(RiftGiantPolicy.summonBudget(2) == 2, "two-player summon budget");
        check(RiftGiantPolicy.summonBudget(5) == 3, "five-player summon budget");
        check(RiftGiantPolicy.summonBudget(20) == 4, "maximum summon budget");
        check(RiftGiantPolicy.admittedSummons(20, 2, 10) == 2,
                "summons must not exceed the roster budget");
        check(RiftGiantPolicy.admittedSummons(5, 0, 1) == 1,
                "summons must reserve the shared active-pressure cap");
        check(RiftGiantPolicy.admittedSummons(2, 2, 5) == 0,
                "a giant cannot summon past its lifetime budget");
        check(RiftGiantPolicy.sharedSummonsUsed(List.of(1, 3, 2)) == 3,
                "the highest persisted guard-giant counter is the shared wave total");
        check(RiftGiantPolicy.sharedSummonsUsed(List.of(-1, 0, 2)) == 2,
                "invalid negative counters cannot lower the shared summon total");
        check(RiftGiantPolicy.sharedSummonsUsed(List.of()) == 0,
                "an encounter with no recorded summons starts with an empty budget");
        check(RiftGiantPolicy.admittedSummons(5,
                        RiftGiantPolicy.sharedSummonsUsed(List.of(2, 3)), 10) == 0,
                "two five-player guard giants cannot each spend the same combined budget");
        check(RiftGiantPolicy.admittedSummons(20,
                        RiftGiantPolicy.sharedSummonsUsed(List.of(2, 3, 3)), 10) == 1,
                "three maximum-roster guard giants share one cumulative summon budget");

        check(RiftGiantPolicy.shouldBeginHeavyStrike(4.0D * 4.0D, true, true, false),
                "a nearby eligible player should receive a telegraphed strike");
        check(!RiftGiantPolicy.shouldBeginHeavyStrike(6.0D * 6.0D, true, true, false),
                "the strike must not begin outside its readable close range");
        check(!RiftGiantPolicy.shouldBeginHeavyStrike(2.0D, true, false, false),
                "the strike cooldown must be honored");
        check(!RiftGiantPolicy.shouldBeginHeavyStrike(2.0D, true, true, true),
                "the giant cannot overlap its own telegraphs");
        check(!RiftGiantPolicy.shouldBeginHeavyStrike(2.0D, true, true, false, true),
                "a pending summon telegraph owns the major ability slot");
        check(RiftGiantPolicy.shouldBeginHeavyStrike(2.0D, true, true, false, false),
                "a nearby strike may begin when no major ability is pending");
        check(RiftGiantPolicy.shouldHitAfterTelegraph(4.8D * 4.8D, true),
                "a player who remains in the warned area can be hit");
        check(!RiftGiantPolicy.shouldHitAfterTelegraph(5.1D * 5.1D, true),
                "a player who dodges beyond the hit radius must escape");
        check(!RiftGiantPolicy.shouldHitAfterTelegraph(1.0D, false),
                "ineligible targets cannot receive heavy-strike damage");

        check(RiftGiantPolicy.isAuraRecipient(49.0D, 3, 3, true, true, false, false, false),
                "allied combat mobs on the seven-block boundary receive the aura");
        check(!RiftGiantPolicy.isAuraRecipient(49.1D, 3, 3, true, true, false, false, false),
                "the aura must stay inside its bounded radius");
        check(!RiftGiantPolicy.isAuraRecipient(1.0D, 3, 3, true, true, true, false, false),
                "the giant cannot buff itself");
        check(!RiftGiantPolicy.isAuraRecipient(1.0D, 3, 3, true, true, false, true, false),
                "ritual casters are excluded from the aura");
        check(RiftGiantPolicy.isAuraRecipient(1.0D, 3, 3, true, true, false, false, true),
                "a giant tank may support eligible allied guards but not itself or casters");
        check(!RiftGiantPolicy.isAuraRecipient(1.0D, 4, 3, true, true, false, false, false),
                "the aura cannot cross wave ownership");

        check(RiftGiantPolicy.shouldRefreshAura(true, 1_000L, 1_000L),
                "the aura refreshes when its active combat phase is due");
        check(!RiftGiantPolicy.shouldRefreshAura(false, 1_000L, 0L),
                "the aura remains inactive during an intermission");
        check(!RiftGiantPolicy.shouldRefreshAura(true, 999L, 1_000L),
                "the aura respects its refresh deadline");

        check(RiftGiantPolicy.shouldRetainAuraLease(true, true, 49.0D, 3, 3),
                "a live same-wave giant inside its aura radius keeps its recipient lease");
        check(!RiftGiantPolicy.shouldRetainAuraLease(true, true, 49.1D, 3, 3),
                "a living but distant former aura owner cannot block the nearby giant from taking ownership");
        check(!RiftGiantPolicy.shouldRetainAuraLease(true, false, 1.0D, 3, 3)
                        && !RiftGiantPolicy.shouldRetainAuraLease(true, true, 1.0D, 4, 3),
                "an aura lease cannot cross world or wave ownership");

        check(RiftGiantPolicy.shouldRetireOrphanedSummon(false, false, true),
                "a missing owner is retired once its known chunk is loaded");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(true, false, true),
                "a loaded owner keeps its summons");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(false, true, true),
                "a known chunk-unloaded owner keeps its summons");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(false, false, false),
                "an unloaded or unknown owner chunk defers orphan cleanup");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(false, true, true, false),
                "a stale stored chunk that happens to be loaded cannot retire a known-unloaded owner");
        check(RiftGiantPolicy.shouldRetireOrphanedSummon(false, true, true, true),
                "an absent owner may be retired after its actual last-known chunk is loaded");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(true, true, true, true),
                "a loaded owner always preserves its summons");
        System.out.println("RiftGiantPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
