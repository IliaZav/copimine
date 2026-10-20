"""Execute official named-trial dispatch with owner-bound Echo enabled."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_selected_personal_echo_starts_without_entering_deferred_recovery(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = extract(source, "boolean startRealitySplitTrials(World world, Location core)")
    for signature in (
        "boolean startNamedRealitySplitTrials(World world, Location core)",
        "boolean namedRealitySplitTrialImplemented(RealitySplitTrialController.NamedTrial trial)",
    ):
        if "    private " + signature in source:
            adapters += extract(source, signature)
    probe = tmp_path / "NamedWave7StartProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class NamedWave7StartProbe {
    String eventId="00000000-0000-0000-0000-000000000071",recoveryReason="";
    long generation=71;
    boolean recovery,echoReady=true;
    boolean namedWave7InitialActorCreation;
    boolean namedWave7RuntimeRestorePending;
    int spawned;
    RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
    RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
    static class World {} static class Location {}
    enum EventPhase {RECOVERY_REQUIRED}
    Logger getLogger(){Logger value=Logger.getAnonymousLogger();value.setLevel(Level.OFF);return value;}
    UUID parseUuidOrNull(String raw){return UUID.fromString(raw);}
    boolean isRealitySplitTrialRuntimeActive(){return !recovery;}
    boolean isOfficialCurrentAttempt(){return true;}
    boolean shouldRetainWave7ActorChunksLoaded(){return false;}
    void ensureOverlayChunksLoaded(World world){}
    void forcePhase(EventPhase phase,String reason){recovery=true;}
    void enterNamedRealitySplitRecovery(String reason){recoveryReason=reason;recovery=true;}
    void clearRealitySplitTrialEntities(String reason){}
    void finishRealitySplitTrial(int room,String source){}
    void finishNamedRealitySplitTrial(int room,String source){}
    boolean preflightNamedEchoDuelRooms(){return echoReady;}
    boolean ensureRealitySplitTrialRoom(World world,Location core,RealitySplitTrialController.TrialState state){
        throw new AssertionError("new named trial entered a historical actor adapter");
    }
    boolean ensureNamedRealitySplitTrialRoom(World world,Location core,RealitySplitTrialController.NamedTrialState state){spawned++;return true;}
''' + adapters + '''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var probe=new NamedWave7StartProbe();
        probe.realitySplitChamberController.begin(71,List.of(new UUID(0,1),new UUID(0,2)));
        probe.realitySplitTrialController.beginNamed(UUID.fromString(probe.eventId),71,
                probe.realitySplitChamberController.assignment(),List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,RealitySplitTrialController.NamedTrial.PERSONAL_ECHO));
        var before=probe.realitySplitTrialController.namedSnapshot();
        require(probe.startRealitySplitTrials(new World(),new Location()),"implemented Echo actor must pass named-trial startup");
        require(!probe.recovery,"supported Echo selection must not enter recovery");
        require(probe.spawned==2,"Warden and personal Echo each create their assigned room actor");
        require(probe.realitySplitTrialController.namedSnapshot().equals(before),"failure cannot erase or reroll selected layout");
        require(!probe.realitySplitTrialController.allComplete(71),"missing actors cannot complete the layout");
    }
}
''', encoding="utf-8")
    paths = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java",
        "domain/Wave7AdmissionPolicy.java",
        "domain/Wave7EchoDuelCellPolicy.java", "domain/Wave7NamedTrialActorPolicy.java",
        "domain/Wave7RoomLayoutPolicy.java",
        "domain/wave7/EchoBehaviorStrategy.java",
        "domain/wave7/EchoLoadoutState.java", "runtime/RealitySplitEchoDuelState.java",
        "runtime/RealitySplitChamberController.java", "runtime/RealitySplitTrialController.java",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "NamedWave7StartProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_official_named_route_starts_generated_and_restored_layouts_for_every_roster_size(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = "\n".join(extract(source, signature) for signature in (
        "boolean startRealitySplitTrials(World world, Location core)",
        "boolean startNamedRealitySplitTrials(World world, Location core)",
        "boolean namedRealitySplitTrialImplemented(RealitySplitTrialController.NamedTrial trial)",
    ))
    probe = tmp_path / "NamedWave7LayoutRouteProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import java.util.random.RandomGenerator;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class NamedWave7LayoutRouteProbe {
    static final UUID EVENT=UUID.fromString("00000000-0000-0000-0000-000000000071");
    enum EventPhase { RECOVERY_REQUIRED }
    String eventId=EVENT.toString(),recoveryReason="";
    long generation=71;
    boolean recovery,echoReady=true;
    boolean namedWave7InitialActorCreation,namedWave7RuntimeRestorePending;
    int spawned;
    Map<RealitySplitTrialController.NamedTrial,Integer> spawnedByTrial=new EnumMap<>(RealitySplitTrialController.NamedTrial.class);
    RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
    RealitySplitTrialController realitySplitTrialController;
    static class World {} static class Location {}
    NamedWave7LayoutRouteProbe(RealitySplitTrialController controller,List<UUID> owners){
        realitySplitTrialController=controller;realitySplitChamberController.begin(generation,owners);
    }
    Logger getLogger(){return Logger.getAnonymousLogger();}
    UUID parseUuidOrNull(String raw){try{return UUID.fromString(raw);}catch(RuntimeException ignored){return null;}}
    boolean isRealitySplitTrialRuntimeActive(){return !recovery;}
    boolean isOfficialCurrentAttempt(){return true;}
    boolean shouldRetainWave7ActorChunksLoaded(){return false;}
    void ensureOverlayChunksLoaded(World world){}
    void forcePhase(EventPhase phase,String reason){recovery=true;recoveryReason=reason;}
    void enterNamedRealitySplitRecovery(String reason){recovery=true;recoveryReason=reason;}
    void clearRealitySplitTrialEntities(String reason){}
    void finishRealitySplitTrial(int room,String source){throw new AssertionError("named route entered historical completion");}
    void finishNamedRealitySplitTrial(int room,String source){}
    boolean preflightNamedEchoDuelRooms(){return echoReady;}
    boolean ensureRealitySplitTrialRoom(World world,Location core,RealitySplitTrialController.TrialState state){
        throw new AssertionError("named route entered a historical actor adapter");
    }
    boolean ensureNamedRealitySplitTrialRoom(World world,Location core,RealitySplitTrialController.NamedTrialState state){
        spawned++;spawnedByTrial.merge(state.trial(),1,Integer::sum);return true;
    }
''' + adapters + '''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static List<UUID> owners(int count){return java.util.stream.IntStream.range(1,count+1).mapToObj(i->new UUID(0,i)).toList();}
    static Set<Set<RealitySplitTrialController.NamedTrial>> combinations(int size){
        Set<Set<RealitySplitTrialController.NamedTrial>> result=new HashSet<>();
        var trials=List.of(RealitySplitTrialController.NamedTrial.values());
        for(int mask=0;mask<(1<<trials.size());mask++){
            Set<RealitySplitTrialController.NamedTrial> selected=new LinkedHashSet<>();
            for(int bit=0;bit<trials.size();bit++)if((mask&(1<<bit))!=0)selected.add(trials.get(bit));
            if(selected.size()==size)result.add(Set.copyOf(selected));
        }
        return result;
    }
    static void run(int playerCount,long seed,boolean restore){
        var participants=owners(playerCount);
        var assignment=ChamberIsolationPolicy.assign(participants);
        var controller=new RealitySplitTrialController();
        controller.beginNamed(EVENT,71,assignment,new Random(seed));
        var generated=controller.namedSnapshot();
        require(generated.trials().size()==(playerCount<=3?playerCount:4),"generated room count for players="+playerCount);
        require(generated.trials().values().stream().mapToInt(t->t.owners().size()).sum()==playerCount,
                "generated roster is complete for players="+playerCount);
        require(generated.selectedLayout().stream().distinct().count()==generated.selectedLayout().size(),
                "generated roles are distinct for players="+playerCount);
        if(restore){
            var encoded=RealitySplitTrialSnapshot.encodeNamed(generated);
            var restored=RealitySplitTrialSnapshot.decodeNamed(encoded,EVENT,71);
            var replacement=new RealitySplitTrialController();
            replacement.restoreNamed(EVENT,71,assignment,restored);
            require(replacement.namedSnapshot().equals(generated),"restore preserves the selected layout for players="+playerCount);
            controller=replacement;
        }
        var probe=new NamedWave7LayoutRouteProbe(controller,participants);
        require(probe.startRealitySplitTrials(new World(),new Location()),
                "official named route starts "+(restore?"restored":"generated")+" layout players="+playerCount
                +" seed="+seed+" trials="+controller.namedSnapshot().selectedLayout());
        require(!probe.recovery,"implemented generated layout stays out of recovery");
        require(probe.spawned==generated.trials().size(),"every selected room starts without partial admission");
        require(controller.namedSnapshot().equals(generated),"start never erases or rerolls the persisted layout");
        for(var trial:controller.namedSnapshot().selectedLayout())
            require(probe.spawnedByTrial.getOrDefault(trial,0)==1,"selected role dispatches once: "+trial);
    }
    public static void main(String[] args){
        for(int playerCount:List.of(2,3,4,5,6,10,20)){
            Set<Set<RealitySplitTrialController.NamedTrial>> observed=new HashSet<>();
            Map<Set<RealitySplitTrialController.NamedTrial>,Long> representatives=new HashMap<>();
            int targetCount=playerCount<=3?playerCount:4;
            Set<Set<RealitySplitTrialController.NamedTrial>> expected=combinations(targetCount);
            int seeds=10_000;
            for(long seed=0;seed<seeds;seed++){
                var selector=new RealitySplitTrialController();
                var assignment=ChamberIsolationPolicy.assign(owners(playerCount));
                selector.beginNamed(EVENT,71,assignment,new Random(seed));
                Set<RealitySplitTrialController.NamedTrial> selected=Set.copyOf(selector.namedSnapshot().selectedLayout());
                observed.add(selected);
                representatives.putIfAbsent(selected,seed);
                if(observed.equals(expected))break;
            }
            require(observed.equals(expected),
                    "generated selection space is covered for players="+playerCount);
            for(long seed:representatives.values())run(playerCount,seed,false);
            // Every supported roster size also traverses snapshot encode/decode and the same production start route.
            run(playerCount,37,true);
        }
    }
}
''', encoding="utf-8")
    paths = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java",
        "domain/Wave7EchoDuelCellPolicy.java", "domain/Wave7NamedTrialActorPolicy.java",
        "domain/Wave7RoomLayoutPolicy.java",
        "domain/wave7/EchoBehaviorStrategy.java", "domain/wave7/EchoLoadoutState.java",
        "runtime/RealitySplitEchoDuelState.java", "runtime/RealitySplitChamberController.java",
        "runtime/RealitySplitTrialController.java", "runtime/RealitySplitTrialSnapshot.java",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "NamedWave7LayoutRouteProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_named_role_actor_runtime_is_checkpointed_scoped_and_death_authoritative():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    implemented = extract(source, "boolean namedRealitySplitTrialImplemented(RealitySplitTrialController.NamedTrial trial)")
    ensure = extract(source, "boolean ensureNamedRealitySplitTrialRoom(World world, Location core,")
    tick = extract(source, "boolean tickNamedTrialActor(LivingEntity actor,")
    projectile_admission = extract(source, "boolean namedTrialProjectileMayHit(Entity projectile, Player target)")
    death = extract(source, "void handleNamedRealitySplitTrialBossDeath(Entity entity, int chamber, String role)")
    assert "Wave7NamedTrialActorPolicy.hasActorAdapter(trial)" in implemented
    assert "EntityType.valueOf(profile.entityType())" in ensure
    assert "readNamedTrialActorCheckpoint(actor, trial)" in ensure
    assert "if (!namedWave7InitialActorCreation) return false" in ensure
    assert "Wave7NamedTrialActorPolicy.decode" in source
    assert "MARKSMAN_QUICK_SHOT" in source and "MARKSMAN_HEAVY_SHOT" in source
    assert "MARKSMAN_SPREAD_SHOT" in source and "ARCHMAGE_FANGS" in source
    assert "ARCHMAGE_SPEAR" in source and "ARCHMAGE_STEP" in source and "ARCHMAGE_BEAM" in source
    assert "renderNamedTrialTelegraph" in tick and "resolveNamedTrialAttack" in tick
    assert "realitySplitChamberController.allowsMobTarget" in projectile_admission
    assert "isCurrentRealitySplitTrialTarget(actor, target)" in projectile_admission
    assert "readNamedTrialActorCheckpoint(entity, trial)" in death
    assert "completeNamedActor(parseUuidOrNull(eventId), generation" in death


def test_named_actor_profiles_do_not_bind_to_legacy_trial_enum():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    spawn = extract(source, "Entity spawnRealitySplitTrialEntity(World world, Location location, EntityType type,")
    named = extract(source, "boolean ensureNamedRealitySplitTrialRoom(World world, Location core,")
    assert "boolean attachLegacyTrialRuntimeState" in spawn
    assert "if (attachLegacyTrialRuntimeState)" in spawn
    assert "RealitySplitTrialController.Trial.valueOf(role)" in spawn
    assert "trial.chamber(), trial.trial().name(), -1, true, true)" in named
    assert "trial.chamber(), trial.trial().name(), -1, true, false)" in named


def test_wave7_live_boundary_runner_waits_for_actual_objective_start_marker():
    runner = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    assert "Wait-Log -AfterOffset $wave7Offset -Pattern 'WAVE_OBJECTIVE_STARTED.*wave=7\\b'" in runner


def test_wave7_sandbox_uses_the_same_distinct_random_trial_pool_as_official_runs():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    preflight = extract(source, "boolean preflightWave7StartBeforeWorldMutation(World world, Location core,")
    assert "if (sandbox)" not in preflight
    assert "namedWave7TestLayout" not in source
    assert "realitySplitTrialController.beginNamed(eventUuid, generation,\n                realitySplitChamberController.assignment(), java.util.concurrent.ThreadLocalRandom.current())" in preflight
    assert "preflightNamedEchoDuelRooms()" in preflight
    runner = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    assert "layout=\\[([A-Z_, ]+)\\]" in runner
    assert "Groups[1].Value -split ', '" in runner
    assert "$wave7Layout.Count -ne 2" in runner
    assert "@($wave7Layout | Select-Object -Unique).Count -ne 2" in runner
    assert "@($wave7Layout | Where-Object { $_ -notin $validWave7Trials }).Count -gt 0" in runner
    assert "if ($wave7Layout -contains 'WARDEN' -and $damageParts.Count -lt 1)" in runner
    assert "LIVE_WAVE7_PLAYER_DAMAGE_LEDGER_NOT_APPLICABLE" in runner
    assert "layout=\\[WARDEN, MOBILE_MARKSMAN\\]" not in runner


def test_named_death_requires_new_actor_identity_and_selected_role(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = extract(source, "void handleRealitySplitTrialBossDeath(Entity entity)")
    adapters += extract(source, "void handleNamedRealitySplitTrialBossDeath(Entity entity, int chamber, String role)")
    echo_credit = extract(source, "boolean consumeNamedEchoLethalDeath(Entity entity)")
    assert "consumeNamedEchoLethalDeath(entity)" in adapters
    assert "consumeOwnerLethalDeath(pair, living.getLastDamageCause())" in echo_credit
    assert "receiptForConfirmedActorDeath(" in adapters
    assert "chamber, owner, entity.getUniqueId())" in adapters
    assert "completePersonalEcho(receipt)" in adapters
    assert "completePersonalEcho(parseUuidOrNull(eventId), generation, chamber, owner)" not in adapters
    probe = tmp_path / "NamedWave7DeathProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class NamedWave7DeathProbe {
    enum EventPhase { WAVE_7, RECOVERY_REQUIRED }
    String eventId="00000000-0000-0000-0000-000000000071";
    long generation=71;
    EventPhase phase=EventPhase.WAVE_7;
    int finished,saves,recoveries;
    Set<UUID> ownerLethalReceipts=new HashSet<>();
    Map<UUID,Wave7NamedTrialActorPolicy.Checkpoint> actorCheckpoints=new HashMap<>();
    Object keyChamberId=new Object(),keyTrialRole=new Object();
    RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
    Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
    Map<UUID,Object> realitySplitTrialRuntimeStates=new HashMap<>();
    static class NamespacedKey {NamespacedKey(Object plugin,String name){}}
    static class Entity {
        UUID id=UUID.randomUUID(),owner;int room;String role;boolean named;
        Entity(int room,String role,UUID owner,boolean named){this.room=room;this.role=role;this.owner=owner;this.named=named;}
        UUID getUniqueId(){return id;}
    }
    UUID parseUuidOrNull(String raw){try{return UUID.fromString(raw);}catch(RuntimeException ignored){return null;}}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    boolean ownedBySession(Entity actor,String event,long gen){return true;}
    int readInt(Entity actor,Object key,int fallback){return actor.room;}
    String readString(Entity actor,Object key){return key==keyTrialRole?actor.role:actor.owner==null?"":actor.owner.toString();}
    boolean isNamedRealitySplitActor(Entity actor){return actor.named&&realitySplitTrialEntityUuids.contains(actor.id);}
    void finishRealitySplitTrial(int room,String reason){throw new AssertionError("named completion reached historical adapter");}
    void finishNamedRealitySplitTrial(int room,String reason){finished++;}
    boolean saveStateSync(){saves++;return true;}
    boolean openCompletedNamedEchoCellBarriers(int chamber){return true;}
    void enterNamedRealitySplitRecovery(String reason){recoveries++;}
    Wave7NamedTrialActorPolicy.Checkpoint readNamedTrialActorCheckpoint(
            Entity actor,RealitySplitTrialController.NamedTrialState trial){return actorCheckpoints.get(actor.id);}
    void removeNamedEchoRuntime(UUID actorId,boolean sendRemove){}
    boolean consumeNamedEchoLethalDeath(Entity actor){return ownerLethalReceipts.remove(actor.id);}
    Entity actor(int room,String role,UUID owner,boolean named){var actor=new Entity(room,role,owner,named);realitySplitTrialEntityUuids.add(actor.id);return actor;}
''' + adapters + '''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var probe=new NamedWave7DeathProbe();
        var owners=java.util.stream.IntStream.range(1,7).mapToObj(i->new UUID(0,i)).toList();
        var assignment=ChamberIsolationPolicy.assign(owners);
        probe.realitySplitTrialController.beginNamed(UUID.fromString(probe.eventId),71,assignment,List.of(
                RealitySplitTrialController.NamedTrial.WARDEN,RealitySplitTrialController.NamedTrial.PERSONAL_ECHO,
                RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN,RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE));
        probe.handleRealitySplitTrialBossDeath(probe.actor(0,"WARDEN",null,false));
        require(probe.finished==0,"historical Warden death cannot resolve a new named trial");
        probe.handleRealitySplitTrialBossDeath(probe.actor(2,"JUGGERNAUT",null,true));
        require(probe.finished==0,"historical Juggernaut death cannot become a Marksman receipt");
        var warden=probe.actor(0,"WARDEN",null,true);
        probe.handleRealitySplitTrialBossDeath(warden);probe.handleRealitySplitTrialBossDeath(warden);
        require(probe.finished==1,"new registered Warden completes exactly once");
        var echoOwner=assignment.playersIn(1).iterator().next();
        var echo=probe.actor(1,"PERSONAL_ECHO",echoOwner,true);
        probe.handleRealitySplitTrialBossDeath(echo);
        require(probe.finished==1,"an Echo death without an owner lethal-hit receipt cannot complete its room");
        var adaptedTrial=probe.realitySplitTrialController.namedSnapshot().trials().values().stream()
                .filter(trial->Wave7NamedTrialActorPolicy.hasActorAdapter(trial.trial())).findFirst().orElseThrow();
        var adaptedActor=probe.actor(adaptedTrial.chamber(),adaptedTrial.trial().name(),null,true);
        probe.actorCheckpoints.put(adaptedActor.id,new Wave7NamedTrialActorPolicy.Checkpoint(
                UUID.fromString(probe.eventId),probe.generation,adaptedTrial.chamber(),adaptedTrial.trial(),
                new UUID(0,999),0L,Wave7NamedTrialActorPolicy.Attack.NONE,0L,0L,null,false));
        probe.handleRealitySplitTrialBossDeath(adaptedActor);
        require(probe.recoveries==1&&probe.finished==1
                        && probe.realitySplitTrialController.namedTrial(adaptedTrial.chamber()).stage()
                                == RealitySplitTrialController.Stage.ACTIVE,
                "a named Marksman or Archmage cannot complete with another actor's persisted checkpoint");
        require(probe.realitySplitTrialController.namedTrial(1).stage()
                        == RealitySplitTrialController.Stage.ACTIVE
                        && probe.realitySplitTrialController.namedTrial(1).echoReceipts().isEmpty(),
                "an unspawned deferred Echo remains active and has no completion receipt");
        require(!probe.realitySplitTrialController.allComplete(71),
                "unavailable named trials cannot authorize the Wave 7 handoff");
    }
}
''', encoding="utf-8")
    paths = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/Wave7EchoDuelCellPolicy.java",
        "domain/Wave7NamedTrialActorPolicy.java",
        "domain/Wave7RoomLayoutPolicy.java",
        "domain/wave7/EchoBehaviorStrategy.java", "domain/wave7/EchoLoadoutState.java",
        "runtime/RealitySplitEchoDuelState.java", "runtime/RealitySplitTrialController.java",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "NamedWave7DeathProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_official_echo_cell_blocks_player_movement_and_teleports_between_duels():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    def public_method(signature):
        start = source.index("    public " + signature)
        opening = source.index("{", start)
        depth = 0
        for index in range(opening, len(source)):
            if source[index] == "{":
                depth += 1
            elif source[index] == "}":
                depth -= 1
                if depth == 0:
                    return source[start:index + 1]
        raise AssertionError(f"unclosed method {signature}")

    move = public_method("void onRealitySplitPlayerMove(PlayerMoveEvent event)")
    teleport = public_method("void onRealitySplitPlayerTeleport(PlayerTeleportEvent event)")
    assert "activeNamedEchoCell(player.getUniqueId())" in move
    assert "isNamedEchoCellTransitionAllowed(player.getUniqueId(), from, to, echoAnchor)" in move
    assert "activeNamedEchoCell(playerId)" in teleport
    assert "isNamedEchoCellLocationAllowed(playerId, target, anchor)" in teleport
    transition_start = source.index("private boolean isNamedEchoCellTransitionAllowed(")
    transition_end = source.index("private Set<UUID> completedNamedEchoOwners(", transition_start)
    assert "Wave7EchoDuelCellBarrierPolicy.allowsOwnerTransition(" in source[transition_start:transition_end]
    assert "completedNamedEchoOwners(trial)" in source[transition_start:transition_end]


def test_official_wave7_preflight_runs_before_cleanup_teleport_and_actor_spawn(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    initializer = extract(source, "boolean initializeWaveGameplay(int wave, World world, Location core,")
    preflight_start = initializer.index("preflightWave7StartBeforeWorldMutation(")
    cleanup = initializer.index("if (!clearWaveEntities(sandbox)) return false;")
    teleport = initializer.index("teleportCurrentParticipantsToChambers(world, core);")
    assert preflight_start < cleanup < teleport
    assert "startRealitySplitTrials(world, core)" in initializer

    preflight = extract(source, "boolean preflightWave7StartBeforeWorldMutation(")
    assert preflight.index("preflightNamedTrialActorAdapters()") < preflight.index(
        "preflightNamedEchoDuelRooms()")
    assert preflight.index("preflightNamedEchoDuelRooms()") < preflight.index(
        "preflightWave7BarrierJournal(world)")
    assert preflight.index("preflightWave7BarrierJournal(world)") < preflight.index(
        "preflightWave7BarrierCells(world, core)")
    assert preflight.index("preflightWave7BarrierCells(world, core)") < preflight.index(
        "saveStateSync()")

    probe = tmp_path / "Wave7PreflightProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.*;
import java.util.random.RandomGenerator;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class Wave7PreflightProbe {
    String eventId="00000000-0000-0000-0000-000000000071",recoveryReason="";
    String lastWarning="";
    long generation=71; boolean adaptersReady=true,echoReady=true,journalReady=true,barrierCellsReady=true,recovery;
    boolean namedWave7InitialActorCreation,namedWave7RuntimeRestorePending;
    List<RealitySplitTrialController.NamedTrial> namedWave7PreflightSelectedLayout=List.of();
    int activeWave,saves,cleanups,teleports,spawns;
    Map<String,String> wave7RecoveryCheckpoint=Map.of();
    RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
    RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
    static class World {} static class Location {World world;Location(World world){this.world=world;}World getWorld(){return world;}}
    enum EventPhase { RECOVERY_REQUIRED }
    static class AttemptLifecycle { boolean enableWave7Returns(long generation){return true;} }
    AttemptLifecycle attemptLifecycle=new AttemptLifecycle();
    Logger getLogger(){return new Logger("wave7-preflight-probe",null){
        @Override public void warning(String message){lastWarning=message;}
    };}
    UUID parseUuidOrNull(String raw){return UUID.fromString(raw);}
    boolean preflightNamedTrialActorAdapters(){return adaptersReady;}
    boolean preflightNamedEchoDuelRooms(){return echoReady;}
    boolean preflightNamedEchoDuelBarrierPlan(){return true;}
    boolean preflightWave7BarrierJournal(World world){return journalReady;}
    boolean preflightWave7BarrierCells(World world,Location core){return barrierCellsReady;}
    boolean namedRealitySplitTrialImplemented(RealitySplitTrialController.NamedTrial trial){return true;}
    void enterNamedRealitySplitRecovery(String reason){recoveryReason=reason;recovery=true;}
    boolean saveStateSync(){saves++;return true;}
''' + test_layout + preflight + r'''
    boolean initializeAfterPreflight(World world,Location core,List<UUID> owners){
        if(!preflightWave7StartBeforeWorldMutation(world,core,new LinkedHashSet<>(owners),false))return false;
        cleanups++;teleports++;spawns++;return true;
    }
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var solo=new Wave7PreflightProbe();
        var soloWorld=new World();
        require(!solo.initializeAfterPreflight(soloWorld,new Location(soloWorld),List.of(new UUID(0,1))),
                "Wave 7 must refuse a one-player roster during preflight");
        require(solo.lastWarning.contains("reason=minimum-roster-size")
                        &&solo.lastWarning.contains("players=1")&&solo.lastWarning.contains("minimum=2"),
                "single-player refusal must report the Wave 7 minimum clearly");
        require(!solo.realitySplitChamberController.owns(71)&&!solo.realitySplitTrialController.owns(71)
                        &&solo.activeWave==0&&solo.saves==0&&solo.cleanups==0
                        &&solo.teleports==0&&solo.spawns==0&&!solo.recovery,
                "single-player refusal must happen before event state, persistence, or world mutation");
        var refused=new Wave7PreflightProbe();refused.echoReady=false;
        var refusedWorld=new World();
        require(!refused.initializeAfterPreflight(refusedWorld,new Location(refusedWorld),List.of(new UUID(0,1),new UUID(0,2))),
                "unready Echo must refuse Wave 7 before world work");
        require(refused.cleanups==0&&refused.teleports==0&&refused.spawns==0,
                "failed preflight must not clear entities, teleport owners, or spawn actors");
        var adapterRefused=new Wave7PreflightProbe();adapterRefused.adaptersReady=false;
        var adapterWorld=new World();
        require(!adapterRefused.initializeAfterPreflight(adapterWorld,new Location(adapterWorld),
                List.of(new UUID(0,1),new UUID(0,2))),"unsupported actor adapter must refuse the attempt");
        require(adapterRefused.cleanups==0&&adapterRefused.teleports==0&&adapterRefused.spawns==0,
                "adapter refusal must not mutate the room world");
        var journalRefused=new Wave7PreflightProbe();journalRefused.journalReady=false;
        var journalWorld=new World();
        require(!journalRefused.initializeAfterPreflight(journalWorld,new Location(journalWorld),
                List.of(new UUID(0,1),new UUID(0,2))),"unsafe journal state must refuse the attempt");
        require(journalRefused.cleanups==0&&journalRefused.teleports==0&&journalRefused.spawns==0,
                "journal refusal must not mutate the room world");
        var cellsRefused=new Wave7PreflightProbe();cellsRefused.barrierCellsReady=false;
        var cellsWorld=new World();
        require(!cellsRefused.initializeAfterPreflight(cellsWorld,new Location(cellsWorld),List.of(new UUID(0,1),new UUID(0,2))),
                "unsafe planned barrier cells must refuse Wave 7");
        require(cellsRefused.cleanups==0&&cellsRefused.teleports==0&&cellsRefused.spawns==0,
                "invalid barrier geometry must not reach cleanup, teleport, or actor spawn");
        var ready=new Wave7PreflightProbe();
        var readyWorld=new World();
        require(ready.initializeAfterPreflight(readyWorld,new Location(readyWorld),List.of(new UUID(0,1),new UUID(0,2))),
                "valid Echo and journal preflight may continue");
        require(ready.cleanups==1&&ready.teleports==1&&ready.spawns==1,
                "successful preflight permits the ordered world-start operations");
        require(ready.namedWave7PreflightSelectedLayout.equals(
                        ready.realitySplitTrialController.namedSnapshot().selectedLayout()),
                "preflight retains the exact sampled room selection for startup");
    }
}
''', encoding="utf-8")
    paths = [SOURCE / item for item in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java", "domain/Wave7EchoDuelCellPolicy.java",
        "domain/Wave7NamedTrialActorPolicy.java", "domain/wave7/EchoBehaviorStrategy.java",
        "domain/Wave7RoomLayoutPolicy.java",
        "domain/wave7/EchoLoadoutState.java", "runtime/RealitySplitEchoDuelState.java",
        "runtime/RealitySplitChamberController.java", "runtime/RealitySplitTrialController.java",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, paths), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7PreflightProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave7_barrier_world_preflight_reads_planned_blocks_without_mutating_world():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    preflight = extract(source, "boolean preflightWave7BarrierCells(World world, Location core)")
    assert "RealitySplitBarrierPolicy.cellsExcludingBoundaries(" in preflight
    assert "echoPlan.closedCells()" in preflight
    assert "inspectWave7BarrierCell(world, core, floorY, cell)" in preflight
    for mutating_call in ("setType(", "spawn(", "spawnEntity(", ".remove(", "prepare("):
        assert mutating_call not in preflight


def test_wave7_start_accepts_a_valid_restored_journal_from_another_wave():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    preflight = extract(source, "boolean preflightWave7BarrierJournal(World world)")
    restored = preflight.index("if (journal.status() == HazardMutationJournal.Status.RESTORED) return true;")
    barrier_only = preflight.index("entry -> !entry.isRealitySplitBarrierMutation()")
    assert restored < barrier_only, (
        "a valid RESTORED journal has no outstanding world mutations, so stale Wave 3/4/5 entries "
        "must not prevent Wave 7 from starting"
    )


def test_wave7_barrier_builder_discards_restored_non_barrier_journal_entries():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    builder = extract(source, "boolean spawnRealitySplitBarriers(World world, Location core)")
    discard = builder.index("boolean discardRestoredJournal =")
    busy_check = builder.index("if (!journal.valid() ||")
    entry_filter = builder.index("if (!entry.isRealitySplitBarrierMutation())")
    assert discard < busy_check, "restored journal ownership must be decided before checking stale entry types"
    assert "journal.status() == HazardMutationJournal.Status.RESTORED" in builder[discard:busy_check]
    assert "!existingOnlyRealitySplit" in builder[discard:busy_check]
    assert busy_check < builder.index("if (discardRestoredJournal)") < entry_filter
    inspect = extract(source,
        "Wave7BarrierPreflightPolicy.Candidate inspectWave7BarrierCell(World world, Location core,")
    assert "world.getBlockAt(" in inspect
    assert "Wave7BarrierPreflightPolicy.Candidate" in inspect


def test_wave7_rehydration_matches_retained_journal_cells_to_canonical_absolute_geometry():
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    builder = extract(source, "boolean spawnRealitySplitBarriers(World world, Location core)")
    assert "Wave7BarrierPreflightPolicy.matchJournalCell(" in builder, (
        "retained journal coordinates must match a canonical wall cell at its absolute Y"
    )
    rejection = builder.index("if (cellMatch.isEmpty())")
    cell_resolution = builder.index("RealitySplitBarrierPolicy.Cell cell = cellMatch.get()", rejection)
    assert "return false;" in builder[rejection:cell_resolution], (
        "a retained journal cell outside canonical geometry must block rebuilding"
    )
    planned_skip = builder.index("if (plannedCellSet.contains(cell))", cell_resolution)
    assert rejection < cell_resolution < planned_skip, (
        "only valid canonical cells may reach the existing planned-cell recovery skip"
    )
    assert "entry.floorY() != floorY + cell.level() - 1" not in builder, (
        "the Y check must not derive and validate the same level from the same journal entry"
    )
