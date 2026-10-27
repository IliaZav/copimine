"""Small melee summons must honor cancellation, cover and actor ownership."""
from pathlib import Path
import subprocess

from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_small_kagune_damage_uses_accepted_living_carrier_hit(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(_method(source, signature) for signature in (
        "private boolean withinSmallKaguneRange(",
        "private " + ("boolean" if "private boolean applySmallKaguneStrike(" in source else "void")
        + " applySmallKaguneStrike(",
        "private boolean startSmallKaguneSummon(",
        "private void tickSmallKagune(",
        "private void trimTentacles(",
    ))
    probe = tmp_path / "SmallKaguneStrikeProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.SmallKagunePolicy;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
import me.copimine.endevent.domain.TentacleScalingPolicy;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.runtime.TentacleController;
public class SmallKaguneStrikeProbe {
    enum EventPhase { BOSS_ACTIVE }
    enum EntityEffect { HURT }
    static class Location {
        double distanceSquared(Location other) { return 1; }
        public Location clone() { return new Location(); }
    }
    static class Entity {
        final UUID id = UUID.randomUUID(); boolean valid=true, dead=false, owned=true;
        UUID getUniqueId() { return id; } boolean isValid() { return valid; }
        boolean isDead() { return dead; } String getWorld() { return "end"; }
        Location getLocation() { return new Location(); }
    }
    static class ItemDisplay extends Entity { }
    static class LivingEntity extends Entity {
        boolean visible = true;
        boolean hasLineOfSight(Player target) { return visible; }
    }
    static class Player extends LivingEntity {
        double health=20; int hurts, damageEvents; boolean cancelled;
        boolean isOnline() { return true; }
        double getHealth() { return health; } void setHealth(double hp) { health=hp; }
        void damage(double amount, Entity source) {
            if (!(source instanceof LivingEntity)) throw new AssertionError("nonliving damage source");
            damageEvents++; if (!cancelled) health -= amount;
        }
        void playEffect(EntityEffect effect) { hurts++; }
    }
    record Tuning(double damage, double range, int maxActive) { boolean enabled() { return true; } }
    final Tuning tuning = new Tuning(4,3,2);
    Tuning smallKaguneTuning() { return tuning; }
    final EventPhase phase = EventPhase.BOSS_ACTIVE;
    BossPhase bossPhase = BossPhase.LAST_SEAL;
    int tentacleTargetCursor; long eventTickCounter;
    final int TENTACLE_SMALL_SLOT = 9;
    final Map<UUID,Long> smallKaguneExpiryTicks = new HashMap<>();
    int contacts, hidden;
    final UUID eventId = UUID.randomUUID(); final long generation = 9;
    final TentacleController tentacleController = new TentacleController();
    final Map<UUID,UUID> tentacleHitboxesByDisplay = new HashMap<>();
    final Map<UUID,Entity> ownedEntities = new HashMap<>();
    boolean isActiveBossParticipant(Player p) { return true; }
    boolean ownedBySession(Entity e, UUID event, long gen) { return e.owned; }
    Logger getLogger() { return Logger.getLogger("small-kagune-probe"); }
    int eventScalePlayers() { return 20; }
    List<Player> activeBossParticipants() { return List.of(new Player(),new Player()); }
    ItemDisplay spawnTentacle(LivingEntity boss, Location requested,
            TentacleAnimationPolicy.Kind kind, int slot, Player target) {
        var actor = new ItemDisplay();
        if (!tentacleController.register(generation,actor.id,kind,slot,eventTickCounter)) return null;
        ownedEntities.put(actor.id,actor); return actor;
    }
    void removeTentacle(UUID id, String reason) { tentacleController.remove(id); }
    Player chooseTentacleTarget(List<Player> roster, Location origin) { return roster.isEmpty()?null:roster.get(0); }
    Player playerForTentacle(UUID id, List<Player> roster) {
        return roster.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null);
    }
    void setTentacleState(ItemDisplay d,TentacleAnimationPolicy.State next,UUID target) {
        tentacleController.transition(generation,d.id,next,eventTickCounter,target);
    }
    void tentacleMarker(ItemDisplay d,TentacleAnimationPolicy.Marker marker,Player target) {
        if (marker == TentacleAnimationPolicy.Marker.CONTACT) contacts++;
        if (marker == TentacleAnimationPolicy.Marker.HIDE_BELOW_FLOOR) hidden++;
    }
''' + methods + r'''
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var p = new SmallKaguneStrikeProbe(); var display = new ItemDisplay();
        var carrier = new LivingEntity(); var target = new Player();
        p.tentacleController.begin(p.generation);
        p.tentacleController.register(p.generation, display.id,
                TentacleAnimationPolicy.Kind.SMALL, 9, 0);
        p.tentacleHitboxesByDisplay.put(display.id, carrier.id);
        p.ownedEntities.put(display.id, display); p.ownedEntities.put(carrier.id,carrier);
        target.cancelled=true; p.applySmallKaguneStrike(display,target);
        check(target.health==20 && target.hurts==0, "cancelled strike bypassed protection");
        target.cancelled=false; carrier.visible=false;
        p.applySmallKaguneStrike(display,target);
        check(target.health==20 && target.hurts==0, "strike went through solid cover");
        carrier.visible=true; carrier.owned=false; p.applySmallKaguneStrike(display,target);
        check(target.health==20, "stale event carrier inflicted damage");
        carrier.owned=true; p.applySmallKaguneStrike(display,target);
        check(target.health==16 && target.hurts==1, "accepted melee strike was lost or duplicated");
        p.ownedEntities.remove(carrier.id); p.applySmallKaguneStrike(display,target);
        check(target.health==16, "missing carrier inflicted invisible damage");

        var cast = new SmallKaguneStrikeProbe();
        cast.tentacleController.begin(cast.generation);
        check(cast.startSmallKaguneSummon(carrier), "valid final-phase summon was unreachable");
        check(cast.tentacleController.count()==2, "configured summon cap ignored");
        check(!cast.startSmallKaguneSummon(carrier) && cast.tentacleController.count()==2,
                "repeated cast exceeded role concurrency");
        cast.trimTentacles(8,0);
        check(cast.tentacleController.count()==2, "ordinary grab trim deleted small melee summons");
        var state=cast.tentacleController.states().values().iterator().next();
        var actor=(ItemDisplay)cast.ownedEntities.get(state.entityId());
        cast.eventTickCounter=0;
        cast.setTentacleState(actor,TentacleAnimationPolicy.State.DYING,null);
        cast.tickSmallKagune(actor,cast.tentacleController.state(actor.id),List.of(target));
        check(cast.tentacleController.state(actor.id)!=null && cast.hidden==0,
                "death animation was removed at its first frame");
        cast.eventTickCounter=TentacleAnimationPolicy.durationTicks(TentacleAnimationPolicy.State.DYING);
        cast.tickSmallKagune(actor,cast.tentacleController.state(actor.id),List.of(target));
        check(cast.tentacleController.state(actor.id)==null && cast.hidden==1,
                "death animation failed to clean up at its deadline");
    }
}
''', encoding="utf-8")
    source_root = ROOT / "copimine-end-event/src"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(source_root),
                             "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "SmallKaguneStrikeProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
