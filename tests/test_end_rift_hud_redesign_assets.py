"""C1-C4 HUD redesign: real asset, alpha, layering and animation contracts.

These tests exercise the actual shipped PNG bytes, not a description:

* every direction is regenerable from the deterministic generator;
* the health recess is transparent so a simulated fill is visible at
  100 / 50 / 25 / 1 / 0 percent;
* the Ram atlas carries seven intact, cracked, shattered and empty cells, and
  the renderer only advances the pose on a higher accepted impact count;
* the Guardian has six distinct phase overlays whose cells never cover the
  real health track, plus six state glyphs;
* the four Wave 7 bars and portraits are redrawn and distinct.
"""

from __future__ import annotations

import hashlib
import importlib.util
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/gui"
ARCHIVE = ROOT / "docs/end-rift/2026-11-21/art-directions"
SCRIPT = ROOT / "scripts/minecraft/GenerateEndRiftHudRedesign.py"
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"

WAVE7_STYLES = ("bone", "sculk", "rune", "mirror")
WAVE7_PORTRAITS = ("marksman", "warden", "archmage", "echo")
# Must match Wave7MinibossBossBarHud.accent().
WAVE7_ACCENTS = {
    "bone": (0x9A, 0x62, 0xFF),
    "sculk": (0x37, 0xE0, 0xD0),
    "rune": (0xF3, 0x4C, 0xDB),
    "mirror": (0xBF, 0xE9, 0xFF),
}


def load_module():
    spec = importlib.util.spec_from_file_location("end_rift_hud_redesign", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pixel_digest(path: Path) -> str:
    """SHA-256 of the decoded RGBA pixels plus their dimensions.

    The artwork is what must be reproducible. Pillow 12 and 13 bundle
    different zlib builds that deflate a handful of these images (the Ram rail)
    to different container bytes with byte-identical pixels, so a container
    digest would fail on the CI interpreter without any art change. The
    dimensions are folded in so a wrongly shaped image with coincidentally
    matching pixel bytes can never pass either.
    """
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        header = f"{rgba.width}x{rgba.height}:".encode("ascii")
        return hashlib.sha256(header + rgba.tobytes()).hexdigest()


def painted(image: Image.Image) -> int:
    return sum(image.getchannel("A").histogram()[1:])


def test_the_generator_reproduces_every_shipped_and_archived_png(tmp_path):
    module = load_module()
    module.build(tmp_path / "gui", tmp_path / "archive")
    shipped = sorted(GUI.glob("ram_seal_*.png"))
    shipped += sorted(GUI.glob("end_rift_guardian_phase_overlay*.png"))
    shipped += sorted(GUI.glob("end_rift_guardian_portrait_aura*.png"))
    shipped += sorted(GUI.glob("end_rift_guardian_state_glyphs*.png"))
    shipped += sorted(GUI.glob("wave7v2_*.png"))
    assert len(shipped) == 2 + 3 + 8, [p.name for p in shipped]
    for path in shipped:
        rebuilt = tmp_path / "gui" / path.name
        assert rebuilt.is_file(), f"{path.name} is not produced by the generator"
        assert pixel_digest(rebuilt) == pixel_digest(path), f"{path.name} is not reproducible"
    archived = sorted(ARCHIVE.glob("*.png"))
    assert len(archived) == 24, [p.name for p in archived]
    for path in archived:
        rebuilt = tmp_path / "archive" / path.name
        assert rebuilt.is_file(), f"{path.name} is not produced by the generator"
        assert pixel_digest(rebuilt) == pixel_digest(path), f"{path.name} is not reproducible"


def test_two_visual_directions_are_archived_for_every_major_bar():
    expected = {
        "ram_seal_rail_a.png", "ram_seal_rail_b.png",
        "ram_seal_plates_a.png", "ram_seal_plates_b.png",
        "guardian_phase_overlay_a.png", "guardian_phase_overlay_b.png",
        "guardian_portrait_aura_a.png", "guardian_portrait_aura_b.png",
    }
    for style in WAVE7_STYLES:
        expected.add(f"wave7_{style}_frame_a.png")
        expected.add(f"wave7_{style}_frame_b.png")
    for stem in WAVE7_PORTRAITS:
        expected.add(f"wave7_{stem}_portrait_a.png")
        expected.add(f"wave7_{stem}_portrait_b.png")
    present = {path.name for path in ARCHIVE.glob("*.png")}
    assert expected <= present, sorted(expected - present)
    # The two directions must be materially different, not a byte duplicate.
    for a in sorted(ARCHIVE.glob("*_a.png")):
        b = a.with_name(a.name[:-6] + "_b.png")
        assert b.is_file(), b.name
        assert digest(a) != digest(b), f"{a.name} and {b.name} are identical"


def test_ram_rail_and_seven_plate_atlas_are_real_art():
    rail = Image.open(GUI / "ram_seal_rail.png")
    assert rail.mode == "RGBA" and rail.size == (300, 56)
    assert rail.getchannel("A").getextrema() == (0, 255)
    # The health recess stays fully transparent after every decoration, so the
    # renderer's real HP fill is never hidden behind the rail art.
    recess = rail.getchannel("A").crop((18, 40, 282, 52)).histogram()
    assert sum(recess[:16]) == sum(recess), "Ram rail covers its health track"
    atlas = Image.open(GUI / "ram_seal_plates.png")
    assert atlas.mode == "RGBA" and atlas.size == (256, 128)

    cells = {}
    for variant in range(4):
        for plate in range(7):
            cell = atlas.crop((plate * 32, variant * 32, plate * 32 + 32, variant * 32 + 32))
            assert painted(cell) > 24, (plate, variant)
            cells[(plate, variant)] = digest_bytes(cell)
    # Each of the four break stages is visually distinct for every plate.
    for plate in range(7):
        stages = [cells[(plate, variant)] for variant in range(4)]
        assert len(set(stages)) == 4, f"plate {plate} repeats an animation stage"
    # The seven intact plates are not seven copies of one sprite.
    assert len({cells[(plate, 0)] for plate in range(7)}) >= 3
    # The engraved Ram emblem is the eighth atlas column.
    emblem = atlas.crop((7 * 32, 0, 8 * 32, 32))
    assert painted(emblem) > 80


def digest_bytes(image: Image.Image) -> str:
    return hashlib.sha256(image.tobytes()).hexdigest()


def test_ram_hud_drives_plates_only_from_accepted_impact_count():
    source = (CLIENT / "RiftRamBossBarHud.java").read_text(encoding="utf-8")
    assert "ram_seal_rail.png" in source and "ram_seal_plates.png" in source
    assert "PLATE_COUNT = 7" in source
    assert "IMPACT_PREFIX = \"END_RIFT_RAM_ARMOR\"" in source
    # The prefix/digit/length parse runs every frame, so it must not compile a
    # regular expression or allocate a matcher on the render thread.
    assert "digit >= '0' && digit <= '7'" in source
    assert ".matches(" not in source
    # A higher durable count schedules; an equal/duplicate one does nothing, and
    # a reordered lower snapshot is ignored instead of rewinding/sounding again.
    assert "impacts > shownImpacts" in source
    assert "reordered or stale snapshot" in source
    # Sounds are per accepted plate and guarded by a stage, never replayed.
    assert "plateSoundStage" in source
    assert "playLocalSound" in source
    # Accessibility and cleanup.
    assert "reducedMotion()" in source
    assert "resetForTest()" in source and "Wave ended" in source
    # The last three plates are the final tower: a slower, heavier sequence and
    # one confined release payoff, still driven by accepted impacts only.
    assert "FINAL_TOWER_FIRST_PLATE = 4" in source
    assert "drawFinalRelease" in source and "FINAL_RELEASE_MILLIS" in source
    assert "finalReleaseAtMillis" in source


def test_wave7_frames_show_the_real_fill_at_every_health_level():
    accent_for = WAVE7_ACCENTS
    for style in WAVE7_STYLES:
        frame = Image.open(GUI / f"wave7v2_bossbar_{style}.png").convert("RGBA")
        assert frame.size == (256, 32), style
        assert frame.getchannel("A").getextrema() == (0, 255), style
        # The source track region must be transparent so a fill drawn after the
        # frame is never hidden. Source coords mirror WAVE7_INNER scaled to 256.
        inner = (14, 12, 242, 20)
        track_alpha = frame.getchannel("A").crop(inner).histogram()
        assert sum(track_alpha[:16]) == sum(track_alpha), f"{style} track is occluded"

        accent = accent_for[style]
        for fraction in (1.0, 0.5, 0.25, 0.01):
            composite = Image.new("RGBA", frame.size, (0, 0, 0, 0))
            composite.alpha_composite(frame)
            left, top, right, bottom = inner
            filled = round((right - left) * fraction)
            draw = ImageDraw.Draw(composite)
            draw.rectangle([left, top, left + max(1, filled), bottom], fill=accent + (255,))
            sample_x = left + max(0, filled // 2)
            sample = composite.getpixel((sample_x, (top + bottom) // 2))
            assert sample[:3] == accent, (style, fraction, sample)
        # At zero percent no accent pixel may remain in the track.
        composite = Image.new("RGBA", frame.size, (0, 0, 0, 0))
        composite.alpha_composite(frame)
        colours = {color[:3] for _count, color in composite.crop(inner).getcolors(1 << 20) if color[3] > 8}
        assert accent not in colours, f"{style} shows health at zero"


def test_wave7_portraits_are_redrawn_and_recognisable():
    for stem in WAVE7_PORTRAITS:
        image = Image.open(GUI / f"wave7v2_portrait_{stem}.png")
        assert image.size == (128, 128) and image.mode == "RGBA"
        assert painted(image) > 128 * 128 * 0.12, stem
    digests = {digest(GUI / f"wave7v2_portrait_{stem}.png") for stem in WAVE7_PORTRAITS}
    assert len(digests) == 4
    source = (CLIENT / "Wave7MinibossBossBarHud.java").read_text(encoding="utf-8")
    for style in WAVE7_STYLES:
        assert f"wave7v2_bossbar_{style}" in source
    for stem in WAVE7_PORTRAITS:
        assert f"wave7v2_portrait_{stem}" in source
    # Existing title ownership is preserved; the Warden is never conflated.
    assert 'SCULK_TITLE = "Хранитель Бездны"' in source


def test_guardian_six_phase_overlays_never_cover_the_health_track():
    atlas = Image.open(GUI / "end_rift_guardian_phase_overlay.png")
    assert atlas.mode == "RGBA" and atlas.size == (360, 840)
    inner = (30, 92, 330, 101)
    seen = set()
    for phase in range(6):
        cell = atlas.crop((0, phase * 140, 360, phase * 140 + 140))
        assert painted(cell) > 300, phase
        histogram = cell.getchannel("A").crop(inner).histogram()
        assert sum(histogram[:16]) == sum(histogram), f"phase {phase} covers the HP track"
        seen.add(digest_bytes(cell))
    assert len(seen) == 6, "phase overlays are duplicates"

    auras = Image.open(GUI / "end_rift_guardian_portrait_aura.png")
    assert auras.size == (384, 64)
    aura_cells = {digest_bytes(auras.crop((i * 64, 0, i * 64 + 64, 64))) for i in range(6)}
    assert len(aura_cells) == 6

    glyphs = Image.open(GUI / "end_rift_guardian_state_glyphs.png")
    assert glyphs.size == (96, 16)
    glyph_cells = {digest_bytes(glyphs.crop((i * 16, 0, i * 16 + 16, 16))) for i in range(6)}
    assert len(glyph_cells) == 6, "phase glyphs are only a hue swap"


def test_guardian_hud_preserves_thresholds_and_adds_phase_state():
    source = (CLIENT / "EndRiftBossBarHud.java").read_text(encoding="utf-8")
    assert "end_rift_guardian_phase_overlay.png" in source
    assert "end_rift_guardian_portrait_aura.png" in source
    assert "end_rift_guardian_state_glyphs.png" in source
    assert "PHASE_SWEEP_MILLIS" in source and "phaseChangedAtMillis" in source
    assert "reducedMotion()" in source
    # Only the phase transition animates the overlay. Continuous pulses made
    # the health display look as if it disconnected during combat.
    assert "VEIN_PERIOD_MILLIS" not in source
    assert "PORTRAIT_AURA" in source and "STATE_GLYPHS" in source
    # The six canonical thresholds are still exact and ordered.
    assert "PHASE_MARKERS = {0.20F, 0.30F, 0.45F, 0.60F, 0.80F}" in source
    for phase in ("AWAKENING", "HUNT", "RIFT", "OVERLOAD", "RAGE", "LAST_SEAL"):
        assert phase in source


def test_reduced_motion_is_a_persisted_client_setting():
    source = (CLIENT / "ClientConfig.java").read_text(encoding="utf-8")
    assert "hud_reduced_motion" in source
    assert "public boolean reducedMotion()" in source
    assert "KEY_HUD_REDUCED_MOTION" in source
    assert "public static ClientConfig config()" in (CLIENT / "CopiMineClient.java").read_text(encoding="utf-8")


def test_per_viewer_animation_state_is_cleared_on_logout_and_world_change():
    """A stale chip, plate or phase sweep must not leak into the next boss."""
    source = (CLIENT / "CopiMineClient.java").read_text(encoding="utf-8")
    disconnect = source[source.index("ClientPlayConnectionEvents.DISCONNECT"):]
    disconnect = disconnect[:disconnect.index("ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE")]
    assert "RiftRamBossBarHud.clearState()" in disconnect
    assert "Wave7MinibossBossBarHud.clearState()" in disconnect
    assert "EndRiftBossBarHud.resetTrackingForTest()" in disconnect
    world = source[source.index("ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE"):]
    world = world[:world.index("ClientCommandRegistrationCallback")]
    assert "RiftRamBossBarHud.clearState()" in world
    assert "Wave7MinibossBossBarHud.clearState()" in world
    assert "EndRiftBossBarHud.resetTrackingForTest()" in world
