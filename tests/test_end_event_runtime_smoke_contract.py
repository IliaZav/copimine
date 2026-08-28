from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SMOKE = (ROOT / "tests/RunEndRiftRuntimeSmoke.ps1").read_text(encoding="utf-8")
BOT = (ROOT / "tests/LocalEndRiftBot.js").read_text(encoding="utf-8")
BOSS_BOT = (ROOT / "tests/LocalEndRiftBossCombatBot.js").read_text(encoding="utf-8")
BOSS_PACKET_BOT = (ROOT / "tests/LocalEndRiftBossPacketBot.js").read_text(encoding="utf-8")
LIVE_BOSS = (ROOT / "tests/RunEndRiftBossDamageLive.ps1").read_text(encoding="utf-8")
AI_PHASES = (ROOT / "tests/RunEndRiftAiPhasesLive.ps1").read_text(encoding="utf-8")
PACKET_TRACE = (ROOT / "tests/PacketUseEntityTracePlugin.java").read_text(encoding="utf-8")
START = (ROOT / "tests/StartEndRiftLocal.ps1").read_text(encoding="utf-8")
RECOVERY = (ROOT / "tests/RunEndRiftRecoverySmoke.ps1").read_text(encoding="utf-8")


def test_runtime_smoke_is_local_only_and_does_not_manage_production_processes() -> None:
    assert "end-rift-server" in SMOKE
    assert "environment:\\s*local" in SMOKE
    assert "127.0.0.1" in SMOKE
    assert "25576" in SMOKE
    assert "CopiMineEndEvent services ready" in SMOKE
    assert "cmartifacts reload" in SMOKE
    assert "CopiMineArtifacts catalog reloaded." in SMOKE
    assert "CopiMineEconomyCore PostgreSQL is ready." in SMOKE
    assert "Stop-Process" not in SMOKE
    assert "Remove-Item" not in SMOKE
    assert "cmend client status" in SMOKE


def test_runtime_smoke_covers_refusal_paths_and_typed_dependencies() -> None:
    for expected in (
        "CopiMineEndEvent", "CopiMineWorldCore", "CopiMineArtifacts",
        "UNCONFIGURED", "UNLOCKED", "requiredPlayers=.*0", "event world", "Core", "cmend cleanup",
        "cmend ritual cancel", "cmend resources reset", "official",
    ):
        assert expected in SMOKE
    assert "clientgate" not in SMOKE
    assert "channel=" in SMOKE


def test_local_bot_can_delay_actions_until_authentication_has_time_to_finish() -> None:
    assert "END_RIFT_BOT_ACTION_DELAY_MS" in BOT
    assert "ACTION ${username} ${command}" in BOT
    assert "add_resource_pack" in BOT
    assert "resource_pack_receive" in BOT
    assert "teleport_confirm" in BOT
    assert "position_look" in BOT
    assert "process.argv.slice(4)" in BOT
    assert "cliActions" in BOT
    assert "client.once('login', startSession)" in BOT


def test_local_boss_probe_uses_real_survival_attack_packets_and_never_production_paths() -> None:
    for expected in (
        "Mineflayer", "use_entity", "bot.attack(", "RESOURCE_PACK",
        "auth: 'offline'", "127.0.0.1", "25566", "attackDelayMs",
        "refreshedDistance", "setQuickBarSlot",
    ):
        assert expected in BOSS_BOT
    assert "production" in BOSS_BOT.lower()
    assert "Stop-Process" not in BOSS_BOT
    assert "Remove-Item" not in BOSS_BOT


def test_low_level_boss_probe_matches_uuid_and_sends_use_entity_attack() -> None:
    for expected in (
        "use_entity", "objectUUID", "END_RIFT_BOSS_UUID", "mouse: 1",
        "END_RIFT_TARGET_ENTITY_TYPE", "arm_animation", "resource_pack_receive", "25566",
    ):
        assert expected in BOSS_PACKET_BOT
    assert "no command-based" in BOSS_PACKET_BOT
    assert "boss damage" in BOSS_PACKET_BOT
    assert "127.0.0.1" in BOSS_PACKET_BOT


def test_live_boss_damage_probe_crosses_absorption_before_player_attack() -> None:
    for expected in (
        "environment:\\s*local", "cmend boss damage 1500", "LocalEndRiftBossCombatBot.js",
        "afterAbsorption", "1.8D", " 90 0", "gamemode survival",
        "END_RIFT_BOSS_ATTACK_DELAY_MS",
        "LIVE_BOSS_DAMAGE_PASS", "LIVE_BOSS_DAMAGE_CLEANUP_PASS",
        "PLAYER_END .*attacks=[1-9]", "cmend boss kill cleanup", "event-mobs=0",
    ):
        assert expected in LIVE_BOSS
    assert "production" in LIVE_BOSS.lower()
    assert "Remove-Item" not in LIVE_BOSS
    assert "EnvironmentVariables['END_RIFT_BOSS_UUID']" in LIVE_BOSS
    assert ".Environment['END_RIFT_BOSS_UUID']" not in LIVE_BOSS
    assert "if ($null -ne $startInfo.ArgumentList)" in LIVE_BOSS
    assert "$startInfo.Arguments" in LIVE_BOSS
    assert LIVE_BOSS.index("Wait-LocalBot") < LIVE_BOSS.index("Get-BossPosition $bossUuid")
    assert "AttackGameMode" in LIVE_BOSS
    assert "if ($AttackGameMode -eq 'survival')" in LIVE_BOSS


def test_live_ai_phase_probe_covers_all_waves_and_boss_stage_boundaries_locally() -> None:
    for expected in (
        "environment:\\s*local", "LocalEndRiftBot.js", "cmend test wave",
        "cmend debug ai", "mobile", "targeted", "outside", "onCore",
        "AWAKENING", "HUNTER", "DISTORTION", "ABSORPTION", "CATASTROPHE",
        "cmend boss damage 500", "cmend boss damage 250", "cmend wave clear",
        "cmend boss kill cleanup",
    ):
        assert expected in AI_PHASES
    assert "production" in AI_PHASES.lower()
    assert "Remove-Item" not in AI_PHASES
    assert "Stop-Process" not in AI_PHASES


def test_local_packet_trace_is_scoped_to_use_entity_and_unregistered_on_disable() -> None:
    assert "PacketType.Play.Client.USE_ENTITY" in PACKET_TRACE
    assert "USE_ENTITY_TRACE" in PACKET_TRACE
    assert "removePacketListeners(this)" in PACKET_TRACE


def test_runtime_smoke_exposes_real_core_and_rune_visual_health() -> None:
    for expected in (
        "coreOverlay=",
        "coreOverlay=true",
        "runes=",
        "END_EVENT_PHYSICAL_VISUALS",
        "NoClassDefFoundError",
    ):
        assert expected in SMOKE
    assert "if ($status -match 'coreOverlay=true')" in SMOKE


def test_local_start_script_is_bounded_to_the_isolated_runtime() -> None:
    for expected in ("local-runtime", "25566", "25576", "environment:\\s*local"):
        assert expected in START
    assert "StartsWith($localPrefix" in START
    assert "WindowStyle Hidden" in START
    assert "Stop-Process" not in START
    assert "Remove-Item" not in START


def test_recovery_smoke_requires_unlocked_durable_state_and_stays_local() -> None:
    for expected in (
        "local-runtime", "state=.*(?:UNLOCKED|UNCONFIGURED)", "VICTORY_COMPLETE", "persistent phase=",
        "END_EVENT_STATE forced=UNLOCKED",
        "END_EVENT_STATE forced=UNCONFIGURED",
        "WorldCore already reports End unlocked; preserving active event",
        "NoClassDefFoundError",
        "ClassNotFoundException",
    ):
        assert expected in RECOVERY
    assert "Stop-Process" not in RECOVERY
    assert "Remove-Item" not in RECOVERY
