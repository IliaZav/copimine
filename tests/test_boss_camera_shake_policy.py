"""Camera feedback is a bounded cosmetic envelope, never a movement modifier."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_camera_envelopes_are_short_local_finite_and_optional(tmp_path):
    java = tmp_path / "BossCameraProbe.java"
    java.write_text("""import me.copimine.client.BossCameraShakePolicy;
public class BossCameraProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  double energy=0;
  for(long age=0;age<10_000;age+=7){
   var sample=BossCameraShakePolicy.sample("GROUND_SLAM",age,2,1);
   check(Double.isFinite(sample.pitchDegrees())&&Double.isFinite(sample.yawDegrees())
      &&Double.isFinite(sample.rollDegrees()),"nonfinite camera offset");
   check(Math.abs(sample.pitchDegrees())<=1.2&&Math.abs(sample.yawDegrees())<=0.6
      &&Math.abs(sample.rollDegrees())<=0.4,"camera amplitude exceeds safe budget");
   if(age<5500||age>=6150)check(sample.isZero(),"shake outside authored contact envelope");
   energy+=Math.abs(sample.pitchDegrees());
  }
  check(energy>0,"slam impact has no camera feedback");
  for(String id:new String[]{"GROUND_SLAM","CHEST_STRIKE","MELEE_SWIPE","PHASE_TRANSITION","DYING"}){
   for(long age=0;age<10_000;age+=37){
    check(BossCameraShakePolicy.sample(id,age,100,1).isZero(),"distant players shake");
    check(BossCameraShakePolicy.sample(id,age,0,0).isZero(),"opt-out ignored");
    check(BossCameraShakePolicy.sample(id,age,Double.NaN,1).isZero(),"invalid distance accepted");
    check(BossCameraShakePolicy.sample(id,age,0,Double.NaN).isZero(),"invalid strength accepted");
   }
  }
  check(BossCameraShakePolicy.sample("UNKNOWN",100,0,1).isZero(),"unknown cue shakes");
  check(BossCameraShakePolicy.sample("GROUND_SLAM",-1,0,1).isZero(),"negative clock shakes");
 }
} """, encoding="utf-8")
    source = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/BossCameraShakePolicy.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(source), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossCameraProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
