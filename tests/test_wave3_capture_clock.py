"""Run production capture timing at different heartbeat rates, including re-entry."""
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def clock_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("portal-clock")
    java = out / "PortalClockProbe.java"
    java.write_text('''
import me.copimine.endevent.domain.PortalCapturePolicy;
public class PortalClockProbe {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static PortalCapturePolicy.PortalState captured(){
        var s=PortalCapturePolicy.tick(PortalCapturePolicy.initial(),true,0);
        return PortalCapturePolicy.tick(s,true,2500);
    }
    public static void main(String[] args){
        var s=captured();
        switch(args[0]){
            case "cadence" -> {
                for(int time=2750;time<=3750;time+=250)s=PortalCapturePolicy.tick(s,false,time);
                var once=PortalCapturePolicy.tick(captured(),false,3750);
                check(s.progressMillis()==2100,"250ms cadence must lose only 800ms * .5 after grace: "+s.progressMillis());
                check(s.progressMillis()==once.progressMillis(),"decay must not depend on heartbeat frequency");
            }
            case "fractional-cadence" -> {
                for(int time=2951;time<=3200;time++)s=PortalCapturePolicy.tick(s,false,time);
                check(s.progressMillis()==2375,"fractional losses must accumulate once, not round up on every heartbeat: "+s.progressMillis());
            }
            case "reentry" -> {
                s=PortalCapturePolicy.tick(s,false,3000);
                s=PortalCapturePolicy.tick(s,false,3250);
                s=PortalCapturePolicy.tick(s,true,3500);
                check(s.progressMillis()==2225,"reentry must not charge already-decayed time twice: "+s.progressMillis());
                s=PortalCapturePolicy.tick(s,true,3750);
                check(s.progressMillis()==2475,"occupied progression must resume from retained progress");
            }
            case "duplicate-backwards" -> {
                s=PortalCapturePolicy.tick(s,false,3250);
                var same=PortalCapturePolicy.tick(s,false,3250);
                var older=PortalCapturePolicy.tick(s,false,3000);
                check(same.equals(s)&&older.equals(s),"duplicate or older clock samples cannot decay again");
            }
            case "late-activation" -> {
                s=PortalCapturePolicy.tick(PortalCapturePolicy.initial(),true,100000);
                check(s.progressMillis()==0,"dormant lifetime must not count as occupancy");
                s=PortalCapturePolicy.tick(s,true,104999);
                check(!s.completed()&&s.progressMillis()==4999,"new active gate still needs five seconds");
            }
        }
    }
}
''', encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/PortalCapturePolicy.java"
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(policy), str(java)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return out


@pytest.mark.parametrize("scenario", ["cadence", "fractional-cadence", "reentry", "duplicate-backwards", "late-activation"])
def test_actual_capture_clock(clock_probe, scenario):
    result = subprocess.run(["java", "-cp", str(clock_probe), "PortalClockProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
