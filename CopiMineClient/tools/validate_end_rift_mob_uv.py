"""Validate every End Rift wave-mob texture as a native, assembled UV input.

The validator deliberately checks the things that caused the old screenshots to
look broken: the declared atlas size, fully opaque texels, and a restrained
palette.  It does not pretend to prove the live Minecraft renderer; the Java
model tests and the native runtime capture are separate evidence layers.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


EXPECTED_MOB_ATLASES = {
    "end_rift_enderman.png": (64, 32),
    "end_rift_elite.png": (64, 32),
    "end_rift_wave_guardian_enderman.png": (64, 32),
    "end_rift_ritual_guard_enderman.png": (64, 32),
    "end_rift_ritual_caster.png": (64, 32),
    "end_rift_spider.png": (64, 32),
    "end_rift_elite_spider.png": (64, 32),
    "end_rift_wave_guardian_spider.png": (64, 32),
    "end_rift_ritual_guard_spider.png": (64, 32),
    "end_rift_skeleton.png": (64, 32),
    "end_rift_elite_skeleton.png": (64, 32),
    "end_rift_wave_guardian_skeleton.png": (64, 32),
    "end_rift_ritual_guard_skeleton.png": (64, 32),
}


def validate_atlases(directory: Path) -> dict[str, list[str]]:
    """Return deterministic validation issues keyed by runtime asset name."""
    report: dict[str, list[str]] = {}
    for name, expected_size in EXPECTED_MOB_ATLASES.items():
        path = directory / name
        issues: list[str] = []
        if not path.is_file():
            report[name] = ["missing"]
            continue
        try:
            with Image.open(path).convert("RGBA") as image:
                if image.size != expected_size:
                    issues.append(f"size={image.size}, expected={expected_size}")
                alpha = {pixel[3] for pixel in image.getdata()}
                if alpha != {255}:
                    issues.append(f"alpha={sorted(alpha)}")
                colours = {pixel[:3] for pixel in image.getdata()}
                maximum = 16 if "skeleton" in name else 14
                if len(colours) > maximum:
                    issues.append(f"palette={len(colours)}, max={maximum}")
        except (OSError, ValueError) as exc:
            issues.append(f"unreadable={exc}")
        report[name] = issues
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path, nargs="?", default=Path(__file__).resolve().parents[1]
                        / "src/main/resources/assets/copimineclient/textures/entity")
    args = parser.parse_args()
    report = validate_atlases(args.directory)
    failed = False
    for name, issues in report.items():
        if issues:
            failed = True
            print(f"FAIL {name}: {'; '.join(issues)}")
        else:
            print(f"OK   {name}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
