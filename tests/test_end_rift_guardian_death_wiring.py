"""Master contract 17.1: a destroyed cocoon defender never comes back.

GuardianDeathLedger answers the contract perfectly on its own. The defect this
file guards against lives at the call sites: the ledger is correct, but nobody
records the kill, or the spawn loop schedules the 90-second deadline anyway and
resurrects a defender the player already paid to kill. A pure-policy test cannot
see any of that.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


def test_a_kill_is_recorded_in_the_death_ledger():
    text = source()
    assert "guardianDeaths.recordDeath(slot)" in text, (
        "the guardian kill must reach the durable death ledger"
    )


def test_destruction_cancels_the_respawn_deadline():
    text = source()
    index = text.index("guardianDeaths.recordDeath(slot)")
    tail = text[index: index + 900]
    assert "tentacleGuardianRespawnDeadlineMillis.remove(slot)" in tail, (
        "a destroyed slot must not retain a respawn deadline"
    )
    assert "TentacleGuardianPolicy.respawnDeadline(" not in tail, (
        "the retired 90-second respawn must not be re-scheduled on a real kill"
    )


def test_the_spawn_loop_refuses_a_destroyed_slot():
    text = source()
    marker = "if (guardianDeaths.isDestroyed(slot)) {"
    assert marker in text, (
        "spawn loop must refuse destroyed slots, or a restart resurrects them"
    )
    # The refusal has to come before the spawn call, not after it.
    guard = text.index(marker)
    spawn = text.index("spawnTentacle(boss, location, false, slot, null)", guard)
    assert guard < spawn


def test_ledger_is_generation_scoped_on_reset():
    text = source()
    body = text[text.index("private void resetTentacleGuardianLedger()"):]
    body = body[: body.index("\n    }")]
    assert "guardianDeaths = new GuardianDeathLedger(" in body, (
        "each encounter generation must start with a clean death ledger"
    )


def test_ledger_field_exists_and_is_not_a_collection_of_timers():
    text = source()
    assert "private GuardianDeathLedger guardianDeaths" in text

def test_cocoon_break_is_recorded_permanently():
    """Contract 17.3: once the cocoon fails it never shields the boss again."""
    text = source()
    text = text[text.index("private boolean commitTentacleGuardianDeathWindow("):]
    marker = "guardianDeaths.markCocoonBroken()"
    assert marker in text, "the cocoon break must reach the durable ledger"
    # It has to happen where the shield actually breaks, next to the window commit.
    index = text.index(marker)
    window = text.rindex("tentacleDamageWindowEndsMillis = tentacleActiveMillis", 0, index)
    assert index - window < 400, "cocoon break must be recorded at the shield break"


def test_a_destroyed_slot_is_never_a_living_defender():
    """A stale display must not re-shield the boss after a restart."""
    text = source()
    body = text[text.index("private boolean isLivingTentacleGuardian("):]
    body = body[: body.index("\n    }")]
    assert "guardianDeaths.isDestroyed(state.slot())" in body, (
        "a destroyed slot must never count as a living defender"
    )

def test_regeneration_stops_permanently_after_the_cocoon_breaks():
    """Contract 17.4: regeneration must not resume once the cocoon is gone."""
    text = source()
    body = text[text.index("private void tickGuardianProtectedRegeneration("):]
    body = body[: body.index("\n    }")]
    assert "guardianDeaths.cocoonBroken()" in body, (
        "a broken cocoon must gate regeneration independently of the live shield"
    )

def test_cocoon_owes_six_to_eight_defenders():
    """Contract 17.1: six for small parties, seven medium, eight large.

    The retired ladder started at two, which let a duo break the seal by killing
    a single pair - the cocoon the contract describes never existed for them.
    """
    text = source()
    body = text[text.index("private int permanentTentacleTarget("):]
    body = body[: body.index("\n    }")]
    assert "KaguneCocoonGuardianPolicy.countFor(" in body, (
        "the cocoon count must come from the contract policy, not the old ladder"
    )
    assert "TentacleScalingPolicy.permanentFor(" not in body, (
        "the retired 2/3/4/5/6/8 ladder must not drive the cocoon"
    )
