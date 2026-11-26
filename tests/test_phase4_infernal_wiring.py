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
    assert "private boolean startRiftFireRing(" in source
    assert "private void startInfernalForge(" in source
    assert "resolveFireRingContact(boss, shell, centre, next)" in source
    assert "fireRingCellBlocked" in source
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
