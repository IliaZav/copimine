"""A Wave 3 gate collapse fires exactly one physical shockwave.

Portal capture used to end with a sound and an action-bar line; the gate simply
stopped being drawn. The approved contract requires a readable implosion with a
radial impulse, and it must happen ONCE per portal even though the completion
callback can run again after a restart or on a repeated tick.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source() -> str:
    return SOURCE.read_text(encoding="utf-8")


def method(name: str) -> str:
    text = source()
    start = text.index("private void " + name + "(")
    end = text.index("\n    /**", start)
    return text[start:end]


def test_the_closure_callback_releases_the_shockwave():
    text = source()
    marker = text.index('releasePortalCollapseShockwave(index, portal);')
    window = text[max(0, marker - 700):marker]
    assert "if (!states.get(index).completed())" in window, (
        "the shockwave must sit on the one-time completion edge, not on every tick")


def test_the_shockwave_fires_at_most_once_per_portal():
    body = method("releasePortalCollapseShockwave")
    assert "if (!portalShockwaveReleased.add(index)) return;" in body, (
        "a repeated completion callback must not kick the players twice")
    assert body.index("portalShockwaveReleased.add") < body.index("setVelocity"), (
        "the guard must gate the impulse")


def test_the_impulse_pushes_outward_and_is_bounded():
    body = method("releasePortalCollapseShockwave")
    assert "dx / length * strength" in body and "dz / length * strength" in body, (
        "knockback must be radial, away from the gate")
    assert "distance > PORTAL_SHOCKWAVE_RADIUS" in body, (
        "players outside the radius must not be moved at all")
    assert "PORTAL_SHOCKWAVE_LIFT * falloff" in body, (
        "the vertical component must fall off with distance, not launch players out of the arena")
    assert "player.getVelocity().multiply(0.25D).add(impulse)" in body, (
        "the old velocity must be damped so the impulse stays bounded")


def test_the_shockwave_is_visual_only_and_touches_no_blocks():
    body = method("releasePortalCollapseShockwave")
    for forbidden in ("setType(", "breakNaturally(", "createExplosion(", "damage("):
        assert forbidden not in body, (
            "the collapse is a magical implosion, never a destructive explosion")


def test_the_release_set_is_cleared_with_the_wave():
    text = source()
    marker = text.index("portalShockwaveReleased.clear();")
    assert text.count("portalShockwaveReleased.clear();") >= 3, (
        "every portal reset path must also drop the released indexes, or a "
        "second event would inherit a silenced portal")
    window = text[marker:marker + 120]
    assert "portalShockwaveReleased.clear();" in window
