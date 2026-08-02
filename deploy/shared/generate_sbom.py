#!/usr/bin/env python3
"""Generate a dependency and binary inventory for a CopiMine release.

The release archive is intentionally assembled from a tracked tree plus an
explicit generated-artifact allowlist.  This small, dependency-free tool
records the hashes of those binary artifacts in SPDX 2.3 JSON and records the
pinned Python requirements as SPDX packages.  It does not claim that a file is
licensed when the repository does not provide that information.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any


SHA256 = re.compile(r"^[0-9a-f]{64}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def posix_relative(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


def package_id(prefix: str, value: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9.-]+", "-", value).strip("-") or "item"
    return f"SPDXRef-{prefix}-{safe}"


def parse_requirements(path: Path) -> list[tuple[str, str]]:
    packages: list[tuple[str, str]] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line or line.startswith(("-", "git+", "http:" , "https:")):
            continue
        requirement = line.split(";", 1)[0].strip()
        match = re.fullmatch(r"([A-Za-z0-9_.-]+(?:\[[A-Za-z0-9_,.-]+\])?)==([A-Za-z0-9_.+!-]+)", requirement)
        if not match:
            raise ValueError(f"Python dependency is not exactly pinned: {raw}")
        packages.append((match.group(1), match.group(2)))
    return packages


def checksum_paths(root: Path) -> list[str]:
    manifest = root / "thirdparty" / "checksums.txt"
    paths: list[str] = []
    for raw in manifest.read_text(encoding="ascii").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) != 3 or parts[0] != "SHA256" or not SHA256.fullmatch(parts[2]):
            raise ValueError(f"Malformed third-party checksum line: {raw}")
        paths.append(parts[1])
    return paths


def artifact_paths(root: Path) -> list[str]:
    release = json.loads((root / "deploy" / "release_manifest.json").read_text(encoding="utf-8"))
    paths = {
        str(release["resourcePack"]["path"]),
        str(release["modpack"]["path"]),
        str(release["clientMod"]["path"]),
        *map(str, release["serverPlugins"].keys()),
        *checksum_paths(root),
    }
    for candidate in [root / "minecraft" / "server" / "purpur.jar"]:
        if candidate.is_file():
            paths.add(posix_relative(root, candidate))
    for directory in (
        root / "minecraft" / "server" / "plugins",
        root / "minecraft" / "server" / "libraries",
    ):
        if directory.is_dir():
            for candidate in directory.rglob("*.jar"):
                paths.add(posix_relative(root, candidate))
    return sorted(paths)


def make_file(root: Path, relative: str, index: int) -> dict[str, Any]:
    normalized = PurePosixPath(relative)
    if normalized.is_absolute() or ".." in normalized.parts:
        raise ValueError(f"Unsafe SBOM path: {relative}")
    path = root.joinpath(*normalized.parts)
    if not path.is_file():
        raise FileNotFoundError(f"SBOM artifact is missing: {relative}")
    digest = sha256(path)
    return {
        "SPDXID": f"SPDXRef-File-{index:04d}",
        "fileName": normalized.as_posix(),
        "checksums": [{"algorithm": "SHA256", "checksumValue": digest}],
        "licenseConcluded": "NOASSERTION",
        "licenseInfoInFiles": ["NOASSERTION"],
        "copyrightText": "NOASSERTION",
    }


def build_document(root: Path) -> dict[str, Any]:
    release = json.loads((root / "deploy" / "release_manifest.json").read_text(encoding="utf-8"))
    commit = str(release.get("gitCommit", "unknown"))
    files = [make_file(root, relative, index) for index, relative in enumerate(artifact_paths(root), start=1)]

    packages: list[dict[str, Any]] = [
        {
            "SPDXID": "SPDXRef-Package-CopiMine",
            "name": "CopiMine release",
            "versionInfo": commit,
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": True,
            "licenseConcluded": "NOASSERTION",
            "licenseDeclared": "NOASSERTION",
            "copyrightText": "NOASSERTION",
        }
    ]
    for name, version in parse_requirements(root / "admin-web" / "requirements.txt"):
        packages.append(
            {
                "SPDXID": package_id("Package-Python", name),
                "name": name,
                "versionInfo": version,
                "downloadLocation": "NOASSERTION",
                "filesAnalyzed": False,
                "licenseConcluded": "NOASSERTION",
                "licenseDeclared": "NOASSERTION",
                "copyrightText": "NOASSERTION",
            }
        )

    relationships = [
        {
            "spdxElementId": "SPDXRef-Package-CopiMine",
            "relationshipType": "CONTAINS",
            "relatedSpdxElement": item["SPDXID"],
        }
        for item in files
    ]

    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "CopiMine release SBOM",
        "documentNamespace": f"https://copimine.ru/spdx/{commit}",
        "creationInfo": {
            "created": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "creators": ["Tool: CopiMine generate_sbom.py"],
        },
        "packages": packages,
        "files": files,
        "relationships": relationships,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    document = build_document(root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"SPDX SBOM written: {args.output} ({len(document['files'])} binary files, {len(document['packages'])} packages)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
