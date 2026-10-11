"""Fail-closed verification for retained native Minecraft 26.3 evidence."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import stat
import struct
import sys
import zlib
import zipfile
from pathlib import Path, PurePosixPath

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
import rfc8785


class AcceptanceError(ValueError):
    """Raised when the native acceptance record is incomplete or inconsistent."""


SERVER_CHECKS = ("paperStarted", "pluginsLoaded", "resourcePackDelivered", "bridgeHandshake", "restartRecovery")
CLIENT_CHECKS = ("fabricStarted", "modLoaded", "resourcePackApplied", "bridgeHandshake", "customModelRendered", "shaderRuntime")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SHA512_RE = re.compile(r"^[0-9a-f]{128}$")
EVIDENCE_DIRECTORY = "artifacts/minecraft-26.3/native-acceptance"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_PNG_BYTES = 128 * 1024 * 1024
MAX_SIGNATURE_BYTES = 1024
ACCEPTANCE_PUBLIC_KEY_ENV = "MIGRATION_ACCEPTANCE_PUBLIC_KEY"
ACCEPTANCE_SIGNATURE_PATH = EVIDENCE_DIRECTORY + "/acceptance.sig"
ACCEPTANCE_SIGNATURE_CONTEXT = b"CopiMine Minecraft 26.3 native acceptance v1\n"
MAX_JSON_BYTES = 10 * 1024 * 1024


def _parse_json_bytes(raw: bytes, description: str) -> dict:
    if len(raw) > MAX_JSON_BYTES:
        raise AcceptanceError(f"{description} exceeds the 10 MiB safety limit")
    try:
        value = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=_unique_object)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise AcceptanceError(f"Cannot read {description}: {exc}") from exc
    if not isinstance(value, dict):
        raise AcceptanceError(f"{description} must be a JSON object")
    return value


def _read_json_with_bytes(path: Path, description: str) -> tuple[bytes, dict]:
    try:
        with path.open("rb") as source:
            raw = source.read(MAX_JSON_BYTES + 1)
    except OSError as exc:
        raise AcceptanceError(f"Cannot read {description}: {exc}") from exc
    return raw, _parse_json_bytes(raw, description)


def _read_json(path: Path, description: str) -> dict:
    return _read_json_with_bytes(path, description)[1]


def _unique_object(pairs: list[tuple[str, object]]) -> dict:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise AcceptanceError(f"JSON object contains a duplicate property: {key}")
        result[key] = value
    return result


def _read_signed_acceptance(
    root: Path,
    evidence_path: Path,
    public_key_hex: str | None = None,
    *,
    detached_signature: bytes | None = None,
    verified_digests: dict[str, str] | None = None,
) -> dict:
    public_key_hex = public_key_hex if public_key_hex is not None else os.environ.get(ACCEPTANCE_PUBLIC_KEY_ENV)
    if not isinstance(public_key_hex, str) or not re.fullmatch(r"[0-9a-f]{64}", public_key_hex):
        raise AcceptanceError(f"trusted Ed25519 key is missing or invalid in {ACCEPTANCE_PUBLIC_KEY_ENV}")

    signature_path: Path | None = None
    try:
        with evidence_path.open("rb") as record_file:
            record_bytes = record_file.read(MAX_JSON_BYTES + 1)
        if detached_signature is None:
            try:
                signature_path = _relative_file(root, ACCEPTANCE_SIGNATURE_PATH, "native acceptance signature")
            except AcceptanceError as exc:
                if "file is missing" in str(exc):
                    raise AcceptanceError("native acceptance detached signature is missing") from exc
                raise
            with signature_path.open("rb") as signature_file:
                signature_text = signature_file.read(MAX_SIGNATURE_BYTES + 1)
        else:
            if not isinstance(detached_signature, bytes):
                raise AcceptanceError("native acceptance detached signature is invalid")
            signature_text = detached_signature
    except OSError as exc:
        raise AcceptanceError(f"Cannot read native acceptance signature or record: {exc}") from exc
    if len(record_bytes) > MAX_JSON_BYTES:
        raise AcceptanceError("native acceptance record exceeds the 10 MiB safety limit")
    if len(signature_text) > MAX_SIGNATURE_BYTES:
        raise AcceptanceError("native acceptance signature exceeds the 1 KiB safety limit")

    try:
        value = json.loads(record_bytes.decode("utf-8"), object_pairs_hook=_unique_object)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise AcceptanceError(f"Cannot read native acceptance record as UTF-8 JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise AcceptanceError("native acceptance record must be a JSON object")
    try:
        canonical_record = rfc8785.dumps(value)
    except (rfc8785.CanonicalizationError, ValueError, TypeError) as exc:
        raise AcceptanceError(f"native acceptance record is not valid RFC 8785 JSON: {exc}") from exc
    if record_bytes != canonical_record:
        raise AcceptanceError("native acceptance record must use RFC 8785 canonical JSON encoding")

    raw_signature_text = signature_text
    if signature_text.endswith(b"\r\n"):
        signature_text = signature_text[:-2]
    elif signature_text.endswith(b"\n"):
        signature_text = signature_text[:-1]
    try:
        encoded_signature = signature_text.decode("ascii")
        signature = base64.b64decode(encoded_signature, validate=True)
    except (UnicodeError, ValueError) as exc:
        raise AcceptanceError("native acceptance detached signature is missing or malformed") from exc
    if base64.b64encode(signature).decode("ascii") != encoded_signature or len(signature) != 64:
        raise AcceptanceError("native acceptance detached signature is missing or malformed")

    try:
        public_key = Ed25519PublicKey.from_public_bytes(bytes.fromhex(public_key_hex))
        public_key.verify(signature, ACCEPTANCE_SIGNATURE_CONTEXT + canonical_record)
    except InvalidSignature as exc:
        raise AcceptanceError("native acceptance detached signature does not match the trusted key and record") from exc
    except ValueError as exc:
        raise AcceptanceError(f"trusted Ed25519 key in {ACCEPTANCE_PUBLIC_KEY_ENV} is invalid") from exc
    if verified_digests is not None:
        verified_digests[EVIDENCE_DIRECTORY + "/acceptance.json"] = hashlib.sha256(record_bytes).hexdigest()
        if signature_path is not None:
            verified_digests[ACCEPTANCE_SIGNATURE_PATH] = hashlib.sha256(raw_signature_text).hexdigest()
    return value


def _sha_file(path: Path, algorithm: str = "sha256") -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _relative_file(root: Path, relative: object, description: str, *, prefix: str | None = None) -> Path:
    if not isinstance(relative, str) or not relative or "\\" in relative or ":" in relative:
        raise AcceptanceError(f"{description} path must be a relative POSIX path")
    posix_path = PurePosixPath(relative)
    if posix_path.is_absolute() or any(part in ("", ".", "..") for part in posix_path.parts):
        raise AcceptanceError(f"{description} path escapes the repository")
    if prefix is not None and not relative.startswith(prefix):
        raise AcceptanceError(f"{description} must be retained under {prefix}")

    current = root
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    for part in posix_path.parts:
        current = current / part
        try:
            info = current.lstat()
        except OSError as exc:
            raise AcceptanceError(f"{description} file is missing: {relative}") from exc
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag:
            raise AcceptanceError(f"{description} path must not contain symlinks or reparse points")
    if not stat.S_ISREG(current.lstat().st_mode):
        raise AcceptanceError(f"{description} is not a regular file: {relative}")
    return current


def _lock_claims_native_acceptance(value: object) -> bool:
    if isinstance(value, dict):
        for key, nested in value.items():
            if (key == "status" and nested == "migration-accepted") or (key == "nativeVerified" and nested is True):
                return True
            if _lock_claims_native_acceptance(nested):
                return True
    elif isinstance(value, list):
        return any(_lock_claims_native_acceptance(item) for item in value)
    return False


def require_native_acceptance_for_promoted_locks(root: Path) -> None:
    """Require retained signed evidence whenever a profile lock claims native acceptance."""
    root = root.resolve()
    lock_paths = (
        ("tools/minecraft-26.3/profile.lock.json", "Minecraft 26.3 profile lock"),
        ("tools/minecraft-26.3/server-plugins.lock.json", "Minecraft 26.3 server plugin lock"),
        ("tools/minecraft-26.3/client-mods.lock.json", "Minecraft 26.3 client mods lock"),
    )
    promoted = False
    for relative, description in lock_paths:
        path = _relative_file(root, relative, description)
        if _lock_claims_native_acceptance(_read_json(path, description)):
            promoted = True

    if not promoted:
        return

    required_evidence = (
        (EVIDENCE_DIRECTORY + "/acceptance.json", "native acceptance record"),
        (ACCEPTANCE_SIGNATURE_PATH, "native acceptance detached signature"),
    )
    missing: list[str] = []
    for relative, description in required_evidence:
        try:
            _relative_file(root, relative, description)
        except AcceptanceError as exc:
            if "file is missing" not in str(exc):
                raise
            missing.append(description)
    if missing:
        raise AcceptanceError(
            "signed native acceptance evidence is required when a lock claims migration acceptance or native verification; "
            "missing " + " and ".join(missing)
        )


def _evidence_file(
    root: Path,
    entry: object,
    description: str,
    suffix: str | None = None,
    referenced_files: set[str] | None = None,
    verified_digests: dict[str, str] | None = None,
) -> tuple[Path, str]:
    if not isinstance(entry, dict):
        raise AcceptanceError(f"{description} must include a path and SHA-256")
    relative = entry.get("path")
    path = _relative_file(root, relative, description, prefix=EVIDENCE_DIRECTORY + "/")
    expected = entry.get("sha256")
    if not isinstance(expected, str) or not SHA256_RE.fullmatch(expected):
        raise AcceptanceError(f"{description} SHA-256 must be 64 lowercase hex characters")
    if suffix and not path.name.lower().endswith(suffix):
        raise AcceptanceError(f"{description} must use the {suffix} file format")
    actual = _sha_file(path)
    if actual != expected:
        raise AcceptanceError(f"{description} SHA-256 mismatch: expected {expected}, got {actual}")
    if referenced_files is not None:
        referenced_files.add(relative)
    if verified_digests is not None:
        verified_digests[relative] = actual
    return path, actual


def _verify_closed_evidence_tree(
    root: Path,
    referenced_files: set[str],
    *,
    allow_missing_signature: bool = False,
) -> None:
    evidence_root = root / EVIDENCE_DIRECTORY
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    try:
        root_info = evidence_root.lstat()
    except OSError as exc:
        raise AcceptanceError(f"Cannot inspect native acceptance evidence directory: {exc}") from exc
    if (
        stat.S_ISLNK(root_info.st_mode)
        or getattr(root_info, "st_file_attributes", 0) & reparse_flag
        or not stat.S_ISDIR(root_info.st_mode)
    ):
        raise AcceptanceError("native acceptance evidence directory must be a regular directory")

    expected_files = set(referenced_files)
    expected_files.update((EVIDENCE_DIRECTORY + "/acceptance.json", ACCEPTANCE_SIGNATURE_PATH))
    signature_path = root / ACCEPTANCE_SIGNATURE_PATH
    if allow_missing_signature and not signature_path.exists():
        expected_files.discard(ACCEPTANCE_SIGNATURE_PATH)
    evidence_parts = PurePosixPath(EVIDENCE_DIRECTORY).parts
    expected_directories: set[str] = set()
    for relative in expected_files:
        parts = PurePosixPath(relative).parts
        for depth in range(len(evidence_parts) + 1, len(parts)):
            expected_directories.add("/".join(parts[:depth]))

    actual_files: set[str] = set()

    def raise_walk_error(error: OSError) -> None:
        raise AcceptanceError(f"Cannot enumerate native acceptance evidence: {error}") from error

    for current, directories, filenames in os.walk(evidence_root, topdown=True, followlinks=False, onerror=raise_walk_error):
        current_path = Path(current)
        for name in directories:
            path = current_path / name
            relative = path.relative_to(root).as_posix()
            try:
                info = path.lstat()
            except OSError as exc:
                raise AcceptanceError(f"Cannot inspect native acceptance path: {relative}") from exc
            if (
                stat.S_ISLNK(info.st_mode)
                or getattr(info, "st_file_attributes", 0) & reparse_flag
                or not stat.S_ISDIR(info.st_mode)
            ):
                raise AcceptanceError(f"native acceptance path must not contain links or non-directories: {relative}")
            if relative not in expected_directories:
                raise AcceptanceError(f"unreferenced native acceptance directory: {relative}")

        for name in filenames:
            path = current_path / name
            relative = path.relative_to(root).as_posix()
            try:
                info = path.lstat()
            except OSError as exc:
                raise AcceptanceError(f"Cannot inspect native acceptance path: {relative}") from exc
            if (
                stat.S_ISLNK(info.st_mode)
                or getattr(info, "st_file_attributes", 0) & reparse_flag
                or not stat.S_ISREG(info.st_mode)
            ):
                raise AcceptanceError(f"native acceptance path must be a regular file: {relative}")
            if relative not in expected_files:
                raise AcceptanceError(f"unreferenced native acceptance file: {relative}")
            actual_files.add(relative)

    missing = expected_files - actual_files
    if missing:
        raise AcceptanceError(f"native acceptance evidence is missing referenced files: {sorted(missing)}")


def _write_verified_snapshot(root: Path, output: Path, verified_digests: dict[str, str]) -> None:
    root = root.resolve()
    output = output if output.is_absolute() else root / output
    try:
        output = output.absolute()
        relative_output = output.relative_to(root)
    except (OSError, ValueError) as exc:
        raise AcceptanceError("verified snapshot output must stay inside the repository") from exc
    evidence_parts = PurePosixPath(EVIDENCE_DIRECTORY).parts
    if (
        not relative_output.parts
        or ".." in relative_output.parts
        or relative_output.parts[:len(evidence_parts)] == evidence_parts
    ):
        raise AcceptanceError("verified snapshot output must be outside the native acceptance evidence directory")
    current = root
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    for part in relative_output.parts[:-1]:
        current = current / part
        try:
            info = current.lstat()
        except FileNotFoundError:
            current.mkdir()
            info = current.lstat()
        except OSError as exc:
            raise AcceptanceError(f"Cannot inspect verified snapshot output directory: {exc}") from exc
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag or not stat.S_ISDIR(info.st_mode):
            raise AcceptanceError("verified snapshot output path must not contain links or non-directories")
    output = current / relative_output.parts[-1]
    if output.exists() or output.is_symlink():
        raise AcceptanceError("verified snapshot output already exists")

    expected_paths = {EVIDENCE_DIRECTORY + "/acceptance.json", ACCEPTANCE_SIGNATURE_PATH}
    expected_paths.update(verified_digests)
    if set(verified_digests) != expected_paths:
        raise AcceptanceError("verified snapshot requires the accepted record, signature, and every validated evidence file")

    temporary = output.with_name(output.name + ".tmp")
    if temporary.exists() or temporary.is_symlink():
        raise AcceptanceError("verified snapshot temporary output already exists")
    try:
        with zipfile.ZipFile(temporary, "x", compression=zipfile.ZIP_STORED) as archive:
            for relative in sorted(expected_paths):
                source_path = _relative_file(root, relative, "verified snapshot evidence file", prefix=EVIDENCE_DIRECTORY + "/")
                digest = hashlib.sha256()
                try:
                    with source_path.open("rb") as source, archive.open(relative, "w") as destination:
                        for chunk in iter(lambda: source.read(1024 * 1024), b""):
                            digest.update(chunk)
                            destination.write(chunk)
                except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
                    raise AcceptanceError(f"Cannot build verified native acceptance snapshot: {exc}") from exc
                if digest.hexdigest() != verified_digests[relative]:
                    raise AcceptanceError(f"native acceptance evidence changed after native acceptance validation: {relative}")
        os.link(temporary, output)
    except OSError as exc:
        raise AcceptanceError(f"Cannot publish verified native acceptance snapshot: {exc}") from exc
    finally:
        temporary.unlink(missing_ok=True)


def _verify_locked_artifact(root: Path, entry: object, description: str, *, relative: str, digest: str, algorithm: str) -> None:
    if not isinstance(entry, dict):
        raise AcceptanceError(f"candidateArtifacts.{description} must include a path and digest")
    if entry.get("path") != relative:
        raise AcceptanceError(f"candidateArtifacts.{description}.path must be {relative}")
    key = "sha512" if algorithm == "sha512" else "sha256"
    if entry.get(key) != digest:
        raise AcceptanceError(f"candidateArtifacts.{description}.{key} does not match its profile lock")
    path = _relative_file(root, relative, f"candidate artifact {description}", prefix="build/minecraft-26.3/")
    if _sha_file(path, algorithm) != digest:
        raise AcceptanceError(f"candidate artifact {description} bytes do not match the profile lock")


def _verify_client_mod_inventory(
    root: Path,
    entry: object,
    client_mods: dict,
    profile: dict,
    referenced_files: set[str] | None = None,
    verified_digests: dict[str, str] | None = None,
) -> None:
    inventory_path, _ = _evidence_file(
        root, entry, "client mod inventory", ".json", referenced_files, verified_digests
    )
    inventory = _read_json(inventory_path, "client mod inventory")
    if inventory.get("schemaVersion") != 1 or inventory.get("minecraftVersion") != "26.3":
        raise AcceptanceError("client mod inventory must use schemaVersion 1 for Minecraft 26.3")
    if not isinstance(inventory.get("profile"), str) or not inventory["profile"].strip():
        raise AcceptanceError("client mod inventory must identify the active launcher profile")

    expected: dict[str, tuple[str, int]] = {}
    client_artifact = profile.get("clientArtifact")
    if not isinstance(client_artifact, dict):
        raise AcceptanceError("client profile lock must include the CopiMineClient artifact")
    client_filename = client_artifact.get("filename")
    client_sha512 = client_artifact.get("sha512")
    client_size = client_artifact.get("size")
    if (
        not isinstance(client_filename, str) or PurePosixPath(client_filename).name != client_filename
        or any(char in client_filename for char in "\\/:")
        or not isinstance(client_sha512, str) or not SHA512_RE.fullmatch(client_sha512)
        or not isinstance(client_size, int) or not 0 < client_size < 100_000_000
    ):
        raise AcceptanceError("CopiMineClient artifact lock is invalid for the client mod inventory")
    client_relative = f"build/minecraft-26.3/client/libs/{client_filename}"
    client_candidate = _relative_file(root, client_relative, "staged CopiMineClient artifact", prefix="build/minecraft-26.3/")
    if client_candidate.stat().st_size != client_size or _sha_file(client_candidate, "sha512") != client_sha512:
        raise AcceptanceError("staged CopiMineClient bytes do not match the locked client profile")
    expected[client_filename] = (client_sha512, client_size)

    modules = client_mods.get("modules")
    if not isinstance(modules, list):
        raise AcceptanceError("client mod lock modules must be a list")
    for module in modules:
        if not isinstance(module, dict):
            raise AcceptanceError("client mod lock entries must be objects")
        filename, sha512, size = module.get("filename"), module.get("sha512"), module.get("size")
        if (
            not isinstance(filename, str) or not filename or PurePosixPath(filename).name != filename
            or any(char in filename for char in "\\/:")
            or not isinstance(sha512, str) or not SHA512_RE.fullmatch(sha512)
            or not isinstance(size, int) or not 0 < size < 100_000_000
            or filename in expected
        ):
            raise AcceptanceError("client mod lock entry is invalid or duplicates a mod filename")
        relative = f"build/minecraft-26.3/client-mods/{filename}"
        candidate = _relative_file(root, relative, f"staged client mod {filename}", prefix="build/minecraft-26.3/")
        if candidate.stat().st_size != size or _sha_file(candidate, "sha512") != sha512:
            raise AcceptanceError(f"staged client mod bytes do not match lock: {filename}")
        expected[filename] = (sha512, size)

    rows = inventory.get("mods")
    if not isinstance(rows, list) or not rows:
        raise AcceptanceError("client mod inventory must contain the active mod JARs")
    observed: dict[str, tuple[str, int]] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise AcceptanceError("client mod inventory rows must be objects")
        filename, sha512, size = row.get("filename"), row.get("sha512"), row.get("size")
        if (
            not isinstance(filename, str) or not filename or PurePosixPath(filename).name != filename
            or any(char in filename for char in "\\/:")
            or not isinstance(sha512, str) or not SHA512_RE.fullmatch(sha512)
            or not isinstance(size, int) or not 0 < size < 100_000_000
            or filename in observed
        ):
            raise AcceptanceError("client mod inventory row is invalid or duplicated")
        observed[filename] = (sha512, size)
    if set(observed) != set(expected):
        raise AcceptanceError("client mod inventory filenames differ from the locked client profile")
    for filename, expected_digest in expected.items():
        if observed[filename] != expected_digest:
            raise AcceptanceError(f"client mod inventory does not match locked JAR: {filename}")


def _expected_plugins(lock: dict, *, require_native_verified: bool = True) -> dict[str, dict[str, object]]:
    modules = lock.get("modules")
    baseline = lock.get("unchangedBaseline", [])
    if not isinstance(modules, list) or not modules:
        raise AcceptanceError("server plugin lock must contain modules")
    if not isinstance(baseline, list):
        raise AcceptanceError("server plugin lock unchangedBaseline must be a list")

    module_identity_candidates = {
        module.get("pluginName") for module in modules
        if isinstance(module, dict) and isinstance(module.get("pluginName"), str) and module.get("pluginName")
    }
    expected: dict[str, dict[str, str]] = {}
    baseline_names: set[str] = set()
    for item in baseline:
        if not isinstance(item, dict):
            raise AcceptanceError("every unchangedBaseline entry must be an object")
        name = item.get("pluginName")
        if not isinstance(name, str) or not name:
            raise AcceptanceError("every unchangedBaseline entry must have a pluginName")
        if name in baseline_names:
            raise AcceptanceError(f"server plugin lock contains duplicate baseline pluginName entries: {name}")
        baseline_names.add(name)
        if name in module_identity_candidates:
            # A module replaces this older identity; only the module's runtime candidate is authoritative.
            continue
        candidate = item.get("candidate")
        is_replacement_candidate = (
            isinstance(candidate, dict)
            and (candidate.get("buildArtifact") or candidate.get("url"))
        )
        selected = candidate if is_replacement_candidate else item
        if require_native_verified and selected.get("nativeVerified") is not True:
            raise AcceptanceError(f"installed baseline plugin is not marked nativeVerified=true: {name}")
        filename = selected.get("runtimeFilename") or selected.get("filename") or item.get("filename")
        digest = selected.get("sha256")
        if (
            not isinstance(filename, str) or not filename
            or PurePosixPath(filename).name != filename or any(char in filename for char in "\\/:")
            or not isinstance(digest, str) or not SHA256_RE.fullmatch(digest)
        ):
            raise AcceptanceError(f"installed baseline plugin lock is incomplete: {name}")
        if name in expected:
            raise AcceptanceError(f"server plugin lock contains duplicate pluginName entries: {name}")
        expected[name] = {"filename": filename, "sha256": digest, "source": "baseline", "candidate": selected}

    module_names: set[str] = set()
    for module in modules:
        if not isinstance(module, dict) or (
            require_native_verified and module.get("nativeVerified") is not True
        ):
            raise AcceptanceError("every installed server module must be marked nativeVerified=true")
        name = module.get("pluginName")
        filename = module.get("runtimeFilename") or module.get("filename")
        digest = module.get("buildSha256") if module.get("buildScript") else module.get("sha256")
        sha512 = module.get("buildSha512") if module.get("buildScript") else module.get("sha512")
        if (
            not isinstance(name, str) or not name
            or not isinstance(filename, str) or not filename
            or PurePosixPath(filename).name != filename or any(char in filename for char in "\\/:")
        ):
            raise AcceptanceError("every server module must have a pluginName and runtime filename")
        if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest):
            raise AcceptanceError(f"server module SHA-256 is invalid: {name}")
        if name in module_names:
            raise AcceptanceError(f"server plugin lock contains duplicate module pluginName entries: {name}")
        module_names.add(name)
        expected[name] = {
            "filename": filename, "sha256": digest, "sha512": sha512,
            "source": "module", "candidate": module,
        }
    filenames = [str(row["filename"]) for row in expected.values()]
    if len(filenames) != len(set(filenames)):
        raise AcceptanceError("server plugin lock contains duplicate runtime filenames")
    return expected


def _replacement_plugin_names(lock: dict) -> set[str]:
    module_names = {module["pluginName"] for module in lock["modules"]}
    names = set(module_names)
    for baseline in lock.get("unchangedBaseline", []):
        name = baseline.get("pluginName")
        if name in module_names:
            # The installer treats the migration module as authoritative for
            # an identity it replaces; the older baseline candidate is unused.
            continue
        candidate = baseline.get("candidate")
        if not isinstance(candidate, dict):
            continue
        if not candidate.get("buildArtifact") and not candidate.get("url"):
            continue
        if not isinstance(name, str) or not name:
            raise AcceptanceError("every replacement baseline candidate must have a pluginName")
        if name in names:
            raise AcceptanceError(f"server plugin lock contains duplicate replacement pluginName entries: {name}")
        names.add(name)
    return names


def _verify_plugin_candidates(root: Path, lock: dict) -> None:
    modules = lock["modules"]
    for module in modules:
        filename = module["filename"]
        relative = module.get("buildArtifact") or f"build/minecraft-26.3/server-plugins/{filename}"
        path = _relative_file(root, relative, f"staged server plugin {module['pluginName']}", prefix="build/minecraft-26.3/")
        source_built = bool(module.get("buildScript"))
        sha256 = module.get("buildSha256") if source_built else module.get("sha256")
        sha512 = module.get("buildSha512") if source_built else module.get("sha512")
        size = module.get("buildSize") if source_built else module.get("size")
        if not isinstance(sha256, str) or not SHA256_RE.fullmatch(sha256) or _sha_file(path) != sha256:
            raise AcceptanceError(f"staged server plugin bytes do not match lock: {module['pluginName']}")
        if not isinstance(size, int) or path.stat().st_size != size:
            raise AcceptanceError(f"staged server plugin size does not match lock: {module['pluginName']}")
        if sha512 is not None and (not isinstance(sha512, str) or not SHA512_RE.fullmatch(sha512) or _sha_file(path, "sha512") != sha512):
            raise AcceptanceError(f"staged server plugin SHA-512 does not match lock: {module['pluginName']}")
        if source_built:
            receipt_relative = module.get("buildReceipt")
            receipt_path = _relative_file(root, receipt_relative, f"staged server plugin build receipt {module['pluginName']}", prefix="build/minecraft-26.3/")
            receipt = _read_json(receipt_path, f"staged server plugin build receipt {module['pluginName']}")
            if (
                receipt.get("sourceCommit") != module.get("sourceCommit")
                or receipt.get("sha512") != sha512
                or receipt.get("size") != size
                or receipt.get("nativeVerified") is not False
            ):
                raise AcceptanceError(f"staged server plugin build receipt differs from lock: {module['pluginName']}")

    module_names = {module["pluginName"] for module in modules}
    for item in lock.get("unchangedBaseline", []):
        if item.get("pluginName") in module_names:
            continue
        candidate = item.get("candidate") or {}
        artifact = candidate.get("buildArtifact")
        if artifact:
            relative = artifact
        elif candidate.get("url"):
            filename = candidate.get("filename") or item.get("filename")
            if (
                not isinstance(filename, str) or not filename
                or PurePosixPath(filename).name != filename or any(char in filename for char in "\\/:")
            ):
                raise AcceptanceError(f"staged baseline candidate filename is invalid: {item['pluginName']}")
            size = candidate.get("size")
            sha512 = candidate.get("sha512")
            if not isinstance(size, int) or not 0 < size < 100_000_000:
                raise AcceptanceError(f"staged baseline candidate size is invalid: {item['pluginName']}")
            if not isinstance(sha512, str) or not SHA512_RE.fullmatch(sha512):
                raise AcceptanceError(f"staged baseline candidate SHA-512 is invalid: {item['pluginName']}")
            relative = f"build/minecraft-26.3/server-plugins/{filename}"
        else:
            continue
        path = _relative_file(root, relative, f"staged baseline candidate {item['pluginName']}", prefix="build/minecraft-26.3/")
        if _sha_file(path) != candidate.get("sha256"):
            raise AcceptanceError(f"staged baseline candidate bytes do not match lock: {item['pluginName']}")
        if "size" in candidate and path.stat().st_size != candidate["size"]:
            raise AcceptanceError(f"staged baseline candidate size does not match lock: {item['pluginName']}")
        if "sha512" in candidate and _sha_file(path, "sha512") != candidate["sha512"]:
            raise AcceptanceError(f"staged baseline candidate SHA-512 does not match lock: {item['pluginName']}")


def _read_plugin_rows(value: object, description: str) -> dict[str, dict[str, str]]:
    if isinstance(value, dict):
        rows = value.get("plugins")
    else:
        rows = value
    if not isinstance(rows, list):
        raise AcceptanceError(f"{description} must include a plugins list")
    observed: dict[str, dict[str, str]] = {}
    filenames: set[str] = set()
    for row in rows:
        if not isinstance(row, dict):
            raise AcceptanceError(f"every {description} row must be an object")
        name, filename, digest = row.get("pluginName"), row.get("filename"), row.get("sha256")
        if not isinstance(name, str) or not name or not isinstance(filename, str) or not filename:
            raise AcceptanceError(f"{description} rows require pluginName and filename")
        if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest):
            raise AcceptanceError(f"{description} SHA-256 is invalid: {name}")
        if name in observed or filename in filenames:
            raise AcceptanceError(f"{description} contains duplicate plugin names or filenames")
        observed[name] = {"filename": filename, "sha256": digest}
        filenames.add(filename)
    return observed


def _verify_plugin_snapshot(root: Path, entry: object, observed: dict[str, dict[str, str]],
                           expected: dict[str, dict[str, object]],
                           referenced_files: set[str] | None = None,
                           verified_digests: dict[str, str] | None = None) -> None:
    snapshot_path, _ = _evidence_file(
        root, entry, "installed plugin byte snapshot", ".zip", referenced_files, verified_digests
    )
    if snapshot_path.stat().st_size > 512 * 1024 * 1024:
        raise AcceptanceError("installed plugin byte snapshot exceeds the 512 MiB safety limit")
    try:
        with zipfile.ZipFile(snapshot_path) as archive:
            infos = [info for info in archive.infolist() if not info.is_dir()]
            expected_filenames = {row["filename"] for row in expected.values()}
            if len(infos) != len(expected_filenames) or {info.filename for info in infos} != expected_filenames:
                raise AcceptanceError("installed plugin byte snapshot filenames differ from the locked inventory")
            total_uncompressed = 0
            for info in infos:
                if info.filename != PurePosixPath(info.filename).name or not info.filename.lower().endswith(".jar"):
                    raise AcceptanceError("installed plugin byte snapshot contains an unsafe archive path")
                if info.external_attr >> 16 & 0o170000 == 0o120000:
                    raise AcceptanceError("installed plugin byte snapshot contains a symbolic link")
                if info.file_size > 100 * 1024 * 1024:
                    raise AcceptanceError("an installed plugin JAR in the byte snapshot exceeds 100 MiB")
                total_uncompressed += info.file_size
                if total_uncompressed > 512 * 1024 * 1024:
                    raise AcceptanceError("installed plugin byte snapshot exceeds the 512 MiB expanded safety limit")
                name = next(plugin for plugin, row in expected.items() if row["filename"] == info.filename)
                digest = hashlib.sha256()
                with archive.open(info) as source:
                    for chunk in iter(lambda: source.read(1024 * 1024), b""):
                        digest.update(chunk)
                if digest.hexdigest() != expected[name]["sha256"] or digest.hexdigest() != observed[name]["sha256"]:
                    raise AcceptanceError(f"installed plugin JAR bytes differ from lock and inventory: {name}")
    except (OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as exc:
        raise AcceptanceError(f"Cannot read installed plugin byte snapshot: {exc}") from exc


def _runtime_plugin_directory(root: Path) -> Path | None:
    current = root
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    for part in PurePosixPath("local-runtime/end-rift-server-26.3/plugins").parts:
        current = current / part
        try:
            info = current.lstat()
        except FileNotFoundError:
            return None
        except OSError as exc:
            raise AcceptanceError(f"Cannot inspect isolated runtime plugin directory: {exc}") from exc
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag:
            raise AcceptanceError("isolated runtime plugin directory must not contain symlinks or reparse points")
        if current != root / "local-runtime/end-rift-server-26.3/plugins" and not stat.S_ISDIR(info.st_mode):
            raise AcceptanceError("isolated runtime plugin path contains a non-directory component")
    if not stat.S_ISDIR(current.lstat().st_mode):
        raise AcceptanceError("isolated runtime plugin path is not a directory")
    return current


def _verify_runtime_plugin_files(root: Path, observed: dict[str, dict[str, str]], expected: dict[str, dict[str, object]]) -> None:
    directory = _runtime_plugin_directory(root)
    if directory is None:
        return
    active_files = list(directory.glob("*.jar"))
    if {path.name for path in active_files} != {row["filename"] for row in observed.values()}:
        raise AcceptanceError("actual isolated runtime plugin JAR filenames differ from the installed inventory")
    for name, locked in expected.items():
        path = directory / str(locked["filename"])
        try:
            info = path.lstat()
        except OSError as exc:
            raise AcceptanceError(f"actual isolated runtime plugin JAR is missing: {name}") from exc
        reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & reparse_flag or not stat.S_ISREG(info.st_mode):
            raise AcceptanceError(f"actual isolated runtime plugin JAR is not a regular file: {name}")
        actual = _sha_file(path)
        if actual != locked["sha256"] or actual != observed[name]["sha256"]:
            raise AcceptanceError(f"actual isolated runtime plugin bytes differ from lock and receipt: {name}")


def _verify_inventory(root: Path, entry: object, receipt_entry: object, snapshot_entry: object,
                      expected: dict[str, dict[str, object]], replacement_names: set[str],
                      referenced_files: set[str] | None = None,
                      verified_digests: dict[str, str] | None = None) -> set[str]:
    path, _ = _evidence_file(
        root, entry, "server plugin inventory", ".json", referenced_files, verified_digests
    )
    inventory = _read_json(path, "server plugin inventory")
    observed = _read_plugin_rows(inventory, "server plugin inventory")
    if set(observed) != set(expected):
        missing = sorted(set(expected) - set(observed))
        extra = sorted(set(observed) - set(expected))
        raise AcceptanceError(f"installed server plugin inventory differs from lock (missing={missing}, extra={extra})")
    for name, locked in expected.items():
        actual = observed[name]
        if actual["filename"] != locked["filename"] or actual["sha256"] != locked["sha256"]:
            raise AcceptanceError(f"installed server plugin bytes differ from the lock: {name}")

    receipt_path, _ = _evidence_file(
        root, receipt_entry, "server plugin installer receipt", ".json", referenced_files, verified_digests
    )
    receipt = _read_json(receipt_path, "server plugin installer receipt")
    if (
        receipt.get("schemaVersion") != 1
        or receipt.get("minecraftVersion") != "26.3"
        or receipt.get("nativeVerified") is not False
    ):
        raise AcceptanceError("server plugin installer receipt must be for Minecraft 26.3 and remain a byte-install receipt")
    receipt_inventory = _read_plugin_rows(receipt.get("pluginInventory"), "installer receipt plugin inventory")
    if receipt_inventory != observed:
        raise AcceptanceError("installed server plugin inventory differs from the installer receipt")

    installed_replacements = _read_plugin_rows(receipt.get("installed"), "installer receipt replacement inventory")
    if set(installed_replacements) != replacement_names:
        raise AcceptanceError("installer receipt replacement inventory differs from locked replacement candidates")
    for name in replacement_names:
        if installed_replacements[name] != observed[name]:
            raise AcceptanceError(f"installer receipt replacement bytes differ from installed inventory: {name}")
    _verify_plugin_snapshot(root, snapshot_entry, observed, expected, referenced_files, verified_digests)
    _verify_runtime_plugin_files(root, observed, expected)
    return set(observed)


def _validate_png(path: Path, description: str) -> None:
    try:
        with path.open("rb") as source:
            data = source.read(MAX_PNG_BYTES + 1)
    except OSError as exc:
        raise AcceptanceError(f"Cannot read {description}: {exc}") from exc
    if len(data) > MAX_PNG_BYTES or len(data) < 45 or not data.startswith(PNG_SIGNATURE):
        raise AcceptanceError(f"{description} is not a valid, bounded PNG")
    offset = len(PNG_SIGNATURE)
    width = height = color_type = bit_depth = interlace = None
    image_data: list[bytes] = []
    saw_iend = False
    saw_idat_end = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise AcceptanceError(f"{description} has a truncated PNG chunk")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4 : offset + 8]
        end = offset + 12 + length
        if end > len(data):
            raise AcceptanceError(f"{description} has a truncated PNG chunk")
        chunk = data[offset + 4 : offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(chunk) & 0xFFFFFFFF != expected_crc:
            raise AcceptanceError(f"{description} has a PNG CRC mismatch")
        payload = data[offset + 8 : offset + 8 + length]
        if offset == len(PNG_SIGNATURE) and kind != b"IHDR":
            raise AcceptanceError(f"{description} does not begin with IHDR")
        if kind == b"IHDR":
            if width is not None or length != 13:
                raise AcceptanceError(f"{description} has an invalid IHDR")
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            if not (640 <= width <= 8192 and 360 <= height <= 8192 and bit_depth == 8 and color_type in (2, 6)
                    and compression == 0 and filtering == 0 and interlace == 0):
                raise AcceptanceError(f"{description} dimensions or pixel format are not a Minecraft screenshot")
        elif kind == b"IDAT":
            if saw_idat_end or width is None:
                raise AcceptanceError(f"{description} has non-contiguous or misplaced IDAT data")
            image_data.append(payload)
        elif image_data:
            saw_idat_end = True
        if kind == b"IEND":
            if length != 0 or end != len(data):
                raise AcceptanceError(f"{description} has invalid data after IEND")
            saw_iend = True
            break
        offset = end
    if not saw_iend or width is None or not image_data:
        raise AcceptanceError(f"{description} is missing required PNG chunks")
    expected_size = (width * (3 if color_type == 2 else 4) + 1) * height
    if expected_size > MAX_PNG_BYTES:
        raise AcceptanceError(f"{description} decompressed pixel data exceeds the safety limit")
    decompressor = zlib.decompressobj()
    try:
        raw = decompressor.decompress(b"".join(image_data), expected_size + 1)
    except zlib.error as exc:
        raise AcceptanceError(f"{description} contains invalid compressed image data") from exc
    if len(raw) != expected_size or not decompressor.eof or decompressor.unused_data or decompressor.unconsumed_tail:
        raise AcceptanceError(f"{description} contains incomplete or oversized image data")
    stride = width * (3 if color_type == 2 else 4) + 1
    if any(raw[row * stride] > 4 for row in range(height)):
        raise AcceptanceError(f"{description} contains an invalid PNG scanline filter")


def _verify_logs(
    root: Path,
    server: dict,
    client: dict,
    expected_plugins: set[str],
    pack_filename: str,
    referenced_files: set[str] | None = None,
    verified_digests: dict[str, str] | None = None,
) -> None:
    server_path, _ = _evidence_file(
        root, server.get("log"), "server log", ".log", referenced_files, verified_digests
    )
    if server_path.stat().st_size > 100 * 1024 * 1024:
        raise AcceptanceError("server log exceeds the 100 MiB safety limit")
    server_text = server_path.read_text(encoding="utf-8", errors="replace")
    server_records = [
        match.group("message").strip()
        for match in re.finditer(
            r"(?m)^\[\d{2}:\d{2}:\d{2}\] \[Server thread/INFO\]: (?P<message>.*)$",
            server_text,
        )
    ]
    start_pattern = re.compile(r"Starting minecraft server version\s+26\.3\b", flags=re.IGNORECASE)
    ready_pattern = re.compile(r'Done\s*\([^\r\n)]*\)!\s*(?:For help, type "help")?', flags=re.IGNORECASE)
    start_matches = [
        index for index, message in enumerate(server_records)
        if start_pattern.fullmatch(message)
    ]
    successful_starts: list[list[str]] = []
    for index, start_index in enumerate(start_matches):
        next_start = start_matches[index + 1] if index + 1 < len(start_matches) else len(server_records)
        run = server_records[start_index:next_start]
        ready_index = next((position for position, message in enumerate(run) if ready_pattern.fullmatch(message)), None)
        if ready_index is not None:
            successful_starts.append(run[:ready_index + 1])
    if len(successful_starts) < 2:
        raise AcceptanceError("server log must show two successful Minecraft 26.3 starts for restart recovery")
    for start_number, start_log in enumerate(successful_starts, start=1):
        missing_plugins = sorted(
            name for name in expected_plugins
            if not any(
                re.fullmatch(
                    r"\[" + re.escape(name) + r"\]\s+Enabling\s+" + re.escape(name) + r"\s+v\S+",
                    message,
                    flags=re.IGNORECASE,
                )
                for message in start_log
            )
        )
        if missing_plugins:
            missing = ", ".join(missing_plugins)
            raise AcceptanceError(
                "server log must show every locked plugin enabled on each successful Minecraft 26.3 server start; "
                f"start {start_number} is missing: {missing}"
            )

    logs = client.get("logs")
    if not isinstance(logs, dict):
        raise AcceptanceError("client logs must include both the mod log and Minecraft latest.log")
    mod_path, _ = _evidence_file(
        root, logs.get("mod"), "client mod log", ".log", referenced_files, verified_digests
    )
    minecraft_path, _ = _evidence_file(
        root, logs.get("minecraft"), "client Minecraft log", ".log", referenced_files, verified_digests
    )
    mod_text = mod_path.read_text(encoding="utf-8", errors="replace")
    minecraft_text = minecraft_path.read_text(encoding="utf-8", errors="replace")
    for marker in ("CopiMineClient bootstrap finished", "Bridge hello sent:"):
        if marker not in mod_text:
            raise AcceptanceError(f"client mod log is missing runtime marker: {marker}")
    hello_sessions = [
        (match.group("session"), match.start())
        for match in re.finditer(
            r"Bridge hello sent: session=(?P<session>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\b",
            mod_text,
            flags=re.IGNORECASE,
        )
    ]
    ack_sessions = [
        (match.group("session"), match.start())
        for match in re.finditer(
            r"Bridge handshake acknowledged: protocol=2, session=(?P<session>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\b",
            mod_text,
            flags=re.IGNORECASE,
        )
    ]
    latest_hello_session, latest_hello_position = max(hello_sessions, key=lambda item: item[1]) if hello_sessions else (None, -1)
    if latest_hello_session is None or not any(
        latest_hello_session.lower() == ack_session.lower() and latest_hello_position < ack_position
        for ack_session, ack_position in ack_sessions
    ):
        raise AcceptanceError("client mod log must show a server acknowledgement for the current hello session")
    if not re.search(r"Loading Minecraft 26\.3 with Fabric Loader\s+" + re.escape(str(client.get("fabricLoader"))), minecraft_text):
        raise AcceptanceError("client Minecraft log does not show the pinned Fabric Loader on Minecraft 26.3")
    if "Reloading ResourceManager:" not in minecraft_text or pack_filename not in minecraft_text:
        raise AcceptanceError("client Minecraft log does not show the migrated resource pack applied")
    if "Shader activated through Iris:" not in mod_text and "Fallback post-process activated:" not in mod_text:
        raise AcceptanceError("client mod log does not show a working shader or post-process runtime")


def validate(
    root: Path,
    evidence_path: Path,
    trusted_public_key_hex: str | None = None,
    *,
    detached_signature: bytes | None = None,
    expected_source_commit: str | None = None,
    verified_output: Path | None = None,
) -> None:
    root = root.resolve()
    expected_record = root / EVIDENCE_DIRECTORY / "acceptance.json"
    try:
        actual_record = evidence_path.resolve(strict=True)
    except OSError as exc:
        raise AcceptanceError(f"Cannot read native acceptance record: {exc}") from exc
    if actual_record != expected_record:
        raise AcceptanceError(f"native acceptance record must be {EVIDENCE_DIRECTORY}/acceptance.json")
    referenced_files = {
        EVIDENCE_DIRECTORY + "/acceptance.json",
        ACCEPTANCE_SIGNATURE_PATH,
    }
    verified_digests: dict[str, str] = {}
    evidence_path = _relative_file(root, EVIDENCE_DIRECTORY + "/acceptance.json", "native acceptance record")
    evidence = _read_signed_acceptance(
        root,
        evidence_path,
        trusted_public_key_hex,
        detached_signature=detached_signature,
        verified_digests=verified_digests,
    )
    profile_path = _relative_file(root, "tools/minecraft-26.3/profile.lock.json", "Minecraft 26.3 profile lock")
    plugins_path = _relative_file(root, "tools/minecraft-26.3/server-plugins.lock.json", "Minecraft 26.3 server plugin lock")
    client_mods_path = _relative_file(root, "tools/minecraft-26.3/client-mods.lock.json", "Minecraft 26.3 client mods lock")
    profile = _read_json(profile_path, "Minecraft 26.3 profile lock")
    plugins = _read_json(plugins_path, "Minecraft 26.3 server plugin lock")
    client_mods = _read_json(client_mods_path, "Minecraft 26.3 client mods lock")

    if evidence.get("schemaVersion") != 4 or evidence.get("nativeVerified") is not True or evidence.get("minecraftVersion") != "26.3":
        raise AcceptanceError("native acceptance record must use schemaVersion 4 and attest to Minecraft 26.3 native verification")
    source_commit = evidence.get("sourceCommit")
    if not isinstance(source_commit, str) or not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise AcceptanceError("native acceptance sourceCommit must be a 40-character lowercase Git SHA")
    if expected_source_commit is not None:
        if not isinstance(expected_source_commit, str) or not re.fullmatch(r"[0-9a-f]{40}", expected_source_commit):
            raise AcceptanceError("expected source commit must be a 40-character lowercase Git SHA")
        if source_commit != expected_source_commit:
            raise AcceptanceError("native acceptance sourceCommit does not match the expected source commit")
    if profile.get("minecraftVersion") != "26.3" or plugins.get("minecraftVersion") != "26.3":
        raise AcceptanceError("locked server and client profiles must both target Minecraft 26.3")
    if client_mods.get("minecraftVersion") != "26.3" or client_mods.get("nativeVerified") is not True:
        raise AcceptanceError("Minecraft 26.3 client mods lock must be natively verified")
    if profile.get("status") != "migration-accepted" or plugins.get("status") != "migration-accepted":
        raise AcceptanceError("profile and server plugin lock statuses must be migration-accepted")
    if plugins.get("nativeVerified") is not True:
        raise AcceptanceError("server plugin lock nativeVerified must be true after native verification")
    if evidence.get("profileLockSha256") != _sha_file(profile_path):
        raise AcceptanceError("profileLockSha256 does not match the repository lock")
    if evidence.get("serverPluginLockSha256") != _sha_file(plugins_path):
        raise AcceptanceError("serverPluginLockSha256 does not match the repository lock")
    if evidence.get("clientModsLockSha256") != _sha_file(client_mods_path):
        raise AcceptanceError("clientModsLockSha256 does not match the repository lock")

    paper = profile.get("paper", {})
    client_artifact = profile.get("clientArtifact", {})
    pack = profile.get("resourcePackMigration", {})
    artifacts = evidence.get("candidateArtifacts")
    if not isinstance(artifacts, dict):
        raise AcceptanceError("candidateArtifacts must bind the tested runtime to files built from the profile locks")
    _verify_locked_artifact(
        root, artifacts.get("paper"), "paper",
        relative=f"build/minecraft-26.3/server/paper-26.3-{paper.get('build')}.jar",
        digest=paper.get("sha256"), algorithm="sha256",
    )
    _verify_locked_artifact(
        root, artifacts.get("client"), "client",
        relative=f"build/minecraft-26.3/client/libs/{client_artifact.get('filename')}",
        digest=client_artifact.get("sha512"), algorithm="sha512",
    )
    _verify_locked_artifact(
        root, artifacts.get("resourcePack"), "resourcePack",
        relative="build/minecraft-26.3/CopiMineResourcePack-26.3.zip",
        digest=pack.get("candidateSha256"), algorithm="sha256",
    )

    expected_plugins = _expected_plugins(plugins)
    replacement_plugin_names = _replacement_plugin_names(plugins)
    _verify_plugin_candidates(root, plugins)
    server, client = evidence.get("server"), evidence.get("client")
    if not isinstance(server, dict) or not isinstance(client, dict):
        raise AcceptanceError("native acceptance record must include server and client evidence")
    _verify_client_mod_inventory(
        root, client.get("modInventory"), client_mods, profile, referenced_files, verified_digests
    )
    if server.get("minecraftVersion") != "26.3" or client.get("minecraftVersion") != "26.3":
        raise AcceptanceError("both the server and client must report Minecraft 26.3")
    if server.get("paperBuild") != paper.get("build"):
        raise AcceptanceError("server Paper build does not match the pinned Paper candidate")
    if client.get("fabricLoader") != profile.get("fabric", {}).get("loader"):
        raise AcceptanceError("client Fabric Loader does not match the pinned client profile")
    if not isinstance(server.get("checks"), dict) or not isinstance(client.get("checks"), dict):
        raise AcceptanceError("server and client checks must be objects")
    for name in SERVER_CHECKS:
        if server["checks"].get(name) is not True:
            raise AcceptanceError(f"server native check is incomplete: {name}")
    for name in CLIENT_CHECKS:
        if client["checks"].get(name) is not True:
            raise AcceptanceError(f"client native check is incomplete: {name}")

    observed_plugins = _verify_inventory(
        root, server.get("pluginInventory"), server.get("installerReceipt"),
        server.get("pluginSnapshot"), expected_plugins, replacement_plugin_names,
        referenced_files, verified_digests,
    )
    loaded_plugins = server.get("loadedPlugins")
    if (
        not isinstance(loaded_plugins, list)
        or not all(isinstance(name, str) and name for name in loaded_plugins)
        or len(loaded_plugins) != len(set(loaded_plugins))
        or set(loaded_plugins) != observed_plugins
    ):
        raise AcceptanceError("observed loadedPlugins must match the installed plugin inventory exactly")

    screenshots = client.get("screenshots")
    if not isinstance(screenshots, list) or not screenshots:
        raise AcceptanceError("Minecraft F2 screenshots are required for model and shader runtime checks")
    screenshot_ids: set[str] = set()
    for index, screenshot in enumerate(screenshots):
        if not isinstance(screenshot, dict) or screenshot.get("captureMethod") != "minecraft-f2":
            raise AcceptanceError(f"screenshot {index} must record captureMethod=minecraft-f2")
        screenshot_id = screenshot.get("id")
        if not isinstance(screenshot_id, str) or not screenshot_id or screenshot_id in screenshot_ids:
            raise AcceptanceError(f"screenshot {index} must have a unique id")
        path, _ = _evidence_file(
            root, screenshot, f"client screenshot {index}", ".png", referenced_files, verified_digests
        )
        _validate_png(path, f"client screenshot {index}")
        screenshot_ids.add(screenshot_id)
    visual_checks = client.get("visualChecks")
    if not isinstance(visual_checks, dict):
        raise AcceptanceError("client visualChecks must identify model and shader screenshots")
    for check in ("customModel", "shaderRuntime"):
        visual = visual_checks.get(check)
        screenshot_id = visual.get("screenshotId") if isinstance(visual, dict) else None
        if not isinstance(screenshot_id, str) or screenshot_id not in screenshot_ids:
            raise AcceptanceError(f"client visual check must reference a verified F2 screenshot: {check}")

    loaded_pack = profile.get("resourcePackMigration", {}).get("filename", "CopiMineResourcePack-26.3.zip")
    _verify_logs(root, server, client, observed_plugins, loaded_pack, referenced_files, verified_digests)
    _verify_closed_evidence_tree(
        root,
        referenced_files,
        allow_missing_signature=detached_signature is not None,
    )
    if verified_output is not None:
        _write_verified_snapshot(root, verified_output, verified_digests)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--evidence", type=Path, default=Path(EVIDENCE_DIRECTORY) / "acceptance.json")
    parser.add_argument("--expected-source-commit", type=str)
    parser.add_argument("--verified-output", type=Path, help="write a ZIP containing only the exact validated evidence files")
    parser.add_argument(
        "--require-evidence-for-accepted-locks",
        action="store_true",
        help="fail if any migration lock claims acceptance/native verification without a retained record and signature",
    )
    args = parser.parse_args(argv)
    if args.require_evidence_for_accepted_locks:
        try:
            require_native_acceptance_for_promoted_locks(args.root)
        except AcceptanceError as exc:
            print(f"Native Minecraft 26.3 acceptance is not verified: {exc}", file=sys.stderr)
            return 1
        print("Native acceptance evidence requirements match the current lock states.")
        return 0

    evidence_path = args.evidence if args.evidence.is_absolute() else args.root / args.evidence
    try:
        validate(
            args.root,
            evidence_path,
            expected_source_commit=args.expected_source_commit,
            verified_output=args.verified_output,
        )
    except AcceptanceError as exc:
        print(f"Native Minecraft 26.3 acceptance is not verified: {exc}", file=sys.stderr)
        return 1
    if args.verified_output is None:
        print("Native Minecraft 26.3 evidence, candidate bytes, installed plugins, and runtime logs match the locked profile.")
    else:
        print("Native Minecraft 26.3 evidence passed validation and was retained as an allowlisted ZIP snapshot.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
