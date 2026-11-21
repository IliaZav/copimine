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
  // The authored envelope keeps a clean near band: nothing within 1.15 blocks
  // is fogged at all, so held items and silhouettes are untouched rather than
  // merely "mostly" readable.
  check(gain(0)==0&&gain(0.5)==0&&gain(1.15)==0,"the near band must carry no extinction");
  check(gain(3.85)>0.999&&gain(6)>0.999,"density must reach full weight before mid-distance");
  check(transmission(1,density*1.38)>.95,"near silhouettes/held items remain readable");
  check(transmission(2,density*.72)>.5,"ground approach remains readable");
  // The volume then closes within a few blocks and keeps closing, so the arena
  // behind the fog is never readable at range.
  check(transmission(4,density*.72)<.15,"the fog is already dominant past the near band");
  check(transmission(6,density*.72)<.01,"even thin distant eddies obscure the arena");
  check(transmission(8,density*.72)<.0005,"the far arena is extinguished");
  check(transmission(10,density*.72)<.0001,"nothing at range survives the fog");
  double previous=1;
  for(double d=0;d<10;d+=.05){
   check(gain(d)>=0&&gain(d)<=1,"bounded density");
   check(gain(d+.01)>=gain(d),"no near-plane discontinuity");
   double now=transmission(d+0.05,density*.72);
   check(now<=previous,"transmission must fall monotonically with distance");
   previous=now;
  }
 }
}""".replace('BODY',body).replace('DENSITY',str(density)),encoding='utf-8')
    compiled=subprocess.run(['javac','-d',str(tmp_path),str(probe)],capture_output=True,text=True)
    assert compiled.returncode==0,compiled.stdout+compiled.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'Optics'],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
    assert 'safeExit(' not in shader, 'safe and unsafe cells must have identical fog density'
    assert 'source.a' in shader and 'DepthSampler' in shader


def test_world_chambers_suspend_the_post_effect_and_retain_arena_fog_outside():
    runtime=(CLIENT/'BlackFogVolumeRuntime.java').read_text(encoding='utf-8')
    assert 'box.cells() == width * depth' in runtime
    assert 'BlackFogChamberRenderer.render(context)' in (CLIENT/'CopiMineClient.java').read_text(encoding='utf-8')
    assert runtime.index('effects.remove(EFFECT);', runtime.index('chamber = new Chamber')) < runtime.index('effects.add(EFFECT)')
    assert 'endEventArenaBounds(manager.eventId(), manager.generation())' in runtime
    smoke=(CLIENT/'BlackFogSmokeRuntime.java').read_text(encoding='utf-8')
    assert 'BlackFogVolumeRuntime.chamber() != null' in smoke
    for particle in ('ASH','SMOKE','SOUL','REVERSE_PORTAL'):
        assert particle in smoke
    for forbidden in ('reloadResource','clearParticles(', 'setShaderPack'):
        assert forbidden not in runtime+smoke


def test_the_fog_is_violet_black_not_a_cold_teal_screen():
    """Contract 6.1 names the palette: charcoal, muddy brown, deep violet.

    Nothing pinned this before, which is how a build that rendered the volume in
    a cold blue-teal drifted into players' hands while the source said violet.
    A screenshot of that build measures blue-minus-red of +15 on the upper field;
    the corrected palette measures +5. Both are "dark", so only the hue channel
    distinguishes a correct fog from a stale one - hence an explicit assertion.
    """
    shader = SHADER.read_text(encoding='utf-8')
    match = re.search(r'vec3 ink = mix\((vec3\([^)]*\)), (vec3\([^)]*\)),', shader)
    assert match, 'the volumetric ink palette is missing from the fog shader'
    def triple(text):
        # Strip the vec3( marker first, or its "3" is read as a channel value.
        body = re.search(r'vec3\(([^)]*)\)', text).group(1)
        return [float(v) for v in re.findall(r'[0-9.]+', body)]

    inner = triple(match.group(1))
    outer = triple(match.group(2))
    for name, rgb in (('ink base', inner), ('ink highlight', outer)):
        assert len(rgb) == 3, name + ' must be an rgb triple'
        blue_minus_red = rgb[2] - rgb[0]
        # Violet means blue and red lead green together; a cold fog lets blue
        # alone lead, which is the teal screen this contract forbids.
        assert blue_minus_red <= 0.06, (
            name + ' leans cold/teal (B-R=' + str(round(blue_minus_red, 4)) +
            '); contract 6.1 asks for charcoal, muddy brown and deep violet'
        )
        assert rgb[0] + rgb[2] > rgb[1], (
            name + ' must carry red and blue above green to read as violet'
        )
        # Still a dark fog: nothing here may become a bright wash.
        assert max(rgb) < 0.35, name + ' must stay dark'
    # The soul highlight is the only bright accent, and it must stay violet.
    soul = re.search(r'soul \* vec3\(([^)]*)\)', shader)
    assert soul, 'the soul highlight is missing'
    s = [float(v) for v in re.findall(r'[0-9.]+', soul.group(1))]
    assert s[2] - s[0] <= 0.06, 'the soul highlight leans teal'
    assert s[2] > s[1] and s[0] > s[1], 'the soul highlight must be violet'
