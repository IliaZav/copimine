"""Source-level guard that event role models use the checked UV builder."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client"
MODELS = (
    CLIENT / "RiftEventEndermanModel.java",
    CLIENT / "RiftEventSkeletonModel.java",
    CLIENT / "RiftSpiderModel.java",
)
UV_HELPER = CLIENT / "ModelUvBounds.java"


def test_standard_box_helper_uses_the_declared_footprint_formula() -> None:
    source = UV_HELPER.read_text(encoding="utf-8")
    assert "maxU = (double) u + 2.0D * width + 2.0D * depth" in source
    assert "maxV = (double) v + depth + height" in source
    assert "Float.isFinite" in source
    assert "atlasWidth" in source and "atlasHeight" in source


def test_every_custom_role_model_routes_cuboids_through_uv_validation() -> None:
    raw_cuboid = re.compile(r"(?<!ModelUvBounds)\.cuboid\(")
    for model in MODELS:
        source = model.read_text(encoding="utf-8")
        assert "ModelUvBounds.cuboid" in source, model.name
        assert "ModelUvBounds.boxes" in source, model.name
        assert not raw_cuboid.search(source), model.name


def test_uv_footprint_test_is_present_in_the_client_project() -> None:
    test = ROOT / "CopiMineClient/src/test/java/me/copimine/client/ModelUvBoundsTest.java"
    source = test.read_text(encoding="utf-8")
    assert "standardBoxRejectsUvFootprintOutsideAtlas" in source
    assert "standardBoxAcceptsFootprintInsideAtlas" in source
