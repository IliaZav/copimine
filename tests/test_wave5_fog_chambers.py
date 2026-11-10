"""Execute the shader's slab arithmetic and the production smoke clearance policy."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'tools/minecraft-26.3/client/src/main/java/me/copimine/client'


def test_actual_shader_ray_intersection_and_chamber_budget(tmp_path):
    shader = (CLIENT.parents[3] / 'resources/assets/copimineclient/shaders/post/wave5_volume.fsh').read_text()
    functions = shader[shader.index('vec2 axisSpan('):shader.index('void main()')]
    functions = functions.replace('vec2(', 'new Vec2(').replace('vec3 ', 'Vec3 ').replace('vec2 ', 'Vec2 ')
    functions = re.sub(r'\bfloat\b', 'double', functions)
    functions = re.sub(r'\b(abs|min|max)\(', r'Math.\1(', functions)
    functions = functions.replace('Vec2 axisSpan', 'static Vec2 axisSpan').replace('Vec2 arenaSpan', 'static Vec2 arenaSpan')
    for name in ('BlackFogSmokePolicy', 'BlackFogVolumePolicy'):
        (tmp_path / f'{name}.java').write_bytes((CLIENT / f'{name}.java').read_bytes())
    (tmp_path / 'Probe.java').write_text('''package me.copimine.client;
public class Probe {
    record Vec2(double x,double y) {}
    record Vec3(double x,double y,double z) {}
    static Vec3 ArenaMin = new Vec3(0,0,0), ArenaMax = new Vec3(10,4,10);
    ''' + functions + '''
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static void span(Vec3 origin, Vec3 direction, double begin, double end) {
        Vec2 hit = arenaSpan(origin,direction);
        check(Math.abs(hit.x-begin)<.00001 && Math.abs(hit.y-end)<.00001);
    }
    public static void main(String[] args) {
        span(new Vec3(5,2,5), new Vec3(1,0,0),0,5);
        span(new Vec3(-4,2,5), new Vec3(1,0,0),4,14);
        span(new Vec3(5,2,5), new Vec3(0,1,0),0,2);
        span(new Vec3(5,4,5), new Vec3(0,-1,0),0,4);
        Vec2 miss=arenaSpan(new Vec3(-1,2,5),new Vec3(0,1,0));
        check(miss.y<miss.x);
        miss=arenaSpan(new Vec3(-4,2,5),new Vec3(-1,0,0));
        check(miss.y<miss.x);
        Vec2 hit=arenaSpan(new Vec3(-4,2,5),new Vec3(1,0,0));
        check(Math.min(hit.y,3)<hit.x); // opaque terrain before arena entrance
        check(BlackFogVolumePolicy.FOG_HEIGHT==4);
        check(BlackFogVolumePolicy.CHAMBER_HEIGHT==3);
        check(BlackFogSmokePolicy.budget(255,true)==1);
        check(BlackFogSmokePolicy.budget(256,false)==0);
        check(!BlackFogSmokePolicy.clearOfSafeFloor(2.1,.5,(x,z)->x==0 && z==0));
        check(BlackFogSmokePolicy.clearOfSafeFloor(2.3,.5,(x,z)->x==0 && z==0));
    }
}''')
    compile_result = subprocess.run(['javac', '-d', str(tmp_path), *map(str, tmp_path.glob('*.java'))], capture_output=True, text=True)
    assert compile_result.returncode == 0, compile_result.stderr
    result = subprocess.run(['java', '-cp', str(tmp_path), 'me.copimine.client.Probe'], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr


def test_world_smoke_owns_its_cleanup_and_does_not_reload_model_assets():
    source = (CLIENT / 'BlackFogSmokeRuntime.java').read_text()
    for forbidden in ('clearParticles(', 'reloadResource', 'setShaderPack', 'getModelManager', 'setInvisible('):
        assert forbidden not in source
    assert 'for (Particle p : owned) p.remove();' in source
    assert 'CAMPFIRE_COSY_SMOKE' in source
    assert 'fog.eventId()' in source and 'fog.generation()' in source
    assert 'base + BlackFogVolumePolicy.FOG_HEIGHT' in source
    assert 'BlackFogVolumePolicy.CHAMBER_HEIGHT' in source
