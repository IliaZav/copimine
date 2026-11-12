"""Phase 5 echo copies: the eyes are violet, the body is drained.

``EchoEyesFeature`` is what makes a copy read as the owner rather than as a
clone: it draws an emissive eye pass tinted violet, then a dark translucent body
pass on top. The class imports Minecraft render types and cannot be compiled
without the client jar, but every invariant that matters here is a literal in the
source, so this test reads the source directly.

This is the presentation half of ECHO-003. The server half - the copy wearing the
owner's real skin - is covered by tests/test_end_rift_echo_replica_presentation.py.
"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"


def source():
    return (CLIENT / "EchoEyesFeature.java").read_text(encoding="utf-8")


def test_the_eye_tint_is_violet_and_not_white():
    """The user asked for violet eyes; a white tint is the shipped default."""
    text = source()
    match = re.search(r"ECHO_EYE_TINT\s*=\s*0x([0-9A-Fa-f]{8})", text)
    assert match, "the eye tint constant must exist and be a literal"

    argb = int(match.group(1), 16)
    alpha = (argb >> 24) & 0xFF
    red = (argb >> 16) & 0xFF
    green = (argb >> 8) & 0xFF
    blue = argb & 0xFF

    assert alpha == 0xFF, f"the eyes must be opaque, alpha was {alpha:#04x}"
    assert blue > red > green, (
        "a violet needs blue > red > green, got "
        f"r={red} g={green} b={blue}"
    )
    assert not (red == green == blue), "the eyes must not be a neutral grey or white"
    assert blue >= 0xC0, f"the eyes must glow, blue was {blue:#04x}"
    assert green <= 0x90, f"a violet keeps green low, got {green:#04x}"


def test_the_eye_layer_is_drawn_before_the_darkening_pass():
    """Order matters: the body tint must not wash out the eyes."""
    text = source()
    eyes = text.find("RenderTypes.eyes(EYES)")
    body = text.find("ECHO_BODY_TINT")
    assert eyes >= 0, "the emissive eye pass is missing"
    assert body >= 0, "the darkening body pass is missing"
    assert eyes < body, (
        "the eye pass must be submitted first; drawing the darkened body over "
        "the eyes first would dull them"
    )


def test_the_layer_only_reacts_to_echoes():
    """A normal player must never get violet eyes."""
    text = source()
    guard = re.search(r"if \(!?\(state instanceof EndRiftRenderStateAccess", text)
    assert guard, "the layer must gate on the render-state access interface"
    assert "copimine$isEcho()" in text, "the layer must check the echo flag"
    assert re.search(r"copimine\$isEcho\(\)\)\s*return;", text), (
        "the echo check must actually return early for non-echoes"
    )


def test_the_eye_texture_is_a_real_asset():
    text = source()
    match = re.search(r'"textures/entity/([a-z0-9_]+)\.png"', text)
    assert match, "the eye texture path must be declared"
    name = match.group(1) + ".png"
    found = list(ROOT.rglob(name))
    in_source = [p for p in found if "resources" in p.parts and "build" not in p.parts]
    assert in_source, f"{name} is referenced but does not exist in any resources tree"
