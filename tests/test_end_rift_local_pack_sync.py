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
    script.write_text("param([string]$worktreeRoot,[string]$ServerDir,[string]$propertiesPath)\n$ErrorActionPreference='Stop'\n"
                      + ps_function(source, "Get-LocalSha256") + "\n"
                      + ps_function(source, "Sync-LocalResourcePack") + "\nSync-LocalResourcePack\n", encoding="utf-8-sig")
    result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script),
                             str(tmp_path), str(served.parents[1]), str(properties)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert (served / "CopiMineResourcePack.zip").read_bytes() == payload
    sha1 = hashlib.sha1(payload).hexdigest()
    assert properties.read_text(encoding="utf-8") == original.replace("resource-pack-sha1=old", "resource-pack-sha1=" + sha1)
    assert (served / "CopiMineResourcePack.sha1").read_text().strip() == sha1
    assert (served / "CopiMineResourcePack.sha256").read_text().strip() == hashlib.sha256(payload).hexdigest()
    assert not (served / "CopiMineResourcePack.zip.next").exists()


def test_sync_runs_after_busy_port_rejection_and_before_paper_boot():
    source = STARTER.read_text(encoding="utf-8")
    invocation = source.index("\nSync-LocalResourcePack\n")
    assert source.index("Refused to start because local port") < invocation < source.index("$paper = Start-Process")


def test_local_probe_leaves_native_memory_headroom_for_the_client():
    source = STARTER.read_text(encoding="utf-8")
    arguments = source[source.index("$paper = Start-Process"):source.index("$ready = $false")]
    assert "'-Xms256M'" in arguments
    assert "'-Xmx2G'" in arguments
