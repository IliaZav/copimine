#!/usr/bin/env python3
"""Generate the four crisp 32x32 prisoner ability icons from pixel grids."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/copimineclient/textures/gui"
SCALE = 2

PALETTES = {
    "heal": {"o": (16, 38, 56, 255), "c": (22, 134, 177, 255),
             "b": (55, 219, 241, 255), "w": (234, 255, 255, 255)},
    "surge": {"o": (70, 36, 10, 255), "r": (207, 92, 18, 255),
              "g": (255, 174, 35, 255), "w": (255, 239, 159, 255)},
    "guardian": {"o": (32, 23, 68, 255), "p": (91, 58, 166, 255),
                 "v": (163, 119, 255, 255), "c": (107, 236, 246, 255)},
    "turncoat": {"o": (36, 21, 73, 255), "p": (108, 49, 183, 255),
                 "v": (197, 98, 255, 255), "r": (255, 94, 123, 255),
                 "w": (255, 226, 240, 255)},
}

GRIDS = {
    "heal": [
        "................", "...oo....oo.....", "..ocbo..ocbo....",
        ".ocbbbocbbbco...", ".ocbbbbbbbbbc...", "ocbbbbbbbbbbbc..",
        "ocbbbbbbbbbbbc..", "ocbbbbbbbbbbbc..", ".ocbbbbbbbbbc...",
        "..ocbbbbbbbc....", "...ocbbbbbc.....", "....ocbbbc......",
        ".....ocbc.......", "......oc........", "................",
        "................",
    ],
    "surge": [
        "................", "........oo......", ".......orgo.....",
        "......orggggo...", ".....orgggggo...", "....orggggo.....",
        "...orggggo......", "..orggggo.......", "..oggggo........",
        "...ogggggo......", "....ogggggo.....", ".....oggggggo...",
        "......oggggggo..", ".......oooooo...", "................",
        "................",
    ],
    "guardian": [
        "................", ".....oooooo.....", "...ooppppppoo...",
        "..opvvvvvvvvpo..", ".opvvvvvvvvvvpo.", ".opvvvcccvvvvpo.",
        ".opvvcc..ccvvpo.", ".opvvcc..ccvvpo.", ".opvvvcccvvvvpo.",
        ".opvvvvvvvvvvpo.", "..opvvvvvvvvpo..", "...opvvvvvvpo...",
        "....opvvvvpo....", ".....opvvpo.....", "......oppo......",
        "................",
    ],
    "turncoat": [
        "................", "..oooooo........", ".ooppppoo..rr...",
        ".opvvvvvpo..rwr.", ".opvwwwwpo..rr..", ".opvvvvvpo......",
        ".opvvvvvpo......", "..opvvvpo.......", "...oppppo.......",
        "....oooo..rrrr..", "..........r..r..", ".........r....r.",
        "..........r..r..", "............rr..", "................",
        "................",
    ],
}


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, rows in GRIDS.items():
        if len(rows) != 16 or any(len(row) != 16 for row in rows):
            raise ValueError(f"{name}: every icon grid must be exactly 16x16")
        palette = PALETTES[name]
        image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        pixels = image.load()
        for y, row in enumerate(rows):
            for x, key in enumerate(row):
                if key == ".":
                    continue
                color = palette[key]
                for sy in range(SCALE):
                    for sx in range(SCALE):
                        pixels[x * SCALE + sx, y * SCALE + sy] = color
        if name == "turncoat":
            image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
            pixels = image.load()

            def paint_cell(x: int, y: int, key: str) -> None:
                color = palette[key]
                for sy in range(SCALE):
                    for sx in range(SCALE):
                        pixels[x * SCALE + sx, y * SCALE + sy] = color

            # A hostile face changes allegiance as a clear rightward arrow.
            for y in range(2, 11):
                for x in range(2, 9):
                    edge = x in (2, 8) or y in (2, 10)
                    paint_cell(x, y, "o" if edge else "p")
            for x in range(3, 8):
                paint_cell(x, 3, "v")
            paint_cell(4, 5, "w")
            paint_cell(6, 5, "w")
            for x in range(4, 7):
                paint_cell(x, 8, "o")
            for x in range(8, 12):
                paint_cell(x, 6, "r")
            for x, y in ((11, 4), (12, 5), (13, 6), (14, 7),
                         (13, 8), (12, 9), (11, 10)):
                paint_cell(x, y, "r")
        if name == "heal":
            # A bright pixel cross makes the heart read as an active mend icon.
            for y in range(6, 10):
                for x in range(6, 10):
                    if 7 <= x <= 8 or 7 <= y <= 8:
                        pixels[x * SCALE, y * SCALE] = palette["w"]
                        pixels[x * SCALE + 1, y * SCALE] = palette["w"]
                        pixels[x * SCALE, y * SCALE + 1] = palette["w"]
                        pixels[x * SCALE + 1, y * SCALE + 1] = palette["w"]
        image.save(OUTPUT / f"end_rift_prisoner_{name}.png", optimize=False)


if __name__ == "__main__":
    main()
