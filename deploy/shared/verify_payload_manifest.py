#!/usr/bin/env python3
"""Verify every release payload file covered by the signed manifest.

The release manifest and its signature are deliberately excluded from the
inventory: the manifest signs the inventory, so hashing the manifest itself
would be circular.  The signature is checked separately by the installer and
the signing allowlist is a host trust anchor, not release payload data.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath


EXCLUDED = {
    "deploy/release_manifest.json",
    "deploy/release_manifest.sig",
    "deploy/release-signing.allowed",
}
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def safe_relative(raw: object) -> str:
    if not isinstance(raw, str):
        raise ValueError("payload manifest path must be a string")
    value = raw.replace("\\", "/")
    path = PurePosixPath(value)
    if (
        not value
        or path.is_absolute()
        or ".." in path.parts
        or (len(value) >= 2 and value[1] == ":")
    ):
        raise ValueError(f"unsafe payload manifest path: {raw!r}")
    normalized = path.as_posix()
    if normalized != value.rstrip("/"):
        raise ValueError(f"non-canonical payload manifest path: {raw!r}")
    return normalized


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: verify_payload_manifest.py PAYLOAD_ROOT RELEASE_MANIFEST", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    manifest_path = Path(sys.argv[2]).resolve()
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
        entries = manifest.get("payloadFiles")
        if not isinstance(entries, list) or not entries:
            raise ValueError("signed release manifest has no payloadFiles inventory")

        expected: dict[str, tuple[str, int]] = {}
        for entry in entries:
            if not isinstance(entry, dict):
                raise ValueError("payloadFiles entries must be objects")
            relative = safe_relative(entry.get("path"))
            if relative in EXCLUDED:
                raise ValueError(f"payload inventory must not include excluded file: {relative}")
            if relative in expected:
                raise ValueError(f"duplicate payload inventory entry: {relative}")
            checksum = str(entry.get("sha256", "")).lower()
            if not HEX64.fullmatch(checksum):
                raise ValueError(f"invalid payload SHA256 for {relative}")
            size = entry.get("sizeBytes")
            if isinstance(size, bool) or not isinstance(size, int) or size < 0:
                raise ValueError(f"invalid payload size for {relative}")
            expected[relative] = (checksum, size)

        actual: dict[str, Path] = {}
        for path in root.rglob("*"):
            if path.is_symlink():
                raise ValueError(f"release payload contains a symlink: {path.relative_to(root)}")
            if not path.is_file():
                continue
            relative = path.relative_to(root).as_posix()
            if relative in EXCLUDED:
                continue
            actual[relative] = path

        missing = sorted(set(expected) - set(actual))
        unexpected = sorted(set(actual) - set(expected))
        if missing:
            raise ValueError("payload files missing from extracted release: " + ", ".join(missing[:8]))
        if unexpected:
            raise ValueError("extracted release contains unsigned payload files: " + ", ".join(unexpected[:8]))

        for relative, (expected_hash, expected_size) in expected.items():
            path = actual[relative]
            actual_size = path.stat().st_size
            if actual_size != expected_size:
                raise ValueError(
                    f"payload size mismatch for {relative}: expected {expected_size}, got {actual_size}"
                )
            actual_hash = digest(path)
            if actual_hash != expected_hash:
                raise ValueError(
                    f"payload SHA256 mismatch for {relative}: expected {expected_hash}, got {actual_hash}"
                )
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"payload manifest verification failed: {exc}", file=sys.stderr)
        return 1

    print(f"payload manifest verified: {len(expected)} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
