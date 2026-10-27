"""Exercise production guardian respawn, saved HP, warning and regeneration fencing."""

from pathlib import Path
import subprocess

from tests.test_boss_phase_damage_transaction_adapter import _method


ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "copimine-end-event/src"


def test_guardian_recovery_preserves_damage_and_independent_effective_deadlines(tmp_path):
    source = (SOURCES / "me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = (
        "private void ensurePermanentTentacles(",
        "private boolean allTentacleGuardiansDefeated(",
        "private boolean commitTentacleGuardianDeathWindow(",
        "private void recordTentacleGuardianSpawnFailure(",
        "private boolean tentacleGuardianRegenerationDisabled()",
        "public void onBossGuardianRegeneration(",
        "private void restoreTentacleGuardianState(",
        "private void encodeTentacleGuardianState(",
        "private int permanentTentacleTarget(",
        "private void tickTentaclesWithoutGuardians(",
    )
    methods = "\n".join(_method(source, signature) for signature in signatures)
    probe = tmp_path / "GuardianRespawnRecoveryProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.stream.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.TentacleController;
public class GuardianRespawnRecoveryProbe {
    enum EventPhase { BOSS_ACTIVE, RECOVERY_REQUIRED }
    enum Particle { SCULK_SOUL }
    enum Sound { BLOCK_SCULK_SENSOR_CLICKING }
    enum SoundCategory { HOSTILE }
    static class Entity {
        final UUID id = UUID.randomUUID(); boolean valid=true, dead=false;
        UUID getUniqueId(){return id;} boolean isValid(){return valid;} boolean isDead(){return dead;}
    }
    static class LivingEntity extends Entity { double hp=60; }
    static class Player extends LivingEntity { }
    static class ItemDisplay extends Entity { final Location location=new Location();
        Location getLocation(){return location;} }
    static class Location { final World world=new World();
        World getWorld(){return world;} public Location clone(){return this;}
        Location add(double x,double y,double z){return this;} }
    static class World { int warnings;
        void spawnParticle(Particle p,Location l,int count,double x,double y,double z,double extra){warnings++;}
        void playSound(Location l,Sound s,SoundCategory c,float volume,float pitch){} }
    static class EntityRegainHealthEvent { final Entity entity; boolean cancelled;
        EntityRegainHealthEvent(Entity e){entity=e;} Entity getEntity(){return entity;}
        void setCancelled(boolean value){cancelled=value;} }
    final TentacleController tentacleController=new TentacleController();
    final Map<Integer,Double> tentacleGuardianHealth=new HashMap<>();
    final Map<Integer,Long> tentacleGuardianRespawnDeadlineMillis=new HashMap<>();
    final Set<Integer> tentacleGuardianWarnedSlots=new HashSet<>();
    final Map<Integer,Integer> tentacleGuardianSpawnFailures=new HashMap<>();
    final Map<UUID,Entity> ownedEntities=new HashMap<>();
    final Map<UUID,UUID> tentacleHitboxesByDisplay=new HashMap<>();
    final Map<UUID,UUID> tentacleDisplaysByHitbox=new HashMap<>();
    long generation=7, tentacleGuardianRegenerationDisabledGeneration=Long.MIN_VALUE;
    long tentacleActiveMillis=0, tentacleDamageWindowEndsMillis=-1;
    EventPhase phase=EventPhase.BOSS_ACTIVE;
    BossPhase bossPhase=BossPhase.LAST_SEAL;
    final UUID eventId=UUID.randomUUID(); final LivingEntity boss=new LivingEntity();
    final Location position=new Location();
    int admitted=10, online=10, spawnAttempts, spawned, saves, breaks, temporaryTicks; boolean failSpawn, saveFails;
    String recoveryReason;
    GuardianRespawnRecoveryProbe(){tentacleController.begin(generation);}
    int eventScalePlayers(){return admitted;}
    boolean tentacleRespawnFrozen(){return tentacleDamageWindowEndsMillis>=0
            && tentacleActiveMillis<tentacleDamageWindowEndsMillis;}
    Location permanentTentacleLocation(int slot,int desired){return position;}
    boolean ownedBySession(Entity actor,UUID event,long gen){return true;}
    boolean isCurrentBossCarrier(LivingEntity entity){return entity==boss;}
    boolean isOfficialBoss(LivingEntity entity){return entity==boss;}
    boolean isOfficialEntity(Entity entity){return entity==boss;}
    boolean isTestBoss(LivingEntity entity){return false;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    long tentacleDamageWindowMillis(){return 15_000L;}
    boolean saveStateSync(){saves++; return !saveFails;}
    void forcePhase(EventPhase state,String reason){phase=state; recoveryReason=reason;}
    void renderGuardianShieldBreak(LivingEntity b,boolean broken){if(broken)breaks++;}
    void clearGuardianShieldOrbit(String reason){}
    void tickTentacle(ItemDisplay display,TentacleController.VisualState state,List<Player> participants){temporaryTicks++;}
    ItemDisplay spawnTentacle(LivingEntity b,Location p,boolean temporary,int slot,Object target){
        spawnAttempts++; if(failSpawn)return null;
        var display=new ItemDisplay(); var hitbox=new LivingEntity();
        ownedEntities.put(display.id,display); ownedEntities.put(hitbox.id,hitbox);
        tentacleHitboxesByDisplay.put(display.id,hitbox.id);
        tentacleDisplaysByHitbox.put(hitbox.id,display.id);
        tentacleController.register(generation,display.id,false,slot,0);
        tentacleGuardianHealth.put(slot,60.0); spawned++; return display;
    }
    void updateTentacleGuardianHealthVisual(ItemDisplay display,double hp,double max){
        ((LivingEntity)ownedEntities.get(tentacleHitboxesByDisplay.get(display.id))).hp=hp;
    }
    double tentacleMaxHealth(TentacleAnimationPolicy.Kind kind){return 60;}
    void removeTentacle(UUID id,String reason){tentacleController.remove(id); ownedEntities.remove(id);}
''' + methods + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var partial=new GuardianRespawnRecoveryProbe();
        partial.tentacleGuardianHealth.put(0,37.5);
        partial.ensurePermanentTentacles(partial.boss,1);
        check(partial.spawned==1 && partial.tentacleGuardianHealth.get(0)==37.5,
                "recreating a missing guardian healed the saved partial HP");
        var display=partial.tentacleController.states().values().iterator().next().entityId();
        var carrier=(LivingEntity)partial.ownedEntities.get(partial.tentacleHitboxesByDisplay.get(display));
        check(carrier.hp==37.5,"restored real carrier HP disagrees with the slot ledger");
        partial.admitted=10; partial.online=2;
        check(partial.permanentTentacleTarget(false)==5,
                "disconnecting players removed slots from the admitted ten-player encounter");

        var deferred=new GuardianRespawnRecoveryProbe();
        deferred.tentacleGuardianHealth.put(0,0.0);
        deferred.tentacleGuardianRespawnDeadlineMillis.put(0,90_000L);
        deferred.tentacleDamageWindowEndsMillis=100_000L;
        deferred.tentacleActiveMillis=85_000L;
        deferred.ensurePermanentTentacles(deferred.boss,1);
        check(deferred.position.world.warnings==0 && deferred.spawned==0,
                "deferred guardian warned fifteen seconds before actual appearance");
        deferred.tentacleActiveMillis=95_000L; deferred.ensurePermanentTentacles(deferred.boss,1);
        check(deferred.position.world.warnings==1 && deferred.spawned==0,
                "effective respawn must warn at 95 seconds");
        deferred.tentacleActiveMillis=99_999L; deferred.ensurePermanentTentacles(deferred.boss,1);
        check(deferred.spawned==0,"guardian appeared before vulnerability grace ended");
        deferred.tentacleActiveMillis=100_000L; deferred.ensurePermanentTentacles(deferred.boss,1);
        check(deferred.spawned==1 && !deferred.tentacleGuardianRespawnDeadlineMillis.containsKey(0),
                "guardian failed to appear after a full effective warning");

        var failed=new GuardianRespawnRecoveryProbe(); failed.failSpawn=true;
        failed.tentacleGuardianHealth.put(0,0.0);
        failed.tentacleGuardianRespawnDeadlineMillis.put(0,90_000L);
        failed.tentacleGuardianWarnedSlots.add(0); failed.tentacleActiveMillis=90_000L;
        for(int i=0;i<4;i++)failed.ensurePermanentTentacles(failed.boss,1);
        check(failed.spawnAttempts==3 && failed.phase==EventPhase.RECOVERY_REQUIRED,
                "failed guardian spawns retried forever instead of bounded recovery");
        check(failed.tentacleGuardianRespawnDeadlineMillis.get(0)==90_000L
                        && failed.tentacleGuardianHealth.get(0)==0.0,
                "failed spawn removed the death receipt or invented a living guardian");

        var deaths=new GuardianRespawnRecoveryProbe();
        deaths.tentacleGuardianHealth.put(0,0.0); deaths.tentacleGuardianHealth.put(1,12.0);
        deaths.tentacleGuardianRespawnDeadlineMillis.put(0,90_000L);
        check(!deaths.allTentacleGuardiansDefeated(2),"missing live actor was counted as killed");
        deaths.tentacleGuardianHealth.put(1,0.0);
        check(!deaths.allTentacleGuardiansDefeated(2),"zero HP without a legal death receipt counted");
        deaths.tentacleGuardianRespawnDeadlineMillis.put(1,175_000L);
        check(deaths.allTentacleGuardiansDefeated(2),"all legal per-slot deaths were not recognized");
        check(!deaths.allTentacleGuardiansDefeated(3),"missing initial slot was counted as dead");
        check(deaths.commitTentacleGuardianDeathWindow(deaths.boss,2)
                        && deaths.saves==1 && deaths.breaks==1,
                "legal all-defender deaths did not durably commit before shield-break effects");
        check(deaths.commitTentacleGuardianDeathWindow(deaths.boss,2)
                        && deaths.saves==1 && deaths.breaks==1,
                "the same death set committed or played the break twice");
        var saveFailure=new GuardianRespawnRecoveryProbe();
        saveFailure.tentacleGuardianHealth.put(0,0.0);
        saveFailure.tentacleGuardianRespawnDeadlineMillis.put(0,90_000L); saveFailure.saveFails=true;
        check(!saveFailure.commitTentacleGuardianDeathWindow(saveFailure.boss,1)
                        && saveFailure.phase==EventPhase.RECOVERY_REQUIRED && saveFailure.breaks==0,
                "failed durable commit published successful shield-break effects");

        deaths.tentacleGuardianRegenerationDisabledGeneration=deaths.generation;
        deaths.tentacleDamageWindowEndsMillis=15_000L; deaths.tentacleActiveMillis=30_000L;
        var encoded=new HashMap<String,String>(); deaths.encodeTentacleGuardianState(encoded);
        var restored=new GuardianRespawnRecoveryProbe(); restored.restoreTentacleGuardianState(encoded);
        check(restored.tentacleGuardianRegenerationDisabled(),"restart forgot permanent regeneration ban");
        check(restored.tentacleDamageWindowEndsMillis==0L,"restart refilled an already expired grace");
        var heal=new EntityRegainHealthEvent(restored.boss); restored.onBossGuardianRegeneration(heal);
        check(heal.cancelled,"respawned defenders re-enabled boss regeneration");
        heal=new EntityRegainHealthEvent(new LivingEntity()); restored.onBossGuardianRegeneration(heal);
        check(!heal.cancelled,"guardian healing ban leaked to an unrelated entity");
        restored.generation++;
        heal=new EntityRegainHealthEvent(restored.boss); restored.onBossGuardianRegeneration(heal);
        check(!heal.cancelled,"old generation healing ban leaked into a new encounter");
        var disabled=new GuardianRespawnRecoveryProbe();
        disabled.spawnTentacle(disabled.boss,disabled.position,false,0,null);
        var small=new ItemDisplay(); disabled.ownedEntities.put(small.id,small);
        disabled.tentacleController.register(disabled.generation,small.id,
                TentacleAnimationPolicy.Kind.SMALL,9,0);
        disabled.tickTentaclesWithoutGuardians(List.of());
        check(disabled.tentacleController.permanentCount()==0
                        && disabled.tentacleController.state(small.id)!=null && disabled.temporaryTicks==1,
                "disabling defensive guardians removed or froze the independent small summon role");
    }
}
''', encoding="utf-8")
    classes = [SOURCES / ("me/copimine/endevent/" + name + ".java") for name in (
        "domain/TentacleGuardianPolicy", "domain/TentacleScalingPolicy",
        "domain/TentacleAnimationPolicy", "domain/BossPhase", "runtime/TentacleController",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                             *map(str, classes), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "GuardianRespawnRecoveryProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
