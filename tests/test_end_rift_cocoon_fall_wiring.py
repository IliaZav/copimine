"""Master contract 22.1: the Guardian falls once, lands, and stays furious.

CocoonFallPolicy owns the stage order, the timing and the fury profile. This
file guards the wiring, because the failure mode here is not a wrong number -
it is a fall that is computed correctly and then overwritten every tick by the
hover anchor, so the player never sees the boss come down.
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


def test_the_fall_starts_when_the_last_defender_dies():
    commit = body("private boolean commitTentacleGuardianDeathWindow(")
    assert "beginCocoonFall(" in commit, (
        "the last defender dying is the contract moment for the fall"
    )


def test_the_fall_cannot_replay_in_one_generation():
    begin = body("private void beginCocoonFall(")
    assert "CocoonFallPolicy.mayStartFall(cocoonFallStarted)" in begin, (
        "the policy, not a bare flag, decides whether a fall may start"
    )
    # A new generation must reset the ledger, or a rebuilt encounter inherits
    # the previous run's 'already fallen' state and never falls.
    assert "generation != cocoonFallGeneration" in begin
    assert "cocoonFallStarted = false" in begin


def test_the_descent_overrides_the_hover_pin():
    """The defect this whole file exists for: pin vs fall fighting."""
    pin = body("private boolean pinBossToFinalSealCore(")
    # An earlier vulnerability return used to make this hook unreachable.
    assert pin.index("if (tickCocoonFall())") < pin.index("if (tentacleVulnerabilityOpen())")
    assert "if (tickCocoonFall()) {\n            return true;" in pin


def test_the_boss_actually_moves_during_the_fall():
    tick = body("private boolean tickCocoonFall(")
    assert "CocoonFallPolicy.heightDuringFall(" in tick, (
        "descent height must come from the policy"
    )
    assert "teleportCombatEntity(boss, next)" in tick, (
        "descent must use the owned teleport permit and honour cancellation"
    )
    # The boss must never be driven below the floor it is landing on.
    assert "Math.max(landing.getY()" in tick, (
        "the descent must clamp onto the arena floor instead of through it"
    )


def test_the_landing_plays_once_and_reads_as_a_landing():
    tick = body("private boolean tickCocoonFall(")
    assert "CocoonFallPolicy.mayPlayLanding(cocoonLandingPlayed)" in tick, (
        "the landing must not replay on a restart or a repeated check"
    )
    assert "renderCocoonLanding(" in tick
    landing = body("private void renderCocoonLanding(")
    assert "spawnParticle(" in landing and "playSound(" in landing, (
        "contract 22.1 asks for dust/shockwave/sound at the landing"
    )


def test_melee_fury_hands_the_boss_back_to_combat():
    tick = body("private boolean tickCocoonFall(")
    assert "CocoonFallPolicy.advance(" in tick, (
        "stage progression must come from the policy"
    )
    assert "return cocoonFallStage != CocoonFallPolicy.Stage.MELEE_FURY" in tick, (
        "the fall must release the boss once melee fury begins, "
        "otherwise it would own the position forever"
    )


def test_the_policy_still_denies_the_dangerous_answers():
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/CocoonFallPolicy.java").read_text("utf-8")
    assert "FALL_TICKS = 40" in policy
    assert "LANDING_TICKS = 20" in policy
    assert "mayStartFall" in policy and "mayPlayLanding" in policy
