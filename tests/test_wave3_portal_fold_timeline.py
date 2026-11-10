"""A Wave 3 gate must fold shut and burst on a visible timeline.

Player report: the portals were flat, the closing portal had no animation for a
player standing inside it, and nothing exploded when it closed. The gate used to
scale down linearly and then stop being drawn, so the closure read as a sprite
blink rather than a magical collapse.

These tests pin the authored fold timeline in the pure policy and the production
adapter that consumes it: the fold must be eased, the shard layer must turn, the
implosion must be bounded to the collapse window, and the completion burst must
fire exactly once and never mutate the world.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/PortalPresentationPolicy.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start:cursor + 1]
    raise AssertionError(f"unterminated production method: {signature}")


def test_policy_authors_a_bounded_collapse_timeline():
    policy = POLICY.read_text(encoding="utf-8")
    for name in (
        "collapseProgress",
        "collapseFoldScale",
        "collapseSpinRadians",
        "collapseImplosionIntensity",
        "idleSpinRadians",
    ):
        assert f"public static" in policy and f"{name}(" in policy, (
            f"{name} is part of the authored W3 collapse timeline"
        )
    # The fold is eased, not linear: equal time steps must not give equal scale.
    assert "Math.pow(1.0D - clamped, 2.0D)" in policy, (
        "a linear scale-down is what read as a flat sprite; the fold must ease"
    )
    # The implosion is a discrete pulse, so it must vanish at both ends.
    assert "Math.sin(clamp01(progress) * Math.PI)" in policy
    # A missing deadline must report zero progress rather than NaN.
    assert "if (closingAt < 0L || now <= closingAt) return 0.0D;" in policy


def test_display_animation_consumes_the_authored_timeline():
    animate = _method(SOURCE.read_text(encoding="utf-8"), "private void animatePortalModelVisuals(")
    assert "PortalPresentationPolicy.collapseProgress(" in animate
    assert "PortalPresentationPolicy.collapseSpinRadians(" in animate
    assert "PortalPresentationPolicy.collapseFoldScale(" in animate
    assert "PortalPresentationPolicy.idleSpinRadians(" in animate
    # The layered stack has to actually rotate; a zero quaternion is the flat
    # rendering the player complained about.
    assert "new AxisAngle4f(yaw, 0.0F, 1.0F, 0.0F)" in animate, (
        "the shard/inner layers must turn around the gate axis"
    )
    assert "PortalVisualLayer.FRAME" in animate, (
        "the grounded frame must be distinguishable from the turning layers"
    )


def test_completion_burst_fires_once_and_is_non_destructive():
    source = SOURCE.read_text(encoding="utf-8")
    burst = _method(source, "private void releasePortalCollapseBurst(")
    assert "portalCollapseBurstReleased.add(index)" in burst, (
        "a repeated tick must not replay the closure burst"
    )
    for forbidden in ("setType(", "breakNaturally(", "createExplosion(", "damage(", "setHealth("):
        assert forbidden not in burst, (
            "the closure burst is magical, never a destructive explosion"
        )
    # It must be visible: a flash plus expanding rings, not a bare sound cue.
    assert "Particle.EXPLOSION_EMITTER" in burst
    assert "Particle.FLASH" in burst
    assert burst.count("Particle.DUST") >= 1
    assert "COLLAPSE_SHARD_COUNT" in burst
    assert burst.count("playSound(") >= 3, "the closure needs layered audio"


def test_collapse_burst_is_driven_by_the_fold_deadline():
    update = _method(SOURCE.read_text(encoding="utf-8"), "private void updatePortalObjective(")
    assert "PortalPresentationPolicy.collapseFinished(" in update
    assert "releasePortalCollapseBurst(index, portal);" in update
    marker = update.index("releasePortalCollapseBurst(index, portal);")
    window = update[max(0, marker - 500):marker]
    assert "collapseFinished" in window, (
        "the burst must sit on the finished-collapse edge, not on every tick"
    )


def test_collapse_burst_state_is_cleared_with_the_wave():
    source = SOURCE.read_text(encoding="utf-8")
    assert source.count("portalCollapseBurstReleased.clear();") >= 1, (
        "a second event must not inherit a gate that already burst"
    )