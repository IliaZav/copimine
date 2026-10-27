"""Execute the production presentation method with real Wave 6 spell snapshots."""

from pathlib import Path
import os
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def _java_tool(name: str) -> str:
    executable = name + (".exe" if os.name == "nt" else "")
    if os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    result = shutil.which(name)
    assert result, f"{name} is required for the Wave 6 presentation test"
    return result


@pytest.fixture(scope="module")
def presentation_probe(tmp_path_factory):
    method = _method(EVENT.read_text(encoding="utf-8"),
                     "private void renderRitualSpellPresentation(long now)")
    directory = tmp_path_factory.mktemp("wave6-presentation")
    source = directory / "Wave6PresentationProbe.java"
    source.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.domain.FallingAnvilVolleyPolicy.LandingPoint;
import me.copimine.endevent.runtime.ritual.RitualSpellController;

public class Wave6PresentationProbe {
    static final class World {}
    static final World WORLD = new World();
    static final class Vector {
        double x, y, z;
        Vector(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
        Vector subtract(Vector other) { x-=other.x; y-=other.y; z-=other.z; return this; }
        double lengthSquared() { return x*x+y*y+z*z; }
        Vector normalize() { double n=Math.sqrt(lengthSquared()); if(n>0){x/=n;y/=n;z/=n;} return this; }
        Vector multiply(double n) { x*=n; y*=n; z*=n; return this; }
        double getX() { return x; } double getY() { return y; } double getZ() { return z; }
    }
    static final class Location {
        double x, y, z;
        Location(double x,double y,double z) { this.x=x; this.y=y; this.z=z; }
        World getWorld() { return WORLD; }
        public Location clone() { return new Location(x,y,z); }
        Location add(double a,double b,double c) { x+=a;y+=b;z+=c;return this; }
        Location add(Vector v) { return add(v.x,v.y,v.z); }
        Vector toVector() { return new Vector(x,y,z); }
    }
    static final class Player {
        boolean isOnline() { return true; }
        World getWorld() { return WORLD; }
        Location getLocation() { return new Location(3,70,0); }
    }
    static final class Bukkit {
        static final Player PLAYER = new Player();
        static Player getPlayer(UUID ignored) { return PLAYER; }
    }
    static final class RitualZoneEffectPolicy { static final double RADIUS_BLOCKS=4; }
    static final class RitualSpellVisualPolicy {
        record Point(double x,double y,double z) {}
        record Segment(Point from,Point to) {}
        static List<Segment> ring(double a,double b,double c) { return List.of(new Segment(new Point(0,0,0),new Point(1,0,0))); }
        static List<Segment> gravity(double a) { return ring(a,0,0); }
        static List<Segment> chain(Point a,Point b,double c) { return List.of(new Segment(a,b)); }
    }
    final long generation=7; long eventTickCounter=1, ritualNextSpellVisualMillis, ritualNextSpellSoundMillis;
    RitualSpellController ritualSpellController = new RitualSpellController(generation);
    UUID ritualSpellTargetUuid = new UUID(0,2);
    Location ritualSpellTargetLocation = new Location(3,72,0);
    int cleared, emitted;
    Location coreCombatAnchorLocation() { return new Location(0,70,0); }
    Location ritualSphereCenter(Location core) { return core; }
    boolean isFreeRitualTarget(Player p) { return true; }
    boolean isFiniteLocation(Location p) { return true; }
    double horizontalDistanceSquared(Location a,Location b) { return 0; }
    void clearWorldVfxBeamsByPrefix(String p) { cleared++; }
    void clearWorldVfxBeamsOutside(String p,Set<String> keys) {}
    void emitRitualSpellSegment(String k,Location a,Location b,int c,float width,boolean central,Location sphere) { emitted++; }
    void sendWorldBeamPacket(Player p,String key,Location a,Location b,int c,float width) { emitted++; }
    void renderRitualSpellAmbientFeedback(RitualSpellController.Spell s,Location a,Location b,boolean warning,long now) {}
''' + method + r'''
    public static void main(String[] args) {
        Wave6PresentationProbe p = new Wave6PresentationProbe();
        UUID caster=new UUID(0,1), target=p.ritualSpellTargetUuid;
        boolean accepted = switch(args[0]) {
            case "anchor" -> p.ritualSpellController.startGravityAnchor(p.generation,caster,target,
                new AnchorPoint(3.5,73.5,0.5),0,true,true,true,true,0).accepted();
            case "anvil" -> p.ritualSpellController.startFallingAnvils(p.generation,caster,2,1,
                List.of(new LandingPoint(3,70,0)),true,true,true,true,0).accepted();
            case "legacy" -> p.ritualSpellController.start(p.generation,RitualSpellController.Spell.RIFT_BARRAGE,
                caster,true,true,true,true,0).accepted();
            default -> throw new IllegalArgumentException(args[0]);
        };
        if (!accepted) throw new AssertionError("fixture did not start the requested cast");
        p.renderRitualSpellPresentation(1000);
        if (args[0].equals("legacy") ? p.emitted==0 : p.emitted!=0 || p.cleared==0) {
            throw new AssertionError("wrong legacy presentation behavior for " + args[0]);
        }
        System.out.println("WAVE6_PRESENTATION_PASS " + args[0]);
    }
}
''', encoding="utf-8")
    source_path = os.pathsep.join((str(ROOT / "copimine-end-event/src"),
                                   str(ROOT / "tools/minecraft-26.3/protocol/src/main/java")))
    result = subprocess.run([_java_tool("javac"), "-proc:none", "-encoding", "UTF-8",
                             "-sourcepath", source_path, "-d", str(directory), str(source)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["anchor", "anvil", "legacy"])
def test_wave6_replacement_spells_do_not_enter_legacy_presentation(presentation_probe, scenario):
    result = subprocess.run([_java_tool("java"), "-cp", str(presentation_probe),
                             "Wave6PresentationProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    assert f"WAVE6_PRESENTATION_PASS {scenario}" in result.stdout
