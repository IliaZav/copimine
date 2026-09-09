from __future__ import annotations

import base64
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PUBLISHER = ROOT / "tools/publish_instance_manifest.py"


def publisher_python() -> str:
    configured = os.environ.get("COPIMINE_PUBLISHER_PYTHON")
    if configured:
        return configured
    bundled = ROOT.parents[2] / ".venvs/copimine-launcher-v1/Scripts/python.exe"
    return str(bundled) if bundled.is_file() else sys.executable


def test_publisher_builds_real_files_and_detached_signature(tmp_path: Path) -> None:
    (tmp_path / "thirdparty/client-mods").mkdir(parents=True)
    (tmp_path / "thirdparty/modpack_manifest.json").write_text(
        json.dumps(
            {
                "schemaVersion": 2,
                "minecraftVersion": "1.21.1",
                "loader": "Fabric",
                "files": [
                    {
                        "path": "mods/CopiMineClient-1.0.0.jar",
                        "component": "CopiMineClient",
                        "version": "1.0.0",
                        "required": True,
                    }
                ],
            }
        ),
        encoding="utf-8",
    )
    mod = tmp_path / "thirdparty/client-mods/CopiMineClient-1.0.0.jar"
    mod.write_bytes(b"client")
    java = tmp_path / "java.zip"
    java.write_bytes(b"java archive")
    runtime = tmp_path / "minecraft-runtime.zip"
    runtime.write_bytes(b"minecraft runtime archive")
    output = tmp_path / "release"
    seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"

    result = subprocess.run(
        [
            publisher_python(),
            str(PUBLISHER),
            "--repo-root",
            str(tmp_path),
            "--output-dir",
            str(output),
            "--private-key-hex",
            seed,
            "--public-key-id",
            "launcher-v1-staging",
            "--instance-version",
            "2026.08.15.1",
            "--release-sequence",
            "1",
            "--published-at",
            "2026-08-15T10:00:00Z",
            "--java-archive",
            str(java),
            "--minecraft-runtime-archive",
            str(runtime),
        ],
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    manifest_bytes = (output / "instance-manifest.json").read_bytes()
    document = json.loads(manifest_bytes)
    signature = json.loads((output / "instance-manifest.sig").read_text(encoding="utf-8"))
    public_key = bytes.fromhex((output / "public-key.hex").read_text(encoding="ascii"))
    assert len(public_key) == 32
    assert len(base64.b64decode(signature["signatureBase64"])) == 64
    entry = document["files"][0]
    assert document["schemaVersion"] == 2
    assert document["releaseId"] == "2026.08.15.1"
    assert document["publishedAtUtc"] == "2026-08-15T10:00:00Z"
    assert document["minecraft"]["fabricLoaderVersion"] == "0.19.3"
    assert "instanceVersion" not in document
    assert "publishedAt" not in document
    assert "fabricLoader" not in document["minecraft"]
    assert entry["componentId"] == "copimine-client"
    assert entry["sha256"] == hashlib.sha256(mod.read_bytes()).hexdigest()
    assert (output / "files" / entry["sha256"]).read_bytes() == mod.read_bytes()
    java_digest = document["javaRuntime"]["sha256"]
    assert (output / "files" / java_digest).read_bytes() == java.read_bytes()
    runtime_digest = document["minecraftRuntime"]["sha256"]
    assert (output / "files" / runtime_digest).read_bytes() == runtime.read_bytes()
    assert not list(output.glob("*.key"))
