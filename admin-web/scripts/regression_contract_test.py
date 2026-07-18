#!/usr/bin/env python3
"""Static contracts for behaviors requiring a running Minecraft server."""
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT.parent / "copimine-artifacts"
ARTIFACTS_SOURCE = ARTIFACTS / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
ITEMS = ARTIFACTS / "items.yml"
DEPLOYED_ITEMS = ROOT.parent / "minecraft" / "server" / "plugins" / "CopiMineArtifacts" / "items.yml"
TREASURY = ROOT / "frontend" / "assets" / "js" / "player" / "treasury-pages.js"


def require(text: str, needle: str, subject: str) -> None:
    assert needle in text, f"{subject}: missing {needle!r}"


def main() -> None:
    source = ARTIFACTS_SOURCE.read_text(encoding="utf-8")
    items = ITEMS.read_text(encoding="utf-8")
    deployed_items = DEPLOYED_ITEMS.read_text(encoding="utf-8")
    treasury = TREASURY.read_text(encoding="utf-8")
    miner = items[items.index("id: copimine_miner_pickaxe"):items.index("id: craftsman_hammer")]
    hammer = items[items.index("id: craftsman_hammer"):items.index("id: kozyrny_tuz_pozdnyakova")]

    require(source, '"MINER_3X3"', "artifact effect registry")
    require(source, "breakMinerArea", "artifact 3x3 handler")
    require(miner, "effect: MINER_3X3", "CopiMine Miner catalog entry")
    require(miner, "Копает 3x3.", "CopiMine Miner lore")
    require(source, "PotionEffectType.HASTE, 3600, 1", "Craftsman Hammer implementation")
    require(hammer, "effect: HASTE_BURST_LONG", "Craftsman Hammer catalog entry")
    require(hammer, "ПКМ в воздух: Спешка II на 3 минуты.", "Craftsman Hammer lore")
    require(hammer, "Перезарядка: 20 секунд.", "Craftsman Hammer cooldown lore")
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
