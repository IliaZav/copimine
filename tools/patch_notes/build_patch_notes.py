#!/usr/bin/env python3
"""Validate Git-authored patch YAML and generate public JSON/HTML contracts."""

from __future__ import annotations

import argparse
import html
import json
import re
import shutil
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

import yaml
from PIL import Image


ALLOWED_KINDS = {"added", "changed", "fixed", "removed", "balanced", "verified", "known_issue"}
SECTION_KEYS = ("general", "technical", "bugfixes")
IDENTIFIER_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,119}$")
VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")


class PatchBuildError(ValueError):
    """Raised when a source patch cannot be published safely."""


def _require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise PatchBuildError(f"{label} must be a mapping")
    return value


def _require_text(value: Any, label: str, *, max_length: int = 1000) -> str:
    if not isinstance(value, str) or not value.strip():
        raise PatchBuildError(f"{label} must be a non-empty string")
    text = value.strip()
    if len(text) > max_length:
        raise PatchBuildError(f"{label} is too long")
    if any(ord(char) < 32 and char not in "\t\n" for char in text):
        raise PatchBuildError(f"{label} contains control characters")
    if re.search(r"<\s*/?\s*[A-Za-z!/?]", text) or re.search(r"javascript\s*:", text, re.IGNORECASE):
        raise PatchBuildError(f"{label} contains HTML or script content")
    return text


def _parse_date(value: Any, label: str) -> str:
    text = _require_text(value, label, max_length=64)
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as exc:
        raise PatchBuildError(f"{label} must be an ISO-8601 timestamp") from exc
    if parsed.tzinfo is None:
        raise PatchBuildError(f"{label} must include a timezone")
    return parsed.isoformat().replace("+00:00", "Z")


def _identifier(value: Any, label: str) -> str:
    text = _require_text(value, label, max_length=120)
    if not IDENTIFIER_RE.fullmatch(text):
        raise PatchBuildError(f"{label} is not a safe identifier")
    return text


def _change_rows(value: Any, label: str) -> list[dict[str, str]]:
    if not isinstance(value, list) or not value:
        raise PatchBuildError(f"{label} must contain at least one change")
    result: list[dict[str, str]] = []
    for index, raw in enumerate(value):
        row = _require_mapping(raw, f"{label}[{index}]")
        kind = _require_text(row.get("kind"), f"{label}[{index}].kind", max_length=32).lower()
        if kind not in ALLOWED_KINDS:
            raise PatchBuildError(f"{label}[{index}].kind is unknown: {kind}")
        result.append({"kind": kind, "text": _require_text(row.get("text"), f"{label}[{index}].text")})
    return result


def _section_rows(value: Any, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise PatchBuildError(f"{label} must be a list")
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for index, raw in enumerate(value):
        row = _require_mapping(raw, f"{label}[{index}]")
        row_id = _identifier(row.get("id"), f"{label}[{index}].id")
        if row_id in seen:
            raise PatchBuildError(f"duplicate section id in {label}: {row_id}")
        seen.add(row_id)
        result.append({
            "id": row_id,
            "title": _require_text(row.get("title"), f"{label}[{index}].title", max_length=160),
            "changes": _change_rows(row.get("changes"), f"{label}[{index}].changes"),
        })
    return result


def _human_name(item_id: str) -> str:
    return " ".join(part.capitalize() for part in item_id.replace(":", "_").replace("/", "_").split("_") if part)


def _read_catalog(path: Path) -> dict[str, dict[str, Any]]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PatchBuildError(f"cannot read item catalog: {path}") from exc
    rows = raw.get("items") if isinstance(raw, dict) else raw
    if not isinstance(rows, list):
        raise PatchBuildError("item catalog must contain an items list")
    result: dict[str, dict[str, Any]] = {}
    for index, raw_row in enumerate(rows):
        row = _require_mapping(raw_row, f"catalog.items[{index}]")
        item_id = _identifier(row.get("id", row.get("itemId")), f"catalog.items[{index}].id")
        if item_id in result:
            raise PatchBuildError(f"duplicate catalog item: {item_id}")
        texture = _require_text(row.get("texture", row.get("texturePath")), f"catalog.items[{index}].texture", max_length=240)
        result[item_id] = {
            "itemId": item_id,
            "displayName": _require_text(row.get("displayName"), f"catalog.items[{index}].displayName", max_length=160)
            if row.get("displayName")
            else _human_name(item_id),
            "texture": texture,
        }
    return result


def _resolve_texture(texture_root: Path, texture: str) -> Path:
    normalized = texture.replace("\\", "/").strip()
    if normalized.startswith("copimine:item/"):
        normalized = normalized[len("copimine:item/"):]
    if normalized.startswith("minecraft:item/"):
        normalized = normalized[len("minecraft:item/"):]
    if normalized.startswith(("artifacts/", "narcotics/")):
        normalized = f"item/{normalized}"
    candidate = Path(normalized)
    if candidate.suffix.lower() != ".png":
        candidate = candidate.with_suffix(".png")
    if candidate.is_absolute() or ".." in candidate.parts:
        raise PatchBuildError(f"unsafe texture path: {texture}")
    direct = (texture_root / candidate).resolve()
    root = texture_root.resolve()
    if root != direct and root not in direct.parents:
        raise PatchBuildError(f"texture path escapes texture root: {texture}")
    if direct.is_file():
        return direct
    # Resource-pack model manifests use item/artifacts/... while the source
    # tree stores that namespace below assets/copimine/textures/item.
    fallback = (texture_root / normalized).resolve()
    if fallback.is_file():
        return fallback
    raise PatchBuildError(f"item texture is missing: {texture}")


def _copy_web_texture(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        with Image.open(source) as image:
            if image.width <= 0 or image.height <= 0 or image.width > 2048 or image.height > 2048:
                raise PatchBuildError(f"invalid item texture dimensions: {source}")
            image.convert("RGBA").save(destination, format="WEBP", quality=84, method=6)
    except PatchBuildError:
        raise
    except Exception as exc:  # Pillow has several format-specific exception types.
        raise PatchBuildError(f"item texture is not a valid image: {source}") from exc
    if not destination.is_file() or destination.stat().st_size == 0:
        raise PatchBuildError(f"generated item texture is empty: {destination}")


def _load_patch(path: Path) -> dict[str, Any]:
    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        raise PatchBuildError(f"cannot read patch source: {path}") from exc
    data = _require_mapping(raw, str(path))
    if data.get("schemaVersion") != 1:
        raise PatchBuildError(f"{path}: schemaVersion must be 1")
    patch_id = _identifier(data.get("id"), f"{path}.id")
    slug = _identifier(data.get("slug"), f"{path}.slug")
    version = _require_text(data.get("version"), f"{path}.version", max_length=64)
    if not VERSION_RE.fullmatch(version):
        raise PatchBuildError(f"{path}.version is not semver-like")
    summary = data.get("summary")
    if not isinstance(summary, list) or not 1 <= len(summary) <= 3:
        raise PatchBuildError(f"{path}.summary must contain one to three entries")
    summary = [_require_text(row, f"{path}.summary[{index}]", max_length=280) for index, row in enumerate(summary)]
    sections = _require_mapping(data.get("sections"), f"{path}.sections")
    normalized_sections = {key: _section_rows(sections.get(key, []), f"{path}.sections.{key}") for key in SECTION_KEYS}
    items_raw = data.get("items", [])
    if not isinstance(items_raw, list):
        raise PatchBuildError(f"{path}.items must be a list")
    items: list[dict[str, Any]] = []
    item_ids: set[str] = set()
    for index, raw_item in enumerate(items_raw):
        item = _require_mapping(raw_item, f"{path}.items[{index}]")
        item_id = _identifier(item.get("itemId"), f"{path}.items[{index}].itemId")
        if item_id in item_ids:
            raise PatchBuildError(f"duplicate patch item: {item_id}")
        item_ids.add(item_id)
        items.append({"itemId": item_id, "changes": _change_rows(item.get("changes"), f"{path}.items[{index}].changes")})
    review = _require_mapping(data.get("review"), f"{path}.review")
    reviewed_by = _require_text(review.get("reviewedBy"), f"{path}.review.reviewedBy", max_length=160)
    reviewed_at = _parse_date(review.get("reviewedAtUtc"), f"{path}.review.reviewedAtUtc")
    if review.get("genericMarketingCopyFound") is not False:
        raise PatchBuildError(f"{path}.review.genericMarketingCopyFound must be false")
    return {
        "schemaVersion": 1,
        "id": patch_id,
        "slug": slug,
        "version": version,
        "title": _require_text(data.get("title"), f"{path}.title", max_length=200),
        "publishedAt": _parse_date(data.get("publishedAt"), f"{path}.publishedAt"),
        "summary": summary,
        "sections": normalized_sections,
        "items": items,
        "review": {"reviewedBy": reviewed_by, "reviewedAtUtc": reviewed_at, "genericMarketingCopyFound": False},
    }


def _detail_html(patch: dict[str, Any]) -> str:
    def text(value: Any) -> str:
        return html.escape(str(value), quote=True)

    blocks: list[str] = []
    for key, title in (("general", "Общие изменения"), ("technical", "Технические изменения"), ("bugfixes", "Исправленные ошибки")):
        rows = patch["sections"][key]
        if not rows:
            continue
        blocks.append(f'<section class="patch-section"><h2>{text(title)}</h2>')
        for row in rows:
            blocks.append(f'<article class="patch-group"><h3>{text(row["title"])}</h3><ul>')
            for change in row["changes"]:
                blocks.append(f'<li><span class="change-kind change-kind-{text(change["kind"])}">{text(change["kind"])}</span>{text(change["text"])}</li>')
            blocks.append("</ul></article></section>")
    if patch["items"]:
        blocks.append('<section class="patch-section"><h2>Предметы</h2><div id="patch-items" class="patch-items">')
        for item in patch["items"]:
            blocks.append(f'<article class="patch-item" data-item-id="{text(item["itemId"])}"><div class="patch-item-icon" data-item-icon="{text(item["itemId"])}"></div><div><h3>{text(item["itemId"])}</h3><ul>')
            for change in item["changes"]:
                blocks.append(f'<li><span class="change-kind change-kind-{text(change["kind"])}">{text(change["kind"])}:</span> {text(change["text"])}</li>')
            blocks.append("</ul></div></article>")
        blocks.append("</div></section>")
    known = [change for section in patch["sections"].values() for row in section for change in row["changes"] if change["kind"] == "known_issue"]
    if known:
        blocks.append('<section class="patch-section patch-known-issues"><h2>Известные ограничения</h2><ul>')
        blocks.extend(f'<li>{text(change["text"])}</li>' for change in known)
        blocks.append("</ul></section>")
    return """<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="color-scheme" content="light dark" />
  <link rel="canonical" href="https://copimine.ru/news/%s.html" />
  <title>%s — CopiMine</title>
  <link rel="stylesheet" href="/assets/style.css?v=20260815launchernews1" />
  <link rel="stylesheet" href="/assets/css/launcher-news.css?v=20260815launchernews1" />
</head>
<body data-page-kind="public-patch" data-patch-slug="%s">
  <main class="public-site public-site-compact patch-page">
    <header class="public-nav"><a class="public-brand" href="/index.html" aria-label="CopiMine"><img class="public-brand-logo" src="/assets/brand/copimine-logo.png" alt="" /><span class="public-brand-copy"><strong>CopiMine</strong><small>Новости обновлений</small></span></a><nav aria-label="Разделы сайта"><a href="/index.html">Главная</a><a href="/launcher.html">Лаунчер</a><a href="/news.html" aria-current="page">Новости</a><a href="/server.html">Сервер</a></nav></header>
    <div class="public-page-shell"><article class="patch-detail" data-patch-detail><a class="text-link" href="/news.html">← Все обновления</a><header class="patch-heading"><span class="hero-kicker">Патчноут</span><h1>%s</h1><p><span>Версия %s</span> · <time datetime="%s">%s</time></p></header><ul class="patch-summary">%s</ul>%s</article></div>
  </main>
  <script type="module" src="/assets/js/public/public-page.js?v=20260815launchernews1"></script>
</body>
</html>
""" % (
        text(patch["slug"]), text(patch["title"]), text(patch["slug"]), text(patch["title"]), text(patch["version"]),
        text(patch["publishedAt"]), text(patch["publishedAt"]), "".join(f"<li>{text(item)}</li>" for item in patch["summary"]), "".join(blocks)
    )


def build_notes(
    source_dir: Path,
    output_dir: Path,
    site_dir: Path,
    catalog_path: Path,
    texture_root: Path,
    asset_output_dir: Path | None = None,
) -> dict[str, Any]:
    source_dir = Path(source_dir)
    output_dir = Path(output_dir)
    site_dir = Path(site_dir)
    catalog = _read_catalog(Path(catalog_path))
    files = sorted([*source_dir.glob("*.yaml"), *source_dir.glob("*.yml")])
    if not files:
        raise PatchBuildError(f"no patch YAML files found in {source_dir}")
    patches = [_load_patch(path) for path in files]
    ids: set[str] = set()
    slugs: set[str] = set()
    for patch in patches:
        if patch["id"] in ids or patch["slug"] in slugs:
            raise PatchBuildError(f"duplicate patch id or slug: {patch['id']} / {patch['slug']}")
        ids.add(patch["id"])
        slugs.add(patch["slug"])
    patches.sort(key=lambda patch: (patch["publishedAt"], patch["id"]), reverse=True)

    asset_dir = Path(asset_output_dir) if asset_output_dir is not None else output_dir.parent / "patch-items"
    asset_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)
    site_dir.mkdir(parents=True, exist_ok=True)
    index_rows: list[dict[str, Any]] = []
    for patch in patches:
        normalized_items: list[dict[str, Any]] = []
        for item in patch["items"]:
            catalog_item = catalog.get(item["itemId"])
            if catalog_item is None:
                raise PatchBuildError(f"item {item['itemId']} is missing from the artifact catalog")
            source_texture = _resolve_texture(Path(texture_root), str(catalog_item["texture"]))
            destination = asset_dir / f"{item['itemId']}.webp"
            _copy_web_texture(source_texture, destination)
            normalized_items.append({
                "itemId": item["itemId"],
                "displayName": catalog_item["displayName"],
                "iconUrl": f"/assets/patch-items/{item['itemId']}.webp",
                "changes": item["changes"],
            })
        detail = {**patch, "detailUrl": f"/news/{patch['slug']}.html", "items": normalized_items}
        (output_dir / f"{patch['slug']}.json").write_text(json.dumps(detail, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (site_dir / f"{patch['slug']}.html").write_text(_detail_html(detail), encoding="utf-8")
        index_rows.append({
            "id": patch["id"],
            "slug": patch["slug"],
            "version": patch["version"],
            "title": patch["title"],
            "publishedAt": patch["publishedAt"],
            "summary": patch["summary"],
            "detailUrl": f"/news/{patch['slug']}.html",
            "thumbnailUrl": normalized_items[0]["iconUrl"] if normalized_items else None,
            "badges": ["Launcher"] if "launcher" in patch["title"].lower() or "launcher" in patch["id"].lower() else ["Сервер"],
        })
    index = {"schemaVersion": 1, "patches": index_rows}
    (output_dir / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"count": len(patches), "index": index, "outputDir": str(output_dir), "siteDir": str(site_dir)}


def _default_paths(repo_root: Path) -> argparse.Namespace:
    return argparse.Namespace(
        source_dir=repo_root / "content" / "patches",
        output_dir=repo_root / "admin-web" / "frontend" / "assets" / "public-data" / "patches",
        site_dir=repo_root / "admin-web" / "frontend" / "news",
        catalog_path=repo_root / "resourcepacks" / "models_manifest.json",
        texture_root=repo_root / "resourcepacks" / "src" / "assets" / "copimine" / "textures",
        asset_output_dir=repo_root / "admin-web" / "frontend" / "assets" / "patch-items",
    )


def main(argv: Iterable[str] | None = None) -> int:
    repo_root = Path(__file__).resolve().parents[2]
    defaults = _default_paths(repo_root)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=defaults.source_dir)
    parser.add_argument("--output-dir", type=Path, default=defaults.output_dir)
    parser.add_argument("--site-dir", type=Path, default=defaults.site_dir)
    parser.add_argument("--catalog-path", type=Path, default=defaults.catalog_path)
    parser.add_argument("--texture-root", type=Path, default=defaults.texture_root)
    parser.add_argument("--asset-output-dir", type=Path, default=defaults.asset_output_dir)
    args = parser.parse_args(list(argv) if argv is not None else None)
    try:
        result = build_notes(args.source_dir, args.output_dir, args.site_dir, args.catalog_path, args.texture_root, args.asset_output_dir)
    except PatchBuildError as exc:
        parser.error(str(exc))
        return 2
    print(f"Generated {result['count']} patch notes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
