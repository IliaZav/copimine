"""November uniform fog: execute the actual GLSL distance envelope, retain scope guards."""
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
CLIENT=ROOT/'tools/minecraft-26.3/client/src/main/java/me/copimine/client'
SHADER=CLIENT.parents[3]/'resources/assets/copimineclient/shaders/post/wave5_volume.fsh'


def test_shader_preserves_near_geometry_but_extinguishes_distant_rays(tmp_path):
    shader=SHADER.read_text(encoding='utf-8')
    match=re.search(r'float distanceDensityGain\(float distanceAlongRay\) \{(.*?)\n\}',shader,re.S)
    assert match, 'uniform near-distance extinction envelope is missing'
    policy=(CLIENT/'BlackFogVolumePolicy.java').read_text(encoding='utf-8')
    density=float(re.search(r'EXTINCTION_PER_BLOCK = ([0-9.]+)F',policy)[1])
    body=match[1].replace('smoothstep(', 'smoothstep(')
    probe=tmp_path/'Optics.java'
    probe.write_text("""public class Optics {
 static double smoothstep(double a,double b,double x){double t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
 static double gain(double distanceAlongRay){BODY}
 static double transmission(double distance,double density){
  double optical=0;int samples=120;
  for(int i=0;i<samples;i++)optical+=gain((i+.5)*distance/samples)*density*distance/samples;
  return Math.exp(-optical);
 }
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  double density=DENSITY;
  check(transmission(1,density*1.38)>.85,"near silhouettes/held items remain readable");
  check(transmission(2,density*.72)>.35,"ground approach remains readable");
  check(transmission(6,density*.72)<.003,"even thin distant eddies obscure the arena");
  for(double d=0;d<10;d+=.05){check(gain(d)>=0&&gain(d)<=1,"bounded density");check(gain(d+.01)>=gain(d),"no near-plane discontinuity");}
 }
}""".replace('BODY',body).replace('DENSITY',str(density)),encoding='utf-8')
    compiled=subprocess.run(['javac','-d',str(tmp_path),str(probe)],capture_output=True,text=True)
    assert compiled.returncode==0,compiled.stdout+compiled.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'Optics'],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
    assert 'safeExit(' not in shader, 'safe and unsafe cells must have identical fog density'
    assert 'source.a' in shader and 'DepthSampler' in shader


def test_uniform_runtime_does_not_disable_fog_or_draw_opaque_chamber_walls():
    runtime=(CLIENT/'BlackFogVolumeRuntime.java').read_text(encoding='utf-8')
    assert 'refreshSafeVolume(' not in runtime
    assert 'safe[3]' not in runtime
    assert 'BlackFogChamberRenderer.render(context)' not in (CLIENT/'CopiMineClient.java').read_text(encoding='utf-8')
    assert 'endEventArenaBounds(manager.eventId(), manager.generation())' in runtime
    smoke=(CLIENT/'BlackFogSmokeRuntime.java').read_text(encoding='utf-8')
    assert 'clearOfSafeFloor(' not in smoke, 'uniform smoke must include refuge cells'
    for particle in ('ASH','SMOKE','SOUL','REVERSE_PORTAL'):
        assert particle in smoke
    for forbidden in ('reloadResource','clearParticles(', 'setShaderPack'):
        assert forbidden not in runtime+smoke
