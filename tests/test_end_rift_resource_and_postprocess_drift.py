"""N23-P2-15 — resource and post-processing drift.

Master V5.1 (N23-P2-15) requires that "the signed client JAR, plugin JAR, client
bridge, resourcepack, and optional Iris+BSL render pipeline must agree exactly",
that UV/atlas provenance is verified (event art is shared, never a second
divergent copy), and that the event fog/shader work stays isolated from
non-event shader packs.  The native half of that item (F1/F2/F5, 16:9/16:10,
shader on/off, reduced motion, low FPS, disconnect/rejoin, two simultaneous
viewers) needs a GUI client and a live server, so it stays ``NATIVE_BLOCKED``.

This file pins the half that is provable from the repository: the four
authoring artefacts of a shipped spell cast must never drift apart, and the
spell art must keep a single shared origin.

* geometry  — the twelve authored families in the shipped client source,
* audio     — one registered sound per family in ``sounds.json`` plus its Ogg,
* evidence  — one source-derived preview sheet per family,
* pin       — the very client artifact hash named in all three pin sites.

If any of those four lists drifts, this fails loudly instead of shipping a
client whose mesh, sound, evidence or hash disagree.
"""
from __future__ import annotations

import json
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
CLIENT_RES = ROOT / "tools/minecraft-26.3/client/src/main/resources"
GEOMETRY = CLIENT_SRC / "me/copimine/client/SpellVisualGeometry.java"
RENDERER = CLIENT_SRC / "me/copimine/client/SpellVisualRenderer.java"
SOUNDS_JSON = CLIENT_RES / "assets/copimineclient/sounds.json"
SOUND_DIR = CLIENT_RES / "assets/copimineclient/sounds/spell"
SHEETS = ROOT / "docs/end-rift/2026-11-23/spell_previews"
SHARED_RES = ROOT / "CopiMineClient/src/main/resources"
ARC_TEXTURE = SHARED_RES / "assets/copimineclient/textures/entity/end_rift_spell_arc.png"
CLIENT_BUILD = ROOT / "tools/minecraft-26.3/client/build.gradle"
PROFILE = ROOT / "tools/minecraft-26.3/profile.lock.json"
README = ROOT / "docs/minecraft-26.3/README.md"
PIN_TEST = ROOT / "tests/test_minecraft_migration_profile.py"


def _tokens() -> list[str]:
    text = GEOMETRY.read_text(encoding="utf-8")
    return re.findall(r'^\s+[A-Z_]+\("([a-z]+)"\)', text, flags=re.MULTILINE)


def test_geometry_sound_and_evidence_never_drift_apart() -> None:
    """Mesh family, registered sound and preview sheet must be the same set."""
    tokens = _tokens()
    assert len(tokens) == 12, f"expected 12 authored casts, found {tokens}"
    assert len(set(tokens)) == 12, "a spell token is duplicated in the enum"

    registry = json.loads(SOUNDS_JSON.read_text(encoding="utf-8"))
    registered = {
        key.split(".", 1)[1]
        for key in registry
        if key.startswith("spell.")
    }
    assert registered == set(tokens), (
        "the sound registry and the spell roster drifted: "
        f"only-in-registry {sorted(registered - set(tokens))}, "
        f"only-in-code {sorted(set(tokens) - registered)}"
    )

    for token in tokens:
        entry = registry[f"spell.{token}"]
        names = [sound["name"] for sound in entry["sounds"]]
        assert f"copimineclient:spell/{token}" in names, (
            f"{token} is registered but does not point at spell/{token}"
        )
        ogg = SOUND_DIR / f"{token}.ogg"
        assert ogg.is_file(), f"missing shipped sound asset: {ogg}"
        assert ogg.stat().st_size > 1_000, f"{token}.ogg is a stub"
        # The source-derived preview sheet is the honest stand-in for F2 art.
        assert (SHEETS / f"{token}.svg").is_file(), (
            f"missing preview sheet for {token}"
        )


def test_client_artifact_pin_agrees_across_every_site() -> None:
    """The jar hash the client build must reproduce is named in all three sites."""
    lock = json.loads(PROFILE.read_text(encoding="utf-8"))
    artifact = lock["clientArtifact"]
    filename = artifact["filename"]
    size = artifact["size"]
    sha512 = artifact["sha512"]
    assert artifact["version"] == "0.1.5+26.3", artifact["version"]
    assert re.fullmatch(r"[0-9a-f]{128}", sha512), "client sha512 is malformed"

    # docs/minecraft-26.3/README.md quotes the same filename, size and hash.
    readme = README.read_text(encoding="utf-8")
    assert filename in readme, "the README does not name the pinned client jar"
    assert sha512 in readme, "the README does not quote the pinned client hash"
    squeezed = re.sub(r"\s+", "", readme)
    assert str(size) in squeezed, (
        f"the README does not quote the pinned client size {size}"
    )

    # The pin test asserts the identical size and hash, split over two literals.
    pin = PIN_TEST.read_text(encoding="utf-8")
    assert str(size) in pin.replace("_", ""), (
        f"the pin test does not assert the client size {size}"
    )
    assert sha512[:64] in pin, "the pin test does not assert the first hash half"
    assert sha512[64:] in pin, "the pin test does not assert the second hash half"


def test_spell_art_keeps_one_shared_provenance() -> None:
    """The event art is authored once and shared into the shipped client."""
    build = CLIENT_BUILD.read_text(encoding="utf-8")
    assert "resources.srcDir(new File(checkout, 'CopiMineClient/src/main/resources'))" in build, (
        "the 26.3 client no longer shares CopiMineClient's authored resources"
    )
    assert ARC_TEXTURE.is_file(), f"missing authored spell texture: {ARC_TEXTURE}"

    # The renderer must point at exactly that shared asset, not a private copy.
    renderer = RENDERER.read_text(encoding="utf-8")
    assert 'Identifier.fromNamespaceAndPath(\n            "copimineclient", "textures/entity/end_rift_spell_arc.png")' in renderer, (
        "SpellVisualRenderer no longer resolves the shared arc texture"
    )

    # A real RGBA atlas, not a placeholder: PNG magic, IHDR size and alpha.
    data = ARC_TEXTURE.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "the arc texture is not a PNG"
    width, height, depth, color_type, _, _, interlace = struct.unpack(
        ">IIBBBBB", data[16:29]
    )
    assert (width, height) == (128, 64), f"unexpected atlas size {width}x{height}"
    assert depth == 8 and color_type == 6, "the arc texture must be 8-bit RGBA"
    assert interlace == 0, "the arc texture must not be interlaced"


def test_event_spell_work_publishes_no_shaderpack_and_builds_one_layer() -> None:
    """No spell file leaks into the Iris menu, and the renderer owns one layer."""
    leaks = [
        path
        for base in ROOT.glob("**/shaderpacks")
        if base.is_dir()
        for path in base.glob("*end_rift_spell*")
    ]
    assert not leaks, f"spell art leaked into the shaderpack menu: {leaks}"

    renderer = RENDERER.read_text(encoding="utf-8")
    # Exactly one texture constant and one RenderType factory: the layer is
    # rebuilt from the shared texture, never uploaded per frame or per stroke.
    assert renderer.count("Identifier.fromNamespaceAndPath(") == 1, (
        "SpellVisualRenderer must resolve a single shared texture"
    )
    assert "entityTranslucentEmissive(TEXTURE)" in renderer, (
        "the shared spell layer must be built from the single texture constant"
    )
