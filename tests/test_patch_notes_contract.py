from __future__ import annotations

import json
from pathlib import Path

import pytest
import yaml
from PIL import Image

from tools.patch_notes.build_patch_notes import PatchBuildError, build_notes


def write_png(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (16, 16), (39, 210, 164, 255)).save(path, format="PNG")


def write_valid_patch(source: Path, *, item_id: str = "eternal_totem", kind: str = "fixed", text: str = "Исправлена выдача предмета после перезахода.") -> None:
    source.mkdir(parents=True, exist_ok=True)
    payload = {
        "schemaVersion": 1,
        "id": "2026-08-20-launcher-1.0.0",
        "slug": "copimine-launcher-1-0-0",
        "version": "1.0.0",
        "title": "CopiMine Launcher 1.0.0",
        "publishedAt": "2026-08-20T12:00:00Z",
        "summary": [
            "Добавлена установка Java 21, Minecraft 1.21.1 и Fabric Loader 0.19.3.",
            "Моды синхронизируются по подписанному manifest.",
        ],
        "sections": {
            "general": [{"id": "launcher", "title": "Лаунчер", "changes": [{"kind": "added", "text": "Добавлена отдельная папка экземпляра игры."}]}],
            "technical": [{"id": "reconcile", "title": "Обновление файлов", "changes": [{"kind": "fixed", "text": "Незавершённая загрузка не заменяет рабочий файл."}]}],
            "bugfixes": [{"id": "auth", "title": "Авторизация", "changes": [{"kind": "verified", "text": "После успешной авторизации задержка не возвращается."}]}],
        },
        "items": [{"itemId": item_id, "changes": [{"kind": kind, "text": text}]}],
        "review": {"reviewedBy": "release-review", "reviewedAtUtc": "2026-08-20T13:00:00Z", "genericMarketingCopyFound": False},
    }
    (source / "2026-08-20-1.0.0.yaml").write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")


def make_catalog(root: Path, *, item_id: str = "eternal_totem") -> tuple[Path, Path]:
    catalog = root / "catalog.json"
    texture_root = root / "textures"
    catalog.write_text(json.dumps({"items": [{"id": item_id, "displayName": "Вечный тотем", "texture": f"{item_id}.png"}]}, ensure_ascii=False), encoding="utf-8")
    write_png(texture_root / f"{item_id}.png")
    return catalog, texture_root


def test_valid_patch_generates_bounded_index_detail_and_item_texture(tmp_path: Path) -> None:
    source = tmp_path / "patches"
    output = tmp_path / "public-data" / "patches"
    site = tmp_path / "news"
    catalog, textures = make_catalog(tmp_path)
    write_valid_patch(source)

    result = build_notes(source, output, site, catalog, textures)

    index = json.loads((output / "index.json").read_text(encoding="utf-8"))
    detail = json.loads((output / "copimine-launcher-1-0-0.json").read_text(encoding="utf-8"))
    assert result["count"] == 1
    assert index["schemaVersion"] == 1
    assert index["patches"][0]["detailUrl"] == "/news/copimine-launcher-1-0-0.html"
    assert detail["items"][0]["displayName"] == "Вечный тотем"
    assert detail["items"][0]["iconUrl"] == "/assets/patch-items/eternal_totem.webp"
    assert (site / "copimine-launcher-1-0-0.html").is_file()
    assert (tmp_path / "public-data" / "patch-items" / "eternal_totem.webp").is_file()


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("unknown-kind", "unknown"),
        ("html", "<script>alert(1)</script>"),
    ],
)
def test_patch_source_rejects_unsafe_change_text_or_kind(tmp_path: Path, field: str, value: str) -> None:
    source = tmp_path / "patches"
    output = tmp_path / "public-data" / "patches"
    site = tmp_path / "news"
    catalog, textures = make_catalog(tmp_path)
    write_valid_patch(source, kind=value if field == "unknown-kind" else "fixed", text=value if field == "html" else "valid")

    with pytest.raises(PatchBuildError):
        build_notes(source, output, site, catalog, textures)


def test_patch_source_requires_catalog_texture_review_and_unique_identity(tmp_path: Path) -> None:
    source = tmp_path / "patches"
    output = tmp_path / "public-data" / "patches"
    site = tmp_path / "news"
    catalog, textures = make_catalog(tmp_path)
    write_valid_patch(source, item_id="missing")
    with pytest.raises(PatchBuildError, match="catalog"):
        build_notes(source, output, site, catalog, textures)

    write_valid_patch(source, item_id="eternal_totem")
    (source / "2026-08-21-1.0.1.yaml").write_text(
        (source / "2026-08-20-1.0.0.yaml").read_text(encoding="utf-8").replace("2026-08-20-launcher-1.0.0", "2026-08-20-launcher-1.0.0"),
        encoding="utf-8",
    )
    with pytest.raises(PatchBuildError, match="duplicate"):
        build_notes(source, output, site, catalog, textures)


def test_patch_source_rejects_missing_review_and_more_than_three_summary_lines(tmp_path: Path) -> None:
    source = tmp_path / "patches"
    output = tmp_path / "public-data" / "patches"
    site = tmp_path / "news"
    catalog, textures = make_catalog(tmp_path)
    write_valid_patch(source)
    patch_path = source / "2026-08-20-1.0.0.yaml"
    payload = yaml.safe_load(patch_path.read_text(encoding="utf-8"))
    payload.pop("review")
    patch_path.write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")
    with pytest.raises(PatchBuildError, match="review"):
        build_notes(source, output, site, catalog, textures)

    payload["review"] = {"reviewedBy": "release-review", "reviewedAtUtc": "2026-08-20T13:00:00Z", "genericMarketingCopyFound": False}
    payload["summary"] = ["one", "two", "three", "four"]
    patch_path.write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")
    with pytest.raises(PatchBuildError, match="summary"):
        build_notes(source, output, site, catalog, textures)


def test_patch_detail_keeps_section_boundaries_with_multiple_rows_and_theme_contract(tmp_path: Path) -> None:
    source = tmp_path / "patches"
    output = tmp_path / "public-data" / "patches"
    site = tmp_path / "news"
    catalog, textures = make_catalog(tmp_path)
    write_valid_patch(source)
    patch_path = source / "2026-08-20-1.0.0.yaml"
    payload = yaml.safe_load(patch_path.read_text(encoding="utf-8"))
    payload["sections"]["technical"].append(
        {
            "id": "second-technical-row",
            "title": "Проверка",
            "changes": [{"kind": "verified", "text": "Повторная проверка выполнена."}],
        }
    )
    patch_path.write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")

    build_notes(source, output, site, catalog, textures)

    detail_html = (site / "copimine-launcher-1-0-0.html").read_text(encoding="utf-8")
    assert detail_html.count('<section class="patch-section">') == detail_html.count("</section>")
    assert 'src="/assets/js/theme/theme-bootstrap.js"' in detail_html
    assert 'data-theme-toggle="true"' in detail_html
    assert "onclick=" not in detail_html
    assert not list(output.glob("*.tmp"))
