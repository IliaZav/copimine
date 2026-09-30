"""Render assembled, inspectable previews for every End Rift mob role.

This is a deterministic art-review board built from the same UV sheets and
role matrix used by the client.  It is deliberately labelled as a static
assembled preview: it verifies that the atlas and the intended silhouette read
together, while the Java model tests verify the actual Minecraft model tree.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/copimineclient/textures/entity"
EVIDENCE = ROOT.parent / "artifacts/end-rift-v3-evidence"
OUT = EVIDENCE / "model-previews"
BOARD = EVIDENCE / "end-rift-mob-model-board-20260916.png"
MANIFEST = EVIDENCE / "end-rift-mob-model-preview-manifest.json"


@dataclass(frozen=True)
class RoleSpec:
    slug: str
    label: str
    family: str
    role: str
    texture: str
    geometry: str
    animation: str
    hitbox: str
    variant: str


ROLE_SPECS: tuple[RoleSpec, ...] = (
    RoleSpec("enderman-ordinary", "Ordinary Enderman", "enderman", "ordinary",
             "end_rift_enderman.png", "end_rift_enderman_v1", "END_RIFT_ENDERMAN", "0.6 x 2.9", "ORDINARY"),
    RoleSpec("enderman-elite", "Elite Enderman", "enderman", "elite",
             "end_rift_elite.png", "end_rift_elite_v1", "END_RIFT_ELITE", "0.6 x 2.9", "ELITE"),
    RoleSpec("enderman-wave-guardian", "Wave Guardian Enderman", "enderman", "wave_guardian",
             "end_rift_wave_guardian_enderman.png", "end_rift_wave_guardian_enderman_v1",
             "END_RIFT_WAVE_GUARDIAN", "0.6 x 2.9", "WAVE_GUARDIAN"),
    RoleSpec("enderman-ritual-guard", "Ritual Guard Enderman", "enderman", "ritual_guard",
             "end_rift_ritual_guard_enderman.png", "end_rift_ritual_guard_enderman_v1",
             "END_RIFT_RITUAL_GUARD", "0.6 x 2.9", "RITUAL_GUARD"),
    RoleSpec("enderman-ritual-caster", "Wave 6 Ritual Caster", "enderman", "ritual_caster",
             "end_rift_ritual_caster.png", "end_rift_ritual_caster_v1", "END_RIFT_RITUAL_CASTER",
             "0.6 x 2.9", "RITUAL_CASTER"),
    RoleSpec("skeleton-ordinary", "Ordinary Skeleton", "skeleton", "ordinary",
             "end_rift_skeleton.png", "end_rift_skeleton_v1", "END_RIFT_SKELETON", "0.6 x 1.99", "ORDINARY"),
    RoleSpec("skeleton-elite", "Elite Skeleton", "skeleton", "elite",
             "end_rift_elite_skeleton.png", "end_rift_elite_skeleton_v1", "END_RIFT_ELITE_SKELETON", "0.6 x 1.99", "ELITE"),
    RoleSpec("skeleton-wave-guardian", "Wave Guardian Skeleton", "skeleton", "wave_guardian",
             "end_rift_wave_guardian_skeleton.png", "end_rift_wave_guardian_skeleton_v1",
             "END_RIFT_WAVE_GUARDIAN_SKELETON", "0.6 x 1.99", "WAVE_GUARDIAN"),
    RoleSpec("skeleton-ritual-guard", "Ritual Guard Skeleton", "skeleton", "ritual_guard",
             "end_rift_ritual_guard_skeleton.png", "end_rift_ritual_guard_skeleton_v1",
             "END_RIFT_RITUAL_GUARD_SKELETON", "0.6 x 1.99", "RITUAL_GUARD"),
    RoleSpec("spider-ordinary", "Ordinary Spider", "spider", "ordinary",
             "end_rift_spider.png", "end_rift_spider_v1", "END_RIFT_SPIDER", "1.4 x 0.9", "ORDINARY"),
    RoleSpec("spider-elite", "Elite Spider", "spider", "elite",
             "end_rift_elite_spider.png", "end_rift_elite_spider_v1", "END_RIFT_ELITE_SPIDER",
             "1.4 x 0.9", "ELITE"),
    RoleSpec("spider-wave-guardian", "Wave Guardian Spider", "spider", "wave_guardian",
             "end_rift_wave_guardian_spider.png", "end_rift_wave_guardian_spider_v1",
             "END_RIFT_WAVE_GUARDIAN_SPIDER", "1.4 x 0.9", "WAVE_GUARDIAN"),
    RoleSpec("spider-ritual-guard", "Ritual Guard Spider", "spider", "ritual_guard",
             "end_rift_ritual_guard_spider.png", "end_rift_ritual_guard_spider_v1",
             "END_RIFT_RITUAL_GUARD_SPIDER", "1.4 x 0.9", "RITUAL_GUARD"),
)


def _font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = (
        Path("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    )
    for candidate in candidates:
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def _palette(texture: Path) -> tuple[tuple[int, int, int, int], ...]:
    with Image.open(texture).convert("RGBA") as image:
        colours = list({pixel for pixel in image.getdata()})
    colours.sort(key=lambda colour: (sum(colour[:3]), max(colour[:3])))
    dark = colours[0]
    light = colours[-1]
    accent_candidates = [colour for colour in colours if colour[2] >= colour[0] and colour[2] > colour[1] * 1.5]
    accent = max(accent_candidates or colours, key=lambda colour: colour[2] + colour[0] - colour[1])
    mid = colours[len(colours) // 2]
    bone_candidates = [colour for colour in colours if min(colour[:3]) >= 170]
    bone = max(bone_candidates or (light,), key=lambda colour: sum(colour[:3]))
    return dark, mid, accent, light, bone


def _poly(draw: ImageDraw.ImageDraw, points: Iterable[tuple[int, int]], fill: tuple[int, int, int, int],
          outline: tuple[int, int, int, int] | None = None, width: int = 1) -> None:
    draw.polygon(list(points), fill=fill)
    if outline:
        draw.line(list(points) + [list(points)[0]], fill=outline, width=width, joint="curve")


def _line(draw: ImageDraw.ImageDraw, points: Iterable[tuple[int, int]], fill: tuple[int, int, int, int], width: int = 2) -> None:
    draw.line(list(points), fill=fill, width=width, joint="curve")


def _draw_humanoid(draw: ImageDraw.ImageDraw, spec: RoleSpec,
                   palette: tuple[tuple[int, int, int, int], ...], cx: int, base: int) -> tuple[int, int, int, int]:
    dark, mid, accent, light, bone = palette
    edge = tuple(max(0, channel - 18) for channel in accent[:3]) + (255,)
    top = base - 292
    head_top, head_bottom = top + 8, top + 45
    torso_top, torso_bottom = top + 43, top + 151
    shoulder = torso_top + 7
    if spec.family == "skeleton":
        body_w = 28
        arm_w = 12
        leg_w = 12
        fill = bone
        shell = dark
    else:
        body_w = 48
        arm_w = 15
        leg_w = 18
        fill = mid
        shell = dark

    # Subtle ground shadow keeps the card readable without adding texture noise.
    draw.ellipse((cx - 64, base - 4, cx + 64, base + 11), fill=(3, 2, 8, 220))

    # The long-limbed base silhouette mirrors the actual Biped/Skeleton rig.
    if spec.family == "skeleton":
        _poly(draw, ((cx - body_w // 2, torso_top), (cx + body_w // 2, torso_top),
                     (cx + body_w // 2 - 3, torso_bottom), (cx - body_w // 2 + 3, torso_bottom)),
              shell, edge)
        draw.rectangle((cx - 6, torso_top + 10, cx + 6, torso_bottom - 5), fill=fill)
        for y in (torso_top + 23, torso_top + 43, torso_top + 63, torso_top + 83):
            _line(draw, ((cx - 12, y), (cx - 3, y + 3), (cx - 1, y)), bone, 3)
            _line(draw, ((cx + 12, y), (cx + 3, y + 3), (cx + 1, y)), bone, 3)
        draw.rectangle((cx - 3, torso_top + 25, cx + 3, torso_top + 59), fill=accent)
    else:
        _poly(draw, ((cx - body_w // 2, torso_top), (cx + body_w // 2, torso_top),
                     (cx + body_w // 2 + 4, torso_bottom), (cx - body_w // 2 - 4, torso_bottom)),
              fill, edge)
        _poly(draw, ((cx - 3, torso_top + 12), (cx + 4, torso_top + 5),
                     (cx + 2, torso_top + 45), (cx - 2, torso_top + 75),
                     (cx + 4, torso_bottom - 12), (cx - 4, torso_bottom - 25)), accent)
        draw.rectangle((cx - 2, torso_top + 34, cx + 3, torso_top + 55), fill=light)

    # Head and face plane.
    _poly(draw, ((cx - 22, head_top + 4), (cx + 19, head_top + 4),
                 (cx + 18, head_bottom - 2), (cx + 11, head_bottom + 5),
                 (cx - 19, head_bottom + 5), (cx - 23, head_bottom - 3)),
          fill if spec.family != "skeleton" else shell, edge)
    draw.rectangle((cx - 13, head_top + 15, cx - 5, head_top + 20), fill=accent)
    draw.rectangle((cx + 6, head_top + 15, cx + 14, head_top + 20), fill=accent)
    _line(draw, ((cx - 10, head_bottom - 2), (cx - 2, head_bottom + 2),
                 (cx + 7, head_bottom - 1)), bone if spec.family == "skeleton" else light, 3)

    # Long segmented arms, with the raised cast pose reserved for the caster.
    caster = spec.role == "ritual_caster"
    if caster:
        left_points = ((cx - 20, shoulder + 4), (cx - 36, shoulder - 30),
                       (cx - 29, shoulder - 36), (cx - 11, shoulder + 1))
        right_points = ((cx + 20, shoulder + 4), (cx + 36, shoulder - 30),
                        (cx + 29, shoulder - 36), (cx + 11, shoulder + 1))
    else:
        left_points = ((cx - 18, shoulder), (cx - 31, torso_top + 80),
                       (cx - 43, torso_top + 156), (cx - 31, torso_top + 160),
                       (cx - 17, torso_top + 89))
        right_points = ((cx + 18, shoulder), (cx + 31, torso_top + 80),
                        (cx + 43, torso_top + 156), (cx + 31, torso_top + 160),
                        (cx + 17, torso_top + 89))
    _poly(draw, left_points, fill if spec.family != "skeleton" else shell, edge)
    _poly(draw, right_points, fill if spec.family != "skeleton" else shell, edge)
    cuff = bone if spec.family == "skeleton" else light
    if caster:
        _line(draw, ((cx - 31, shoulder - 25), (cx - 29, shoulder - 33)), cuff, 5)
        _line(draw, ((cx + 31, shoulder - 25), (cx + 29, shoulder - 33)), cuff, 5)
        draw.ellipse((cx - 11, shoulder - 62, cx + 11, shoulder - 40), fill=accent, outline=light, width=2)
        draw.ellipse((cx - 5, shoulder - 56, cx + 5, shoulder - 46), fill=light)
    else:
        _line(draw, ((cx - 38, torso_top + 121), (cx - 34, torso_top + 135)), cuff, 5)
        _line(draw, ((cx + 38, torso_top + 121), (cx + 34, torso_top + 135)), cuff, 5)

    # Narrow legs and articulated shins.
    leg_top = torso_bottom - 3
    for sign in (-1, 1):
        lx = cx + sign * 12
        if spec.family == "skeleton":
            draw.rectangle((lx - leg_w // 2, leg_top, lx + leg_w // 2, base - 17), fill=shell, outline=edge)
            draw.rectangle((lx - 4, leg_top + 6, lx + 4, base - 47), fill=bone)
            draw.rectangle((lx - 7, base - 45, lx + 7, base - 37), fill=accent)
            draw.rectangle((lx - 4, base - 34, lx + 4, base - 6), fill=bone)
        else:
            draw.rectangle((lx - leg_w // 2, leg_top, lx + leg_w // 2, base - 9), fill=fill, outline=edge)
            draw.rectangle((lx - 7, leg_top + 45, lx + 7, base - 36), fill=light)
            draw.rectangle((lx - 10, base - 34, lx + 10, base - 25), fill=dark)
            draw.rectangle((lx - 7, base - 22, lx + 7, base - 6), fill=light)

    # Role-specific readable geometry, matching the named Java parts.
    if spec.role in {"elite", "wave_guardian", "ritual_guard"}:
        _poly(draw, ((cx - 28, shoulder + 2), (cx - 47, shoulder + 7),
                     (cx - 43, shoulder + 24), (cx - 20, shoulder + 16)), fill, edge)
        _poly(draw, ((cx + 28, shoulder + 2), (cx + 47, shoulder + 7),
                     (cx + 43, shoulder + 24), (cx + 20, shoulder + 16)), fill, edge)
        _poly(draw, ((cx - 17, head_top + 6), (cx - 29, head_top - 38),
                     (cx - 20, head_top - 40), (cx - 8, head_top + 4)), shell, edge)
        _poly(draw, ((cx + 17, head_top + 6), (cx + 29, head_top - 38),
                     (cx + 20, head_top - 40), (cx + 8, head_top + 4)), shell, edge)
        draw.rectangle((cx - 23, head_top - 39, cx - 18, head_top - 31), fill=light)
        draw.rectangle((cx + 18, head_top - 39, cx + 23, head_top - 31), fill=light)
    if spec.role == "wave_guardian":
        for offset in (-18, 0, 18):
            _poly(draw, ((cx + offset, torso_top + 3), (cx + offset - 6, torso_top - 18),
                         (cx + offset + 6, torso_top - 18), (cx + offset + 3, torso_top + 7)), accent, edge)
        _line(draw, ((cx, torso_top + 10), (cx, torso_bottom - 4)), accent, 4)
    if spec.role == "ritual_guard":
        draw.ellipse((cx - 14, torso_top + 39, cx + 14, torso_top + 67), fill=dark, outline=accent, width=3)
        _line(draw, ((cx, torso_top + 43), (cx, torso_top + 63)), light, 2)
        _line(draw, ((cx - 10, torso_top + 53), (cx + 10, torso_top + 53)), light, 2)

    if spec.family == "skeleton":
        for sign in (-1, 1):
            _line(draw, ((cx + sign * 17, shoulder + 4), (cx + sign * 34, torso_top + 76),
                         (cx + sign * 42, torso_top + 150)), bone, 3)
        if spec.role == "ritual_guard":
            draw.ellipse((cx - 12, torso_top + 42, cx + 12, torso_top + 66), fill=dark, outline=accent, width=3)
        if spec.role == "wave_guardian":
            _line(draw, ((cx, torso_top + 5), (cx, torso_bottom - 5)), accent, 4)

    return (cx - 50, top - 42, cx + 50, base + 2)


def _draw_spider(draw: ImageDraw.ImageDraw, spec: RoleSpec,
                 palette: tuple[tuple[int, int, int, int], ...], cx: int, base: int) -> tuple[int, int, int, int]:
    dark, mid, accent, light, _bone = palette
    edge = tuple(max(0, channel - 18) for channel in accent[:3]) + (255,)
    body_y = base - 72
    draw.ellipse((cx - 90, base - 7, cx + 90, base + 10), fill=(3, 2, 8, 220))
    # Eight articulated legs are drawn as two clean segments, not a texture grid.
    for side in (-1, 1):
        for index, y in enumerate((body_y - 8, body_y + 1, body_y + 10, body_y + 19)):
            elbow_x = cx + side * (47 + index * 6)
            foot_x = cx + side * (84 + (index % 2) * 6)
            _line(draw, ((cx + side * 27, y), (elbow_x, y + 9), (foot_x, base - 8)), mid, 10)
            _line(draw, ((cx + side * 27, y), (elbow_x, y + 9), (foot_x, base - 8)), edge, 2)
    draw.ellipse((cx - 38, body_y - 21, cx + 38, body_y + 40), fill=mid, outline=edge, width=3)
    _poly(draw, ((cx - 32, body_y + 31), (cx + 28, body_y + 31),
                 (cx + 45, body_y + 49), (cx - 43, body_y + 49)), dark, edge)
    draw.ellipse((cx - 29, body_y - 31, cx + 29, body_y + 16), fill=dark, outline=edge, width=3)
    draw.rectangle((cx - 17, body_y - 13, cx - 5, body_y - 5), fill=accent)
    draw.rectangle((cx + 5, body_y - 13, cx + 17, body_y - 5), fill=accent)
    if spec.role == "elite":
        _line(draw, ((cx - 31, body_y - 13), (cx - 54, body_y - 31), (cx - 66, body_y - 29)), accent, 5)
        _line(draw, ((cx + 31, body_y - 13), (cx + 54, body_y - 31), (cx + 66, body_y - 29)), accent, 5)
    elif spec.role == "wave_guardian":
        for offset in (-20, 0, 20):
            _poly(draw, ((cx + offset, body_y - 26), (cx + offset - 8, body_y - 49),
                         (cx + offset + 8, body_y - 49), (cx + offset + 4, body_y - 23)), accent, edge)
    elif spec.role == "ritual_guard":
        draw.ellipse((cx - 18, body_y + 18, cx + 18, body_y + 54), fill=dark, outline=accent, width=3)
        draw.rectangle((cx - 3, body_y + 22, cx + 3, body_y + 50), fill=light)
        draw.rectangle((cx - 13, body_y + 32, cx + 13, body_y + 38), fill=light)
    return (cx - 99, body_y - 52, cx + 99, base + 2)


def _draw_hitbox(draw: ImageDraw.ImageDraw, spec: RoleSpec, bounds: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = bounds
    if spec.family == "spider":
        inset_x, inset_y = 22, 34
    elif spec.family == "skeleton":
        inset_x, inset_y = 22, 4
    else:
        inset_x, inset_y = 22, 2
    box = (left + inset_x, top + inset_y, right - inset_x, bottom - inset_y)
    colour = (220, 211, 110, 210)
    dash = 7
    for x in range(box[0], box[2], dash * 2):
        draw.line((x, box[1], min(x + dash, box[2]), box[1]), fill=colour, width=2)
        draw.line((x, box[3], min(x + dash, box[2]), box[3]), fill=colour, width=2)
    for y in range(box[1], box[3], dash * 2):
        draw.line((box[0], y, box[0], min(y + dash, box[3])), fill=colour, width=2)
        draw.line((box[2], y, box[2], min(y + dash, box[3])), fill=colour, width=2)
    draw.text((box[0] + 4, box[1] + 4), f"native {spec.hitbox}", font=_font(12, True), fill=colour)


def render_card(spec: RoleSpec, size: tuple[int, int] = (420, 500)) -> Image.Image:
    width, height = size
    texture = ENTITY / spec.texture
    palette = _palette(texture)
    dark, mid, accent, light, _bone = palette
    background = (5, 5, 14, 255)
    image = Image.new("RGBA", size, background)
    draw = ImageDraw.Draw(image)

    draw.rectangle((10, 10, width - 11, height - 11), fill=(10, 8, 23, 255), outline=(47, 31, 72, 255), width=2)
    draw.rectangle((10, 10, width - 11, 55), fill=(18, 12, 38, 255))
    draw.text((23, 17), spec.label, font=_font(20, True), fill=(245, 239, 255, 255))
    draw.text((23, 39), f"{spec.family.upper()} / {spec.role.upper()}", font=_font(11, True), fill=accent)
    draw.text((23, 69), "ASSEMBLED 3/4 PREVIEW", font=_font(12, True), fill=(177, 164, 201, 255))

    if spec.family == "spider":
        bounds = _draw_spider(draw, spec, palette, width // 2, 365)
    else:
        bounds = _draw_humanoid(draw, spec, palette, width // 2, 430)
    _draw_hitbox(draw, spec, bounds)

    # The actual 64x32 UV sheet is shown next to the assembled silhouette.
    swatch = Image.open(texture).convert("RGBA").resize((128, 64), Image.Resampling.NEAREST)
    image.alpha_composite(swatch, (width - 151, height - 108))
    draw.rectangle((width - 153, height - 110, width - 21, height - 41), outline=(107, 89, 135, 255), width=1)
    draw.text((width - 151, height - 35), "UV 64x32", font=_font(11), fill=(177, 164, 201, 255))
    draw.text((23, height - 89), f"geometry  {spec.geometry}", font=_font(12), fill=(221, 214, 236, 255))
    draw.text((23, height - 68), f"animation {spec.animation}", font=_font(12), fill=(221, 214, 236, 255))
    draw.text((23, height - 47), f"variant   {spec.variant}", font=_font(12), fill=(221, 214, 236, 255))
    return image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    manifest: list[dict[str, str]] = []
    cards: list[Image.Image] = []
    for spec in ROLE_SPECS:
        card = render_card(spec)
        path = OUT / f"{spec.slug}.png"
        card.save(path, format="PNG", optimize=False)
        cards.append(card)
        manifest.append({
            **asdict(spec),
            "preview": path.relative_to(ROOT.parent).as_posix(),
            "STATIC_ASSEMBLED_PREVIEW_ONLY": True,
            "NATIVE_MINECRAFT_RENDER_VERIFIED": False,
        })

    columns, rows = 4, 4
    card_width, card_height = 420, 500
    board = Image.new("RGBA", (columns * card_width, rows * card_height + 86), (4, 4, 12, 255))
    board_draw = ImageDraw.Draw(board)
    board_draw.text((22, 17), "COPIMINE / END RIFT MOB MODEL REVIEW", font=_font(30, True), fill=(249, 241, 255, 255))
    board_draw.text((24, 53), "Static assembled previews from runtime UV atlases • native hitboxes unchanged",
                    font=_font(14), fill=(190, 176, 214, 255))
    for index, card in enumerate(cards):
        x = (index % columns) * card_width
        y = 86 + (index // columns) * card_height
        board.alpha_composite(card, (x, y))
    board.save(BOARD, format="PNG", optimize=False)
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"WROTE {BOARD}")
    print(f"WROTE {len(cards)} individual previews under {OUT}")


if __name__ == "__main__":
    main()
