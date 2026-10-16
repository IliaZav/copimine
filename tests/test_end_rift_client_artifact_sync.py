from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SYNC = ROOT / "tests" / "SyncEndRiftClientArtifacts.ps1"
POWERSHELL = shutil.which("powershell.exe") or shutil.which("powershell")


def _run_sync(source_client: Path, source_pack: Path, game_dir: Path) -> subprocess.CompletedProcess[str]:
    assert POWERSHELL, "Windows PowerShell is required for the local artifact sync contract"
    return subprocess.run(
        [
            POWERSHELL,
            "-NoLogo",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(SYNC),
            "-SourceClientJar",
            str(source_client),
            "-SourceResourcePack",
            str(source_pack),
            "-ClientGameDirectory",
            str(game_dir),
            "-SkipClientProcessCheck",
        ],
        capture_output=True,
        text=True,
        check=False,
    )


def test_sync_replaces_stale_client_artifacts_and_quarantines_every_old_copy(tmp_path: Path) -> None:
    """A local profile cannot retain a second, stale copimineclient mod JAR."""
    source_client = tmp_path / "source" / "CopiMineClient-0.1.1.jar"
    source_pack = tmp_path / "source" / "CopiMineResourcePack.zip"
    source_client.parent.mkdir()
    source_client.write_bytes(b"fresh-fabric-client")
    source_pack.write_bytes(b"fresh-resource-pack")

    game_dir = tmp_path / "ServerRP"
    mods = game_dir / "mods"
    packs = game_dir / "resourcepacks"
    mods.mkdir(parents=True)
    packs.mkdir()
    (mods / "CopiMineClient-0.1.0.jar").write_bytes(b"obsolete-client")
    (mods / "CopiMineClient-0.1.1.jar").write_bytes(b"stale-client")
    (mods / "fabric-api.jar").write_bytes(b"unrelated-mod")
    (packs / "CopiMineResourcePack.zip").write_bytes(b"stale-pack")

    result = _run_sync(source_client, source_pack, game_dir)

    assert result.returncode == 0, result.stderr
    assert (mods / "CopiMineClient-0.1.1.jar").read_bytes() == b"fresh-fabric-client"
    assert (packs / "CopiMineResourcePack.zip").read_bytes() == b"fresh-resource-pack"
    assert (mods / "fabric-api.jar").read_bytes() == b"unrelated-mod"
    assert not (mods / "CopiMineClient-0.1.0.jar").exists()
    backups = list((mods / "copimineclient-backups").glob("CopiMineClient-*.jar"))
    assert sorted(item.read_bytes() for item in backups) == [b"obsolete-client", b"stale-client"]
