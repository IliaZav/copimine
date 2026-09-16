#!/usr/bin/env python3
"""Normalize the supplied Rift Guardian atlas to the approved reference palette.

The geometry and UV layout remain artist-owned.  The export contains several
vivid accent colours that are not present in the user's purple/white reference
and become large green/cyan/red panels when the six-face UVs are rendered in
Minecraft.  This deterministic pass changes only those exact source swatches;
transparent pixels, layout, dimensions, and the existing purple/white shading
are preserved.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TEXTURE = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "entity"
    / "end_rift_user_boss.png"
)

# The dark purple, neutral, and violet swatches are already part of the
# approved reference.  Only export colours that create vivid non-purple
# surfaces are normalized.  Keeping this table explicit makes the asset
# change reviewable and the operation idempotent.
REFERENCE_SWATCHES = {
    (180, 212, 225): (179, 179, 179),
    (248, 221, 114): (255, 255, 255),
    (83, 97, 116): (33, 11, 41),
    (91, 188, 244): (87, 0, 118),
    (123, 212, 255): (87, 0, 118),
    (236, 248, 253): (255, 255, 255),
    (244, 134, 134): (255, 255, 255),
    (67, 232, 141): (255, 255, 255),
    (253, 249, 255): (255, 255, 255),
    (255, 248, 153): (255, 255, 255),
    (110, 120, 140): (179, 179, 179),
}


def normalize(input_path: Path, output_path: Path) -> dict[tuple[int, int, int], int]:
    with Image.open(input_path).convert("RGBA") as image:
        if image.size != (128, 128):
            raise ValueError(f"expected a 128x128 boss atlas, got {image.size}")
        pixels = list(image.getdata())

    counts: dict[tuple[int, int, int], int] = {}
    normalized = []
    for red, green, blue, alpha in pixels:
        source = (red, green, blue)
        target = REFERENCE_SWATCHES.get(source, source)
        if alpha > 0 and target != source:
            counts[source] = counts.get(source, 0) + 1
        normalized.append((*target, alpha))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    result = Image.new("RGBA", (128, 128))
    result.putdata(normalized)
    result.save(output_path, format="PNG", optimize=False)
    return counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", nargs="?", type=Path, default=DEFAULT_TEXTURE)
    parser.add_argument("output", nargs="?", type=Path, default=DEFAULT_TEXTURE)
    args = parser.parse_args()
    changed = normalize(args.input, args.output)
    print(f"normalized={args.output} swatches={sum(changed.values())}")
    for source, count in sorted(changed.items()):
        print(f"  {source} -> {REFERENCE_SWATCHES[source]} pixels={count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
