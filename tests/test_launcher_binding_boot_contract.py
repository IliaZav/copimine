from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js"
BOOTSTRAP = ROOT / "admin-web/frontend/assets/js/bootstrap.js"
STAGE_SITE = ROOT / "scripts/stage_copimine_launcher_site.ps1"
CABINET_CACHE_KEY = "20260829launcherlink6"


def _relative_imports(source: str) -> set[str]:
    paths: set[str] = set()
    pattern = re.compile(r"(?:from\s+|import\s*\()([\"'])(\.[^\"']+)\1")
    for match in pattern.finditer(source):
        path = urlsplit(match.group(2)).path
        paths.add(path.removeprefix("./"))
    return paths


def test_cabinet_runtime_import_closure_is_present_in_the_static_site_stage() -> None:
    runtime_source = RUNTIME.read_text(encoding="utf-8")
    stage_script = STAGE_SITE.read_text(encoding="utf-8")
    assert "Get-ChildItem -LiteralPath $frontendRoot -Force" in stage_script
    assert "Where-Object { $_.Name -notin @('downloads', 'launcher') }" in stage_script

    for relative in _relative_imports(runtime_source):
        local = ROOT / "admin-web/frontend/assets/js" / relative
        assert local.is_file(), f"missing local runtime dependency: {relative}"


def test_dynamic_runtime_failure_leaves_a_recoverable_boot_screen() -> None:
    app = BOOTSTRAP.read_text(encoding="utf-8")

    assert "data-boot-state" in app
    assert "Кабинет не загрузился" in app
    assert "Повторить" in app
    assert "cabinetRuntimePromise = null" in app
    assert "loadCabinetRuntime" in app
    assert "cabinet-runtime.js?v=20260829launcherlink6" in app
    assert "error?.stack" in app


def test_cabinet_boot_chain_busts_stale_runtime_cache_after_a_live_syntax_failure() -> None:
    app = (ROOT / "admin-web/frontend/assets/app.js").read_text(encoding="utf-8")
    bootstrap = BOOTSTRAP.read_text(encoding="utf-8")
    runtime = RUNTIME.read_text(encoding="utf-8")
    cabinet_pages = sorted((ROOT / "admin-web/frontend/cabinet").glob("*.html"))

    assert f'./js/bootstrap.js?v={CABINET_CACHE_KEY}' in app
    assert f'./cabinet-runtime.js?v={CABINET_CACHE_KEY}' in bootstrap
    assert f'./shared/app-routes.js?v={CABINET_CACHE_KEY}' in bootstrap
    assert f'./shared/app-routes.js?v={CABINET_CACHE_KEY}' in runtime
    for dependency in (
        "./shared/browser-state.js",
        "./shared/csv.js",
        "./shared/dom.js",
        "./admin/cms-pages.js",
        "./admin/commerce-pages.js",
        "./player/account-pages.js",
    ):
        assert f'{dependency}?v={CABINET_CACHE_KEY}' in runtime
    for page in cabinet_pages:
        source = page.read_text(encoding="utf-8")
        if 'data-page-kind="cabinet"' in source:
            assert f"/assets/app.js?v={CABINET_CACHE_KEY}" in source, page.name


def test_launcher_link_panel_closes_true_branch_before_fallback_panel() -> None:
    runtime = RUNTIME.read_text(encoding="utf-8")

    assert '      </div>`\n    )\n    : panel("Привязка Launcher"' in runtime
