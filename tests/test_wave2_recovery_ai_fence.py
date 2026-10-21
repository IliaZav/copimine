from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def block(source, marker):
    start = source.index(marker)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_wave2_recovery_fence_precedes_ai_restore_and_attack_adapters():
    source = EVENT.read_text(encoding="utf-8")
    ensure = method(source, "private void ensureEventCombatAi(Entity entity)")
    tick = method(source, "private void tickWaveMobAi()")
    rehydrate = method(source, "private boolean rehydrateWaveTwoEntity(Entity entity)")
    mini_bosses = method(source, "private void tickMiniBosses()")

    assert "if (isWaveTwoRecoveryFenced(entity))" in ensure
    assert ensure.index("if (isWaveTwoRecoveryFenced(entity))") < ensure.index("mob.setAI(true)")
    assert "if (isWaveTwoRecoveryFenced(entity)) continue;" in tick
    assert "waveTwoRecoveryFencedEntityUuids.remove(entityId)" in rehydrate
    assert rehydrate.index("waveTwoRecoveryFencedEntityUuids.remove(entityId)") < rehydrate.index(
        "ensureEventCombatAi(entity)")
    assert "isWaveTwoRecoveryFenced(entity)" in mini_bosses


def test_recovery_fenced_wave2_mob_stays_suspended_across_ticks_and_cannot_damage():
    source = EVENT.read_text(encoding="utf-8")
    ensure = method(source, "private void ensureEventCombatAi(Entity entity)")
    guard = block(ensure, "if (isWaveTwoRecoveryFenced(entity))")
    fenced = method(source, "private boolean isWaveTwoRecoveryFenced(Entity entity)")
    suspend = method(source, "private void suspendWaveTwoRecoveryEntity(Entity entity)")
    damage = method(source, "public void onWaveTwoRecoveryFencedEntityDamage(EntityDamageByEntityEvent event)")
    probe = r'''
import java.util.*;
import me.copimine.endevent.domain.WaveTwoEntityRecoveryPolicy;
public final class WaveTwoRecoveryAiFenceProbe {
  enum EventPhase { WAVE_2, RECOVERY_REQUIRED }
  static final String EVENT_KIND_WAVE_MOB = "wave_mob";
  static class Entity {
    final UUID id = UUID.randomUUID(); int wave = 2; String kind = EVENT_KIND_WAVE_MOB;
    UUID getUniqueId() { return id; }
  }
  static class Pathfinder { boolean stopped; void stopPathfinding() { stopped = true; } }
  static class Mob extends Entity {
    boolean ai = true, aware = true; Object target = new Object(); Pathfinder pathfinder = new Pathfinder();
    void setAI(boolean value) { ai = value; } void setAware(boolean value) { aware = value; }
    void setTarget(Object value) { target = value; } Pathfinder getPathfinder() { return pathfinder; }
  }
  static class Projectile extends Entity { Object shooter; Object getShooter() { return shooter; } }
  static class EntityDamageByEntityEvent {
    Entity damager; boolean cancelled;
    Entity getDamager() { return damager; } void setCancelled(boolean value) { cancelled = value; }
  }
  EventPhase phase = EventPhase.RECOVERY_REQUIRED;
  final Set<UUID> waveTwoRecoveryFencedEntityUuids = new HashSet<>();
  boolean isWaveTwoOrdinaryMob(Entity entity) {
    return entity != null && entity.wave == 2 && EVENT_KIND_WAVE_MOB.equals(entity.kind);
  }
  boolean isWaveTwoRecoveryFenced(Entity entity) {
    __FENCED__
  }
  void suspendWaveTwoRecoveryEntity(Entity entity) {
    __SUSPEND__
  }
  void ensureEventCombatAi(Entity entity) {
    __GUARD__
    if (entity instanceof Mob mob) { mob.setAI(true); mob.setAware(true); }
  }
  void onWaveTwoRecoveryFencedEntityDamage(EntityDamageByEntityEvent event) {
    __DAMAGE__
  }
  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  public static void main(String[] args) {
    var h = new WaveTwoRecoveryAiFenceProbe(); var mob = new Mob();
    for (int tick = 0; tick < 5; tick++) {
      h.ensureEventCombatAi(mob);
      check(!mob.ai && !mob.aware && mob.target == null && mob.pathfinder.stopped,
          "ordinary AI ticks cannot restore an unexpected Wave 2 fighter during recovery");
    }
    var melee = new EntityDamageByEntityEvent(); melee.damager = mob;
    h.onWaveTwoRecoveryFencedEntityDamage(melee);
    check(melee.cancelled, "a fenced fighter cannot damage a player with a direct attack");
    var arrow = new Projectile(); arrow.shooter = mob;
    var projectileHit = new EntityDamageByEntityEvent(); projectileHit.damager = arrow;
    h.onWaveTwoRecoveryFencedEntityDamage(projectileHit);
    check(projectileHit.cancelled, "a projectile from a fenced fighter cannot damage a player");
    h.phase = EventPhase.WAVE_2; h.waveTwoRecoveryFencedEntityUuids.remove(mob.getUniqueId());
    h.ensureEventCombatAi(mob);
    check(mob.ai && mob.aware, "a repaired and rehydrated legitimate fighter can regain AI");
    System.out.println("WaveTwoRecoveryAiFenceProbe OK");
  }
}
'''.replace("__FENCED__", fenced[fenced.index("{") + 1:fenced.rindex("}")])
    probe = probe.replace("__SUSPEND__", suspend[suspend.index("{") + 1:suspend.rindex("}")])
    probe = probe.replace("__GUARD__", guard)
    probe = probe.replace("__DAMAGE__", damage[damage.index("{") + 1:damage.rindex("}")])
    output = Path(__import__("tempfile").mkdtemp(prefix="wave2-recovery-ai-"))
    java = output / "WaveTwoRecoveryAiFenceProbe.java"
    java.write_text(probe, encoding="utf-8")
    policy = DOMAIN / "WaveTwoEntityRecoveryPolicy.java"
    pressure = DOMAIN / "PressureBudgetController.java"
    wave_mechanics = DOMAIN / "WaveMechanicsPolicy.java"
    wave_stages = DOMAIN / "WaveTwoStagePolicy.java"
    wave_hunt = DOMAIN / "WaveTwoHuntPolicy.java"
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java), str(policy), str(pressure), str(wave_mechanics),
                               str(wave_stages), str(wave_hunt)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(output), "WaveTwoRecoveryAiFenceProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
