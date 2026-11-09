"""Wave 5 escalation is bounded by the CURRENT contract: five to ten fog cycles.

The earlier approved Wave 5 was three cycles of roughly 40, 50 and 60 seconds. That
is no longer the requirement. The latest direction raises the encounter to between
five and ten activations, shrinking the safe zones and the run window as the wave
progresses and lengthening the fog itself, so a party cannot end the wave early by
surviving a single curtain.

The risk this file guards against is the same as before: a participant count, a
timing policy and an objective requirement drifting apart, so the wave reports
progress against one budget while the runtime spends another. The assertions below
pin the SHAPE of that budget rather than one frozen number, because the count is now
a function of the roster.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def read(name: str) -> str:
    return (DOMAIN / name).read_text(encoding="utf-8")


def test_the_timing_policy_is_five_to_ten_cycles():
    text = read("BlackFogTimingPolicy.java")
    assert "public static final int MIN_CYCLES = 5;" in text, (
        "the current Wave 5 contract starts at five fog cycles")
    assert "public static final int CYCLE_COUNT = 10;" in text, (
        "the protocol/recovery upper bound is ten fog cycles")
    # The retired three-cycle scheme must not creep back in as a live constant.
    assert "CYCLE_COUNT = 3;" not in text
    assert "MIN_CYCLES = 3;" not in text


def test_the_per_party_cycle_count_stays_inside_the_contract_window():
    text = read("BlackFogTimingPolicy.java")
    assert "MIN_CYCLES + (players - 2 + 3) / 4" in text, (
        "the cycle count is scaled from the roster, not frozen at one number")
    # The window is the requirement: no party may be asked for fewer than five or
    # more than ten activations.
    assert "MIN_CYCLES, Math.min(CYCLE_COUNT, targetCycles)" in text, (
        "every target has to be clamped into [MIN_CYCLES, CYCLE_COUNT]")


def test_the_safe_zones_shrink_as_the_wave_progresses():
    text = read("BlackFogTimingPolicy.java")
    # Fewer shelters and less time to reach them, both driven by progress. A wave
    # that kept its opening grace to the last cycle would not escalate at all.
    assert "SAFE_ZONE_SECONDS - (int) Math.floor(3 * progress(cycle, targetCycles))" in text, (
        "the run window has to shorten across the wave")
    assert "initial - (int) Math.floor((initial - last) * progress(cycle, cycleCount(players)))" in text, (
        "the number of shelters has to fall as the wave progresses")


def test_the_fog_outlasts_the_old_ten_second_curtain():
    text = read("BlackFogTimingPolicy.java")
    assert "public static final int FOG_SECONDS = 18;" in text, (
        "the fog is longer than the retired ten-second curtain")
    assert "FOG_SECONDS + (int) Math.round(8 * progress(cycle, targetCycles))" in text, (
        "the fog has to lengthen as the wave progresses")


def test_the_objective_reads_the_cycle_budget_from_the_policy():
    text = read("EndRiftObjective.java")
    assert "REQUIRED_FOG_CYCLES = BlackFogTimingPolicy.CYCLE_COUNT;" in text, (
        "REQUIRED_FOG_CYCLES was a separate literal; when it disagreed with the "
        "timing policy the wave both overran and reported the wrong progress")
    assert "REQUIRED_FOG_CYCLES = 3" not in text
    assert "REQUIRED_FOG_CYCLES = 5" not in text


def test_no_production_constant_still_pins_the_retired_three_cycle_scheme():
    offenders = []
    for path in DOMAIN.glob("*.java"):
        text = path.read_text(encoding="utf-8")
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if ("CYCLE_COUNT = 3;" in stripped or "REQUIRED_FOG_CYCLES = 3;" in stripped
                    or "COMBAT_SECONDS = {40, 50, 60}" in stripped):
                offenders.append(f"{path.name}: {stripped}")
    assert not offenders, f"three-cycle constants survive: {offenders}"


def test_the_safe_zone_policy_spends_the_same_cycle_budget_as_the_timing_policy():
    text = read("BlackFogSafeZonePolicy.java")
    assert "cycleIndex >= BlackFogTimingPolicy.CYCLE_COUNT" in text, (
        "the safe-zone policy must spend the same cycle budget as the timing policy")
