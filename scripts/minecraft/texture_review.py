"""Read-only authored-texture review for the End Rift visual increments.

Three roots matter and must not be confused:

* ``resourcepacks/src``                              - the shipped CopiMine pack
* ``tools/minecraft-26.3/client/src/main/resources`` - the ACTIVE Fabric client
* ``CopiMineClient/src/main/resources``              - the legacy client tree

``tools/minecraft-26.3/client/build.gradle`` adds the legacy tree as a resource
source set and copies the pack into the jar, so the built client carries BOTH
trees. A sheet that exists in two of them has to be changed in both.

For every PNG the script prints size, mode and the opaque fraction, then the two
derived checks that are the measurable form of "the art jumps":

* non-power-of-two sheets (informational only: modern Minecraft samples NPOT
  textures directly, so this is not a defect),
* families of similarly named sheets whose members disagree on size (they share
  normalised UV boxes, so a mismatch stretches one stage against its siblings).

Read-only: it measures, it does not repaint. Run it from the repository root:

    python scripts/minecraft/texture_review.py
"""
import pathlib
import re
from collections import defaultdict

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
ROOTS = {
    "pack": ROOT / "resourcepacks/src",
    "client-active": ROOT / "tools/minecraft-26.3/client/src/main/resources",
    "client-legacy": ROOT / "CopiMineClient/src/main/resources",
}

VARIANT = re.compile(
    r"(full|damaged|critical|pulse|hd|idle|active|inactive|glow|flash|broken|intact|"
    r"stage\d+|phase\d+|small|large|_?v\d+|\d+)$")


def family_key(stem):
    parts = stem.split("_")
    while len(parts) > 1 and VARIANT.fullmatch(parts[-1]):
        parts = parts[:-1]
    return "_".join(parts)


def opaque_fraction(image):
    if image.mode != "RGBA":
        return 1.0
    alpha = image.getchannel("A")
    return sum(1 for v in alpha.getdata() if v > 0) / (image.width * image.height)


def main():
    missing = [str(base) for base in ROOTS.values() if not base.is_dir()]
    if missing:
        raise SystemExit("texture root(s) not found: " + ", ".join(missing))

    rows = []
    for label, base in ROOTS.items():
        for path in sorted(base.rglob("*.png")):
            with Image.open(path) as image:
                image.load()
                rows.append((label, str(path.relative_to(ROOT)), image.size, image.mode,
                             opaque_fraction(image)))

    print(f"total textures: {len(rows)}")
    for label in ROOTS:
        print(f"  {label}: {sum(1 for r in rows if r[0] == label)}")

    print("\n--- fully transparent sheets (a dead asset) ---")
    dead = [r for r in rows if r[4] == 0.0]
    print("  none" if not dead else "\n".join(f"  {r[1]}" for r in dead))

    print("\n--- non-power-of-two sheets ---")
    for label, name, (w, h), mode, frac in rows:
        if w & (w - 1) or h & (h - 1):
            print(f"  [{label}] {w}x{h} {mode} opaque={frac:.3f} {name}")

    print("\n--- families whose members disagree on size ---")
    families = defaultdict(list)
    for label, name, size, mode, frac in rows:
        families[(label, family_key(pathlib.Path(name).stem))].append((name, size))
    for (label, key), members in sorted(families.items()):
        sizes = {size for _, size in members}
        if len(members) > 1 and len(sizes) > 1:
            print(f"  [{label}] {key}")
            for name, size in members:
                print(f"      {size[0]:>5}x{size[1]:<5} {name}")


if __name__ == "__main__":
    main()