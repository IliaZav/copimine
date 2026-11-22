"""The LAST_SEAL arena ritual must be large, bounded and presentation-only.

The final phase used to be announced by two small rings around the boss itself,
so the biggest fight in the encounter looked smaller than the earlier ones. The
ritual is now driven by `LastSealArenaRitualPolicy`, whose geometry is proven in
`LastSealArenaRitualPolicyTest`. This probe covers the wiring the policy test
cannot see: the monolith must actually draw the arena-scale layers, must do it
through the bounded pattern helpers, and must emit the one-shot ascension read
exactly on LAST_SEAL entry.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
MONOLITH = SRC / "CopiMineEndEvent.java"
POLICY = SRC / "domain" / "LastSealArenaRitualPolicy.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    depth = 0
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_the_arena_ritual_is_driven_by_the_policy():
    monolith = MONOLITH.read_text("utf-8")
    visuals = _method(monolith, "private void startCurrentLastSealVisuals(")
    # The heavy arena layers redraw on the policy's slower cadence...
    assert "LastSealArenaRitualPolicy.ritualDue(" in visuals, (
        "the arena ritual must run on the policy cadence, not every tick"
    )
    assert "renderLastSealArenaRitual(" in visuals, (
        "the arena-scale layers must actually be drawn"
    )
    helper = _method(monolith, "private void renderLastSealArenaRitual(")
    # ...and each layer reads its geometry from the shared policy.
    for symbol in ("sealRadius(", "sealPhase(", "sigilRadius(", "sigilPhase(",
                   "spikeAngle(", "spikeHeight(", "pillarAngle("):
        assert f"LastSealArenaRitualPolicy.{symbol}" in helper, (
            f"the ritual must derive {symbol} from the policy"
        )


def test_the_arena_ritual_is_bounded_and_presentation_only():
    monolith = MONOLITH.read_text("utf-8")
    helper = _method(monolith, "private void renderLastSealArenaRitual(")
    # It reuses the bounded, distance- and audience-gated helpers instead of
    # spawning a raw particle per point.
    assert "spawnPatternRing(" in helper and "spawnPatternSegment(" in helper, (
        "the ritual must draw through the bounded pattern helpers"
    )
    for forbidden in ("damage(", "setHealth", "spawn(", "runTask", "createExplosion"):
        assert forbidden not in helper, f"the visual helper must not call {forbidden}"


def test_the_ascension_burst_is_emitted_once_on_last_seal_entry():
    monolith = MONOLITH.read_text("utf-8")
    entry = monolith.index("transition.entered().contains(BossPhase.LAST_SEAL)")
    block = monolith[entry : entry + 1200]
    assert "startCurrentLastSealVisuals(boss)" in block, (
        "the entry must still start the repeating ritual"
    )
    assert "spawnLastSealAscensionBurst(boss)" in block, (
        "the entry must fire the one-shot ascension read"
    )
    burst = _method(monolith, "private void spawnLastSealAscensionBurst(")
    # One-shot: it must not schedule a repeating task of its own.
    assert "runTask" not in burst, "the ascension read is a single emission, not a task"
    assert "END_ROD" in burst and "GROWL" in burst, (
        "the ascension read must lift a spine to the boss and roar"
    )


def test_the_policy_stays_a_bounded_pure_domain_class():
    policy = POLICY.read_text("utf-8")
    # Pure: the whole arena shape is exercised by a plain Java test.
    for forbidden in ("org.bukkit", "import org.", "Bukkit."):
        assert forbidden not in policy, f"the policy must stay Bukkit-free: {forbidden}"
    assert "SEAL_MAX_RADIUS" in policy and "SPIKE_MAX_HEIGHT" in policy, (
        "the ritual needs explicit finite ceilings"
    )
