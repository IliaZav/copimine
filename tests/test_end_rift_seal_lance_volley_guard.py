"""Guard the phase-6 Seal Lance Volley: it must fire at more than one player.

Binding sources:
- ``COPIMINE_END_RIFT_WAVES_CANONICAL.md`` section 6: "Seal Lance Volley:
  large, authored 3D spear-like Rift projectiles fired at more than one
  player".
- The 2026-11-22 objective prompt section 9G.03: "2-3 eligible targets receive
  visible sky/ground facing warnings; lock aim, then fire a short distinct
  volley with staggered impact".

Before this change ``KAGUNE_STAB`` resolved exactly one brain target and built
exactly one construct, so the volley could never hit more than one player and
the group-pressure promise was dead code. The fix selects a bounded, distinct
target ladder (``lanceVolleyTargets``) and starts one independently locked
construct lane per target, with the whole volley still treated as a single
major cast.

This is a static source scan on purpose: the construct runtime is Bukkit-bound,
so a behavioural harness would need a full Paper adapter. The scan still fails
loudly if the multi-target ladder, the per-lane start, the bounded cap or the
single-major-cast arbitration is reverted.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/LastSealConstructRuntime.java"


def test_volley_cap_can_exceed_one_but_stays_bounded():
    source = EVENT.read_text(encoding="utf-8")
    assert "MAX_LANCE_LANES = 3" in source, "the volley must allow more than one lane"
    # clamp(party, 1, MAX_LANCE_LANES): solo stays one honest lance, a duo gets
    # two, and a large party is capped instead of flooding the render.
    assert "Math.min(MAX_LANCE_LANES, Math.max(1, activeBossParticipants().size()))" in source, (
        "lane count must be clamp(party, 1, MAX_LANCE_LANES)"
    )


def test_stab_dispatch_builds_a_multi_target_volley():
    source = EVENT.read_text(encoding="utf-8")
    assert "lanceVolleyTargets(boss, target)" in source, (
        "KAGUNE_STAB must select a distinct multi-target ladder"
    )
    assert "newConstructLane().start(boss, lane, kind, eventTickCounter)" in source, (
        "every selected lane must start its own independently locked construct"
    )


def test_lance_targets_are_distinct_and_legally_targetable():
    source = EVENT.read_text(encoding="utf-8")
    start = source.index("private List<Player> lanceVolleyTargets(")
    body = source[start:source.index("private Player currentBrainTarget(", start)]
    # A duplicate lane would stack two spears on one victim and still count as
    # "one target", so distinctness is the actual requirement.
    assert "!chosen.contains(" in body
    assert "!isCombatTarget(" in body
    assert "chosen.size() >= lanes" in body


def test_the_volley_is_still_one_major_cast():
    source = EVENT.read_text(encoding="utf-8")
    # Active/refuse arbitration must consult every lane, not a single instance.
    assert "private boolean hasActiveConstructLane()" in source
    assert "for (me.copimine.endevent.runtime.LastSealConstructRuntime lane : lastSealConstructs)" in source
    # The old single-instance field must be gone, otherwise one lane could hide
    # another and a second major cast could start mid-volley.
    assert "private me.copimine.endevent.runtime.LastSealConstructRuntime lastSealConstruct;" not in source


def test_runtime_exposes_bounded_lane_retirement():
    runtime = RUNTIME.read_text(encoding="utf-8")
    assert "public boolean done()" in runtime, "the event needs to retire finished lanes"
    assert "!active && carrier == null" in runtime


def test_hazard_reservation_is_released_only_when_the_last_lane_retires():
    source = EVENT.read_text(encoding="utf-8")
    # The volley holds ONE hazard reservation for the whole cast. If the first
    # lane to land released it, a second major cast could start while sibling
    # lances are still telegraphing, which is the exact starvation the single
    # major slot exists to prevent. The finished() hook must therefore gate the
    # release on there being no active lane left.
    start = source.index("public void finished(me.copimine.endevent.runtime.LastSealConstructRuntime.Scope owner")
    body = source[start:source.index("public void failed(String reason)", start)]
    assert "releaseBossHazardReservation(\"kagune-construct-\" + reason)" in body
    assert "!hasActiveConstructLane()" in body, (
        "finished() must release the reservation only once every lane has retired"
    )
    # The per-lane runtime only calls finished() from cancel() of a running
    # lane, so a lane that fails to start can never release the volley slot.
    runtime = RUNTIME.read_text(encoding="utf-8")
    assert "if (running) host.finished(scope, reason);" in runtime
