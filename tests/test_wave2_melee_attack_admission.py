"""Wave 2 ordinary melee needs its own admission path after squad targeting."""

from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
COORDINATOR = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/WaveCombatCoordinator.java"
CLIENT_INPUT_MIXIN = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/mixin/KeyboardInputPrisonerMixin.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated production method: {signature}")


def test_wave2_direct_bukkit_melee_has_separate_higher_and_monitor_admission_hooks() -> None:
    source = EVENT.read_text(encoding="utf-8")
    admission_offset = source.index(
        "public void onWaveTwoMeleeAttackAdmission(EntityDamageByEntityEvent event)")
    result_offset = source.index(
        "public void onWaveTwoMeleeAttackResult(EntityDamageByEntityEvent event)")
    admission = _method(source,
                       "public void onWaveTwoMeleeAttackAdmission(EntityDamageByEntityEvent event)")
    result = _method(source,
                     "public void onWaveTwoMeleeAttackResult(EntityDamageByEntityEvent event)")

    assert "priority = EventPriority.HIGHEST" in source[max(0, admission_offset - 180):admission_offset]
    assert "event.getDamager() instanceof Mob attacker" in admission
    assert "event.getEntity() instanceof Player target" in admission
    assert "isWaveTwoOrdinaryMob(attacker)" in admission
    assert "readEntityGeneration(attacker) != generation" in admission
    assert "waveCombatCoordinator.reserveMeleeAttack" in admission
    assert "event.setCancelled(true)" in admission
    assert "priority = EventPriority.MONITOR" in source[max(0, result_offset - 180):result_offset]
    assert "pendingWaveTwoMeleeAdmissions.remove(event)" in result
    assert "waveCombatCoordinator.finishMeleeAttack" in result
    assert "ignoreCancelled = true" in source[max(0, admission_offset - 180):admission_offset]
    assert "ownedEntities.get(attacker.getUniqueId()) != attacker" in admission
    assert "new WaveTwoMeleeAttackEventToken(null, false)" in admission
    assert "new WaveTwoMeleeAttackEventToken(admitted, true)" in admission
    assert "token.admitted() && !event.isCancelled()" in result
    assert "waveCombatCoordinator.cancelTarget(target)" in _method(
        source, "private void cancelWaveCombatTarget(UUID target)")
    for handler in (
        "public void onPlayerQuit(PlayerQuitEvent event)",
        "public void onPlayerDeath(PlayerDeathEvent event)",
        "public void onPlayerChangedWorld(PlayerChangedWorldEvent event)",
    ):
        assert "cancelWaveCombatTarget(uuid)" in _method(source, handler)


def test_wave2_mark_inverts_directional_input_only_while_reverse_mark_is_active() -> None:
    mixin = CLIENT_INPUT_MIXIN.read_text(encoding="utf-8")
    assert "WaveTwoControlInputPolicy.applyIfReverseActive(" in mixin
    assert "ClientBridgeProtocol.endEventState()" in mixin
    assert "this.keyPresses = applied.keyPresses()" in mixin
    assert "this.moveVector = applied.moveVector()" in mixin
    for camera_rotation_write in ("setYRot(", "setXRot(", "turn(", "yRot =", "xRot ="):
        assert camera_rotation_write not in mixin

    protocol = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocol.java").read_text(
        encoding="utf-8")
    assert "resetEndEventConnection();" in _method(protocol, "public static void onJoin(UUID playerUuid)")
    assert "resetEndEventConnection();" in _method(protocol, "public static void onDisconnect()")
    assert "END_EVENT_STATE.clear();" in _method(protocol, "private static void resetEndEventConnection()")


@pytest.fixture(scope="module")
def melee_adapter_harness(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    methods = [
        _method(source,
                "public void onWaveTwoMeleeAttackAdmission(EntityDamageByEntityEvent event)"),
        _method(source,
                "public void onWaveTwoMeleeAttackResult(EntityDamageByEntityEvent event)"),
        _method(source, "private boolean isWaveTwoOrdinaryMob(Entity entity)"),
    ]
    harness = r'''
import java.util.*;
import me.copimine.endevent.runtime.WaveCombatCoordinator;

public final class WaveTwoMeleeAdmissionHarness {
    enum EventPhase { WAVE_2, READY_FOR_PLAYERS, RECOVERY_REQUIRED }
    enum EventPriority { HIGHEST, MONITOR }
    @interface EventHandler { EventPriority priority(); boolean ignoreCancelled() default false; }
    static final String EVENT_KIND_WAVE_MOB = "wave_mob", EVENT_KIND_ELITE = "elite";
    static class Entity {
        final UUID id = UUID.randomUUID();
        final Map<Object,Object> values = new HashMap<>();
        final Map<Object,Integer> ints = new HashMap<>();
        final Map<Object,Long> longs = new HashMap<>();
        boolean valid = true, dead;
        UUID getUniqueId() { return id; }
        boolean isValid() { return valid; }
        boolean isDead() { return dead; }
    }
    static final class Player extends Entity { boolean online = true; }
    static class Mob extends Entity {
        Object target;
        Object getTarget() { return target; }
    }
    static final class Skeleton extends Mob {}
    static final class EntityDamageByEntityEvent {
        final Entity damager, entity;
        boolean cancelled;
        EntityDamageByEntityEvent(Entity damager, Entity entity) {
            this.damager = damager; this.entity = entity;
        }
        Entity getDamager() { return damager; }
        Entity getEntity() { return entity; }
        boolean isCancelled() { return cancelled; }
        void setCancelled(boolean value) { cancelled = value; }
    }
    record WaveTwoMeleeAttackEventToken(
            WaveCombatCoordinator.MeleeAttackLease lease, boolean admitted) {}
    final Object keyWave = new Object(), keyKind = new Object(), keyGeneration = new Object();
    final Object keyWaveTwoFinalElite = new Object();
    final Object keyWaveSpawnGroup = new Object();
    String readString(Entity entity, Object key) {
        Object value = entity.values.get(key); return value instanceof String s ? s : "";
    }
    int readInt(Entity entity, Object key, int fallback) {
        return entity.ints.getOrDefault(key, fallback);
    }
    long readEntityGeneration(Entity entity) {
        return entity.longs.getOrDefault(keyGeneration, Long.MIN_VALUE);
    }
    boolean isApprovedWaveTwoFinalElite(Entity entity) {
        return entity instanceof Skeleton
                && "group-3-skeleton-v1".equals(readString(entity, keyWaveTwoFinalElite))
                && readInt(entity, keyWaveSpawnGroup, 0) == 3;
    }
    EventPhase phase = EventPhase.WAVE_2;
    boolean testWaveFrontVisualMode;
    int activeWave = 2;
    long generation = 8L, eventTickCounter = 100L;
    final Map<UUID,Entity> ownedEntities = new HashMap<>();
    final Map<EntityDamageByEntityEvent, WaveTwoMeleeAttackEventToken>
            pendingWaveTwoMeleeAdmissions = new IdentityHashMap<>();
    final WaveCombatCoordinator waveCombatCoordinator = new WaveCombatCoordinator();
    List<Player> participants = new ArrayList<>();
    final Set<UUID> recoveryFenced = new HashSet<>();
    boolean isWaveTwoRecoveryFenced(Entity entity) {
        return entity != null && readInt(entity, keyWave, 0) == 2
                && (phase == EventPhase.RECOVERY_REQUIRED || recoveryFenced.contains(entity.getUniqueId()));
    }
    boolean isWaveTargetAllowed(Entity entity, Player player) {
        return player != null && player.online && participants.stream()
                .anyMatch(candidate -> candidate.getUniqueId().equals(player.getUniqueId()));
    }
    List<Player> activeWaveParticipants() { return participants; }
    Mob mob(Player target) {
        return mob(target, generation);
    }
    Mob mob(Player target, long mobGeneration) {
        Mob mob = new Mob(); mob.ints.put(keyWave, 2); mob.values.put(keyKind, EVENT_KIND_WAVE_MOB);
        mob.longs.put(keyGeneration, mobGeneration);
        mob.target = target; ownedEntities.put(mob.id, mob); return mob;
    }
    EntityDamageByEntityEvent hit(Mob mob, Player player) {
        return new EntityDamageByEntityEvent(mob, player);
    }
    __METHODS__
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    void apply(EntityDamageByEntityEvent event) {
        onWaveTwoMeleeAttackAdmission(event);
    }
    void resolve(EntityDamageByEntityEvent event) { onWaveTwoMeleeAttackResult(event); }
    public static void main(String[] args) {
        var h = new WaveTwoMeleeAdmissionHarness();
        switch (args[0]) {
            case "two-player-target-cap" -> {
                Player marked = new Player(), ally = new Player(); h.participants = List.of(marked, ally);
                h.waveCombatCoordinator.begin(h.generation);
                var fighters = new ArrayList<Mob>();
                for (int index = 0; index < 7; index++) fighters.add(h.mob(marked));
                int allowed = 0, blocked = 0;
                for (Mob fighter : fighters) {
                    var event = h.hit(fighter, marked); h.apply(event);
                    if (event.cancelled) blocked++; else allowed++;
                    var token = h.pendingWaveTwoMeleeAdmissions.get(event);
                    check(token != null && token.admitted() == !event.cancelled,
                            "every processed ordinary melee event has its own accepted or rejected token");
                    h.resolve(event);
                }
                check(allowed == 2 && blocked == 5,
                        "seven stable squad members may route to one target while only two direct hits are admitted");
                h.eventTickCounter = 119L;
                var before = h.hit(fighters.get(2), marked); h.apply(before);
                check(before.cancelled, "accepted hits keep the target pressure cap for the full attack cooldown");
                h.eventTickCounter = 120L;
                var expired = h.hit(fighters.get(2), marked); h.apply(expired);
                check(!expired.cancelled, "the 20-tick direct melee window expires at its boundary");
                h.resolve(expired);
            }
            case "five-player-scaling" -> {
                h.participants = new ArrayList<>();
                for (int index = 0; index < 5; index++) h.participants.add(new Player());
                h.waveCombatCoordinator.begin(h.generation);
                int allowed = 0, blocked = 0;
                for (Player player : h.participants) {
                    for (int index = 0; index < 3; index++) {
                        var event = h.hit(h.mob(player), player); h.apply(event);
                        if (event.cancelled) blocked++; else allowed++;
                        h.resolve(event);
                    }
                }
                check(allowed == 10 && blocked == 5,
                        "five players receive ten simultaneous admitted hits with one blocked excess per player");
            }
            case "cancelled-hit-releases-slot" -> {
                Player target = new Player(), ally = new Player(); h.participants = List.of(target, ally);
                h.waveCombatCoordinator.begin(h.generation);
                Mob first = h.mob(target), second = h.mob(target), third = h.mob(target);
                var cancelled = h.hit(first, target); h.apply(cancelled);
                check(!cancelled.cancelled, "the first eligible melee event receives a tentative lease");
                cancelled.setCancelled(true); h.resolve(cancelled);
                var replacement = h.hit(second, target); h.apply(replacement);
                check(!replacement.cancelled, "a later plugin cancellation releases the tentative slot at MONITOR");
                h.resolve(replacement);
            var secondAccepted = h.hit(third, target); h.apply(secondAccepted);
            check(!secondAccepted.cancelled, "the target can still receive its second admitted hit");
            h.resolve(secondAccepted);
            Mob fourth = h.mob(target);
            var capped = h.hit(fourth, target); h.apply(capped);
                check(capped.cancelled, "accepted replacement damage retains its cooldown lease");
            }
            case "unregistered-pre-cancelled-and-mismatched-do-not-consume" -> {
                Player firstTarget = new Player(), secondTarget = new Player();
                h.participants = List.of(firstTarget, secondTarget); h.waveCombatCoordinator.begin(h.generation);
                Mob unregistered = new Mob(); unregistered.ints.put(h.keyWave, 2);
                unregistered.values.put(h.keyKind, EVENT_KIND_WAVE_MOB); unregistered.target = firstTarget;
                var unregisteredHit = h.hit(unregistered, firstTarget); h.apply(unregisteredHit);
                check(!unregisteredHit.cancelled && h.pendingWaveTwoMeleeAdmissions.isEmpty(),
                        "unregistered damage does not reserve a melee admission");
                Mob assignedElsewhere = h.mob(firstTarget);
                var mismatch = h.hit(assignedElsewhere, secondTarget); h.apply(mismatch);
                check(mismatch.cancelled, "a direct hit cannot land on a player outside the mob's assigned target");
                check(h.pendingWaveTwoMeleeAdmissions.containsKey(mismatch)
                                && !h.pendingWaveTwoMeleeAdmissions.get(mismatch).admitted(),
                        "a rejected tracked event gets a non-reserving result token");
                h.resolve(mismatch);
                Player outsider = new Player();
                var ineligible = h.hit(h.mob(outsider), outsider); h.apply(ineligible);
                check(ineligible.cancelled && h.pendingWaveTwoMeleeAdmissions.containsKey(ineligible)
                                && !h.pendingWaveTwoMeleeAdmissions.get(ineligible).admitted(),
                        "a non-participant receives a rejected event token without reserving a slot");
                h.resolve(ineligible);
                var priorCancelled = h.hit(h.mob(firstTarget), firstTarget);
                priorCancelled.setCancelled(true); h.apply(priorCancelled); h.resolve(priorCancelled);
                check(h.pendingWaveTwoMeleeAdmissions.isEmpty(),
                        "an event canceled before HIGHEST cannot reserve or leak a slot");
                Mob sameAttacker = h.mob(firstTarget);
                var allowedOne = h.hit(sameAttacker, firstTarget); h.apply(allowedOne);
                var duplicate = h.hit(sameAttacker, firstTarget); h.apply(duplicate);
                check(duplicate.cancelled && h.pendingWaveTwoMeleeAdmissions.containsKey(duplicate)
                                && !h.pendingWaveTwoMeleeAdmissions.get(duplicate).admitted(),
                        "duplicate direct events from one attacker are rejected without a second lease");
                h.resolve(duplicate);
                var allowedTwo = h.hit(h.mob(firstTarget), firstTarget); h.apply(allowedTwo);
                check(!allowedOne.cancelled && !allowedTwo.cancelled,
                        "unregistered, mismatched, or already-cancelled events do not consume the two valid slots");
                h.resolve(allowedOne); h.resolve(allowedTwo);
            }
            case "recovery-fence-and-disposable-wave" -> {
                Player target = new Player(), ally = new Player(); h.participants = List.of(target, ally);
                h.waveCombatCoordinator.begin(h.generation);
                Mob fenced = h.mob(target); h.recoveryFenced.add(fenced.id);
                var rejected = h.hit(fenced, target); h.apply(rejected);
                check(rejected.cancelled, "recovery-fenced direct melee is rejected before damage");
                h.phase = EventPhase.READY_FOR_PLAYERS; h.testWaveFrontVisualMode = true;
                Mob local = h.mob(target); var localHit = h.hit(local, target); h.apply(localHit);
                check(!localHit.cancelled, "the disposable Wave 2 test arena keeps its real melee path");
            }
            case "stale-generation-attacker" -> {
                Player target = new Player(), ally = new Player(); h.participants = List.of(target, ally);
                h.waveCombatCoordinator.begin(h.generation);
                Mob stale = h.mob(target, h.generation - 1L);
                var staleHit = h.hit(stale, target); h.apply(staleHit);
                check(staleHit.cancelled,
                        "a tracked Wave 2 mob stamped by an older generation cannot hit a current participant");
                var staleToken = h.pendingWaveTwoMeleeAdmissions.get(staleHit);
                check(staleToken != null && !staleToken.admitted(),
                        "a stale-generation rejection carries a non-reserving event token");
                h.resolve(staleHit);
                var currentFirst = h.hit(h.mob(target), target); h.apply(currentFirst);
                var currentSecond = h.hit(h.mob(target), target); h.apply(currentSecond);
                check(!currentFirst.cancelled && !currentSecond.cancelled,
                        "the stale actor did not consume either current-generation melee slot");
                h.resolve(currentFirst); h.resolve(currentSecond);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.out.println(args[0] + " OK");
    }
}
'''.replace("__METHODS__", "\n".join(methods))
    output = tmp_path_factory.mktemp("wave2-melee-admission")
    java = output / "WaveTwoMeleeAdmissionHarness.java"
    java.write_text(harness, encoding="utf-8")
    compiled = subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(output),
                               str(java), str(COORDINATOR)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return output


@pytest.mark.parametrize("scenario", [
    "two-player-target-cap", "five-player-scaling", "cancelled-hit-releases-slot",
    "unregistered-pre-cancelled-and-mismatched-do-not-consume",
    "recovery-fence-and-disposable-wave", "stale-generation-attacker",
])
def test_wave2_bukkit_melee_admission_adapter(melee_adapter_harness, scenario):
    result = subprocess.run(["java", "-cp", str(melee_adapter_harness),
                             "WaveTwoMeleeAdmissionHarness", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
