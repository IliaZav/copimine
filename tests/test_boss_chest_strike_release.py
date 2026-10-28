"""A chest-strike sweep that cannot continue must release the cast it owns.

`reconcileBossCastProjection` parks while `bossChestStrikeActive` is set, so a
sweep callback that returns early without clearing the flag freezes the boss for
the rest of the phase. The edge volley already self-heals; the chest strike must
do the same.
"""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def chest_strike_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("boss-chest-strike")
    source = SOURCE.read_text(encoding="utf-8")
    signatures = [
        "void startChestStrike(",
        "void abortBossChestStrike(",
        "void cancelBossCastTask(",
        "void reconcileBossCastProjection(",
    ]
    methods = "\n".join(extract(source, signature) for signature in signatures)
    java = out / "BossChestStrikeReleaseProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
public class BossChestStrikeReleaseProbe {
    enum EventPhase {BOSS_ACTIVE,VICTORY_PROCESSING}
    enum SoundCategory {HOSTILE}
    enum Sound {ENTITY_WARDEN_SONIC_CHARGE}
    enum Particle {SOUL_FIRE_FLAME}
    static final class World {
        void playSound(Object l, Object s, Object c, float a, float b) {}
        void spawnParticle(Object p, Object l, int n, double a, double b, double c, double d) {}
    }
    static final class Location {
        final double x, y, z; final World world;
        Location(){this(0, 0, 0, new World());}
        Location(double x,double y,double z,World w){this.x=x;this.y=y;this.z=z;this.world=w;}
        Location(World w,double x,double y,double z){this(x,y,z,w);}
        Location add(Vector v){return new Location(world,x+v.x,y+v.y,z+v.z);}
        double getX(){return x;} double getY(){return y;} double getZ(){return z;}
        World getWorld(){return world;}
        Vector toVector(){return new Vector(x,y,z);}
        public Location clone(){return new Location(x,y,z,world);}
        Vector getDirection(){return new Vector(0,0,1);}
    }
    static final class Vector {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Vector clone(){return new Vector(x,y,z);}
        Vector multiply(double s){return new Vector(x*s,y*s,z*s);}
        Vector add(Vector o){return new Vector(x+o.x,y+o.y,z+o.z);}
        Vector setY(double v){y=v;return this;}
        Vector subtract(Vector o){return new Vector(x-o.x,y-o.y,z-o.z);}
        double lengthSquared(){return x*x+y*y+z*z;}
        double getX(){return x;} double getZ(){return z;}
    }
    static final class LivingEntity {
        final UUID id=UUID.randomUUID(); boolean valid=true, dead, carrier=true;
        double height=2.0;
        UUID getUniqueId(){return id;}
        boolean isValid(){return valid;}
        boolean isDead(){return dead;}
        double getHeight(){return height;}
        boolean hasLineOfSight(Object p){return true;}
        Location getLocation(){return new Location();}
        void setInvulnerable(boolean v){}
    }
    static final class Player {
        final UUID id=UUID.randomUUID();
        UUID getUniqueId(){return id;}
        Location getLocation(){return new Location();}
        Location getEyeLocation(){return new Location();}
        World getWorld(){return new World();}
        double getHeight(){return 1.8;}
    }
    static final class BukkitTask { final Runnable runnable; BukkitTask(Runnable r){runnable=r;} void cancel(){} boolean isCancelled(){return false;} }
    static final class Scheduler {
        final List<BukkitTask> tasks=new ArrayList<>();
        BukkitTask runTaskLater(Object plugin,Runnable r,long delay){var t=new BukkitTask(r);tasks.add(t);return t;}
    }
    static final class Bukkit {
        static Scheduler scheduler;
        static Scheduler getScheduler(){return scheduler;}
        static Collection<? extends Player> getOnlinePlayers(){return List.of();}
    }
    final Scheduler scheduler=new Scheduler();
    final LivingEntity boss=new LivingEntity();
    final Player target=new Player();
    UUID bossUuid=boss.id;
    String eventId="event";
    long generation=7L;
    boolean carrier=true;
    static final class Registry { boolean owns(long g){return true;} }
    final Registry taskRegistry=new Registry();
    BukkitTask bossCastTask;
    long bossChestStrikeSequence, bossCastSequence, bossEdgeVolleySequence;
    long bossCastDeadlineMillis, bossSpellPauseUntilMillis;
    boolean bossChestStrikeActive, bossEdgeVolleyActive;
    BossPhase bossPhase=BossPhase.RIFT;
    EventPhase phase=EventPhase.BOSS_ACTIVE;
    BossAbilityState bossAbilityState=BossAbilityState.NONE;
    int released;

    boolean isTestBoss(LivingEntity b){return false;}
    boolean isCurrentBossCarrier(LivingEntity b){return carrier && b != null && b.valid && !b.dead;}
    List<Player> activeLivingPlayers(){return List.of();}
    boolean hasProjectileBlockLineOfSight(Location a, Location b){return true;}
    boolean applyContactHit(Player p, LivingEntity b, double d, Vector k){return false;}
    void registerEncounterTask(BukkitTask t){}
    void releaseBossHazardReservation(String reason){released++;}
    void clearWorldVfxBeam(String s){}
    void clearArenaInferno(){}
    void clearVoidMarkZones(){}
    Vector contactFacing(Location o){return new Vector(0,0,1);}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + methods + r'''
    static void check(boolean condition, String message){if(!condition) throw new AssertionError(message);}

    public static void main(String[] args){
        var probe=new BossChestStrikeReleaseProbe();
        Bukkit.scheduler=probe.scheduler;
        String scenario=args[0];
        probe.startChestStrike(probe.boss, probe.target, probe.generation);
        check(probe.bossChestStrikeActive, "start did not claim the cast flag");
        check(!Bukkit.scheduler.tasks.isEmpty(), "start scheduled no sweep");

        if(scenario.equals("lost-carrier")) probe.carrier=false;
        if(scenario.equals("boss-invalid")) probe.boss.valid=false;
        if(scenario.equals("last-seal")) probe.bossPhase=BossPhase.LAST_SEAL;
        if(scenario.equals("phase-left")) probe.phase=EventPhase.VICTORY_PROCESSING;

        // Every scheduled sweep runs; each one hits a guard and must hand back
        // the cast instead of leaving the flag parked.
        for(var task : new ArrayList<>(Bukkit.scheduler.tasks)) task.runnable.run();

        check(!probe.bossChestStrikeActive, "aborted sweep leaked bossChestStrikeActive: reconcile parks and the boss freezes");
        check(probe.bossAbilityState==BossAbilityState.RECOVERY, "aborted sweep left the cast outside recovery: " + probe.bossAbilityState);
        check(probe.bossCastDeadlineMillis>System.currentTimeMillis(), "aborted sweep left no bounded recovery deadline");
        check(probe.released>0, "aborted sweep kept the boss hazard reservation");

        // The projection must now resume managing the state instead of parking.
        probe.reconcileBossCastProjection(probe.boss, probe.bossCastDeadlineMillis+60_000L);
        check(probe.bossAbilityState==BossAbilityState.NONE, "reconcile stayed parked after release: " + probe.bossAbilityState);
    }
}
''', encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
         "-d", str(out), str(java)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return out


@pytest.mark.parametrize("scenario", ["lost-carrier", "boss-invalid", "last-seal", "phase-left"])
def test_aborted_chest_strike_releases_the_cast(chest_strike_probe, scenario):
    result = subprocess.run(
        ["java", "-cp", str(chest_strike_probe), "BossChestStrikeReleaseProbe", scenario],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr