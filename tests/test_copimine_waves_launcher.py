from pathlib import Path
import json
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "scripts" / "minecraft" / "StartCopiMineWavesLocal.ps1"


def _powershell():
    return shutil.which("pwsh") or shutil.which("powershell")


def _fixture(tmp_path):
    (tmp_path / "scripts/minecraft").mkdir(parents=True)
    (tmp_path / "tools/minecraft-26.3").mkdir(parents=True)
    runtime = tmp_path / "local-runtime/end-rift-server-26.3"
    (runtime / "plugins").mkdir(parents=True)
    shutil.copy2(LAUNCHER, tmp_path / "scripts/minecraft/StartCopiMineWavesLocal.ps1")
    shutil.copy2(ROOT / "tools/minecraft-26.3/profile.lock.json",
                 tmp_path / "tools/minecraft-26.3/profile.lock.json")
    shutil.copy2(ROOT / "tools/minecraft-26.3/server-plugins.lock.json",
                 tmp_path / "tools/minecraft-26.3/server-plugins.lock.json")
    return runtime


def test_launcher_pins_the_single_canonical_runtime_and_artifacts():
    text = LAUNCHER.read_text(encoding="utf-8")
    assert "local-runtime/end-rift-server-26.3" in text
    assert "end-rift-server'" not in text
    assert "12f032de165dad419f990c97adfce055916302c93659f9d422c4c861468d543c" not in text
    assert "endEventCandidate.sha256" in text
    assert "profile.clientArtifact.sha512" in text
    assert "resourcePackMigration.candidateSha256" in text
    assert "Get-Process -Name" not in text
    assert "Stop-Process -Name" not in text
    assert "Start-Transcript" in text
    assert "& powershell.exe" not in text


def test_launcher_refuses_a_missing_installed_end_event(tmp_path):
    shell = _powershell()
    if not shell:
        pytest.skip("PowerShell is required")
    runtime = _fixture(tmp_path)
    (runtime / "plugins/CopiMineEndEvent.jar").write_bytes(b"stale")
    result = subprocess.run(
        [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
         str(tmp_path / "scripts/minecraft/StartCopiMineWavesLocal.ps1"),
         "-RepositoryRoot", str(tmp_path), "-Doctor"],
        capture_output=True, text=True, timeout=60,
        env={**__import__("os").environ, "COPIMINE_ENV_FILE": ""},
    )
    output = result.stdout + result.stderr
    assert result.returncode != 0
    assert "Installed CopiMineEndEvent size mismatch" in output
    assert "official installer" in output


def test_launcher_refuses_an_unknown_end_event_hash(tmp_path):
    shell = _powershell()
    if not shell:
        pytest.skip("PowerShell is required")
    runtime = _fixture(tmp_path)
    lock = json.loads((tmp_path / "tools/minecraft-26.3/server-plugins.lock.json").read_text(encoding="utf-8"))
    size = next(item["candidate"]["size"] for item in lock["unchangedBaseline"]
                if item.get("candidate", {}).get("filename") == "CopiMineEndEvent-26.3.jar")
    (runtime / "plugins/CopiMineEndEvent.jar").write_bytes(b"x" * size)
    result = subprocess.run(
        [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
         str(tmp_path / "scripts/minecraft/StartCopiMineWavesLocal.ps1"),
         "-RepositoryRoot", str(tmp_path), "-Doctor"],
        capture_output=True, text=True, timeout=60,
        env={**__import__("os").environ, "COPIMINE_ENV_FILE": ""},
    )
    output = result.stdout + result.stderr
    assert result.returncode != 0
    assert "Installed CopiMineEndEvent hash mismatch" in output