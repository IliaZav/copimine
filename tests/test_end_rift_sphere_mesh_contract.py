"""Wave 6 sphere must look like a prison from outside and not blind the captive inside.

The client mesh is the only thing the player actually sees for complaint (10). Before this
contract the class was covered solely by an artifact-presence check in
test_copimine_client_mob_artifact ("RitualSphereMesh.class is in the jar"), so the shell,
the interior view, and the opacity budget could regress silently.

We parse the real source rather than run it: the invariants are arithmetic, and a silent
change to any of them is exactly what the player would report as "no sphere" or
"can't see anything while captured".
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MESH = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/RitualSphereMesh.java"


def _source() -> str:
    assert MESH.is_file(), f"sphere mesh is missing: {MESH}"
    return MESH.read_text(encoding="utf-8")


def _const(name: str) -> float:
    match = re.search(rf"public static final float {name}\s*=\s*([0-9.]+)F", _source())
    assert match, f"{name} constant is gone"
    return float(match.group(1))


def test_sphere_radius_matches_the_server_anchor() -> None:
    radius = _const("RADIUS")
    assert 2.0 <= radius <= 3.0, f"sphere radius {radius} left the authored range"


def test_outside_camera_sees_shell_and_inside_camera_does_not() -> None:
    source = _source()
    assert "shellVisibleFrom" in source
    assert re.search(
        r"x\*x\s*\+\s*y\*y\s*\+\s*z\*z\s*>\s*\(RADIUS\s*\+\s*[0-9.]+\)",
        source,
    ), "the shell no longer requires the camera to stand outside the membrane"
    radius = _const("RADIUS")

    def shell(x: float) -> bool:
        return x * x > (radius + 0.12) ** 2

    assert shell(radius + 1.0) is True
    assert shell(radius * 0.5) is False
    assert shell(0.0) is False


def test_surface_is_translucent_even_at_the_centre() -> None:
    source = _source()
    assert "surfaceAlpha" in source
    assert re.search(r"65\.0D\s*\+\s*115\.0D", source), (
        "surface alpha budget changed; an opaque shell would hide the scene behind it"
    )
    worst = round(65.0 + 115.0 * 1.0)
    assert worst < 255, "the sphere would render fully opaque"


def test_interior_ribbons_keep_a_clear_aim_cone() -> None:
    source = _source()
    assert "interiorAlpha" in source
    assert re.search(r"Math\.cos\(Math\.toRadians\(12\)\)", source), (
        "the 12-degree aim cone disappeared; the captive would be blinded by their own cage"
    )
    # The cone must be APPLIED, not merely mentioned: a dead guard around the
    # comparison would keep the constant and let ribbons cover the crosshair.
    assert re.search(r"if\s*\(\s*nearestForward\s*>=\s*clearAim", source), (
        "the aim cone constant is no longer applied to the ribbon test"
    )
    assert "if (false)" not in source and "if(false)" not in source, (
        "an always-false guard disables the interior culling"
    )
    assert re.search(r"up\s*-\s*padding\s*<\s*-0\.12", source), (
        "the lower-screen guard is gone; the HUD would be covered by ribbons"
    )


def test_interior_refuses_a_camera_outside_the_shell() -> None:
    source = _source()
    assert re.search(
        r"view\.x\(\)\*view\.x\(\)\s*\+\s*view\.y\(\)\*view\.y\(\)"
        r"\s*\+\s*view\.z\(\)\*view\.z\(\)\s*>=\s*\(RADIUS\s*-\s*[0-9.]+\)",
        source,
    ), "interiorQuads no longer refuses an outside camera"
    radius = _const("RADIUS")
    assert (radius * 0.5) ** 2 < (radius - 0.02) ** 2


def test_sphere_is_built_from_a_finite_closed_mesh() -> None:
    source = _source()
    assert re.search(r"latitude\s*<\s*16", source)
    assert re.search(r"longitude\s*<\s*32", source)
    assert "List.copyOf" in source, "the mesh must be immutable once built"


def test_shield_is_a_separate_mesh_with_its_own_model_id() -> None:
    source = _source()
    assert "SHIELD_MODEL_DATA = 830021" in source
    assert re.search(r"SPHERE_MODEL_DATA\s*=\s*830019", source)
    assert "buildShield" in source and "shieldQuads" in source
