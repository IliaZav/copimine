import me.copimine.endevent.domain.RiftGiantPolicy;

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
        check(!RiftGiantPolicy.isAuraRecipient(1.0D, 3, 3, true, true, false, false, true),
                "ritual guards are excluded from the aura");
        check(!RiftGiantPolicy.isAuraRecipient(1.0D, 4, 3, true, true, false, false, false),
                "the aura cannot cross wave ownership");

        check(RiftGiantPolicy.shouldRefreshAura(true, 1_000L, 1_000L),
                "the aura refreshes when its active combat phase is due");
        check(!RiftGiantPolicy.shouldRefreshAura(false, 1_000L, 0L),
                "the aura remains inactive during an intermission");
        check(!RiftGiantPolicy.shouldRefreshAura(true, 999L, 1_000L),
                "the aura respects its refresh deadline");

        check(RiftGiantPolicy.shouldRetireOrphanedSummon(false, false, true),
                "a missing owner is retired once its known chunk is loaded");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(true, false, true),
                "a loaded owner keeps its summons");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(false, true, true),
                "a known chunk-unloaded owner keeps its summons");
        check(!RiftGiantPolicy.shouldRetireOrphanedSummon(false, false, false),
                "an unloaded or unknown owner chunk defers orphan cleanup");
        System.out.println("RiftGiantPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
