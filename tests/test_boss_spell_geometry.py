"""Execute authored spell geometry and its bounded LOD independently of Bukkit."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[1]

def test_each_spell_has_finite_distinct_bounded_geometry(tmp_path):
    probe=tmp_path/'SpellGeometryProbe.java'
    probe.write_text(r'''import java.util.*;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
public class SpellGeometryProbe {
 public static void main(String[] args){
  Set<String> silhouettes=new HashSet<>();
  for(var shape:BossSpellGeometryPolicy.Shape.values()){
   for(var beat:BossSpellGeometryPolicy.Beat.values())for(int frame=0;frame<=20;frame++){
    var points=BossSpellGeometryPolicy.sample(shape,beat,frame/20.0,4);
    if(points.isEmpty()||points.size()>96)throw new AssertionError("unbounded/missing "+shape);
    for(var p:points)if(!Double.isFinite(p.side()+p.up()+p.forward())||p.layer()<0||p.layer()>2)
      throw new AssertionError("invalid point "+shape);
   }
   if(!silhouettes.add(BossSpellGeometryPolicy.sample(shape,BossSpellGeometryPolicy.Beat.CHARGE,.63,4).toString()))
    throw new AssertionError("same silhouette for different spell: "+shape);
  }
  if(!BossSpellGeometryPolicy.sample(BossSpellGeometryPolicy.Shape.VOID_SPHERE,BossSpellGeometryPolicy.Beat.CHARGE,Double.NaN,4).isEmpty())throw new AssertionError("NaN");
  if(BossSpellGeometryPolicy.stride(49*49,2)!=0||BossSpellGeometryPolicy.stride(0,20)!=2||BossSpellGeometryPolicy.stride(0,2)!=1)throw new AssertionError("distance/group budget");
  if(BossSpellGeometryPolicy.anchor(BossSpellGeometryPolicy.Shape.RIFT_LANCE,BossSpellGeometryPolicy.Beat.CHARGE)!=BossSpellGeometryPolicy.Anchor.CHEST)throw new AssertionError("false target impact at projectile charge");
  if(BossSpellGeometryPolicy.anchor(BossSpellGeometryPolicy.Shape.VOID_RUNE,BossSpellGeometryPolicy.Beat.CHARGE)!=BossSpellGeometryPolicy.Anchor.LOCKED_FLOOR)throw new AssertionError("rune moved from floor");
 }
}''',encoding='utf-8')
    result=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(probe)],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    result=subprocess.run(['java','-cp',str(tmp_path),'SpellGeometryProbe'],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
