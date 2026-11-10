"""The visible impact footprint follows the actual bounded attack geometry."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_impact_geometry_is_distinct_finite_and_inside_its_attack_footprint(tmp_path):
    java = tmp_path / "BossImpactGeometryProbe.java"
    java.write_text("""import me.copimine.endevent.domain.BossImpactGeometryPolicy;
public class BossImpactGeometryProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  var kind=BossImpactGeometryPolicy.Kind.class;
  for(var k:kind.getEnumConstants())for(double progress:new double[]{0,0.25,0.5,0.75,1}){
    var points=BossImpactGeometryPolicy.sample(k,7,progress);
    check(!points.isEmpty()&&points.size()<=96,"unbounded or missing impact "+k);
    for(var p:points){
      check(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()),"nonfinite point");
      check(p.layer()>=0&&p.layer()<=2,"unknown visual layer");
      if(k==BossImpactGeometryPolicy.Kind.SLAM||k==BossImpactGeometryPolicy.Kind.SWIPE){
        check(Math.hypot(p.x(),p.z())<=7.00001,"visual exceeds attack radius");
        if(k==BossImpactGeometryPolicy.Kind.SWIPE)check(p.z()>=0,"swipe drawn behind committed facing");
      }
    }
  }
  check(BossImpactGeometryPolicy.sample(BossImpactGeometryPolicy.Kind.SLAM,Double.NaN,0).isEmpty(),"invalid radius");
  check(BossImpactGeometryPolicy.sample(BossImpactGeometryPolicy.Kind.SLAM,7,Double.NaN).isEmpty(),"invalid time");
  check(!BossImpactGeometryPolicy.sample(BossImpactGeometryPolicy.Kind.SWIPE,4.5,0.5).equals(
      BossImpactGeometryPolicy.sample(BossImpactGeometryPolicy.Kind.SLAM,4.5,0.5)),"all attacks share one generic ring");
 }
} """, encoding="utf-8")
    source = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossImpactGeometryPolicy.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(source), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossImpactGeometryProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
