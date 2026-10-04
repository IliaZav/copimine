"""Run the production main tick with the AI flag set by disposable mob spawns."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]


def method(source, name):
    start = source.index("    private " + name)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def tick_probe(tmp_path_factory):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    directory = tmp_path_factory.mktemp("main-tick-wave-dispatch")
    java = directory / "MainTickWaveDispatch.java"
    ordinary = ["sampleRuntimeDiagnostics", "emitDiagnosticSnapshot", "tickOfflineRosterGrace",
                "updatePadOccupancy", "updateCombatHelpers", "tickWaveMobAi", "tickWaveCommanderAura",
                "tickMiniBosses", "tickEventArrowProjectiles", "refreshClientBindingsForOnlinePlayers",
                "containRealitySplitPlayers", "tickWave4ObeliskAssault", "tickStartRitual",
                "tickCurrentIntermission", "tickCoreRestoration", "tickPreBossCooldown", "tickBossCinematic",
                "issueVictoryRewards", "tickShardChannels", "tickShardPassives"]
    java.write_text('''
import me.copimine.endevent.domain.EventPhase;
public class MainTickWaveDispatch {
  boolean bootstrapped = true, enabled = true, testCombatAiMode, testWaveFrontVisualMode,
          testWave4ObeliskMode, testWaveInspectionMode, endUnlocked;
  long lastMainThreadTickAtMillis, lastMainThreadGeneration, generation = 5,
       eventTickCounter, nextVictoryRetryMillis;
  String lastMainThreadPhase; int lastMainThreadWave, activeWave, objectives, bosses;
  EventPhase phase = EventPhase.READY_FOR_PLAYERS; Object boss;
  boolean isEnabled() { return enabled; }
  Object liveBoss() { return boss; }
  void renderRitualZoneVisuals(long now) { }
  void tickWaveCompletion() { objectives++; }
  void tickBoss() { bosses++; }
  public static void main(String[] args) {
    var p = new MainTickWaveDispatch();
    p.activeWave = Integer.parseInt(args[0]);
    p.testWaveFrontVisualMode = Boolean.parseBoolean(args[1]);
    p.testCombatAiMode = Boolean.parseBoolean(args[2]);
    p.phase = EventPhase.valueOf(args[3]);
    p.bootstrapped = Boolean.parseBoolean(args[4]);
    p.boss = Boolean.parseBoolean(args[5]) ? new Object() : null;
    p.testWaveInspectionMode = Boolean.parseBoolean(args[8]);
    p.tick();
    if (p.objectives != Integer.parseInt(args[6]) || p.bosses != Integer.parseInt(args[7]))
      throw new AssertionError("objectives=" + p.objectives + " bosses=" + p.bosses);
  }
''' + "\n".join("void " + name + "() { }" for name in ordinary) + "\n"
                    + method(source, "void tick()") + "\n" + method(source, "boolean isCombatPhase()")
                    + "\n}", encoding="utf-8")
    phase = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java"
    result = subprocess.run(["javac", "-J-Xmx128m", "-d", str(directory), str(phase), str(java)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


SCENARIOS = [(wave, True, ai, "READY_FOR_PLAYERS", True, False, 1, 0, False)
             for wave in range(1, 8) for ai in [False, True]]
SCENARIOS += [(wave, False, False, "WAVE_" + str(wave), True, False, 1, 0, False) for wave in range(1, 8)]
SCENARIOS += [(5, True, True, "READY_FOR_PLAYERS", False, False, 0, 0, False),
              (0, False, True, "READY_FOR_PLAYERS", True, True, 0, 1, False),
              (0, False, False, "READY_FOR_PLAYERS", True, False, 0, 0, False),
              (0, False, False, "BOSS_ACTIVE", True, True, 0, 1, False),
              (5, True, True, "READY_FOR_PLAYERS", True, False, 0, 0, True),
              (7, True, False, "READY_FOR_PLAYERS", True, False, 0, 0, True)]


@pytest.mark.parametrize("wave,sandbox,ai,phase,ready,boss,objectives,boss_ticks,inspection", SCENARIOS)
def test_main_tick_updates_the_same_wave_controller(tick_probe, wave, sandbox, ai, phase, ready, boss,
                                                    objectives, boss_ticks, inspection):
    result = subprocess.run(["java", "-Xms8m", "-Xmx64m", "-cp", str(tick_probe), "MainTickWaveDispatch",
                             str(wave), str(sandbox).lower(), str(ai).lower(), phase, str(ready).lower(),
                             str(boss).lower(), str(objectives), str(boss_ticks), str(inspection).lower()], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
