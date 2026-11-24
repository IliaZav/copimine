"""Execute the production hover/fall adapter, rather than matching its strings."""
from pathlib import Path
import subprocess

import pytest

from tests.test_wave_skeleton_protection_runtime import declaration

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


@pytest.fixture(scope="module")
def probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("cocoon-fall-adapter")
    source = (DOMAIN.parent / "CopiMineEndEvent.java").read_text("utf-8")
    methods = "\n".join(declaration(source, signature) for signature in (
        "private void beginCocoonFall(", "private boolean tickCocoonFall(",
        "private boolean pinBossToFinalSealCore(",
        "private void syncCocoonFallAnimation(", "private long currentBossAnimationCueMillis(",
        "private boolean tickBossCocoonPresentation(",
        "private void tickWaveMobContainment(",
    ))
    fixture = directory / "CocoonFallAdapterProbe.java"
    fixture.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
public class CocoonFallAdapterProbe {
    long generation=4, cocoonFallGeneration=Long.MIN_VALUE, nextTargetMillis;
    long bossVisualCueSequence,eventTickCounter,bossHitboxAnimationStartedMillis;
    long cocoonFallLastServerTick=Long.MIN_VALUE;
    Object riftRam;boolean localTextureShowcase,bootstrapped=true;
    String bossHitboxAnimationId="IDLE_BREATH",bossBindingInstanceId="instance";
    UUID bossUuid=new UUID(0,1);static final long BOSS_PHASE_VISUAL_TRANSITION_MILLIS=800;
    List<String> animations=new ArrayList<>();
    String eventId="test", recoveryReason="";
    boolean cocoonFallStarted, cocoonLandingPlayed, rejectMove, redirectMove, oldCast=true, oldRing=true, oldProjectiles=true;
    int cocoonFallElapsedTicks=-1, cocoonFallMoveFailures;
    CocoonFallPolicy.Stage cocoonFallStage=CocoonFallPolicy.Stage.COCOONED;
    BossPhase bossPhase=BossPhase.LAST_SEAL;
    LivingEntity boss=new Mob(); double floorY=69; int impacts, moves, pins;
    enum EventPhase { RECOVERY_REQUIRED }
    static class Vector { Vector(double x,double y,double z){} }
    static class World {UUID getUID(){return new UUID(0,3);}}
    static class Bukkit {static int tick;static int getCurrentTick(){return tick;}
        // Held grab targets are resolved through Bukkit, exactly like production.
        static Player getPlayer(UUID id){return null;}}
    final static World WORLD=new World();
    static class Location {
        double x,y,z; Location(double y){this.y=y;}
        World getWorld(){return WORLD;} double getX(){return x;} double getY(){return y;}
        double getZ(){return z;} void setY(double y){this.y=y;}
        Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
        public Location clone(){Location n=new Location(y);n.x=x;n.z=z;return n;}
    }
    static class LivingEntity {
        double y=71.5; boolean gravity; boolean valid=true;
        boolean isDead(){return false;} boolean isValid(){return valid;}
        UUID getUniqueId(){return new UUID(0,1);} World getWorld(){return WORLD;}
        double getX(){return 0;} double getY(){return y;} double getZ(){return 0;}
        Location getLocation(){return new Location(y);}
        boolean teleport(Location loc){throw new AssertionError("unpermitted teleport");}
        void setGravity(boolean b){gravity=b;} void setVelocity(Vector v){}
    }
    static class Player extends LivingEntity {
        boolean isOnline(){return true;}
        // The break zeroes the pre-release fall distance of anyone still held,
        // so the anchor lease is measured from the release moment.
        void setFallDistance(float d){}
    }
    // The restrained tentacle index: the break cancels grabs and temporary
    // eruptions, and the probe starts it empty so only the walk itself runs.
    static class TentacleAnimationPolicy { enum Kind { PERMANENT, TEMPORARY, UNDER_PLAYER, SMALL } }
    static class TentacleController {
        record VisualState(UUID entityId,TentacleAnimationPolicy.Kind kind,int slot,UUID target){}
        final Map<UUID,VisualState> states=new LinkedHashMap<>();
        Map<UUID,VisualState> states(){return states;}
    }
    final TentacleController tentacleController=new TentacleController();
    // The anchor lease protects a released player from the fall's own damage.
    record Wave6AnchorFallLease(UUID worldId,long untilMillis){}
    final Map<UUID,Wave6AnchorFallLease> wave6AnchorFallLeases=new HashMap<>();
    // Cooldowns the fury entry must clear so grounded combat starts immediately.
    long nextSpellMillis,bossSpellRetryAtMillis,nextBossCloseStrikeMillis,lastBossPathRequestMillis;
    boolean isPlayerUnderTentacleControl(Player p){return false;}
    boolean isActiveBossParticipant(Player p){return true;}
    void removeTentacle(UUID id,String reason){tentacleController.states().remove(id);}
    static class Pathfinder { void stopPathfinding(){} }
    static class Mob extends LivingEntity {
        boolean ai; boolean hasAI(){return ai;} void setAI(boolean b){ai=b;}
        void setAware(boolean b){} void setTarget(Object p){}
        Pathfinder getPathfinder(){return new Pathfinder();}
    }
    static class Logger { void info(String s){} void severe(String s){} void warning(String s){} }
    Logger getLogger(){return new Logger();} LivingEntity liveBoss(){return boss;}
    boolean isOfficialBoss(LivingEntity b){return true;} boolean ensureFinalSealBarriersIntact(){return true;}
    boolean tentacleVulnerabilityOpen(){return cocoonFallStarted;}
    Location coreBlockTopLocation(){return new Location(floorY);}
    Location finalSealCoreAnchorLocation(){pins++;return new Location(floorY+2.5);}
    String locationText(Location l){return "location";}
    void tickKaguneRingCast(LivingEntity b){} void tickEchoAttackReplicas(LivingEntity b){}
    void renderCocoonLanding(Location l){impacts++;}
    // 9G.07: the authored shell split plays before the descent. The probe
    // stubs it so the fall clock assertions stay about the fall, not the VFX.
    void playCocoonShatter(LivingEntity b,Location floor){}
    boolean teleportCombatEntity(LivingEntity b,Location l){moves++;if(rejectMove)return false;b.y=l.y+(redirectMove?1.0:0.0);return true;}
    void forcePhase(EventPhase p,String reason){recoveryReason=reason;}
    boolean saveStateSync(){return true;}
    void saveStateAsync(){}
    void cancelBossCastForPhaseChange(LivingEntity boss){oldCast=false;}
    void clearKaguneRingCast(String reason){oldRing=false;}
    void clearActiveRiftProjectiles(){oldProjectiles=false;}
    // The cocoon break also drains the separate rift-fireball registry and the
    // scheduled LAST_SEAL visual task, then restores phase music on fury entry.
    // The probe starts with an empty registry, so these stubs exist only to
    // prove the break path compiles and runs against the real production body.
    static final class RiftFireballRuntimeState {
        boolean isBossSpell(){return true;}
        long generation(){return 4L;}
        UUID entityId(){return new UUID(0,2);}
    }
    final Map<UUID,RiftFireballRuntimeState> activeRiftFireballs=new LinkedHashMap<>();
    void cleanupRiftFireball(UUID id,String reason){}
    static final class LastSealVisualTask { void cancel(){} boolean isCancelled(){return false;} }
    LastSealVisualTask currentLastSealVisualTask;
    String musicForPhase(){return "melee-fury";}
    void playEventMusic(String track){}
    void clearWorldVfxBeamsByPrefix(String prefix){}
    boolean isBossCombatRuntimeActive(LivingEntity b){return b!=null&&b.isValid();}
    boolean isEnabled(){return true;}boolean isCombatPhase(){return false;}
    boolean hasLiveTestWaveEntities(){return false;}
    void tickRiftRam(){}void enforceWaveMobContainment(){}
    void updateBossHitbox(LivingEntity b){}
    void startBossHitboxAnimation(String id){bossHitboxAnimationId=id;}
    List<Player> eventAudience(){return List.of(new Player());}
    void sendClientPacket(Player p,String type,String instance,long duration,String uuid,String phase){
        animations.add(phase);
        long max=phase.endsWith("COCOON_FALL")?2000:phase.endsWith("COCOON_LAND")?1000:800;
        if(duration<0||duration>max)throw new AssertionError("invalid remaining duration");
    }
''' + methods + r'''
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void near(double a,double b){check(Math.abs(a-b)<1e-8,"height "+a+" != "+b);}
    // The authored descent: release from suspension on an eased curve
    // (heightDuringFall uses 1 - progress^2) and land exactly on the floor.
    static double authoredHeight(int elapsed){
        double progress=Math.min(1.0,Math.max(0.0,elapsed/40.0));
        return 2.5*(1.0-progress*progress);
    }
    public static void main(String[] args){
        CocoonFallAdapterProbe p=new CocoonFallAdapterProbe();
        p.floorY=Double.parseDouble(args[1]);p.boss.y=p.floorY+2.5;
        p.beginCocoonFall(p.boss);
        if(args[0].equals("clock")) {
            for(int tick=0;tick<=60;tick++){
                Bukkit.tick=tick;
                if(tick%5==0)p.pinBossToFinalSealCore(p.boss);
                p.tickWaveMobContainment();
                check(p.cocoonFallElapsedTicks==tick+1,"5-tick strategy and 1-tick motion double-stepped/stalled");
                near(p.boss.y,p.floorY+authoredHeight(tick));
            }
            check(p.cocoonFallStage==CocoonFallPolicy.Stage.MELEE_FURY&&((Mob)p.boss).ai,"fury did not resume at 60 server ticks");
            check(p.impacts==1,"landing duplicated across the two tick owners");return;
        }
        if(args[0].equals("cast")) {
            check(!p.oldCast && !p.oldRing && !p.oldProjectiles,"cocoon spell survived the fall");
            return;
        }
        if(args[0].equals("cancel") || args[0].equals("redirect")){
            p.rejectMove=args[0].equals("cancel");p.redirectMove=args[0].equals("redirect"); p.pinBossToFinalSealCore(p.boss);
            check(p.cocoonFallElapsedTicks==0,"cancelled move advanced clock");
            check(p.impacts==0,"cancelled move played impact");
            for(int i=0;i<4;i++){Bukkit.tick++;p.pinBossToFinalSealCore(p.boss);}
            check(!p.recoveryReason.isEmpty(),"rejected moves must have bounded recovery");
            return;
        }
        for(int tick=0;tick<=40;tick++){
            p.eventTickCounter=tick;
            Bukkit.tick=tick;
            check(p.pinBossToFinalSealCore(p.boss),"descent must own movement at "+tick);
            near(p.boss.y,p.floorY+authoredHeight(tick));
            check(!((Mob)p.boss).ai,"AI resumed during descent/landing");
        }
        check(p.impacts==1,"exactly one landing cue");
        for(int tick=41;tick<60;tick++){
            p.eventTickCounter=tick;
            Bukkit.tick=tick;
            check(p.pinBossToFinalSealCore(p.boss),"landing lost ownership at "+tick);
            check(p.cocoonFallStage==CocoonFallPolicy.Stage.LANDING,"landing shortened");
        }
        p.eventTickCounter=60;
        Bukkit.tick=60;
        check(!p.pinBossToFinalSealCore(p.boss),"fury must release movement at tick 60");
        check(p.animations.stream().filter(a->a.endsWith("COCOON_FALL")).count()==8,"fall pose was absent or spammed");
        check(p.animations.stream().filter(a->a.endsWith("COCOON_LAND")).count()==4,"landing pose was absent or replayed");
        check(p.bossHitboxAnimationId.equals("IDLE_BREATH"),"fury retained the landing pose");
        check(p.bossVisualCueSequence==1,"old cue resets were not invalidated once");
        check(p.cocoonFallStage==CocoonFallPolicy.Stage.MELEE_FURY,"no fury");
        check(((Mob)p.boss).ai && p.boss.gravity,"fury did not resume AI/gravity");
        p.beginCocoonFall(p.boss); p.boss.y=p.floorY+0.5;
        check(!p.pinBossToFinalSealCore(p.boss),"fury repinned on duplicate begin");
        near(p.boss.y,p.floorY+0.5);check(p.impacts==1 && p.pins==0,"fall replayed/rehovered");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory),
        *map(str, (DOMAIN / (name + ".java") for name in (
            "CocoonFallPolicy", "BossPhase", "BossFinalSealAnchorPolicy", "EndRiftAiPolicy", "BossAnimationId"))),
        str(ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BossAnimationPlayback.java"),
        str(fixture)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("floor", [69, 101, -20])
def test_real_fall_landing_and_permanent_fury(probe, floor):
    result = subprocess.run(["java", "-cp", str(probe), "CocoonFallAdapterProbe", "fall", str(floor)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("mode", ["cancel", "redirect"])
def test_rejected_relocation_does_not_advance_fall(probe, mode):
    result = subprocess.run(["java", "-cp", str(probe), "CocoonFallAdapterProbe", mode, "69"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_cocoon_break_cancels_existing_casts_and_projectiles(probe):
    result = subprocess.run(["java", "-cp", str(probe), "CocoonFallAdapterProbe", "cast", "69"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_real_five_tick_strategy_and_per_tick_motion_share_one_clock(probe):
    result = subprocess.run(["java", "-cp", str(probe), "CocoonFallAdapterProbe", "clock", "69"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
