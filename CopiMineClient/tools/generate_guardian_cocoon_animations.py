"""Author the cocoon descent/landing on the supplied Guardian bone hierarchy.

Only additive source-space tracks are written. Geometry, pivots, textures and
the artist's original clips are never rewritten by this generator.
"""
from pathlib import Path
import argparse
import json

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations"


def pose(body, height, head, arm, spread, thigh, shin, twist=0):
    return {
        "body": {"rotation": [body, twist, twist * .35], "position": [0, height, 0]},
        "head": {"rotation": [head, -twist * .65, -twist * .2]},
        "left_hand": {"rotation": [arm + twist * .4, 0, -spread]},
        "right_hand": {"rotation": [arm - twist * .4, 0, spread]},
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
        (.2, pose(-6, 0, 10, -15, 24, 5, -10, -5)),
        (.55, pose(9, -.3, -5, 18, 35, 12, -20, 7)),
        (1.05, pose(19, -.8, -12, 36, 32, 23, -38, -4)),
        (1.55, pose(28, -2, -17, 49, 37, 30, -51, 3)),
        (2.0, BRACE),
    ]),
    "cocoon_land": (1.0, [
        (0.0, BRACE),
        (.08, pose(44, -7, -22, 74, 44, 48, -78, 3)),
        (.24, pose(31, -4.5, -16, 46, 28, 34, -55, -4)),
        (.48, pose(11, -1.4, -6, 13, 12, 13, -23, 2)),
        (.72, pose(-5, .5, 7, -12, 9, -2, 4, -1)),
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
