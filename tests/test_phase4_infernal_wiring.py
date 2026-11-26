"""Phase 4 infernal expansion - the live wiring in the real end-event monolith.

The pure policies are proven in ``test_phase4_infernal_expansion.py``. This file
proves the *server* really consults them: the cold patches decay with health,
the two signature spells are gated once per HP threshold, and the Infernal
Forge's vulnerable window really feeds the boss damage multiplier.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _source() -> str:
    return MONOLITH.read_text(encoding="utf-8")


def test_inferno_cold_patches_decay_with_health():
    source = _source()
    assert "import me.copimine.endevent.domain.InfernoDecayPolicy;" in source
    # The cast chooses reachable, decaying patches instead of the fixed set.
    assert "InfernoDecayPolicy.selectCovering(" in source
    assert "InfernoDecayPolicy.safePatchCount(hpFraction, players)" in source
    assert "InfernoDecayPolicy.MAX_SAFE_SPACING_BLOCKS" in source
    assert "arenaInfernoRelocateIntervalTicks = InfernoDecayPolicy.relocateIntervalTicks(hpFraction)" in source
    # The live ticker relocates on the decay cadence.
    assert "castTick - arenaInfernoLastRelocateTick >= arenaInfernoRelocateIntervalTicks" in source
    assert "private void relocateInfernoSafeCells(" in source
    assert "private double bossHealthFraction(" in source


def test_phase4_signature_spells_gated_once_per_threshold():
    source = _source()
    assert "import me.copimine.endevent.domain.BossSignatureCastGatePolicy;" in source
    assert "import me.copimine.endevent.domain.RiftFireRingPolicy;" in source
    assert "import me.copimine.endevent.domain.InfernalForgePolicy;" in source
    # The main boss tick runs the signature loop.
    assert "tickOverloadSignatureCasts(boss);" in source
    assert "BossSignatureCastGatePolicy.gate(" in source
    assert "OVERLOAD_FIRE_RING_THRESHOLDS" in source
    assert "OVERLOAD_INFERNAL_FORGE_THRESHOLDS" in source
    # Both spells really exist as server implementations.
    assert "private void startRiftFireRing(" in source
    assert "private void startInfernalForge(" in source
    assert "RiftFireRingPolicy.detonates(" in source
    assert "InfernalForgePolicy.impulseConnects(" in source
    assert "InfernalForgePolicy.slamConnects(" in source


def test_forge_vulnerable_window_reaches_the_damage_multiplier():
    source = _source()
    assert "infernalForgeVulnerableUntilTick" in source
    assert "InfernalForgePolicy.VULNERABILITY_DAMAGE_MULTIPLIER" in source
    assert "return InfernalForgePolicy.VULNERABILITY_DAMAGE_MULTIPLIER;" in source


def test_signature_ledgers_retire_on_phase_leave():
    source = _source()
    assert "overloadFireRingSpentThresholds.clear();" in source
    assert "infernalForgeSpentThresholds.clear();" in source


def _signature_thresholds(source: str, name: str) -> list[float]:
    import re

    match = re.search(
        rf"{name}\s*=\s*List\.of\(([^)]*)\)", source
    )
    assert match, f"{name} must stay a real configured threshold list"
    return [float(value.strip().rstrip("D")) for value in match.group(1).split(",")]


def test_signature_thresholds_are_inside_the_overload_band():
    """V7-19: a threshold below the OVERLOAD floor is a silently deleted cast.

    The phase walk moves to RAGE at ``0.30`` (``BossPhase.OVERLOAD`` is
    ``(0.30, 0.45]``). The former ring/forge lists ended at ``0.30``/``0.26``,
    so their second authored set-piece could never fire. Every configured
    threshold must stay strictly inside the band it is gated to.
    """

    source = _source()
    ring = _signature_thresholds(source, "OVERLOAD_FIRE_RING_THRESHOLDS")
    forge = _signature_thresholds(source, "OVERLOAD_INFERNAL_FORGE_THRESHOLDS")
    for name, thresholds in (
        ("ring", ring),
        ("forge", forge),
    ):
        assert len(thresholds) == 2, f"{name} keeps two staggered set-pieces"
        assert thresholds == sorted(thresholds, reverse=True), (
            f"{name} thresholds are listed highest-first"
        )
        for threshold in thresholds:
            assert 0.30 < threshold <= 0.45, (
                f"{name} threshold {threshold} is outside OVERLOAD (0.30, 0.45]"
            )
    # Staggered: the two lists never share an HP window, so the set-pieces
    # alternate instead of landing as one unavoidable combo.
    assert not set(ring) & set(forge), "ring and forge thresholds must be distinct"


def test_signature_cast_cannot_start_mid_inferno():
    """V7-19: magma/vents plus a ring or forge would be an unavoidable combo.

    ``ArenaInfernoPolicy`` forbids a new relocation volley or hard restraint
    while the burning floor is live. The signature ticker must apply the same
    rule and must bail *before* it spends a threshold, so the set-piece is
    deferred to a real gap rather than deleted by the Inferno.
    """

    source = _source()
    start = source.index("private void tickOverloadSignatureCasts(")
    gate_start = source.index("bossAbilityState != BossAbilityState.NONE", start)
    spend_ring = source.index("overloadFireRingSpentThresholds.add(", start)
    guard = source[gate_start:spend_ring]
    assert "!arenaInfernoBlocks.isEmpty()" in guard, (
        "signature ticker must refuse while the Inferno floor is live"
    )
    assert guard.index("!arenaInfernoBlocks.isEmpty()") < len(guard)
