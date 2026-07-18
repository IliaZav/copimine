#!/usr/bin/env python3
"""Static contracts for behaviors requiring a running Minecraft server."""
from __future__ import annotations

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT.parent / "copimine-artifacts"
ARTIFACTS_SOURCE = ARTIFACTS / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
ITEMS = ARTIFACTS / "items.yml"
DEPLOYED_ITEMS = ROOT.parent / "minecraft" / "server" / "plugins" / "CopiMineArtifacts" / "items.yml"
TREASURY = ROOT / "frontend" / "assets" / "js" / "player" / "treasury-pages.js"
RESOURCEPACK = ROOT.parent / "resourcepacks"
RESOURCEPACK_MODELS = RESOURCEPACK / "models_manifest.json"
RESOURCEPACK_TEXTURE_SOURCES = RESOURCEPACK / "item_texture_sources.json"
RESOURCEPACK_ARTIFACT_MODELS = RESOURCEPACK / "src" / "assets" / "copimine" / "models" / "item" / "artifacts"
RESOURCEPACK_ARTIFACT_TEXTURES = RESOURCEPACK / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"


def require(text: str, needle: str, subject: str) -> None:
    assert needle in text, f"{subject}: missing {needle!r}"


def item_block(items: str, item_id: str) -> str:
    match = re.search(rf"(?ms)^  - id: {re.escape(item_id)}\r?\n.*?(?=^  - id:|\Z)", items)
    assert match is not None, f"artifact catalog: item {item_id!r} is missing"
    return match.group(0)


def method_block(source: str, signature: str) -> str:
    match = re.search(rf"(?s){re.escape(signature)}.*?(?=\r?\n\s*private )", source)
    assert match is not None, f"artifact source: method {signature!r} is missing"
    return match.group(0)


def require_item_resource(item_id: str, models_manifest: str, texture_sources: str) -> None:
    require(models_manifest, f'"id": "{item_id}"', "resource-pack model manifest")
    require(models_manifest, f'"model": "copimine:item/artifacts/{item_id}"', "resource-pack model manifest")
    require(models_manifest, f'"texture": "copimine:item/artifacts/{item_id}"', "resource-pack model manifest")
    require(texture_sources, f'"id":"{item_id}"', "resource-pack item texture sources")

    model_path = RESOURCEPACK_ARTIFACT_MODELS / f"{item_id}.json"
    texture_path = RESOURCEPACK_ARTIFACT_TEXTURES / f"{item_id}.png"
    assert model_path.is_file(), f"resource-pack model is missing: {model_path}"
    assert texture_path.is_file() and texture_path.stat().st_size > 0, f"resource-pack texture is missing or empty: {texture_path}"
    require(model_path.read_text(encoding="utf-8"), f"copimine:item/artifacts/{item_id}", "resource-pack item model")


def main() -> None:
    source = ARTIFACTS_SOURCE.read_text(encoding="utf-8")
    items = ITEMS.read_text(encoding="utf-8")
    deployed_items = DEPLOYED_ITEMS.read_text(encoding="utf-8")
    treasury = TREASURY.read_text(encoding="utf-8")
    models_manifest = RESOURCEPACK_MODELS.read_text(encoding="utf-8")
    texture_sources = RESOURCEPACK_TEXTURE_SOURCES.read_text(encoding="utf-8")
    miner = item_block(items, "copimine_miner_pickaxe")
    hammer = item_block(items, "craftsman_hammer")
    ace = item_block(items, "kozyrny_tuz_pozdnyakova")
    wind_hammer = method_block(source, "private boolean triggerWindHammer(Player player, Block ground) {")
    interact_handler = method_block(source, "public void onArtifactInteract(PlayerInteractEvent var1) {")

    require(source, '"MINER_3X3"', "artifact effect registry")
    require(source, "breakMinerArea", "artifact 3x3 handler")
    require(miner, "effect: MINER_3X3", "CopiMine Miner catalog entry")
    require(miner, "Копает 3x3.", "CopiMine Miner lore")

    require(hammer, 'name: "&6Молот Ветра"', "Wind Hammer catalog entry")
    require(hammer, "material: MACE", "Wind Hammer catalog entry")
    require(hammer, "custom_model_data: 10012", "Wind Hammer catalog entry")
    require(hammer, "effect: WIND_HAMMER", "Wind Hammer catalog entry")
    require(hammer, "cooldown_seconds: 60", "Wind Hammer cooldown")
    require(hammer, "ПКМ по земле: удар ветра.", "Wind Hammer lore")
    require(hammer, "Все существа в радиусе 10 блоков взлетают на 5 блоков.", "Wind Hammer lore")
    require(hammer, "Левитация: 4 секунды.", "Wind Hammer lore")
    require(hammer, "Перезарядка: 1 минута.", "Wind Hammer lore")
    assert "HASTE_BURST_LONG" not in hammer, "Wind Hammer must not use the retired haste effect"
    require(interact_handler, '"WIND_HAMMER".equals(var4)', "Wind Hammer interaction")
    require(interact_handler, "var5 != Action.RIGHT_CLICK_BLOCK", "Wind Hammer interaction")
    require(interact_handler, "!var1.getClickedBlock().getType().isSolid()", "Wind Hammer interaction")
    require(interact_handler, "this.triggerWindHammer(var2, var1.getClickedBlock())", "Wind Hammer interaction")
    require(wind_hammer, "getNearbyEntities(center, 10.0D, 10.0D, 10.0D)", "Wind Hammer radius")
    require(wind_hammer, "distanceSquared(center) > 100.0D", "Wind Hammer radius")
    require(wind_hammer, "setY(Math.max(living.getVelocity().getY(), 1.0D))", "Wind Hammer launch")
    require(wind_hammer, "PotionEffectType.LEVITATION, 80, 0", "Wind Hammer levitation")

    require(ace, "source: ADMIN_ONLY", "Pozdnyakov Ace source")
    require(ace, "effect: POZDNYAKOV_ACE", "Pozdnyakov Ace effect")
    require(ace, "за помощь в развитии проекта CopiMine", "Pozdnyakov Ace lore")
    require(source, 'if ("ADMIN_ONLY".equalsIgnoreCase(this.str(var16.get("source"))))', "admin-only catalog handling")
    require(source, "this.adminOnlyCatalogItems.add", "admin-only store exclusion")
    require(source, "this.isAdminOnlyCatalogItem(itemId)", "admin-only grant restriction")
    require(source, '"POZDNYAKOV_ACE".equalsIgnoreCase', "Pozdnyakov Ace effect handler")
    require(source, "PotionEffectType.NAUSEA, 100, 2", "Pozdnyakov Ace attacker effect")
    require(source, "originalType == Material.LAVA", "Pozdnyakov Ace lava conversion")
    require(source, "block.getType() == Material.MAGMA_BLOCK", "Pozdnyakov Ace safe lava restore")

    require_item_resource("craftsman_hammer", models_manifest, texture_sources)
    require_item_resource("kozyrny_tuz_pozdnyakova", models_manifest, texture_sources)
    assert items == deployed_items, "deployed artifact catalog differs from source catalog"
    require(items, "Освобождение от налогов на 3 месяца.", "tax clock lore")
    require(treasury, "formatTaxExemptionRemaining", "bank tax-exemption rendering")
    require(treasury, "taxExemptionCountdown", "bank minute countdown")
    require(treasury, "window.setTimeout(render, 60000 - (Date.now() % 60000))", "site tax-exemption minute refresh")
    require(source, 'Path.of("/opt/copimine/admin-web/.env")', "deployed artifact database environment")
    require((ROOT / "frontend" / "assets" / "style.css").read_text(encoding="utf-8"), '"./css/release-ui.css"', "public stylesheet entrypoint")
    require((ROOT / "frontend" / "assets" / "cabinet.css").read_text(encoding="utf-8"), '"./css/release-ui.css"', "cabinet stylesheet entrypoint")


if __name__ == "__main__":
    main()
