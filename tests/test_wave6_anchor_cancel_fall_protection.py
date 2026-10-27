"""A cancelled anchor must release only the players it actually lifted safely."""

from pathlib import Path
import subprocess
import pytest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start : cursor + 1]
    raise AssertionError(signature)


@pytest.mark.parametrize("scenario", ["cancel", "grounded", "world-change", "quit", "death"])
def test_cancelled_anchor_protects_its_lifted_players_once(tmp_path, scenario):
    source = SOURCE.read_text(encoding="utf-8")
    preserve = _method(source, "private void preserveWave6AnchorFallProtection(")
    fall_handler = _method(source, "public void onGravityAnchorFallDamage(")
    cleanup = _method(source, "public boolean cleanupGravityAnchor(")
    tick = _method(source, "private void tickWave6RitualRuntimeOnce()")
    retirement = (_method(source, "private void retireWave6AnchorFallLeases()")
                  if "private void retireWave6AnchorFallLeases()" in source else "")
    assert "preserveWave6AnchorFallProtection(cast)" in cleanup
    probe = tmp_path / "AnchorCancelFallProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.GravityAnchorLandingPolicy;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.GravityAnchorCast;

public class AnchorCancelFallProbe {
    record Wave6AnchorFallLease(UUID worldId, long untilMillis) { }
    static final class World { final UUID id=UUID.randomUUID(); UUID getUID(){return id;} }
    static final class Entity {
        final World world;
        Entity(World world) { this.world=world; }
        World getWorld() { return world; }
    }
    static final class Player {
        final UUID id=UUID.randomUUID(); World world; boolean online=true, dead=false, grounded=false;
        float fallDistance=12;
        Player(World w){world=w;}
        UUID getUniqueId(){return id;} World getWorld(){return world;}
        boolean isOnline(){return online;} boolean isDead(){return dead;} boolean isOnGround(){return grounded;}
        void setFallDistance(float value){fallDistance=value;}
    }
    static final class Bukkit {
        static final Map<UUID,Player> players=new HashMap<>();
        static World arenaWorld;
        static Player getPlayer(UUID id){return players.get(id);}
        static World getWorld(String ignored) { return arenaWorld; }
    }
    static final class EntityDamageEvent {
        enum DamageCause { FALL, OTHER }
        final Object entity; final DamageCause cause; boolean cancelled;
        EntityDamageEvent(Object entity,DamageCause cause){this.entity=entity;this.cause=cause;}
        Object getEntity(){return entity;} DamageCause getCause(){return cause;}
        void setCancelled(boolean value){cancelled=value;}
    }
    final Map<UUID,Set<UUID>> wave6AnchorLiftedPlayers=new HashMap<>();
    final Map<UUID,Set<UUID>> wave6AnchorLandingResolvedPlayers=new HashMap<>();
    final Map<UUID,Wave6AnchorFallLease> wave6AnchorFallLeases=new HashMap<>();
    final Map<UUID,Entity> ownedEntities=new HashMap<>();
    final long generation=7;
    final String worldName="arena";
    long eventTickCounter=7, lastWave6RitualRuntimeTick=Long.MIN_VALUE;
    static final class Adapter { void tick(long generation,long tick) { } }
    Adapter wave6RitualRuntimeAdapter;
''' + preserve + "\n" + fall_handler + "\n" + retirement + "\n" + tick + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        var p=new AnchorCancelFallProbe(); var world=new World(); var lifted=new Player(world);
        Bukkit.arenaWorld=world;
        var uninvolved=new Player(world); var alreadyResolved=new Player(world);
        Bukkit.players.put(lifted.id,lifted); Bukkit.players.put(uninvolved.id,uninvolved);
        Bukkit.players.put(alreadyResolved.id,alreadyResolved);
        UUID crystal=UUID.randomUUID();
        p.ownedEntities.put(crystal,new Entity(world));
        var cast=new GravityAnchorCast(7,UUID.randomUUID(),lifted.id,new AnchorPoint(0,70,0),crystal,1);
        p.wave6AnchorLiftedPlayers.put(crystal,Set.of(lifted.id,alreadyResolved.id));
        p.wave6AnchorLandingResolvedPlayers.put(crystal,Set.of(alreadyResolved.id));
        p.preserveWave6AnchorFallProtection(cast);
        p.wave6AnchorLiftedPlayers.clear(); p.wave6AnchorLandingResolvedPlayers.clear();
        check(lifted.fallDistance==0 && p.wave6AnchorFallLeases.containsKey(lifted.id),
                "cancelled cast lost the fall protection it owns");
        if (!args[0].equals("cancel")) {
            // A harmless first landing need not emit a FALL damage event.
            // The always-called event tick must retire its lease even after
            // the cast adapter has already been cleared.
            switch (args[0]) {
                case "grounded" -> lifted.grounded=true;
                case "world-change" -> lifted.world=new World();
                case "quit" -> lifted.online=false;
                case "death" -> lifted.dead=true;
                default -> throw new AssertionError(args[0]);
            }
            p.tickWave6RitualRuntimeOnce();
            check(!p.wave6AnchorFallLeases.containsKey(lifted.id),
                    "terminated player state retained a lease for an unrelated later fall: " + args[0]);
            lifted.grounded=false;
            lifted.world=world;
            lifted.online=true;
            lifted.dead=false;
            var unrelated=new EntityDamageEvent(lifted,EntityDamageEvent.DamageCause.FALL);
            p.onGravityAnchorFallDamage(unrelated);
            check(!unrelated.cancelled,"a later unrelated fall inherited the old cast lease");
            return;
        }
        var fall=new EntityDamageEvent(lifted,EntityDamageEvent.DamageCause.FALL);
        p.onGravityAnchorFallDamage(fall);
        check(fall.cancelled && !p.wave6AnchorFallLeases.containsKey(lifted.id),
                "owned fall was not protected exactly once");
        fall=new EntityDamageEvent(lifted,EntityDamageEvent.DamageCause.FALL);
        p.onGravityAnchorFallDamage(fall); check(!fall.cancelled,"fall lease was reused");
        fall=new EntityDamageEvent(uninvolved,EntityDamageEvent.DamageCause.FALL);
        p.onGravityAnchorFallDamage(fall); check(!fall.cancelled,"unrelated player was protected");
        fall=new EntityDamageEvent(alreadyResolved,EntityDamageEvent.DamageCause.FALL);
        p.onGravityAnchorFallDamage(fall); check(!fall.cancelled,"resolved landing was protected twice");
        p.wave6AnchorFallLeases.put(lifted.id,new Wave6AnchorFallLease(world.id,System.currentTimeMillis()-1));
        fall=new EntityDamageEvent(lifted,EntityDamageEvent.DamageCause.FALL);
        p.onGravityAnchorFallDamage(fall); check(!fall.cancelled,"expired lease suppressed unrelated fall");
        // A target leaving the crystal's world must not receive protection in
        // its destination world merely because it was once lifted here.
        var otherWorld = new World();
        lifted.world = otherWorld;
        p.wave6AnchorLiftedPlayers.put(crystal,Set.of(lifted.id));
        p.preserveWave6AnchorFallProtection(cast);
        check(!p.wave6AnchorFallLeases.containsKey(lifted.id),
                "cancelled old-world cast protected an unrelated destination-world fall");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath",
                             str(ROOT / "copimine-end-event/src"), "-d", str(tmp_path), str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "AnchorCancelFallProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
