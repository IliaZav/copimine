"""Execute the production rules; source presence is not gameplay evidence."""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain'


def test_five_pillars_and_traveling_stomp(tmp_path):
    probe = tmp_path / 'FivePillarProbe.java'
    probe.write_text('''
import me.copimine.endevent.domain.RiftRamPolicy;
public class FivePillarProbe {
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
  public static void main(String[] args) {
    check(RiftRamPolicy.REQUIRED_IMPACTS == 5);
    double[] damage = {.20, .35, .50, .65, .80, 1.0};
    for (int i=0;i<6;i++) {
      check(Math.abs(RiftRamPolicy.damageMultiplier(i)-damage[i]) < 1e-9);
      check(!RiftRamPolicy.isComplete(i, true));
      check(RiftRamPolicy.isComplete(i, false) == (i==5));
    }
    check(RiftRamPolicy.segmentHitsPillar(-5,64,0, 5,64,0, 0,64,0));
    check(!RiftRamPolicy.segmentHitsPillar(-5,80,0, 5,80,0, 0,64,0));
    // Crossing the vertical band elsewhere along the segment must not hit.
    check(!RiftRamPolicy.segmentHitsPillar(-10,64,0, 10,100,0, 0,64,0));
    check(!RiftRamPolicy.segmentHitsPillar(0,64,0, 0,64,0, 0,64,0));
    check(!RiftRamPolicy.segmentHitsPillar(Double.NaN,64,0, 5,64,0, 0,64,0));
    check(RiftRamPolicy.stompHits(4, 0, true, 3,5, false));
    check(!RiftRamPolicy.stompHits(4, 1.2, false,3,5,false));
    check(!RiftRamPolicy.stompHits(4, 0,true,3,5,true));
    check(!RiftRamPolicy.stompHits(10,0,true,3,5,false));
    check(!RiftRamPolicy.stompHits(13,0,true,11,12,false));
    check(!RiftRamPolicy.stompHits(Double.NaN,0,true,3,5,false));
    check(RiftRamPolicy.STOMP_SLOW_TICKS == 60);
    check(RiftRamPolicy.STOMP_SLOW_AMPLIFIER == 1);
    for(int players:new int[]{2,5,10,11,20}) {
      check(RiftRamPolicy.healthForPlayers(players)>=180);
      check(RiftRamPolicy.healthForPlayers(players)<=400);
    }
    System.out.println("OK");
  }
}''', encoding='utf-8')
    built = subprocess.run(['javac','-encoding','UTF-8','-d',str(tmp_path),
                            str(DOMAIN/'RiftRamPolicy.java'),str(probe)], capture_output=True,text=True)
    assert built.returncode == 0, built.stderr
    result = subprocess.run(['java','-cp',str(tmp_path),'FivePillarProbe'],capture_output=True,text=True)
    assert result.returncode == 0, result.stderr
