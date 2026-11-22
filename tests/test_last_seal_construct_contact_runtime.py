"""Execute the real Last-Seal construct runtime's one-contact-per-cast rule.

The shipped order recorded the receipt (``hits.add``) BEFORE the damage
transaction. A refused hit (dead, out of the event, immunity window) therefore
consumed the single allowed contact and the spell silently did nothing for the
rest of its cast. This probe compiles the real ``LastSealConstructRuntime`` with
a deterministic host and proves a refused hit is not consumed.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/LastSealConstructRuntime.java"
FRAME = ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/KaguneSpellFrame.java"

STUBS = {
    "org/bukkit/World.java": """
        package org.bukkit;
        import java.util.UUID;
        public class World {
            public final UUID id = UUID.randomUUID();
            public UUID getUID() { return id; }
            public boolean equals(Object o) { return o instanceof World w && w.id.equals(id); }
            public int hashCode() { return id.hashCode(); }
        }
    """,
    "org/bukkit/util/Vector.java": """
        package org.bukkit.util;
        public class Vector {
            public double x, y, z;
            public Vector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
            public Vector clone() { return new Vector(x, y, z); }
            public Vector subtract(Vector o) { x -= o.x; y -= o.y; z -= o.z; return this; }
            public Vector multiply(double m) { x *= m; y *= m; z *= m; return this; }
            public Vector setY(double v) { y = v; return this; }
            public Vector normalize() { double l = Math.sqrt(x*x + y*y + z*z);
                if (l > 0) { x /= l; y /= l; z /= l; } return this; }
            public double lengthSquared() { return x*x + y*y + z*z; }
            public double getX() { return x; }
            public double getZ() { return z; }
        }
    """,
    "org/bukkit/Location.java": """
        package org.bukkit;
        import org.bukkit.util.Vector;
        public class Location {
            public World world; public double x, y, z;
            public Location(World w, double x, double y, double z) { world = w; this.x = x; this.y = y; this.z = z; }
            public Location clone() { return new Location(world, x, y, z); }
            public World getWorld() { return world; }
            public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
            public Vector toVector() { return new Vector(x, y, z); }
            public Location add(double dx, double dy, double dz) { x += dx; y += dy; z += dz; return this; }
            public Location add(Vector v) { x += v.x; y += v.y; z += v.z; return this; }
            public Location subtract(Vector v) { x -= v.x; y -= v.y; z -= v.z; return this; }
            public double distanceSquared(Location o) {
                double dx = x-o.x, dy = y-o.y, dz = z-o.z; return dx*dx + dy*dy + dz*dz; }
        }
    """,
    "org/bukkit/entity/LivingEntity.java": """
        package org.bukkit.entity;
        import org.bukkit.Location;
        public class LivingEntity {
            public Location at;
            public Location getLocation() { return at; }
        }
    """,
    "org/bukkit/entity/ItemDisplay.java": """
        package org.bukkit.entity;
        public class ItemDisplay {
            public boolean valid = true;
            public boolean isValid() { return valid; }
        }
    """,
    "org/bukkit/entity/Player.java": """
        package org.bukkit.entity;
        import java.util.UUID;
        import org.bukkit.Location;
        import org.bukkit.World;
        import org.bukkit.util.Vector;
        public class Player extends LivingEntity {
            public final UUID id = UUID.randomUUID();
            public World world;
            public boolean online = true, dead = false;
            public Player(World w, double x, double y, double z) {
                world = w; at = new Location(w, x, y, z);
            }
            public UUID getUniqueId() { return id; }
            public boolean isOnline() { return online; }
            public boolean isDead() { return dead; }
            public World getWorld() { return world; }
            public Location getLocation() { return at; }
            public double getWidth() { return 0.6D; }
            public double getHeight() { return 1.8D; }
            public void setFallDistance(float value) { }
            public void setVelocity(Vector value) { }
        }
    """,
}

PROBE = r'''
import java.util.*;
import me.copimine.protocol.KaguneSpellFrame;
import me.copimine.endevent.runtime.LastSealConstructRuntime;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class LastSealContactProbe {
    static final class Host implements LastSealConstructRuntime.Host {
        final World world = new World();
        final LivingEntity boss = new LivingEntity();
        final Player player = new Player(world, 0, 0, 0);
        List<Player> party = List.of(player);
        KaguneSpellFrame published;
        LastSealConstructRuntime.Scope scope;
        int hitCalls, successfulHits, finishedCalls;
        boolean acceptHit;
        String finishedReason = "";
        Host() { boss.at = new Location(world, 0, 0, 8);
            scope = new LastSealConstructRuntime.Scope("event", 1L, UUID.randomUUID()); }
        public LastSealConstructRuntime.Scope scope(LivingEntity b) { return scope; }
        public boolean active(LivingEntity b) { return true; }
        public List<Player> participants() { return party; }
        public Location floor(Location near) { return near.clone(); }
        public boolean traversable(Location feet) { return true; }
        public ItemDisplay create(Location origin, KaguneSpellFrame initial) { return new ItemDisplay(); }
        public void publish(ItemDisplay carrier, KaguneSpellFrame frame) { published = frame; }
        public void warning(Location origin, List<KaguneSpellFrame.Point> points, int kind) { }
        public void impact(Location origin, int kind) { }
        public boolean hit(LivingEntity b, Player p, double damage) {
            hitCalls++;
            if (!acceptHit) return false;
            successfulHits++;
            return true;
        }
        public void remove(ItemDisplay carrier) { }
        public void finished(LastSealConstructRuntime.Scope scope, String reason) {
            finishedCalls++; finishedReason = reason;
        }
        public void failed(String reason) { throw new AssertionError("failed: " + reason); }
    }
    static void check(boolean ok, String m) { if (!ok) throw new AssertionError(m); }
    public static void main(String[] args) {
        // ERUPTION strikes at the locked ground, so a player standing on it is in
        // contact from the first STRIKING frame.
        var host = new Host();
        var runtime = new LastSealConstructRuntime(host);
        check(runtime.start(host.boss, host.player, KaguneSpellFrame.ERUPTION, 0L), "cast did not start");
        // A refused hit must NOT consume the one allowed contact.
        host.acceptHit = false;
        runtime.tick(host.boss, 46L);
        check(host.hitCalls == 1, "the strike did not attempt a hit");
        check(host.successfulHits == 0, "a refused hit was counted as damage");
        // The very next damaging frame may still hit the same player.
        host.acceptHit = true;
        runtime.tick(host.boss, 47L);
        check(host.successfulHits == 1, "the receipt was consumed by a refused hit");
        // And once it really hit, it may never hit that player again this cast.
        runtime.tick(host.boss, 48L);
        runtime.tick(host.boss, 49L);
        check(host.successfulHits == 1, "the same player was damaged twice in one cast");
        check(runtime.active(), "cast ended early");

        var partyHost = new Host();
        var second = new Player(partyHost.world, 6, 0, 0);
        var overlapping = new Player(partyHost.world, .8, 0, 0);
        var far = new Player(partyHost.world, 14, 0, 0);
        partyHost.party = List.of(partyHost.player, second, overlapping, far);
        var partyCast = new LastSealConstructRuntime(partyHost);
        check(partyCast.start(partyHost.boss, partyHost.player, KaguneSpellFrame.ERUPTION, 0L),
                "party eruption did not start");
        var locked = partyHost.published.points();
        check(locked.contains(new KaguneSpellFrame.Point(6, 0, 0)), "teammate's position was ignored");
        check(!locked.contains(new KaguneSpellFrame.Point(.8, 0, 0)), "overlapping warning was admitted");
        check(!locked.contains(new KaguneSpellFrame.Point(14, 0, 0)), "far-away warning was admitted");
        check(locked.size() == 5, "bounded eruption layout did not fill its valid roots");
        partyHost.acceptHit = true;
        partyCast.tick(partyHost.boss, 29L);
        check(partyHost.hitCalls == 0, "warning did damage");
        partyCast.tick(partyHost.boss, 47L);
        check(partyHost.hitCalls == 2, "the two locked positions did not resolve independently");
        System.out.println("OK");
    }
}
'''


def test_refused_hit_does_not_consume_the_single_contact(tmp_path):
    stubs = tmp_path / "stubs"
    for rel, body in STUBS.items():
        target = stubs / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body, encoding="utf-8")
    runtime = tmp_path / "runtime"
    runtime.mkdir()
    probe = tmp_path / "LastSealContactProbe.java"
    probe.write_text(PROBE, encoding="utf-8")
    sources = [str(p) for p in stubs.rglob("*.java")] + [str(FRAME), str(RUNTIME), str(probe)]
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(runtime)] + sources,
                           capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(["java", "-cp", str(runtime), "LastSealContactProbe"],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
