from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def test_spell_flight_particles_are_rate_limited_for_remote_players() -> None:
    flight = MAIN[MAIN.index("private void launchSpellFlight") : MAIN.index("private void spawnSpellFlightPattern")]
    assert "SPELL_FLIGHT_RENDER_INTERVAL_TICKS = 2" in MAIN
    assert "ticks[0] % SPELL_FLIGHT_RENDER_INTERVAL_TICKS == 0" in flight


def test_bossbar_and_hot_loops_remain_bounded() -> None:
    assert "now - bossBarLastUpdateMillis < 200L" in MAIN
    assert "MAX_ACTIVE_RIFT_PROJECTILES = 8" in MAIN
    tick = MAIN[MAIN.index("private void tick()"): MAIN.index("private void updatePadOccupancy")]
    assert "Bukkit.getWorlds()" not in tick
    assert "tickWaveMobAi();" in tick
    assert "tickMiniBosses();" in tick
    assert "playBossVisualCue(" not in tick


def test_arena_spell_flight_is_sent_only_to_nearby_event_players() -> None:
    start = MAIN.index("private void spawnSpellFlightPattern")
    end = MAIN.index("private void spawnRiftProjectileTrail", start)
    pattern = MAIN[start:end]
    assert "eventAudience()" in pattern
    assert "player.spawnParticle" in pattern
    assert "world.spawnParticle" not in pattern


def test_boss_projectile_trail_is_sent_only_to_nearby_event_players() -> None:
    start = MAIN.index("if (age[0] % SPELL_FLIGHT_RENDER_INTERVAL_TICKS == 0) {")
    end = MAIN.index("public void onRiftProjectileHit", start)
    projectile = MAIN[start:end]
    assert "for (Player viewer : eventAudience())" in projectile
    assert "spawnRiftProjectileTrail(viewer" in projectile
    trail_start = MAIN.index("private void spawnRiftProjectileTrail")
    trail_end = MAIN.index("private void spawnPatternRing", trail_start)
    trail = MAIN[trail_start:trail_end]
    assert "world.spawnParticle" not in trail


def test_boss_visual_cue_renderer_stays_viewer_scoped_and_out_of_one_tick_schedulers() -> None:
    cue = MAIN[MAIN.index("private void playBossVisualCue"): MAIN.index("private void renderBossVisualCue")]
    render = MAIN[MAIN.index("private void renderBossVisualCue"): MAIN.index("private void resetBossVisualCue")]
    assert "for (Player viewer : eventAudience())" in cue
    assert "viewer.playSound" in cue
    assert "runTaskTimer(this, () -> {" not in cue
    assert "viewer.spawnParticle" in render
    assert "world.spawnParticle" not in render
    assert "runTaskTimer(this, () -> {" not in render


def test_local_performance_probe_is_bounded_and_does_not_mutate_game_state() -> None:
    probe = (ROOT / "tests/RunEndRiftPerformanceLive.ps1").read_text(encoding="utf-8")
    for marker in (
        "DurationSeconds = 900",
        "SampleSeconds = 5",
        "EndRiftPerfA",
        "EndRiftPerfB",
        "LocalEndRiftMobCombatBot.js",
        "Get-RuntimeDiagnosticSample",
        "Assert-PlayersStillConnected",
        "connected_players",
        "event_mobs",
        "paper_cpu_percent",
        "paper_working_set_mb",
        "PERF_PASS",
        "Export-Csv",
        "environment:\\s*local",
        "codex/end-rift-event",
    ):
        assert marker in probe, marker
    for forbidden in (
        "StartEndRiftLocal",
        "minecraft:reload",
        "cmend core remove",
        "cmend core setat",
        "cmend resources",
        "save-all",
        "stop",
    ):
        assert forbidden not in probe, forbidden
