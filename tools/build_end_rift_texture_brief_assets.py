from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "texture-brief-assets"
FONT_PATHS = (
    Path("C:/Windows/Fonts/arial.ttf"),
    Path("C:/Windows/Fonts/segoeui.ttf"),
)

BLOCK_ITEMS = [
    ("block/end_event_core.png", "Ядро: блок"),
    ("block/end_event_core_charged.png", "Ядро: заряжено"),
    ("block/end_event_rune.png", "Руна: свободна"),
    ("block/end_event_rune_occupied.png", "Руна: занята"),
    ("item/end_event_core.png", "Предмет: ядро"),
    ("item/end_event_core_charged.png", "Предмет: заряжено"),
    ("item/end_event_pad.png", "Предмет: руна"),
    ("item/end_event_pad_occupied.png", "Предмет: руна занята"),
    ("item/rift_core_shard.png", "Награда: осколок"),
    ("item/end_event_boss.png", "Иконка босса"),
]

ENTITIES = [
    ("end_rift_spider.png", "Паук"),
    ("end_rift_skeleton.png", "Скелет"),
    ("end_rift_elite_skeleton.png", "Элитный скелет"),
    ("end_rift_enderman.png", "Эндермен"),
    ("end_rift_elite.png", "Элитный эндермен"),
    ("end_rift_guardian.png", "Страж волны"),
    ("end_rift_shulker.png", "Шалкер (резерв)"),
]

BOSS_PHASES = [
    ("rift_guardian_awakening.png", "Босс: Пробуждение"),
    ("rift_guardian_hunter.png", "Босс: Охотник"),
    ("rift_guardian_distortion.png", "Босс: Искажение"),
    ("rift_guardian_absorption.png", "Босс: Поглощение"),
    ("rift_guardian_catastrophe.png", "Босс: Катастрофа"),
]


def font(size: int):
    for path in FONT_PATHS:
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def fit_nearest(image: Image.Image, max_width: int, max_height: int) -> Image.Image:
    source = image.convert("RGBA")
    scale = min(max_width / source.width, max_height / source.height)
    scale = max(scale, 1.0)
    size = (max(1, round(source.width * scale)), max(1, round(source.height * scale)))
    return source.resize(size, Image.Resampling.NEAREST)


def draw_checker(canvas: Image.Image, box: tuple[int, int, int, int], cell: int = 16) -> None:
    draw = ImageDraw.Draw(canvas)
    left, top, right, bottom = box
    colors = ((33, 39, 54, 255), (45, 52, 70, 255))
    for y in range(top, bottom, cell):
        for x in range(left, right, cell):
            color = colors[((x - left) // cell + (y - top) // cell) % 2]
            draw.rectangle((x, y, min(x + cell, right), min(y + cell, bottom)), fill=color)


def make_sheet(items: list[tuple[Path, str]], output: Path, title: str, columns: int, cell_w: int, cell_h: int) -> None:
    margin = 28
    title_h = 54
    rows = (len(items) + columns - 1) // columns
    canvas = Image.new("RGBA", (margin * 2 + columns * cell_w, margin * 2 + title_h + rows * cell_h), (13, 17, 29, 255))
    draw = ImageDraw.Draw(canvas)
    draw.text((margin, margin), title, fill=(236, 243, 255, 255), font=font(27))
    label_font = font(18)
    path_font = font(13)
    for index, (path, label) in enumerate(items):
        row, column = divmod(index, columns)
        left = margin + column * cell_w
        top = margin + title_h + row * cell_h
        right = left + cell_w
        bottom = top + cell_h
        draw.rounded_rectangle((left + 6, top + 6, right - 6, bottom - 6), radius=10, fill=(23, 29, 45, 255), outline=(67, 79, 105, 255), width=2)
        image_box = (left + 22, top + 22, right - 22, top + cell_h - 66)
        draw_checker(canvas, image_box)
        image = Image.open(path).convert("RGBA")
        fitted = fit_nearest(image, image_box[2] - image_box[0] - 10, image_box[3] - image_box[1] - 10)
        x = image_box[0] + (image_box[2] - image_box[0] - fitted.width) // 2
        y = image_box[1] + (image_box[3] - image_box[1] - fitted.height) // 2
        canvas.alpha_composite(fitted, (x, y))
        draw.text((left + 20, bottom - 54), label, fill=(245, 247, 252, 255), font=label_font)
        draw.text((left + 20, bottom - 31), path.name, fill=(155, 171, 196, 255), font=path_font)
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output, format="PNG", optimize=True)


def resolve(relative: str) -> Path:
    return ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / relative


def main() -> int:
    missing: list[str] = []
    block_items = []
    for relative, label in BLOCK_ITEMS:
        path = resolve(relative)
        if not path.exists():
            missing.append(str(path))
        else:
            block_items.append((path, label))
    entities = []
    for name, label in ENTITIES:
        path = ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity" / name
        if not path.exists():
            missing.append(str(path))
        else:
            entities.append((path, label))
    phases = []
    for name, label in BOSS_PHASES:
        path = ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity" / name
        if not path.exists():
            missing.append(str(path))
        else:
            phases.append((path, label))
    if missing:
        raise FileNotFoundError("Missing texture files:\n" + "\n".join(missing))
    OUT.mkdir(parents=True, exist_ok=True)
    make_sheet(block_items, OUT / "current_block_item_textures.png", "ТЕКУЩИЕ БЛОКИ И ПРЕДМЕТЫ", 3, 285, 258)
    make_sheet(entities + phases, OUT / "current_entity_and_boss_textures.png", "ТЕКУЩИЕ МОБЫ И БОСС", 3, 285, 258)
    print(f"Wrote {OUT / 'current_block_item_textures.png'}")
    print(f"Wrote {OUT / 'current_entity_and_boss_textures.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
