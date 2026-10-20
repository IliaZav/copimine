"""Capture the actual isolated Minecraft 26.3 plugin JARs for native acceptance."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import zipfile

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
CANDIDATE_SNAPSHOT_DIRECTORY = "build/minecraft-26.3/native-plugin-candidate"
sys.path.insert(0, str(REPOSITORY_ROOT))

from scripts.minecraft.install_migration_server_plugins import capture_plugin_inventory
from scripts.minecraft.validate_native_acceptance import (
    AcceptanceError,
    EVIDENCE_DIRECTORY,
    _expected_plugins,
    _read_plugin_rows,
    _replacement_plugin_names,
    _read_json,
    _read_json_with_bytes,
)


def _is_regular(path: Path, description: str) -> None:
    info = path.lstat()
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag or not stat.S_ISREG(info.st_mode):
        raise AcceptanceError(f"{description} must be a regular file without reparse points")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _atomic_write(destination: Path, writer) -> None:
    if destination.exists() or destination.is_symlink():
        _is_regular(destination, "acceptance evidence destination")
    descriptor, name = tempfile.mkstemp(prefix=destination.name + ".", suffix=".candidate", dir=destination.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            writer(stream)
        _is_regular(temporary, "temporary acceptance evidence")
        os.replace(temporary, destination)
        _is_regular(destination, "published acceptance evidence")
    finally:
        temporary.unlink(missing_ok=True)


def capture(root: Path, *, candidate_mode: bool = False) -> dict[str, dict[str, str]]:
    root = root.resolve()
    server = root / "local-runtime/end-rift-server-26.3"
    plugin_directory = server / "plugins"
    if not server.is_dir() or server.resolve() != server:
        raise AcceptanceError("isolated Minecraft 26.3 server directory is missing or redirected")
    if not plugin_directory.is_dir() or plugin_directory.resolve() != plugin_directory:
        raise AcceptanceError("isolated Minecraft 26.3 plugins directory is missing or redirected")

    lock_path = root / "tools/minecraft-26.3/server-plugins.lock.json"
    lock = _read_json(lock_path, "Minecraft 26.3 server plugin lock")
    if candidate_mode:
        if (
            lock.get("minecraftVersion") != "26.3"
            or lock.get("status") != "runtime-candidates"
            or lock.get("nativeVerified") is not False
        ):
            raise AcceptanceError("candidate snapshot mode requires the unaccepted runtime-candidates server plugin lock")
    elif (
        lock.get("minecraftVersion") != "26.3"
        or lock.get("status") != "migration-accepted"
        or lock.get("nativeVerified") is not True
    ):
        raise AcceptanceError("server plugin lock must be natively accepted before capturing installed plugin bytes")
    expected = _expected_plugins(lock, require_native_verified=not candidate_mode)
    inventory = capture_plugin_inventory(plugin_directory)
    observed = {row["pluginName"]: row for row in inventory}
    if set(observed) != set(expected):
        raise AcceptanceError("actual runtime plugin identities differ from the accepted server lock")
    for name, locked in expected.items():
        row = observed[name]
        if row["filename"] != locked["filename"] or row["sha256"] != locked["sha256"]:
            raise AcceptanceError(f"actual runtime plugin bytes differ from accepted lock: {name}")

    receipt_path = server / "migration-plugin-receipt.json"
    _is_regular(receipt_path, "migration plugin receipt")
    receipt_bytes, receipt = _read_json_with_bytes(receipt_path, "migration plugin receipt")
    if (
        receipt.get("schemaVersion") != 1
        or receipt.get("minecraftVersion") != "26.3"
        or receipt.get("nativeVerified") is not False
        or receipt.get("pluginInventory") != inventory
    ):
        raise AcceptanceError("installer receipt does not match actual runtime plugin bytes")

    replacement_names = _replacement_plugin_names(lock)
    installed = _read_plugin_rows(receipt.get("installed"), "installer receipt replacement inventory")
    if set(installed) != replacement_names:
        raise AcceptanceError("installer receipt replacement list differs from locked replacement candidates")
    for name in replacement_names:
        row = observed[name]
        replacement = installed[name]
        if replacement.get("filename") != row["filename"] or replacement.get("sha256") != row["sha256"]:
            raise AcceptanceError(f"installer receipt does not match installed replacement bytes: {name}")

    evidence_relative_directory = (
        Path(CANDIDATE_SNAPSHOT_DIRECTORY) if candidate_mode else Path(EVIDENCE_DIRECTORY)
    )
    evidence_directory = root
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    for part in evidence_relative_directory.parts:
        evidence_directory = evidence_directory / part
        if evidence_directory.exists() or evidence_directory.is_symlink():
            info = evidence_directory.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag or not stat.S_ISDIR(info.st_mode):
                raise AcceptanceError("native acceptance evidence path must contain only regular directories")
        else:
            evidence_directory.mkdir()
    snapshot_path = evidence_directory / "installed-plugins.zip"
    inventory_path = evidence_directory / "server-plugins.json"
    receipt_copy_path = evidence_directory / "migration-plugin-receipt.json"

    def write_snapshot(stream) -> None:
        with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for row in sorted(inventory, key=lambda value: value["filename"].casefold()):
                info = zipfile.ZipInfo(row["filename"], date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                copied_digest = hashlib.sha256()
                with archive.open(info, "w") as output, (plugin_directory / row["filename"]).open("rb") as source:
                    for chunk in iter(lambda: source.read(1024 * 1024), b""):
                        copied_digest.update(chunk)
                        output.write(chunk)
                if copied_digest.hexdigest() != row["sha256"]:
                    raise AcceptanceError(
                        f"copied plugin JAR bytes differ from inventory: {row['pluginName']}"
                    )

    _atomic_write(snapshot_path, write_snapshot)
    inventory_bytes = (json.dumps({"plugins": inventory}, sort_keys=True, indent=2) + "\n").encode("utf-8")
    _atomic_write(inventory_path, lambda stream: stream.write(inventory_bytes))
    _atomic_write(receipt_copy_path, lambda stream: stream.write(receipt_bytes))

    def entry(path: Path) -> dict[str, str]:
        return {"path": path.relative_to(root).as_posix(), "sha256": _sha256(path)}

    return {
        "pluginInventory": entry(inventory_path),
        "installerReceipt": entry(receipt_copy_path),
        "pluginSnapshot": entry(snapshot_path),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument(
        "--candidate",
        action="store_true",
        help=(
            "capture an exact full-lock plugin snapshot under build/ while the lock is unaccepted; "
            "does not touch signed acceptance evidence"
        ),
    )
    args = parser.parse_args(argv)
    try:
        print(json.dumps(capture(args.root, candidate_mode=args.candidate), sort_keys=True, indent=2))
    except (AcceptanceError, OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print(f"Cannot capture native plugin snapshot: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
