"""Guard the vanilla skeleton look path against a second local rotation."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client" / "RiftEventSkeletonModel.java"


def test_skeleton_does_not_apply_head_look_after_vanilla_set_angles() -> None:
    source = MODEL.read_text(encoding="utf-8")
    super_start = source.index("super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);")
    set_angles_end = source.index("    public boolean isElite()", super_start)
    after_super = source[super_start:set_angles_end]
    assert "head.yaw += MathHelper.clamp(headYaw" not in after_super
    assert "head.pitch += MathHelper.clamp(headPitch" not in after_super
    assert "leftForearm.roll += pulse" in after_super
    assert "rightForearm.roll -= pulse" in after_super
    assert "leftLowerLeg.yaw += pulse" in after_super
    assert "rightLowerLeg.yaw -= pulse" in after_super
    assert "eliteShoulderLeft.pitch += pulse" in after_super
    assert "eliteShoulderRight.pitch -= pulse" in after_super
    assert "eliteHornLeft.roll += pulse" in after_super
    assert "eliteHornRight.roll -= pulse" in after_super
    assert "chestRift.yScale =" in after_super
    assert "guardCrest.yaw =" in after_super
    assert "guardChestSeal.yScale =" in after_super
    assert "guardianSpine.yScale =" in after_super
    assert "eliteMantle.roll =" in after_super
    assert "eliteHornCrown.pitch =" in after_super
    assert after_super.count("super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);") == 1


def test_skeleton_keeps_the_vanilla_head_part_for_single_look_application() -> None:
    source = MODEL.read_text(encoding="utf-8")
    assert "ModelPart head;" in source
    assert "super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);" in source
