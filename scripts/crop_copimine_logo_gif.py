"""Create the tightly framed header variant of the supplied CopiMine GIF."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def content_bounds(frames: list[Image.Image], padding: int) -> tuple[int, int, int, int]:
    boxes = [frame.getchannel("A").getbbox() for frame in frames]
    boxes = [box for box in boxes if box is not None]
    if not boxes:
        raise ValueError("GIF has no visible pixels")

    left = max(0, min(box[0] for box in boxes) - padding)
    top = max(0, min(box[1] for box in boxes) - padding)
    right = min(frames[0].width, max(box[2] for box in boxes) + padding)
    bottom = min(frames[0].height, max(box[3] for box in boxes) + padding)
    return left, top, right, bottom


def crop_logo(source: Path, destination: Path, padding: int) -> None:
    with Image.open(source) as image:
        frames: list[Image.Image] = []
        durations: list[int] = []
        for index in range(image.n_frames):
            image.seek(index)
            frames.append(image.convert("RGBA").copy())
            durations.append(image.info.get("duration", 20))
        if len(frames) < 2:
            raise ValueError("The supplied logo must remain animated")

        box = content_bounds(frames, padding)
        cropped = [frame.crop(box) for frame in frames]
        destination.parent.mkdir(parents=True, exist_ok=True)
        cropped[0].save(
            destination,
            format="GIF",
            save_all=True,
            append_images=cropped[1:],
            duration=durations,
            loop=image.info.get("loop", 0),
            disposal=2,
            transparency=0,
            optimize=False,
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--padding", type=int, default=24)
    args = parser.parse_args()
    crop_logo(args.source, args.destination, args.padding)


if __name__ == "__main__":
    main()
