"""Run the production W6 caster selection and final channel admission decisions.

Only Bukkit entities and scheduling are detached.  These tests are not native
Minecraft evidence; they keep the sphere owning its spell channel while mages
are merely damaged, and they stop the channel on the fifth legitimate death.

Superseded expectation: an earlier build silenced the sphere ability of every
caster whose protection was removed (``AWAKENED_ATTACKING``).  The canonical
contract states that deaths, not damage, retire sphere spell ownership, so an
awakened mage keeps casting and only the fifth death ends the channel.
"""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_portal_layer_runtime import declaration

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def channel_probe(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    dispatch = declaration(source, "private void castNextRitualAbility(long now)")
    selection = dispatch[dispatch.index("        LivingEntity casterEntity = null;"):
                         dispatch.index("        if (casterEntity == null || tactics == null || role == null)")]
    tick = declaration(source, "private void tickRitualSpellController(long now)")
    execute = declaration(tick, "if (snapshot.stage() == RitualSpellController.Stage.EXECUTE)")
    execute = execute[execute.index("{") + 1:execute.index("            boolean applied = switch")]
    folder = tmp_path_factory.mktemp("ritual-channel-admission")
    java = folder / "RitualChannelAdmissionProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RitualCasterTacticsPolicy;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy;
public class RitualChannelAdmissionProbe {
    static class World {}
    static final World WORLD=new World();
    static class Entity {
        final UUID id=UUID.randomUUID();
        UUID getUniqueId(){return id;} World getWorld(){return WORLD;}
    }
    static class LivingEntity extends Entity {
        RitualCasterTacticsPolicy.State state;
        LivingEntity(RitualCasterTacticsPolicy.State s){state=s;}
        void swingMainHand(){}
    }
    static class Mob extends LivingEntity {Mob(RitualCasterTacticsPolicy.State s){super(s);}}
    static class Enderman extends Mob {
        Enderman(RitualCasterTacticsPolicy.State s){super(s);} void setScreaming(boolean value){}
    }
    static class Player extends Entity {boolean online=true; boolean isOnline(){return online;}}
    static class Bukkit {static Player target; static Player getPlayer(UUID id){return target;}}
    static class RitualSpellController {
        enum Stage {EXECUTE}
        enum Spell {RIFT_BARRAGE}
        record Snapshot(UUID caster,Spell spell) {Stage stage(){return Stage.EXECUTE;}}
        int cleared; void clear(long generation){cleared++;}
    }
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    Map<UUID,Integer> ritualCasterSlots=new HashMap<>();
    RitualSpellController ritualSpellController=new RitualSpellController();
    UUID ritualSpellTargetUuid; long generation=7;
    int ritualCasterDeathCount=1, ritualAbilityCursor;
    boolean executed;
    RitualCasterTacticsPolicy.State ritualCasterTacticsState(LivingEntity e){return e.state;}
    boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id);}
    boolean isFreeRitualTarget(Player p){return p!=null&&p.online;}
    RitualCasterProgressionPolicy.MajorSpell majorSpell(RitualSpellController.Spell s){return RitualCasterProgressionPolicy.MajorSpell.RIFT_BARRAGE;}
    Object coreCombatAnchorLocation(){return null;}
    Object ritualSphereCenter(Object p){return null;}
    void faceRitualCaster(Mob mob,Object p){}
    LivingEntity select(List<LivingEntity> casters) {
''' + selection + r'''
        return casterEntity;
    }
    void execute(RitualSpellController.Snapshot snapshot) {
''' + execute + r'''
        executed=true;
    }
    void clearRitualSpellRuntimeCast(){ritualSpellController.clear(generation);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new RitualChannelAdmissionProbe();
        var awake=new Mob(RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING);
        var eligible=new Mob(RitualCasterTacticsPolicy.State.EXPOSED_CASTING);
        p.ritualCasterSlots.put(awake.id,0);p.ritualCasterSlots.put(eligible.id,1);
        switch(args[0]) {
            case "awakened-first-still-casts" -> check(p.select(List.of(awake,eligible))==awake,
                    "a damaged caster keeps its sphere channel instead of silencing the sphere");
            case "awakened-middle-still-casts" -> {
                p.ritualAbilityCursor=1;
                check(p.select(List.of(eligible,awake))==awake,
                        "round-robin selection must not skip a damaged caster that still owns the channel");
            }
            case "all-awakened" -> check(p.select(List.of(awake))==awake,
                    "an all-awakened roster still feeds the sphere until the fifth death");
            case "eligible" -> check(p.select(List.of(eligible))==eligible,"eligible caster must remain selectable");
            case "execute-awakened", "execute-eligible", "execute-owner-missing", "execute-fifth-death" -> {
                var caster=args[0].equals("execute-eligible")?eligible:awake;
                if(!args[0].equals("execute-owner-missing"))p.ownedEntities.put(caster.id,caster);
                if(args[0].equals("execute-fifth-death"))p.ritualCasterDeathCount=5;
                Bukkit.target=new Player();p.ritualSpellTargetUuid=Bukkit.target.id;
                p.execute(new RitualSpellController.Snapshot(caster.id,RitualSpellController.Spell.RIFT_BARRAGE));
                boolean mustExecute=args[0].equals("execute-eligible")||args[0].equals("execute-awakened");
                check(p.executed==mustExecute,
                        "EXECUTE must revalidate the caster channel: damage keeps it, the fifth death and a lost owner stop it");
                if(!p.executed)check(p.ritualSpellController.cleared==1,
                        "rejected EXECUTE must retire the stale scheduler");
            }
            default -> throw new AssertionError("unknown scenario");
        }
    }
}
''', encoding="utf-8")
    compile_result = subprocess.run(["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(folder),
                                     str(DOMAIN / "RitualCasterTacticsPolicy.java"),
                                     str(DOMAIN / "RitualCasterProgressionPolicy.java"), str(java)],
                                    capture_output=True, text=True, timeout=30)
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    return folder


@pytest.mark.parametrize("scenario", ["awakened-first-still-casts", "awakened-middle-still-casts", "all-awakened",
                                      "eligible", "execute-awakened", "execute-eligible",
                                      "execute-owner-missing", "execute-fifth-death"])
def test_major_spell_requires_channel_eligible_owner(channel_probe, scenario):
    result = subprocess.run(["java", "-Xmx64m", "-cp", str(channel_probe),
                             "RitualChannelAdmissionProbe", scenario], capture_output=True, text=True, timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr
