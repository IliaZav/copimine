from __future__ import annotations

import hashlib
import json
import mimetypes
import os
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from backend.envfile import parse_env_file, resolve_env_file


@dataclass(frozen=True)
class ManagedArtifact:
    key: str
    bucket: str
    filename: str
    path: Path
    url: str
    media_type: str
    manifest_path: Path | None = None
    hash_sidecar_path: Path | None = None

    def exists(self) -> bool:
        return self.path.exists() and self.path.is_file()


def project_root_from_backend(backend_file: Path) -> Path:
    return backend_file.resolve().parents[2]


def app_root_from_backend(backend_file: Path) -> Path:
    return backend_file.resolve().parents[1]


def env_values(app_root: Path) -> dict[str, str]:
    return parse_env_file(resolve_env_file(app_root / ".env"))


def safe_json(path: Path) -> dict[str, Any]:
    try:
        if not path.exists():
            return {}
        data = json.loads(path.read_text(encoding="utf-8", errors="replace"))
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def digest_file(path: Path, algorithm: str) -> str:
    hasher = hashlib.new(algorithm)
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def read_sidecar_hash(path: Path | None) -> str:
    if path is None or not path.exists():
        return ""
    try:
        return str(path.read_text(encoding="utf-8", errors="replace")).strip().lstrip("\ufeff").split()[0].strip()
    except Exception:
        return ""


def git_commit(project_root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(project_root), "rev-parse", "HEAD"],
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
        ).strip()
    except Exception:
        return ""


def release_manifest(project_root: Path) -> dict[str, Any]:
    return safe_json(project_root / "deploy" / "release_manifest.json")


def managed_artifacts(project_root: Path) -> dict[str, ManagedArtifact]:
    release = release_manifest(project_root)
    resource_url = str(release.get("resourcePack", {}).get("downloadUrl") or "/resourcepacks/CopiMineResourcePack.zip").strip() or "/resourcepacks/CopiMineResourcePack.zip"
    modpack_url = str(release.get("modpack", {}).get("downloadUrl") or "/downloads/CopiMineMods.zip").strip() or "/downloads/CopiMineMods.zip"
    client_url = str(release.get("clientMod", {}).get("downloadUrl") or "/downloads/CopiMineClient-0.1.0.jar").strip() or "/downloads/CopiMineClient-0.1.0.jar"
    thirdparty = project_root / "thirdparty"
    resourcepacks = project_root / "resourcepacks" / "build"
    return {
        "resourcepack": ManagedArtifact(
            key="resourcepack",
            bucket="resourcepacks",
            filename="CopiMineResourcePack.zip",
            path=resourcepacks / "CopiMineResourcePack.zip",
            url=resource_url,
            media_type="application/zip",
            manifest_path=project_root / "deploy" / "release_manifest.json",
            hash_sidecar_path=resourcepacks / "CopiMineResourcePack.sha1",
        ),
        "modpack": ManagedArtifact(
            key="modpack",
            bucket="downloads",
            filename="CopiMineMods.zip",
            path=thirdparty / "CopiMineMods.zip",
            url=modpack_url,
            media_type="application/zip",
            manifest_path=thirdparty / "modpack_manifest.json",
            hash_sidecar_path=thirdparty / "CopiMineMods.sha1",
        ),
        "client_mod": ManagedArtifact(
            key="client_mod",
            bucket="downloads",
            filename="CopiMineClient-0.1.0.jar",
            path=thirdparty / "client-mods" / "CopiMineClient-0.1.0.jar",
            url=client_url,
            media_type="application/java-archive",
            manifest_path=project_root / "thirdparty" / "thirdparty_manifest.json",
            hash_sidecar_path=None,
        ),
    }


def artifact_snapshot(artifact: ManagedArtifact) -> dict[str, Any]:
    exists = artifact.exists()
    guessed_type = mimetypes.guess_type(artifact.filename)[0] or artifact.media_type
    sha1 = digest_file(artifact.path, "sha1") if exists else ""
    sha256 = digest_file(artifact.path, "sha256") if exists else ""
    sidecar = read_sidecar_hash(artifact.hash_sidecar_path)
    manifest = safe_json(artifact.manifest_path) if artifact.manifest_path is not None else {}
    payload = asdict(artifact)
    payload["path"] = str(artifact.path)
    payload["manifest_path"] = str(artifact.manifest_path) if artifact.manifest_path else ""
    payload["hash_sidecar_path"] = str(artifact.hash_sidecar_path) if artifact.hash_sidecar_path else ""
    payload.update(
        {
            "exists": exists,
            "size": artifact.path.stat().st_size if exists else 0,
            "modified": int(artifact.path.stat().st_mtime) if exists else None,
            "media_type": guessed_type,
            "sha1": sha1,
            "sha256": sha256,
            "recordedSha1": sidecar,
            "sha1MatchesSidecar": bool(sha1 and sidecar and sha1.lower() == sidecar.lower()),
            "manifest": manifest,
        }
    )
    return payload


def runtime_snapshot(project_root: Path, app_root: Path) -> dict[str, Any]:
    env_path = resolve_env_file(app_root / ".env")
    release = release_manifest(project_root)
    checks = {
        key: artifact_snapshot(artifact)
        for key, artifact in managed_artifacts(project_root).items()
    }
    return {
        "projectRoot": str(project_root),
        "appRoot": str(app_root),
        "envFile": str(env_path),
        "envExists": env_path.exists(),
        "gitCommit": git_commit(project_root) or str(release.get("gitCommit") or ""),
        "releaseManifest": release,
        "artifacts": checks,
    }


def artifact_by_route(project_root: Path, bucket: str, filename: str) -> ManagedArtifact:
    normalized_bucket = str(bucket or "").strip().lower()
    normalized_filename = Path(str(filename or "")).name
    for artifact in managed_artifacts(project_root).values():
        if artifact.bucket == normalized_bucket and artifact.filename == normalized_filename:
            return artifact
    raise KeyError(f"Unknown managed artifact route: {bucket}/{normalized_filename}")


def public_runtime_snapshot(project_root: Path, app_root: Path) -> dict[str, Any]:
    snapshot = runtime_snapshot(project_root, app_root)
    return {
        "gitCommit": snapshot["gitCommit"],
        "envExists": snapshot["envExists"],
        "artifacts": snapshot["artifacts"],
        "generatedAt": int(os.path.getmtime(snapshot["envFile"])) if snapshot["envExists"] else None,
    }
