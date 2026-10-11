"""The acceptance gate binds native evidence to built and installed Minecraft bytes."""

import base64
import ctypes
import hashlib
import json
import os
import subprocess
import stat
import struct
import sys
import time
import zlib
import zipfile
from pathlib import Path, PurePosixPath
from types import SimpleNamespace

import pytest
import rfc8785
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from scripts.minecraft.validate_native_acceptance import (
    ACCEPTANCE_PUBLIC_KEY_ENV,
    ACCEPTANCE_SIGNATURE_CONTEXT,
    AcceptanceError,
    MAX_JSON_BYTES,
    MAX_PNG_BYTES,
    _expected_plugins,
    _read_signed_acceptance,
    _validate_png,
    validate,
)
from scripts.minecraft import sign_native_acceptance as acceptance_signer
from scripts.minecraft import validate_native_acceptance as acceptance_validator
from scripts.minecraft.sign_native_acceptance import SigningError, generate_key, sign_record


SERVER_CHECKS = ["paperStarted", "pluginsLoaded", "resourcePackDelivered", "bridgeHandshake", "restartRecovery"]
CLIENT_CHECKS = ["fabricStarted", "modLoaded", "resourcePackApplied", "bridgeHandshake", "customModelRendered", "shaderRuntime"]
EVIDENCE = Path("artifacts/minecraft-26.3/native-acceptance")
_TEST_PRIVATE_KEY = Ed25519PrivateKey.generate()
_TEST_PUBLIC_KEY_HEX = _TEST_PRIVATE_KEY.public_key().public_bytes(
    encoding=serialization.Encoding.Raw,
    format=serialization.PublicFormat.Raw,
).hex()


class _ReadSizeProbe:
    def __init__(self, path: Path, source, calls: list[tuple[Path, int]]):
        self._path = path
        self._source = source
        self._calls = calls

    def __enter__(self):
        self._source.__enter__()
        return self

    def __exit__(self, exc_type, exc, traceback):
        return self._source.__exit__(exc_type, exc, traceback)

    def read(self, size: int = -1):
        self._calls.append((self._path, size))
        return self._source.read(size)

    def __getattr__(self, name):
        return getattr(self._source, name)


def _observe_file_read_sizes(monkeypatch, watched_paths: set[Path]) -> list[tuple[Path, int]]:
    original_open = Path.open
    calls: list[tuple[Path, int]] = []

    def observe_open(path: Path, *args, **kwargs):
        source = original_open(path, *args, **kwargs)
        if path in watched_paths:
            return _ReadSizeProbe(path, source, calls)
        return source

    monkeypatch.setattr(Path, "open", observe_open)
    return calls


@pytest.fixture(autouse=True)
def trusted_test_acceptance_key(monkeypatch):
    monkeypatch.setenv(ACCEPTANCE_PUBLIC_KEY_ENV, _TEST_PUBLIC_KEY_HEX)


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = rfc8785.dumps(value)
    path.write_bytes(encoded)
    if path.name == "acceptance.json":
        signature = _TEST_PRIVATE_KEY.sign(ACCEPTANCE_SIGNATURE_CONTEXT + encoded)
        path.with_name("acceptance.sig").write_text(base64.b64encode(signature).decode("ascii") + "\n", encoding="ascii")


def paper_server_startup_log(enabled_plugin_sets: list[list[str]]) -> str:
    lines = []
    second = 1
    for plugin_names in enabled_plugin_sets:
        lines.append(f"[00:00:{second:02d}] [Server thread/INFO]: Starting minecraft server version 26.3")
        second += 1
        for name in plugin_names:
            lines.append(f"[00:00:{second:02d}] [Server thread/INFO]: [{name}] Enabling {name} v1.0")
            second += 1
        lines.append(f"[00:00:{second:02d}] [Server thread/INFO]: Done (1.2s)!")
        second += 1
    return "\n".join(lines) + "\n"


def digest(path: Path, algorithm: str = "sha256") -> str:
    return hashlib.new(algorithm, path.read_bytes()).hexdigest()


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)


def valid_png() -> bytes:
    width, height = 640, 360
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend((x % 256, y % 256, (x + y) % 256))
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(bytes(raw)))
        + png_chunk(b"IEND", b"")
    )


def evidence_entry(path: Path, root: Path) -> dict:
    return {"path": path.relative_to(root).as_posix(), "sha256": digest(path)}


def write_plugin_snapshot(path: Path, files: dict[str, bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for filename, data in sorted(files.items()):
            archive.writestr(filename, data)


def make_fixture(root: Path) -> tuple[Path, dict]:
    artifact_dir = root / "build/minecraft-26.3"
    paper = artifact_dir / "server/paper-26.3-161.jar"
    client = artifact_dir / "client/libs/CopiMineClient-0.1.1+26.3.jar"
    locked_mod = artifact_dir / "client-mods/fabric-api-0.162.0+26.3.jar"
    pack = artifact_dir / "CopiMineResourcePack-26.3.zip"
    plugin_candidate = artifact_dir / "server-plugins/TestPlugin.jar"
    baseline_candidate = artifact_dir / "plugins/jars/BaselinePlugin-26.3.jar"
    for path, value in ((paper, b"locked Paper 26.3 fixture"), (client, b"locked Fabric client fixture"),
                        (locked_mod, b"locked Fabric API 26.3 fixture"),
                        (pack, b"locked migrated resource pack fixture"), (plugin_candidate, b"locked TestPlugin candidate")):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(value)

    baseline_bytes = b"unchanged but installed baseline plugin"
    baseline_candidate.parent.mkdir(parents=True, exist_ok=True)
    baseline_candidate.write_bytes(baseline_bytes)
    runtime_plugins = root / "local-runtime/end-rift-server-26.3/plugins"
    runtime_plugins.mkdir(parents=True, exist_ok=True)
    (runtime_plugins / plugin_candidate.name).write_bytes(plugin_candidate.read_bytes())
    (runtime_plugins / "BaselinePlugin.jar").write_bytes(baseline_bytes)
    profile = {
        "status": "migration-accepted",
        "minecraftVersion": "26.3",
        "paper": {"build": 161, "sha256": digest(paper)},
        "clientArtifact": {"filename": client.name, "sha512": digest(client, "sha512"), "size": client.stat().st_size},
        "resourcePackMigration": {"candidateSha256": digest(pack), "filename": "CopiMineResourcePack-26.3.zip"},
        "fabric": {"loader": "0.19.5"},
    }
    plugins = {
        "status": "migration-accepted",
        "nativeVerified": True,
        "minecraftVersion": "26.3",
        "modules": [{
            "pluginName": "TestPlugin", "filename": plugin_candidate.name,
            "sha256": digest(plugin_candidate), "sha512": digest(plugin_candidate, "sha512"),
            "size": plugin_candidate.stat().st_size, "nativeVerified": True,
        }],
        "unchangedBaseline": [{
            "pluginName": "BaselinePlugin", "filename": "BaselinePlugin.jar",
            "sha256": hashlib.sha256(baseline_bytes).hexdigest(), "nativeVerified": True,
            "candidate": {
                "filename": baseline_candidate.name,
                "runtimeFilename": "BaselinePlugin.jar",
                "buildArtifact": baseline_candidate.relative_to(root).as_posix(),
                "sha256": digest(baseline_candidate),
                "nativeVerified": True,
            },
        }],
    }
    profile_path = root / "tools/minecraft-26.3/profile.lock.json"
    plugins_path = root / "tools/minecraft-26.3/server-plugins.lock.json"
    client_mods_path = root / "tools/minecraft-26.3/client-mods.lock.json"
    client_mods = {
        "schemaVersion": 1,
        "minecraftVersion": "26.3",
        "nativeVerified": True,
        "modules": [{
            "filename": locked_mod.name,
            "sha512": digest(locked_mod, "sha512"),
            "size": locked_mod.stat().st_size,
        }],
    }
    write_json(profile_path, profile)
    write_json(plugins_path, plugins)
    write_json(client_mods_path, client_mods)

    evidence_dir = root / EVIDENCE
    evidence_dir.mkdir(parents=True, exist_ok=True)
    server_log = evidence_dir / "server.log"
    mod_log = evidence_dir / "copimineclient.log"
    minecraft_log = evidence_dir / "latest.log"
    inventory_path = evidence_dir / "server-plugins.json"
    installer_receipt_path = evidence_dir / "migration-plugin-receipt.json"
    plugin_snapshot_path = evidence_dir / "installed-plugins.zip"
    screenshot_model = evidence_dir / "model.png"
    screenshot_shader = evidence_dir / "shader.png"
    mod_inventory_path = evidence_dir / "client-mod-inventory.json"
    server_log.write_text(
        paper_server_startup_log([["TestPlugin", "BaselinePlugin"], ["TestPlugin", "BaselinePlugin"]]),
        encoding="utf-8",
    )
    session_id = "6f893b31-e88d-4237-b02a-41c7fa8af5ef"
    mod_log.write_text(
        "CopiMineClient bootstrap finished\nBridge hello sent: session=" + session_id + "\n"
        "Bridge handshake acknowledged: protocol=2, session=" + session_id + "\n"
        "Shader activated through Iris: test profile\n",
        encoding="utf-8",
    )
    minecraft_log.write_text(
        "Loading Minecraft 26.3 with Fabric Loader 0.19.5\n"
        "Reloading ResourceManager: vanilla, fabric, file/CopiMineResourcePack-26.3.zip\n",
        encoding="utf-8",
    )
    inventory_rows = [
        {"pluginName": "TestPlugin", "filename": "TestPlugin.jar", "sha256": digest(plugin_candidate)},
        {"pluginName": "BaselinePlugin", "filename": "BaselinePlugin.jar", "sha256": hashlib.sha256(baseline_bytes).hexdigest()},
    ]
    write_json(inventory_path, {"plugins": inventory_rows})
    write_json(installer_receipt_path, {
        "schemaVersion": 1,
        "minecraftVersion": "26.3",
        "nativeVerified": False,
        "installed": [
            {**inventory_rows[0], "sha512": digest(plugin_candidate, "sha512"), "version": "1.0"},
            {**inventory_rows[1], "sha512": digest(baseline_candidate, "sha512"), "version": "2.0"},
        ],
        "pluginInventory": inventory_rows,
    })
    write_plugin_snapshot(plugin_snapshot_path, {
        "TestPlugin.jar": plugin_candidate.read_bytes(),
        "BaselinePlugin.jar": baseline_bytes,
    })
    screenshot_model.write_bytes(valid_png())
    screenshot_shader.write_bytes(valid_png())
    write_json(mod_inventory_path, {
        "schemaVersion": 1,
        "minecraftVersion": "26.3",
        "profile": "Copimine",
        "mods": [
            {"filename": client.name, "sha512": digest(client, "sha512"), "size": client.stat().st_size},
            {"filename": locked_mod.name, "sha512": digest(locked_mod, "sha512"), "size": locked_mod.stat().st_size},
        ],
    })

    record = {
        "schemaVersion": 4,
        "sourceCommit": "a" * 40,
        "nativeVerified": True,
        "minecraftVersion": "26.3",
        "profileLockSha256": digest(profile_path),
        "serverPluginLockSha256": digest(plugins_path),
        "clientModsLockSha256": digest(client_mods_path),
        "candidateArtifacts": {
            "paper": {"path": paper.relative_to(root).as_posix(), "sha256": digest(paper)},
            "client": {"path": client.relative_to(root).as_posix(), "sha512": digest(client, "sha512")},
            "resourcePack": {"path": pack.relative_to(root).as_posix(), "sha256": digest(pack)},
        },
        "server": {
            "minecraftVersion": "26.3",
            "paperBuild": 161,
            "loadedPlugins": ["TestPlugin", "BaselinePlugin"],
            "checks": {name: True for name in SERVER_CHECKS},
            "pluginInventory": evidence_entry(inventory_path, root),
            "installerReceipt": evidence_entry(installer_receipt_path, root),
            "pluginSnapshot": evidence_entry(plugin_snapshot_path, root),
            "log": evidence_entry(server_log, root),
        },
        "client": {
            "minecraftVersion": "26.3",
            "fabricLoader": "0.19.5",
            "modInventory": evidence_entry(mod_inventory_path, root),
            "checks": {name: True for name in CLIENT_CHECKS},
            "logs": {
                "mod": evidence_entry(mod_log, root),
                "minecraft": evidence_entry(minecraft_log, root),
            },
            "screenshots": [
                {**evidence_entry(screenshot_model, root), "id": "model", "captureMethod": "minecraft-f2"},
                {**evidence_entry(screenshot_shader, root), "id": "shader", "captureMethod": "minecraft-f2"},
            ],
            "visualChecks": {
                "customModel": {"screenshotId": "model"},
                "shaderRuntime": {"screenshotId": "shader"},
            },
        },
    }
    record_path = evidence_dir / "acceptance.json"
    write_json(record_path, record)
    return record_path, record


def make_url_baseline_fixture(root: Path) -> tuple[Path, dict, Path]:
    record_path, record = make_fixture(root)
    plugins_path = root / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    candidate = plugins["unchangedBaseline"][0]["candidate"]
    source = root / candidate["buildArtifact"]
    staged_candidate = root / "build/minecraft-26.3/server-plugins/BaselinePlugin-26.3.jar"
    staged_candidate.parent.mkdir(parents=True, exist_ok=True)
    staged_candidate.write_bytes(source.read_bytes())
    candidate["filename"] = staged_candidate.name
    candidate["url"] = "https://example.invalid/BaselinePlugin-26.3.jar"
    candidate["size"] = staged_candidate.stat().st_size
    candidate["sha512"] = digest(staged_candidate, "sha512")
    del candidate["buildArtifact"]
    write_json(plugins_path, plugins)
    record["serverPluginLockSha256"] = digest(plugins_path)
    write_json(record_path, record)
    return record_path, record, staged_candidate


def test_acceptance_gate_accepts_matching_locks_artifacts_inventory_logs_and_valid_f2_pngs(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    validate(tmp_path, record_path)


def test_acceptance_gate_requires_every_locked_plugin_on_each_successful_server_start(tmp_path):
    record_path, record = make_fixture(tmp_path)
    server_log = tmp_path / EVIDENCE / "server.log"
    server_log.write_text(
        paper_server_startup_log([["TestPlugin", "BaselinePlugin"], ["TestPlugin"]]),
        encoding="utf-8",
    )
    record["server"]["log"] = evidence_entry(server_log, tmp_path)
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="each successful Minecraft 26.3 server start"):
        validate(tmp_path, record_path)


def test_acceptance_gate_ignores_plugin_prefixed_fake_server_log_records(tmp_path):
    record_path, record = make_fixture(tmp_path)
    server_log = tmp_path / EVIDENCE / "server.log"
    server_log.write_text(
        "[00:00:01] [Server thread/INFO]: Starting minecraft server version 26.3\n"
        "[00:00:02] [Server thread/INFO]: [TestPlugin] Enabling TestPlugin v1.0\n"
        "[00:00:03] [Server thread/INFO]: [BaselinePlugin] Enabling BaselinePlugin v1.0\n"
        "[00:00:04] [Server thread/INFO]: Done (1.2s)!\n"
        "[00:00:05] [Server thread/INFO]: [SpoofingPlugin] Starting minecraft server version 26.3\n"
        "[00:00:06] [Server thread/INFO]: [TestPlugin] Enabling TestPlugin v1.0\n"
        "[00:00:07] [Server thread/INFO]: [BaselinePlugin] Enabling BaselinePlugin v1.0\n"
        "[00:00:08] [Server thread/INFO]: [SpoofingPlugin] Done (1.1s)!\n",
        encoding="utf-8",
    )
    record["server"]["log"] = evidence_entry(server_log, tmp_path)
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="two successful Minecraft 26.3 starts"):
        validate(tmp_path, record_path)


def test_acceptance_gate_ignores_plugin_prefixed_fake_enable_record(tmp_path):
    record_path, record = make_fixture(tmp_path)
    server_log = tmp_path / EVIDENCE / "server.log"
    server_log.write_text(
        "[00:00:01] [Server thread/INFO]: Starting minecraft server version 26.3\n"
        "[00:00:02] [Server thread/INFO]: [SpoofingPlugin] Enabling TestPlugin v1.0\n"
        "[00:00:03] [Server thread/INFO]: [BaselinePlugin] Enabling BaselinePlugin v1.0\n"
        "[00:00:04] [Server thread/INFO]: Done (1.2s)!\n"
        "[00:00:05] [Server thread/INFO]: Starting minecraft server version 26.3\n"
        "[00:00:06] [Server thread/INFO]: [TestPlugin] Enabling TestPlugin v1.0\n"
        "[00:00:07] [Server thread/INFO]: [BaselinePlugin] Enabling BaselinePlugin v1.0\n"
        "[00:00:08] [Server thread/INFO]: Done (1.2s)!\n",
        encoding="utf-8",
    )
    record["server"]["log"] = evidence_entry(server_log, tmp_path)
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="start 1 is missing: TestPlugin"):
        validate(tmp_path, record_path)


def test_verified_snapshot_contains_only_signed_paths_even_if_source_tree_changes_after_validation(tmp_path, monkeypatch):
    record_path, _ = make_fixture(tmp_path)
    snapshot_path = tmp_path / "build" / "verified-native-acceptance.zip"
    original = acceptance_validator._verify_closed_evidence_tree

    def close_then_add_unreferenced_file(root, referenced_files, **kwargs):
        original(root, referenced_files, **kwargs)
        (root / EVIDENCE / "added-after-validation.bin").write_bytes(b"not in signed evidence")

    monkeypatch.setattr(acceptance_validator, "_verify_closed_evidence_tree", close_then_add_unreferenced_file)

    validate(tmp_path, record_path, verified_output=snapshot_path)

    with zipfile.ZipFile(snapshot_path) as archive:
        names = set(archive.namelist())
    assert "artifacts/minecraft-26.3/native-acceptance/acceptance.json" in names
    assert "artifacts/minecraft-26.3/native-acceptance/acceptance.sig" in names
    assert "artifacts/minecraft-26.3/native-acceptance/added-after-validation.bin" not in names


def test_verified_snapshot_rejects_changed_signed_file_after_closed_tree_check(tmp_path, monkeypatch):
    record_path, _ = make_fixture(tmp_path)
    original = acceptance_validator._verify_closed_evidence_tree

    def close_then_change_server_log(root, referenced_files, **kwargs):
        original(root, referenced_files, **kwargs)
        (root / EVIDENCE / "server.log").write_text("tampered after verification", encoding="utf-8")

    monkeypatch.setattr(acceptance_validator, "_verify_closed_evidence_tree", close_then_change_server_log)

    with pytest.raises(AcceptanceError, match="changed after native acceptance validation"):
        validate(tmp_path, record_path, verified_output=tmp_path / "build" / "verified-native-acceptance.zip")


def test_acceptance_gate_rejects_missing_source_commit(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record.pop("sourceCommit")
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="sourceCommit"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_source_commit_not_matching_ci_parent(tmp_path):
    record_path, _ = make_fixture(tmp_path)

    with pytest.raises(AcceptanceError, match="does not match the expected source commit"):
        validate(tmp_path, record_path, expected_source_commit="b" * 40)


def test_acceptance_gate_rejects_missing_client_mod_inventory(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record["client"].pop("modInventory")
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="client mod inventory"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_client_mod_inventory_that_differs_from_locked_jars(tmp_path):
    record_path, record = make_fixture(tmp_path)
    inventory_path = tmp_path / EVIDENCE / "client-mod-inventory.json"
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    inventory["mods"][1]["sha512"] = "0" * 128
    write_json(inventory_path, inventory)
    record["client"]["modInventory"] = evidence_entry(inventory_path, tmp_path)
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="client mod inventory"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_missing_record(tmp_path):
    with pytest.raises(AcceptanceError, match="Cannot read native acceptance record"):
        validate(tmp_path, tmp_path / EVIDENCE / "acceptance.json")


def write_unaccepted_lock_bundle(root: Path, *, promoted: str | None = None) -> None:
    locks = {
        "tools/minecraft-26.3/profile.lock.json": {"minecraftVersion": "26.3", "status": "migration-candidate"},
        "tools/minecraft-26.3/server-plugins.lock.json": {
            "minecraftVersion": "26.3", "status": "runtime-candidates", "modules": []
        },
        "tools/minecraft-26.3/client-mods.lock.json": {"minecraftVersion": "26.3", "nativeVerified": False},
    }
    if promoted == "profile-status":
        locks["tools/minecraft-26.3/profile.lock.json"]["status"] = "migration-accepted"
    elif promoted == "plugin-native-verified":
        locks["tools/minecraft-26.3/server-plugins.lock.json"]["nativeVerified"] = True
    elif promoted == "client-native-verified":
        locks["tools/minecraft-26.3/client-mods.lock.json"]["nativeVerified"] = True

    for relative, lock in locks.items():
        write_json(root / relative, lock)


def test_native_acceptance_requirement_allows_unaccepted_candidate_locks_without_evidence(tmp_path):
    write_unaccepted_lock_bundle(tmp_path)

    acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


@pytest.mark.parametrize("promoted", ["profile-status", "plugin-native-verified", "client-native-verified"])
def test_native_acceptance_requirement_rejects_promoted_locks_without_signed_evidence(tmp_path, promoted):
    write_unaccepted_lock_bundle(tmp_path, promoted=promoted)

    with pytest.raises(AcceptanceError, match="signed native acceptance evidence is required"):
        acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


def test_native_acceptance_requirement_detects_nested_native_verification_claims(tmp_path):
    write_unaccepted_lock_bundle(tmp_path)
    plugin_lock_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugin_lock = json.loads(plugin_lock_path.read_text(encoding="utf-8"))
    plugin_lock["modules"] = [{"pluginName": "nested-candidate", "review": {"nativeVerified": True}}]
    write_json(plugin_lock_path, plugin_lock)

    with pytest.raises(AcceptanceError, match="signed native acceptance evidence is required"):
        acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


def test_native_acceptance_requirement_allows_promoted_locks_with_record_and_signature(tmp_path):
    write_unaccepted_lock_bundle(tmp_path, promoted="client-native-verified")
    evidence_dir = tmp_path / EVIDENCE
    evidence_dir.mkdir(parents=True)
    (evidence_dir / "acceptance.json").write_text("{}", encoding="utf-8")
    (evidence_dir / "acceptance.sig").write_text("signature", encoding="ascii")

    acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


@pytest.mark.parametrize("present_file", ["acceptance.json", "acceptance.sig"])
def test_native_acceptance_requirement_rejects_partial_evidence_for_promoted_locks(tmp_path, present_file):
    write_unaccepted_lock_bundle(tmp_path, promoted="client-native-verified")
    evidence_dir = tmp_path / EVIDENCE
    evidence_dir.mkdir(parents=True)
    (evidence_dir / present_file).write_text("placeholder", encoding="ascii")

    with pytest.raises(AcceptanceError, match="signed native acceptance evidence is required"):
        acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


def test_native_acceptance_requirement_rejects_reparse_point_signature_path(tmp_path, monkeypatch):
    write_unaccepted_lock_bundle(tmp_path, promoted="profile-status")
    evidence_dir = tmp_path / EVIDENCE
    evidence_dir.mkdir(parents=True)
    (evidence_dir / "acceptance.json").write_text("{}", encoding="utf-8")
    signature_path = evidence_dir / "acceptance.sig"
    signature_path.write_text("signature", encoding="ascii")
    original_lstat = Path.lstat

    def lstat_with_signature_reparse_point(path):
        if path == signature_path:
            return SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_file_attributes=0x400)
        return original_lstat(path)

    monkeypatch.setattr(Path, "lstat", lstat_with_signature_reparse_point)

    with pytest.raises(AcceptanceError, match="must not contain symlinks or reparse points"):
        acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


def test_native_acceptance_requirement_rejects_directory_instead_of_signature_file(tmp_path):
    write_unaccepted_lock_bundle(tmp_path, promoted="profile-status")
    evidence_dir = tmp_path / EVIDENCE
    evidence_dir.mkdir(parents=True)
    (evidence_dir / "acceptance.json").write_text("{}", encoding="utf-8")
    (evidence_dir / "acceptance.sig").mkdir()

    with pytest.raises(AcceptanceError, match="native acceptance detached signature is not a regular file"):
        acceptance_validator.require_native_acceptance_for_promoted_locks(tmp_path)


def test_shared_json_reader_caps_read_before_parsing(tmp_path, monkeypatch):
    record_path = tmp_path / "oversized-lock.json"
    record_path.write_bytes(b"x" * 9)
    monkeypatch.setattr(acceptance_validator, "MAX_JSON_BYTES", 8)
    calls = _observe_file_read_sizes(monkeypatch, {record_path})

    with pytest.raises(AcceptanceError, match="record exceeds the 10 MiB safety limit"):
        acceptance_validator._read_json_with_bytes(record_path, "record")

    assert calls == [(record_path, 9)]


@pytest.mark.parametrize("oversized_input", ["record", "signature"])
def test_acceptance_gate_bounds_signed_input_reads_before_allocation(tmp_path, monkeypatch, oversized_input):
    record_path, _ = make_fixture(tmp_path)
    if oversized_input == "record":
        record_path.write_bytes(b"x" * (MAX_JSON_BYTES + 1))
        expected_error = "record exceeds the 10 MiB safety limit"
    else:
        record_path.with_name("acceptance.sig").write_bytes(b"x" * 1025)
        expected_error = "signature exceeds the 1 KiB safety limit"

    signature_path = record_path.with_name("acceptance.sig")
    calls = _observe_file_read_sizes(monkeypatch, {record_path, signature_path})
    with pytest.raises(AcceptanceError, match=expected_error):
        _read_signed_acceptance(tmp_path, record_path, _TEST_PUBLIC_KEY_HEX)
    assert calls == [(record_path, MAX_JSON_BYTES + 1), (signature_path, 1025)]


def test_acceptance_gate_bounds_png_read_before_allocation(tmp_path, monkeypatch):
    screenshot = tmp_path / "oversized.png"
    monkeypatch.setattr("scripts.minecraft.validate_native_acceptance.MAX_PNG_BYTES", 44)
    with screenshot.open("wb") as output:
        output.truncate(45)

    calls = _observe_file_read_sizes(monkeypatch, {screenshot})
    with pytest.raises(AcceptanceError, match="not a valid, bounded PNG"):
        _validate_png(screenshot, "oversized screenshot")
    assert calls == [(screenshot, 45)]


def test_signer_bounds_acceptance_record_and_previous_signature_reads(tmp_path, monkeypatch):
    oversized_record = tmp_path / "oversized-acceptance.json"
    oversized_record.write_bytes(b"x" * (MAX_JSON_BYTES + 1))
    oversized_signature = tmp_path / "oversized-acceptance.sig"
    oversized_signature.write_bytes(b"x" * 1025)

    calls = _observe_file_read_sizes(monkeypatch, {oversized_record, oversized_signature})
    with pytest.raises(SigningError, match="record exceeds the 10 MiB safety limit"):
        acceptance_signer._canonical_record(oversized_record)
    with pytest.raises(SigningError, match="signature exceeds the 1 KiB safety limit"):
        acceptance_signer._write_signature_atomically(oversized_signature, b"replacement")
    assert calls == [(oversized_record, MAX_JSON_BYTES + 1), (oversized_signature, 1025)]


def test_signer_bounds_private_key_read(tmp_path, monkeypatch):
    private_key_path = tmp_path / "native-acceptance.pem"
    private_key_path.write_bytes(
        _TEST_PRIVATE_KEY.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
    )
    calls = _observe_file_read_sizes(monkeypatch, {private_key_path})

    loaded_key = acceptance_signer._load_private_key(private_key_path)

    assert isinstance(loaded_key, Ed25519PrivateKey)
    assert calls == [(private_key_path, 16 * 1024 + 1)]


def test_signer_rejects_oversized_private_key_before_pem_parsing(tmp_path, monkeypatch):
    private_key_path = tmp_path / "oversized-native-acceptance.pem"
    private_key_path.write_bytes(b"x" * 9)
    monkeypatch.setattr(acceptance_signer, "MAX_PRIVATE_KEY_BYTES", 8)
    calls = _observe_file_read_sizes(monkeypatch, {private_key_path})

    with pytest.raises(SigningError, match="private key exceeds the 16 KiB safety limit"):
        acceptance_signer._load_private_key(private_key_path)

    assert calls == [(private_key_path, 9)]


def test_signer_rechecks_record_with_bounded_read_before_signing(tmp_path, monkeypatch):
    root = tmp_path / "checkout"
    root.mkdir()
    record_path, _ = make_fixture(root)
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    generate_key(private_key_path, root)
    original_read_bytes = Path.read_bytes
    original_canonical_record = acceptance_signer._canonical_record
    calls = _observe_file_read_sizes(monkeypatch, {record_path})

    def enlarge_after_canonical_read(path):
        canonical = original_canonical_record(path)
        with path.open("wb") as output:
            output.write(b"x" * (MAX_JSON_BYTES + 1))
        return canonical

    def reject_record_unbounded_read(path):
        if path == record_path:
            raise AssertionError("the signer must recheck acceptance.json with a bounded read")
        return original_read_bytes(path)

    monkeypatch.setattr(acceptance_signer, "_canonical_record", enlarge_after_canonical_read)
    monkeypatch.setattr(Path, "read_bytes", reject_record_unbounded_read)
    with pytest.raises(SigningError, match="record exceeds the 10 MiB safety limit"):
        sign_record(root, private_key_path)
    assert calls == [(record_path, MAX_JSON_BYTES + 1), (record_path, MAX_JSON_BYTES + 1)]


def test_signer_validates_candidate_before_replacing_previous_signature(tmp_path, monkeypatch):
    root = tmp_path / "checkout"
    root.mkdir()
    record_path, _ = make_fixture(root)
    signature_path = record_path.with_name("acceptance.sig")
    with signature_path.open("rb") as source:
        previous_signature = source.read()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    generate_key(private_key_path, root)
    original_replace = acceptance_signer.os.replace
    replace_calls = []

    def track_replace(source, destination):
        if Path(destination) == signature_path:
            replace_calls.append((Path(source), Path(destination)))
        return original_replace(source, destination)

    def fail_validation(*_args, **_kwargs):
        raise AcceptanceError("forced candidate validation failure")

    monkeypatch.setattr(acceptance_signer.os, "replace", track_replace)
    monkeypatch.setattr(acceptance_signer, "validate", fail_validation)
    with pytest.raises(AcceptanceError, match="forced candidate validation failure"):
        sign_record(root, private_key_path)

    assert not replace_calls, "an invalid candidate must be rejected before publishing any signature bytes"
    with signature_path.open("rb") as source:
        assert source.read() == previous_signature


def test_signer_preserves_previous_signature_when_atomic_publish_fails(tmp_path, monkeypatch):
    root = tmp_path / "checkout"
    root.mkdir()
    record_path, _ = make_fixture(root)
    signature_path = record_path.with_name("acceptance.sig")
    with signature_path.open("rb") as source:
        previous_signature = source.read()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    generate_key(private_key_path, root)
    original_replace = acceptance_signer.os.replace

    def fail_atomic_publish(source, destination):
        if Path(destination) == signature_path:
            raise PermissionError("forced atomic publish denial")
        return original_replace(source, destination)

    monkeypatch.setattr(acceptance_signer.os, "replace", fail_atomic_publish)
    with pytest.raises(SigningError, match="cannot write detached signature atomically"):
        sign_record(root, private_key_path)
    with signature_path.open("rb") as source:
        assert source.read() == previous_signature


def test_signer_transaction_lock_serializes_processes(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    started_path = tmp_path / "signer-started.txt"
    entered_path = tmp_path / "signer-entered-lock.txt"
    child_script = """
import sys
from pathlib import Path
from scripts.minecraft.sign_native_acceptance import _signing_transaction_lock
root, started, entered = map(Path, sys.argv[1:4])
started.write_text('started', encoding='ascii')
with _signing_transaction_lock(root):
    entered.write_text('entered', encoding='ascii')
"""
    child = None
    try:
        with acceptance_signer._signing_transaction_lock(root):
            child = subprocess.Popen(
                [sys.executable, "-c", child_script, str(root), str(started_path), str(entered_path)],
                cwd=Path(__file__).resolve().parents[1],
            )
            deadline = time.monotonic() + 10
            while not started_path.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            assert started_path.exists(), "the second signer process did not start"
            time.sleep(0.2)
            assert not entered_path.exists(), "a second signer entered before the first released its lock"
        assert child is not None and child.wait(timeout=10) == 0
        assert entered_path.is_file(), "the second signer should proceed after the first releases its lock"
    finally:
        if child is not None and child.poll() is None:
            child.wait(timeout=10)


def test_signer_transaction_lock_preserves_operation_oserror(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    with pytest.raises(OSError, match="forced signing operation failure"):
        with acceptance_signer._signing_transaction_lock(root):
            raise OSError("forced signing operation failure")


@pytest.mark.parametrize("section,check", [("server", SERVER_CHECKS[0]), ("client", CLIENT_CHECKS[1])])
def test_acceptance_gate_rejects_incomplete_native_checks(tmp_path, section, check):
    record_path, record = make_fixture(tmp_path)
    record[section]["checks"][check] = False
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="native check is incomplete"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_unsigned_native_record(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    record_path.with_name("acceptance.sig").unlink()
    with pytest.raises(AcceptanceError, match="acceptance detached signature is missing"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_unreferenced_file_in_evidence_directory(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    (record_path.parent / "unreviewed.ps1").write_text("untrusted payload", encoding="utf-8")

    with pytest.raises(AcceptanceError, match="unreferenced native acceptance file"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_unreferenced_directory_in_evidence_tree(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    (record_path.parent / "unreviewed").mkdir()

    with pytest.raises(AcceptanceError, match="unreferenced native acceptance directory"):
        validate(tmp_path, record_path)


def test_acceptance_gate_requires_a_trusted_external_public_key(tmp_path, monkeypatch):
    record_path, _ = make_fixture(tmp_path)
    monkeypatch.delenv(ACCEPTANCE_PUBLIC_KEY_ENV)
    with pytest.raises(AcceptanceError, match="trusted Ed25519 key is missing or invalid"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_record_changed_after_signing(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record["server"]["checks"]["paperStarted"] = False
    record_path.write_bytes(rfc8785.dumps(record))
    with pytest.raises(AcceptanceError, match="signature does not match"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_untrusted_public_key(tmp_path, monkeypatch):
    record_path, _ = make_fixture(tmp_path)
    other_key = Ed25519PrivateKey.generate().public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    ).hex()
    monkeypatch.setenv(ACCEPTANCE_PUBLIC_KEY_ENV, other_key)
    with pytest.raises(AcceptanceError, match="signature does not match"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_noncanonical_signed_record(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record_path.write_text(json.dumps(record, indent=2), encoding="utf-8")
    with pytest.raises(AcceptanceError, match="RFC 8785 canonical JSON"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_duplicate_json_properties(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    record_path.write_text('{"schemaVersion":4,"schemaVersion":4}', encoding="utf-8")
    with pytest.raises(AcceptanceError, match="duplicate property: schemaVersion"):
        validate(tmp_path, record_path)


def test_signer_creates_external_key_and_signs_only_valid_native_record(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    record_path, _ = make_fixture(root)
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    public_key_hex = generate_key(private_key_path, root)

    assert private_key_path.is_file()
    assert not private_key_path.is_relative_to(root)
    assert sign_record(root, private_key_path) == public_key_hex
    validate(root, record_path, trusted_public_key_hex=public_key_hex)


def test_signer_validates_fresh_evidence_before_creating_signature_file(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    record_path, _ = make_fixture(root)
    signature_path = record_path.with_name("acceptance.sig")
    signature_path.unlink()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    public_key_hex = generate_key(private_key_path, root)

    assert sign_record(root, private_key_path) == public_key_hex
    assert signature_path.is_file()
    validate(root, record_path, trusted_public_key_hex=public_key_hex)


def test_signer_rejects_unreferenced_file_without_existing_signature(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    record_path, _ = make_fixture(root)
    signature_path = record_path.with_name("acceptance.sig")
    signature_path.unlink()
    (record_path.parent / "unreviewed.bin").write_bytes(b"unreferenced")
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    generate_key(private_key_path, root)

    with pytest.raises(AcceptanceError, match="unreferenced native acceptance file"):
        sign_record(root, private_key_path)
    assert not signature_path.exists()


@pytest.mark.skipif(os.name != "nt", reason="requires Windows ACL controls")
def test_signer_removes_shared_acl_from_existing_external_private_key(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    make_fixture(root)
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    generate_key(private_key_path, root)
    icacls = acceptance_signer._windows_system_executable("icacls.exe")
    shared_sid = "S-1-5-32-545"  # BUILTIN\Users
    subprocess.run(
        [str(icacls), str(private_key_path), "/grant", f"*{shared_sid}:R"],
        check=True,
        capture_output=True,
        cwd=str(icacls.parent),
    )

    acl_path = private_key_path.with_suffix(".acl")
    subprocess.run(
        [str(icacls), str(private_key_path), "/save", str(acl_path)],
        check=True,
        capture_output=True,
        cwd=str(icacls.parent),
    )
    assert "BU" in acl_path.read_text(encoding="utf-16le")

    sign_record(root, private_key_path)

    subprocess.run(
        [str(icacls), str(private_key_path), "/save", str(acl_path)],
        check=True,
        capture_output=True,
        cwd=str(icacls.parent),
    )
    acl = acl_path.read_text(encoding="utf-16le")
    assert "BU" not in acl
    assert shared_sid not in acl


def test_signer_rejects_a_private_key_inside_the_checkout(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    private_key_path = root / "private.pem"
    private_key_path.write_bytes(b"not a usable key")
    with pytest.raises(SigningError, match="outside the repository checkout"):
        sign_record(root, private_key_path)


@pytest.mark.skipif(os.name == "nt", reason="POSIX private-key permission controls")
def test_signer_restricts_existing_external_private_key_permissions(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    make_fixture(root)
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    generate_key(private_key_path, root)
    private_key_path.chmod(0o644)

    sign_record(root, private_key_path)

    assert stat.S_IMODE(private_key_path.stat().st_mode) == 0o600


def test_keygen_preserves_an_existing_private_key(tmp_path):
    root = tmp_path / "checkout"
    root.mkdir()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    private_key_path.parent.mkdir()
    original = b"keep existing key file"
    private_key_path.write_bytes(original)
    with pytest.raises(SigningError, match="cannot create external private key"):
        generate_key(private_key_path, root)
    assert private_key_path.read_bytes() == original


@pytest.mark.skipif(os.name != "nt", reason="Windows default private-key path")
def test_default_key_path_uses_local_app_data_on_windows(monkeypatch):
    monkeypatch.setenv("LOCALAPPDATA", r"C:\Users\CopiMine\AppData\Local")
    monkeypatch.setenv("XDG_CONFIG_HOME", r"C:\ignore-this-on-windows")

    assert str(acceptance_signer._default_key_path()) == (
        r"C:\Users\CopiMine\AppData\Local\CopiMine\minecraft-26.3\native-acceptance-ed25519-private.pem"
    )


def test_default_key_path_uses_xdg_config_home_on_posix(monkeypatch):
    monkeypatch.setattr(acceptance_signer.sys, "platform", "linux")
    monkeypatch.setattr(acceptance_signer, "Path", PurePosixPath)
    monkeypatch.setenv("LOCALAPPDATA", r"C:\must-be-ignored")
    monkeypatch.setenv("XDG_CONFIG_HOME", "/home/copimine/.config")

    assert str(acceptance_signer._default_key_path()) == (
        "/home/copimine/.config/CopiMine/minecraft-26.3/native-acceptance-ed25519-private.pem"
    )


def test_default_key_path_uses_xdg_default_when_config_home_is_unset(monkeypatch):
    monkeypatch.setattr(acceptance_signer.sys, "platform", "linux")
    monkeypatch.setattr(acceptance_signer, "Path", PurePosixPath)
    monkeypatch.delenv("LOCALAPPDATA", raising=False)
    monkeypatch.delenv("XDG_CONFIG_HOME", raising=False)
    monkeypatch.setattr(
        acceptance_signer.Path,
        "home",
        classmethod(lambda path_type: path_type("/home/copimine")),
        raising=False,
    )

    assert str(acceptance_signer._default_key_path()) == (
        "/home/copimine/.config/CopiMine/minecraft-26.3/native-acceptance-ed25519-private.pem"
    )


def test_default_key_path_rejects_relative_xdg_config_home(monkeypatch):
    monkeypatch.setattr(acceptance_signer.sys, "platform", "linux")
    monkeypatch.setattr(acceptance_signer, "Path", PurePosixPath)
    monkeypatch.setenv("XDG_CONFIG_HOME", "relative-config")

    with pytest.raises(SigningError, match="XDG_CONFIG_HOME must be an absolute path"):
        acceptance_signer._default_key_path()


def test_keygen_restricts_new_file_before_writing_private_key(tmp_path, monkeypatch):
    root = tmp_path / "checkout"
    root.mkdir()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    restricted_file_states = []
    original_restrict = acceptance_signer._restrict_private_key_permissions

    def record_restricted_file(path):
        restricted_file_states.append((path.read_bytes(), path.stat().st_mode))
        original_restrict(path)

    monkeypatch.setattr(acceptance_signer, "_restrict_private_key_permissions", record_restricted_file)

    acceptance_signer.generate_key(private_key_path, root)

    assert len(restricted_file_states) == 1
    assert restricted_file_states[0][0] == b""
    if os.name != "nt":
        assert stat.S_IMODE(restricted_file_states[0][1]) == 0o600
    assert acceptance_signer._load_private_key(private_key_path) is not None


def test_keygen_closes_raw_descriptor_before_removing_after_fdopen_failure(tmp_path, monkeypatch):
    root = tmp_path / "checkout"
    root.mkdir()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    events = []
    original_open = os.open
    original_close = os.close
    original_remove = acceptance_signer._remove_partial_key

    def create_descriptor(path):
        return original_open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)

    def fail_fdopen(descriptor, mode):
        events.append(("fdopen", descriptor, mode))
        raise RuntimeError("fdopen failed")

    def tracked_close(descriptor):
        events.append(("close", descriptor))
        return original_close(descriptor)

    def tracked_remove(path):
        events.append(("remove", path))
        return original_remove(path)

    monkeypatch.setattr(acceptance_signer, "_create_private_key_file_descriptor", create_descriptor)
    monkeypatch.setattr(acceptance_signer.os, "fdopen", fail_fdopen)
    monkeypatch.setattr(acceptance_signer.os, "close", tracked_close)
    monkeypatch.setattr(acceptance_signer, "_remove_partial_key", tracked_remove)

    with pytest.raises(RuntimeError, match="fdopen failed"):
        acceptance_signer.generate_key(private_key_path, root)

    assert [event[0] for event in events] == ["fdopen", "close", "remove"]
    assert not private_key_path.exists()


def test_current_user_sid_extracts_ascii_sid_from_non_utf8_identity(monkeypatch):
    monkeypatch.setattr(
        acceptance_signer.subprocess,
        "run",
        lambda *args, **kwargs: SimpleNamespace(stdout=b'"domain\\user-\xff",S-1-5-21-1-2-3\r\n'),
    )

    assert acceptance_signer._current_user_sid() == "S-1-5-21-1-2-3"


def test_windows_system_directory_comes_from_get_system_directoryw(tmp_path, monkeypatch):
    system_dir = tmp_path / "Windows" / "System32"

    def get_system_directory(buffer, buffer_length):
        assert buffer_length > len(str(system_dir))
        buffer.value = str(system_dir)
        return len(str(system_dir))

    kernel32 = SimpleNamespace(GetSystemDirectoryW=get_system_directory)
    monkeypatch.setattr(
        acceptance_signer.ctypes,
        "WinDLL",
        lambda *_args, **_kwargs: kernel32,
        raising=False,
    )

    assert acceptance_signer._windows_system_directory() == system_dir


def test_windows_private_key_file_uses_protected_user_only_dacl_at_creation(tmp_path, monkeypatch):
    key_path = tmp_path / "native-acceptance.pem"
    calls = {}

    class ApiFunction:
        def __init__(self, callback):
            self.callback = callback

        def __call__(self, *args):
            return self.callback(*args)

    def convert_sddl(sddl, revision, descriptor_out, size_out):
        calls["sddl"] = sddl
        calls["revision"] = revision
        ctypes.cast(descriptor_out, ctypes.POINTER(ctypes.c_void_p)).contents.value = 0x1234
        return 1

    def create_file(filename, access, share, security_attributes, creation, flags, template):
        attributes = ctypes.cast(
            security_attributes,
            ctypes.POINTER(acceptance_signer._WindowsSecurityAttributes),
        ).contents
        calls["create"] = (filename, access, share, attributes.bInheritHandle,
                           attributes.lpSecurityDescriptor, creation, flags, template)
        return 0x5678

    def select_dll(name, **_kwargs):
        if name == "advapi32":
            return SimpleNamespace(
                ConvertStringSecurityDescriptorToSecurityDescriptorW=ApiFunction(convert_sddl)
            )
        return SimpleNamespace(
            CreateFileW=ApiFunction(create_file),
            LocalFree=ApiFunction(lambda descriptor: calls.setdefault("freed", descriptor) or 0),
            CloseHandle=ApiFunction(lambda handle: calls.setdefault("closed", handle) or 1),
        )

    def open_osfhandle(handle, flags):
        calls["fd"] = (handle, flags)
        return 37

    monkeypatch.delattr(acceptance_signer.os, "O_BINARY", raising=False)
    monkeypatch.setattr(acceptance_signer.os, "name", "nt")
    monkeypatch.setattr(acceptance_signer, "_current_user_sid", lambda: "S-1-5-21-1-2-3")
    monkeypatch.setattr(acceptance_signer.ctypes, "WinDLL", select_dll, raising=False)
    monkeypatch.setitem(
        sys.modules,
        "msvcrt",
        SimpleNamespace(open_osfhandle=open_osfhandle),
    )

    descriptor = acceptance_signer._create_windows_private_key_file_descriptor(key_path)

    assert descriptor == 37
    assert calls["sddl"] == "D:P(A;;FA;;;S-1-5-21-1-2-3)"
    assert calls["revision"] == 1
    assert calls["create"][2:6] == (3, False, 0x1234, 1)
    assert calls["fd"] == (0x5678, os.O_WRONLY | getattr(os, "O_BINARY", 0))
    assert calls["freed"].value == 0x1234
    assert "closed" not in calls


def test_windows_private_key_creation_reports_close_handle_failure(tmp_path, monkeypatch):
    key_path = tmp_path / "native-acceptance.pem"
    calls = []

    class ApiFunction:
        def __init__(self, callback):
            self.callback = callback

        def __call__(self, *args):
            return self.callback(*args)

    def convert_sddl(_sddl, _revision, descriptor_out, _size_out):
        ctypes.cast(descriptor_out, ctypes.POINTER(ctypes.c_void_p)).contents.value = 0x1234
        return 1

    def create_file(filename, *_args):
        with open(filename, "wb"):
            pass
        return 0x5678

    def close_handle(handle):
        calls.append(handle)
        return 0

    def select_dll(name, **_kwargs):
        if name == "advapi32":
            return SimpleNamespace(
                ConvertStringSecurityDescriptorToSecurityDescriptorW=ApiFunction(convert_sddl)
            )
        return SimpleNamespace(
            CreateFileW=ApiFunction(create_file),
            LocalFree=ApiFunction(lambda _descriptor: 0),
            CloseHandle=ApiFunction(close_handle),
        )

    monkeypatch.setattr(acceptance_signer.os, "name", "nt")
    monkeypatch.setattr(acceptance_signer, "_current_user_sid", lambda: "S-1-5-21-1-2-3")
    monkeypatch.setattr(acceptance_signer.ctypes, "WinDLL", select_dll, raising=False)
    monkeypatch.setattr(acceptance_signer.ctypes, "get_last_error", lambda: 6, raising=False)
    monkeypatch.setitem(sys.modules, "msvcrt", SimpleNamespace(open_osfhandle=lambda *_args: (_ for _ in ()).throw(OSError("conversion failed"))))

    with pytest.raises(SigningError, match="cannot close securely created private-key file handle"):
        acceptance_signer._create_windows_private_key_file_descriptor(key_path)

    assert calls == [0x5678]
    assert not key_path.exists()


def test_windows_private_key_creation_reports_partial_file_removal_failure(tmp_path, monkeypatch):
    key_path = tmp_path / "native-acceptance.pem"

    class ApiFunction:
        def __init__(self, callback):
            self.callback = callback

        def __call__(self, *args):
            return self.callback(*args)

    def convert_sddl(_sddl, _revision, descriptor_out, _size_out):
        ctypes.cast(descriptor_out, ctypes.POINTER(ctypes.c_void_p)).contents.value = 0x1234
        return 1

    def create_file(filename, *_args):
        with open(filename, "wb"):
            pass
        return 0x5678

    def select_dll(name, **_kwargs):
        if name == "advapi32":
            return SimpleNamespace(
                ConvertStringSecurityDescriptorToSecurityDescriptorW=ApiFunction(convert_sddl)
            )
        return SimpleNamespace(
            CreateFileW=ApiFunction(create_file),
            LocalFree=ApiFunction(lambda _descriptor: 0),
            CloseHandle=ApiFunction(lambda _handle: 1),
        )

    def fail_unlink(_path, *args, **kwargs):
        raise PermissionError("unlink blocked")

    monkeypatch.setattr(acceptance_signer.os, "name", "nt")
    monkeypatch.setattr(acceptance_signer, "_current_user_sid", lambda: "S-1-5-21-1-2-3")
    monkeypatch.setattr(acceptance_signer.ctypes, "WinDLL", select_dll, raising=False)
    monkeypatch.setitem(sys.modules, "msvcrt", SimpleNamespace(open_osfhandle=lambda *_args: (_ for _ in ()).throw(OSError("conversion failed"))))
    monkeypatch.setattr(Path, "unlink", fail_unlink)

    with pytest.raises(SigningError, match="cannot remove partially created private-key file"):
        acceptance_signer._create_windows_private_key_file_descriptor(key_path)


def test_current_user_sid_uses_absolute_system_directory_executable(tmp_path, monkeypatch):
    system_dir = tmp_path / "Windows" / "System32"
    system_dir.mkdir(parents=True)
    (system_dir / "whoami.exe").write_bytes(b"test executable placeholder")
    calls = []
    monkeypatch.setattr(acceptance_signer, "_windows_system_directory", lambda: system_dir, raising=False)
    monkeypatch.setattr(
        acceptance_signer.subprocess,
        "run",
        lambda command, **kwargs: calls.append((command, kwargs))
        or SimpleNamespace(stdout=b'"domain\\user",S-1-5-21-1-2-3\r\n'),
    )

    assert acceptance_signer._current_user_sid() == "S-1-5-21-1-2-3"
    assert calls[0][0] == [str(system_dir / "whoami.exe"), "/user", "/fo", "csv", "/nh"]
    assert calls[0][1]["cwd"] == str(system_dir)


def test_windows_private_key_permission_restriction_replaces_the_dacl(tmp_path, monkeypatch):
    key_path = tmp_path / "native-acceptance.pem"
    key_path.write_bytes(b"key")

    class ApiFunction:
        def __init__(self, callback):
            self.callback = callback

        def __call__(self, *args):
            return self.callback(*args)

    calls = {}

    def convert_sddl(sddl, revision, descriptor_out, size_out):
        calls["sddl"] = sddl
        ctypes.cast(descriptor_out, ctypes.POINTER(ctypes.c_void_p)).contents.value = 0x1234
        return 1

    def get_dacl(descriptor, present_out, dacl_out, defaulted_out):
        calls["descriptor"] = descriptor.value
        ctypes.cast(present_out, ctypes.POINTER(ctypes.c_int)).contents.value = 1
        ctypes.cast(dacl_out, ctypes.POINTER(ctypes.c_void_p)).contents.value = 0x5678
        ctypes.cast(defaulted_out, ctypes.POINTER(ctypes.c_int)).contents.value = 0
        return 1

    def set_security(filename, object_type, security_info, owner, group, dacl, sacl):
        calls["set"] = (filename, object_type, security_info, owner, group, dacl.value, sacl)
        return 0

    def select_dll(name, **_kwargs):
        if name == "advapi32":
            return SimpleNamespace(
                ConvertStringSecurityDescriptorToSecurityDescriptorW=ApiFunction(convert_sddl),
                GetSecurityDescriptorDacl=ApiFunction(get_dacl),
                SetNamedSecurityInfoW=ApiFunction(set_security),
            )
        return SimpleNamespace(LocalFree=ApiFunction(lambda descriptor: 0))

    monkeypatch.setattr(acceptance_signer.os, "name", "nt")
    monkeypatch.setattr(acceptance_signer, "_current_user_sid", lambda: "S-1-5-21-1-2-3")
    monkeypatch.setattr(acceptance_signer.ctypes, "WinDLL", select_dll, raising=False)

    acceptance_signer._restrict_private_key_permissions(key_path)

    assert calls["sddl"] == "D:P(A;;FA;;;S-1-5-21-1-2-3)"
    assert calls["descriptor"] == 0x1234
    assert calls["set"] == (
        str(key_path),
        1,
        0x00000004 | 0x80000000,
        None,
        None,
        0x5678,
        None,
    )


def test_keygen_removes_new_key_after_unexpected_permission_setup_error(tmp_path, monkeypatch):
    root = tmp_path / "checkout"
    root.mkdir()
    private_key_path = tmp_path / "private" / "native-acceptance.pem"
    monkeypatch.setattr(
        acceptance_signer,
        "_restrict_private_key_permissions",
        lambda _: (_ for _ in ()).throw(RuntimeError("unexpected permission setup error")),
    )

    with pytest.raises(RuntimeError, match="unexpected permission setup error"):
        generate_key(private_key_path, root)

    assert not private_key_path.exists()


def test_signer_documented_direct_script_entrypoint_imports_from_clean_environment(tmp_path):
    root = Path(__file__).resolve().parents[1]
    script = root / "scripts/minecraft/sign_native_acceptance.py"
    environment = os.environ.copy()
    environment.pop("PYTHONPATH", None)

    result = subprocess.run(
        [sys.executable, str(script), "--help"],
        cwd=tmp_path,
        env=environment,
        capture_output=True,
        text=True,
        check=False,
    )

    assert result.returncode == 0, result.stderr
    assert "create and use an external ed25519 key" in result.stdout.lower()


def test_acceptance_gate_rejects_candidate_artifact_drift(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    artifact = tmp_path / "build/minecraft-26.3/server/paper-26.3-161.jar"
    artifact.write_bytes(b"changed after evidence capture")
    with pytest.raises(AcceptanceError, match="Paper bytes do not match|candidate artifact paper bytes"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_profile_that_is_still_candidate(tmp_path):
    record_path, record = make_fixture(tmp_path)
    profile_path = tmp_path / "tools/minecraft-26.3/profile.lock.json"
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    profile["status"] = "migration-candidate"
    write_json(profile_path, profile)
    record["profileLockSha256"] = digest(profile_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="statuses must be migration-accepted"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_server_plugin_lock_that_is_still_candidate(tmp_path):
    record_path, record = make_fixture(tmp_path)
    plugins_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    plugins["status"] = "runtime-candidates"
    plugins["nativeVerified"] = False
    write_json(plugins_path, plugins)
    record["serverPluginLockSha256"] = digest(plugins_path)
    write_json(record_path, record)

    with pytest.raises(AcceptanceError, match="statuses must be migration-accepted"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_plugin_not_marked_native_verified(tmp_path):
    record_path, record = make_fixture(tmp_path)
    plugins_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    plugins["modules"][0]["nativeVerified"] = False
    write_json(plugins_path, plugins)
    record["serverPluginLockSha256"] = digest(plugins_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="every installed server module"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_client_mods_not_marked_native_verified(tmp_path):
    record_path, record = make_fixture(tmp_path)
    client_mods_path = tmp_path / "tools/minecraft-26.3/client-mods.lock.json"
    client_mods = json.loads(client_mods_path.read_text(encoding="utf-8"))
    client_mods["nativeVerified"] = False
    write_json(client_mods_path, client_mods)
    record["clientModsLockSha256"] = digest(client_mods_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="client mods lock must be natively verified"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_changed_client_mods_lock(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    client_mods_path = tmp_path / "tools/minecraft-26.3/client-mods.lock.json"
    client_mods = json.loads(client_mods_path.read_text(encoding="utf-8"))
    client_mods["modules"].append({"project": "different-mod"})
    write_json(client_mods_path, client_mods)
    with pytest.raises(AcceptanceError, match="clientModsLockSha256"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_unverified_baseline_plugin(tmp_path):
    record_path, record = make_fixture(tmp_path)
    plugins_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    plugins["unchangedBaseline"][0]["candidate"]["nativeVerified"] = False
    write_json(plugins_path, plugins)
    record["serverPluginLockSha256"] = digest(plugins_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="installed baseline plugin is not marked nativeVerified=true"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_modified_log(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    (tmp_path / EVIDENCE / "server.log").write_text("edited\n", encoding="utf-8")
    with pytest.raises(AcceptanceError, match="server log SHA-256 mismatch"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_logs_without_actual_runtime_markers(tmp_path):
    record_path, record = make_fixture(tmp_path)
    mod_log = tmp_path / EVIDENCE / "copimineclient.log"
    mod_log.write_text("Fabric loaded\n", encoding="utf-8")
    record["client"]["logs"]["mod"] = evidence_entry(mod_log, tmp_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="missing runtime marker"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_screenshot_without_f2_provenance(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record["client"]["screenshots"][0]["captureMethod"] = "edited"
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="captureMethod=minecraft-f2"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_fabricated_invalid_png_even_with_matching_hash(tmp_path):
    record_path, record = make_fixture(tmp_path)
    screenshot = tmp_path / EVIDENCE / "model.png"
    screenshot.write_bytes(b"\x89PNG\r\n\x1a\nfixture")
    record["client"]["screenshots"][0]["sha256"] = digest(screenshot)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="valid, bounded PNG"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_incomplete_plugin_inventory(tmp_path):
    record_path, record = make_fixture(tmp_path)
    inventory_path = tmp_path / EVIDENCE / "server-plugins.json"
    write_json(inventory_path, {"plugins": [{"pluginName": "TestPlugin", "filename": "TestPlugin.jar", "sha256": "0" * 64}]})
    record["server"]["pluginInventory"] = evidence_entry(inventory_path, tmp_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="inventory differs from lock"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_inventory_that_differs_from_installer_receipt(tmp_path):
    record_path, record = make_fixture(tmp_path)
    receipt_path = tmp_path / EVIDENCE / "migration-plugin-receipt.json"
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    receipt["pluginInventory"][0]["sha256"] = "0" * 64
    write_json(receipt_path, receipt)
    record["server"]["installerReceipt"] = evidence_entry(receipt_path, tmp_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="differs from the installer receipt"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_missing_actual_runtime_plugin_jar(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    (tmp_path / "local-runtime/end-rift-server-26.3/plugins/TestPlugin.jar").unlink()
    with pytest.raises(AcceptanceError, match="actual isolated runtime plugin JAR filenames"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_modified_actual_runtime_plugin_jar(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    (tmp_path / "local-runtime/end-rift-server-26.3/plugins/TestPlugin.jar").write_bytes(b"modified runtime plugin")
    with pytest.raises(AcceptanceError, match="actual isolated runtime plugin bytes differ"):
        validate(tmp_path, record_path)


def test_acceptance_gate_validates_captured_plugin_bytes_when_runtime_tree_is_not_present(tmp_path):
    record_path, _ = make_fixture(tmp_path)
    import shutil

    shutil.rmtree(tmp_path / "local-runtime")
    validate(tmp_path, record_path)


def test_acceptance_gate_rejects_duplicate_module_identities(tmp_path):
    record_path, record = make_fixture(tmp_path)
    plugins_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    plugins["modules"].append(dict(plugins["modules"][0]))
    write_json(plugins_path, plugins)
    record["serverPluginLockSha256"] = digest(plugins_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="duplicate module pluginName"):
        validate(tmp_path, record_path)


def test_expected_plugin_inventory_uses_module_when_it_supersedes_old_baseline_identity():
    lock = {
        "modules": [{
            "pluginName": "TestPlugin", "filename": "TestPlugin-2.jar",
            "sha256": "a" * 64, "nativeVerified": True,
        }],
        "unchangedBaseline": [{
            "pluginName": "TestPlugin", "filename": "TestPlugin-1.jar",
            "sha256": "b" * 64, "nativeVerified": False,
            "candidate": {
                "filename": "TestPlugin-1.jar", "sha256": "c" * 64,
                "nativeVerified": False,
            },
        }],
    }

    expected = _expected_plugins(lock)

    assert expected["TestPlugin"]["source"] == "module"
    assert expected["TestPlugin"]["filename"] == "TestPlugin-2.jar"
    assert expected["TestPlugin"]["sha256"] == "a" * 64


def test_expected_plugin_inventory_uses_build_candidate_without_candidate_filename():
    candidate = {
        "buildArtifact": "build/minecraft-26.3/plugins/jars/BuiltPlugin-26.3.jar",
        "sha256": "c" * 64,
        "nativeVerified": True,
    }
    lock = {
        "modules": [{
            "pluginName": "ModulePlugin", "filename": "ModulePlugin.jar",
            "sha256": "a" * 64, "nativeVerified": True,
        }],
        "unchangedBaseline": [{
            "pluginName": "BuiltPlugin", "filename": "BuiltPlugin.jar",
            "sha256": "b" * 64, "nativeVerified": True,
            "candidate": candidate,
        }],
    }

    expected = _expected_plugins(lock)

    assert expected["BuiltPlugin"]["candidate"] == candidate
    assert expected["BuiltPlugin"]["filename"] == "BuiltPlugin.jar"
    assert expected["BuiltPlugin"]["sha256"] == "c" * 64


def test_expected_plugin_inventory_rejects_incomplete_selected_build_candidate():
    lock = {
        "modules": [{
            "pluginName": "ModulePlugin", "filename": "ModulePlugin.jar",
            "sha256": "a" * 64, "nativeVerified": True,
        }],
        "unchangedBaseline": [{
            "pluginName": "BuiltPlugin", "filename": "BuiltPlugin.jar",
            "sha256": "b" * 64, "nativeVerified": True,
            "candidate": {
                "buildArtifact": "build/minecraft-26.3/plugins/jars/BuiltPlugin-26.3.jar",
                "nativeVerified": True,
            },
        }],
    }

    with pytest.raises(AcceptanceError, match="installed baseline plugin lock is incomplete"):
        _expected_plugins(lock)


def test_acceptance_gate_accepts_url_sourced_baseline_candidate(tmp_path):
    record_path, _, _ = make_url_baseline_fixture(tmp_path)

    validate(tmp_path, record_path)


def test_acceptance_gate_rejects_modified_url_sourced_baseline_candidate(tmp_path):
    record_path, _, staged_candidate = make_url_baseline_fixture(tmp_path)
    staged_candidate.write_bytes(b"modified after URL candidate staging")

    with pytest.raises(AcceptanceError, match="staged baseline candidate bytes do not match lock: BaselinePlugin"):
        validate(tmp_path, record_path)


def test_acceptance_gate_uses_module_when_it_supersedes_built_baseline_candidate(tmp_path):
    record_path, record = make_fixture(tmp_path)
    plugins_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    plugins["unchangedBaseline"][0]["pluginName"] = plugins["modules"][0]["pluginName"]
    baseline_artifact = tmp_path / plugins["unchangedBaseline"][0]["candidate"]["buildArtifact"]
    baseline_artifact.write_bytes(b"obsolete candidate ignored by module precedence")
    write_json(plugins_path, plugins)
    record["serverPluginLockSha256"] = digest(plugins_path)

    evidence_dir = tmp_path / EVIDENCE
    inventory_path = evidence_dir / "server-plugins.json"
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    module_row = inventory["plugins"][0]
    write_json(inventory_path, {"plugins": [module_row]})

    receipt_path = evidence_dir / "migration-plugin-receipt.json"
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    receipt["installed"] = receipt["installed"][:1]
    receipt["pluginInventory"] = [module_row]
    write_json(receipt_path, receipt)

    snapshot_path = evidence_dir / "installed-plugins.zip"
    module_candidate = tmp_path / "build/minecraft-26.3/server-plugins/TestPlugin.jar"
    write_plugin_snapshot(snapshot_path, {"TestPlugin.jar": module_candidate.read_bytes()})

    (tmp_path / "local-runtime/end-rift-server-26.3/plugins/BaselinePlugin.jar").unlink()
    server_log = evidence_dir / "server.log"
    server_log.write_text(
        paper_server_startup_log([["TestPlugin"], ["TestPlugin"]]),
        encoding="utf-8",
    )
    record["server"]["loadedPlugins"] = ["TestPlugin"]
    record["server"]["pluginInventory"] = evidence_entry(inventory_path, tmp_path)
    record["server"]["installerReceipt"] = evidence_entry(receipt_path, tmp_path)
    record["server"]["pluginSnapshot"] = evidence_entry(snapshot_path, tmp_path)
    record["server"]["log"] = evidence_entry(server_log, tmp_path)
    write_json(record_path, record)

    validate(tmp_path, record_path)


def test_acceptance_gate_uses_source_build_hash_for_coreprotect_candidate(tmp_path):
    record_path, record = make_fixture(tmp_path)
    plugins_path = tmp_path / "tools/minecraft-26.3/server-plugins.lock.json"
    plugins = json.loads(plugins_path.read_text(encoding="utf-8"))
    module = plugins["modules"][0]
    module.update({
        "buildScript": "scripts/minecraft/BuildCoreProtectMigration.ps1",
        "buildArtifact": "build/minecraft-26.3/server-plugins/CoreProtect.jar",
        "buildReceipt": "build/minecraft-26.3/server-plugins/CoreProtect.jar.receipt.json",
        "sourceCommit": "4b1e9ab33496d1672946c3ad497c119a0bb08df3",
        "sha256": "1" * 64,
        "sha512": "2" * 128,
        "size": 1,
        "buildSha256": module["sha256"],
        "buildSha512": module["sha512"],
        "buildSize": module["size"],
        "pluginName": "CoreProtect",
        "filename": "CoreProtect.jar",
    })
    artifact = tmp_path / module["buildArtifact"]
    artifact.parent.mkdir(parents=True, exist_ok=True)
    artifact.write_bytes((tmp_path / "build/minecraft-26.3/server-plugins/TestPlugin.jar").read_bytes())
    write_json(artifact.with_name(artifact.name + ".receipt.json"), {
        "sourceCommit": module["sourceCommit"], "sha512": module["buildSha512"],
        "size": module["buildSize"], "nativeVerified": False,
    })
    plugins["unchangedBaseline"] = [row for row in plugins["unchangedBaseline"] if row["pluginName"] != "BaselinePlugin"]
    plugins["modules"][0]["nativeVerified"] = True
    write_json(plugins_path, plugins)

    record["serverPluginLockSha256"] = digest(plugins_path)
    server_log = tmp_path / EVIDENCE / "server.log"
    server_log.write_text(
        paper_server_startup_log([["CoreProtect"], ["CoreProtect"]]),
        encoding="utf-8",
    )
    record["server"]["log"] = evidence_entry(server_log, tmp_path)
    inventory_path = tmp_path / EVIDENCE / "server-plugins.json"
    receipt_path = tmp_path / EVIDENCE / "migration-plugin-receipt.json"
    snapshot_path = tmp_path / EVIDENCE / "installed-plugins.zip"
    core_row = {"pluginName": "CoreProtect", "filename": "CoreProtect.jar", "sha256": module["buildSha256"]}
    write_json(inventory_path, {"plugins": [core_row]})
    write_json(receipt_path, {
        "schemaVersion": 1, "minecraftVersion": "26.3", "nativeVerified": False,
        "installed": [{**core_row, "version": "1.0"}], "pluginInventory": [core_row],
    })
    runtime_plugins = tmp_path / "local-runtime/end-rift-server-26.3/plugins"
    (runtime_plugins / "TestPlugin.jar").unlink()
    (runtime_plugins / "CoreProtect.jar").write_bytes(artifact.read_bytes())
    (runtime_plugins / "BaselinePlugin.jar").unlink(missing_ok=True)
    write_plugin_snapshot(snapshot_path, {"CoreProtect.jar": artifact.read_bytes()})
    record["server"]["loadedPlugins"] = ["CoreProtect"]
    record["server"]["pluginInventory"] = evidence_entry(inventory_path, tmp_path)
    record["server"]["installerReceipt"] = evidence_entry(receipt_path, tmp_path)
    record["server"]["pluginSnapshot"] = evidence_entry(snapshot_path, tmp_path)
    write_json(record_path, record)

    validate(tmp_path, record_path)


def test_acceptance_gate_requires_matching_current_session_ack_after_hello(tmp_path):
    record_path, record = make_fixture(tmp_path)
    mod_log = tmp_path / EVIDENCE / "copimineclient.log"
    session_id = "6f893b31-e88d-4237-b02a-41c7fa8af5ef"
    mod_log.write_text(
        "CopiMineClient bootstrap finished\n"
        "Bridge hello sent: session=" + session_id + "\n"
        "Bridge handshake acknowledged: protocol=2, session=b4bfc0f3-fb42-450e-a6bd-98087b36b55c\n"
        "Shader activated through Iris: test profile\n",
        encoding="utf-8",
    )
    record["client"]["logs"]["mod"] = evidence_entry(mod_log, tmp_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="acknowledgement for the current hello session"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_matching_ack_that_precedes_hello(tmp_path):
    record_path, record = make_fixture(tmp_path)
    mod_log = tmp_path / EVIDENCE / "copimineclient.log"
    session_id = "6f893b31-e88d-4237-b02a-41c7fa8af5ef"
    mod_log.write_text(
        "CopiMineClient bootstrap finished\n"
        "Bridge handshake acknowledged: protocol=2, session=" + session_id + "\n"
        "Bridge hello sent: session=" + session_id + "\n"
        "Shader activated through Iris: test profile\n",
        encoding="utf-8",
    )
    record["client"]["logs"]["mod"] = evidence_entry(mod_log, tmp_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="acknowledgement for the current hello session"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_stale_ack_before_later_unacknowledged_session(tmp_path):
    record_path, record = make_fixture(tmp_path)
    mod_log = tmp_path / EVIDENCE / "copimineclient.log"
    first_session = "6f893b31-e88d-4237-b02a-41c7fa8af5ef"
    second_session = "b4bfc0f3-fb42-450e-a6bd-98087b36b55c"
    mod_log.write_text(
        "CopiMineClient bootstrap finished\n"
        "Bridge hello sent: session=" + first_session + "\n"
        "Bridge handshake acknowledged: protocol=2, session=" + first_session + "\n"
        "Bridge hello sent: session=" + second_session + "\n"
        "Shader activated through Iris: test profile\n",
        encoding="utf-8",
    )
    record["client"]["logs"]["mod"] = evidence_entry(mod_log, tmp_path)
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="acknowledgement for the current hello session"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_visual_check_without_its_screenshot(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record["client"]["visualChecks"]["customModel"]["screenshotId"] = "missing"
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="must reference a verified F2 screenshot"):
        validate(tmp_path, record_path)


def test_acceptance_gate_rejects_path_traversal(tmp_path):
    record_path, record = make_fixture(tmp_path)
    record["server"]["log"]["path"] = "../../outside.log"
    write_json(record_path, record)
    with pytest.raises(AcceptanceError, match="escapes the repository"):
        validate(tmp_path, record_path)
