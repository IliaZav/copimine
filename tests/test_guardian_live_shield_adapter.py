"""Run production shield membership against missing/stale actor pairs."""

from pathlib import Path
import subprocess

from tests.test_boss_phase_damage_transaction_adapter import _method


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "copimine-end-event/src"


def test_only_complete_current_living_guardians_grant_shields(tmp_path):
    source = (SOURCE_ROOT / "me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(_method(source, signature) for signature in (
        "private int livingTentacleGuardianCount()",
        "private List<Integer> livingTentacleGuardianSlots()",
    ))
    # Include any shared membership predicate from the production adapter.
    signature = "private boolean isLivingTentacleGuardian("
    if signature in source:
        methods += "\n" + _method(source, signature)
    probe = tmp_path / "GuardianLiveShieldProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.TentacleAnimationPolicy;
import me.copimine.endevent.runtime.TentacleController;
public class GuardianLiveShieldProbe {
    static class Entity {
        boolean valid = true, dead = false, owned = true;
        boolean isValid() { return valid; }
        boolean isDead() { return dead; }
    }
    static class ItemDisplay extends Entity { }
    static class LivingEntity extends Entity { }
    final UUID eventId = UUID.randomUUID();
    final long generation = 8;
    final TentacleController tentacleController = new TentacleController();
    final Map<Integer, Double> tentacleGuardianHealth = new HashMap<>();
    final Map<UUID, Entity> ownedEntities = new HashMap<>();
    final Map<UUID, UUID> tentacleHitboxesByDisplay = new HashMap<>();
    final Map<UUID, UUID> tentacleDisplaysByHitbox = new HashMap<>();
    boolean ownedBySession(Entity actor, UUID event, long gen) { return actor.owned; }
''' + methods + r'''
    void check(int expected, String message) {
        if (livingTentacleGuardianCount() != expected
                || livingTentacleGuardianSlots().size() != expected)
            throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var p = new GuardianLiveShieldProbe();
        p.tentacleController.begin(p.generation);
        var id = UUID.randomUUID();
        var hitbox = UUID.randomUUID();
        p.tentacleController.register(p.generation, id, false, 0, 0);
        p.tentacleGuardianHealth.put(0, 60.0);
        p.check(0, "a stale controller entry invented a shield");
        var display = new ItemDisplay();
        var carrier = new LivingEntity();
        p.ownedEntities.put(id, display);
        p.tentacleHitboxesByDisplay.put(id, hitbox);
        p.tentacleDisplaysByHitbox.put(hitbox, id);
        p.check(0, "a display without a damageable carrier invented a shield");
        p.ownedEntities.put(hitbox, carrier);
        p.check(1, "a complete living guardian failed to protect");
        carrier.valid = false; p.check(0, "unloaded carrier protected");
        carrier.valid = true; carrier.dead = true; p.check(0, "dead carrier protected");
        carrier.dead = false; display.owned = false; p.check(0, "old event display protected");
        display.owned = true; carrier.owned = false; p.check(0, "old event carrier protected");
        carrier.owned = true; display.valid = false; p.check(0, "missing display protected");
        display.valid = true; p.tentacleDisplaysByHitbox.clear();
        p.check(0, "mismatched actor pair protected");
        p.tentacleDisplaysByHitbox.put(hitbox, id);
        p.tentacleController.transition(p.generation, id,
                TentacleAnimationPolicy.State.DYING, 1, null);
        p.check(0, "dying guardian protected");
        p.tentacleController.transition(p.generation, id,
                TentacleAnimationPolicy.State.SHIELD_CHANNEL, 2, null);
        p.tentacleGuardianHealth.put(0, 0.0); p.check(0, "zero private HP protected");
        p.tentacleGuardianHealth.put(0, 60.0);
        p.check(1, "successfully reconciled guardian did not protect");
    }
}
''', encoding="utf-8")
    sources = [SOURCE_ROOT / ("me/copimine/endevent/" + name + ".java") for name in (
        "domain/TentacleAnimationPolicy", "runtime/TentacleController",
    )]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                             *map(str, sources), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "GuardianLiveShieldProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
