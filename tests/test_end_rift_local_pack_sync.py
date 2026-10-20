"""Execute the startup pack synchronizer without booting Paper or touching a real profile."""
from pathlib import Path
import hashlib
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
STARTER = ROOT / "tests/StartEndRiftLocal.ps1"


def ps_function(text, name):
    start = text.index("function " + name + " {")
    opening = text.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


@pytest.mark.parametrize("existing", [False, True])
def test_startup_synchronizes_served_copy_and_declared_digest(tmp_path, existing):
    built = tmp_path / "resourcepacks/build"
    served = tmp_path / "local-runtime/end-rift-server/resourcepacks/build"
    built.mkdir(parents=True)
    served.mkdir(parents=True)
    payload = b"latest authored pack payload\x00\xff"
    (built / "CopiMineResourcePack.zip").write_bytes(payload)
    if existing:
        (served / "CopiMineResourcePack.zip").write_bytes(b"stale HTTP copy")
    properties = served.parents[1] / "server.properties"
    original = "resource-pack=http\\://127.0.0.1\\:8092/CopiMineResourcePack.zip\nresource-pack-sha1=old\nresource-pack-prompt=Нужен пакет\nrcon.password=fixture-only\n"
    properties.write_text(original, encoding="utf-8")
    source = STARTER.read_text(encoding="utf-8")
    script = tmp_path / "probe.ps1"
    auth_helper = ROOT / "tests/EndRiftLocalAuthMode.ps1"
    script.write_text("param([string]$worktreeRoot,[string]$ServerDir,[string]$propertiesPath,[string]$sourcePackPath,"
                      "[string]$expectedLegacyPackSha256,[string]$expectedMigrationPackSha256)\n$ErrorActionPreference='Stop'\n"
                      + f". '{auth_helper}'\n$localRuntimeRoot = Join-Path $worktreeRoot 'local-runtime'\n"
                      + ps_function(source, "Get-LocalSha256") + "\n"
                      + ps_function(source, "Sync-LocalResourcePack") + "\nSync-LocalResourcePack\n", encoding="utf-8-sig")
    result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script),
                             str(tmp_path), str(served.parents[1]), str(properties),
                             str(built / "CopiMineResourcePack.zip"),
                             hashlib.sha256(payload).hexdigest(), "f" * 64], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert (served / "CopiMineResourcePack.zip").read_bytes() == payload
    sha1 = hashlib.sha1(payload).hexdigest()
    assert properties.read_text(encoding="utf-8") == original.replace("resource-pack-sha1=old", "resource-pack-sha1=" + sha1)
    assert (served / "CopiMineResourcePack.sha1").read_text().strip() == sha1
    assert (served / "CopiMineResourcePack.sha256").read_text().strip() == hashlib.sha256(payload).hexdigest()
    assert not (served / "CopiMineResourcePack.zip.next").exists()


def test_sync_runs_after_busy_port_rejection_and_before_paper_boot():
    source = STARTER.read_text(encoding="utf-8")
    busy_port_guard = source.index("$null = Assert-EndRiftLocalPortsAvailable -Ports @(25566, 25576)")
    invocation = source.index("\nSync-LocalResourcePack\n")
    assert busy_port_guard < invocation < source.index("$paper = Start-Process")


def test_startup_can_serve_a_pinned_26_3_pack_selected_outside_the_worktree(tmp_path):
    built = tmp_path / "resourcepacks/build"
    served = tmp_path / "local-runtime/end-rift-server/resourcepacks/build"
    profile = tmp_path / "profile/resourcepacks/CopiMineResourcePack-26.3.zip"
    built.mkdir(parents=True)
    served.mkdir(parents=True)
    profile.parent.mkdir(parents=True)
    legacy_payload = b"pinned legacy payload"
    candidate_payload = b"pinned 26.3 candidate payload"
    (built / "CopiMineResourcePack.zip").write_bytes(legacy_payload)
    profile.write_bytes(candidate_payload)
    properties = served.parents[1] / "server.properties"
    properties.write_text(
        "resource-pack=http\\://127.0.0.1\\:8092/CopiMineResourcePack.zip\n"
        "resource-pack-sha1=old\n",
        encoding="utf-8",
    )
    source = STARTER.read_text(encoding="utf-8")
    script = tmp_path / "migration-pack-probe.ps1"
    auth_helper = ROOT / "tests/EndRiftLocalAuthMode.ps1"
    script.write_text(
        "param([string]$worktreeRoot,[string]$ServerDir,[string]$propertiesPath,"
        "[string]$sourcePackPath,[string]$expectedLegacyPackSha256,[string]$expectedMigrationPackSha256)\n"
        "$ErrorActionPreference='Stop'\n"
        + f". '{auth_helper}'\n$localRuntimeRoot = Join-Path $worktreeRoot 'local-runtime'\n"
        + ps_function(source, "Get-LocalSha256")
        + "\n"
        + ps_function(source, "Sync-LocalResourcePack")
        + "\nSync-LocalResourcePack\n",
        encoding="utf-8-sig",
    )
    result = subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(script),
            str(tmp_path),
            str(served.parents[1]),
            str(properties),
            str(profile),
            hashlib.sha256(legacy_payload).hexdigest(),
            hashlib.sha256(candidate_payload).hexdigest(),
        ],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert (served / "CopiMineResourcePack.zip").read_bytes() == candidate_payload
    candidate_sha1 = hashlib.sha1(candidate_payload).hexdigest()
    assert f"resource-pack-sha1={candidate_sha1}" in properties.read_text(encoding="utf-8")

    unpinned = profile.parent / "unpinned.zip"
    unpinned.write_bytes(b"not a pinned artifact")
    rejected = subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(script),
            str(tmp_path),
            str(served.parents[1]),
            str(properties),
            str(unpinned),
            hashlib.sha256(legacy_payload).hexdigest(),
            hashlib.sha256(candidate_payload).hexdigest(),
        ],
        capture_output=True,
        text=True,
    )
    assert rejected.returncode != 0
    assert "Selected resource pack source SHA-256 is not pinned" in rejected.stdout + rejected.stderr
    assert (served / "CopiMineResourcePack.zip").read_bytes() == candidate_payload


def test_local_starter_exposes_pack_source_selection_and_reads_both_pinned_hashes():
    source = STARTER.read_text(encoding="utf-8")
    assert "[string]$ResourcePackSourcePath" in source
    assert "resourcePackMigration.legacyPackSha256" in source
    assert "resourcePackMigration.candidateSha256" in source
    assert "$selectedResourcePackSourcePath = if ([string]::IsNullOrWhiteSpace($ResourcePackSourcePath))" in source
    assert "$sourcePackPath = [IO.Path]::GetFullPath" in source
    assert "source SHA-256 is not pinned" in source


def test_local_starter_defaults_to_the_pinned_26_3_candidate_pack():
    source = STARTER.read_text(encoding="utf-8")
    assert "Join-Path $worktreeRoot 'build\\minecraft-26.3\\CopiMineResourcePack-26.3.zip'" in source
    assert "$defaultResourcePackSourcePath = Join-Path $worktreeRoot 'build\\minecraft-26.3\\CopiMineResourcePack-26.3.zip'" in source


def test_local_probe_leaves_native_memory_headroom_for_the_client():
    source = STARTER.read_text(encoding="utf-8")
    arguments = source[source.index("$paper = Start-Process"):source.index("$ready = $false")]
    assert "'-Xms128M'" in arguments
    assert "'-Xmx512M'" in arguments
    assert "'-Xss512k'" in arguments
    assert "'-XX:CICompilerCount=2'" in arguments
    assert "'-XX:TieredStopAtLevel=1'" in arguments
    assert "'-XX:HeapBaseMinAddress=34g'" in arguments
