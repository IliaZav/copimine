from __future__ import annotations

import json
import shutil
import time
from pathlib import Path
from typing import Any

try:
    import yaml  # type: ignore
except Exception:  # pragma: no cover
    yaml = None

APP_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = APP_ROOT.parent
DEFAULT_MANIFEST = APP_ROOT / "backend" / "plugin_registry_manifest.json"


class PluginRegistryError(RuntimeError):
    pass


def load_registry_manifest(manifest_path: Path | None = None) -> dict[str, Any]:
    path = manifest_path or DEFAULT_MANIFEST
    if not path.exists():
        return {"plugins": {}}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # pragma: no cover
        raise PluginRegistryError(f"Не удалось прочитать manifest plugin registry: {path}") from exc


def list_registry_plugins(manifest_path: Path | None = None) -> list[dict[str, Any]]:
    manifest = load_registry_manifest(manifest_path)
    out: list[dict[str, Any]] = []
    for plugin_id, row in (manifest.get("plugins") or {}).items():
        item = dict(row or {})
        item["pluginId"] = plugin_id
        out.append(item)
    out.sort(key=lambda item: str(item.get("pluginId") or ""))
    return out


def require_registry_plugin(plugin_id: str, manifest_path: Path | None = None) -> dict[str, Any]:
    manifest = load_registry_manifest(manifest_path)
    plugin = (manifest.get("plugins") or {}).get(plugin_id)
    if not plugin:
        raise PluginRegistryError("Плагин не входит в allowlist plugin registry.")
    item = dict(plugin)
    item["pluginId"] = plugin_id
    return item


def _resolve_project_path(relative_path: str) -> Path:
    raw = Path(relative_path)
    path = (PROJECT_ROOT / raw).resolve()
    root = PROJECT_ROOT.resolve()
    if root not in path.parents and path != root:
        raise PluginRegistryError("Конфиг плагина выходит за пределы проекта.")
    return path


def registry_config_path(plugin: dict[str, Any]) -> Path | None:
    config_path = str(plugin.get("configPath") or "").strip()
    if not config_path:
        return None
    return _resolve_project_path(config_path)


def registry_schema(plugin: dict[str, Any]) -> dict[str, Any]:
    return dict(plugin.get("editableKeys") or {})


def registry_status(plugin_id: str, manifest_path: Path | None = None) -> dict[str, Any]:
    plugin = require_registry_plugin(plugin_id, manifest_path)
    config_path = registry_config_path(plugin)
    return {
        "pluginId": plugin["pluginId"],
        "displayName": str(plugin.get("displayName") or plugin["pluginId"]),
        "configPath": str(config_path) if config_path else "",
        "configExists": bool(config_path and config_path.exists()),
        "reloadMode": str(plugin.get("reloadMode") or "none"),
        "reloadCommand": str(plugin.get("reloadCommand") or ""),
        "editableKeys": sorted((plugin.get("editableKeys") or {}).keys()),
    }


def read_registry_config(plugin_id: str, manifest_path: Path | None = None) -> dict[str, Any]:
    plugin = require_registry_plugin(plugin_id, manifest_path)
    config_path = registry_config_path(plugin)
    if not config_path:
        return {"pluginId": plugin_id, "values": {}, "configPath": ""}
    if not yaml:
        raise PluginRegistryError("PyYAML недоступен для чтения config.")
    if not config_path.exists():
        return {"pluginId": plugin_id, "values": {}, "configPath": str(config_path)}
    payload = yaml.safe_load(config_path.read_text(encoding="utf-8", errors="replace")) or {}
    out: dict[str, Any] = {}
    for key in registry_schema(plugin).keys():
        out[key] = dotted_get(payload, key)
    return {"pluginId": plugin_id, "values": out, "configPath": str(config_path)}


def dotted_get(payload: dict[str, Any], dotted_key: str) -> Any:
    current: Any = payload
    for part in str(dotted_key or "").split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current.get(part)
    return current


def dotted_set(payload: dict[str, Any], dotted_key: str, value: Any) -> None:
    parts = [part for part in str(dotted_key or "").split(".") if part]
    if not parts:
        raise PluginRegistryError("Пустой ключ конфига недопустим.")
    current = payload
    for part in parts[:-1]:
        next_value = current.get(part)
        if not isinstance(next_value, dict):
            next_value = {}
            current[part] = next_value
        current = next_value
    current[parts[-1]] = value


def validate_registry_values(plugin_id: str, values: dict[str, Any], manifest_path: Path | None = None) -> dict[str, Any]:
    plugin = require_registry_plugin(plugin_id, manifest_path)
    schema = registry_schema(plugin)
    safe_values: dict[str, Any] = {}
    for key, value in (values or {}).items():
        if key not in schema:
            raise PluginRegistryError(f"Ключ '{key}' не входит в allowlist.")
        rules = dict(schema.get(key) or {})
        kind = str(rules.get("type") or "string").strip().lower()
        if kind == "enum":
            text = str(value or "").strip()
            allowed = {str(item) for item in (rules.get("allow") or [])}
            if text not in allowed:
                raise PluginRegistryError(f"Ключ '{key}' принимает только: {', '.join(sorted(allowed))}")
            safe_values[key] = text
        elif kind == "bool":
            if not isinstance(value, bool):
                raise PluginRegistryError(f"Ключ '{key}' должен быть boolean.")
            safe_values[key] = value
        elif kind == "int":
            if not isinstance(value, int):
                raise PluginRegistryError(f"Ключ '{key}' должен быть integer.")
            min_value = rules.get("min")
            max_value = rules.get("max")
            if min_value is not None and value < int(min_value):
                raise PluginRegistryError(f"Ключ '{key}' меньше минимума {min_value}.")
            if max_value is not None and value > int(max_value):
                raise PluginRegistryError(f"Ключ '{key}' больше максимума {max_value}.")
            safe_values[key] = value
        elif kind == "int_list":
            if not isinstance(value, list) or not all(isinstance(item, int) for item in value):
                raise PluginRegistryError(f"Ключ '{key}' должен быть списком integer.")
            allowed = [int(item) for item in (rules.get("allow") or [])]
            if allowed and sorted(value) != sorted(allowed):
                raise PluginRegistryError(f"Ключ '{key}' должен совпадать с allowlist значений.")
            safe_values[key] = value
        elif kind == "string":
            text = str(value or "")
            max_length = int(rules.get("maxLength") or 1000)
            if len(text) > max_length:
                raise PluginRegistryError(f"Ключ '{key}' длиннее {max_length} символов.")
            safe_values[key] = text
        else:
            raise PluginRegistryError(f"Неподдерживаемый schema type '{kind}' для ключа '{key}'.")
    return safe_values


def backup_registry_config(plugin_id: str, backups_dir: Path, manifest_path: Path | None = None) -> dict[str, Any]:
    plugin = require_registry_plugin(plugin_id, manifest_path)
    config_path = registry_config_path(plugin)
    if not config_path:
        raise PluginRegistryError("У плагина нет configPath для backup.")
    if not config_path.exists():
        raise PluginRegistryError("Файл конфига для backup не найден.")
    backups_dir.mkdir(parents=True, exist_ok=True)
    filename = f"{plugin_id}-{int(time.time())}.bak"
    target = backups_dir / filename
    shutil.copy2(config_path, target)
    return {"name": filename, "path": str(target), "source": str(config_path)}


def apply_registry_values(plugin_id: str, values: dict[str, Any], backups_dir: Path, manifest_path: Path | None = None) -> dict[str, Any]:
    plugin = require_registry_plugin(plugin_id, manifest_path)
    config_path = registry_config_path(plugin)
    if not config_path:
        raise PluginRegistryError("У плагина нет configPath для apply.")
    if not yaml:
        raise PluginRegistryError("PyYAML недоступен для записи config.")
    validated = validate_registry_values(plugin_id, values, manifest_path)
    backup = backup_registry_config(plugin_id, backups_dir, manifest_path)
    payload = {}
    if config_path.exists():
        payload = yaml.safe_load(config_path.read_text(encoding="utf-8", errors="replace")) or {}
    for key, value in validated.items():
        dotted_set(payload, key, value)
    config_path.write_text(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return {
        "ok": True,
        "pluginId": plugin_id,
        "updatedKeys": sorted(validated.keys()),
        "backup": backup,
        "reloadMode": str(plugin.get("reloadMode") or "none"),
        "reloadCommand": str(plugin.get("reloadCommand") or ""),
    }
