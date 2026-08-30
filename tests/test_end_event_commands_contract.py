from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
MAIN = (PLUGIN / "src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (PLUGIN / "config.yml").read_text(encoding="utf-8")


def test_v5_admin_command_matrix_and_confirmation_guards() -> None:
    for command in (
        "core set", "arena pos1", "arena pos2", "arena clear confirm",
        "gate preview", "gate close", "gate restore", "portalroom set", "resources add",
        "resources reset confirm", "ritual start", "ritual cancel confirm",
        "cleanup confirm", "reset confirm", "unlock confirm",
        "wave spawn", "boss spawn official confirm", "boss phase",
        "boss kill", "boss spell", "client status", "test wave", "test boss",
        "test run creative", "test teleport",
    ):
        assert command.split()[0] in MAIN
    assert "Повтори /cmend resources reset confirm" in MAIN
    assert "Повтори /cmend ritual cancel confirm" in MAIN
    assert "Повтори /cmend reset confirm" in MAIN
    assert "Повтори /cmend unlock confirm" in MAIN
    assert "simulate-victory" in MAIN
    assert "Тестовая волна не создана: сначала настрой Core" in MAIN
    assert "Волна не создана: сначала настрой Core" in MAIN
    assert "/cmend test run creative" in MAIN
    assert "return isConfigured() && coreLocation() != null;" in MAIN
    assert "if (!isConfigured()) {" in MAIN
    assert "/cmend gate close" in MAIN


def test_dangerous_paths_are_never_console_or_production_mutations() -> None:
    assert "Bukkit.dispatchCommand" not in MAIN
    assert "clientgate" not in MAIN.lower()
    assert "wipe" not in MAIN.lower()
    assert "environment: local" in CONFIG
    assert "environment=production" in MAIN
    assert "WorldCore service" in MAIN


def test_event_owned_loot_is_configured_and_cleanup_has_no_death_path() -> None:
    for section in ("wave-mob:", "elite:", "final-wave:", "test:"):
        assert section in CONFIG
    assert "addConfiguredDrops(event, config.lootProfile(\"test\")" in MAIN
    config_source = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")
    for loot_accessor in ("lootProfile(\"elite-enderman\")", "lootProfile(\"final-wave\")",
                          "lootProfile(\"common-enderman\")", "lootProfile(\"spider\")"):
        assert loot_accessor.replace("lootProfile(", "").rstrip(")\"") in MAIN + config_source
    assert "addConfiguredDrops(event, config.lootProfile(profile), profile)" in MAIN
    spawn = MAIN[MAIN.index("private void spawnEnderman"):
                 MAIN.index("private Entity spawnOwnedMob")]
    assert "elite ? \"elite-enderman\"" in spawn
    assert "lootIssuedEntityUuids.add" in MAIN
    assert "cleanupOwnedEntities(eventId, generation)" in MAIN
    assert "entity.remove()" in MAIN
