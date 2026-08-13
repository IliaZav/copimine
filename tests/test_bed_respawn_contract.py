"""Regression contract for vanilla bed respawn with EssentialsX Spawn enabled."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ESSENTIALS_CONFIG = ROOT / "minecraft" / "server" / "plugins" / "Essentials" / "config.yml"
PATCH_SCRIPT = ROOT / "deploy" / "ubuntu" / "enable_bed_respawn.sh"


def _setting(text: str, key: str) -> str:
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith(f"{key}:") and not stripped.startswith("#"):
            return stripped.split(":", 1)[1].strip().lower()
    raise AssertionError(f"missing active Essentials setting: {key}")


def test_essentials_spawn_delegates_respawn_to_a_player_bed() -> None:
    config = ESSENTIALS_CONFIG.read_text(encoding="utf-8")

    assert _setting(config, "respawn-listener-priority") == "none"
    assert _setting(config, "respawn-at-home") == "false"
    assert _setting(config, "respawn-at-home-bed") == "true"
    assert _setting(config, "random-respawn-location") in {'"none"', "none"}


def test_bed_respawn_patch_is_scoped_and_keeps_a_restore_copy() -> None:
    script = PATCH_SCRIPT.read_text(encoding="utf-8")

    assert "respawn-listener-priority: none" in script
    assert "respawn-at-home: false" in script
    assert "respawn-at-home-bed: true" in script
    assert "cp -a -- \"$config\" \"$backup_dir/config.yml\"" in script
    assert "/opt/copimine-backups" in script
    assert "world" not in script.lower()
    assert "postgres" not in script.lower()
