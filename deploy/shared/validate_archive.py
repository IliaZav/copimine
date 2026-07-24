#!/usr/bin/env python3
"""Reject unsafe release archives before a privileged extraction.

Only regular files and directories are accepted.  Links and device/FIFO
members are deliberately rejected because a release archive is extracted by
root and must never be able to redirect writes outside its staging directory.
"""
from __future__ import annotations

import stat
import sys
import tarfile
import zipfile
from pathlib import PurePosixPath


def safe_path(raw: str) -> str:
    value = raw.replace("\\", "/")
    path = PurePosixPath(value)
    if not value or path.is_absolute() or ".." in path.parts:
        raise ValueError(f"unsafe archive path: {raw!r}")
    # Windows drive prefixes are not absolute to PurePosixPath on Linux.
    if len(value) >= 2 and value[1] == ":":
        raise ValueError(f"unsafe archive path: {raw!r}")
    return value.rstrip("/")


def check_tar(path: str) -> None:
    seen: set[str] = set()
    with tarfile.open(path, "r:*") as archive:
        for member in archive:
            name = safe_path(member.name)
            if name in seen:
                raise ValueError(f"duplicate archive member: {member.name!r}")
            seen.add(name)
            if not (member.isreg() or member.isdir()):
                raise ValueError(f"archive member is not a regular file or directory: {member.name!r}")
            if member.issym() or member.islnk():
                raise ValueError(f"archive links are not allowed: {member.name!r}")


def check_zip(path: str) -> None:
    seen: set[str] = set()
    with zipfile.ZipFile(path) as archive:
        for member in archive.infolist():
            name = safe_path(member.filename)
            if name in seen:
                raise ValueError(f"duplicate archive member: {member.filename!r}")
            seen.add(name)
            mode = (member.external_attr >> 16) & 0xFFFF
            if mode and not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
                raise ValueError(f"zip member is not a regular file or directory: {member.filename!r}")
            if stat.S_ISLNK(mode):
                raise ValueError(f"zip links are not allowed: {member.filename!r}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: validate_archive.py ARCHIVE", file=sys.stderr)
        return 2
    try:
        archive = sys.argv[1]
        if archive.lower().endswith((".tar.gz", ".tgz", ".tar")):
            check_tar(archive)
        elif archive.lower().endswith(".zip"):
            check_zip(archive)
        else:
            raise ValueError("unsupported archive extension")
    except (OSError, tarfile.TarError, zipfile.BadZipFile, ValueError) as exc:
        print(f"archive validation failed: {exc}", file=sys.stderr)
        return 1
    print("archive members are safe")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
