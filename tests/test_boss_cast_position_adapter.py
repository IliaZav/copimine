"""A committed cast owns facing, and only a STATIONARY cast locks locomotion.

SUPERSEDES the original form of this test, which asserted that every cast stopped
native pursuit and cleared AI. That was the freeze bug: nothing restored those
flags when the cast ended, so repeated casts left the Guardian permanently
immobile. The contract now distinguishes casts:

  * a MOBILE cast (projectile, arrows, void blast/mark, fireball, summons) keeps
    its movement controller; clearing the path here is exactly what froze it;
  * a STATIONARY cast (swipes, slams, inferno, final strike) stops the path for
    its own authored window only, and native AI stays enabled so the body cannot
    inherit a disabled flag after release.

Facing, gravity and a grounded vertical velocity remain universal, because a boss
must never float or aim at a stale direction once a cast ends.
"""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_committed_boss_stops_navigation_without_hovering(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    # Extract the two-argument overload only. The one-argument entry point is a
    # delegate, and the probe supplies its own so the spell can be chosen per
    # scenario; extracting both would redeclare it.
    methods = (
        extract(source, "void holdBossCastPosition(LivingEntity boss, EndRiftAiPolicy.BossSpell spell)")
        + "\n"
        + extract(source, "void aimBossCastAt(")
    )
    java = tmp_path / "BossCastPositionProbe.java"
    java.write_text(r'''
import me.copimine.endevent.domain.BossCastMobilityPolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;
public class BossCastPositionProbe {
    record Vector(double x,double y,double z){double getY(){return y;}}
    record Location(double x,double z){Object getWorld(){return "end";}double getX(){return x;}double getZ(){return z;}}
    static class LivingEntity {
        Vector velocity=new Vector(2,.5,3);boolean valid=true,dead,gravity;float yaw;
        boolean isValid(){return valid;}boolean isDead(){return dead;}
        Vector getVelocity(){return velocity;}void setVelocity(Vector v){velocity=v;}
        Object getWorld(){return "end";}Location getLocation(){return new Location(0,0);}
        void setGravity(boolean value){gravity=value;}void setRotation(float y,float p){yaw=y;}
    }
    static class Pathfinder {boolean stopped;void stopPathfinding(){stopped=true;}}
    static class Mob extends LivingEntity {
        final Pathfinder path=new Pathfinder();Object target=new Object();boolean ai=true,aware=true;
        void setTarget(Object t){target=t;}Pathfinder getPathfinder(){return path;}
        void setAI(boolean b){ai=b;}void setAware(boolean b){aware=b;}
    }
    float bossCastLockedYaw=73;
    EndRiftAiPolicy.BossSpell previousBossSpell;

    // Probe-side delegate: the scenario picks the spell instead of reading it
    // off the runtime's "previous spell" field.
    void holdBossCastPosition(LivingEntity boss){ holdBossCastPosition(boss, previousBossSpell); }
''' + methods + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new BossCastPositionProbe();

        // A MOBILE cast must not touch the movement controller at all. This is the
        // regression that froze the Guardian: before the fix, every cast stopped
        // the path, cleared AI and cleared awareness, and nothing restored them.
        var mobile=new Mob();Object focus=mobile.target;
        p.previousBossSpell=EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE;
        p.holdBossCastPosition(mobile);
        check(mobile.target==focus,"mobile cast discarded the stable pursuit focus");
        check(!mobile.path.stopped,"a mobile cast stopped pathfinding and froze the boss");
        check(mobile.ai&&mobile.aware,"a mobile cast disabled native AI");
        // Gravity is restored before the mobile early-return, so the boss is still
        // pulled down. Its velocity is deliberately NOT clamped: a mobile cast
        // keeps its movement controller, including a jump it was already making.
        check(mobile.gravity,"mobile cast left the boss without gravity");
        check(mobile.velocity.x()==2&&mobile.velocity.z()==3,
                "mobile cast overrode the movement controller it is supposed to leave alone");

        // A STATIONARY cast still owns locomotion for its own authored window,
        // but native AI stays enabled so no flag survives the release.
        var fixed=new Mob();
        p.previousBossSpell=EndRiftAiPolicy.BossSpell.GROUND_SLAM;
        p.holdBossCastPosition(fixed);
        check(fixed.path.stopped,"a stationary cast must stop the path for its window");
        check(fixed.ai&&fixed.aware,"stationary cast disabled native AI, which release cannot restore");
        check(fixed.gravity,"committed boss lost gravity");
        check(fixed.velocity.x()==0&&fixed.velocity.z()==0,"committed boss drifted horizontally");
        check(fixed.velocity.y()<=0,"committed boss was launched upward");
        check(fixed.yaw==73,"committed aim changed");

        // A grounded fall is preserved, an invalid vertical component is not.
        var falling=new Mob();falling.velocity=new Vector(1,-.7,2);
        p.previousBossSpell=EndRiftAiPolicy.BossSpell.CHEST_STRIKE;
        p.holdBossCastPosition(falling);
        check(falling.velocity.y()==-.7,"grounded fall was suspended");

        var broken=new Mob();broken.velocity=new Vector(1,Double.NaN,2);
        p.previousBossSpell=EndRiftAiPolicy.BossSpell.CHEST_STRIKE;
        p.holdBossCastPosition(broken);
        check(broken.velocity.y()==0,"invalid vertical velocity survived");

        // Aiming still works and can be reacquired.
        var aiming=new Mob();
        p.aimBossCastAt(aiming,new Location(10,0));check(aiming.yaw==-90,"cast did not aim at its committed target");
        p.aimBossCastAt(aiming,new Location(0,-10));check(Math.abs(aiming.yaw)==180,"next salvo could not reacquire aim");

        // A dead or invalid boss is never touched.
        var dead=new Mob();dead.dead=true;float before=dead.yaw;
        p.previousBossSpell=EndRiftAiPolicy.BossSpell.GROUND_SLAM;
        p.holdBossCastPosition(dead);
        check(dead.yaw==before&&!dead.path.stopped,"a dead boss was still repositioned");
    }
}
''', encoding="utf-8")
    # The extracted body calls into the REAL BossCastMobilityPolicy, so the
    # domain tree is on the sourcepath rather than stubbed: a hand-written copy
    # of the policy could drift from the rules the runtime actually obeys.
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8",
         "-sourcepath", str(ROOT / "copimine-end-event/src"),
         "-d", str(tmp_path), str(java)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossCastPositionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
