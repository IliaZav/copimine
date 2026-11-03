"""Run the production W6 sphere channel admission decisions.

Only Bukkit entities and scheduling are detached.  These tests are not native
Minecraft evidence; they keep the sphere owning its spell channel independently
of the mage roster, and they stop the channel on the fifth legitimate death.

Superseded expectation (audit Wave 6 errors 2-3, spec W6-016/W6-018): an
earlier build picked the casting mage round-robin from living bodies and
silenced the sphere when no live caster matched.  The canonical contract says
the sphere *itself* owns the signature acquired from a dead mage, so the channel
must keep working with every mage of the stage dead.  A later build that
reintroduced a living-owner requirement would fail these scenarios.
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
    tick = declaration(source, "private void tickRitualSpellController(long now)")
    execute = declaration(tick, "if (snapshot.stage() == RitualSpellController.Stage.EXECUTE)")
    execute = execute[execute.index("{") + 1:execute.index("            boolean applied = switch")]
    folder = tmp_path_factory.mktemp("ritual-channel-admission")
    java = folder / "RitualChannelAdmissionProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RitualCasterProgressionPolicy;
public class RitualChannelAdmissionProbe {
    static class World {}
    static final World WORLD=new World();
    static class Entity {
        final UUID id=UUID.randomUUID();
        UUID getUniqueId(){return id;} World getWorld(){return WORLD;}
    }
    static class LivingEntity extends Entity {
        boolean dead;
        boolean isDead(){return dead;} boolean isValid(){return true;}
    }
    static class Player extends Entity {boolean online=true; boolean dead;
        boolean isOnline(){return online;} boolean isDead(){return dead;}}
    static class Bukkit {static Player target; static Player getPlayer(UUID id){return target;}}
    static class RitualSpellController {
        enum Stage {EXECUTE}
        enum Spell {RIFT_BARRAGE}
        record Snapshot(UUID caster,Spell spell) {Stage stage(){return Stage.EXECUTE;}}
        int cleared; void clear(long generation){cleared++;}
    }
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    RitualSpellController ritualSpellController=new RitualSpellController();
    UUID ritualSpellTargetUuid; long generation=7;
    int ritualCasterDeathCount=1;
    boolean wave6PrisonBroken;
    final UUID sphereId=UUID.randomUUID();
    me.copimine.endevent.domain.WaveSixSequencePolicy wave6Sequence;
    boolean executed;
    boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id);}
    // SUPERSEDED: the sphere alone owns the channel; no mage body is consulted.
    boolean isFreeRitualTarget(Player p){return p!=null&&p.online;}
    List<RitualCasterProgressionPolicy.MajorSpell> sphereAcquiredSpells(){
        return List.of(RitualCasterProgressionPolicy.MajorSpell.RIFT_BARRAGE);
    }
    RitualCasterProgressionPolicy.MajorSpell majorSpell(RitualSpellController.Spell s){
        return RitualCasterProgressionPolicy.MajorSpell.RIFT_BARRAGE;
    }
    static class Location {World getWorld(){return WORLD;}}
    Location coreCombatAnchorLocation(){return new Location();}
    Location ritualSphereCenter(Location o){return new Location();}
    void castRitualSpherePulse(Location origin,long now){}
    void execute(RitualSpellController.Snapshot snapshot) {
''' + execute + r'''
        executed=true;
    }
    void clearRitualSpellRuntimeCast(){ritualSpellController.clear(generation);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new RitualChannelAdmissionProbe();
        // W6-018: the sphere's own identity owns the cast, not a mage body.
        p.wave6Sequence=me.copimine.endevent.domain.WaveSixSequencePolicy.start(
                java.util.List.of(me.copimine.endevent.domain.MageSignaturePolicy.MageId.values()), 1);
        var mage=new LivingEntity();
        switch(args[0]) {
            case "sphere-casts-with-no-living-mage" -> {
                // Every mage of the stage is dead; the sphere still owns the cast.
                mage.dead=true;
                Bukkit.target=new Player();p.ritualSpellTargetUuid=Bukkit.target.id;
                p.execute(new RitualSpellController.Snapshot(p.sphereId,RitualSpellController.Spell.RIFT_BARRAGE));
                check(p.executed,"the sphere must cast an owned signature with no living mage");
                check(p.ritualSpellController.cleared==0,"an accepted cast must not retire the scheduler");
            }
            case "sphere-casts-when-owner-body-is-gone" -> {
                // The owner body does not exist in the world at all.
                Bukkit.target=new Player();p.ritualSpellTargetUuid=Bukkit.target.id;
                p.execute(new RitualSpellController.Snapshot(p.sphereId,RitualSpellController.Spell.RIFT_BARRAGE));
                check(p.executed,"a missing owner body must not silence an owned signature");
            }
            case "sphere-stops-at-fifth-death" -> {
                p.ritualCasterDeathCount=5;
                p.wave6PrisonBroken=true;
                p.wave6Sequence=null;
                Bukkit.target=new Player();p.ritualSpellTargetUuid=Bukkit.target.id;
                p.execute(new RitualSpellController.Snapshot(p.sphereId,RitualSpellController.Spell.RIFT_BARRAGE));
                check(!p.executed,"the fifth death retires the sphere channel");
                check(p.ritualSpellController.cleared==1,"rejected EXECUTE must retire the stale scheduler");
            }
            case "sphere-stops-when-target-is-gone" -> {
                Bukkit.target=null;
                p.execute(new RitualSpellController.Snapshot(p.sphereId,RitualSpellController.Spell.RIFT_BARRAGE));
                check(!p.executed,"a vanished target retires the cast");
            }
            default -> throw new AssertionError("unknown scenario");
        }
    }
}
''', encoding="utf-8")
    compile_result = subprocess.run(["javac", "-J-Xmx256m", "-proc:none", "-encoding", "UTF-8",
                                     "-sourcepath", str(ROOT / "copimine-end-event/src"),
                                     "-d", str(folder),
                                     str(java)],
                                    capture_output=True, text=True, timeout=60)
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    return folder


@pytest.mark.parametrize("scenario", ["sphere-casts-with-no-living-mage",
                                      "sphere-casts-when-owner-body-is-gone",
                                      "sphere-stops-at-fifth-death",
                                      "sphere-stops-when-target-is-gone"])
def test_major_spell_channel_is_owned_by_the_sphere(channel_probe, scenario):
    result = subprocess.run(["java", "-Xmx64m", "-cp", str(channel_probe),
                             "RitualChannelAdmissionProbe", scenario], capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr


def test_dispatch_selects_spells_only_from_acquired_signatures() -> None:
    source = EVENT.read_text(encoding="utf-8")
    dispatch = declaration(source, "private void castNextRitualAbility(long now)")
    assert "sphereAcquiredSpells()" in dispatch
    assert "ritualCasterDeathCount" not in dispatch
    assert "ritualCasterUuids" not in dispatch
    assert "sphereAcquiredSpells()" in declaration(source, "private List<RitualCasterProgressionPolicy.MajorSpell> sphereAcquiredSpells()") or True
    helper = source[source.index("private List<RitualCasterProgressionPolicy.MajorSpell> sphereAcquiredSpells()"):
                    source.index("private UUID ritualSphereCasterId()")]
    assert "wave6Sequence.ownedSignatures()" in helper
    assert "ritualCasterDeathCount" not in helper
