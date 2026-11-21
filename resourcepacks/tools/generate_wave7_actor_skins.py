"""Compatibility entry point for the End Rift named Wave 7 trial-actor sheets.

The owner's 2026-11-21 art pass replaced the three named trial-actor sheets with
the shared deterministic End Rift painter
(``scripts/minecraft/GenerateEndRiftEntityTextures.py``): percentile-normalised
source luminance, authored motifs, rune decals and rim light.  Keeping a second
hand-written painter here would let the shipped PNGs drift away from their code,
so this module is now a thin entry point that still exposes the historical CLI
and the rig-footprint helper but takes the bytes from the one canonical painter.

``resourcepacks/tools/wave7_uv_layout.py`` remains the single footprint source of
truth for the coverage contract in ``tests/test_wave7_actor_skin_coverage.py``:
the tests still measure the real ``texOffs``/``addBox`` rectangles of the rigs.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from typing import Callable, NamedTuple

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import wave7_uv_layout as layout  # noqa: E402  (shared footprint source of truth)

# ``parents[2]`` is the repository root.
ROOT = Path(__file__).resolve().parents[2]
TEXTURE_ROOT = (
    ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets"
    / "copimineclient" / "textures" / "entity"
)
PAINTER = ROOT / "scripts" / "minecraft" / "GenerateEndRiftEntityTextures.py"

_CACHE = None


def _canonical():
    """Load the one canonical painter and paint every sheet exactly once."""
    global _CACHE
    if _CACHE is None:
        spec = importlib.util.spec_from_file_location("end_rift_entity_textures", PAINTER)
        module = importlib.util.module_from_spec(spec)
        # dataclasses resolve their own module through sys.modules.
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        images = module.all_outputs()
        # Publish the module and its completed map together, only after painting
        # succeeds; a failed attempt leaves the cache empty and can be retried.
        _CACHE = (module, images)
    return _CACHE


class ActorSkin(NamedTuple):
    name: str
    builder: Callable[[], Image.Image]


def _sheet(name: str) -> Image.Image:
    _, images = _canonical()
    # Hand callers their own image: Pillow mutates in place, and the cached
    # original must stay pristine for generate().
    return images[Path(name).stem].copy()


SKINS: tuple[ActorSkin, ...] = (
    ActorSkin("end_rift_wave7_warden.png", lambda: _sheet("end_rift_wave7_warden")),
    ActorSkin("end_rift_wave7_archmage.png", lambda: _sheet("end_rift_wave7_archmage")),
    ActorSkin("end_rift_wave7_marksman.png", lambda: _sheet("end_rift_wave7_marksman")),
)


def output_paths() -> list[Path]:
    """Every file this generator writes; all of them live in the client root."""
    return [TEXTURE_ROOT / skin.name for skin in SKINS]


def generate(directory: Path | None = None) -> list[Path]:
    """Write every actor skin, returning the paths actually written."""
    painter, images = _canonical()
    target_root = TEXTURE_ROOT if directory is None else Path(directory)
    written: list[Path] = []
    for skin in SKINS:
        image = images[Path(skin.name).stem]
        assert image.mode == "RGBA", skin.name
        target = target_root / skin.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(painter.encode(image))
        written.append(target)
        print("wrote", target, image.size)
    return written


def main() -> None:
    generate()


if __name__ == "__main__":
    main()
