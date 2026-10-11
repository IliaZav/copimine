"""Native evidence captures byte-for-byte plugin JARs from the isolated runtime."""

import hashlib
import importlib.util
import io
import json
from pathlib import Path
import zipfile

import pytest

from scripts.minecraft.install_migration_server_plugins import capture_plugin_inventory


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = Path("artifacts/minecraft-26.3/native-acceptance")


def load_capture():
    path = ROOT / "scripts/minecraft/capture_native_plugin_snapshot.py"
    spec = importlib.util.spec_from_file_location("native_plugin_snapshot", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def digest(data: bytes, algorithm: str = "sha256") -> str:
    return hashlib.new(algorithm, data).hexdigest()


def plugin_jar(name: str, version: str) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("plugin.yml", f"name: {name}\nversion: {version}\n")
    return buffer.getvalue()


def make_runtime(root: Path, *, accepted: bool = True):
    plugins_dir = root / "local-runtime/end-rift-server-26.3/plugins"
    plugins_dir.mkdir(parents=True)
    module_bytes = plugin_jar("CandidatePlugin", "1.0")
    baseline_bytes = plugin_jar("BaselinePlugin", "2.0")
    authme_bytes = plugin_jar("AuthMe", "6.0.1")
    (plugins_dir / "CandidatePlugin.jar").write_bytes(module_bytes)
    (plugins_dir / "BaselinePlugin.jar").write_bytes(baseline_bytes)
    (plugins_dir / "AuthMe.jar").write_bytes(authme_bytes)
    inventory = capture_plugin_inventory(plugins_dir)
    by_name = {row["pluginName"]: row for row in inventory}
    candidate = by_name["CandidatePlugin"]
    authme = by_name["AuthMe"]
    native_verified = accepted
    lock = {
        "status": "migration-accepted" if accepted else "runtime-candidates",
        "nativeVerified": native_verified,
        "minecraftVersion": "26.3",
        "modules": [
            {
                **candidate,
                "size": len(module_bytes),
                "filename": "CandidatePlugin.jar",
                "nativeVerified": native_verified,
            },
            {
                **authme,
                "size": len(authme_bytes),
                "filename": "AuthMe.jar",
                "nativeVerified": native_verified,
            },
        ],
        "unchangedBaseline": [{
            "pluginName": "BaselinePlugin",
            "filename": "BaselinePlugin.jar",
            "sha256": digest(baseline_bytes),
            "nativeVerified": native_verified,
        }],
    }
    lock_path = root / "tools/minecraft-26.3/server-plugins.lock.json"
    lock_path.parent.mkdir(parents=True)
    lock_path.write_text(json.dumps(lock), encoding="utf-8")
    receipt = {
        "schemaVersion": 1,
        "minecraftVersion": "26.3",
        "nativeVerified": False,
        "installed": [
            {**candidate, "version": "1.0"},
            {**authme, "version": "6.0.1"},
        ],
        "pluginInventory": inventory,
    }
    (plugins_dir.parent / "migration-plugin-receipt.json").write_text(json.dumps(receipt), encoding="utf-8")
    return plugins_dir, module_bytes, baseline_bytes


def test_capture_copies_actual_installed_plugins_receipt_and_inventory(tmp_path):
    module = load_capture()
    plugins_dir, module_bytes, baseline_bytes = make_runtime(tmp_path)
    authme_bytes = (plugins_dir / "AuthMe.jar").read_bytes()

    entries = module.capture(tmp_path)

    assert set(entries) == {"pluginInventory", "installerReceipt", "pluginSnapshot"}
    snapshot_path = tmp_path / entries["pluginSnapshot"]["path"]
    assert entries["pluginSnapshot"]["sha256"] == digest(snapshot_path.read_bytes())
    with zipfile.ZipFile(snapshot_path) as snapshot:
        assert snapshot.read("CandidatePlugin.jar") == module_bytes
        assert snapshot.read("BaselinePlugin.jar") == baseline_bytes
        assert snapshot.read("AuthMe.jar") == authme_bytes
    inventory_path = tmp_path / entries["pluginInventory"]["path"]
    assert len(json.loads(inventory_path.read_text(encoding="utf-8"))["plugins"]) == 3
    receipt_path = tmp_path / entries["installerReceipt"]["path"]
    assert json.loads(receipt_path.read_text(encoding="utf-8"))["minecraftVersion"] == "26.3"


def test_capture_candidate_snapshot_does_not_promote_candidate_lock(tmp_path):
    module = load_capture()
    make_runtime(tmp_path, accepted=False)

    entries = module.capture(tmp_path, candidate_mode=True)

    lock = json.loads((tmp_path / "tools/minecraft-26.3/server-plugins.lock.json").read_text(encoding="utf-8"))
    assert lock["status"] == "runtime-candidates"
    assert lock["nativeVerified"] is False
    assert all(item["nativeVerified"] is False for item in lock["modules"])
    inventory = json.loads((tmp_path / entries["pluginInventory"]["path"]).read_text(encoding="utf-8"))
    assert {row["pluginName"] for row in inventory["plugins"]} == {
        "AuthMe", "BaselinePlugin", "CandidatePlugin"
    }
    assert entries["pluginSnapshot"]["path"].startswith("build/minecraft-26.3/native-plugin-candidate/")
    assert not (tmp_path / EVIDENCE).exists()


def test_candidate_capture_does_not_overwrite_signed_acceptance_files(tmp_path):
    module = load_capture()
    make_runtime(tmp_path, accepted=False)
    evidence_dir = tmp_path / EVIDENCE
    evidence_dir.mkdir(parents=True)
    record = evidence_dir / "acceptance.json"
    signature = evidence_dir / "acceptance.sig"
    record.write_bytes(b"existing signed record")
    signature.write_bytes(b"existing detached signature")

    module.capture(tmp_path, candidate_mode=True)

    assert record.read_bytes() == b"existing signed record"
    assert signature.read_bytes() == b"existing detached signature"


def test_final_capture_rejects_candidate_lock_until_native_verification(tmp_path):
    module = load_capture()
    make_runtime(tmp_path, accepted=False)

    with pytest.raises(module.AcceptanceError, match="server plugin lock must be natively accepted"):
        module.capture(tmp_path)


def test_candidate_capture_rejects_lock_already_promoted_to_accepted(tmp_path):
    module = load_capture()
    make_runtime(tmp_path, accepted=True)

    with pytest.raises(module.AcceptanceError, match="candidate snapshot mode requires"):
        module.capture(tmp_path, candidate_mode=True)


def test_candidate_capture_rejects_authme_free_runtime_against_full_lock(tmp_path):
    module = load_capture()
    plugins_dir, _, _ = make_runtime(tmp_path, accepted=False)
    (plugins_dir / "AuthMe.jar").unlink()

    with pytest.raises(module.AcceptanceError, match="actual runtime plugin identities differ"):
        module.capture(tmp_path, candidate_mode=True)


def test_capture_rejects_runtime_plugin_bytes_that_changed_after_install(tmp_path):
    module = load_capture()
    plugins_dir, _, _ = make_runtime(tmp_path)
    (plugins_dir / "CandidatePlugin.jar").write_bytes(plugin_jar("CandidatePlugin", "changed"))

    with pytest.raises(module.AcceptanceError, match="actual runtime plugin bytes differ"):
        module.capture(tmp_path)


def test_capture_rejects_plugin_bytes_that_change_before_snapshot_copy(tmp_path, monkeypatch):
    module = load_capture()
    plugins_dir, _, _ = make_runtime(tmp_path)
    jar_path = plugins_dir / "CandidatePlugin.jar"
    original_atomic_write = module._atomic_write

    def mutate_before_snapshot(destination, writer):
        if destination.name == "installed-plugins.zip":
            jar_path.write_bytes(plugin_jar("CandidatePlugin", "changed after inventory"))
        return original_atomic_write(destination, writer)

    monkeypatch.setattr(module, "_atomic_write", mutate_before_snapshot)

    with pytest.raises(module.AcceptanceError, match="copied plugin JAR bytes differ from inventory: CandidatePlugin"):
        module.capture(tmp_path)

    snapshot_path = tmp_path / EVIDENCE / "installed-plugins.zip"
    assert not snapshot_path.exists()
    assert not list(snapshot_path.parent.glob("installed-plugins.zip.*.candidate"))


def test_capture_copies_the_exact_receipt_bytes_used_for_validation(tmp_path, monkeypatch):
    module = load_capture()
    plugins_dir, _, _ = make_runtime(tmp_path)
    receipt_path = plugins_dir.parent / "migration-plugin-receipt.json"
    validated_bytes = receipt_path.read_bytes()
    original_atomic_write = module._atomic_write

    def mutate_before_receipt_copy(destination, writer):
        if destination.name == "migration-plugin-receipt.json":
            changed_receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
            changed_receipt["changedAfterValidation"] = True
            receipt_path.write_text(json.dumps(changed_receipt), encoding="utf-8")
        return original_atomic_write(destination, writer)

    monkeypatch.setattr(module, "_atomic_write", mutate_before_receipt_copy)

    entries = module.capture(tmp_path)

    copied_path = tmp_path / entries["installerReceipt"]["path"]
    assert copied_path.read_bytes() == validated_bytes
    assert receipt_path.read_bytes() != validated_bytes


def test_capture_rejects_duplicate_replacement_rows_in_installer_receipt(tmp_path):
    module = load_capture()
    plugins_dir, _, _ = make_runtime(tmp_path)
    receipt_path = plugins_dir.parent / "migration-plugin-receipt.json"
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    receipt["installed"].append(dict(receipt["installed"][0]))
    receipt_path.write_text(json.dumps(receipt), encoding="utf-8")

    with pytest.raises(module.AcceptanceError, match="duplicate plugin names or filenames"):
        module.capture(tmp_path)


def test_capture_rejects_missing_runtime_plugins_directory(tmp_path):
    module = load_capture()
    root = tmp_path.resolve()
    plugins_dir, _, _ = make_runtime(root)
    for jar in plugins_dir.glob("*.jar"):
        jar.unlink()

    with pytest.raises(module.AcceptanceError, match="actual runtime plugin identities differ"):
        module.capture(root)
