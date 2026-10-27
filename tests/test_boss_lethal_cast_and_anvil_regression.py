"""Static integration guards for the real boss entry, chest scheduling, and Wave 6 anvil placement.

Other tests execute the underlying Java policy. These assert the production
adapter actually wires policy to the gameplay boundary.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start:cursor + 1]
    raise AssertionError(f"Method not terminated: {signature}")


def test_official_lethal_hit_activates_last_seal_guardians_before_any_victory():
    method = _method(SERVER.read_text(encoding="utf-8"), "private void handleOfficialLethalHit(")
    assert "ensurePermanentTentacles(boss," in method, (
        "An overpowered hit must create/validate final defenders before deciding on defeat"
    )
    assert method.index("synchronizeBossPhase(boss);") < method.index("ensurePermanentTentacles(boss,")
    assert method.index("ensurePermanentTentacles(boss,") < method.index("commitOfficialBossDefeat(boss, source);")
    assert "tentacleGuardianShielded()" in method
    assert "last-seal-entry" in method


def test_chest_projectile_callbacks_are_fenced_to_their_own_cast():
    source = SERVER.read_text(encoding="utf-8")
    launch = _method(source, "private void startChestStrike(")
    cancelled = _method(source, "private void cancelBossCastTask()")
    assert "bossChestStrikeSequence" in launch
    assert "bossChestStrikeSequence != castSequence" in launch
    assert "bossChestStrikeSequence++" in cancelled


def test_nine_anvils_are_centered_per_player_without_evicting_overlaps():
    source = SERVER.read_text(encoding="utf-8")
    placement = _method(source, "private AnvilCluster lockFallingAnvilCluster(int participants, Location avoidCenter)")
    assert "safeLandingGrid(target.getLocation(), 3, 0, 0)" in placement
    assert "occupiedColumns" not in placement
    assert "centerOffsets" not in placement
    assert "if (selectedGrid == null) return null;" in placement
