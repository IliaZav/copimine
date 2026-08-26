"""Regression contracts for vanilla per-player bed respawn."""

from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "StartEndRiftLocalUserSession.ps1"
HARDEN_RUNTIME = ROOT / "deploy" / "shared" / "harden_game_runtime.py"
BED_PROBE = ROOT / "tests" / "LocalBedRespawnBot.js"
MINEFLAYER_BED_PROBE = ROOT / "tests" / "LocalBedRespawnMineflayerBot.js"


def load_runtime_hardening_module():
    spec = importlib.util.spec_from_file_location("copimine_harden_game_runtime", HARDEN_RUNTIME)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_local_runner_disables_essentials_respawn_override_before_paper_starts() -> None:
    source = RUNNER.read_text(encoding="utf-8")

    assert "function Ensure-LocalVanillaBedRespawn" in source
    assert "respawn-listener-priority" in source
    assert "respawn-listener-priority' -Value 'none'" in source
    assert source.index("Ensure-LocalVanillaBedRespawn") < source.index("Start-LocalPaper")


def test_deployment_hardening_disables_essentials_respawn_override(tmp_path: Path) -> None:
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


def test_local_behavior_probe_uses_real_bed_interaction_and_respawn_ack() -> None:
    source = BED_PROBE.read_text(encoding="utf-8")

    assert "client.write('block_place'" in source
    assert "location: target" in source
    assert "meta?.name === 'death_combat_event'" in source
    assert "client.write('client_command', { actionId: 0 })" in source


def test_mineflayer_behavior_probe_uses_normal_client_block_interaction() -> None:
    source = MINEFLAYER_BED_PROBE.read_text(encoding="utf-8")

    assert "bot.blockAt(bedPosition)" in source
    assert "bot.sleep(block)" in source
    assert "bot.on('death'" in source
    assert "bot.respawn()" in source
