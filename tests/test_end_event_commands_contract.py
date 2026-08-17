from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
MAIN = (PLUGIN / "src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (PLUGIN / "config.yml").read_text(encoding="utf-8")


def test_v5_admin_command_matrix_and_confirmation_guards() -> None:
    for command in (
        "core set", "arena pos1", "arena pos2", "arena clear confirm",
        "gate preview", "gate restore", "portalroom set", "resources add",
        "resources reset confirm", "ritual start", "ritual cancel confirm",
        "cleanup confirm", "reset confirm", "unlock confirm",
        "wave spawn", "boss spawn official confirm", "boss phase",
        "boss kill", "boss spell", "client status", "test wave", "test boss",
    ):
        assert command.split()[0] in MAIN
    assert "Повтори /cmend resources reset confirm" in MAIN
    assert "Повтори /cmend ritual cancel confirm" in MAIN
    assert "Повтори /cmend reset confirm" in MAIN
    assert "Повтори /cmend unlock confirm" in MAIN
    assert "simulate-victory" in MAIN


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
    assert "addConfiguredDrops(event, config.testLoot())" in MAIN
    for loot_accessor in ("config.eliteLoot()", "config.finalWaveLoot()", "config.waveMobLoot()"):
        assert loot_accessor in MAIN
    assert "addConfiguredDrops(event, loot)" in MAIN
    assert "cleanupOwnedEntities(eventId, generation)" in MAIN
    assert "entity.remove()" in MAIN
