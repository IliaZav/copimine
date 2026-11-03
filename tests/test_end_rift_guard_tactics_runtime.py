"""Execute the real W6 guard adapters at detached Paper movement/visibility boundaries."""

from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def guard_probe(tmp_path_factory):
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(declaration(source, signature) for signature in (
        "private void tickRitualGuardPost(",
        "private void tickRitualGuardEndermanCombat(",
        "private void beginRitualGuardAbility(",
        "private boolean ritualGuardTargetAllowed(",
        "private boolean isRitualGuardAbilityCastValid(",
    ))
    directory = tmp_path_factory.mktemp("guard-tactics")
    fixture = directory / "GuardTacticsProbe.java"
    fixture.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RitualGuardAggroPolicy;
import me.copimine.endevent.domain.RitualGuardAbilityPolicy;
public class GuardTacticsProbe {
    long generation=1, ritualGuardAbilityGeneration=1, eventTickCounter, ritualGuardAbilityResolveAtTick;
    String eventId="fixture";
    UUID ritualGuardAbilityGuard, ritualGuardAbilityTarget;
    RitualGuardAbilityPolicy.Profile ritualGuardAbilityProfile=RitualGuardAbilityPolicy.forEntityType("ENDERMAN");
    Location ritualGuardAbilityOrigin=new Location(0,0), ritualGuardAbilityImpact=new Location(2,0);
    static final long WAVE_PATH_REQUEST_INTERVAL_MILLIS=1000;
    static final String EVENT_KIND_RITUAL_GUARD="RITUAL_GUARD";
    Map<UUID,Long> nextWavePathRequestMillis=new HashMap<>(), ritualCasterAlertUntil=new HashMap<>(),
        wavePlayerAggroUntil=new HashMap<>();
    Map<UUID,UUID> ritualGuardPreferredTargets=new HashMap<>();
    Map<UUID,Long> ritualGuardEndermanMeleeBurstUntilTick=new HashMap<>(),
        ritualGuardEndermanMeleeCooldownUntilTick=new HashMap<>();
    Map<UUID,UUID> ritualGuardCasters=new HashMap<>();
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    enum EntityType { ZOMBIE, SPIDER, ENDERMAN }
    enum Sound { BLOCK_BEACON_POWER_SELECT, BLOCK_AMETHYST_BLOCK_CHIME }
    enum SoundCategory { HOSTILE }
    static class World { void playSound(Location at,Sound sound,SoundCategory category,float volume,float pitch){} }
    static final World WORLD=new World();
    static class Vector {
        double x,z; Vector(){ } Vector(double x,double z){this.x=x;this.z=z;}
        Vector subtract(Vector v){x-=v.x;z-=v.z;return this;} Vector setY(double y){return this;}
    }
    static class Location {
        double x,y,z; Location(double x,double z){this(x,0,z);} Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);} Vector toVector(){return new Vector(x,z);}
        Location add(Vector v){x+=v.x;z+=v.z;return this;}
        double distanceSquared(Location other){double dx=x-other.x,dy=y-other.y,dz=z-other.z;return dx*dx+dy*dy+dz*dz;}
        double getY(){return y;}
        World getWorld(){return WORLD;}
    }
    static class Entity {
        UUID id=UUID.randomUUID(); Location pos;
        Entity(double x,double z){pos=new Location(x,z);}
        UUID getUniqueId(){return id;} Location getLocation(){return pos;} World getWorld(){return WORLD;}
        Location getEyeLocation(){return pos;}
    }
    static class Player extends Entity {
        boolean online=true, dead; Player(double x,double z){super(x,z);}
        Location getEyeLocation(){return pos;} boolean isOnline(){return online;}
        boolean isDead(){return dead;} double getHealth(){return dead?0:20;}
    }
    static class Path {
        int moves, stops; boolean active, moveAccepted=true;
        void stopPathfinding(){stops++;active=false;}
        boolean hasPath(){return active;}
        boolean moveTo(Location p,double speed){moves++;active=moveAccepted;return moveAccepted;}
        boolean moveTo(Player p,double speed){return moveTo(p.getLocation(),speed);}
    }
    static class Mob extends Entity {
        Player target; Path path=new Path(); boolean ai, aware, los=true; EntityType type=EntityType.ZOMBIE;
        Mob(double x,double z){super(x,z);} Player getTarget(){return target;}
        EntityType getType(){return type;}
        void setTarget(Player p){target=p;} Path getPathfinder(){return path;}
        void setAI(boolean value){ai=value;} void setAware(boolean value){aware=value;}
        void swingMainHand(){}
        void setVelocity(Vector v){ } boolean hasLineOfSight(Player p){return los;}
    }
    static class Skeleton extends Mob { Skeleton(double x,double z){super(x,z);} }
    static class Spider extends Mob { Spider(double x,double z){super(x,z);type=EntityType.SPIDER;} }
    static class Enderman extends Mob { Enderman(double x,double z){super(x,z);type=EntityType.ENDERMAN;} }
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id){return players.get(id);}
    }
    static double horizontalDistanceSquared(Location a,Location b){return (a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z);}
    void faceRitualCaster(Mob mob,Location target){ }
    void renderRitualGuardAbilityTelegraph(){ }
    static class Log { void info(String text){} void warning(String text){} }
    Log getLogger(){return new Log();}
    Player ritualNearestTarget(Location origin,double radius){return null;}
    void maintainSkeletonCombatPosture(Skeleton skeleton,Player target,String kind,long now){ }
    boolean isCurrentRitualGuard(Entity guard){return ritualGuardCasters.containsKey(guard.id);}
    boolean ritualTargetAllowed(Entity guard,Player player){return player.online&&!player.dead;}
    boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id);}
    METHODS
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var f=new GuardTacticsProbe(); var guard=new Mob(2.4,0); var caster=new Mob(0,0);
        var target=new Player(2,0); f.ritualGuardCasters.put(guard.id,caster.id);
        f.ownedEntities.put(guard.id,guard);f.ownedEntities.put(caster.id,caster);
        switch(args[0]){
            case "leash" -> {
                // W6-013: there is no leash to the mage. An alarmed caster wakes
                // the guard and it accepts any legal participant, however far
                // from its caster that participant stands.
                f.ritualCasterAlertUntil.put(caster.id,Long.MAX_VALUE);
                target.pos=new Location(15,0);
                check(f.ritualGuardTargetAllowed(guard,target),
                    "an alarmed caster wakes its guards for any legal participant, with no caster leash");
                target.pos=new Location(40,0);
                check(f.ritualGuardTargetAllowed(guard,target),
                    "distance from the mage never makes a legal target ineligible");
            }
            case "hysteresis" -> {
                guard.target=target; target.pos=new Location(8,0);
                check(f.ritualGuardTargetAllowed(guard,target),"engaged guard must retain a target in the wider hysteresis band");
                target.pos=new Location(10.01,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"unprovoked interception ends outside the ten-block holding radius");
                guard.target=null; target.pos=new Location(5.9,0);
                check(f.ritualGuardTargetAllowed(guard,target),"an idle guard wakes inside the six-block threat radius");
                target.pos=new Location(6.1,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"an idle guard does not chase beyond the inner radius");
            }
            case "chase" -> {
                // A guard with a target pursues it instead of holding a post.
                guard.pos=new Location(7,0);
                // Inside the ten-block engaged band, so the guard legitimately
                // holds this target and must pursue it.
                guard.target=target; target.pos=new Location(8,0);
                f.tickRitualGuardPost(guard,caster,caster.pos,target,0);
                check(guard.target==target&&guard.ai&&guard.aware,
                    "a guard with a legal target stays engaged and keeps its AI on");
                check(guard.path.moves==1,"a guard with a target pathfinds toward it once");
                f.tickRitualGuardPost(guard,caster,caster.pos,target,100);
                check(guard.path.moves==1,"the pursuit path is not re-requested on every tick");
                f.tickRitualGuardPost(guard,caster,caster.pos,target,1000);
                check(guard.path.moves==2,"the pursuit path is refreshed after the request interval");
            }
            case "idle" -> {
                // No target: the guard still stands with its AI on, so it keeps
                // fighting whatever legitimately enters range.
                f.tickRitualGuardPost(guard,caster,caster.pos,null,0);
                check(guard.target==null,"an idle guard has no target");
                check(guard.ai&&guard.aware,
                    "an idle guard keeps its AI and awareness on instead of freezing on a post");
                check(guard.path.moves==0,"an idle guard does not march back to a duty position");
            }
            case "lost-target" -> {
                // Losing the target must not start a return-to-post route.
                guard.target=target; guard.path.active=true;
                f.tickRitualGuardPost(guard,caster,caster.pos,null,250);
                check(guard.target==null,"a guard with no legal target clears it");
                check(guard.ai&&guard.aware,"a guard that lost its target stays awake and ready");
                check(guard.path.moves==0,"losing a target never launches a home-path route");
            }
            case "visibility" -> {
                f.ritualGuardAbilityGuard=guard.id;f.ritualGuardAbilityTarget=target.id;
                Bukkit.players.put(target.id,target);
                check(f.isRitualGuardAbilityCastValid(),"visible nearby target retains its committed cast");
                guard.los=false;
                check(!f.isRitualGuardAbilityCastValid(),"guard must cancel its committed cast when the target takes cover");
                guard.los=true;f.generation=2;
                check(!f.isRitualGuardAbilityCastValid(),"stale generation must cancel its committed cast");
            }
            case "cast-retention" -> {
                target.pos=new Location(4.5,0); guard.target=target;
                Bukkit.players.put(target.id,target);
                check(f.ritualGuardTargetAllowed(guard,target),"acquired target remains eligible in the six-block retention band");
                f.beginRitualGuardAbility(guard,target,RitualGuardAbilityPolicy.forEntityType("ENDERMAN"));
                check(guard.ai&&guard.aware,
                    "a casting guard keeps fighting: the telegraph never freezes it");
                check(f.isRitualGuardAbilityCastValid(),"committed target remains valid after begin");
                target.pos=new Location(10.01,0);
                check(!f.isRitualGuardAbilityCastValid(),"committed target still expires beyond the ten-block hysteresis boundary");
            }
            case "spider-support" -> {
                var spider=new Spider(0,0); var postCaster=new Mob(0,0);
                f.ritualGuardCasters.put(spider.id,postCaster.id);
                f.ownedEntities.put(spider.id,spider);f.ownedEntities.put(postCaster.id,postCaster);
                var distant=new Player(5,0);
                f.tickRitualGuardPost(spider,postCaster,postCaster.pos,distant,0);
                check(spider.target==distant,"a spider guard keeps a melee target beyond bite range");
                check(spider.ai&&spider.aware,
                    "a spider guard chases instead of freezing outside bite range");
                check(spider.path.moves==1,"a spider guard pathfinds toward a distant target");
                var closeSpider=new Spider(0,0); var closeTarget=new Player(1.9,0);
                f.ritualGuardCasters.put(closeSpider.id,postCaster.id);
                f.ownedEntities.put(closeSpider.id,closeSpider);
                f.tickRitualGuardPost(closeSpider,postCaster,postCaster.pos,closeTarget,0);
                check(closeSpider.target==closeTarget&&closeSpider.ai&&closeSpider.aware,
                    "spider keeps native melee when already inside bite range");
            }
            case "enderman-burst" -> {
                var enderman=new Enderman(0,0); var ritualCaster=new Mob(0,0); var closeTarget=new Player(2,0);
                f.ritualGuardCasters.put(enderman.id,ritualCaster.id);
                f.ownedEntities.put(enderman.id,enderman);f.ownedEntities.put(ritualCaster.id,ritualCaster);
                f.eventTickCounter=100;
                check(f.ritualGuardTargetAllowed(enderman,closeTarget)
                        && closeTarget.getWorld().equals(enderman.getWorld())
                        && RitualGuardAbilityPolicy.mayBeginEndermanMelee(2.0D,100L,0L),
                    "test preconditions expose close, eligible, ready melee target allowed="
                    +f.ritualGuardTargetAllowed(enderman,closeTarget)+" worlds="
                    +closeTarget.getWorld().equals(enderman.getWorld())+" policy="
                    +RitualGuardAbilityPolicy.mayBeginEndermanMelee(2.0D,100L,0L));
                f.tickRitualGuardEndermanCombat(enderman,closeTarget,100L);
                check(enderman.target==closeTarget&&enderman.ai&&enderman.aware,
                    "Enderman performs a native melee burst at close range target="+enderman.target
                    +" ai="+enderman.ai+" end="+f.ritualGuardEndermanMeleeBurstUntilTick.get(enderman.id)
                    +" cooldown="+f.ritualGuardEndermanMeleeCooldownUntilTick.get(enderman.id));
                check(f.ritualGuardEndermanMeleeBurstUntilTick.get(enderman.id)==140L
                        && f.ritualGuardEndermanMeleeCooldownUntilTick.get(enderman.id)==220L,
                    "Enderman melee burst and next-burst cooldown are bounded in server ticks");
                f.eventTickCounter=120;f.tickRitualGuardEndermanCombat(enderman,closeTarget,120L);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman remains in melee through the second pinned vanilla attack window");
                f.eventTickCounter=139;f.tickRitualGuardEndermanCombat(enderman,closeTarget,139L);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman does not end its two-attack opportunity before the burst deadline");
                f.eventTickCounter=140;f.tickRitualGuardEndermanCombat(enderman,closeTarget,140L);
                check(enderman.target==closeTarget&&enderman.ai&&enderman.aware,
                    "Enderman keeps fighting after the burst instead of freezing on a post");
                f.eventTickCounter=219;f.tickRitualGuardEndermanCombat(enderman,closeTarget,219L);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman still pursues during its burst cooldown");
                f.eventTickCounter=220;f.tickRitualGuardEndermanCombat(enderman,closeTarget,220L);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman may begin the next melee burst when cooldown expires");
            }
        }        System.out.println("GuardTacticsProbe OK "+args[0]);
    }
}
'''.replace("METHODS", methods), encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualGuardAggroPolicy.java"
    ability_policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualGuardAbilityPolicy.java"
    # Guard slots are bounded by the roster policy, so it is a compile-time
    # dependency of the ability policy and must be compiled alongside it. The
    # scaling policy in turn reads the Wave 6 roster and the shared pressure
    # budget, which pulls in the wave mechanics and carrier contracts.
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    transitive = [domain / (name + ".java") for name in (
        "RitualSphereScalingPolicy", "WaveSixRosterPolicy", "MageSignaturePolicy",
        "RitualCasterTacticsPolicy", "WaveMechanicsPolicy", "RiftCarrierPolicy",
        "PressureBudgetController",
    )]
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory),
                               str(policy), str(ability_policy), *map(str, transitive), str(fixture)],
                              capture_output=True, text=True, timeout=45)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["leash", "hysteresis", "chase", "idle", "lost-target", "visibility", "cast-retention", "spider-support", "enderman-burst"])
def test_actual_guard_tactics(guard_probe, scenario):
    result = subprocess.run(["java", "-cp", str(guard_probe), "GuardTacticsProbe", scenario],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr


def test_guards_are_null_safe_and_never_teleported_back_to_a_post():
    """W6-013: no post system, no leash, no return-to-mage timer."""
    source = SOURCE.read_text(encoding="utf-8")
    groups = declaration(source, "private void tickRitualGuardGroups(")
    assert "teleportCombatEntity(guard, safe)" not in groups
    post = declaration(source, "private void tickRitualGuardPost(")
    # A guard outside the arena simply drops its target; nothing marches it home.
    assert "teleportCombatEntity" not in post
    assert "ritualGuardReturnPath" not in post


def test_guard_combat_policy_exposes_no_post_or_leash_predicates():
    """The retired post/leash vocabulary must not come back on the policy."""
    policy = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
              / "domain" / "RitualGuardAggroPolicy.java").read_text(encoding="utf-8")
    for retired in ("withinLeash", "postOffset", "mayRequestReturnPath",
                    "nextReturnPathAttempt", "returnPathRetryDelayMillis", "Stance"):
        assert retired not in policy, f"{retired} must not survive the post system removal"
    # Placement is a one-off spawn ring, not a duty position.
    assert "spawnRingOffset" in policy


def test_guard_spider_retains_its_separate_web_target_assignment():
    source = SOURCE.read_text(encoding="utf-8")
    groups = declaration(source, "private void tickRitualGuardGroups(")
    abilities = declaration(source, "private void tickRitualGuardAbilities(")
    assert "target = ritualNearestGuardTarget(guard, caster, now)" in groups
    assert "tickRitualGuardPost(guard, caster, core, target, now)" in groups
    assert "tickRitualGuardAbilities(now)" in groups
    assert "ritualNearestGuardTarget(guard, owner instanceof Mob caster ? caster : guard, now)" in abilities


def test_ranged_guard_targets_are_far_first_sticky_visible_and_range_bounded():
    source = SOURCE.read_text(encoding="utf-8")
    target_picker = declaration(source, "private Player ritualNearestGuardTarget(")
    target_validity = declaration(source, "private boolean ritualGuardTargetAllowed(")
    ability_tick = declaration(source, "private void tickRitualGuardAbilities(")

    assert "ritualGuardPreferredTargets.get(guardId)" in target_picker
    assert "ritualGuardPreferredTargets.put(guardId," in target_picker
    assert "RitualGuardAggroPolicy.preferredTargetIndex" in target_picker
    assert "guard.hasLineOfSight(player)" in target_picker
    assert "maximumRangeForGuardRole" in target_picker
    assert "ritualGuardPreferredTargets" in target_validity
    assert "RitualGuardAbilityPolicy.forGuardActionAtRange(" in ability_tick
    assert "roleSlot, actionOrdinal, distance)" in ability_tick
    assert "mayBeginSpiderWeb(distance)" in ability_tick


def test_wave6_zombie_tanks_keep_their_melee_knockback_profile_after_recovery():
    source = SOURCE.read_text(encoding="utf-8")
    configure = declaration(source, "private void configureRitualGuardCombatAttributes(")
    # SUPERSEDED (audit W6 error 1, spec W6-002): guards spawn per stage from
    # spawnWaveSixStage(), not up-front from the objective start.
    spawn = declaration(source, "private boolean spawnWaveSixStage(")
    reindex = declaration(source, "private void reindexRitualEntity(")

    assert "Attribute.GENERIC_KNOCKBACK_RESISTANCE" in configure
    assert "Attribute.GENERIC_ATTACK_KNOCKBACK" in configure
    assert "RitualGuardStatsPolicy.ZOMBIE_KNOCKBACK_RESISTANCE" in configure
    assert "RitualGuardStatsPolicy.ZOMBIE_ATTACK_KNOCKBACK" in configure
    assert "configureRitualGuardCombatAttributes(living)" in spawn
    assert "configureRitualGuardCombatAttributes(living)" in reindex


def test_wave6_roster_recovery_rejects_a_saved_entity_with_the_wrong_species():
    source = SOURCE.read_text(encoding="utf-8")
    recovery = declaration(source, "private boolean ensureRitualCasterRosterAvailableForRecovery(")

    assert "entityTypeForGuardRole(roleSlot)" in recovery
    assert "isGuardEntityTypeCompatible" in recovery
    assert "giantReplacement != isRiftGiant(guard)" in recovery


def test_guard_giants_share_one_persisted_wave_summon_budget():
    source = SOURCE.read_text(encoding="utf-8")
    summon = declaration(source, "private boolean tickRiftGiantSummon(")
    assert "sharedRiftGiantSummonsUsed(wave)" in summon
    assert "storeSharedRiftGiantSummonsUsed(giant, wave, totalUsed)" in summon

    count = declaration(source, "private int sharedRiftGiantSummonsUsed(")
    assert "ownedEntities.values()" in count
    assert "isRiftGiant(giant)" in count
    assert "readInt(giant, keyWave, -1) != wave" in count
    assert "RiftGiantPolicy.sharedSummonsUsed(counters)" in count

    store = declaration(source, "private void storeSharedRiftGiantSummonsUsed(")
    assert "ownedEntities.values()" in store
    assert ".getPersistentDataContainer().set(keyRiftGiantSummonsUsed" in store
