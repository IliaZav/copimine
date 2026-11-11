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
    long cocoonFallGeneration=Long.MIN_VALUE;
    boolean cocoonFallStarted,cocoonLandingPlayed;
    int cocoonFallElapsedTicks=-1,cocoonFallMoveFailures;
    me.copimine.endevent.domain.CocoonFallPolicy.Stage cocoonFallStage=
            me.copimine.endevent.domain.CocoonFallPolicy.Stage.COCOONED;

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
    // Contract 17.1: the spawn loop consults the death ledger before rebuilding
    // a slot, so the field has to exist even where this probe ignores it.
    me.copimine.endevent.domain.GuardianDeathLedger guardianDeaths=
            new me.copimine.endevent.domain.GuardianDeathLedger(7L);
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
        if(scenario.startsWith("fall-")) {
            var original=dead();original.tentacleDamageWindowEndsMillis=15_000L;
            original.cocoonFallStarted=true;original.cocoonFallGeneration=original.generation;
            int elapsed=Integer.parseInt(scenario.substring(5));
            original.cocoonFallElapsedTicks=elapsed;
            original.cocoonFallStage=elapsed<=40?CocoonFallPolicy.Stage.FALLING:
                    elapsed<=60?CocoonFallPolicy.Stage.LANDING:CocoonFallPolicy.Stage.MELEE_FURY;
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            check(restored.phase!=EventPhase.RECOVERY_REQUIRED,"valid fall refused");
            check(restored.cocoonFallGeneration==restored.generation && restored.cocoonFallStarted,
                    "restart lost fall ownership");
            check(restored.cocoonFallStage==original.cocoonFallStage
                    && restored.cocoonFallElapsedTicks==elapsed,"restart replayed fall stage/time");
            check(restored.cocoonLandingPlayed==(elapsed>40),"restart replayed landing cue");
            check(restored.guardianDeaths.cocoonBroken() && restored.guardianDeaths.isDestroyed(0),
                    "fall restore rebuilt a cocoon");
            restored.tentacleActiveMillis=9_000_000;restored.ensurePermanentTentacles(restored.boss,1);
            check(restored.spawned==0 && restored.position.world.warnings==0,"fallen boss regained guardians");
        }else if(scenario.startsWith("bad-fall-")) {
            var original=dead();original.tentacleDamageWindowEndsMillis=15_000L;
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            if(scenario.equals("bad-fall-orphan")) {
                saved.clear();saved.put(CocoonFallPolicy.SNAPSHOT_KEY,"MELEE_FURY:61");
            }else if(!scenario.equals("bad-fall-missing")) saved.put(CocoonFallPolicy.SNAPSHOT_KEY,
                    scenario.substring("bad-fall-".length()));
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            check(restored.phase==EventPhase.RECOVERY_REQUIRED,"malformed/missing fall receipt accepted");
        }else if(!scenario.startsWith("invalid-")) {
            // Master 17.1 supersedes the old 90-second return assertions.
            // Reconstruct the actual permanent death ledger from saved HP.
            var original=dead();
            original.tentacleActiveMillis=scenario.startsWith("restart-")
                    ?Long.parseLong(scenario.substring(8)):89_000L;
            if(scenario.equals("independent-slots")) {
                original.tentacleGuardianHealth.put(1,60.0);
            }
            var saved=new HashMap<String,String>();original.encodeTentacleGuardianState(saved);
            var restored=new GuardianWarningRestartProbe();restored.restoreTentacleGuardianState(saved);
            check(restored.phase!=EventPhase.RECOVERY_REQUIRED,"legal partial guardian snapshot refused");
            check(restored.guardianDeaths.isDestroyed(0),"restart lost permanent death");
            if(scenario.equals("independent-slots"))
                check(restored.guardianDeaths.isLiving(1),"living slot was marked destroyed");
            for(long elapsed:new long[]{0,1_000,90_000,180_000,9_000_000}) {
                restored.tentacleActiveMillis=elapsed;
                restored.ensurePermanentTentacles(restored.boss,1);
            }
            check(restored.spawned==0 && restored.position.world.warnings==0,
                    "destroyed guardian warned or respawned after restart");
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
def test_guardian_restart_preserves_permanent_deaths_and_rejects_invalid_receipts(guardian_warning_probe, scenario):
    result = subprocess.run(["java", "-cp", str(guardian_warning_probe), "GuardianWarningRestartProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("scenario", [
    "fall-0", "fall-20", "fall-40", "fall-41", "fall-60", "fall-61",
    "bad-fall-missing", "bad-fall-orphan", "bad-fall-MELEE_FURY:0", "bad-fall-FALLING:61",
    "bad-fall-LANDING:1", "bad-fall-LANDING:40", "bad-fall-MELEE_FURY:60",
    "bad-fall-COCOONED:0", "bad-fall-unknown:5",
])
def test_actual_guardian_snapshot_resumes_fall_without_new_cocoon(guardian_warning_probe, scenario):
    result = subprocess.run(["java", "-cp", str(guardian_warning_probe), "GuardianWarningRestartProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
