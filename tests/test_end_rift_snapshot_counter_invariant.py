"""The snapshot counter invariant must not fail on an idle event.

`ADMIN_SNAPSHOT_COUNTERS_NON_NEGATIVE` used to include `generation >= 1` in its
coherence test.  An unconfigured event legitimately reports generation 0, so the
operator command `cmend debug invariants` failed on a perfectly healthy server
with every counter at zero.
"""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def invariant_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("snapshot-invariant")
    method = extract(SOURCE.read_text(encoding="utf-8"),
                     "void handleStructuredDiagnosticsInvariants(")
    java = out / "SnapshotCounterInvariantProbe.java"
    java.write_text(r'''
import java.util.*;
public class SnapshotCounterInvariantProbe {
    enum EventPhase {AWAKENING,BOSS_ACTIVE}
    interface CommandSender { String getName(); }

    static final class EndRiftDiagnosticSnapshot {
        final long generation; final int owned, tasks, proxies, wave7;
        EndRiftDiagnosticSnapshot(long g, int o, int t, int p, int w) {
            generation=g; owned=o; tasks=t; proxies=p; wave7=w;
        }
        long generation(){return generation;}
        int ownedEntityCount(){return owned;}
        int activeTaskCount(){return tasks;}
        int bossHitboxProxyCount(){return proxies;}
        int wave7TemporaryBlockCount(){return wave7;}
        Map<String,Object> toFields(){return new LinkedHashMap<>();}
    }
    static final class Monitor {
        int failures; String last;
        int failureCount(){return failures;}
        Optional<String> lastFailure(){return Optional.ofNullable(last);}
    }
    static final class EndRiftDiagnostics {
        final Monitor monitor=new Monitor();
        Monitor invariants(){return monitor;}
        void invariant(boolean ok, long tick, long gen, int wave, String phase,
                       String name, String correlation, Map<String,Object> fields){
            if(!ok){monitor.failures++; monitor.last=name;}
        }
    }

    EndRiftDiagnostics endRiftDiagnostics = new EndRiftDiagnostics();
    EndRiftDiagnosticSnapshot snapshot;
    long eventTickCounter, generation;
    int activeWave;
    EventPhase phase = EventPhase.AWAKENING;
    String eventId = "idle";
    String printed = "";

    EndRiftDiagnosticSnapshot currentDiagnosticSnapshot(){return snapshot;}
    void message(CommandSender sender, String text){printed = text;}

METH
    static void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }

    public static void main(String[] args){
        var probe = new SnapshotCounterInvariantProbe();
        // Idle event: generation 0, all counters zero.
        probe.snapshot = new EndRiftDiagnosticSnapshot(0L, 0, 0, 0, 0);
        probe.handleStructuredDiagnosticsInvariants(null);
        check(probe.endRiftDiagnostics.monitor.failures == 0,
                "idle event reported a counter invariant failure: " + probe.endRiftDiagnostics.monitor.last);
        check(probe.printed.contains("countersNonNegative=true"),
                "idle event printed countersNonNegative=false: " + probe.printed);

        // A real negative counter must still fail.
        var broken = new SnapshotCounterInvariantProbe();
        broken.snapshot = new EndRiftDiagnosticSnapshot(4L, -1, 0, 0, 0);
        broken.handleStructuredDiagnosticsInvariants(null);
        check(broken.endRiftDiagnostics.monitor.failures == 1,
                "negative counter was not reported");
        check("ADMIN_SNAPSHOT_COUNTERS_NON_NEGATIVE".equals(broken.endRiftDiagnostics.monitor.last),
                "wrong invariant name: " + broken.endRiftDiagnostics.monitor.last);
        check(broken.printed.contains("countersNonNegative=false"),
                "negative counter printed as coherent: " + broken.printed);
    }
}
'''.replace("METH", method), encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(out), str(java)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return out


def test_snapshot_counter_invariant_tolerates_an_idle_event(invariant_probe):
    result = subprocess.run(
        ["java", "-cp", str(invariant_probe), "SnapshotCounterInvariantProbe"],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr