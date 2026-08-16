from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_launcher_packaging_produces_a_folder_selecting_msi() -> None:
    build = read("scripts/build_copimine_launcher.ps1")
    stage = read("scripts/stage_copimine_launcher_site.ps1")
    assert "--msi" in build
    assert "--instLocation" in build
    assert "CopiMineLauncherSetup-$Version.msi" in build
    assert "MsiPath" in stage
    assert "downloads/launcher" in stage


def test_launcher_page_exposes_the_optional_folder_selecting_installer() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    render = read("admin-web/frontend/assets/js/public/launcher-render.js")
    data = read("admin-web/frontend/assets/js/public/launcher-data.js")
    assert 'id="launcherMsiBtn"' in launcher
    assert "msiDownloadUrl" in data
    assert "launcherMsiBtn" in render
