"""Author the cocoon descent/landing on the supplied Guardian bone hierarchy.

Only additive source-space tracks are written. Geometry, pivots, textures and
the artist's original clips are never rewritten by this generator.
"""
from pathlib import Path
import argparse
import json

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations"


def pose(body, height, head, arm, spread, thigh, shin):
    return {
        "body": {"rotation": [body, 0, 0], "position": [0, height, 0]},
        "head": {"rotation": [head, 0, 0]},
        "left_hand": {"rotation": [arm, 0, -spread]},
        "right_hand": {"rotation": [arm, 0, spread]},
        "group6": {"rotation": [thigh, 0, 0]},
        "group5": {"rotation": [thigh, 0, 0]},
        "group2": {"rotation": [shin, 0, 0]},
        "group": {"rotation": [shin, 0, 0]},
    }


NEUTRAL = pose(0, 0, 0, 0, 0, 0, 0)
BRACE = pose(32, -3, -18, 55, 35, 34, -58)
CLIPS = {
    "cocoon_fall": (2.0, [
        (0.0, NEUTRAL),
        (0.3, pose(14, -.5, -8, 20, 20, 8, -12)),
        (1.2, pose(24, -1.5, -14, 40, 30, 26, -44)),
        (2.0, BRACE),
    ]),
    "cocoon_land": (1.0, [
        (0.0, BRACE),
        (.15, pose(42, -7, -20, 70, 42, 48, -78)),
        (.45, pose(12, -2, -8, 18, 10, 16, -28)),
        (.7, pose(-4, 1, 5, -8, 5, -2, 4)),
        (1.0, NEUTRAL),
    ]),
}


def document(name, length, frames):
    bones = {}
    for time, frame in frames:
        for bone, tracks in frame.items():
            for channel, vector in tracks.items():
                bones.setdefault(bone, {}).setdefault(channel, {})[f"{time:g}"] = {"vector": vector}
    return {"format_version": "1.8.0", "animations": {
        f"animation.guardian.{name}": {"animation_length": length, "loop": False, "bones": bones}
    }}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for name, (length, frames) in CLIPS.items():
        path = OUTPUT / f"{name}.animation.json"
        expected = (json.dumps(document(name, length, frames), indent=2) + "\n").encode("utf-8")
        if args.check:
            if not path.is_file() or path.read_bytes() != expected:
                raise SystemExit(f"Generated cocoon animation differs: {path}")
        else:
            path.write_bytes(expected)
        print(f"{name}: {length}s, {len(frames)} key poses")


if __name__ == "__main__":
    main()
