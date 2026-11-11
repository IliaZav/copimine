"""Master contract 14.6: no skeleton towers in any boss phase.

BossSummonAdmissionPolicy is the gate that makes this refusal explicit. It
matters because the failure mode is SILENT: a summon helper written for a wave
and reused by the boss would put a skeleton on a pillar mid-fight and nothing in
the build would complain. Wave 6 is the only approved owner of that mechanic.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source():
    return MONOLITH.read_text("utf-8")


def body(name, text=None):
    text = text if text is not None else source()
    start = text.index(name if "(" in name else name + "(")
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace: index + 1]
    raise AssertionError("unbalanced " + name)


def test_boss_summons_ask_the_admission_gate():
    summon = body("private void summonServants(")
    assert "BossSummonAdmissionPolicy.allowsSkeletonSummon(" in summon, (
        "a boss-phase summon must consult the gate before spawning"
    )
    # The gate must run BEFORE anything is spawned.
    assert summon.index("BossSummonAdmissionPolicy.") < summon.index("spawnOwnedMob("), (
        "asking after the spawn would be a report, not a refusal"
    )
    assert "return;" in summon[summon.index("BossSummonAdmissionPolicy."):]


def test_a_refusal_is_logged_not_silent():
    summon = body("private void summonServants(")
    assert "BOSS_SUMMON_REFUSED" in summon, (
        "a refused boss summon must leave a trace, or the gate hides its own work"
    )
    assert "refusalReason(" in summon


def test_wave_six_is_the_only_skeleton_pillar_owner():
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossSummonAdmissionPolicy.java").read_text("utf-8")
    assert "WAVE_SIX_PILLAR_MECHANIC_OWNER = 6" in policy, (
        "contract 14.6 names Wave 6 as the sole approved owner"
    )
    # Every boss phase must refuse the forbidden mechanics.
    for phase in ("AWAKENING", "HUNT", "RIFT", "OVERLOAD", "RAGE", "LAST_SEAL"):
        assert phase in policy or "allowsSkeletonTower" in policy


def test_the_gate_is_not_a_blanket_ban_that_gets_worked_around():
    """Ordinary pressure summons must stay legal."""
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossSummonAdmissionPolicy.java").read_text("utf-8")
    assert "SKELETON_ARCHETYPES" in policy
    # The forbidden set is skeletons, not all mobs.
    assert "SPIDER" not in policy.split("SKELETON_ARCHETYPES")[1].split(")")[0], (
        "a blanket ban invites workarounds; only skeletons are forbidden"
    )
