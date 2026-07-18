from __future__ import annotations

import re
from pathlib import Path
from typing import Any

try:
    import yaml  # type: ignore
except Exception:  # pragma: no cover
    yaml = None

APP_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = APP_ROOT.parent
DEFAULT_ITEMS_FILE = PROJECT_ROOT / "copimine-artifacts" / "items.yml"

MINECRAFT_COLOR_RE = re.compile(r"§.|&[0-9A-FK-ORa-fk-or]")


def strip_minecraft_format(value: str) -> str:
    return MINECRAFT_COLOR_RE.sub("", str(value or ""))


def _safe_yaml_load(items_file: Path) -> dict[str, Any]:
    if not yaml or not items_file.exists():
        return {}
    try:
        return yaml.safe_load(items_file.read_text(encoding="utf-8", errors="replace")) or {}
    except Exception:
        return {}


def _string_list(values: Any) -> list[str]:
    if not isinstance(values, list):
        return []
    out: list[str] = []
    for value in values:
        text = strip_minecraft_format(str(value or "")).strip()
        if text:
            out.append(text)
    return out


def _item_description(lore: list[str], fallback: str = "") -> str:
    for line in lore:
        text = strip_minecraft_format(line).strip()
        if text:
            return text
    return strip_minecraft_format(fallback).strip()


def load_commerce_catalog(items_file: Path | None = None, fallback_base_url: str = "") -> dict[str, Any]:
    source = items_file or DEFAULT_ITEMS_FILE
    raw = _safe_yaml_load(source)

    ar_items: list[dict[str, Any]] = []
    for entry in raw.get("items") or []:
        if not isinstance(entry, dict):
            continue
        item_id = str(entry.get("id") or "").strip().lower()
        if not item_id:
            continue
        if str(entry.get("source") or "AR_SHOP").strip().upper() == "ADMIN_ONLY":
            continue
        lore = _string_list(entry.get("lore") or [])
        ar_items.append(
            {
                "item_id": item_id,
                "display_name": strip_minecraft_format(str(entry.get("name") or item_id)),
                "description": _item_description(lore, "Официальный предмет CopiMine."),
                "image_url": f"/assets/item-textures/{item_id}.png",
                "category": str(entry.get("category") or "RP").strip().upper(),
                "base_material": str(entry.get("material") or "STONE").strip().upper(),
                "price_ar": int(entry.get("price_ar") or 0),
                "supply_limit": int(entry.get("supply_limit") or 0),
                "per_player_limit": int(entry.get("per_player_limit") or 0),
                "enabled": True,
                "source": str(entry.get("source") or "AR_SHOP").strip().upper(),
                "cooldown_seconds": int(entry.get("cooldown_seconds") or 0),
                "effect_profile_id": str(entry.get("effect") or "").strip().upper(),
                "repairable": bool(entry.get("repairable", True)),
                "custom_model_data": int(entry.get("custom_model_data") or 0),
                "visual_effect_id": str(entry.get("visual_effect_id") or "").strip().upper(),
                "lore": lore,
            }
        )
    ar_items.sort(key=lambda row: (int(row.get("price_ar") or 0), str(row.get("display_name") or row.get("item_id") or "")))

    donation_root = raw.get("donation-catalog") or {}
    base_url = str(donation_root.get("website-base-url") or fallback_base_url or "").strip().rstrip("/")
    donation_items: list[dict[str, Any]] = []
    donation_by_id: dict[str, dict[str, Any]] = {}
    for entry in donation_root.get("items") or []:
        if not isinstance(entry, dict):
            continue
        item_id = str(entry.get("item-id") or "").strip().lower()
        if not item_id:
            continue
        lore = _string_list(entry.get("lore") or [])
        effect_description = str(entry.get("effect-description") or "").strip()
        row = {
            "item_id": item_id,
            "display_name": strip_minecraft_format(str(entry.get("display-name") or item_id)),
            "description": _item_description(lore, effect_description or "Именной предмет CopiMine."),
            "image_url": f"/assets/item-textures/{item_id}.png",
            "base_material": str(entry.get("base-material") or "PAPER").strip().upper(),
            "price_donation": int(entry.get("price-donation") or 0),
            "enabled": bool(entry.get("enabled", True)),
            "source": str(entry.get("source") or "DONATION_SHOP").strip().upper(),
            "owner_bound": bool(entry.get("owner-bound", True)),
            "reclaim_policy": str(entry.get("reclaim-policy") or "LOSS_ONLY").strip().upper(),
            "consume_policy": str(entry.get("consume-policy") or "PERSISTENT").strip().upper(),
            "effect_profile_id": str(entry.get("effect-profile-id") or "").strip().upper(),
            "effect_description": effect_description,
            "cooldown_seconds": int(entry.get("cooldown-seconds") or 0),
            "proc_chance": float(entry.get("proc-chance") or 0.0),
            "max_stack": int(entry.get("max-stack") or 1),
            "repairable": bool(entry.get("repairable", False)),
            "custom_texture_mode_allowed": bool(entry.get("custom-texture-mode-allowed", True)),
            "custom_model_data": int(entry.get("custom-model-data") or 0),
            "visual_effect_id": str(entry.get("visual-effect-id") or "").strip().upper(),
            "lore": lore,
        }
        donation_items.append(row)
        donation_by_id[item_id] = row
    donation_items.sort(key=lambda row: (not bool(row.get("enabled")), int(row.get("price_donation") or 0), str(row.get("display_name") or row.get("item_id") or "")))

    return {
        "ar": {
            "items": ar_items,
            "byId": {str(item.get("item_id") or ""): item for item in ar_items},
        },
        "donation": {
            "catalogVersion": int(donation_root.get("catalog-version") or 0),
            "updatedAt": int(donation_root.get("updated-at") or 0),
            "websiteBaseUrl": base_url,
            "items": donation_items,
            "byId": donation_by_id,
        },
    }


def donation_catalog_snapshot(items_file: Path | None = None, fallback_base_url: str = "") -> dict[str, Any]:
    return load_commerce_catalog(items_file, fallback_base_url)["donation"]


def ar_catalog_snapshot(items_file: Path | None = None) -> dict[str, Any]:
    return load_commerce_catalog(items_file, "")["ar"]


def admin_gift_catalog_snapshot(items_file: Path | None = None) -> dict[str, Any]:
    """Return the complete admin-gift catalog, including hidden items.

    Public shop snapshots intentionally omit ADMIN_ONLY entries.  The admin
    card needs a separate, explicit snapshot so a hidden item can be granted
    without accidentally becoming purchasable by players.
    """
    source = items_file or DEFAULT_ITEMS_FILE
    raw = _safe_yaml_load(source)
    buckets: dict[str, list[dict[str, Any]]] = {"AR": [], "DONATION": [], "HIDDEN": []}

    for entry in raw.get("items") or []:
        if not isinstance(entry, dict):
            continue
        item_id = str(entry.get("id") or "").strip().lower()
        if not item_id:
            continue
        item_source = str(entry.get("source") or "AR_SHOP").strip().upper()
        bucket = "HIDDEN" if item_source == "ADMIN_ONLY" else "AR"
        lore = _string_list(entry.get("lore") or [])
        buckets[bucket].append(
            {
                "item_id": item_id,
                "display_name": strip_minecraft_format(str(entry.get("name") or item_id)),
                "description": _item_description(lore, "Официальный предмет CopiMine."),
                "image_url": f"/assets/item-textures/{item_id}.png",
                "base_material": str(entry.get("material") or "STONE").strip().upper(),
                "category": str(entry.get("category") or "RP").strip().upper(),
                "source": item_source,
                "enabled": True,
                "price_ar": int(entry.get("price_ar") or 0),
                "lore": lore,
            }
        )

    donation_root = raw.get("donation-catalog") or {}
    for entry in donation_root.get("items") or []:
        if not isinstance(entry, dict):
            continue
        item_id = str(entry.get("item-id") or "").strip().lower()
        if not item_id:
            continue
        lore = _string_list(entry.get("lore") or [])
        effect_description = str(entry.get("effect-description") or "").strip()
        buckets["DONATION"].append(
            {
                "item_id": item_id,
                "display_name": strip_minecraft_format(str(entry.get("display-name") or item_id)),
                "description": _item_description(lore, effect_description or "Именной предмет CopiMine."),
                "image_url": f"/assets/item-textures/{item_id}.png",
                "base_material": str(entry.get("base-material") or "PAPER").strip().upper(),
                "category": "DONATION",
                "source": str(entry.get("source") or "DONATION_SHOP").strip().upper(),
                "enabled": bool(entry.get("enabled", True)),
                "price_donation": int(entry.get("price-donation") or 0),
                "lore": lore,
            }
        )

    for rows in buckets.values():
        rows.sort(key=lambda row: str(row.get("display_name") or row.get("item_id") or ""))
    return {"categories": buckets, "items": [item for rows in buckets.values() for item in rows]}
