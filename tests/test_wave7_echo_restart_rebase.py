"""Wave 7 restart recovery: the Echo duel's absolute ticks must be re-based.

``RealitySplitEchoDuelState.ActorState`` persists absolute tick deadlines
(``nextActionTick``, the two cooldowns, the pending-use deadline, both shield
deadlines and the jump cooldown) while ``eventTickCounter`` starts at zero on
every boot and is never saved. A copy restored from a snapshot therefore held
deadlines in the tens of thousands and every action gate in
``EchoDuelCombatPolicy.ready`` refused it until the fresh counter caught up:
`/cmend` recovery produced a copy that stood still for a very long time.

``Wave4ObeliskRecoveryPolicy.restoreTiming`` already solves exactly this for the
obelisk timers by re-basing the saved relative timers onto the fresh counter.
The Echo duel gets the same rule, applied where the carrier is rebuilt, and the
behavioural proof lives in ``EchoDuelCombatRuntimeAdapterTest``
(``restoredDuelDeadlinesRebaseOntoFreshCounter``) because no test can load this
Bukkit class. These assertions pin the wiring itself: a rebase that exists but
is never called would leave the freeze in place.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = (
    ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
    / "CopiMineEndEvent.java"
)
ADAPTER = (
    ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
    / "runtime" / "wave7" / "EchoDuelCombatRuntimeAdapter.java"
)


def _section(source: str, start: str, end: str) -> str:
    left = source.index(start)
    return source[left : source.index(end, left)]


def test_adapter_exposes_a_bounded_restart_rebase():
    adapter = ADAPTER.read_text(encoding="utf-8")
    rebase = _section(
        adapter,
        "public static ActorState rebaseRestoredDeadlines",
        "private static long horizon",
    )

    assert "COOLDOWNS.shieldCooldownTicks()" in rebase
    assert "COOLDOWNS.shieldHoldTicks()" in rebase
    assert "COOLDOWNS.sharedActionTicks()" in rebase
    assert "COOLDOWNS.consumableUseTicks()" in rebase
    assert "COOLDOWNS.jumpCooldownTicks()" in rebase
    # Every deadline is clamped, never replaced by a fresh duration, so a
    # cooldown saved earlier on the same boot is handed back unchanged.
    assert "Math.min(savedDeadline, latest)" in adapter
    # The shield window keeps its documented order after the clamp.
    assert "Math.min(shieldCooldownUntil," in rebase


def test_restored_echo_actor_is_rebuilt_from_the_rebased_deadlines():
    source = EVENT.read_text(encoding="utf-8")
    rebuild = _section(
        source,
        "private boolean ensureNamedEchoDuelActor",
        "private String ownerName",
    )

    assert (
        "me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter"
        "\n                            .rebaseRestoredDeadlines(duel.actor(), eventTickCounter)"
    ) in rebuild
    assert "restoredActor.nextActionTick()" in rebuild
    assert "restoredActor.pendingUseUntilTick()" in rebuild
    assert "restoredActor.shieldActiveUntilTick()" in rebuild
    assert "restoredActor.shieldCooldownUntilTick()" in rebuild
    assert "restoredActor.jumpCooldownUntilTick()" in rebuild
    # The stale absolute values must not reach the new actor checkpoint: that
    # copy is exactly the frozen one this recovery exists for.
    assert "duel.actor().nextActionTick()" not in rebuild
    assert "duel.actor().pendingUseUntilTick()" not in rebuild
    assert "duel.actor().rangedCooldownUntilTick()" not in rebuild
    assert "duel.actor().jumpCooldownUntilTick()" not in rebuild
    # The fresh plan branch still starts from the live counter, not from zero.
    assert "0L, eventTickCounter, 0L, 0L, 0L, 0L)" in rebuild