"""Filesystem-backed control plane for CopiMine Launcher distribution.

This module deliberately has no dependency on ``backend.main``.  It is safe to
use from local/staging tooling without importing the production database setup.
All mutable state lives below the configured control directory and all writes
are atomic replacements of small JSON/text files.
"""

from __future__ import annotations

import copy
import hashlib
import html
import json
import os
import re
import shutil
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import urlparse


MAX_MOD_SIZE = 256 * 1024 * 1024
MAX_TEXT_LENGTH = 4000
MAX_SUMMARY_ITEMS = 3
MAX_TELEMETRY_LINES = 10_000
TELEMETRY_EVENTS = frozenset({"launch", "reconcile_success", "reconcile_failure", "game_exit"})
COMPONENT_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,63}$")
VERSION_RE = re.compile(r"^[0-9A-Za-z][0-9A-Za-z._+\-]{0,63}$")
FILENAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\.jar$")
SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9-]{1,79}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
DIAGNOSTIC_RE = re.compile(r"^[A-Z0-9][A-Z0-9_.:-]{0,79}$")


class ControlPlaneError(ValueError):
    """A stable, user-safe control-plane failure."""

    def __init__(self, code: str, message: str, *, field: str | None = None) -> None:
        self.code = code
        self.field = field
        self.message = message
        suffix = f" [{field}]" if field else ""
        super().__init__(f"{code}{suffix}: {message}")


def _utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _plain_text(value: Any, *, field: str, maximum: int = MAX_TEXT_LENGTH, required: bool = False) -> str:
    if value is None:
        value = ""
    if not isinstance(value, str):
        raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "text value must be a string", field=field)
    value = html.unescape(re.sub(r"<[^>]*>", "", value))
    value = "".join(ch for ch in value if ch in "\n\r\t" or ord(ch) >= 32).strip()
    if required and not value:
        raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "value is required", field=field)
    if len(value) > maximum:
        raise ControlPlaneError("LAUNCHER_NEWS_INVALID", f"value exceeds {maximum} characters", field=field)
    return value


def _safe_texture_url(value: Any, *, field: str) -> str:
    if value in (None, ""):
        return ""
    if not isinstance(value, str) or len(value) > 512:
        raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "invalid texture URL", field=field)
    value = value.strip()
    parsed = urlparse(value)
    if value.startswith("/assets/") and not value.startswith("//"):
        return value
    if parsed.scheme == "https" and parsed.netloc and not parsed.username and not parsed.password:
        return value
    raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "texture URL must be an /assets path or HTTPS URL", field=field)


def validate_component_id(value: Any) -> str:
    if not isinstance(value, str):
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "component id must be text", field="componentId")
    value = value.strip().lower()
    if not COMPONENT_RE.fullmatch(value):
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "component id is not safe", field="componentId")
    return value


def validate_version(value: Any) -> str:
    if not isinstance(value, str) or not VERSION_RE.fullmatch(value.strip()):
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "version is not safe", field="version")
    return value.strip()


def validate_filename(value: Any) -> str:
    if not isinstance(value, str) or not FILENAME_RE.fullmatch(value.strip()):
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "filename must be a safe .jar name", field="filename")
    return value.strip()


def validate_sha256(value: Any, *, field: str = "sha256") -> str:
    if not isinstance(value, str) or not SHA256_RE.fullmatch(value.lower()):
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "SHA-256 must be 64 lowercase hex characters", field=field)
    return value.lower()


def _safe_slug(value: Any) -> str:
    if not isinstance(value, str) or not SLUG_RE.fullmatch(value.strip().lower()):
        raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "slug is not safe", field="slug")
    return value.strip().lower()


def _safe_path(value: Any) -> str:
    if not isinstance(value, str) or "\\" in value:
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "path is not safe", field="path")
    path = Path(value)
    if path.is_absolute() or ".." in path.parts or len(path.parts) != 2 or path.parts[0] != "mods":
        raise ControlPlaneError("LAUNCHER_MOD_INVALID", "managed path must be mods/<filename>", field="path")
    validate_filename(path.parts[1])
    return path.as_posix()


def _atomic_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def _atomic_json(path: Path, value: Any) -> None:
    payload = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    _atomic_bytes(path, payload.encode("utf-8"))


def _append_jsonl(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n")


def _copy_json(value: Any) -> Any:
    return copy.deepcopy(value)


class ControlPlane:
    """Manage drafts and immutable distribution state below one directory."""

    def __init__(
        self,
        root: Path,
        *,
        source_manifest: Path | None = None,
        public_root: Path | None = None,
        clock: Any = _utc_now,
    ) -> None:
        self.root = Path(root).resolve()
        self.source_manifest = Path(source_manifest).resolve() if source_manifest else None
        self.public_root = Path(public_root).resolve() if public_root else None
        self.clock = clock

    @property
    def state_path(self) -> Path:
        return self.root / "state.json"

    @property
    def state_temp_path(self) -> Path:
        return self.root / "state.json.tmp"

    def _default_state(self) -> dict[str, Any]:
        base: dict[str, Any] = {
            "schemaVersion": 1,
            "releaseId": "draft",
            "releaseSequence": 0,
            "baseManifest": {
                "schemaVersion": 2,
                "files": [],
            },
            "draftMods": [],
            "draftNews": [],
            "publishedNews": [],
            "releases": [],
            "currentReleaseId": None,
            "previousReleaseId": None,
        }
        if not self.source_manifest or not self.source_manifest.is_file():
            return base
        try:
            document = json.loads(self.source_manifest.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ControlPlaneError("LAUNCHER_STATE_INVALID", f"source manifest could not be read: {exc}") from exc
        if not isinstance(document, dict) or not isinstance(document.get("files"), list):
            raise ControlPlaneError("LAUNCHER_STATE_INVALID", "source manifest has no files array")
        base["baseManifest"] = _copy_json(document)
        base["releaseId"] = str(document.get("releaseId") or "draft")
        base["releaseSequence"] = int(document.get("releaseSequence") or 0)
        for entry in document["files"]:
            if not isinstance(entry, dict):
                continue
            path = str(entry.get("path") or "")
            if not path.lower().startswith("mods/"):
                continue
            try:
                filename = validate_filename(Path(path).name)
                component = validate_component_id(entry.get("componentId") or entry.get("component") or Path(filename).stem)
                digest = validate_sha256(entry.get("sha256"), field="sha256")
            except ControlPlaneError:
                continue
            base["draftMods"].append(
                {
                    "componentId": component,
                    "version": validate_version(str(entry.get("version") or "0.0.0")),
                    "filename": filename,
                    "path": f"mods/{filename}",
                    "required": bool(entry.get("required", True)),
                    "sha256": digest,
                    "sizeBytes": int(entry.get("sizeBytes") or entry.get("size") or 0),
                    "displayName": str(entry.get("displayName") or component),
                }
            )
        return base

    def _normalise_state(self, state: dict[str, Any]) -> dict[str, Any]:
        defaults = self._default_state()
        for key, value in defaults.items():
            state.setdefault(key, value)
        if not isinstance(state.get("draftMods"), list):
            state["draftMods"] = []
        if not isinstance(state.get("draftNews"), list):
            state["draftNews"] = []
        if not isinstance(state.get("publishedNews"), list):
            state["publishedNews"] = []
        if not isinstance(state.get("releases"), list):
            state["releases"] = []
        return state

    def load_state(self) -> dict[str, Any]:
        self.root.mkdir(parents=True, exist_ok=True)
        state: dict[str, Any] | None = None
        for candidate in (self.state_path, self.state_temp_path):
            if not candidate.is_file():
                continue
            try:
                decoded = json.loads(candidate.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            if isinstance(decoded, dict):
                state = decoded
                break
        if state is None:
            state = self._default_state()
        state = self._normalise_state(state)
        _atomic_json(self.state_path, state)
        if self.state_temp_path.exists():
            self.state_temp_path.unlink()
        return state

    def _save(self, state: dict[str, Any], *, action: str, details: dict[str, Any] | None = None) -> None:
        self.root.mkdir(parents=True, exist_ok=True)
        _atomic_json(self.state_path, self._normalise_state(state))
        event = {"at": self.clock(), "action": action}
        if details:
            event["details"] = details
        _append_jsonl(self.root / "audit.jsonl", event)

    def _store_bytes(self, data: bytes) -> tuple[str, int]:
        if not isinstance(data, (bytes, bytearray, memoryview)):
            raise ControlPlaneError("LAUNCHER_MOD_INVALID", "uploaded content must be bytes", field="file")
        payload = bytes(data)
        if not payload:
            raise ControlPlaneError("LAUNCHER_MOD_INVALID", "uploaded file is empty", field="file")
        if len(payload) > MAX_MOD_SIZE:
            raise ControlPlaneError("LAUNCHER_MOD_INVALID", "uploaded file is too large", field="file")
        digest = hashlib.sha256(payload).hexdigest()
        _atomic_bytes(self.root / "files" / digest, payload)
        return digest, len(payload)

    def _find_mod(self, state: dict[str, Any], component_id: str) -> dict[str, Any]:
        component_id = validate_component_id(component_id)
        for item in state["draftMods"]:
            if item.get("componentId") == component_id:
                return item
        raise ControlPlaneError("LAUNCHER_MOD_NOT_FOUND", "managed mod was not found", field="componentId")

    def list_mods(self) -> list[dict[str, Any]]:
        return _copy_json(self.load_state()["draftMods"])

    def add_mod(
        self,
        component_id: str,
        version: str,
        filename: str,
        data: bytes,
        *,
        required: bool = True,
        display_name: str | None = None,
    ) -> dict[str, Any]:
        state = self.load_state()
        component = validate_component_id(component_id)
        version = validate_version(version)
        filename = validate_filename(filename)
        path = f"mods/{filename}"
        if any(item.get("componentId") == component or item.get("path") == path for item in state["draftMods"]):
            raise ControlPlaneError("LAUNCHER_MOD_DUPLICATE", "component id or managed path already exists")
        digest, size = self._store_bytes(data)
        item = {
            "componentId": component,
            "version": version,
            "filename": filename,
            "path": path,
            "required": bool(required),
            "sha256": digest,
            "sizeBytes": size,
            "displayName": _plain_text(display_name or component, field="displayName", maximum=160, required=True),
        }
        state["draftMods"].append(item)
        self._save(state, action="mod.add", details={"componentId": component, "sha256": digest})
        return _copy_json(item)

    def replace_mod(
        self,
        component_id: str,
        *,
        version: str | None = None,
        filename: str | None = None,
        data: bytes | None = None,
        required: bool | None = None,
        display_name: str | None = None,
    ) -> dict[str, Any]:
        state = self.load_state()
        item = self._find_mod(state, component_id)
        component = item["componentId"]
        if version is not None:
            item["version"] = validate_version(version)
        if filename is not None:
            item["filename"] = validate_filename(filename)
            item["path"] = f"mods/{item['filename']}"
        if any(other is not item and other.get("path") == item["path"] for other in state["draftMods"]):
            raise ControlPlaneError("LAUNCHER_MOD_DUPLICATE", "managed path already exists")
        if data is not None:
            digest, size = self._store_bytes(data)
            item["sha256"] = digest
            item["sizeBytes"] = size
        if required is not None:
            item["required"] = bool(required)
        if display_name is not None:
            item["displayName"] = _plain_text(display_name, field="displayName", maximum=160, required=True)
        self._save(state, action="mod.replace", details={"componentId": component})
        return _copy_json(item)

    def remove_mod(self, component_id: str) -> None:
        state = self.load_state()
        component = validate_component_id(component_id)
        original = len(state["draftMods"])
        state["draftMods"] = [item for item in state["draftMods"] if item.get("componentId") != component]
        if len(state["draftMods"]) == original:
            raise ControlPlaneError("LAUNCHER_MOD_NOT_FOUND", "managed mod was not found", field="componentId")
        self._save(state, action="mod.remove", details={"componentId": component})

    def manifest_for_draft(self) -> dict[str, Any]:
        state = self.load_state()
        manifest = _copy_json(state["baseManifest"])
        preserved = [
            _copy_json(item)
            for item in manifest.get("files", [])
            if isinstance(item, dict) and not str(item.get("path") or "").lower().startswith("mods/")
        ]
        manifest["files"] = preserved + _copy_json(state["draftMods"])
        return manifest

    @staticmethod
    def _news_lines(value: Any, *, field: str) -> list[str]:
        if value is None:
            return []
        if isinstance(value, str):
            value = [line for line in value.splitlines() if line.strip()]
        if not isinstance(value, list) or len(value) > 50:
            raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "section must be a list of lines", field=field)
        return [_plain_text(line, field=field, maximum=MAX_TEXT_LENGTH, required=True) for line in value]

    def save_news(self, payload: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "news payload must be an object")
        slug = _safe_slug(payload.get("slug"))
        title = _plain_text(payload.get("title"), field="title", maximum=200, required=True)
        version = payload.get("version")
        if version is not None:
            version = validate_version(str(version))
        summary = self._news_lines(payload.get("summary"), field="summary")[:MAX_SUMMARY_ITEMS]
        raw_sections = payload.get("sections") or {}
        if not isinstance(raw_sections, dict):
            raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "sections must be an object", field="sections")
        sections = {
            name: self._news_lines(raw_sections.get(name), field=f"sections.{name}")
            for name in ("general", "technical", "bugfixes")
        }
        items: list[dict[str, Any]] = []
        raw_items = payload.get("items") or []
        if not isinstance(raw_items, list) or len(raw_items) > 100:
            raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "items must be a bounded list", field="items")
        for index, raw in enumerate(raw_items):
            if not isinstance(raw, dict):
                raise ControlPlaneError("LAUNCHER_NEWS_INVALID", "item must be an object", field=f"items[{index}]")
            item_id = _plain_text(raw.get("itemId"), field=f"items[{index}].itemId", maximum=160, required=True)
            item = {
                "itemId": item_id,
                "displayName": _plain_text(raw.get("displayName") or item_id, field=f"items[{index}].displayName", maximum=200, required=True),
                "iconUrl": _safe_texture_url(raw.get("iconUrl"), field=f"items[{index}].iconUrl"),
                "changes": self._news_lines(raw.get("changes"), field=f"items[{index}].changes"),
            }
            items.append(item)
        record = {
            "slug": slug,
            "title": title,
            "version": version or "",
            "publishedAt": str(payload.get("publishedAt") or ""),
            "summary": summary,
            "sections": sections,
            "items": items,
            "updatedAt": self.clock(),
        }
        state = self.load_state()
        state["draftNews"] = [item for item in state["draftNews"] if item.get("slug") != slug]
        state["draftNews"].append(record)
        self._save(state, action="news.save", details={"slug": slug})
        return _copy_json(record)

    def delete_news(self, slug: str) -> None:
        slug = _safe_slug(slug)
        state = self.load_state()
        before = len(state["draftNews"])
        state["draftNews"] = [item for item in state["draftNews"] if item.get("slug") != slug]
        if len(state["draftNews"]) == before:
            raise ControlPlaneError("LAUNCHER_NEWS_NOT_FOUND", "news draft was not found", field="slug")
        self._save(state, action="news.delete", details={"slug": slug})

    def publish_news(self, slug: str) -> dict[str, Any]:
        slug = _safe_slug(slug)
        state = self.load_state()
        record = next((item for item in state["draftNews"] if item.get("slug") == slug), None)
        if record is None:
            raise ControlPlaneError("LAUNCHER_NEWS_NOT_FOUND", "news draft was not found", field="slug")
        record = _copy_json(record)
        record["publishedAt"] = record.get("publishedAt") or self.clock()
        record["updatedAt"] = self.clock()
        state["draftNews"] = [item for item in state["draftNews"] if item.get("slug") != slug]
        state["publishedNews"] = [item for item in state["publishedNews"] if item.get("slug") != slug]
        state["publishedNews"].insert(0, record)
        self._save(state, action="news.publish", details={"slug": slug})
        return record

    def list_news(self, *, include_drafts: bool = False) -> list[dict[str, Any]]:
        state = self.load_state()
        result = _copy_json(state["publishedNews"])
        if include_drafts:
            result.extend(_copy_json(state["draftNews"]))
        return result

    def record_telemetry(
        self,
        event: str,
        launcher_version: str,
        manifest_sequence: int,
        diagnostic_code: str | None = None,
    ) -> None:
        if event not in TELEMETRY_EVENTS:
            raise ControlPlaneError("LAUNCHER_TELEMETRY_INVALID", "unsupported event", field="event")
        version = validate_version(launcher_version)
        if not isinstance(manifest_sequence, int) or manifest_sequence < 0 or manifest_sequence > 2**31 - 1:
            raise ControlPlaneError("LAUNCHER_TELEMETRY_INVALID", "manifest sequence is invalid", field="manifestSequence")
        code = ""
        if diagnostic_code:
            if not isinstance(diagnostic_code, str) or not DIAGNOSTIC_RE.fullmatch(diagnostic_code):
                raise ControlPlaneError("LAUNCHER_TELEMETRY_INVALID", "diagnostic code is invalid", field="diagnosticCode")
            code = diagnostic_code
        _append_jsonl(
            self.root / "telemetry.jsonl",
            {
                "at": self.clock(),
                "event": event,
                "launcherVersion": version,
                "manifestSequence": manifest_sequence,
                "diagnosticCode": code,
            },
        )

    def record_download(self, kind: str) -> None:
        if not isinstance(kind, str) or not re.fullmatch(r"[a-z0-9_.-]{1,40}", kind):
            raise ControlPlaneError("LAUNCHER_TELEMETRY_INVALID", "download kind is invalid", field="kind")
        _append_jsonl(self.root / "telemetry.jsonl", {"at": self.clock(), "event": "download", "kind": kind})

    def stats(self) -> dict[str, Any]:
        events: dict[str, int] = {}
        downloads: dict[str, int] = {}
        diagnostics: list[dict[str, Any]] = []
        path = self.root / "telemetry.jsonl"
        if path.is_file():
            lines = path.read_text(encoding="utf-8").splitlines()[-MAX_TELEMETRY_LINES:]
            for line in lines:
                try:
                    row = json.loads(line)
                except json.JSONDecodeError:
                    continue
                event = row.get("event")
                if event == "download":
                    kind = str(row.get("kind") or "unknown")
                    downloads[kind] = downloads.get(kind, 0) + 1
                elif isinstance(event, str):
                    events[event] = events.get(event, 0) + 1
                code = row.get("diagnosticCode")
                if code:
                    diagnostics.append({"at": row.get("at", ""), "code": code, "event": event})
        return {
            "events": events,
            "downloads": downloads,
            "diagnostics": list(reversed(diagnostics[-50:])),
            "telemetryLines": sum(events.values()) + sum(downloads.values()),
        }

    def dashboard(self) -> dict[str, Any]:
        state = self.load_state()
        return {
            "schemaVersion": 1,
            "draftRelease": {
                "releaseId": state.get("releaseId"),
                "releaseSequence": state.get("releaseSequence", 0),
                "modCount": len(state.get("draftMods", [])),
            },
            "currentReleaseId": state.get("currentReleaseId"),
            "previousReleaseId": state.get("previousReleaseId"),
            "mods": _copy_json(state.get("draftMods", [])),
            "news": _copy_json(state.get("publishedNews", [])),
            "draftNews": _copy_json(state.get("draftNews", [])),
            "releases": _copy_json(state.get("releases", [])),
            "stats": self.stats(),
        }


def default_control_plane() -> ControlPlane:
    app_root = Path(__file__).resolve().parents[1]
    project_root = app_root.parent
    configured = os.getenv("COPIMINE_LAUNCHER_CONTROL_DIR")
    source = os.getenv("COPIMINE_LAUNCHER_SOURCE_MANIFEST")
    public = os.getenv("COPIMINE_LAUNCHER_PUBLIC_ROOT")
    return ControlPlane(
        Path(configured) if configured else app_root / "data" / "launcher-control",
        source_manifest=Path(source) if source else project_root / "artifacts" / "launcher" / "Release" / "instance-current" / "instance-manifest.json",
        public_root=Path(public) if public else app_root / "frontend",
    )


__all__ = [
    "COMPONENT_RE",
    "ControlPlane",
    "ControlPlaneError",
    "MAX_MOD_SIZE",
    "TELEMETRY_EVENTS",
    "default_control_plane",
    "validate_component_id",
    "validate_filename",
    "validate_sha256",
    "validate_version",
]
