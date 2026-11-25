#!/usr/bin/env python3
"""Render source-derived preview sheets for the authored V5.1 spell meshes.

Section 9L asks for per-ability visual acceptance assets and Section 9A.03(D)
asks for authored 3D geometry rather than one billboard.  This workstation has
no GUI Minecraft client, so an in-game capture is honestly BLOCKED.  What is
*not* blocked is proof of what the shipped code actually draws: this script
compiles the real ``SpellVisualGeometry`` source (it is pure JDK Java), runs it
through a tiny probe and projects the resulting strokes to an isometric SVG.

The sheets therefore show the real authored mesh across the seven authored
semantic cast keys of ``SpellVisualEnvelope`` (Section 9A.03(C)) - the same
strokes, widths, style roles, brightness, re-time and material heat the 26.3
renderer consumes - not a hand-drawn mock-up.  The colour ramp mirrors
``SpellVisualToneMap`` (deep exterior -> base -> white-hot core); the live hue
still comes from the server cue at runtime.

Usage::

    py -3 scripts/minecraft/RenderSpellVisualPreviews.py --out <dir>
    py -3 scripts/minecraft/RenderSpellVisualPreviews.py --check

``--check`` re-renders into a temporary directory and fails when the committed
sheets differ, so a mesh edit can never silently leave stale art behind.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GEOMETRY = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/SpellVisualGeometry.java"
ENVELOPE = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/SpellVisualEnvelope.java"
DEFAULT_OUT = ROOT / "docs/end-rift/2026-11-23/spell_previews"

# The seven authored semantic cast keys of ``SpellVisualEnvelope`` (Section
# 9A.03(C)), in milliseconds since the first authored frame.  Drawing the real
# keys - not arbitrary instants - means each sheet shows the actual cast arc
# (dormant -> anticipation -> energized -> release -> peak -> dissipate ->
# completed), including the family's own re-time and material heat.  Every
# family reads the same key clock, so the seven panels stay comparable.
FRAMES = (0, 240, 540, 860, 1180, 1580, 2000)

# Art-direction palette per spell, as (deep, base, hot) RGB triples.  Kept in
# sync with the families' identities in Section 9A.02; the white-hot end is the
# tone map's core, not a server hue.
PALETTE: dict[str, tuple[tuple[int, int, int], tuple[int, int, int], tuple[int, int, int]]] = {
    "crown": ((38, 16, 62), (140, 74, 214), (236, 214, 255)),
    "circle": ((10, 34, 48), (46, 168, 214), (214, 246, 255)),
    "fangline": ((46, 8, 20), (198, 42, 74), (255, 208, 214)),
    "sweep": ((10, 40, 38), (40, 176, 158), (208, 255, 244)),
    "sphere": ((34, 8, 52), (176, 60, 214), (246, 212, 255)),
    "sculk": ((6, 34, 30), (30, 158, 128), (206, 255, 236)),
    "gravity": ((16, 14, 56), (86, 76, 214), (220, 218, 255)),
    "fan": ((46, 28, 6), (214, 138, 40), (255, 234, 198)),
    "heavy": ((48, 18, 6), (214, 96, 34), (255, 220, 190)),
    "separation": ((46, 12, 38), (214, 68, 152), (255, 212, 238)),
    "beam": ((30, 20, 60), (170, 140, 246), (250, 246, 255)),
    "geyser": ((44, 20, 8), (222, 110, 40), (255, 226, 190)),
}

CELL_W = 430
CELL_H = 470
MARGIN = 26
HEADER_H = 74


# ----------------------------------------------------------------------------
# Compile and run the real geometry class.
# ----------------------------------------------------------------------------
PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.List;
    import java.util.Locale;

    /** Dumps the authored mesh at fixed instants as JSON; no Minecraft types. */
    public final class SpellPreviewProbe {
        private static String num(double value) {
            return String.format(Locale.ROOT, "%.4f", value);
        }

        public static void main(String[] args) {
            long[] frames = new long[args.length];
            for (int index = 0; index < args.length; index++) {
                frames[index] = Long.parseLong(args[index]);
            }
            StringBuilder out = new StringBuilder("{\\"spells\\":[");
            SpellVisualGeometry.Spell[] spells = SpellVisualGeometry.Spell.values();
            for (int s = 0; s < spells.length; s++) {
                SpellVisualGeometry.Spell spell = spells[s];
                if (s > 0) out.append(',');
                double bound = SpellVisualGeometry.maxReach(spell);
                out.append("{\\"token\\":\\"").append(spell.token())
                   .append("\\",\\"name\\":\\"").append(spell.name())
                   .append("\\",\\"bound\\":").append(num(bound))
                   .append(",\\"frames\\":[");
                for (int f = 0; f < frames.length; f++) {
                    List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                            spell, frames[f], 4242L, bound, SpellVisualGeometry.Quality.FULL);
                    if (f > 0) out.append(',');
                    out.append("{\\"millis\\":").append(frames[f])
                       .append(",\\"beat\\":\\"")
                       .append(SpellVisualEnvelope.beat(spell, frames[f]).name())
                       .append("\\",\\"heat\\":").append(num(SpellVisualEnvelope.heat(spell, frames[f])))
                       .append(",\\"strokes\\":[");
                    for (int i = 0; i < strokes.size(); i++) {
                        SpellVisualGeometry.Stroke stroke = strokes.get(i);
                        if (i > 0) out.append(',');
                        out.append("{\\"style\\":\\"").append(stroke.style())
                           .append("\\",\\"brightness\\":").append(num(stroke.brightness()))
                           .append(",\\"alpha\\":").append(num(stroke.alpha()))
                           .append(",\\"points\\":[");
                        for (int p = 0; p < stroke.points().size(); p++) {
                            SpellVisualGeometry.Point point = stroke.points().get(p);
                            if (p > 0) out.append(',');
                            out.append('[').append(num(point.x())).append(',')
                               .append(num(point.y())).append(',')
                               .append(num(point.z())).append(']');
                        }
                        out.append("],\\"widths\\":[");
                        for (int w = 0; w < stroke.halfWidths().size(); w++) {
                            if (w > 0) out.append(',');
                            out.append(num(stroke.halfWidths().get(w)));
                        }
                        out.append("]}");
                    }
                    out.append("]}");
                }
                out.append("]}");
            }
            out.append("]}");
            System.out.println(out);
        }
    }
    """
)


def _java_tool(name: str) -> Path:
    """Mirror the probe helper in the test suite: CI uses JAVA_HOME."""
    executable = name + (".exe" if os.name == "nt" else "")
    for variable in ("JAVA_HOME", "JAVA_HOME_25_X64", "JAVA_HOME_21_X64"):
        configured = os.environ.get(variable)
        if configured:
            candidate = Path(configured) / "bin" / executable
            if candidate.is_file():
                return candidate
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/" + executable))
    if candidates:
        return candidates[0]
    found = shutil.which(name)
    if not found:
        raise SystemExit(f"{name} is required to compile the geometry probe but was not found")
    return Path(found)


def build_geometry() -> dict:
    """Compile ``SpellVisualGeometry`` and return its mesh dump."""
    if not GEOMETRY.is_file():
        raise SystemExit(f"authored geometry source is missing: {GEOMETRY}")
    javac = _java_tool("javac")
    java = javac.with_name("java.exe" if os.name == "nt" else "java")
    with tempfile.TemporaryDirectory(prefix="spell_preview_") as tmp:
        tmp_path = Path(tmp)
        package = tmp_path / "src/me/copimine/client"
        package.mkdir(parents=True, exist_ok=True)
        (package / "SpellPreviewProbe.java").write_text(PROBE, encoding="utf-8")
        sources = [str(package / "SpellPreviewProbe.java"), str(GEOMETRY), str(ENVELOPE)]
        compiled = subprocess.run(
            [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out")] + sources,
            capture_output=True, text=True, timeout=300,
        )
        if compiled.returncode != 0:
            raise SystemExit("geometry probe failed to compile:\n" + compiled.stdout + compiled.stderr)
        ran = subprocess.run(
            [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.SpellPreviewProbe",
             *[str(frame) for frame in FRAMES]],
            capture_output=True, text=True, timeout=300,
        )
        if ran.returncode != 0:
            raise SystemExit("geometry probe failed:\n" + ran.stdout + ran.stderr)
        return json.loads(ran.stdout.strip().splitlines()[-1])


# ----------------------------------------------------------------------------
# Isometric projection and SVG emission.
# ----------------------------------------------------------------------------
COS30 = math.cos(math.radians(30.0))
SIN30 = math.sin(math.radians(30.0))


def project(x: float, y: float, z: float, scale: float) -> tuple[float, float, float]:
    """Isometric map: +x/+z spread the floor, +y rises.  Third value is depth."""
    sx = (x - z) * COS30 * scale
    sy = ((x + z) * SIN30 - y) * scale
    depth = x + z - y * 0.5
    return sx, sy, depth


def mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> str:
    t = max(0.0, min(1.0, t))
    r = round(a[0] + (b[0] - a[0]) * t)
    g = round(a[1] + (b[1] - a[1]) * t)
    bl = round(a[2] + (b[2] - a[2]) * t)
    return f"#{r:02x}{g:02x}{bl:02x}"


def _number(value: float) -> str:
    return f"{value:.2f}".rstrip("0").rstrip(".")


def cell_svg(spell: dict) -> str:
    """One square panel showing a single authored instant of the mesh."""
    token = spell["token"]
    bound = float(spell["bound"])
    deep, base, hot = PALETTE.get(token, PALETTE["beam"])
    span = max(6.0, bound * 2.2)
    scale = (CELL_W - 2.0 * MARGIN) / span

    parts: list[str] = []
    # Ground grid so the vertical dimension reads as 3D, not as a flat decal.
    extent = max(2.0, bound * 0.8)
    for step in range(-4, 5):
        offset = extent * step / 4.0
        a = project(offset, 0.0, -extent, scale)
        b = project(offset, 0.0, extent, scale)
        c = project(-extent, 0.0, offset, scale)
        d = project(extent, 0.0, offset, scale)
        for p, q in ((a, b), (c, d)):
            parts.append(
                f'<line x1="{_number(p[0])}" y1="{_number(p[1])}" '
                f'x2="{_number(q[0])}" y2="{_number(q[1])}" '
                f'stroke="{mix(deep, base, 0.25)}" stroke-width="0.6" opacity="0.30"/>'
            )

    # Painter order: far segments first.  Sorting is stable and deterministic.
    segments: list[tuple[float, float, float, float, float, float, str, float]] = []
    for stroke in spell["strokes"]:
        points = stroke["points"]
        widths = stroke["widths"]
        for index in range(len(points) - 1):
            x1, y1, z1 = points[index]
            x2, y2, z2 = points[index + 1]
            sx1, sy1, depth = project(x1, y1, z1, scale)
            sx2, sy2, depth2 = project(x2, y2, z2, scale)
            width = max(widths[index], widths[index + 1]) * 2.0 * scale
            segments.append((max(depth, depth2), sx1, sy1, sx2, sy2, width,
                             stroke["style"], float(stroke["brightness"])))
    segments.sort(key=lambda item: item[0])

    # Three passes: a wide glow, the toned body, and a white-hot core on the
    # brightest hero strokes, mirroring the runtime tone map.
    for opacity, blur, width_scale, hot_only in ((0.30, 3.4, 2.6, False),
                                                 (0.92, 0.0, 1.0, False),
                                                 (0.95, 1.2, 0.46, True)):
        filter_attr = f' filter="url(#soft)"' if blur > 0 else ""
        parts.append(f'<g opacity="{opacity}"{filter_attr}>')
        for _depth, sx1, sy1, sx2, sy2, width, _style, brightness in segments:
            if hot_only and brightness < 0.9:
                continue
            if hot_only:
                colour = mix(base, hot, 0.92)
            else:
                colour = mix(deep, base, 0.35 + 0.65 * brightness)
            parts.append(
                f'<line x1="{_number(sx1)}" y1="{_number(sy1)}" '
                f'x2="{_number(sx2)}" y2="{_number(sy2)}" '
                f'stroke="{colour}" stroke-width="{_number(max(0.7, width * width_scale))}" '
                f'stroke-linecap="round"/>'
            )
        parts.append("</g>")

    cx, cy = CELL_W / 2.0, CELL_H / 2.0 + 18.0
    return (
        f'<g transform="translate({_number(cx)},{_number(cy)})">'
        + "".join(parts)
        + "</g>"
    )


def mesh_stats(spell: dict) -> tuple[int, int]:
    strokes = spell["strokes"]
    segments = sum(max(0, len(stroke["points"]) - 1) for stroke in strokes)
    return len(strokes), segments


def spell_svg(spell: dict) -> str:
    deep, base, hot = PALETTE.get(spell["token"], PALETTE["beam"])
    width = CELL_W * len(spell["frames"])
    height = CELL_H + HEADER_H + MARGIN
    frames = spell["frames"]
    top = frames[0]
    top_strokes, top_segments = mesh_stats(top)

    body: list[str] = []
    for index, frame in enumerate(frames):
        decorated = dict(spell)
        decorated["strokes"] = frame["strokes"]
        body.append(f'<g transform="translate({_number(index * CELL_W)},'
                    f'{_number(HEADER_H)})">{cell_svg(decorated)}</g>')
        beat = str(frame.get("beat", "")).lower()
        heat = float(frame.get("heat", 0.0))
        label = f"{index + 1}/7 {beat}  {frame['millis']} ms  heat {heat:.2f}"
        x = index * CELL_W + CELL_W / 2.0
        body.append(
            f'<text x="{_number(x)}" y="{_number(HEADER_H - 14)}" '
            f'fill="{mix(base, hot, 0.4)}" font-size="13" text-anchor="middle" '
            f'font-family="Segoe UI,Helvetica,Arial,sans-serif" letter-spacing="1.5">{label}</text>'
        )

    title = spell["name"].replace("_", " ").title()
    stats = (f"{spell['token']}  |  {len(top['strokes'])} strokes  |  "
             f"{top_segments} segments  |  reach {_number(float(spell['bound']))} blocks  |  "
             f"seed 4242  |  tier FULL  |  7 semantic cast keys")
    return textwrap.dedent(
        f"""\
        <?xml version="1.0" encoding="UTF-8"?>
        <!-- Source-derived preview of the authored SpellVisualGeometry mesh across the seven
             authored semantic cast keys of SpellVisualEnvelope (Section 9A.03(C)), generated by
             scripts/minecraft/RenderSpellVisualPreviews.py from the shipped Java source.
             NOT an in-game screenshot; native F2 capture is BLOCKED. -->
        <svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}"
             viewBox="0 0 {width} {height}" role="img" aria-label="{title} authored mesh preview">
          <defs>
            <filter id="soft" x="-25%" y="-25%" width="150%" height="150%">
              <feGaussianBlur stdDeviation="4.2"/>
            </filter>
            <radialGradient id="void" cx="50%" cy="42%" r="72%">
              <stop offset="0%" stop-color="{mix(deep, base, 0.18)}"/>
              <stop offset="100%" stop-color="#05040a"/>
            </radialGradient>
          </defs>
          <rect width="{width}" height="{height}" fill="url(#void)"/>
          <rect x="0.5" y="0.5" width="{width - 1}" height="{height - 1}" fill="none"
                stroke="{mix(deep, base, 0.5)}" stroke-opacity="0.5"/>
          <text x="{MARGIN}" y="40" fill="#f4efff" font-size="26" letter-spacing="2.5"
                font-family="Segoe UI,Helvetica,Arial,sans-serif">{title}</text>
          <text x="{MARGIN}" y="63" fill="{mix(base, hot, 0.25)}" font-size="13"
                font-family="Consolas,Menlo,monospace">{stats}</text>
          {''.join(body)}
        </svg>
        """
    )


def _lfs(path: Path) -> str:
    """Text with normalised LF so a CRLF checkout still matches the fresh render."""
    return path.read_text(encoding="utf-8").replace("\r\n", "\n")


REEL_NAME = "playtest_reel.html"


def reel_html(geometry: dict) -> str:
    """A self-contained flipbook player for the committed sheets.

    Each committed ``<token>.svg`` is a horizontal strip of the seven authored
    semantic cast keys.  This page shows one cell at a time and steps through the
    seven, so the whole cast arc animates in a browser without a GUI Minecraft
    client and without duplicating a single byte of geometry.  It is a watching
    aid, not a measurement: the in-game F2 capture stays BLOCKED.
    """
    sheet_w = CELL_W * len(FRAMES)
    sheet_h = CELL_H + HEADER_H + MARGIN

    cards: list[str] = []
    for spell in geometry["spells"]:
        token = spell["token"]
        name = spell["name"].replace("_", " ").title()
        deep, base, hot = PALETTE.get(token, PALETTE["beam"])
        beats = [str(frame.get("beat", "")).lower() for frame in spell["frames"]]
        strip = " &rsaquo; ".join(beats)
        cards.append(textwrap.dedent(
            f"""\
            <figure class="card" style="--deep:{_rgb(deep)};--base:{_rgb(base)};--hot:{_rgb(hot)}">
              <div class="viewport" style="aspect-ratio:{sheet_w} / {sheet_h}">
                <img class="strip" src="{token}.svg" alt="{name} authored cast arc, seven keys">
              </div>
              <figcaption>
                <span class="name">{name}</span>
                <span class="beats">{strip}</span>
              </figcaption>
            </figure>
            """
        ))
    return textwrap.dedent(
        f"""\
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>End Rift V5.1 authored cast arc - flipbook</title>
        <style>
          :root {{ color-scheme: dark; }}
          * {{ box-sizing: border-box; }}
          body {{
            margin: 0; padding: 28px 24px 48px;
            background: radial-gradient(circle at 50% 0%, #140f22, #05040a 62%);
            color: #ece7fb; font-family: "Segoe UI", Helvetica, Arial, sans-serif;
          }}
          h1 {{ margin: 0 0 6px; font-size: 24px; letter-spacing: 2px; font-weight: 600; }}
          .caveat {{
            margin: 0 0 22px; max-width: 900px; font-size: 13px; line-height: 1.5;
            color: #b7a9d8;
          }}
          .caveat strong {{ color: #ffd9a0; }}
          .grid {{
            display: grid; gap: 20px;
            grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
          }}
          .card {{
            margin: 0; border: 1px solid color-mix(in srgb, var(--base) 55%, #000);
            border-radius: 14px; overflow: hidden;
            background: linear-gradient(180deg, color-mix(in srgb, var(--deep) 30%, #0a0812), #070610);
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.55), inset 0 0 0 1px rgba(255,255,255,0.03);
          }}
          .viewport {{ width: 100%; overflow: hidden; }}
          .strip {{
            display: block; width: {len(FRAMES) * 100}%; height: auto;
            animation: cast {len(FRAMES) * 0.26:.2f}s steps({len(FRAMES)}) infinite;
          }}
          @keyframes cast {{ from {{ transform: translateX(0); }} to {{ transform: translateX(-100%); }} }}
          @media (prefers-reduced-motion: reduce) {{
            .strip {{ animation: none; }}
          }}
          figcaption {{ padding: 10px 14px 14px; display: flex; flex-direction: column; gap: 4px; }}
          .name {{ font-size: 15px; letter-spacing: 1.2px; color: var(--hot); }}
          .beats {{ font-size: 11px; letter-spacing: 0.4px; color: #9d90bf; }}
        </style>
        </head>
        <body>
          <h1>End Rift V5.1 - authored cast arc flipbook</h1>
          <p class="caveat">
            Each panel steps through the seven authored semantic cast keys of
            <code>SpellVisualEnvelope</code> (Section 9A.03(C)) from the committed source-derived
            sheets. <strong>This is not an in-game screenshot.</strong> Native F2 capture remains
            BLOCKED on this host; the sheets are projections of the shipped mesh, not a render of it.
            Freeze the loop by enabling "reduce motion" in your OS, or open any
            <code>&lt;token&gt;.svg</code> to inspect every key side by side.
          </p>
          <div class="grid">
        {textwrap.indent("".join(cards), "    ")}
          </div>
        </body>
        </html>
        """
    )


def _rgb(triple: tuple[int, int, int]) -> str:
    return "#{:02x}{:02x}{:02x}".format(*triple)


def render(geometry: dict, out_dir: Path) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for spell in geometry["spells"]:
        frames = spell["frames"]
        if len(frames) != len(FRAMES):
            raise SystemExit(f"probe returned {len(frames)} frames, expected {len(FRAMES)}")
        path = out_dir / f"{spell['token']}.svg"
        # Always LF: the sheets are committed as text, so a Windows checkout with
        # core.autocrlf and a Linux runner must produce byte-identical evidence.
        path.write_text(spell_svg(spell), encoding="utf-8", newline="\n")
        written.append(path)
    # The flipbook reuses the very sheets above, so it carries no geometry of its
    # own and stays in lockstep with the mesh by construction.
    (out_dir / REEL_NAME).write_text(reel_html(geometry), encoding="utf-8", newline="\n")
    return written


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--check", action="store_true",
                        help="compare freshly rendered sheets with the committed ones")
    args = parser.parse_args(argv)

    geometry = build_geometry()
    geometry["spells"] = [spell for spell in geometry["spells"] if len(spell["frames"]) == len(FRAMES)]

    if not args.check:
        written = render(geometry, args.out)
        for path in written:
            print(f"wrote {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
        print(f"{len(written)} authored preview sheets")
        return 0

    committed = args.out
    if not committed.is_dir():
        print(f"no committed preview directory: {committed}", file=sys.stderr)
        return 1
    with tempfile.TemporaryDirectory(prefix="spell_previews_") as tmp:
        fresh = Path(tmp) / "out"
        render(geometry, fresh)
        problems: list[str] = []
        for sheet in sorted(fresh.glob("*.svg")):
            target = committed / sheet.name
            if not target.is_file():
                problems.append(f"missing committed sheet: {target.name}")
                continue
            if (_lfs(target) != _lfs(sheet)):
                problems.append(f"stale committed sheet: {target.name}")
        expected = {f"{spell['token']}.svg" for spell in geometry["spells"]}
        for extra in sorted(p.name for p in committed.glob("*.svg") if p.name not in expected):
            problems.append(f"unexpected committed sheet: {extra}")
        # The flipbook must describe the same seven keys as the sheets it plays.
        fresh_reel = fresh / REEL_NAME
        target_reel = committed / REEL_NAME
        if not target_reel.is_file():
            problems.append(f"missing committed flipbook: {REEL_NAME}")
        elif _lfs(target_reel) != _lfs(fresh_reel):
            problems.append(f"stale committed flipbook: {REEL_NAME}")
    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        print("re-run: py -3 scripts/minecraft/RenderSpellVisualPreviews.py "
              "--out docs/end-rift/2026-11-23/spell_previews", file=sys.stderr)
        return 1
    print(f"{len(geometry['spells'])} authored preview sheets match the shipped geometry")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
