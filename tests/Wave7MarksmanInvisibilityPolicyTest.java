import me.copimine.endevent.domain.Wave7MarksmanInvisibilityPolicy;

/**
 * MARK-005 / MARK-006: the Marksman may vanish for five seconds to reposition,
 * but that is not invulnerability, not permission to fire unseen, and not
 * reusable on demand.
 */
public final class Wave7MarksmanInvisibilityPolicyTest {
    public static void main(String[] args) {
        vanishesForExactlyFiveSeconds();
        isNotInvulnerabilityAndBlocksUnseenAttacks();
        requiresAClearRevealBeforeAttackingAgain();
        enforcesARealCooldownThatCannotChain();
        doesNotGrantAFreshVanishAfterRestart();
        System.out.println("Wave7MarksmanInvisibilityPolicyTest OK");
    }

    private static void vanishesForExactlyFiveSeconds() {
        // SUPERSEDED (2026-11-04): an interim pass shrank the window to 3.4 s
        // on the strength of the same request that produced it. The contract
        // (MARK-005/006) and this class's own documentation both say five
        // seconds, so the window is back to five; what keeps the pair slippery
        // is the shorter cooldown, not a window shorter than the contract's.
        check(Wave7MarksmanInvisibilityPolicy.INVISIBLE_TICKS == 100L,
                "five seconds is 100 ticks at twenty ticks per second");
        check(Wave7MarksmanInvisibilityPolicy.INVISIBLE_MILLIS
                        == Wave7MarksmanInvisibilityPolicy.INVISIBLE_TICKS * 50L,
                "the recorded deadline is five real seconds");
        check(Wave7MarksmanInvisibilityPolicy.REVEAL_TICKS > 0L,
                "a reveal must be announced before the attack resumes");
    }

    private static void isNotInvulnerabilityAndBlocksUnseenAttacks() {
        long started = 1_000L;
        var window = Wave7MarksmanInvisibilityPolicy.vanish(started, 0L);
        check(window != null, "a ready Marksman may vanish");
        check(window.hiddenAt(started + 1L), "the Marksman is hidden during the window");
        check(window.hiddenAt(started + Wave7MarksmanInvisibilityPolicy.INVISIBLE_TICKS - 1L),
                "the window is inclusive of its final hidden tick");
        check(!window.hiddenAt(window.revealStartsAt() + Wave7MarksmanInvisibilityPolicy.REVEAL_TICKS),
                "the window ends after the reveal");
        // Vanishing never implies damage immunity.
        check(!Wave7MarksmanInvisibilityPolicy.immuneWhileHidden(),
                "invisibility must never be turned into invulnerability");
        // Firing while hidden is refused, not merely discouraged.
        check(!window.mayAttack(started + 2L), "a hidden Marksman cannot attack unseen");
        check(window.mayAttack(window.attackResumesAt()),
                "attacks resume once the reveal has been announced");
        check(!window.mayAttack(window.revealStartsAt()),
                "the reveal tick itself is not an attack tick");
    }

    private static void requiresAClearRevealBeforeAttackingAgain() {
        var window = Wave7MarksmanInvisibilityPolicy.vanish(500L, 0L);
        check(window.revealStartsAt() == 500L + Wave7MarksmanInvisibilityPolicy.INVISIBLE_TICKS,
                "the reveal starts when the hidden window ends");
        check(window.attackResumesAt() == window.revealStartsAt()
                        + Wave7MarksmanInvisibilityPolicy.REVEAL_TICKS,
                "attacks wait for the whole reveal");
        // The reveal guard exists precisely to stop a point-blank full fan.
        check(Wave7MarksmanInvisibilityPolicy.REVEAL_TICKS >= 10L,
                "the reveal must be long enough to read, not a single tick");
    }

    private static void enforcesARealCooldownThatCannotChain() {
        long started = 2_000L;
        var window = Wave7MarksmanInvisibilityPolicy.vanish(started, 0L);
        long ready = window.cooldownUntil();
        check(ready >= window.attackResumesAt() + Wave7MarksmanInvisibilityPolicy.COOLDOWN_TICKS,
                "the cooldown is measured after the reveal, not inside the window");
        check(!Wave7MarksmanInvisibilityPolicy.readyAt(ready - 1L, ready),
                "the Marksman cannot vanish again before its cooldown ends");
        check(Wave7MarksmanInvisibilityPolicy.readyAt(ready, ready),
                "the Marksman may vanish again exactly when the cooldown ends");
        check(Wave7MarksmanInvisibilityPolicy.vanish(started + 5L, ready) == null,
                "a second vanish inside the cooldown is refused, so it cannot chain");
    }

    private static void doesNotGrantAFreshVanishAfterRestart() {
        // Resume works in ticks on the same timeline as the live window.
        long deadline = 200L;
        // A restart keeps the absolute deadline; it does not reset to a new window.
        var resumed = Wave7MarksmanInvisibilityPolicy.resume(deadline, 150L);
        check(resumed.hiddenAt(151L), "a resumed window is still hidden before its deadline");
        check(!resumed.hiddenAt(deadline + 1L), "the resumed window still ends on schedule");
        check(resumed.revealStartsAt() == deadline,
                "the resumed window reveals exactly on the persisted deadline");
        check(resumed.offlineTicks() > 0L, "time spent unloaded is accounted, not skipped");
        // Offline time past the deadline ends the window instead of leaving a
        // permanently invisible root behind.
        check(Wave7MarksmanInvisibilityPolicy.resume(deadline, deadline + 50L) == null,
                "a window whose deadline passed while offline cannot stay invisible");
        check(Wave7MarksmanInvisibilityPolicy.resume(deadline, deadline) == null,
                "a deadline reached exactly now leaves no hidden time");
        check(Wave7MarksmanInvisibilityPolicy.resume(0L, 5L) == null,
                "no deadline means no invisibility at all");
        check(Wave7MarksmanInvisibilityPolicy.resume(-1L, 5L) == null,
                "a malformed deadline cannot create invisibility");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}