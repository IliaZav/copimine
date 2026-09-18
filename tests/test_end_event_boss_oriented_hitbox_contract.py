"""Source contract for exact oriented boss hit acceptance.

The Bukkit Interaction rig is intentionally broad because Paper exposes only
an axis-aligned selector.  The final decision must go through the pure OBB
policy for every server hit route.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
POLICY = (DOMAIN / "BossOrientedHitboxPolicy.java").read_text(encoding="utf-8")
CONTROLLER = (
    ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/BossHitboxController.java"
).read_text(encoding="utf-8")


def test_obb_policy_exposes_finite_ray_and_model_part_transforms() -> None:
    assert "public static OrientedBox fromPart(" in POLICY
    assert "public static OptionalDouble nearestHitDistance(" in POLICY
    assert "public record Vec3(" in POLICY
    assert "public record Ray(" in POLICY
    assert "public record Euler(" in POLICY
    assert "public record Matrix3(" in POLICY
    assert "public record OrientedBox(" in POLICY
    assert "Matrix3 inverse = box.orientation().transpose();" in POLICY
    assert "return tMax >= 0.0D && tMin <= maxDistance" in POLICY


def test_controller_uses_obb_for_all_acceptance_routes() -> None:
    assert CONTROLLER.count("BossOrientedHitboxPolicy.fromPartWithPose(") >= 1
    assert CONTROLLER.count("rayIntersects(transformedObb(") >= 2
    assert CONTROLLER.count("BossOrientedHitboxPolicy.segmentIntersects(") >= 2
    assert "private boolean rayIntersects(BossOrientedHitboxPolicy.OrientedBox" in CONTROLLER
    assert "BossOrientedHitboxPolicy.nearestHitDistance(" in CONTROLLER
    assert "double[] minimums" not in CONTROLLER
    assert "double[] maximums" not in CONTROLLER


def test_broad_interaction_selector_and_exact_obb_decision_stay_separate() -> None:
    assert "proxy.setInteractionWidth" in CONTROLLER
    assert "BossHitboxTransformPolicy.transformWithPose(part," in CONTROLLER
    assert "BossOrientedHitboxPolicy.fromPartWithPose(part," in CONTROLLER
