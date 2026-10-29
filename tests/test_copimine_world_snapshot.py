from __future__ import annotations

import importlib.util
import io
import tarfile
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts/minecraft/copimine_world_snapshot.py"
SPEC = importlib.util.spec_from_file_location("copimine_world_snapshot", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
WORLD_SNAPSHOT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WORLD_SNAPSHOT)


def _archive(path: Path, entries: list[tuple[str, bytes, bytes]]) -> None:
    with tarfile.open(path, "w:gz") as tar:
        for name, kind, payload in entries:
            info = tarfile.TarInfo(name)
            info.mtime = 1_700_000_000
            if kind == tarfile.DIRTYPE:
                info.type = tarfile.DIRTYPE
                tar.addfile(info)
            elif kind == tarfile.SYMTYPE:
                info.type = tarfile.SYMTYPE
                info.linkname = payload.decode("utf-8")
                tar.addfile(info)
            else:
                info.type = tarfile.REGTYPE
                info.size = len(payload)
                tar.addfile(info, io.BytesIO(payload))


def test_extracts_only_world_files_and_omits_the_stale_session_lock(tmp_path: Path) -> None:
    archive = tmp_path / "world.tar.gz"
    _archive(
        archive,
        [
            ("CopiMine/", tarfile.DIRTYPE, b""),
            ("CopiMine/level.dat", tarfile.REGTYPE, b"level"),
            ("CopiMine/region/r.0.0.mca", tarfile.REGTYPE, b"region"),
            ("CopiMine/session.lock", tarfile.REGTYPE, b"stale-lock"),
        ],
    )

    destination = tmp_path / "extracted"
    result = WORLD_SNAPSHOT.extract_world_snapshot(archive, destination)

    assert result["files"] == 2
    assert (destination / "CopiMine/level.dat").read_bytes() == b"level"
    assert (destination / "CopiMine/region/r.0.0.mca").read_bytes() == b"region"
    assert not (destination / "CopiMine/session.lock").exists()


@pytest.mark.parametrize(
    "entries",
    [
        [("CopiMine/../outside.txt", tarfile.REGTYPE, b"escape")],
        [("outside/level.dat", tarfile.REGTYPE, b"wrong root")],
        [
            ("CopiMine/level.dat", tarfile.REGTYPE, b"level"),
            ("CopiMine/linked", tarfile.SYMTYPE, b"level.dat"),
        ],
    ],
)
def test_rejects_unsafe_world_archives_before_creating_destination(
    tmp_path: Path, entries: list[tuple[str, bytes, bytes]]
) -> None:
    archive = tmp_path / "unsafe.tar.gz"
    _archive(archive, entries)
    destination = tmp_path / "extracted"

    with pytest.raises(ValueError):
        WORLD_SNAPSHOT.extract_world_snapshot(archive, destination)

    assert not destination.exists()


def test_refuses_to_extract_over_an_existing_destination(tmp_path: Path) -> None:
    archive = tmp_path / "world.tar.gz"
    _archive(archive, [("CopiMine/level.dat", tarfile.REGTYPE, b"level")])
    destination = tmp_path / "existing"
    destination.mkdir()

    with pytest.raises(FileExistsError):
        WORLD_SNAPSHOT.extract_world_snapshot(archive, destination)

    assert list(destination.iterdir()) == []
