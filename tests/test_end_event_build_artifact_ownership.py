"""The direct plugin packager cannot publish over a pinned Gradle candidate."""
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("legacy", [True, False])
def test_direct_packager_preserves_gradle_candidate(tmp_path, legacy):
    powershell = shutil.which("pwsh") or shutil.which("powershell")
    if not powershell:
        pytest.skip("PowerShell is required to execute the actual packaging tail")
    source = (ROOT / "copimine-end-event/build-plugin.ps1").read_text(encoding="utf-8-sig")
    tail = source[source.index("Copy-Item -LiteralPath $jar -Destination $serverJar -Force"):]
    candidate = tmp_path / "build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar"
    candidate.parent.mkdir(parents=True)
    candidate.write_bytes(b"verified-gradle-java25-candidate")
    plugin = tmp_path / "plugin"
    plugin.mkdir()
    (plugin / "plugin.jar").write_bytes(b"direct-packager-output")
    (plugin / "config.yml").write_text("unchanged: true\n", encoding="utf-8")
    (tmp_path / "server").mkdir()
    script = tmp_path / "packaging-tail.ps1"
    script.write_text(
        "$ErrorActionPreference='Stop'\n"
        "$releaseRoot=$PSScriptRoot\n"
        "$pluginDir=Join-Path $PSScriptRoot 'plugin'\n"
        "$jar=Join-Path $pluginDir 'plugin.jar'\n"
        "$serverJar=Join-Path $PSScriptRoot 'server/plugin.jar'\n"
        "$serverDataDir=Join-Path $PSScriptRoot 'server/data'\n"
        f"$isLegacyPaper=${str(legacy).lower()}\n"
        "$SyncServerConfig=$true\n" + tail,
        encoding="utf-8",
    )
    result = subprocess.run(
        [powershell, "-NoProfile", "-File", str(script)],
        capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert (tmp_path / "server/plugin.jar").read_bytes() == b"direct-packager-output"
    assert (tmp_path / "server/data/config.yml").read_text() == "unchanged: true\n"
    assert candidate.read_bytes() == b"verified-gradle-java25-candidate"
