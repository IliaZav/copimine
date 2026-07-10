from __future__ import annotations

import json
import os
import socket
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from .deploy_runtime import app_root_from_backend, artifact_snapshot, managed_artifacts, project_root_from_backend, release_manifest
from .envfile import parse_env_file, resolve_env_file

try:
    import psycopg  # type: ignore
except Exception:  # pragma: no cover
    psycopg = None


@dataclass(frozen=True)
class CheckResult:
    key: str
    status: str
    required: bool
    summary: str
    details: dict[str, Any]


def _ok(key: str, summary: str, *, required: bool = True, **details: Any) -> CheckResult:
    return CheckResult(key=key, status="ok", required=required, summary=summary, details=details)


def _warn(key: str, summary: str, *, required: bool = False, **details: Any) -> CheckResult:
    return CheckResult(key=key, status="warn", required=required, summary=summary, details=details)


def _fail(key: str, summary: str, *, required: bool = True, **details: Any) -> CheckResult:
    return CheckResult(key=key, status="fail", required=required, summary=summary, details=details)


def _tcp_check(host: str, port: int, timeout: float = 1.5) -> tuple[bool, str]:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True, "reachable"
    except Exception as exc:
        return False, f"{type(exc).__name__}: {exc}"


def _directory_check(key: str, path: Path, *, must_exist: bool = True, writable: bool = False, required: bool = True) -> CheckResult:
    if must_exist and not path.exists():
        return _fail(key, "directory missing", required=required, path=str(path))
    if path.exists() and not path.is_dir():
        return _fail(key, "path is not a directory", required=required, path=str(path))
    if writable and path.exists() and not os.access(path, os.W_OK):
        return _fail(key, "directory is not writable", required=required, path=str(path))
    if not path.exists():
        return _warn(key, "directory not present yet", required=required, path=str(path))
    return _ok(key, "directory ready", required=required, path=str(path), writable=os.access(path, os.W_OK))


def _env_check(app_root: Path) -> tuple[CheckResult, dict[str, str], Path]:
    env_path = resolve_env_file(app_root / ".env")
    if not env_path.exists():
        return _fail("env_file", ".env file missing", path=str(env_path)), {}, env_path
    values = parse_env_file(env_path)
    required_keys = [
        "POSTGRES_HOST",
        "POSTGRES_PORT",
        "POSTGRES_DB",
        "POSTGRES_USER",
        "POSTGRES_PASSWORD",
        "DATABASE_URL",
        "SECRET_KEY",
        "ADMIN_PUBLIC_BASE_URL",
    ]
    missing = [key for key in required_keys if not str(values.get(key, "")).strip() or values.get(key) == "CHANGE_ME"]
    if missing:
        return _fail("env_file", "required env values are missing", path=str(env_path), missing=missing), values, env_path
    return _ok("env_file", ".env loaded", path=str(env_path), keys=len(values)), values, env_path


def _postgres_check(values: dict[str, str]) -> CheckResult:
    if psycopg is None:
        return _fail("postgres", "psycopg is not installed", library="psycopg")
    try:
        with psycopg.connect(
            host=str(values.get("POSTGRES_HOST", "127.0.0.1")),
            port=int(values.get("POSTGRES_PORT", "5432") or "5432"),
            dbname=str(values.get("POSTGRES_DB", "copimine")),
            user=str(values.get("POSTGRES_USER", "copimine")),
            password=str(values.get("POSTGRES_PASSWORD", "")),
            connect_timeout=5,
        ) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT current_database(), current_user, version()")
                row = cur.fetchone()
        return _ok(
            "postgres",
            "postgres connection ok",
            database=str(row[0]) if row else "",
            user=str(row[1]) if row else "",
        )
    except Exception as exc:
        return _fail("postgres", "postgres connection failed", error=f"{type(exc).__name__}: {exc}")


def _artifact_checks(project_root: Path) -> list[CheckResult]:
    checks: list[CheckResult] = []
    for key, artifact in managed_artifacts(project_root).items():
        snapshot = artifact_snapshot(artifact)
        if not snapshot["exists"]:
            checks.append(_fail(f"artifact_{key}", "artifact missing", path=snapshot["path"], url=snapshot["url"]))
            continue
        if artifact.hash_sidecar_path and not snapshot["sha1MatchesSidecar"]:
            checks.append(
                _fail(
                    f"artifact_{key}",
                    "artifact hash mismatch",
                    path=snapshot["path"],
                    recordedSha1=snapshot["recordedSha1"],
                    actualSha1=snapshot["sha1"],
                )
            )
            continue
        checks.append(
            _ok(
                f"artifact_{key}",
                "artifact ready",
                path=snapshot["path"],
                size=snapshot["size"],
                sha1=snapshot["sha1"],
                sha256=snapshot["sha256"],
                url=snapshot["url"],
            )
        )
    return checks


def _discord_check(values: dict[str, str]) -> CheckResult:
    token = str(values.get("DISCORD_BOT_TOKEN", "")).strip()
    guild_id = str(values.get("DISCORD_GUILD_ID", "")).strip()
    if not token or token == "CHANGE_ME":
        return _warn("discord", "discord token not configured", required=False)
    if not guild_id or guild_id == "CHANGE_ME":
        return _warn("discord", "discord guild id missing", required=False)
    return _ok("discord", "discord config present", required=False, guildIdConfigured=True)


def _minecraft_check(project_root: Path) -> CheckResult:
    server_dir = project_root / "minecraft" / "server"
    props = server_dir / "server.properties"
    plugins = server_dir / "plugins"
    if not server_dir.exists():
        return _fail("minecraft", "minecraft server directory missing", path=str(server_dir))
    if not props.exists():
        return _fail("minecraft", "server.properties missing", path=str(props))
    if not plugins.exists():
        return _fail("minecraft", "plugins directory missing", path=str(plugins))
    return _ok("minecraft", "minecraft paths present", serverDir=str(server_dir), pluginsDir=str(plugins))


def run_startup_checks() -> dict[str, Any]:
    backend_file = Path(__file__)
    app_root = app_root_from_backend(backend_file)
    project_root = project_root_from_backend(backend_file)
    env_result, values, env_path = _env_check(app_root)
    checks: list[CheckResult] = [env_result]
    data_dir = Path(values.get("COPIMINE_ADMIN_DATA", app_root / "data"))
    data_dir.mkdir(parents=True, exist_ok=True)
    checks.append(_directory_check("data_dir", data_dir, writable=True))
    checks.append(_directory_check("frontend_dir", app_root / "frontend", writable=False))
    checks.append(_directory_check("resourcepacks_dir", project_root / "resourcepacks" / "build", writable=False))
    checks.append(_directory_check("thirdparty_dir", project_root / "thirdparty", writable=False))
    checks.append(_minecraft_check(project_root))
    checks.extend(_artifact_checks(project_root))
    if env_result.status != "fail":
        checks.append(_postgres_check(values))
    checks.append(_discord_check(values))
    manifest = release_manifest(project_root)
    if not manifest:
        checks.append(_warn("release_manifest", "release manifest not found", path=str(project_root / "deploy" / "release_manifest.json")))
    else:
        checks.append(_ok("release_manifest", "release manifest loaded", gitCommit=str(manifest.get("gitCommit", ""))))
    failures = [item for item in checks if item.status == "fail" and item.required]
    warnings = [item for item in checks if item.status == "warn"]
    return {
        "ok": not failures,
        "generatedAt": int(time.time()),
        "envFile": str(env_path),
        "projectRoot": str(project_root),
        "appRoot": str(app_root),
        "summary": {
            "total": len(checks),
            "failures": len(failures),
            "warnings": len(warnings),
        },
        "checks": [asdict(item) for item in checks],
    }


def main(argv: list[str]) -> int:
    strict = "--strict" in argv
    report = run_startup_checks()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if strict and not report.get("ok", False):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
