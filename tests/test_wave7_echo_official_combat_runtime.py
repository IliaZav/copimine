from pathlib import Path


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

