"""Actual defence-anchor adapter must wait for the previous portal's collapse."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def sequence_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("portal-sequence")
    source = SOURCE.read_text("utf-8")
    methods = extract(source, "Location nearestOpenPortal(Location from)")
    if "private int currentWave3ActivePortal(" in source:
        methods += extract(source, "int currentWave3ActivePortal(")
    methods = methods.replace("System.currentTimeMillis()", "clock")
    java = out / "PortalSequenceProbe.java"
    java.write_text('''
import java.util.*;import me.copimine.endevent.domain.*;
public class PortalSequenceProbe {
    long clock=1000;int activeWave=3;
    Map<Integer,List<Location>> wavePortals=new HashMap<>();
    Map<Integer,List<PortalCapturePolicy.PortalState>> portalCaptureStates=new HashMap<>();
    Map<Integer,Long> portalClosingStartedMillis=new HashMap<>();
    record Location(int id){Object getWorld(){return "arena";}}
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
''' + methods + '''
    public static void main(String[] args){
        var p=new PortalSequenceProbe();var origin=new Location(99);
        var initial=PortalCapturePolicy.initial();var done=new PortalCapturePolicy.PortalState(true,5000,1000,1000);
        p.wavePortals.put(3,List.of(new Location(0),new Location(1),new Location(2)));
        p.portalCaptureStates.put(3,List.of(done,initial,initial));
        switch(args[0]){
            case "collapse" -> {
                p.portalClosingStartedMillis.put(0,1000L);p.clock=1599;
                check(p.nearestOpenPortal(origin)==null,"defenders and capture must not activate next portal during collapse");
                p.clock=1600;check(p.nearestOpenPortal(origin).id()==1,"next gate must activate after bounded collapse");
            }
            case "missing-closure" -> check(p.nearestOpenPortal(origin)==null,"missing closure receipt must fail closed");
            case "first-only" -> {
                p.portalCaptureStates.put(3,List.of(initial,initial,initial));
                check(p.nearestOpenPortal(new Location(2)).id()==0,"inactive closer portals cannot attract defenders");
            }
        }
    }
}
''', encoding="utf-8")
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(java),
                            str(domain / "PortalCapturePolicy.java"), str(domain / "PortalPresentationPolicy.java")], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return out


@pytest.mark.parametrize("scenario", ["collapse", "missing-closure", "first-only"])
def test_defence_anchor_uses_real_active_gate(sequence_probe, scenario):
    result = subprocess.run(["java", "-cp", str(sequence_probe), "PortalSequenceProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
