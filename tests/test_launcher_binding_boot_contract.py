from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js"
BOOTSTRAP = ROOT / "admin-web/frontend/assets/js/bootstrap.js"
STAGE_SITE = ROOT / "scripts/stage_copimine_launcher_site.ps1"


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
    assert "Get-ChildItem -LiteralPath $frontendRoot -Force | Copy-Item -Destination $destination -Recurse -Force" in stage_script

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
