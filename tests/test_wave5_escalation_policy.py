"""Roster-scaled fog with the Nov 10 playable 7-to-3 second reach window."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain'


def test_roster_profiles_and_monotonic_escalation(tmp_path):
    probe = tmp_path / 'FogEscalationProbe.java'
    probe.write_text('''
import me.copimine.endevent.domain.*;
public class FogEscalationProbe {
  static void check(boolean b) { if (!b) throw new AssertionError(); }
  public static void main(String[] args) {
    int[] parties = {2,5,10,11,20}, targets = {5,6,7,8,10};
    for (int i=0;i<parties.length;i++) {
      int p=parties[i], n=BlackFogTimingPolicy.cycleCount(p);
      check(n==targets[i]);
      int zones=100, side=4, warning=100, fog=0;
      for(int c=0;c<n;c++) {
        int z=BlackFogTimingPolicy.zoneCount(p,c);
        int s=BlackFogSafeZonePolicy.sideForCycleAndPlayers(c,p);
        int w=BlackFogTimingPolicy.safeZoneSeconds(c,n);
        int f=BlackFogTimingPolicy.fogSeconds(c,n);
        check(z>=1 && z<=zones && s>=1 && s<=3 && s<=side);
        check(w>=3 && w<=warning && w<=7 && f>=18 && f<=26 && f>=fog);
        zones=z; side=s; warning=w; fog=f;
        check(BlackFogTimingPolicy.hasRemainingCycles(c,n));
      }
      check(side==1 && warning==3 && fog==26);
      check(!BlackFogTimingPolicy.hasRemainingCycles(n,n));
      check(zones < BlackFogTimingPolicy.zoneCount(p,0) || zones==1);
      if(p>=5) check(zones < p); // Late shelter deliberately cannot fit everyone.
    }
    check(BlackFogTimingPolicy.cycleCount(-1)==5);
    check(BlackFogTimingPolicy.cycleCount(999)==10);
  }
}
''', encoding='utf-8')
    compiled = subprocess.run(['javac','-d',str(tmp_path),str(DOMAIN/'BlackFogTimingPolicy.java'),
                              str(DOMAIN/'BlackFogSafeZonePolicy.java'),str(probe)],capture_output=True,text=True)
    assert compiled.returncode == 0, compiled.stderr
    ran = subprocess.run(['java','-cp',str(tmp_path),'FogEscalationProbe'],capture_output=True,text=True)
    assert ran.returncode == 0, ran.stderr
