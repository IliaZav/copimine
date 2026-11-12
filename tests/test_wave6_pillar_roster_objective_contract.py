"""Wave 6 pillar roster must reach the Rift Ram objective that gates the wave.

REPRODUCES the reported defect "Wave 6 final pillar 4-vs-3 display invariant".

The Rift Ram roster is FOUR ordinary pillars plus ONE final pillar that absorbs
three collisions -- seven accepted impacts in total. Two independent places knew
that shape:

  * `RiftRamPolicy.REQUIRED_IMPACTS` = MINOR_PILLARS + FINAL_PILLAR_IMPACTS = 7,
    which the live runtime (`RiftRamEncounter`) really counts, and
  * `EndRiftObjective.REQUIRED_PILLAR_IMPACTS` = 3, which the wave adapter
    `RiftRamWaveEncounter` uses as its ceiling and the coordinator uses to decide
    whether wave 6 may close.

Only one of them is the source of truth. Because the adapter silently clamps
the authoritative impact count into its own smaller requirement, the wave's
objective reports COMPLETE while the ram's armour still holds and pillars are
still standing -- and the runtime's own progress (which it reports through
`reportRuntimeResult` every tick) is dropped on the floor, because the shared
adapter only ever writes `progress = required` from a boolean.

These tests execute the PRODUCTION adapter and the PRODUCTION runtime policy.
Nothing here reads a source string to decide pass/fail.
"""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src'
DOMAIN = SRC / 'me' / 'copimine' / 'endevent' / 'domain'
RUNTIME = SRC / 'me' / 'copimine' / 'endevent' / 'runtime'
ENCOUNTER = RUNTIME / 'encounter'

PROBE = r'''
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.RiftRamPolicy;
import me.copimine.endevent.runtime.EncounterContext;
import me.copimine.endevent.runtime.encounter.RiftRamWaveEncounter;
import me.copimine.endevent.runtime.encounter.WaveEncounter;

public class RamObjectiveBridgeProbe {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    static EncounterContext ramContext(String eventId, long generation) {
        Set<UUID> players = Set.of(UUID.randomUUID());
        return new EncounterContext(eventId, generation, "CopiMine", 0, 64, 0,
                new EncounterContext.ArenaBounds(-40, 0, -40, 40, 120, 40),
                players, players, EndRiftObjective.Objective.RIFT_RAM);
    }

    /**
     * The runtime's live impact count must reach the objective every tick.
     *
     * This is exactly what the plugin does: RiftRamEncounter.impacts() is the
     * authoritative count and it is reported on each tick.
     */
    static void liveImpactsReachTheObjective() {
        RiftRamWaveEncounter ram = new RiftRamWaveEncounter();
        EncounterContext context = ramContext("event-1", 7L);
        check(ram.start(context).accepted(), "the ram objective must start");
        check(ram.required() == RiftRamPolicy.REQUIRED_IMPACTS,
                "the ram objective requires " + ram.required()
                        + " impacts, but the live runtime counts "
                        + RiftRamPolicy.REQUIRED_IMPACTS + " accepted collisions");
        for (int impacts = 0; impacts <= RiftRamPolicy.REQUIRED_IMPACTS; impacts++) {
            boolean finished = impacts == RiftRamPolicy.REQUIRED_IMPACTS;
            WaveEncounter.Result live = ram.reportRuntimeResult(context, finished, impacts);
            check(live.accepted(), "live impact " + impacts + " was refused: " + live.reason());
            check(live.progress() == impacts,
                    "the objective shows " + live.progress() + " of "
                            + RiftRamPolicy.REQUIRED_IMPACTS + " pillars after "
                            + impacts + " real collisions");
            check(live.complete() == finished,
                    "after " + impacts + " of " + RiftRamPolicy.REQUIRED_IMPACTS
                            + " collisions the objective reported "
                            + (live.complete() ? "COMPLETE" : "IN PROGRESS")
                            + " (" + (RiftRamPolicy.PILLAR_COUNT - impacts)
                            + " pillars still standing)");
        }
    }

    /** A stale or hostile report must never inflate the objective. */
    static void objectiveCannotOutrunTheRoster() {
        RiftRamWaveEncounter ram = new RiftRamWaveEncounter();
        EncounterContext context = ramContext("event-3", 9L);
        ram.start(context);
        WaveEncounter.Result ahead = ram.reportRuntimeResult(context, false, 99);
        check(ahead.progress() == RiftRamPolicy.REQUIRED_IMPACTS,
                "an over-reported count must clamp to the roster, got " + ahead.progress());
        WaveEncounter.Result back = ram.reportRuntimeResult(context, false, -4);
        check(back.progress() == RiftRamPolicy.REQUIRED_IMPACTS,
                "a negative count must not rewind progress, got " + back.progress());
    }

    /**
     * The ordinary one-pillar-at-a-time API must accept the whole roster too.
     *
     * recordPillarImpact is what a caller that pushes one destroyed pillar at a
     * time uses; it must not refuse impact six or seven as if they were
     * impossible on a five-pillar wave.
     */
    static void pillarReportsUpToTheRealRoster() {
        RiftRamWaveEncounter ram = new RiftRamWaveEncounter();
        EncounterContext context = ramContext("event-2", 8L);
        check(ram.start(context).accepted(), "the ram objective must start");
        for (int impacts = 1; impacts <= RiftRamPolicy.REQUIRED_IMPACTS; impacts++) {
            WaveEncounter.Result recorded = ram.recordPillarImpact(context, impacts);
            check(recorded.accepted(), "pillar " + impacts + " of "
                    + RiftRamPolicy.PILLAR_COUNT + " was rejected as impossible: "
                    + recorded.reason());
            check(recorded.progress() == impacts,
                    "pillar " + impacts + " recorded progress " + recorded.progress());
        }
        check(ram.recordPillarImpact(context, RiftRamPolicy.REQUIRED_IMPACTS + 1).status()
                        == WaveEncounter.Status.REJECTED,
                "an impossible pillar count must still be rejected");
    }

    /** The roster the renderer draws is the roster the policy owns. */
    static void rendererAndObjectiveAgree() {
        int expected = RiftRamPolicy.MINOR_PILLARS + RiftRamPolicy.FINAL_PILLAR_IMPACTS;
        check(RiftRamPolicy.REQUIRED_IMPACTS == expected, "the roster adds up");
        check(RiftRamPolicy.ARMOUR_PLATES == expected,
                "one armour plate per accepted collision");
        // The old three-impact constant must not survive as a second, smaller
        // roster next to the one the encounter actually runs.
        check(EndRiftObjective.REQUIRED_PILLAR_IMPACTS == RiftRamPolicy.REQUIRED_IMPACTS,
                "EndRiftObjective.REQUIRED_PILLAR_IMPACTS is "
                        + EndRiftObjective.REQUIRED_PILLAR_IMPACTS
                        + " but the ram's roster is " + RiftRamPolicy.REQUIRED_IMPACTS);
    }

    public static void main(String[] args) {
        liveImpactsReachTheObjective();
        objectiveCannotOutrunTheRoster();
        pillarReportsUpToTheRealRoster();
        rendererAndObjectiveAgree();
        System.out.println("OK");
    }
}
'''


def _compile(tmp_path):
    probe = tmp_path / 'RamObjectiveBridgeProbe.java'
    probe.write_text(PROBE, encoding='utf-8')
    sources = [
        DOMAIN / 'RiftRamPolicy.java',
        DOMAIN / 'EndRiftObjective.java',
        DOMAIN / 'BlackFogTimingPolicy.java',
        RUNTIME / 'EncounterContext.java',
        ENCOUNTER / 'WaveEncounter.java',
        ENCOUNTER / 'AbstractWaveEncounter.java',
        ENCOUNTER / 'RiftRamWaveEncounter.java',
        probe,
    ]
    built = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp_path)]
                           + [str(path) for path in sources],
                           capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return tmp_path


def test_live_pillar_impacts_reach_the_wave_objective(tmp_path):
    folder = _compile(tmp_path)
    ran = subprocess.run(['java', '-cp', str(folder), 'RamObjectiveBridgeProbe'],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert 'OK' in ran.stdout