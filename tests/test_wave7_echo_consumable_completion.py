"""Wave 7 Echo: the eat START stages no heal, the completion does.

``EchoDuelCombatPolicy`` heals a copy only when a consumable use finishes
(``completeConsumable``), and the adapter reports that finishing tick as
``Action.NONE`` because the supply was already spent at the START. The runtime
must therefore apply the heal on that ``NONE`` state change and must NOT demand
a health increase when the eat begins.

The two halves of this contract had drifted apart: after the policy moved the
heal to completion the runtime still required ``nextDuel.currentHealth() >
carrier.getHealth()`` for the START (rejecting every eat, because the health
checkpoint already matched the body) and short-circuited the ``Action.NONE``
completion without persisting it (dropping the heal). ``EchoConsumableTimingTest``
and ``EchoDuelCombatRuntimeAdapterTest`` prove the policy/adapter side with real
Java; these assertions pin the runtime wiring, because no unit test can load the
Bukkit class.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = (
    ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
    / "CopiMineEndEvent.java"
)


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
                return source[start : index + 1]
    raise AssertionError(f"unclosed method: {signature}")


def test_a_none_tick_routes_through_the_state_change_handler():
    source = EVENT.read_text(encoding="utf-8")
    commit = _method(source, "private boolean commitNamedEchoCombatAction(")

    # A NONE decision is no longer dropped before the handler: the completion
    # heal lives in nextDuel and would be lost by an early return.
    assert "if (decision.action() == EchoDuelCombatRuntimeAdapter.Action.NONE) {" in commit
    assert "return commitNamedEchoStateChange(owner, carrier, decision);" in commit
    # The old blanket short-circuit that swallowed the completion heal is gone.
    assert "return decision != null && decision.action() == EchoDuelCombatRuntimeAdapter.Action.NONE;" not in commit


def test_the_completion_handler_persists_and_projects_the_heal():
    source = EVENT.read_text(encoding="utf-8")
    handler = _method(source, "private boolean commitNamedEchoStateChange(")

    # An ordinary idle tick (same record before and after) stays a no-op.
    assert "if (next.equals(prior)) return true;" in handler
    # The healed actor is registered and made durable before it is projected.
    register = handler.index("registerPersonalEchoDuel(next)")
    persist = handler.index("saveStateSync()")
    heal = handler.index("carrier.setHealth(restored)")
    assert register < persist < heal
    # The heal only ever raises the body toward the completed checkpoint.
    assert "restored <= carrier.getHealth()" in handler
    assert "restored > carrier.getMaxHealth()" in handler
    # A body that refuses the heal must not roll the completed use back.
    assert 'getLogger().fine("W7 echo completion heal refused: "' in handler


def test_the_eat_start_does_not_demand_a_health_increase():
    source = EVENT.read_text(encoding="utf-8")
    execute = _method(source, "private boolean executeNamedEchoCombatAction(")
    consumable = execute[
        execute.index("case CONSUMABLE -> {") : execute.index("case SHIELD -> {")
    ]

    # The start of the use carries no heal, so a guard that rejects an equal
    # health value rejects every legitimate start.
    assert "expectedHealth <= carrier.getHealth()" not in consumable
    assert "expectedHealth > carrier.getHealth()" in consumable
    # It still defends the two real invariants: finite and never above maximum.
    assert "!Double.isFinite(expectedHealth)" in consumable
    assert "expectedHealth > carrier.getMaxHealth()" in consumable
    # The buff potion stays an animation, not a heal, on this same action.
    assert "return executeNamedEchoPotionDrink(owner, carrier, decision);" in consumable
