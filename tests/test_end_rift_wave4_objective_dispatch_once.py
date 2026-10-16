"""Keep the disposable Wave 4 harness on the same single objective dispatch as production."""
from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]


def method(source, signature):
    start = source.index("    private " + signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def dispatch_probe(tmp_path_factory):
    tmp_path = tmp_path_factory.mktemp("wave4-objective-dispatch")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    ordinary = [
        "sampleRuntimeDiagnostics", "emitDiagnosticSnapshot", "tickOfflineRosterGrace",
        "updatePadOccupancy", "updateCombatHelpers", "tickWaveCommanderAura",
        "tickMiniBosses", "tickEliteAbilityState", "tickEventArrowProjectiles",
        "refreshClientBindingsForOnlinePlayers", "tickUncommittedRealitySplitBoundaryRollbacks",
        "containRealitySplitPlayers",
        "issueVictoryRewards", "tickShardChannels", "tickShardPassives",
    ]
    java = tmp_path / "Wave4ObjectiveDispatchProbe.java"
    java.write_text(
        """
import me.copimine.endevent.domain.EventPhase;
public class Wave4ObjectiveDispatchProbe {
  boolean bootstrapped = true, enabled = true, testCombatAiMode, testWaveFrontVisualMode = true,
          testWave4ObeliskMode = true, testWaveInspectionMode, endUnlocked;
  long lastMainThreadTickAtMillis, lastMainThreadGeneration, generation = 5,
       eventTickCounter, nextVictoryRetryMillis;
  String lastMainThreadPhase;
  int lastMainThreadWave, activeWave = 4, objectives, wave4Objectives, fireSchedules;
  boolean cancelInAi, restartInObjective, cancelInObjective;
  EventPhase phase = EventPhase.READY_FOR_PLAYERS;
  BridgeTicker endEventBridgeV4 = new BridgeTicker();
  static class BridgeTicker { int ticks; void tick() { ticks++; } }
  boolean isEnabled() { return enabled; }
  Object liveBoss() { return null; }
  void renderRitualZoneVisuals(long now) { }
  void tickWaveCompletion() { objectives++; if (activeWave == 4) tickWave4ObeliskAssault(); }
  void tickWave4ObeliskAssault() {
    wave4Objectives++; fireSchedules++;
    if (restartInObjective) { generation++; phase = EventPhase.WAVE_4; }
    if (cancelInObjective) cancel();
  }
  void cancel() {
    generation++; phase = EventPhase.READY_FOR_PLAYERS;
    testWaveFrontVisualMode = false; testWave4ObeliskMode = false; activeWave = 0;
  }
  void tickWaveMobAi() { if (cancelInAi) cancel(); }
  void tickStartRitual() { }
  void tickCurrentIntermission() { }
  void tickCoreRestoration() { }
  void tickPreBossCooldown() { }
  void tickBossCinematic() { }
  void tickBoss() { }
  void tickEventCombatProfiles() { }
  void tickWave7Returns() { }
  void tickEchoPresentationProbe() { }
  public static void main(String[] args) {
    var probe = new Wave4ObjectiveDispatchProbe();
    String scenario = args[0]; int expected = 1;
    if (scenario.startsWith("official")) {
      probe.phase = EventPhase.WAVE_4; probe.testWaveFrontVisualMode = false;
      probe.testWave4ObeliskMode = false;
    }
    if (scenario.equals("disposable-ai")) probe.testCombatAiMode = true;
    if (scenario.equals("inspection")) { probe.testWaveInspectionMode = true; expected = 0; }
    if (scenario.equals("cancel-before-objective")) { probe.cancelInAi = true; expected = 0; }
    if (scenario.equals("official-cancel-objective")) probe.cancelInObjective = true;
    if (scenario.equals("official-restart-objective")) probe.restartInObjective = true;
    if (scenario.equals("restarted")) { probe.generation++; probe.eventTickCounter = 0; }
    for (int cycle = 1; cycle <= 3; cycle++) {
      probe.tick();
      int total = probe.cancelInObjective ? expected : expected * cycle;
      if (probe.wave4Objectives != total || probe.objectives != total || probe.fireSchedules != total)
        throw new AssertionError(scenario + " cycle=" + cycle + " objective ticks=" + probe.wave4Objectives
                + " dispatches=" + probe.objectives + " mutations=" + probe.fireSchedules + "; expected=" + total);
      if (probe.endEventBridgeV4.ticks != cycle)
        throw new AssertionError(scenario + " End Event Bridge V4 ticks=" + probe.endEventBridgeV4.ticks
                + "; expected=" + cycle);
    }
  }
"""
        + "\n".join(f"  void {name}() {{ }}" for name in ordinary)
        + "\n"
        + method(source, "void tick()")
        + "\n}\n",
        encoding="utf-8",
    )
    phase = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java"
    compiled = subprocess.run(["javac", "-J-Xmx128m", "-d", str(tmp_path), str(phase), str(java)],
                              capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return tmp_path


@pytest.mark.parametrize("scenario", ["official", "disposable", "disposable-ai", "inspection", "restarted",
                                      "cancel-before-objective", "official-cancel-objective",
                                      "official-restart-objective"])
def test_wave4_objective_dispatch_is_once_per_cycle(dispatch_probe, scenario):
    run = subprocess.run(["java", "-Xms8m", "-Xmx64m", "-cp", str(dispatch_probe),
                          "Wave4ObjectiveDispatchProbe", scenario], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
