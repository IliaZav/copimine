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
