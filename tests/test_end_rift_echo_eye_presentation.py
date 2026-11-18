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
import struct
import zlib


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"


def source():
    return (CLIENT / "EchoEyesFeature.java").read_text(encoding="utf-8")


def renderer_source():
    return (CLIENT / "EchoVanillaRenderer.java").read_text(encoding="utf-8")


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


def test_the_body_tint_darkens_the_skin_and_keeps_a_violet_bias():
    """The copy wears the owner's *darkened* skin, biased violet, not grey.

    The darkening is a translucent body pass, so vanilla has no negative
    brightness and the tint has to do the work by being genuinely dark. A tint
    that stayed bright would be invisible; a neutral one would read as a
    different, paler player instead of a drained copy.
    """
    match = re.search(r"ECHO_BODY_TINT\s*=\s*0x([0-9A-Fa-f]{8})", renderer_source())
    assert match, "the body tint constant must exist and be a literal"

    argb = int(match.group(1), 16)
    alpha = (argb >> 24) & 0xFF
    red = (argb >> 16) & 0xFF
    green = (argb >> 8) & 0xFF
    blue = argb & 0xFF

    assert alpha == 0xFF, f"the body pass must be fully applied, alpha was {alpha:#04x}"
    assert max(red, green, blue) <= 0x80, (
        f"the tint must actually darken the skin, got r={red} g={green} b={blue}"
    )
    assert blue > red > green, (
        f"the drain must keep the violet bias, got r={red} g={green} b={blue}"
    )
    assert green < 0x60, f"a violet drain keeps green well down, got {green:#04x}"


def test_the_eye_texture_namespace_resolves_to_a_shipped_pack_asset():
    """The declared identifier and the shipped file must be the same location.

    The client mod's own assets live under ``copimineclient``; the eye sheet
    instead ships in the resource pack under ``copimine``. Nothing at runtime
    would fail loudly if the two drifted -- the pass would simply sample a
    missing texture -- so the namespace in the code must be tied to a real file
    in the pack that the client loads.
    """
    match = re.search(
        r'fromNamespaceAndPath\(\s*"([a-z0-9_]+)"\s*,\s*"([a-z0-9_/.]+)"\s*\)',
        source(),
    )
    assert match, "the eye texture identifier must be declared with a namespace"
    namespace, path = match.group(1), match.group(2)
    asset = ROOT / "resourcepacks/src/assets" / namespace / path
    assert asset.is_file(), (
        f"the eye pass samples {namespace}:{path}, but the pack does not ship "
        f"{asset.relative_to(ROOT)}"
    )


def test_the_eye_texture_pixels_are_violet_and_only_cover_the_head():
    """The asset itself, not just the tint: the painted texels are violet.

    The layer tints a violet emissive pass, but the texels that pass samples must
    also be violet and must sit on the vanilla head's eye faces; a texel painted
    white or placed anywhere else would glow the wrong colour or in the wrong
    place.
    """
    data = (ROOT / "resourcepacks/src/assets/copimine/textures/entity/"
                   "end_event_echo_eyes.png").read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"

    offset, idat = 8, b""
    while offset < len(data):
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        kind, payload = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + size]
        if kind == b"IHDR":
            assert struct.unpack(">IIBBBBB", payload) == (64, 64, 8, 6, 0, 0, 0)
        if kind == b"IDAT":
            idat += payload
        offset += size + 12
    pixels = zlib.decompress(idat)

    visible = []
    for y in range(64):
        row = pixels[y * 257:(y + 1) * 257]
        assert row[0] == 0
        for x in range(64):
            if row[1 + x * 4 + 3]:
                visible.append((x, y, tuple(row[1 + x * 4:1 + x * 4 + 4])))

    assert {x for x, _, _ in visible} <= {9, 10, 13, 14}, (
        "the eye glow must stay on the vanilla head's eye texels"
    )
    assert {y for _, y, _ in visible} <= {12, 13}, (
        "the eye glow must stay on the vanilla head's eye texels"
    )
    for x, y, (r, g, b, a) in visible:
        assert a > 0
        assert b > r > g and not (r == g == b), (
            f"pixel ({x}, {y}) is not violet: r={r} g={g} b={b}"
        )

