"""Cover Wave 7 wall admission and server-controlled Marksman movement."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_wave7_wall_gate_is_used_by_fangs_targets_and_step():
    source = SOURCE.read_text(encoding="utf-8")
    fangs = extract(source, "boolean spawnNamedTrialFangs(LivingEntity actor, Player target,")
    damage = extract(source, "boolean namedTrialProjectileMayHit(Entity projectile, Player target)")
    resolve = extract(source, "boolean resolveNamedTrialAttack(LivingEntity actor, Player target,")
    targets = extract(source, "Player closestRealitySplitTrialTarget(Entity source, int chamber)")
    step = extract(source, "boolean stepNamedTrialArchmage(LivingEntity actor, Player target, int chamber)")
    assert "hasNamedTrialLineOfSight(actor.getEyeLocation(), target.getEyeLocation())" in fangs
    assert "Attack.ARCHMAGE_FANGS.name()" in damage
    assert "hasNamedTrialLineOfSight(actor.getEyeLocation(), target.getEyeLocation())" in damage
    assert "hasNamedTrialLineOfSight(actor.getEyeLocation(), target.getEyeLocation())" in resolve
    assert "hasNamedTrialLineOfSight(living.getEyeLocation(), player.getEyeLocation())" in targets
    assert "hasNamedTrialLineOfSight(actor.getEyeLocation()" in step
    assert "teleportCombatEntity(actor, destination)" in step


def test_marksman_moves_to_cover_or_a_clear_attack_angle():
    source = SOURCE.read_text(encoding="utf-8")
    movement = extract(source, "void moveNamedTrialMarksman(LivingEntity actor, int chamber)")
    assert "closestRealitySplitTrialParticipant(actor)" in movement
    assert "boolean seekCover" in movement
    assert "boolean projectedVisible" in movement
    assert "if (seekCover ? !projectedVisible : projectedVisible)" in movement
    assert "isSafeCombatEntityLocation(next, actor)" in movement


def test_production_line_of_sight_helper_rejects_blocked_or_cross_world_rays(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    helper = extract(source, "boolean hasNamedTrialLineOfSight(Location from, Location to)")
    bukkit = tmp_path / "org" / "bukkit" / "FluidCollisionMode.java"
    bukkit.parent.mkdir(parents=True, exist_ok=True)
    bukkit.write_text("package org.bukkit; public enum FluidCollisionMode { NEVER }\n", encoding="utf-8")
    probe = tmp_path / "Wave7LineOfSightProbe.java"
    probe.write_text('''
public class Wave7LineOfSightProbe {
    static final class Vector {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}
        double length(){return Math.sqrt(x*x+y*y+z*z);}
        Vector normalize(){double length=length();if(length>0){x/=length;y/=length;z/=length;}return this;}
    }
    static final class World {
        boolean blocked;
        World(boolean blocked){this.blocked=blocked;}
        Object rayTraceBlocks(Location from,Vector direction,double distance,org.bukkit.FluidCollisionMode fluids,boolean ignorePassable){
            return blocked?new Object():null;
        }
    }
    static final class Location {
        final World world; final double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        World getWorld(){return world;}
        Vector toVector(){return new Vector(x,y,z);}
    }
''' + helper + '''
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var clear=new Wave7LineOfSightProbe();
        var open=new World(false);var wall=new World(true);
        require(clear.hasNamedTrialLineOfSight(new Location(open,0,1,0),new Location(open,0,1,8)),
                "an unobstructed same-world line must be admitted");
        require(!clear.hasNamedTrialLineOfSight(new Location(wall,0,1,0),new Location(wall,0,1,8)),
                "a solid block ray hit must deny line of sight");
        require(!clear.hasNamedTrialLineOfSight(new Location(open,0,1,0),new Location(wall,0,1,8)),
                "cross-world combat rays must be denied");
    }
}
''', encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(bukkit), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(
        ["java", "-cp", str(tmp_path), "Wave7LineOfSightProbe"],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
