"""Exercise deterministic fallback coverage when random anchors hit arena obstacles."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[1]
def test_bounded_search_covers_safe_outer_band_after_primary_refusals(tmp_path):
    probe=tmp_path/'ObeliskSearchProbe.java'
    probe.write_text("""import java.util.*;import me.copimine.endevent.domain.*;
public class ObeliskSearchProbe {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  for(int count:new int[]{2,3,4,5,6})for(long seed=1;seed<=20;seed++){
   var primary=ObeliskPlacementPolicy.scatter(count,seed,20);
   var spares=ObeliskPlacementPolicy.spareCandidates(12,seed,20,primary);
   var search=ObeliskPlacementPolicy.searchCandidates(seed,20);
   check(search.size()<=512,"bounded checks");
   check(search.equals(ObeliskPlacementPolicy.searchCandidates(seed,20)),"stable restart order");
   check(new HashSet<>(search).size()==search.size(),"unique search anchors");
   // Model built arena obstacles at every random primary and spare position.
   // Valid sites remain in the same approved radial band.
   var rejected=new HashSet<>(primary);rejected.addAll(spares);
   var seated=new ArrayList<ObeliskPlacementPolicy.Point>();
   for(var at:search){
    if(rejected.contains(at)||seated.stream().anyMatch(old->Math.hypot(old.x()-at.x(),old.z()-at.z())<4))continue;
    seated.add(at);if(seated.size()==count)break;
   }
   check(seated.size()==count,"blocked random positions must not hide usable arena sites");
   for(var at:seated)check(Math.hypot(at.x(),at.z())>=13&&Math.hypot(at.x(),at.z())<=17,"outer band");
  }
 }
}""",encoding='utf-8')
    domain=ROOT/'copimine-end-event/src/me/copimine/endevent/domain'
    result=subprocess.run(['javac','-d',str(tmp_path),str(probe),*[str(domain/f'{name}.java') for name in ['ObeliskPlacementPolicy','ObeliskScalingPolicy','ObeliskFireDirectorPolicy']]],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    result=subprocess.run(['java','-cp',str(tmp_path),'ObeliskSearchProbe'],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
