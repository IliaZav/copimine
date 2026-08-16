#!/usr/bin/env python3
"""Build a signed CopiMine instance manifest from real local artifacts.

The private key is an input secret only. It is never copied to the output tree
and the generated signature covers the exact UTF-8 bytes written to disk.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


COMPONENT_ID_OVERRIDES = {
    "CopiMineClient": "copimine-client",
    "CustomSkinLoader": "custom-skin-loader",
    "Emotecraft": "emotecraft",
    "Fabric API": "fabric-api",
    "Mod Menu": "mod-menu",
    "Simple Voice Chat": "simple-voice-chat",
    "Iris": "iris",
    "Sodium": "sodium",
}
SAFE_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--private-key-file", type=Path)
    parser.add_argument("--private-key-hex")
    parser.add_argument("--public-key-id", required=True)
    parser.add_argument("--instance-version", required=True)
    parser.add_argument("--release-sequence", type=int, required=True)
    parser.add_argument("--published-at", required=True)
    parser.add_argument("--java-archive", type=Path, required=True)
    parser.add_argument("--java-version", default="21.0.10")
    parser.add_argument("--java-provider", default="Eclipse Adoptium")
    parser.add_argument("--java-build-id", default="temurin-21")
    parser.add_argument("--download-origin", default="https://copimine.ru/launcher/files")
    parser.add_argument("--minimum-launcher-version", default="1.0.0")
    return parser.parse_args()


def sha256(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(chunk)
            digest.update(chunk)
    return digest.hexdigest(), size


def load_private_key(args: argparse.Namespace) -> Ed25519PrivateKey:
    supplied = [args.private_key_file is not None, args.private_key_hex is not None, bool(os.environ.get("COPIMINE_MANIFEST_PRIVATE_KEY_HEX"))]
    if sum(supplied) != 1:
        raise SystemExit("Provide exactly one private key source: --private-key-file, --private-key-hex, or COPIMINE_MANIFEST_PRIVATE_KEY_HEX")
    if args.private_key_file is not None:
        raw = args.private_key_file.read_bytes().strip()
        value = raw.decode("ascii")
    elif args.private_key_hex is not None:
        value = args.private_key_hex
    else:
        value = os.environ["COPIMINE_MANIFEST_PRIVATE_KEY_HEX"]
    try:
        seed = bytes.fromhex(value)
    except ValueError as error:
        raise SystemExit("Manifest private key must be hexadecimal") from error
    if len(seed) != 32:
        raise SystemExit("Manifest private key must contain exactly 32 bytes")
    return Ed25519PrivateKey.from_private_bytes(seed)


def stable_component_id(name: str) -> str:
    value = COMPONENT_ID_OVERRIDES.get(name, re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-"))
    if not SAFE_ID.fullmatch(value):
        raise SystemExit(f"Unsafe component id derived from {name!r}: {value!r}")
    return value


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_bytes(data)
    temporary.replace(path)


def copy_hashed(source: Path, output_files: Path) -> tuple[str, int]:
    if not source.is_file():
        raise SystemExit(f"Required release artifact is missing: {source}")
    digest, size = sha256(source)
    output_files.mkdir(parents=True, exist_ok=True)
    destination = output_files / digest
    if not destination.exists() or destination.stat().st_size != size:
        shutil.copyfile(source, destination)
    return digest, size


def main() -> None:
    args = parse_args()
    repo_root = args.repo_root.resolve()
    output_dir = args.output_dir.resolve()
    manifest_source = repo_root / "thirdparty" / "modpack_manifest.json"
    modpack = json.loads(manifest_source.read_text(encoding="utf-8"))
    if modpack.get("schemaVersion") != 2 or modpack.get("minecraftVersion") != "1.21.1" or modpack.get("loader") != "Fabric":
        raise SystemExit("thirdparty/modpack_manifest.json is not the expected Minecraft 1.21.1 Fabric source")
    if args.release_sequence <= 0:
        raise SystemExit("release sequence must be positive")
    published_at = datetime.fromisoformat(args.published_at.replace("Z", "+00:00"))
    if published_at.tzinfo is None:
        published_at = published_at.replace(tzinfo=timezone.utc)
    published_at = published_at.astimezone(timezone.utc)

    private_key = load_private_key(args)
    public_key = private_key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    if len(public_key) != 32:
        raise SystemExit("Unexpected Ed25519 public key length")

    output_files = output_dir / "files"
    entries: list[dict[str, object]] = []
    component_ids: set[str] = set()
    paths: set[str] = set()
    for source_entry in modpack.get("files", []):
        name = str(source_entry.get("component", ""))
        component_id = stable_component_id(name)
        relative_path = str(source_entry.get("path", "")).replace("\\", "/")
        source = repo_root / "thirdparty" / "client-mods" / Path(relative_path).name
        digest, size = copy_hashed(source, output_files)
        if component_id in component_ids or relative_path.lower() in paths:
            raise SystemExit(f"Duplicate component/path in source manifest: {component_id} / {relative_path}")
        component_ids.add(component_id)
        paths.add(relative_path.lower())
        entries.append(
            {
                "componentId": component_id,
                "path": relative_path,
                "url": f"{args.download_origin.rstrip('/')}/{digest}",
                "sha256": digest,
                "size": size,
                "ownership": "MANAGED",
                "required": bool(source_entry.get("required", False)),
                "kind": "mod",
                "version": str(source_entry.get("version", args.instance_version)),
                "installPolicy": "REPLACE",
            }
        )

    java_archive = args.java_archive.resolve()
    java_digest, java_size = copy_hashed(java_archive, output_files)
    document: dict[str, object] = {
        "schemaVersion": 2,
        "channel": "stable",
        "releaseId": args.instance_version,
        "publishedAtUtc": published_at.isoformat().replace("+00:00", "Z"),
        "minimumLauncherVersion": args.minimum_launcher_version,
        "minecraft": {"version": "1.21.1", "fabricLoaderVersion": "0.19.3", "javaMajor": 21},
        "server": {"name": "CopiMine", "address": "mc.copimine.ru", "acceptServerResourcePack": True, "port": 25565},
        "files": entries,
        "configPolicies": [],
        "newsUrl": "https://copimine.ru/news/copimine-launcher-1-0-0.html",
        "releaseSequence": args.release_sequence,
        "javaRuntime": {
            "provider": args.java_provider,
            "buildId": args.java_build_id,
            "platform": "windows-x64",
            "version": args.java_version,
            "url": f"{args.download_origin.rstrip('/')}/{java_digest}",
            "sizeBytes": java_size,
            "sha256": java_digest,
        },
        "publicKeyId": args.public_key_id,
    }
    manifest_bytes = (json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    signature = private_key.sign(manifest_bytes)
    signature_document = {
        "algorithm": "Ed25519",
        "publicKeyId": args.public_key_id,
        "signatureBase64": __import__("base64").b64encode(signature).decode("ascii"),
    }
    atomic_write(output_dir / "instance-manifest.json", manifest_bytes)
    atomic_write(
        output_dir / "instance-manifest.sig",
        (json.dumps(signature_document, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8"),
    )
    (output_dir / "public-key.hex").write_text(public_key.hex() + "\n", encoding="ascii")
    print(f"MANIFEST_OUTPUT={output_dir / 'instance-manifest.json'}")
    print(f"SIGNATURE_OUTPUT={output_dir / 'instance-manifest.sig'}")
    print(f"MANIFEST_SHA256={hashlib.sha256(manifest_bytes).hexdigest()}")
    print(f"PUBLIC_KEY_HEX={public_key.hex()}")
    print(f"MANAGED_FILE_COUNT={len(entries)}")


if __name__ == "__main__":
    main()
