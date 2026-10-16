"""Execute the actual ownership adapter, including spawn and rehydration entry."""
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def daylight_probe(tmp_path_factory):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    def declaration(signature):
        start = source.index(signature)
        opening = source.index("{", start)
        depth, end = 1, opening + 1
        while depth:
            depth += (source[end] == "{") - (source[end] == "}")
            end += 1
        return source[start:end]
    methods = (declaration("private void registerOwnedEntity(Entity entity)") + "\n"
               + declaration("private boolean isWaveCombatKind(String kind)") + "\n"
               + declaration("private void rememberWave7ProjectileIncarnations(Entity entity, int wave)"))
    directory = tmp_path_factory.mktemp("wave-daylight")
    fixture = directory / "WaveDaylightProbe.java"
    fixture.write_text(r'''
import java.util.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class WaveDaylightProbe {
    Object keyKind=new Object(), keyWave=new Object();
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    long generation=1; String eventId="event"; Scope encounterResourceScope;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Map<UUID,Map<UUID,Long>> wave7ProjectileIncarnations=new LinkedHashMap<>();
    Set<UUID> officialRewardRoster=Set.of();
    boolean isOfficialWave7ReturnContext(){return false;}
    boolean isOfficialEntity(Entity entity){return true;}
    static final String EVENT_KIND_WAVE_MOB="WAVE_MOB", EVENT_KIND_ELITE="ELITE",
        EVENT_KIND_WAVE_GUARDIAN="WAVE_GUARDIAN", EVENT_KIND_REALITY_SPLIT_TRIAL="REALITY_SPLIT_TRIAL",
        EVENT_KIND_RITUAL_CASTER="RITUAL_CASTER", EVENT_KIND_RITUAL_GUARD="RITUAL_GUARD";
    static class Entity {
        UUID id=UUID.randomUUID(); String kind; int wave; boolean valid=true;
        Entity(String kind,int wave){this.kind=kind;this.wave=wave;}
        UUID getUniqueId(){return id;} boolean isValid(){return valid;} boolean isDead(){return false;}
        Type getType(){return Type.SKELETON;}
    }
    enum Type { SKELETON }
    static class Projectile extends Entity {Projectile(){super("PROJECTILE",7);}}
    static class Skeleton extends Entity {
        boolean daylight=true; int fireTicks=27; double health=43;
        Skeleton(String kind,int wave){super(kind,wave);}
        void setShouldBurnInDay(boolean value){daylight=value;}
    }
    static class EndRiftObjective {
        static boolean isNumberedWave(int wave){return wave>=1&&wave<=7;}
    }
    static class Scope {
        long generation(){return 1;} void registerEntity(UUID id,Runnable cleanup){}
    }
    String readString(Entity entity,Object key){return entity.kind;}
    int readInt(Entity entity,Object key,int fallback){return entity.wave;}
    boolean isPersistentLayoutEntity(Entity entity,String kind,int wave){return false;}
    boolean isScopedTransientEncounterEntity(Entity entity,String kind,int wave,boolean layout){return false;}
    void removeScopedOwnedEntity(UUID id,long owner){}
    void emitDiagnostic(String category,String action,String severity,int wave,String reason,UUID player,UUID entity,String correlation,Map<String,?> fields){}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        WaveDaylightProbe probe=new WaveDaylightProbe();
        String kind=args[0]; int wave=Integer.parseInt(args[1]);
        Skeleton skeleton=new Skeleton(kind,wave);
        boolean protectedWave=probe.isWaveCombatKind(kind)&&EndRiftObjective.isNumberedWave(wave);
        probe.registerOwnedEntity(skeleton);
        check(skeleton.daylight!=protectedWave,"numbered-wave skeleton sunlight must not kill a guard or advance its objective");
        check(skeleton.fireTicks==27&&skeleton.health==43,"actual fire hazards and current combat health must remain unchanged");
        check(probe.wave7ProjectileIncarnations.isEmpty(),"living skeleton registration must not fabricate projectile receipts");
        skeleton.daylight=true;
        probe.registerOwnedEntity(skeleton);
        check(skeleton.daylight!=protectedWave,"idempotent reindex must restore the daylight rule without resetting health");
        probe.registerOwnedEntity(null);
    }
''' + methods + "\n}\n", encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    result = subprocess.run(["javac", "-d", str(directory), str(lifecycle), str(fixture)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("kind,wave", [
    *(('WAVE_MOB', wave) for wave in range(1, 8)),
    ('ELITE', 4), ('WAVE_GUARDIAN', 7), ('RITUAL_GUARD', 6), ('REALITY_SPLIT_TRIAL', 7),
    ('NATURAL', 6), ('BOSS', 0), ('WAVE_MOB', 0), ('WAVE_MOB', 8),
])
def test_real_wave_skeleton_registration(daylight_probe, kind, wave):
    result = subprocess.run(["java", "-cp", str(daylight_probe), "WaveDaylightProbe", kind, str(wave)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
