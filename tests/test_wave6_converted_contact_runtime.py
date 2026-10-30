"""Exercise real Turncoat target selection and fallback melee against Bukkit fakes.

Selection retains the existing 16-block horizontal acquisition radius. Only the
scripted contact hit is restricted; native combat keeps the selected target.
"""
from pathlib import Path
import subprocess

import pytest

from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def converted_probe(tmp_path_factory):
    directory = tmp_path_factory.mktemp("wave6-converted-contact")
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(extract_method(source, signature) for signature in (
        "private void tickRitualConvertedMobs(",
        "private boolean isRitualConvertedTargetAllowed(",
    ))
    code = r'''
import java.util.*;
public class ConvertedContactProbe {
    enum Particle { CRIT }
    static class World {int impacts; void spawnParticle(Object... args){impacts++;}}
    static class Location {
        double x,y,z;
        Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
        double distanceSquared(Location b){return (x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z);}
    }
    static class BoundingBox {
        double minX,minY,minZ,maxX,maxY,maxZ;
        BoundingBox(Location p){minX=p.x-.3;minY=p.y;minZ=p.z-.3;maxX=p.x+.3;maxY=p.y+1.8;maxZ=p.z+.3;}
        BoundingBox expand(double x,double y,double z){minX-=x;maxX+=x;minY-=y;maxY+=y;minZ-=z;maxZ+=z;return this;}
        boolean overlaps(BoundingBox b){return minX<b.maxX&&maxX>b.minX&&minY<b.maxY&&maxY>b.minY&&minZ<b.maxZ&&maxZ>b.minZ;}
    }
    static class Entity {
        final UUID id=UUID.randomUUID();World world;Location position=new Location(0,0,0);
        boolean converted,owned=true,valid=true,dead;
        Entity(World world){this.world=world;}
        UUID getUniqueId(){return id;}World getWorld(){return world;}String getType(){return getClass().getSimpleName();}
        Location getLocation(){return new Location(position.x,position.y,position.z);}
        BoundingBox getBoundingBox(){return new BoundingBox(position);}
        boolean isValid(){return valid;}boolean isDead(){return dead;}
    }
    static class LivingEntity extends Entity {
        double health=20,absorption;int damageCalls,noDamageTicks;boolean cancelled,zeroDamage;
        LivingEntity(World world){super(world);}
        double getHealth(){return health;}double getAbsorptionAmount(){return absorption;}
        int getNoDamageTicks(){return noDamageTicks;}int getMaximumNoDamageTicks(){return 20;}
        void damage(double amount,Entity source){
            damageCalls++;
            if(cancelled||zeroDamage||noDamageTicks>getMaximumNoDamageTicks()/2)return;
            double absorbed=Math.min(absorption,amount);absorption-=absorbed;health-=amount-absorbed;
            noDamageTicks=20;
        }
    }
    static class Mob extends LivingEntity {
        LivingEntity target;boolean lineOfSight=true,ai,aware;
        Mob(World world){super(world);}
        void setTarget(LivingEntity value){target=value;}LivingEntity getTarget(){return target;}boolean hasLineOfSight(Entity entity){return lineOfSight;}
        boolean hasAI(){return ai;}boolean isAware(){return aware;}
    }
    static class Player extends LivingEntity {Player(World world){super(world);}}
    final World world=new World();
    final Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();
    final Map<UUID,Long> ritualConvertedMobUntilMillis=new LinkedHashMap<>();
    final Set<UUID> ritualConversionTargets=new HashSet<>();
    long eventTickCounter=20,generation=7;int activeWave=6;String eventId="event";boolean waveActive=true;
    boolean isRitualConvertedMob(Entity e){return e!=null&&e.converted&&e.owned&&ritualConvertedMobUntilMillis.containsKey(e.id);}
    boolean isLiveOwnedEntity(UUID id){Entity e=ownedEntities.get(id);return e!=null&&e.valid&&!e.dead&&e.owned&&(!(e instanceof LivingEntity l)||l.health>0);}
    boolean isRitualConversionCombatTarget(Entity e){return e instanceof Mob&&!e.converted;}
    boolean ownedBySession(Entity e,String event,long gen){return e.owned&&ownedEntities.get(e.id)==e;}
    boolean ritualWaveActive(){return waveActive;}
    void removeRitualEntity(UUID id){ownedEntities.remove(id);ritualConvertedMobUntilMillis.remove(id);ritualConversionTargets.remove(id);}
    double horizontalDistanceSquared(Location a,Location b){return (a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z);}
''' + methods + r'''
    public static void main(String[] args){
        String scenario=args[0];var p=new ConvertedContactProbe();var ally=new Mob(p.world);var enemy=new Mob(p.world);
        ally.converted=true;enemy.position=new Location(1,0,0);
        p.ownedEntities.put(ally.id,ally);p.ownedEntities.put(enemy.id,enemy);
        p.ritualConvertedMobUntilMillis.put(ally.id,Long.MAX_VALUE);p.ritualConversionTargets.add(ally.id);
        boolean acquired=true,attempt=false,accepted=false,expired=false;long now=1_000L;
        switch(scenario){
            case "contact" -> {attempt=true;accepted=true;}
            case "absorbed-contact" -> {attempt=true;accepted=true;enemy.absorption=4;}
            case "repeated-tick" -> {attempt=true;accepted=true;}
            case "cancelled-contact" -> {attempt=true;enemy.cancelled=true;}
            case "zero-damage-contact" -> {attempt=true;enemy.zeroDamage=true;}
            case "cover-contact" -> ally.lineOfSight=false;
            case "cover-distant" -> {ally.lineOfSight=false;enemy.position=new Location(12,0,0);}
            case "visible-distant" -> enemy.position=new Location(12,0,0);
            case "acquisition-edge" -> enemy.position=new Location(16,0,0);
            case "outside-acquisition" -> {enemy.position=new Location(16.01,0,0);acquired=false;}
            case "above" -> enemy.position=new Location(1,30,0);
            case "below" -> enemy.position=new Location(1,-30,0);
            case "vertical-gap" -> enemy.position=new Location(1,1.9,0);
            case "outside-contact" -> enemy.position=new Location(2,0,0);
            case "native-melee" -> {ally.ai=true;ally.aware=true;}
            case "native-ranged" -> {ally.ai=true;ally.aware=true;enemy.position=new Location(12,0,0);}
            case "recent-native-hit" -> {enemy.health=17;enemy.noDamageTicks=10;}
            case "off-cadence" -> p.eventTickCounter=21;
            case "other-world" -> {enemy.world=new World();acquired=false;}
            case "foreign-target" -> {enemy.owned=false;acquired=false;}
            case "dead-target" -> {enemy.dead=true;acquired=false;}
            case "expired" -> {now=Long.MAX_VALUE;acquired=false;expired=true;}
            case "inactive" -> {p.waveActive=false;acquired=false;expired=true;}
            case "other-wave" -> {p.activeWave=7;acquired=false;expired=true;}
            default -> throw new AssertionError("unknown scenario "+scenario);
        }
        double before=enemy.health,beforeAbsorption=enemy.absorption;
        p.tickRitualConvertedMobs(now);
        if(scenario.equals("repeated-tick"))p.tickRitualConvertedMobs(now);
        if((ally.target==enemy)!=acquired)throw new AssertionError("target acquisition/native target changed: "+scenario);
        if(enemy.damageCalls!=(attempt?1:0))throw new AssertionError("unexpected scripted damage calls="+enemy.damageCalls+" for "+scenario);
        double damage=before+beforeAbsorption-enemy.health-enemy.absorption;
        if(damage!=(accepted?2.0:0.0))throw new AssertionError("accepted damage="+damage+" for "+scenario);
        if(p.world.impacts!=(accepted?1:0))throw new AssertionError("hit feedback requires one accepted hit: "+scenario);
        if(expired&&(p.ownedEntities.containsKey(ally.id)||p.ritualConvertedMobUntilMillis.containsKey(ally.id)))
            throw new AssertionError("expired conversion must be removed");
    }
}
'''
    path = directory / "ConvertedContactProbe.java"
    path.write_text(code, encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(path)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "contact", "absorbed-contact", "repeated-tick", "cancelled-contact", "zero-damage-contact",
    "cover-contact", "cover-distant", "visible-distant", "acquisition-edge", "outside-acquisition",
    "above", "below", "vertical-gap", "outside-contact", "native-melee", "native-ranged",
    "recent-native-hit", "off-cadence", "other-world", "foreign-target", "dead-target",
    "expired", "inactive", "other-wave",
])
def test_converted_ally_requires_real_contact_for_its_fallback_hit(converted_probe, scenario):
    result = subprocess.run(["java", "-cp", str(converted_probe), "ConvertedContactProbe", scenario],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr
