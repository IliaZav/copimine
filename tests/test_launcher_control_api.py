from __future__ import annotations

import hashlib
import importlib
import json
import sys
from pathlib import Path

from fastapi.testclient import TestClient


ROOT = Path(__file__).resolve().parents[1]
ADMIN_ROOT = ROOT / "admin-web"
if str(ADMIN_ROOT) not in sys.path:
    sys.path.insert(0, str(ADMIN_ROOT))


def load_main(monkeypatch, tmp_path: Path):
    server = tmp_path / "server"
    world = server / "world"
    (world / "playerdata").mkdir(parents=True)
    (world / "stats").mkdir()
    (world / "advancements").mkdir()
    (server / "logs").mkdir(parents=True)
    (server / "logs" / "latest.log").write_text("[INFO] staging\n", encoding="utf-8")
    for name, value in {
        "COPIMINE_STARTUP_STRICT": "0",
        "SECRET_KEY": "s" * 64,
        "COPIMINE_AUTH_STORAGE": "sqlite",
        "COPIMINE_ADMIN_DATA": str(tmp_path / "admin-data"),
        "MC_SERVER_DIR": str(server),
        "MC_WORLD_DIR": str(world),
        "POSTGRES_PASSWORD": "",
        "ADMIN_PUBLIC_BASE_URL": "https://testserver",
        "ALLOW_INSECURE_HTTP_AUTH": "0",
        "COPIMINE_LAUNCHER_CONTROL_DIR": str(tmp_path / "control"),
        "COPIMINE_LAUNCHER_PUBLIC_ROOT": str(tmp_path / "public"),
        "COPIMINE_MANIFEST_PRIVATE_KEY_HEX": "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
    }.items():
        monkeypatch.setenv(name, value)
    sys.modules.pop("backend.main", None)
    return importlib.import_module("backend.main")


def complete_source_manifest(tmp_path: Path) -> Path:
    release = tmp_path / "complete-source"
    (release / "files").mkdir(parents=True)
    mod_bytes = b"complete-mod"
    java_bytes = b"complete-java"
    mod_digest = hashlib.sha256(mod_bytes).hexdigest()
    java_digest = hashlib.sha256(java_bytes).hexdigest()
    (release / "files" / mod_digest).write_bytes(mod_bytes)
    (release / "files" / java_digest).write_bytes(java_bytes)
    document = {
        "schemaVersion": 2,
        "channel": "stable",
        "releaseId": "2026.08.17.1",
        "publishedAtUtc": "2026-08-17T08:00:00Z",
        "minimumLauncherVersion": "1.0.0",
        "minecraft": {"version": "1.21.1", "fabricLoaderVersion": "0.19.3", "javaMajor": 21},
        "server": {"name": "CopiMine", "address": "mc.copimine.ru", "acceptServerResourcePack": True, "port": 25565},
        "files": [{
            "componentId": "seeded-client", "path": "mods/Seeded.jar", "url": f"https://copimine.ru/launcher/files/{mod_digest}",
            "sha256": mod_digest, "size": len(mod_bytes), "ownership": "MANAGED", "required": True,
            "kind": "mod", "version": "1.0.0", "installPolicy": "REPLACE",
        }],
        "configPolicies": [],
        "newsUrl": "https://copimine.ru/news/staging.html",
        "releaseSequence": 1,
        "javaRuntime": {
            "provider": "Eclipse Adoptium", "buildId": "temurin-21", "platform": "windows-x64", "version": "21.0.10",
            "url": f"https://copimine.ru/launcher/files/{java_digest}", "sizeBytes": len(java_bytes), "sha256": java_digest,
        },
        "publicKeyId": "launcher-v1-staging",
    }
    path = release / "instance-manifest.json"
    path.write_text(json.dumps(document), encoding="utf-8")
    return path


def test_launcher_api_routes_are_exposed_with_auth_and_public_split(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)
    paths = {route.path for route in main.app.routes}
    assert "/api/admin/launcher" in paths
    assert "/api/admin/launcher/mods/upload" in paths
    assert "/api/admin/launcher/release/publish" in paths
    assert "/api/admin/launcher/news/{slug}" in paths
    assert "/api/public/launcher" in paths
    assert "/api/public/news/{slug}" in paths
    assert "/api/launcher/telemetry" in paths
    assert "/launcher/files/{sha256}" in paths
    assert "/launcher/stable/{filename}" in paths


def test_public_feed_file_and_telemetry_work_without_database(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)
    from backend.launcher_control import ControlPlane

    plane = ControlPlane(tmp_path / "control", source_manifest=complete_source_manifest(tmp_path), public_root=tmp_path / "public")
    plane.load_state()
    payload = b"staging mod"
    item = plane.add_mod("staging-mod", "1.0.0", "StagingMod.jar", payload)
    plane.publish_release(
        private_key_hex="9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
        public_key_id="launcher-v1-staging",
            release_id="2026.08.17.5",
            release_sequence=5,
    )
    with TestClient(main.app) as client:
        launcher = client.get("/api/public/launcher")
        assert launcher.status_code == 200, launcher.text
        assert launcher.json()["data"]["currentRelease"]["releaseId"] == "2026.08.17.5"
        assert client.get("/launcher/stable/instance-manifest.json").status_code == 200
        assert client.get("/launcher/stable/instance-manifest.sig").status_code == 200
        file_response = client.get(f"/launcher/files/{item['sha256']}")
        assert file_response.status_code == 200
        assert file_response.content == payload
        telemetry = client.post(
            "/api/launcher/telemetry",
            json={"event": "launch", "launcherVersion": "1.0.0", "manifestSequence": 5},
        )
        assert telemetry.status_code == 200, telemetry.text
        assert telemetry.json()["ok"] is True


def test_admin_launcher_mutation_requires_auth_csrf_and_confirmation(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)
    from backend.launcher_control import ControlPlane

    ControlPlane(tmp_path / "control", public_root=tmp_path / "public").load_state()
    with TestClient(main.app) as client:
        unauth = client.get("/api/admin/launcher")
        assert unauth.status_code == 401

        main.app.dependency_overrides[main.require_admin] = lambda: "staging-admin"
        try:
            csrf = client.get("/api/auth/csrf")
            assert csrf.status_code == 200
            token = client.cookies.get("cm_csrf")
            form = {"component_id": "demo-mod", "version": "1.0.0", "filename": "Demo.jar"}
            missing_csrf = client.post("/api/admin/launcher/mods/upload", data=form, files={"file": ("Demo.jar", b"demo")}, headers={"X-Copimine-Confirm": "LAUNCHER_MOD_UPLOAD"})
            assert missing_csrf.status_code == 403
            saved = client.post(
                "/api/admin/launcher/mods/upload",
                data=form,
                files={"file": ("Demo.jar", b"demo")},
                headers={"X-CSRF-Token": token, "X-Copimine-Confirm": "LAUNCHER_MOD_UPLOAD"},
            )
            assert saved.status_code == 200, saved.text
            assert saved.json()["mod"]["sha256"] == hashlib.sha256(b"demo").hexdigest()
        finally:
            main.app.dependency_overrides.clear()


def test_admin_news_editor_publishes_structured_item_aware_contract(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)
    with TestClient(main.app) as client:
        main.app.dependency_overrides[main.require_admin] = lambda: "staging-admin"
        try:
            client.get("/api/auth/csrf")
            token = client.cookies.get("cm_csrf")
            headers = {"X-CSRF-Token": token, "X-Copimine-Confirm": "LAUNCHER_NEWS_SAVE"}
            payload = {
                "slug": "patch-1-0-1",
                "title": "Patch <1.0.1>",
                "version": "1.0.1",
                "publishedAt": "",
                "summary": ["Stable update"],
                "sections": {"general": ["New content"], "technical": [], "bugfixes": ["Fixed lag"]},
                "items": [{"itemId": "copimine:token", "displayName": "Token", "iconUrl": "/assets/patch-items/token.png", "changes": ["Updated"]}],
            }
            saved = client.put("/api/admin/launcher/news/patch-1-0-1", json=payload, headers=headers)
            assert saved.status_code == 200, saved.text
            assert "<" not in saved.json()["news"]["title"]
            published = client.post(
                "/api/admin/launcher/news/patch-1-0-1/publish",
                headers={"X-CSRF-Token": token, "X-Copimine-Confirm": "LAUNCHER_NEWS_PUBLISH"},
            )
            assert published.status_code == 200, published.text
            public = client.get("/api/public/news")
            assert public.status_code == 200
            assert public.json()["news"][0]["items"][0]["iconUrl"] == "/assets/patch-items/token.png"
        finally:
            main.app.dependency_overrides.clear()
