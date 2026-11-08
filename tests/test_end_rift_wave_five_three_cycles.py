"""Wave 5 runs exactly three fog cycles, matching the approved contract.

The approved Wave 5 contract is three cycles of roughly 40, 50 and 60 seconds.
The shipped code still carried five-cycle constants from an older decision, so
a party that survived the approved encounter was asked to survive two more
cycles and the wave overran its length. Tests that asserted the old count were
updated with the fix; this file pins the contract itself so the two constants
cannot drift apart again.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def read(name: str) -> str:
    return (DOMAIN / name).read_text(encoding="utf-8")


def test_the_timing_policy_runs_three_cycles():
    text = read("BlackFogTimingPolicy.java")
    assert "public static final int CYCLE_COUNT = 3;" in text, (
        "the approved Wave 5 is three cycles")
    assert "CYCLE_COUNT = 5" not in text


def test_the_combat_windows_are_the_approved_forty_fifty_sixty():
    text = read("BlackFogTimingPolicy.java")
    assert "COMBAT_SECONDS = {40, 50, 60}" in text, (
        "the three approved cycles are 40, 50 and 60 seconds of combat")


def test_the_objective_reads_the_cycle_count_from_the_policy():
    text = read("EndRiftObjective.java")
    assert "REQUIRED_FOG_CYCLES = BlackFogTimingPolicy.CYCLE_COUNT;" in text, (
        "REQUIRED_FOG_CYCLES was a separate literal; when it disagreed with the "
        "timing policy the wave both overran and reported the wrong progress")
    assert "REQUIRED_FOG_CYCLES = 5" not in text


def test_no_production_constant_still_says_five_cycles():
    offenders = []
    for path in DOMAIN.glob("*.java"):
        text = path.read_text(encoding="utf-8")
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if "CYCLE_COUNT = 5" in stripped or "REQUIRED_FOG_CYCLES = 5" in stripped:
                offenders.append(f"{path.name}: {stripped}")
    assert not offenders, f"five-cycle constants survive: {offenders}"


def test_the_safe_zone_still_shrinks_within_three_cycles():
    text = read("BlackFogSafeZonePolicy.java")
    assert "cycleIndex >= BlackFogTimingPolicy.CYCLE_COUNT" in text, (
        "the safe-zone policy must spend the same cycle budget as the timing policy")
