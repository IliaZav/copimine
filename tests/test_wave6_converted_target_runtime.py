"""Turncoat native targeting passes through every applicable production listener.

The contact fixture compiles the real selector/predicate; this fixture adds all
five target callbacks and dispatches native target events in both priority ties.
"""
import subprocess

import pytest

from tests.test_wave6_converted_contact_runtime import SOURCE, converted_probe
from tests.test_wave7_named_trial_warning_audio_runtime import extract_method


@pytest.fixture(scope="module")
def target_probe(converted_probe, tmp_path_factory):
    directory = tmp_path_factory.mktemp("wave6-converted-target")
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(extract_method(source, signature) for signature in (
        "public void onEventSkeletonTarget(",
        "public void onWaveNavigationTarget(",
        "public void onCollapseRingRoleTarget(",
        "public void onRitualRoleTarget(",
        "public void onRitualConvertedMobTarget(",
    ))
    support = r'''
    static class Skeleton extends Mob {Skeleton(World world){super(world);}}
    static class EntityTargetLivingEntityEvent {
        final Entity entity;LivingEntity target;boolean cancelled;
        EntityTargetLivingEntityEvent(Entity entity,LivingEntity target){this.entity=entity;this.target=target;}
        Entity getEntity(){return entity;}LivingEntity getTarget(){return target;}void setTarget(LivingEntity target){this.target=target;}
        void setCancelled(boolean value){cancelled=value;}boolean isCancelled(){return cancelled;}
    }
    static class SkeletonCombatPolicy {static boolean canTargetPlayersOnly(String type,boolean allowed){return allowed;}}
    static class NamespacedKey {NamespacedKey(Object owner,String key){}}
    static class RealitySplitTrialController {enum NamedTrial {PERSONAL_ECHO}}
    static class Bukkit {static Player getPlayer(UUID id){return null;}}
    final Object keyKind="kind",keyTrialRole="role",keyWave="wave";
    static final String EVENT_KIND_REALITY_SPLIT_TRIAL="trial";
    final Map<UUID,Object> waveLeashReturns=new HashMap<>();boolean testWaveFrontVisualMode;
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("TurncoatTargetProbe");}
    String readString(Entity entity,Object key){return "WAVE_MOB";}
    int readInt(Entity entity,Object key,int fallback){return 0;}
    boolean isWaveCombatKind(String kind){return "WAVE_MOB".equals(kind);}
    boolean isEventSkeleton(Entity entity){return entity instanceof Skeleton;}
    boolean isCombatTarget(Player player){return player.valid&&!player.dead&&player.owned;}
    boolean realitySplitTargetAllowed(Entity entity,Player player){return isCombatTarget(player);}
    boolean isOfficialCurrentAttempt(){return true;}
    boolean isCurrentRitualGuard(Entity entity){return false;}
    boolean isCurrentRitualCaster(Entity entity){return false;}
    boolean isCurrentCollapseGuard(Entity entity){return false;}
    boolean ritualGuardTargetAllowed(Entity entity,Player player){return false;}
    boolean currentCollapseTargetAllowed(Entity entity,Player player){return false;}
    boolean isWaveTargetAllowed(Entity entity,Player player){return isCombatTarget(player);}
    boolean isCurrentRealitySplitTrialTarget(Mob entity,Player player){return false;}
    UUID parseUuidOrNull(String raw){try{return UUID.fromString(raw);}catch(Exception ignored){return null;}}
''' + methods + r'''
    void dispatchNativeTarget(EntityTargetLivingEntityEvent event,boolean reverse){
        if(reverse){
            onRitualConvertedMobTarget(event);onRitualRoleTarget(event);onCollapseRingRoleTarget(event);
            onWaveNavigationTarget(event);onEventSkeletonTarget(event);
        }else{
            onEventSkeletonTarget(event);onWaveNavigationTarget(event);onCollapseRingRoleTarget(event);
            onRitualRoleTarget(event);onRitualConvertedMobTarget(event);
        }
        if(!event.isCancelled()&&event.entity instanceof Mob mob)mob.setTarget(event.getTarget());
    }
    public static void runTarget(String scenario,boolean reverse){
        var p=new ConvertedContactProbe();
        Mob ally=scenario.contains("skeleton")?new Skeleton(p.world):new Mob(p.world);
        ally.converted=true;ally.ai=true;ally.aware=true;
        LivingEntity enemy=new Mob(p.world);enemy.position=new Location(3,0,0);
        p.ownedEntities.put(ally.id,ally);p.ownedEntities.put(enemy.id,enemy);
        p.ritualConvertedMobUntilMillis.put(ally.id,Long.MAX_VALUE);p.ritualConversionTargets.add(ally.id);
        boolean cancelled=false,runTick=false,priorCancelled=false;
        switch(scenario){
            case "mob-legal", "skeleton-legal" -> runTick=true;
            case "skeleton-cover" -> {runTick=true;ally.lineOfSight=false;}
            case "mob-player", "skeleton-player" -> {enemy=new Player(p.world);cancelled=true;}
            case "mob-converted", "skeleton-converted" -> {enemy.converted=true;p.ritualConvertedMobUntilMillis.put(enemy.id,Long.MAX_VALUE);cancelled=true;}
            case "mob-foreign", "skeleton-foreign" -> {enemy.owned=false;cancelled=true;}
            case "mob-prior-cancel", "skeleton-prior-cancel" -> {priorCancelled=true;cancelled=true;}
            case "mob-expired", "skeleton-expired" -> {p.ritualConvertedMobUntilMillis.put(ally.id,0L);cancelled=true;}
            case "mob-inactive", "skeleton-inactive" -> {p.waveActive=false;cancelled=true;}
            case "mob-stale-wave", "skeleton-stale-wave" -> {p.activeWave=7;cancelled=true;}
            case "mob-dead", "skeleton-dead" -> {ally.dead=true;cancelled=true;}
            case "mob-other-world", "skeleton-other-world" -> {enemy.world=new World();cancelled=true;}
            case "hostile-mob", "hostile-skeleton" -> {ally.converted=false;cancelled=true;}
            case "ordinary-player" -> {ally.converted=false;enemy=new Player(p.world);}
            default -> throw new AssertionError("unknown scenario "+scenario);
        }
        if(runTick){
            p.tickRitualConvertedMobs(1_000L);
            if(ally.target!=enemy||enemy.damageCalls!=0)throw new AssertionError("native owner must acquire target without a scripted hit");
        }else{
            ally.setTarget(null);
        }
        var event=new EntityTargetLivingEntityEvent(ally,enemy);event.setCancelled(priorCancelled);
        p.dispatchNativeTarget(event,reverse);
        if(event.isCancelled()!=cancelled)throw new AssertionError("native listener chain cancellation="+event.isCancelled()+" for "+scenario);
        if(!cancelled&&ally.target!=enemy)throw new AssertionError("accepted native target must be retained");
        if(cancelled&&ally.target!=null)throw new AssertionError("rejected target must not remain selected");
        if(scenario.endsWith("expired")){
            p.tickRitualConvertedMobs(1_000L);
            if(p.ownedEntities.containsKey(ally.id)||p.ritualConvertedMobUntilMillis.containsKey(ally.id))
                throw new AssertionError("expired converted actor must be removed, not reclassified as an enemy");
        }
    }
'''
    code = (converted_probe / "ConvertedContactProbe.java").read_text(encoding="utf-8")
    anchor = "    public static void main(String[] args){"
    assert code.count(anchor) == 1
    code = code.replace(anchor, support + "\n" + anchor, 1)
    path = directory / "ConvertedContactProbe.java"
    path.write_text(code, encoding="utf-8")
    main = directory / "ConvertedTargetMain.java"
    main.write_text("public class ConvertedTargetMain {public static void main(String[] args){"
                    "ConvertedContactProbe.runTarget(args[0],Boolean.parseBoolean(args[1]));}}", encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(path), str(main)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("reverse", [False, True])
@pytest.mark.parametrize("scenario", [
    "mob-legal", "skeleton-legal", "skeleton-cover", "mob-player", "skeleton-player",
    "mob-converted", "skeleton-converted", "mob-foreign", "skeleton-foreign",
    "mob-prior-cancel", "skeleton-prior-cancel", "mob-expired", "skeleton-expired",
    "mob-inactive", "skeleton-inactive", "mob-stale-wave", "skeleton-stale-wave",
    "mob-dead", "skeleton-dead", "mob-other-world", "skeleton-other-world",
    "hostile-mob", "hostile-skeleton", "ordinary-player",
])
def test_converted_native_target_passes_complete_listener_chain(target_probe, scenario, reverse):
    result = subprocess.run(["java", "-cp", str(target_probe), "ConvertedTargetMain", scenario, str(reverse).lower()],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr
