#!/usr/bin/env python3
"""Contract test for one-origin local Launcher distribution and binding.

The test uses a disposable frontend root and SQLite data directory.  It must
prove that the same backend origin can serve the generated Launcher release
contract, installer feed files, and the binding API without touching the
production site or database.
"""

from __future__ import annotations

import importlib
import json
import os
import sys
import tempfile
from pathlib import Path

from fastapi.testclient import TestClient


ROOT = Path(__file__).resolve().parents[2]
ADMIN_ROOT = ROOT / "admin-web"
if str(ADMIN_ROOT) not in sys.path:
    sys.path.insert(0, str(ADMIN_ROOT))


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="copimine-launcher-distribution-") as temporary:
        root = Path(temporary)
        frontend = root / "frontend"
        stable = frontend / "launcher" / "stable"
        launcher_files = frontend / "launcher" / "files"
        launcher_downloads = frontend / "downloads" / "launcher"
        metadata_dir = frontend / "assets" / "public-data" / "launcher"
        for directory in (stable, launcher_files, launcher_downloads, metadata_dir):
            directory.mkdir(parents=True, exist_ok=True)

        managed = b"managed-launcher-artifact"
        digest = __import__("hashlib").sha256(managed).hexdigest()
        (launcher_files / digest).write_bytes(managed)
        (stable / "instance-manifest.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 2,
                    "channel": "stable",
                    "releaseId": "local-contract",
                    "releaseSequence": 1,
                    "publicKeyId": "test",
                    "files": [],
                }
            ),
            encoding="utf-8",
        )
        (stable / "instance-manifest.sig").write_bytes(b"signature")
        installer = launcher_downloads / "CopiMineLauncherFolderSetup-1.0.3.exe"
        installer.write_bytes(b"MZ" + b"folder-installer")
        for name, payload in (
            ("RELEASES", b"release-feed"),
            ("releases.win.json", b"{}"),
            ("assets.win.json", b"[]"),
        ):
            (launcher_downloads / name).write_bytes(payload)
        (metadata_dir / "latest.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "channel": "stable",
                    "version": "1.0.3",
                    "filename": installer.name,
                    "downloadUrl": f"/downloads/launcher/{installer.name}",
                    "sizeBytes": installer.stat().st_size,
                    "sha256": __import__("hashlib").sha256(installer.read_bytes()).hexdigest(),
                }
            ),
            encoding="utf-8",
        )
        source_manifest = root / "source-manifest.json"
        source_manifest.write_text((stable / "instance-manifest.json").read_text(encoding="utf-8"), encoding="utf-8")

        values = {
            "COPIMINE_FRONTEND_ROOT": str(frontend),
            "COPIMINE_LAUNCHER_PUBLIC_ROOT": str(frontend),
            "COPIMINE_LAUNCHER_SOURCE_MANIFEST": str(source_manifest),
            "COPIMINE_ADMIN_DATA": str(root / "admin-data"),
            "COPIMINE_AUTH_STORAGE": "sqlite",
            "COPIMINE_AUTH_DB": str(root / "admin-data" / "auth.sqlite3"),
            "DATABASE_URL": "sqlite:///" + str(root / "admin-data" / "auth.sqlite3").replace("\\", "/"),
            "COPIMINE_STARTUP_STRICT": "0",
            "SECRET_KEY": "local-launcher-distribution-contract-secret-0123456789",
            "ADMIN_PUBLIC_BASE_URL": "http://127.0.0.1:8090",
            "ALLOW_INSECURE_HTTP_AUTH": "1",
            "MC_SERVER_DIR": str(root / "server"),
            "MC_WORLD_DIR": str(root / "server" / "world"),
            "MC_LOG_FILE": str(root / "server" / "logs" / "latest.log"),
        }
        for key, value in values.items():
            os.environ[key] = value

        (root / "server" / "world").mkdir(parents=True, exist_ok=True)
        (root / "server" / "logs").mkdir(parents=True, exist_ok=True)
        (root / "server" / "logs" / "latest.log").write_text("local staging\n", encoding="utf-8")

        sys.modules.pop("backend.main", None)
        module = importlib.import_module("backend.main")
        with TestClient(module.app, base_url="http://127.0.0.1:8090") as client:
            manifest_response = client.get("/launcher/stable/instance-manifest.json")
            assert manifest_response.status_code == 200, manifest_response.text
            assert manifest_response.json()["releaseId"] == "local-contract"

            installer_response = client.head(f"/downloads/launcher/{installer.name}")
            assert installer_response.status_code == 200, installer_response.text
            assert int(installer_response.headers["content-length"]) == installer.stat().st_size

            release_feed = client.get("/downloads/launcher/RELEASES")
            assert release_feed.status_code == 200, release_feed.text

            public_launcher = client.get("/api/public/launcher")
            assert public_launcher.status_code == 200, public_launcher.text
            assert public_launcher.json()["data"]["installer"]["filename"] == installer.name

            challenge = client.post(
                "/api/launcher/link/challenge",
                json={
                    "device_id": "contract-device-20260824",
                    "minecraft_name": "ContractPlayer",
                    "launcher_version": "1.0.3",
                },
            )
            assert challenge.status_code == 200, challenge.text
            assert challenge.json().get("ok") is True

    print("Launcher distribution contract PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
