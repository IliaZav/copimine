from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AUTH_EFFECTS = (
    ROOT
    / "minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java"
)
HARDEN_RUNTIME = ROOT / "deploy/shared/harden_game_runtime.py"
HARDEN_SCRIPT = ROOT / "deploy/ubuntu/apply_game_hardening.sh"


def load_runtime_hardening_module():
    spec = importlib.util.spec_from_file_location("copimine_harden_game_runtime", HARDEN_RUNTIME)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_unauthenticated_captcha_command_is_allowed_to_reach_authme():
    source = AUTH_EFFECTS.read_text(encoding="utf-8")

    assert '"captcha" -> true' in source


def test_essentials_respawn_override_is_disabled_for_vanilla_bed_respawn(tmp_path):
    runtime = load_runtime_hardening_module()
    config = tmp_path / "config.yml"
    config.write_text(
        "respawn-listener-priority: high\n"
        "respawn-at-home: false\n"
        "respawn-at-home-bed: true\n",
        encoding="utf-8",
    )

    runtime.sync_essentials(config)

    result = config.read_text(encoding="utf-8")
    assert "respawn-listener-priority: \"none\"" in result
    assert "respawn-at-home: false" in result
    assert "respawn-at-home-bed: true" in result


def test_essentials_is_resynced_after_plugin_startup():
    source = HARDEN_SCRIPT.read_text(encoding="utf-8")
    post_start = source.index("copimine_apply_post_start_game_hardening")
    assert "copimine_sync_game_runtime_hardening" in source[post_start:]
