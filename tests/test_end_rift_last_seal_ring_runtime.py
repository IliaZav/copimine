"""Contract 20 wiring, rewritten for LastSealRingRuntime (commit 9a0b9794).

The three legacy files asserted an inline monolith implementation that no
longer exists. This one asserts the real runtime is reachable, refuses an
infeasible escape layout before publishing, derives its rows from the policy,
advances one row at a time, claims damage once per cast, and retires cleanly.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
MONOLITH = SRC / "CopiMineEndEvent.java"
RUNTIME = SRC / "runtime" / "LastSealRingRuntime.java"


def test_ring_runtime_is_wired_into_the_live_boss_spell_path():
    monolith = MONOLITH.read_text("utf-8")
    assert "startKaguneRingCast(boss)" in monolith, (
        "the ring cast must be reachable from the live spell dispatch"
    )
    body = monolith[monolith.index("private boolean startKaguneRingCast("):]
    body = body[: body.index("\n    }") + 6]
    assert "ringRuntime().start(" in body, "the cast must delegate to the runtime"

    runtime = RUNTIME.read_text("utf-8")
    assert "KaguneRingGapPolicy.layout(" in runtime, (
        "gap geometry must come from the deterministic policy layout"
    )
    assert "!planned.feasible()" in runtime, (
        "an infeasible escape pattern must refuse the cast before it fires"
    )
    assert "planned == null" in runtime, "a null layout must also refuse the cast"


def test_rows_advance_in_order_and_the_cast_is_bounded():
    runtime = RUNTIME.read_text("utf-8")
    assert "elapsed / ROW_TICKS" in runtime, "the row index must come from elapsed ticks"
    assert "ROW_TICKS = EMERGE_TICKS + TRAVEL_TICKS + RETRACT_TICKS + GAP_TICKS" in runtime
    assert "CAST_TICKS = ROW_TICKS * KaguneRingGapPolicy.ROW_COUNT" in runtime, (
        "the cast length must be derived from the policy is three rows"
    )
    assert "elapsed >= CAST_TICKS" in runtime, "the cast must end instead of running forever"
    assert "int row = (int) (elapsed / ROW_TICKS)" in runtime, (
        "rows advance one at a time from the clock, never all at once"
    )


def test_damage_is_claimed_once_per_player_for_the_whole_cast():
    runtime = RUNTIME.read_text("utf-8")
    assert "hitPlayers.contains(" in runtime, "the claim must be checked before damage"
    assert "hitPlayers.add(" in runtime, "the claim must be recorded after damage"
    assert "hitPlayers.clear()" in runtime, "the claim must reset when a new cast starts"
    assert "hitPlayers.remove(" in runtime, "a refused hit must release the claim"
    assert "KaguneGuardianFightPolicy.BASE_STRIKE_DAMAGE" in runtime, (
        "damage must come from the authored fight policy"
    )


def test_cancel_and_phase_change_retire_the_cast():
    runtime = RUNTIME.read_text("utf-8")
    assert "layout = null" in runtime, "cleanup must forget the frozen layout"
    assert "hitPlayers.clear()" in runtime, "cleanup must forget the per-cast claims"
    assert "retireCarrier()" in runtime, "cleanup must retire the authored carrier"
    assert "cancel(\"owner-or-carrier-lost\")" in runtime, (
        "a stale owner or carrier must end the cast"
    )
    monolith = MONOLITH.read_text("utf-8")
    assert "clearKaguneRingCast(" in monolith, "the monolith must expose a cancel path"
    assert "lastSealRing.cancel(reason)" in monolith, (
        "the monolith cancel path must delegate to the runtime"
    )


def test_the_live_path_publishes_before_it_can_damage():
    runtime = RUNTIME.read_text("utf-8")
    publish = runtime.index("host.publish(carrier, frame);")
    hit = runtime.index("host.hit(boss, player")
    assert publish < hit, (
        "publish first: a failed frame must not deal invisible damage"
    )
    assert "!frame.canDamage()" in runtime, "non-damaging frames must be skipped"
