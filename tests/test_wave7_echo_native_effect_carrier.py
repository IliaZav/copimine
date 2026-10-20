"""Execute the production carrier factory; undead immunity was reproduced on Paper."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_echo_carrier_accepts_regeneration_with_player_like_health(tmp_path):
    main = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    start = re.search(r"\s+(?:Husk|Pillager) carrier = spawn\.getWorld\(\)\.spawn\(spawn,", main).start()
    end = main.index("            tag(carrier, ECHO_PROBE_KIND", start)
    factory = main[start:end]
    # Boundaries model the observed pinned-runtime immunity and species defaults.
    # The factory itself is the real production source; native readback is separate.
    java = r'''
import java.util.function.Consumer;
public class EchoCarrierFactoryChecks {
    enum Attribute { GENERIC_MAX_HEALTH }
    static class AttributeInstance {double maximum;void setBaseValue(double value){maximum=value;}}
    static class Mob {
        final AttributeInstance maximum=new AttributeInstance();double health;boolean persistent=true,undead,canJoinRaid=true;
        Mob(double maximum,boolean undead){this.maximum.maximum=maximum;health=maximum;this.undead=undead;}
        void setPersistent(boolean value){persistent=value;}
        void setCanJoinRaid(boolean value){canJoinRaid=value;}
        void setHealth(double value){health=value;}
        AttributeInstance getAttribute(Attribute attribute){return maximum;}
        boolean addRegeneration(){return !undead;}
    }
    public static class Husk extends Mob {public Husk(){super(20,true);}}
    public static class Pillager extends Mob {public Pillager(){super(24,false);}}
    static class World {
        <T extends Mob>T spawn(Location location,Class<T> type,Consumer<T> configure)throws Exception {
            T mob=type.getConstructor().newInstance();configure.accept(mob);return mob;
        }
    }
    static class Location {World getWorld(){return new World();}}
    static Mob productionFactory(Location spawn)throws Exception {
''' + factory + r'''
        return carrier;
    }
    public static void main(String[] args)throws Exception {
        Mob carrier=productionFactory(new Location());
        if(!carrier.addRegeneration())throw new AssertionError("Echo carrier rejects native golden-apple regeneration");
        if(carrier.health!=20||carrier.maximum.maximum!=20)throw new AssertionError("Echo carrier needs explicit player-like 20 HP");
        if(carrier.persistent)throw new AssertionError("disposable carrier became persistent");
        if(carrier.canJoinRaid)throw new AssertionError("Echo carrier can join an unrelated native raid");
    }
}
'''
    source = tmp_path / "EchoCarrierFactoryChecks.java"
    source.write_text(java, encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(source)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "EchoCarrierFactoryChecks"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
