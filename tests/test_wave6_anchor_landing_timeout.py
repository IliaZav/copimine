"""Exercise the real Anchor landing timeout before its cancellation cleanup."""

from pathlib import Path
import subprocess

from tests.test_wave6_anchor_cancel_fall_protection import _method


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "copimine-end-event/src"


def test_timeout_does_not_report_an_airborne_player_as_landed(tmp_path):
    source = (SOURCE_ROOT / "me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    landing = _method(source, "public boolean resolveGravityAnchorLanding(")
    probe = tmp_path / "AnchorLandingTimeoutProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.GravityAnchorLandingPolicy;
import me.copimine.endevent.domain.GravityAnchorPolicy.AnchorPoint;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.GravityAnchorCast;
public class AnchorLandingTimeoutProbe {
    static class Entity { }
    static final class Player extends Entity {
        final UUID id = UUID.randomUUID();
        boolean grounded;
        double health = 20;
        int hits;
        boolean isOnGround() { return grounded; }
        double getHealth() { return health; }
        void setFallDistance(float ignored) { }
        void damage(double amount, Entity source) { hits++; health -= amount; }
    }
    static final long ANCHOR_LANDING_TICKS = 80L;
    long eventTickCounter;
    final Player player = new Player();
    final Map<UUID, Entity> ownedEntities = new HashMap<>();
    final Map<UUID, Long> wave6AnchorLandingStartedAt = new HashMap<>();
    final Map<UUID, Set<UUID>> wave6AnchorLandingResolvedPlayers = new HashMap<>();
    final Map<UUID, Set<UUID>> wave6AnchorLandingDamageApplied = new HashMap<>();
    final Map<UUID, Set<UUID>> wave6AnchorLiftedPlayers = new HashMap<>();
    Player anchorAffectedPlayer(UUID id, Object ignored) { return id.equals(player.id) ? player : null; }
''' + landing + r'''
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var p = new AnchorLandingTimeoutProbe();
        UUID crystal = UUID.randomUUID();
        var cast = new GravityAnchorCast(7, UUID.randomUUID(), p.player.id,
                new AnchorPoint(0, 70, 0), crystal, 0, List.of(p.player.id));
        p.wave6AnchorLiftedPlayers.put(crystal, Set.of(p.player.id));
        check(!p.resolveGravityAnchorLanding(cast), "airborne player prematurely completed landing");
        p.eventTickCounter = 80;
        check(p.resolveGravityAnchorLanding(cast), "landing timeout did not release the major cast");
        check(!p.wave6AnchorLandingResolvedPlayers.get(crystal).contains(p.player.id),
                "timeout falsely reported an airborne player as landed and removed fall protection");
        check(p.player.hits == 0, "airborne timeout applied landing damage");
        p.player.grounded = true;
        p.resolveGravityAnchorLanding(cast);
        check(p.player.hits == 1 && p.player.health == 18,
                "a real landing must resolve exactly one controlled hit");
        p.resolveGravityAnchorLanding(cast);
        check(p.player.hits == 1, "actual landing was charged twice");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(SOURCE_ROOT),
                               "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    ran = subprocess.run(["java", "-cp", str(tmp_path), "AnchorLandingTimeoutProbe"],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
