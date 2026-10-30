"""Execute guardian warning snapshot/restore through the production adapter methods."""
from pathlib import Path
import subprocess

import pytest

from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
SOURCES = ROOT / "copimine-end-event/src"


def _compile_probe(source_path, out):
    source = source_path.read_text(encoding="utf-8")
    methods = "\n".join(_method(source, signature) for signature in (
        "private void ensurePermanentTentacles(",
        "private void restoreTentacleGuardianState(",
        "private void encodeTentacleGuardianState(",
    ))
    java = out / "GuardianWarningRestartProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.stream.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.TentacleController;
public class GuardianWarningRestartProbe {
    enum EventPhase {BOSS_ACTIVE,RECOVERY_REQUIRED}
    enum Particle {SCULK_SOUL}
    enum Sound {BLOCK_SCULK_SENSOR_CLICKING}
    enum SoundCategory {HOSTILE}
    static class World {
        int warnings;
        void spawnParticle(Particle p,Location l,int n,double x,double y,double z,double d){warnings++;}
        void playSound(Object... args){}
    }
    static class Location {
        final World world=new World(); World getWorld(){return world;}
        public Location clone(){return this;} Location add(double x,double y,double z){return this;}
    }
    static class Entity {
        final UUID id=UUID.randomUUID(); boolean valid=true,dead;
        UUID getUniqueId(){return id;}boolean isValid(){return valid;}boolean isDead(){return dead;}
    }
    static class LivingEntity extends Entity {}
    static class ItemDisplay extends Entity {}
    final TentacleController tentacleController=new TentacleController();
    final Map<Integer,Double> tentacleGuardianHealth=new HashMap<>();
    final Map<Integer,Long> tentacleGuardianRespawnDeadlineMillis=new HashMap<>();
    final Set<Integer> tentacleGuardianWarnedSlots=new HashSet<>();
    final Map<Integer,Integer> tentacleGuardianSpawnFailures=new HashMap<>();
    final Map<UUID,Entity> ownedEntities=new HashMap<>();
    final Map<UUID,UUID> tentacleHitboxesByDisplay=new HashMap<>(),tentacleDisplaysByHitbox=new HashMap<>();
    long generation=7,tentacleActiveMillis,tentacleDamageWindowEndsMillis=-1;
    long tentacleGuardianRegenerationDisabledGeneration=Long.MIN_VALUE,tentacleGuardianRegenerationNextStepMillis;
    double tentacleGuardianRegenerationGranted;
    String eventId="official",recoveryReason;
    BossPhase bossPhase=BossPhase.LAST_SEAL;
    EventPhase phase=EventPhase.BOSS_ACTIVE;
    final LivingEntity boss=new LivingEntity(); final Location position=new Location();
    int spawned,failures;
    GuardianWarningRestartProbe(){tentacleController.begin(generation);}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    boolean ownedBySession(Entity e,String id,long gen){return true;}
    boolean tentacleRespawnFrozen(){return tentacleDamageWindowEndsMillis>tentacleActiveMillis;}
    boolean tentacleGuardianRegenerationDisabled(){return tentacleGuardianRegenerationDisabledGeneration==generation;}
    Location permanentTentacleLocation(int slot,int desired){return position;}
    void removeTentacle(UUID id,String reason){tentacleController.remove(id);ownedEntities.remove(id);}
    void recordTentacleGuardianSpawnFailure(int slot){failures++;}
    void updateTentacleGuardianHealthVisual(ItemDisplay display,double hp,double max){}
    double tentacleMaxHealth(TentacleAnimationPolicy.Kind kind){return 60;}
    void renderGuardianShieldBreak(LivingEntity boss,boolean broken){}
    ItemDisplay spawnTentacle(LivingEntity boss,Location pos,boolean temp,int slot,Object target){
        var display=new ItemDisplay();var hitbox=new LivingEntity();
        ownedEntities.put(display.id,display);ownedEntities.put(hitbox.id,hitbox);
        tentacleHitboxesByDisplay.put(display.id,hitbox.id);tentacleDisplaysByHitbox.put(hitbox.id,display.id);
        tentacleController.register(generation,display.id,false,slot,0);
        tentacleGuardianHealth.put(slot,60.0);spawned++;return display;
    }
''' + methods + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static GuardianWarningRestartProbe dead(){
        var p=new GuardianWarningRestartProbe();
        p.tentacleGuardianHealth.put(0,0.0);p.tentacleGuardianRespawnDeadlineMillis.put(0,90_000L);
        return p;
    }
    public static void main(String[] args){
        String scenario=args[0];
        if(scenario.startsWith("restart-")){
            long savedAt=Long.parseLong(scenario.substring(8));
            var original=dead();original.tentacleActiveMillis=85_000L;
            original.ensurePermanentTentacles(original.boss,1);
            check(original.position.world.warnings==1 && original.spawned==0,"initial full warning missing");
            original.tentacleActiveMillis=savedAt;
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            restored.ensurePermanentTentacles(restored.boss,1);
            long remaining=90_000L-savedAt;
            check(restored.tentacleGuardianRespawnDeadlineMillis.get(0)==remaining,
                    "restart discarded elapsed warning; expected "+remaining+"ms but got "+restored.tentacleGuardianRespawnDeadlineMillis.get(0));
            check(restored.position.world.warnings==0,"already warned slot replayed its warning on restart");
            restored.tentacleActiveMillis=remaining-1;restored.ensurePermanentTentacles(restored.boss,1);
            check(restored.spawned==0,"respawn preceded its exact remaining active time");
            restored.tentacleActiveMillis=remaining;restored.ensurePermanentTentacles(restored.boss,1);
            restored.ensurePermanentTentacles(restored.boss,1);
            check(restored.spawned==1,"restored slot did not respawn once at its preserved deadline");
        }else if(scenario.equals("legacy-unwarned")){
            var original=dead();original.tentacleActiveMillis=89_000;
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            restored.ensurePermanentTentacles(restored.boss,1);
            check(restored.position.world.warnings==1 && restored.spawned==0
                    && restored.tentacleGuardianRespawnDeadlineMillis.get(0)==5_000L,
                    "legacy snapshot invented a warning receipt or spawned without full warning");
        }else if(scenario.equals("independent-slots")){
            var original=dead();original.tentacleGuardianHealth.put(1,0.0);
            original.tentacleGuardianRespawnDeadlineMillis.put(1,120_000L);
            original.tentacleActiveMillis=85_000;original.ensurePermanentTentacles(original.boss,2);
            original.tentacleActiveMillis=89_000;
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            restored.ensurePermanentTentacles(restored.boss,2);
            check(restored.tentacleGuardianRespawnDeadlineMillis.get(0)==1_000L
                    && restored.tentacleGuardianRespawnDeadlineMillis.get(1)==31_000L,
                    "restart coupled the independent slot clocks");
            restored.tentacleActiveMillis=1_000;restored.ensurePermanentTentacles(restored.boss,2);
            check(restored.spawned==1 && restored.position.world.warnings==0,"slot B shared slot A warning or spawn");
            restored.tentacleActiveMillis=26_000;restored.ensurePermanentTentacles(restored.boss,2);
            check(restored.position.world.warnings==1 && restored.spawned==1,"slot B warning deadline changed");
            restored.tentacleActiveMillis=31_000;restored.ensurePermanentTentacles(restored.boss,2);
            check(restored.spawned==2,"slot B lost its independent respawn");
        }else if(scenario.equals("deferred-again")){
            var original=dead();original.tentacleActiveMillis=85_000;original.ensurePermanentTentacles(original.boss,1);
            original.tentacleActiveMillis=89_000;
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            restored.tentacleDamageWindowEndsMillis=15_000;
            restored.ensurePermanentTentacles(restored.boss,1);
            check(!restored.tentacleGuardianWarnedSlots.contains(0)
                    && restored.tentacleGuardianRespawnDeadlineMillis.get(0)==15_000L,
                    "new vulnerability grace did not invalidate the old appearance warning");
            restored.tentacleActiveMillis=10_000;restored.ensurePermanentTentacles(restored.boss,1);
            check(restored.position.world.warnings==1 && restored.spawned==0,"deferred appearance needs fresh full warning");
            restored.tentacleActiveMillis=15_000;restored.ensurePermanentTentacles(restored.boss,1);
            check(restored.spawned==1,"deferred warned guardian failed to return");
        }else{
            var original=dead();var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            if(scenario.equals("invalid-orphan"))saved.clear();
            saved.put("boss-guardian-respawn-warned.0",scenario.equals("invalid-value")?"MAYBE":"DONE");
            if(scenario.equals("invalid-live"))saved.put("tentacle-guardian.0.health","10.0");
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            check(restored.phase==EventPhase.RECOVERY_REQUIRED && restored.tentacleGuardianWarnedSlots.isEmpty(),
                    "malformed warning receipt bypassed safe recovery: "+scenario);
        }
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(SOURCES),
                             "-d", str(out), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return out


@pytest.fixture(scope="module")
def guardian_warning_probe(tmp_path_factory):
    return _compile_probe(SOURCE, tmp_path_factory.mktemp("guardian-warning-restart"))


@pytest.mark.parametrize("scenario", [
    "restart-85000", "restart-89000", "restart-89999", "legacy-unwarned",
    "independent-slots", "deferred-again", "invalid-value", "invalid-live", "invalid-too-early", "invalid-orphan",
])
def test_guardian_warning_restart_keeps_earned_active_time(guardian_warning_probe, scenario):
    result = subprocess.run(["java", "-cp", str(guardian_warning_probe), "GuardianWarningRestartProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
