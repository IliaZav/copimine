"""Exercise the Launcher control plane entirely inside disposable local paths."""

from __future__ import annotations

import base64
import hashlib
import importlib
import json
import os
import sys
import tempfile
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient


ROOT = Path(__file__).resolve().parents[2]
ADMIN_ROOT = ROOT / "admin-web"
if str(ADMIN_ROOT) not in sys.path:
    sys.path.insert(0, str(ADMIN_ROOT))

SEED = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"


def build_source(root: Path) -> tuple[Path, bytes]:
    release = root / "source"
    files = release / "files"
    files.mkdir(parents=True)
    source_mod = b"source-mod"
    java = b"source-java"
    source_digest = hashlib.sha256(source_mod).hexdigest()
    java_digest = hashlib.sha256(java).hexdigest()
    (files / source_digest).write_bytes(source_mod)
    (files / java_digest).write_bytes(java)
    manifest = {
        "schemaVersion": 2,
        "channel": "stable",
        "releaseId": "2026.08.17.1",
        "publishedAtUtc": "2026-08-17T00:00:00Z",
        "minimumLauncherVersion": "1.0.0",
        "minecraft": {"version": "1.21.1", "fabricLoaderVersion": "0.19.3", "javaMajor": 21},
        "server": {"name": "CopiMine staging", "address": "127.0.0.1", "port": 25565, "acceptServerResourcePack": True},
        "files": [
            {
                "componentId": "source-mod",
                "path": "mods/SourceMod.jar",
                "url": f"https://copimine.ru/launcher/files/{source_digest}",
                "sha256": source_digest,
                "size": len(source_mod),
                "ownership": "MANAGED",
                "required": True,
                "kind": "mod",
                "version": "1.0.0",
                "installPolicy": "REPLACE",
            }
        ],
        "configPolicies": [],
        "newsUrl": "https://copimine.ru/news/staging.html",
        "releaseSequence": 1,
        "javaRuntime": {
            "provider": "Eclipse Adoptium",
            "buildId": "temurin-21",
            "platform": "windows-x64",
            "version": "21.0.10",
            "url": f"https://copimine.ru/launcher/files/{java_digest}",
            "sizeBytes": len(java),
            "sha256": java_digest,
        },
        "publicKeyId": "launcher-v1-staging",
    }
    path = release / "instance-manifest.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    return path, source_mod


def assert_disposable_environment(root: Path, values: dict[str, str]) -> None:
    if values.get("COPIMINE_AUTH_STORAGE") != "sqlite":
        raise RuntimeError("SMOKE_ENVIRONMENT_INVALID: auth storage must be sqlite")
    for key in ("COPIMINE_LAUNCHER_CONTROL_DIR", "COPIMINE_LAUNCHER_PUBLIC_ROOT", "COPIMINE_LAUNCHER_SOURCE_MANIFEST", "MC_SERVER_DIR", "MC_WORLD_DIR", "COPIMINE_ADMIN_DATA"):
        candidate = Path(values[key]).resolve()
        if root not in candidate.parents and candidate != root:
            raise RuntimeError(f"SMOKE_ENVIRONMENT_INVALID: {key} escaped disposable root")


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="copimine-launcher-smoke-") as temp:
        root = Path(temp).resolve()
        source_manifest, source_bytes = build_source(root)
        server = root / "server"
        world = server / "world"
        (world / "playerdata").mkdir(parents=True)
        (world / "stats").mkdir()
        (world / "advancements").mkdir()
        (server / "logs").mkdir(parents=True)
        (server / "logs" / "latest.log").write_text("[INFO] disposable launcher smoke\n", encoding="utf-8")
        values = {
            "COPIMINE_STARTUP_STRICT": "0",
            "SECRET_KEY": "s" * 64,
            "COPIMINE_AUTH_STORAGE": "sqlite",
            "COPIMINE_ADMIN_DATA": str(root / "admin-data"),
            "MC_SERVER_DIR": str(server),
            "MC_WORLD_DIR": str(world),
            "POSTGRES_PASSWORD": "",
            "ADMIN_PUBLIC_BASE_URL": "https://staging.invalid",
            "ALLOW_INSECURE_HTTP_AUTH": "0",
            "COPIMINE_LAUNCHER_CONTROL_DIR": str(root / "control"),
            "COPIMINE_LAUNCHER_PUBLIC_ROOT": str(root / "public"),
            "COPIMINE_LAUNCHER_SOURCE_MANIFEST": str(source_manifest),
            "COPIMINE_LAUNCHER_DOWNLOAD_ORIGIN": "https://copimine.ru/launcher/files",
            "COPIMINE_MANIFEST_PRIVATE_KEY_HEX": SEED,
        }
        assert_disposable_environment(root, values)
        with patch.dict(os.environ, values, clear=False):
            sys.modules.pop("backend.main", None)
            main_module = importlib.import_module("backend.main")
            client = TestClient(main_module.app)
            main_module.app.dependency_overrides[main_module.require_admin] = lambda: "staging-admin"
            try:
                csrf_response = client.get("/api/auth/csrf")
                csrf_response.raise_for_status()
                csrf = client.cookies.get("cm_csrf")
                headers = {"X-CSRF-Token": csrf or "", "X-Copimine-Confirm": "LAUNCHER_MOD_UPLOAD"}

                upload = client.post(
                    "/api/admin/launcher/mods/upload",
                    data={"component_id": "smoke-mod", "version": "1.0.0", "filename": "SmokeMod.jar", "required": "true"},
                    files={"file": ("SmokeMod.jar", b"smoke-mod")},
                    headers=headers,
                )
                upload.raise_for_status()
                uploaded = upload.json()["mod"]

                validation = client.post(
                    "/api/admin/launcher/release/validate?publicKeyId=launcher-v1-staging",
                    headers={"X-CSRF-Token": csrf or ""},
                )
                validation.raise_for_status()
                if not validation.json().get("ok"):
                    raise RuntimeError(f"release validation failed: {validation.text}")

                publish_headers = {"X-CSRF-Token": csrf or "", "X-Copimine-Confirm": "LAUNCHER_RELEASE_PUBLISH"}
                published = client.post(
                    "/api/admin/launcher/release/publish",
                    json={"publicKeyId": "launcher-v1-staging", "releaseId": "2026.08.17.2", "releaseSequence": 2},
                    headers=publish_headers,
                )
                published.raise_for_status()
                release = published.json()["release"]

                public = client.get("/api/public/launcher")
                public.raise_for_status()
                if public.json()["data"]["currentRelease"]["releaseId"] != release["releaseId"]:
                    raise RuntimeError("public launcher pointer did not advance")
                manifest_response = client.get("/launcher/stable/instance-manifest.json")
                signature_response = client.get("/launcher/stable/instance-manifest.sig")
                manifest_response.raise_for_status()
                signature_response.raise_for_status()
                downloaded = client.get(f"/launcher/files/{uploaded['sha256']}")
                downloaded.raise_for_status()
                if hashlib.sha256(downloaded.content).hexdigest() != uploaded["sha256"]:
                    raise RuntimeError("downloaded managed mod hash mismatch")

                news_payload = {
                    "slug": "staging-release",
                    "title": "Staging release",
                    "version": "1.0.0",
                    "publishedAt": "2026-08-17T00:00:00Z",
                    "summary": ["Signed release published"],
                    "sections": {"general": ["Launcher feed updated"], "technical": ["SHA-256 checked"], "bugfixes": []},
                    "items": [{"itemId": "copimine:token", "displayName": "Token", "iconUrl": "/assets/patch-items/token.png", "changes": ["Texture preserved"]}],
                }
                news_headers = {"X-CSRF-Token": csrf or "", "X-Copimine-Confirm": "LAUNCHER_NEWS_SAVE"}
                saved_news = client.put("/api/admin/launcher/news/staging-release", json=news_payload, headers=news_headers)
                saved_news.raise_for_status()
                published_news = client.post(
                    "/api/admin/launcher/news/staging-release/publish",
                    headers={"X-CSRF-Token": csrf or "", "X-Copimine-Confirm": "LAUNCHER_NEWS_PUBLISH"},
                )
                published_news.raise_for_status()
                news = client.get("/api/public/news/staging-release")
                news.raise_for_status()
                if news.json()["news"]["items"][0]["iconUrl"] != "/assets/patch-items/token.png":
                    raise RuntimeError("item texture contract was not preserved")

                telemetry = client.post("/api/launcher/telemetry", json={"event": "launch", "launcherVersion": "1.0.0", "manifestSequence": 2})
                telemetry.raise_for_status()
                stats = client.get("/api/admin/launcher/stats")
                stats.raise_for_status()
                if stats.json().get("telemetryLines", 0) < 1:
                    raise RuntimeError("telemetry was not counted")

                rolled = client.post(
                    "/api/admin/launcher/release/rollback",
                    json={"releaseId": "2026.08.17.2"},
                    headers={"X-CSRF-Token": csrf or "", "X-Copimine-Confirm": "LAUNCHER_RELEASE_ROLLBACK"},
                )
                rolled.raise_for_status()
                rollback = rolled.json()["release"]
                if rollback.get("rollbackOf") != "2026.08.17.2":
                    raise RuntimeError("rollback did not create a monotonic rollback release")

                print(json.dumps({
                    "ok": True,
                    "releaseId": release["releaseId"],
                    "rollbackReleaseId": rollback["releaseId"],
                    "manifestSha256": hashlib.sha256(manifest_response.content).hexdigest(),
                    "signatureBytes": len(signature_response.content),
                    "managedModSha256": uploaded["sha256"],
                    "sourceFixtureBytes": len(source_bytes),
                    "newsSlug": "staging-release",
                    "telemetry": "counted",
                }, ensure_ascii=False))
                return 0
            finally:
                main_module.app.dependency_overrides.pop(main_module.require_admin, None)


if __name__ == "__main__":
    raise SystemExit(main())
