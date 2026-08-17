from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "admin-web" / "backend"
import sys

if str(BACKEND) not in sys.path:
    sys.path.insert(0, str(BACKEND))

import launcher_control as control  # noqa: E402


def source_manifest(tmp_path: Path) -> Path:
    release = tmp_path / "source"
    (release / "files").mkdir(parents=True)
    client_bytes = b"seeded-client"
    digest = hashlib.sha256(client_bytes).hexdigest()
    (release / "files" / digest).write_bytes(client_bytes)
    path = release / "instance-manifest.json"
    path.write_text(
        json.dumps(
            {
                "schemaVersion": 2,
                "releaseId": "seed",
                "releaseSequence": 4,
                "minecraft": {"version": "1.21.1"},
                "server": {"address": "mc.example.test"},
                "files": [
                    {
                        "path": "mods/Seeded.jar",
                        "componentId": "seeded-client",
                        "version": "1.0.0",
                        "required": True,
                        "sha256": digest,
                        "sizeBytes": len(client_bytes),
                    },
                    {
                        "path": "config/copimine.json",
                        "sha256": "a" * 64,
                        "sizeBytes": 1,
                    },
                ],
            }
        ),
        encoding="utf-8",
    )
    return path


def complete_source_manifest(tmp_path: Path) -> Path:
    release = tmp_path / "complete-source"
    (release / "files").mkdir(parents=True)
    mod_bytes = b"complete-mod"
    java_bytes = b"complete-java"
    runtime_bytes = b"complete-minecraft-runtime"
    mod_digest = hashlib.sha256(mod_bytes).hexdigest()
    java_digest = hashlib.sha256(java_bytes).hexdigest()
    runtime_digest = hashlib.sha256(runtime_bytes).hexdigest()
    (release / "files" / mod_digest).write_bytes(mod_bytes)
    (release / "files" / java_digest).write_bytes(java_bytes)
    (release / "files" / runtime_digest).write_bytes(runtime_bytes)
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
        "minecraftRuntime": {
            "url": f"https://copimine.ru/launcher/files/{runtime_digest}", "sizeBytes": len(runtime_bytes), "sha256": runtime_digest,
        },
        "publicKeyId": "launcher-v1-staging",
    }
    path = release / "instance-manifest.json"
    path.write_text(json.dumps(document), encoding="utf-8")
    return path


def test_control_plane_seeds_manifest_and_preserves_non_mod_files(tmp_path: Path) -> None:
    plane = control.ControlPlane(tmp_path / "control", source_manifest=source_manifest(tmp_path))

    state = plane.load_state()
    assert state["baseManifest"]["server"]["address"] == "mc.example.test"
    assert [item["componentId"] for item in plane.list_mods()] == ["seeded-client"]
    draft = plane.manifest_for_draft()
    assert any(item["path"] == "config/copimine.json" for item in draft["files"])
    assert any(item["path"] == "mods/Seeded.jar" for item in draft["files"])


def test_mod_crud_hashes_server_bytes_and_rejects_duplicate_or_traversal(tmp_path: Path) -> None:
    plane = control.ControlPlane(tmp_path / "control")
    plane.load_state()
    payload = b"new-mod"
    added = plane.add_mod(
        component_id="map-helper",
        version="2.1.0",
        filename="MapHelper.jar",
        data=payload,
        required=False,
    )
    assert added["sha256"] == hashlib.sha256(payload).hexdigest()
    assert (tmp_path / "control" / "files" / added["sha256"]).read_bytes() == payload
    with pytest.raises(control.ControlPlaneError, match="LAUNCHER_MOD_DUPLICATE"):
        plane.add_mod("map-helper", "2.1.0", "MapHelper.jar", b"other")
    with pytest.raises(control.ControlPlaneError, match="LAUNCHER_MOD_INVALID"):
        plane.add_mod("bad/../id", "2.1.0", "MapHelper.jar", b"other")
    with pytest.raises(control.ControlPlaneError, match="LAUNCHER_MOD_INVALID"):
        plane.add_mod("another-mod", "2.1.0", "../evil.jar", b"other")
    replaced = plane.replace_mod("map-helper", version="2.2.0", data=b"updated")
    assert replaced["version"] == "2.2.0"
    assert replaced["sha256"] == hashlib.sha256(b"updated").hexdigest()
    plane.remove_mod("map-helper")
    assert not plane.list_mods()


def test_state_recovers_valid_atomic_temp_and_writes_json_atomically(tmp_path: Path) -> None:
    root = tmp_path / "control"
    plane = control.ControlPlane(root)
    plane.load_state()
    state_path = root / "state.json"
    state_path.write_text("{broken", encoding="utf-8")
    temp_state = json.loads((root / "state.json.tmp").read_text(encoding="utf-8")) if (root / "state.json.tmp").exists() else None
    assert temp_state is None
    recovered = {"schemaVersion": 1, "baseManifest": {}, "draftMods": [], "draftNews": [], "publishedNews": [], "releases": []}
    (root / "state.json.tmp").write_text(json.dumps(recovered), encoding="utf-8")
    assert plane.load_state()["schemaVersion"] == 1
    assert json.loads(state_path.read_text(encoding="utf-8"))["schemaVersion"] == 1


def test_news_validation_is_plain_text_and_texture_urls_are_safe(tmp_path: Path) -> None:
    plane = control.ControlPlane(tmp_path / "control")
    plane.load_state()
    news = plane.save_news(
        {
            "slug": "summer-update",
            "title": "Summer <Update>",
            "version": "1.2.0",
            "summary": ["<b>Fast</b> fixes"],
            "sections": {"general": ["New island"], "technical": [], "bugfixes": ["Fixed <lag>"]},
            "items": [{"itemId": "copimine:token", "displayName": "Token", "iconUrl": "/assets/patch-items/token.png"}],
        }
    )
    assert "<" not in news["title"]
    assert news["items"][0]["iconUrl"] == "/assets/patch-items/token.png"
    with pytest.raises(control.ControlPlaneError, match="LAUNCHER_NEWS_INVALID"):
        plane.save_news({"slug": "bad slug", "title": "Bad"})
    with pytest.raises(control.ControlPlaneError, match="LAUNCHER_NEWS_INVALID"):
        plane.save_news({"slug": "unsafe", "title": "Unsafe", "items": [{"iconUrl": "javascript:alert(1)"}]})
    published = plane.publish_news("summer-update")
    assert published["slug"] == "summer-update"
    assert plane.list_news()[0]["slug"] == "summer-update"


def test_telemetry_is_bounded_and_aggregated_without_database(tmp_path: Path) -> None:
    plane = control.ControlPlane(tmp_path / "control")
    plane.load_state()
    plane.record_telemetry("launch", "1.0.0", 4)
    plane.record_telemetry("reconcile_failure", "1.0.0", 4, "MANIFEST_HASH_MISMATCH")
    with pytest.raises(control.ControlPlaneError, match="LAUNCHER_TELEMETRY_INVALID"):
        plane.record_telemetry("raw-player-data", "1.0.0", 4)
    stats = plane.stats()
    assert stats["events"]["launch"] == 1
    assert stats["events"]["reconcile_failure"] == 1
    assert stats["diagnostics"][0]["code"] == "MANIFEST_HASH_MISMATCH"
    assert (tmp_path / "control" / "telemetry.jsonl").is_file()


def test_release_validation_fails_closed_without_server_signing_key(tmp_path: Path) -> None:
    plane = control.ControlPlane(tmp_path / "control", public_root=tmp_path / "public")
    plane.load_state()
    result = plane.validate_release(public_key_id="launcher-v1-staging")
    assert result["ok"] is False
    assert result["code"] == "LAUNCHER_RELEASE_NOT_READY"
    assert "SIGNING_KEY_MISSING" in result["reasons"]


def test_ephemeral_staging_release_is_signed_and_rollback_keeps_files(tmp_path: Path) -> None:
    pytest.importorskip("cryptography")
    plane = control.ControlPlane(tmp_path / "control", source_manifest=complete_source_manifest(tmp_path), public_root=tmp_path / "public")
    plane.load_state()
    plane.add_mod("map-helper", "2.1.0", "MapHelper.jar", b"map-helper")
    seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"

    first = plane.publish_release(
        private_key_hex=seed,
        public_key_id="launcher-v1-staging",
        release_id="2026.08.17.2",
        release_sequence=2,
    )
    manifest_path = tmp_path / "public" / "launcher" / "stable" / "instance-manifest.json"
    signature_path = tmp_path / "public" / "launcher" / "stable" / "instance-manifest.sig"
    assert first["releaseId"] == "2026.08.17.2"
    assert manifest_path.is_file() and signature_path.is_file()
    manifest_bytes = manifest_path.read_bytes()
    signature = json.loads(signature_path.read_text(encoding="utf-8"))
    public_key = bytes.fromhex((tmp_path / "public" / "launcher" / "stable" / "public-key.hex").read_text(encoding="ascii"))
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

    Ed25519PublicKey.from_public_bytes(public_key).verify(
        __import__("base64").b64decode(signature["signatureBase64"]), manifest_bytes
    )
    digest = hashlib.sha256(b"map-helper").hexdigest()
    assert (tmp_path / "public" / "launcher" / "files" / digest).read_bytes() == b"map-helper"
    runtime_digest = json.loads((tmp_path / "control" / "releases" / "2026.08.17.2" / "instance-manifest.json").read_text(encoding="utf-8"))["minecraftRuntime"]["sha256"]
    assert (tmp_path / "public" / "launcher" / "files" / runtime_digest).is_file()

    plane.replace_mod("map-helper", version="2.2.0", data=b"map-helper-v2")
    second = plane.publish_release(
        private_key_hex=seed,
        public_key_id="launcher-v1-staging",
        release_id="2026.08.17.3",
        release_sequence=3,
    )
    assert second["releaseId"] == "2026.08.17.3"
    assert (tmp_path / "control" / "releases" / "2026.08.17.2" / "instance-manifest.json").is_file()
    rolled = plane.rollback_release("2026.08.17.2", private_key_hex=seed)
    assert rolled["rollbackOf"] == "2026.08.17.2"
    assert rolled["releaseSequence"] == 4
    assert json.loads(manifest_path.read_text(encoding="utf-8"))["releaseSequence"] == 4


def test_published_news_generates_static_patch_contract_and_safe_detail_page(tmp_path: Path) -> None:
    public = tmp_path / "public"
    plane = control.ControlPlane(tmp_path / "control", public_root=public)
    plane.load_state()
    plane.save_news(
        {
            "slug": "staging-news",
            "title": "Staging <release>",
            "version": "1.0.1",
            "summary": ["Ready"],
            "sections": {"general": ["Added"], "technical": ["Signed"], "bugfixes": ["Fixed"]},
            "items": [{"itemId": "copimine:token", "displayName": "Token", "iconUrl": "/assets/patch-items/token.png", "changes": ["Texture"]}],
        }
    )
    plane.publish_news("staging-news")
    index = json.loads((public / "assets/public-data/patches/index.json").read_text(encoding="utf-8"))
    detail = json.loads((public / "assets/public-data/patches/staging-news.json").read_text(encoding="utf-8"))
    html = (public / "news/staging-news.html").read_text(encoding="utf-8")
    assert index["patches"][0]["slug"] == "staging-news"
    assert detail["items"][0]["iconUrl"] == "/assets/patch-items/token.png"
    assert "Staging" in html
    assert "<release>" not in html
    assert "<script>" not in html
