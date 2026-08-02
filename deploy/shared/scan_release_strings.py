#!/usr/bin/env python3
"""Scan release binaries for accidental secrets and retired CopiMine fields."""
from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


SECRET_PATTERNS = (
    (
        re.compile(
            rb"-----BEGIN [A-Z0-9 ]+ PRIVATE KEY-----\r?\n"
            rb"[A-Za-z0-9+/=\r\n]{64,}\r?\n"
            rb"-----END [A-Z0-9 ]+ PRIVATE KEY-----"
        ),
        "embedded private-key material",
    ),
    (re.compile(rb"AKIA[0-9A-Z]{16}"), "AWS access-key marker"),
    (re.compile(rb"(?:gh[pousr]_|github_pat_)[A-Za-z0-9_]{20,}"), "GitHub token marker"),
    (re.compile(rb"sk-(?:live|test|proj)-[A-Za-z0-9_-]{16,}"), "payment/API key marker"),
)
RETIRED_MARKERS = (
    (b"visiblePin", "visiblePin field"),
)


def read_members(path: Path):
    if path.suffix.lower() in {".jar", ".zip"}:
        with zipfile.ZipFile(path) as archive:
            for member in archive.infolist():
                if member.is_dir():
                    continue
                try:
                    yield f"{path.name}!/{member.filename}", archive.read(member)
                except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
                    raise ValueError(f"could not read {path}!/{member.filename}: {exc}") from exc
    else:
        yield path.name, path.read_bytes()


def scan_path(path: Path) -> list[str]:
    findings: list[str] = []
    for member_name, data in read_members(path):
        for pattern, label in SECRET_PATTERNS:
            if pattern.search(data):
                findings.append(f"{member_name}: {label}")
        for marker, label in RETIRED_MARKERS:
            if marker in data or marker.decode("ascii").encode("utf-16le") in data:
                findings.append(f"{member_name}: {label}")
    return findings


def load_paths(root: Path, sbom: Path | None) -> list[Path]:
    if sbom is not None:
        document = json.loads(sbom.read_text(encoding="utf-8"))
        raw_paths = [str(item["fileName"]) for item in document.get("files", [])]
    else:
        raw_paths = []
        for candidate in (root / "minecraft" / "server" / "purpur.jar", root / "thirdparty"):
            if candidate.is_file():
                raw_paths.append(candidate.relative_to(root).as_posix())
            elif candidate.is_dir():
                raw_paths.extend(path.relative_to(root).as_posix() for path in candidate.rglob("*") if path.is_file())
        raw_paths.extend(path.relative_to(root).as_posix() for path in (root / "minecraft" / "server" / "plugins").glob("*.jar"))
    paths: list[Path] = []
    for raw in sorted(set(raw_paths)):
        relative = PurePosixPath(raw)
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError(f"unsafe scan path: {raw}")
        path = root.joinpath(*relative.parts)
        if not path.is_file():
            raise FileNotFoundError(f"scan artifact is missing: {raw}")
        paths.append(path)
    return paths


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--sbom", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    sbom = args.sbom.resolve() if args.sbom else None
    paths = load_paths(root, sbom)
    findings: list[str] = []
    for path in paths:
        findings.extend(scan_path(path))
    if findings:
        print("Release binary scan failed:", file=sys.stderr)
        for finding in findings:
            print(f" - {finding}", file=sys.stderr)
        return 1
    print(f"Release binary scan passed ({len(paths)} artifacts).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
