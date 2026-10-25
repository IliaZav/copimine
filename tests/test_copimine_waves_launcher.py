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
    assert "$serverArguments" not in text
    assert "& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $server" in text


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

STARTER = ROOT / "scripts" / "minecraft" / "StartMigrationTestServer.ps1"


def test_starter_exempts_first_party_event_spawns_from_clearlag():
    text = STARTER.read_text(encoding="utf-8")
    assert "function Protect-MigrationEventSpawnsFromClearLag" in text
    assert "exempt-spawn-reasons:" in text
    assert "'- CUSTOM'" in text
    call = text.index("Protect-MigrationEventSpawnsFromClearLag -RuntimeDirectory $runtime")
    assert call > text.index("if ($ValidateOnly) {")
    assert call < text.index("& $java @javaArguments")


def _run_clearlag_helper(shell, helper, runtime):
    command = f". '{helper}'; Protect-MigrationEventSpawnsFromClearLag -RuntimeDirectory '{runtime}'"
    return subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
                          capture_output=True, text=True, timeout=60)


def test_clearlag_exemption_is_idempotent_and_scoped_to_existing_configs(tmp_path):
    shell = _powershell()
    if not shell:
        pytest.skip("PowerShell is required")
    text = STARTER.read_text(encoding="utf-8")
    start = text.index("function Protect-MigrationEventSpawnsFromClearLag {")
    end = text.index("function Test-MigrationPaperRuntimeListener {")
    helper = tmp_path / "clearlag-helper.ps1"
    helper.write_text(text[start:end], encoding="utf-8")
    config = tmp_path / "plugins/ClearLag/config.yml"
    config.parent.mkdir(parents=True)
    original = "mob-spawn-limiter:\n  enabled: true\n  exempt-spawn-reasons:\n    - CURED\n  per-world: {}\n"
    config.write_text(original, encoding="utf-8")

    _run_clearlag_helper(shell, helper.as_posix(), tmp_path)
    after_first = config.read_text(encoding="utf-8")
    assert after_first == original.replace("    - CURED\n", "    - CURED\n    - CUSTOM\n")

    _run_clearlag_helper(shell, helper.as_posix(), tmp_path)
    assert config.read_text(encoding="utf-8") == after_first

    empty = tmp_path / "no-clearlag"
    empty.mkdir()
    result = _run_clearlag_helper(shell, helper.as_posix(), empty)
    assert result.returncode == 0, result.stderr
    assert not (empty / "plugins/ClearLag/config.yml").exists()
