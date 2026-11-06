"""Execute the real W6 stage adapter with an unavailable and exhausted core."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_missing_core_preserves_a_bounded_retry_ticket(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    advance = extract(source, "private void advanceWaveSixStage(long now)")
    fixture = tmp_path / "Wave6StageTicketProbe.java"
    fixture.write_text(
        r'''
import java.util.*;
public class Wave6StageTicketProbe {
    static final int WAVE6_MAX_STAGE_SPAWN_ATTEMPTS = 3;
    static final long WAVE6_STAGE_SPAWN_RETRY_MILLIS = 100L;
    static final class WaveSixSequencePolicy { static final int STAGE_COUNT = 5; }
    static final class RitualSphereScalingPolicy { static final class Profile {} }
    static final class World {}
    static final class Location { World world; Location(World world) { this.world = world; } World getWorld() { return world; } }
    static final class RitualSphereState { RitualSphereScalingPolicy.Profile profile() { return new RitualSphereScalingPolicy.Profile(); } }
    static final class Sequence {
        boolean mageStageActive() { return true; }
        int stageIndex() { return 0; }
        Object currentMage() { return "mage"; }
    }
    static final class ProbeLogger {
        final List<String> rows = new ArrayList<>();
        void warning(String value) { rows.add(value); }
        void severe(String value) { rows.add(value); }
    }
    final ProbeLogger logger = new ProbeLogger();
    String eventId = "event";
    long generation = 1L;
    Sequence wave6Sequence = new Sequence();
    RitualSphereState ritualSphereState = new RitualSphereState();
    Location core;
    long wave6NextStageAtMillis;
    int wave6StageSpawnAttempts, waveObjectiveMobCount, stageSpawnCalls;
    final Set<String> ritualCasterUuids = Set.of("caster");
    final Set<String> ritualGuardUuids = Set.of("guard");
    boolean wave6PrisonBroken, recoveryRequired, spawnAccepted = true;
    String recoveryReason;
    ProbeLogger getLogger() { return logger; }
    Location coreCombatAnchorLocation() { return core; }
    boolean spawnWaveSixStage(World world, Location anchor, RitualSphereScalingPolicy.Profile profile) {
        stageSpawnCalls++; return spawnAccepted;
    }
    void markWave6RecoveryRequired(String reason) { recoveryRequired = true; recoveryReason = reason; }
    void reconcileRitualCasterShields() {}
    void announceEventTitle(String title, String subtitle, boolean fade) {}
    String mageName(Object mage) { return String.valueOf(mage); }
    void saveStateAsync() {}
    void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
''' + advance + r'''
    public static void main(String[] args) {
        var retry = new Wave6StageTicketProbe();
        retry.wave6NextStageAtMillis = 500L;
        retry.advanceWaveSixStage(500L);
        retry.require(retry.wave6NextStageAtMillis == 600L,
                "a missing core must leave one finite retry ticket");
        retry.require(retry.wave6StageSpawnAttempts == 1 && retry.stageSpawnCalls == 0,
                "a missing core consumes one bounded prerequisite attempt without spawning");
        retry.require(!retry.recoveryRequired,
                "a recoverable missing core must not immediately fail the ritual");

        var exhausted = new Wave6StageTicketProbe();
        exhausted.wave6NextStageAtMillis = 700L;
        exhausted.wave6StageSpawnAttempts = WAVE6_MAX_STAGE_SPAWN_ATTEMPTS - 1;
        exhausted.advanceWaveSixStage(700L);
        exhausted.require(exhausted.wave6NextStageAtMillis == Long.MAX_VALUE,
                "exhaustion must consume the ticket so recovery is not retried every tick");
        exhausted.require(exhausted.recoveryRequired
                        && "WAVE6_STAGE_SPAWN_EXHAUSTED".equals(exhausted.recoveryReason),
                "bounded prerequisite failure must enter explicit recovery");
        exhausted.require(exhausted.stageSpawnCalls == 0,
                "an unavailable core must never start a partial stage");

        var partial = new Wave6StageTicketProbe();
        partial.core = new Location(new World());
        partial.spawnAccepted = false;
        partial.wave6NextStageAtMillis = 800L;
        partial.advanceWaveSixStage(800L);
        partial.require(partial.wave6NextStageAtMillis == 900L,
                "a failed stage spawn receives a bounded retry ticket");
        partial.advanceWaveSixStage(900L);
        partial.advanceWaveSixStage(1000L);
        partial.require(partial.wave6NextStageAtMillis == Long.MAX_VALUE
                        && partial.wave6StageSpawnAttempts == WAVE6_MAX_STAGE_SPAWN_ATTEMPTS,
                "a failed stage spawn stops retrying at the configured bound");
        partial.require(partial.recoveryRequired && partial.stageSpawnCalls == 3,
                "exhausted partial stage creation enters recovery exactly once");
    }
}
''',
        encoding="utf-8",
    )
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", str(fixture)],
                              capture_output=True, text=True, timeout=30)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "Wave6StageTicketProbe"],
                              capture_output=True, text=True, timeout=30)
    assert executed.returncode == 0, executed.stdout + executed.stderr
