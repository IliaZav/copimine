"""Guide visibility is a finite, shared server/client window, not a permanent beacon exploit."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[1]
CLIENT=ROOT/'tools/minecraft-26.3/client/src/main/java/me/copimine/client'


def test_fog_guides_hold_then_fade_identically_in_server_and_client(tmp_path):
    probe=tmp_path/'GuideWindow.java'
    probe.write_text(r"""import me.copimine.endevent.domain.BlackFogTimingPolicy;
import me.copimine.client.BlackFogGuidePolicy;
public class GuideWindow {
 public static void main(String[] args){
  float previous=1;
  for(long age=0;age<=10000;age+=25){
   float client=BlackFogGuidePolicy.strength(age);
   float server=BlackFogTimingPolicy.guideStrength(age);
   if(client!=server||client<0||client>previous)throw new AssertionError("divergent guide fade at "+age);
   if(age<=3500&&client!=1)throw new AssertionError("guide vanished before fair entry window");
   if(age>=5000&&client!=0)throw new AssertionError("permanent guidance visibility exploit");
   previous=client;
  }
  if(BlackFogGuidePolicy.strength(4250)<.49||BlackFogGuidePolicy.strength(4250)>.51)throw new AssertionError("smooth fade midpoint");
 }
}""",encoding='utf-8')
    compiled=subprocess.run(['javac','-encoding','UTF-8','-d',str(tmp_path),
      str(ROOT/'copimine-end-event/src/me/copimine/endevent/domain/BlackFogTimingPolicy.java'),
      str(CLIENT/'BlackFogGuidePolicy.java'),str(probe)],capture_output=True,text=True)
    assert compiled.returncode==0,compiled.stdout+compiled.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'GuideWindow'],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
