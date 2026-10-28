"""A committed animation owns facing and grounded movement until recovery."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_committed_boss_stops_navigation_without_hovering(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = extract(source, "void holdBossCastPosition(") + "\n" + extract(source, "void aimBossCastAt(")
    java = tmp_path / "BossCastPositionProbe.java"
    java.write_text(r'''
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
''' + methods + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new BossCastPositionProbe();var b=new Mob();p.holdBossCastPosition(b);
        check(b.target==null&&b.path.stopped&&!b.ai&&!b.aware,"native pursuit survived cast commitment");
        check(b.gravity&&b.velocity.x()==0&&b.velocity.z()==0&&b.velocity.y()==0,"committed boss drifted or flew");
        check(b.yaw==73,"committed aim changed");
        b.velocity=new Vector(1,-.7,2);p.holdBossCastPosition(b);
        check(b.velocity.y()==-.7,"grounded fall was suspended");
        b.velocity=new Vector(1,Double.NaN,2);p.holdBossCastPosition(b);
        check(b.velocity.y()==0,"invalid vertical velocity survived");
        p.aimBossCastAt(b,new Location(10,0));check(b.yaw==-90,"cast did not aim at its committed target");
        p.aimBossCastAt(b,new Location(0,-10));check(Math.abs(b.yaw)==180,"next salvo could not reacquire aim");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossCastPositionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
