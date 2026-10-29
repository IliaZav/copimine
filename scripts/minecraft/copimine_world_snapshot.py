"""Safely inspect and extract a CopiMine world snapshot tarball."""

from __future__ import annotations

import argparse
import os
import shutil
import tarfile
from pathlib import Path, PurePosixPath


WORLD_ROOT = "CopiMine"
MAX_MEMBERS = 250_000
MAX_UNCOMPRESSED_BYTES = 100 * 1024**3


def _validated_members(archive: Path) -> list[tarfile.TarInfo]:
    try:
        with tarfile.open(archive, "r:gz") as tar:
            members = tar.getmembers()
    except (OSError, tarfile.TarError) as error:
        raise ValueError(f"Cannot read world archive: {error}") from error

    if not members or len(members) > MAX_MEMBERS:
        raise ValueError(f"Unexpected world archive member count: {len(members)}")

    names: set[str] = set()
    total_bytes = 0
    has_level_dat = False
    for member in members:
        raw_name = member.name
        if not raw_name or "\\" in raw_name or ":" in raw_name or raw_name.startswith("/"):
            raise ValueError(f"Unsafe world archive path: {raw_name!r}")

        path = PurePosixPath(raw_name)
        parts = path.parts
        if not parts or parts[0] != WORLD_ROOT or any(part in ("", ".", "..") for part in parts):
            raise ValueError(f"World archive must contain only {WORLD_ROOT}/ entries: {raw_name!r}")

        normalized = "/".join(parts)
        if normalized in names:
            raise ValueError(f"Duplicate world archive path: {normalized!r}")
        names.add(normalized)

        if not (member.isdir() or member.isfile()):
            raise ValueError(f"Links and special files are not allowed in a world archive: {raw_name!r}")
        if member.isfile():
            total_bytes += member.size
            if member.size < 0 or total_bytes > MAX_UNCOMPRESSED_BYTES:
                raise ValueError("World archive exceeds the uncompressed size limit")
        if normalized == f"{WORLD_ROOT}/level.dat" and member.isfile():
            has_level_dat = True

    if not has_level_dat:
        raise ValueError(f"World archive is missing {WORLD_ROOT}/level.dat")
    return members


def extract_world_snapshot(archive: Path, destination: Path) -> dict[str, int]:
    """Extract regular world files to a new directory, excluding stale session.lock."""
    archive = archive.resolve(strict=True)
    destination = destination.resolve(strict=False)
    members = _validated_members(archive)
    if destination.exists():
        raise FileExistsError(f"Extraction destination already exists: {destination}")

    destination.mkdir(parents=True, exist_ok=False)
    extracted_files = 0
    extracted_bytes = 0
    directories: set[Path] = set()

    try:
        with tarfile.open(archive, "r:gz") as tar:
            for member in members:
                parts = PurePosixPath(member.name).parts
                target = destination.joinpath(*parts)
                if target != destination and destination not in target.parents:
                    raise ValueError(f"World archive path escapes the destination: {member.name!r}")

                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                    directories.add(target)
                    continue

                if member.name.rstrip("/") == f"{WORLD_ROOT}/session.lock":
                    continue

                target.parent.mkdir(parents=True, exist_ok=True)
                source = tar.extractfile(member)
                if source is None:
                    raise ValueError(f"Cannot read world archive file: {member.name!r}")
                with source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)
                actual_size = target.stat().st_size
                if actual_size != member.size:
                    raise ValueError(f"Truncated world archive file: {member.name!r}")
                os.utime(target, (member.mtime, member.mtime))
                extracted_files += 1
                extracted_bytes += actual_size

        world = destination / WORLD_ROOT
        if not (world / "level.dat").is_file():
            raise ValueError("Extracted world is missing level.dat")
        return {"files": extracted_files, "directories": len(directories), "bytes": extracted_bytes}
    except Exception:
        # Preserve any partial extraction for inspection; callers place it in a
        # timestamped backup directory and no existing world is overwritten.
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("destination", type=Path)
    arguments = parser.parse_args()
    result = extract_world_snapshot(arguments.archive, arguments.destination)
    print(
        "WORLD_SNAPSHOT_EXTRACTED "
        f"files={result['files']} directories={result['directories']} bytes={result['bytes']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
