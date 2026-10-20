from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
EVENT_SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
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
    raise AssertionError(f"unclosed method: {signature}")


def test_official_named_echo_tick_routes_through_scoped_finite_combat_adapter():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    tick = method_body(source, "private boolean tickNamedEchoDuel(")
    configure = method_body(source, "private boolean configureNamedEchoRuntime(")

    assert "EchoDuelCombatRuntimeAdapter.tick(" in tick
    assert "Bukkit.getMobGoals().removeAllGoals(carrier)" in configure
    assert "carrier.setTarget(null)" in tick
    assert "carrier.setTarget(owner)" not in tick
    assert "EchoReplicaInventory.restore(" in tick
    assert "namedEchoReplicas.put(carrier.getUniqueId(), replica)" in tick
    assert "commitNamedEchoCombatAction(" in tick
    assert "if (decision.isEmpty())" in tick
    assert "current.replicaState()" in tick


def test_official_echo_compensates_rejected_native_effects_after_durable_cost_checkpoint():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    commit = method_body(source, "private boolean commitNamedEchoCombatAction(")
    execute = method_body(source, "private boolean executeNamedEchoCombatAction(")

    register = commit.index("registerPersonalEchoDuel(")
    persist = commit.index("saveStateSync()")
    execute_at = commit.index("executeNamedEchoCombatAction(")
    assert register < persist < execute_at
    assert "compensateRejectedNamedEchoCombatAction" in commit
    compensate = method_body(source, "private boolean compensateRejectedNamedEchoCombatAction(")
    assert "rejectedActionCheckpoint(decision)" in compensate
    assert "registerPersonalEchoDuel(refunded)" in compensate
    assert "saveStateSync()" in compensate
    assert "beginOwnerCombatAction" in execute
    assert "consumeAcceptedOwnerCombatAction" in execute
    assert "discardServerLaunchedEchoArrow" in execute
    assert "arrow.isValid()" in execute and "velocity.lengthSquared()" in execute
    assert "carrier.getHealth(), expectedHealth" in execute


def test_official_echo_persists_bounded_jump_critical_and_finite_owner_shield():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    tick = method_body(source, "private boolean tickNamedEchoDuel(")
    execute = method_body(source, "private boolean executeNamedEchoCombatAction(")
    block = method_body(source, "private boolean blockNamedEchoOwnerHit(")
    sync = method_body(source, "private boolean synchronizeNamedEchoShield(")
    adapter_source = (ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/wave7/EchoDuelCombatRuntimeAdapter.java").read_text(encoding="utf-8")
    listener_source = (ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/wave7/EchoCombatAdmissionListener.java").read_text(encoding="utf-8")
    on_damage = method_body(listener_source, "public void onDamage(")

    assert "carrier.isOnGround()" in tick and "isNamedEchoCriticalWindow(carrier)" in tick
    assert "synchronizeNamedEchoShield(carrier, current, replica)" in tick
    assert "Action.CRITICAL" in execute and "attack.getValue() * (critical ? 1.5D : 1.0D)" in execute
    assert "carrier.setVelocity(jump)" in execute
    assert "startUsingItem(EquipmentSlot.OFF_HAND)" in execute
    assert "shieldActiveUntilTick()" in sync and "carrier.clearActiveItem()" in sync
    assert "tryStartJump" in adapter_source and "actor.jumpCooldownUntilTick()" in adapter_source

    policy = block.index("EchoDuelCombatPolicy.tryBlockWithShield(")
    register = block.index("registerPersonalEchoDuel(next)")
    persist = block.index("saveStateSync()")
    assert policy < register < persist
    assert "namedEchoOwnerFacesAttack" in block
    assert "incomingOwnerHitHandler.block(context, byEntity, direct, causing)" in on_damage
    assert on_damage.index("allowed(context, direct") < on_damage.index("incomingOwnerHitHandler.block")


def test_echo_health_checkpoint_batch_saves_once_for_many_changes_and_skips_empty_batch(tmp_path):
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    reconcile = method_body(source, "private void tickRealitySplitTrialRuntimes(")
    collect = method_body(source, "private boolean checkpointNamedEchoHealthChanges(")
    tick_duel = method_body(source, "private boolean tickNamedEchoDuel(")
    checkpoint = method_body(source, "private boolean persistNamedEchoHealthCheckpoints(")

    batch_at = reconcile.index("checkpointNamedEchoHealthChanges(namedWorld, namedCore, trial, cells, duels)")
    refresh_at = reconcile.index("duels = realitySplitTrialController.namedSnapshot()", batch_at)
    duel_tick_at = reconcile.index("tickNamedEchoDuel(namedWorld, namedCore, trial, cell, duel)", refresh_at)
    assert batch_at < refresh_at < duel_tick_at
    assert collect.count("persistNamedEchoHealthCheckpoints(updates)") == 1
    assert "updates.add(duel.withActor(" in collect
    assert "saveStateSync()" not in collect
    assert "carrier.getHealth() != duel.actor().currentHealth()" not in tick_duel
    # Rebinds still capture the live HP synchronously before the duel can resume.
    assert "if (!saveStateSync()) return false;" in tick_duel

    package_dir = tmp_path / "me" / "copimine" / "endevent" / "runtime"
    package_dir.mkdir(parents=True)
    duel_state = package_dir / "RealitySplitEchoDuelState.java"
    duel_state.write_text('''
package me.copimine.endevent.runtime;
public final class RealitySplitEchoDuelState {
    public record DuelRecord(int value) { }
}
''', encoding="utf-8")

    probe = tmp_path / "EchoHealthCheckpointBatchProbe.java"
    probe.write_text('''
import java.util.List;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;

public final class EchoHealthCheckpointBatchProbe {
    static final class Controller {
        int registrations;
        int rejectAt = -1;
        boolean registerPersonalEchoDuel(DuelRecord update) {
            registrations++;
            return registrations != rejectAt;
        }
    }
    final Controller realitySplitTrialController = new Controller();
    int saves;
    boolean saveStateSync() { saves++; return true; }

''' + checkpoint + '''
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var changed = new EchoHealthCheckpointBatchProbe();
        require(changed.persistNamedEchoHealthCheckpoints(List.of(new DuelRecord(1), new DuelRecord(2))),
                "changed batch should checkpoint successfully");
        require(changed.realitySplitTrialController.registrations == 2 && changed.saves == 1,
                "two owner health changes must produce one synchronous snapshot");

        var unchanged = new EchoHealthCheckpointBatchProbe();
        require(unchanged.persistNamedEchoHealthCheckpoints(List.of()),
                "empty batch should be a no-op success");
        require(unchanged.realitySplitTrialController.registrations == 0 && unchanged.saves == 0,
                "unchanged health must not write a snapshot");

        var rejected = new EchoHealthCheckpointBatchProbe();
        rejected.realitySplitTrialController.rejectAt = 2;
        require(!rejected.persistNamedEchoHealthCheckpoints(List.of(new DuelRecord(1), new DuelRecord(2))),
                "failed registration must reject the batch");
        require(rejected.saves == 0, "partial checkpoint registration must not be persisted");
        System.out.println("EchoHealthCheckpointBatchProbe OK");
    }
}
''', encoding="utf-8")

    compile = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(duel_state), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compile.returncode == 0, compile.stdout + compile.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "EchoHealthCheckpointBatchProbe"],
        capture_output=True,
        text=True,
    )
    assert run.returncode == 0, run.stdout + run.stderr

