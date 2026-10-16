"""Regression contracts for the visible Wave 6 ritual scene."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT_MIXIN = ROOT / (
    "CopiMineClient/src/main/java/me/copimine/client/mixin/"
    "EndermanEyesFeatureRendererMixin.java"
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_client_eyes_mixin_matches_1_21_entity_descriptor() -> None:
    source = read(CLIENT_MIXIN)
    assert "import net.minecraft.entity.Entity;" in source
    assert "Entity entity," in source, "EyesFeatureRenderer erases its render argument to Entity in 1.21.1"
    assert "LivingEntity entity," not in source
    assert "entity instanceof EndermanEntity" in source
    assert 'visual.startsWith("END_RIFT_")' in source


def test_wave6_start_removes_stale_walls_and_wave3_portals() -> None:
    source = read(SERVER)
    start = source.index("private boolean startRitualSphereObjective")
    end = source.index("/** Place one ritual entity", start)
    body = source[start:end]
    assert "clearLegacyWave7BarrierArtifacts" in body
    assert "clearLegacyWave3PortalArtifacts" in body


def test_bootstrap_clears_stale_wave7_walls_and_wave3_portals_outside_wave7() -> None:
    source = read(SERVER)
    start = source.index("private void restorePersistedCombatRuntime")
    end = source.index("private void restorePersistedRitualSphereObjective", start)
    body = source[start:end]
    assert "clearRealitySplitBarriers(\"bootstrap-non-wave7\")" in body
    assert "clearLegacyWave7BarrierArtifacts(\"bootstrap-non-wave7\")" in body
    assert "clearLegacyWave3PortalArtifacts(\"bootstrap-non-wave7\")" in body


def test_prisoner_anchor_is_inside_the_sphere_not_three_blocks_to_the_side(tmp_path) -> None:
    import subprocess

    source = read(SERVER)

    def declaration(signature: str) -> str:
        start = source.index(signature)
        brace = source.index('{', start)
        depth, end = 1, brace + 1
        while depth:
            depth += (source[end] == '{') - (source[end] == '}')
            end += 1
        return source[start:end]

    fixture = tmp_path / 'PrisonerAnchorProbe.java'
    fixture.write_text('''
public class PrisonerAnchorProbe {
    long eventTickCounter;
    static class Location implements Cloneable {
        double x,y,z;
        Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
    }
    public static void main(String[] args){
        var probe=new PrisonerAnchorProbe(); var core=new Location(13.5,68,-34.5);
        if(probe.ritualPrisonerLocation(null)!=null)throw new AssertionError("null anchor remains unavailable");
        for(int tick=0;tick<=480;tick+=15){
            probe.eventTickCounter=tick;
            var center=probe.ritualSphereCenter(core); var feet=probe.ritualPrisonerLocation(core);
            if(feet.x!=center.x||feet.z!=center.z||Math.abs(feet.y-(center.y-.9))>1e-9)
                throw new AssertionError("player stays centered within the bobbing sphere, tick="+tick);
            if(Math.abs(center.y-core.y-6.2)>.120000001)
                throw new AssertionError("latest raised sphere height is retained");
            if(core.x!=13.5||core.y!=68||core.z!=-34.5)throw new AssertionError("anchor calculation cannot mutate Core");
        }
    }
''' + declaration('private Location ritualSphereCenter(') + '\n'
        + declaration('private Location ritualPrisonerLocation(') + '\n}', encoding='utf-8')
    compiled = subprocess.run(['javac', '-J-Xmx96m', '-encoding', 'UTF-8', '-d', str(tmp_path), str(fixture),
                               str(ROOT / 'copimine-end-event/src/me/copimine/endevent/domain/RitualSpherePresentationPolicy.java')],
                              capture_output=True, text=True, timeout=30)
    assert compiled.returncode == 0, compiled.stderr
    executed = subprocess.run(['java', '-Xms8m', '-Xmx96m', '-XX:+UseSerialGC', '-cp', str(tmp_path),
                               'PrisonerAnchorProbe'], capture_output=True, text=True, timeout=30)
    assert executed.returncode == 0, executed.stdout + executed.stderr


def test_wave6_sphere_keeps_outside_layers_without_obscuring_the_prisoner() -> None:
    source = read(SERVER)
    start = source.index("private void renderCurrentRitualSphere")
    end = source.index("private void renderRitualZone", start)
    body = source[start:end]
    assert "isInsideRitualSphereViewer(viewer, center)" in body
    assert "spawnPatternRing" not in body, "the second renderer cannot duplicate all three shell rings"
    channel = source[source.index("private void renderRitualSphereChanneling"):
                     source.index("private void attemptRitualPrisonerCapture")]
    assert "isInsideRitualSphereViewer(viewer, center)" in channel
    assert channel.count("spawnPatternRing") == 3
    assert "24, phase" in channel
    assert "Particle.CLOUD" not in channel + body
    assert 'clearWorldVfxBeamsOutside("wave6-ritual-", desired)' in body


def test_wave6_caster_display_name_is_plain_zaklinatel() -> None:
    source = read(SERVER)
    assert 'ChatColor.LIGHT_PURPLE + "Заклинатель"' in source
    assert "Кастёр Сферы" not in source
