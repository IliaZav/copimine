"""Wave 1 selects an ELITE Enderman carrier, attaches instead of dropping, and holds 15 s.

Three independent defects were found by reading the live paths:

1. findCurrentCarrierCandidate() accepted ANY combat mob of the wave, so an
   ordinary Enderman could be handed the charge.
2. selectCarrier() was immediately followed by spawnCurrentCarrierCharge(),
   which routed through carrierDied() whenever the phase was CARRIER_ACTIVE —
   i.e. the objective flipped to CHARGE_DROPPED for a carrier that was still
   alive and walking.
3. The wave config fielded ZERO elite Endermen, so even a correct elite-only
   filter would have had an empty candidate set.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CONFIG = ROOT / "copimine-end-event/config.yml"


def extract(start: str, end: str) -> str:
    text = SOURCE.read_text(encoding="utf-8")
    begin = text.index(start)
    return text[begin:text.index(end, begin)]


def test_the_carrier_is_selected_only_from_live_elite_endermen():
    candidate = extract(
        "private Entity findCurrentCarrierCandidate()", "private Entity currentCarrierChargeEntity()")
    assert 'EVENT_KIND_ELITE.equals(readString(entity, keyKind))' in candidate, (
        "an ordinary combat mob must never be handed the Wave 1 charge")
    assert "entity instanceof Enderman" in candidate, (
        "the carrier must be an Enderman, not a spider or skeleton")
    assert "isLiveOwnedEntity(entity.getUniqueId())" in candidate


def test_the_wave_one_config_actually_fields_elites():
    text = CONFIG.read_text(encoding="utf-8")
    # "wave-1:" also appears in the music section as a sound id, so anchor on
    # the roster block itself rather than on the bare key.
    start = text.index("  wave-1:\n    type: RIFT_CARRIERS")
    # Bound the slice from the SAME offset; searching from the file start would
    # match the "wave-2:" that follows and yield an empty block.
    block = text[start:text.index("  wave-2:", start)]
    line = [row for row in block.splitlines() if "elite-endermen:" in row][0]
    value = int(line.split(":", 1)[1].strip())
    assert value >= 1, (
        "Wave 1's charge belongs to an elite Enderman; with 0 elites the candidate "
        "set is empty and the wave silently falls back")


def test_selecting_a_living_carrier_attaches_the_charge_instead_of_dropping_it():
    body = extract(
        "private void spawnCurrentCarrierCharge(Location requested)",
        "private void renderCurrentCarrierCharge(Entity charge)")
    assert "!isLiveOwnedEntity(currentCarrierState.carrier())" in body, (
        "carrierDied() may only run for a carrier that is actually gone; otherwise "
        "a living carrier's charge is reported as dropped")
    drop = body.index("RiftCarrierPolicy.carrierDied(")
    guard = body.index("!isLiveOwnedEntity(currentCarrierState.carrier())")
    assert guard < drop, "the liveness guard must gate the death transition"


def test_a_recovered_carrier_is_still_elite():
    body = extract(
        "private boolean spawnCurrentCarrierReplacement(World world, Location core)",
        "private Entity findCurrentCarrierCandidate()")
    call = body[body.index("spawnEnderman("):]
    call = call[:call.index(");")]
    assert ", true," in call, (
        "the recovery replacement must be spawned as an elite, not downgraded to "
        "an ordinary Enderman")


def test_the_core_channel_is_raised_to_fifteen_seconds():
    text = SOURCE.read_text(encoding="utf-8")
    assert "CORE_CHARGE_HOLD_MILLIS = 15_000L" in text, (
        "the approved contract raised charge loading from 5 s to 15 s")
    assert "CORE_CHARGE_HOLD_MILLIS = 5_000L" not in text
