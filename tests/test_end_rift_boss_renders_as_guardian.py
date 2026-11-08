"""The boss must render as the authored Guardian, not as a vanilla Enderman.

Three independent defects conspired so that /cmend boss spawn produced a plain
Enderman:

1. The server only sent END_BOSS_BIND to players that passed the combat-viewer
   rules. The harness operator is not a combat participant and is usually
   outside the arena cuboid, so the packet was never sent and the client had no
   reason to draw anything but vanilla.
2. The recovery pass deleted any test boss it re-indexed, so a chunk reload in
   the middle of the harness removed the boss outright.
3. Even with the visual bound, the client routed END_RIFT_GUARDIAN_V1 through
   selectVisual -> eventRenderer, which returns the ordinary event Enderman
   model/skin; the guardian branch was reachable only via the endBoss flag.

These tests hold all three.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"


def body(path, signature):
    text = path.read_text(encoding="utf-8")
    start = text.index(signature)
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace:index + 1]
    raise AssertionError("unterminated " + signature)


def test_the_boss_harness_operator_is_treated_as_a_visual_viewer():
    viewer = body(SERVER, "private boolean isEventVisualViewer(Player player)")
    assert "isBossHarnessViewer(player)" in viewer, (
        "the harness operator must be able to receive END_BOSS_BIND")
    # Presence is not enough: a rule that is only mentioned inside a dead
    # condition still reads as "implemented" while doing nothing. The call has
    # to sit in the live OR-chain of the returned expression.
    returned = viewer[viewer.rindex("return"):]
    assert "isBossHarnessViewer(player)" in returned, (
        "the harness rule must be part of the returned viewer decision")
    assert "false &&" not in returned, (
        "the harness rule must not be short-circuited away")


def test_the_harness_viewer_rule_is_narrow():
    rule = body(SERVER, "private boolean isBossHarnessViewer(Player player)")
    assert "testCombatAiMode" in rule, (
        "widening must be limited to the disposable harness")
    assert "isTestBoss(boss)" in rule, (
        "only a test boss may widen the viewer scope")
    assert "isCombatTarget" not in rule and "getGameMode" not in rule, (
        "the rule must not turn the operator into a combat participant")


def test_a_live_test_boss_survives_a_chunk_reload():
    text = SERVER.read_text(encoding="utf-8")
    marker = text.index('"stale-test-boss-recovery"')
    window = text[max(0, marker - 900):marker]
    assert "ownedBySession(entity, eventId, generation)" in window, (
        "a boss that still belongs to the live session must be re-registered, "
        "not removed")
    assert "registerOwnedEntity(entity)" in window, (
        "the surviving boss has to be indexed again")


def test_the_guardian_visual_selects_the_guardian_model_without_the_flag():
    mixin = (CLIENT / "mixin/LivingEntityRendererMixin.java").read_text(encoding="utf-8")
    assert "copimine$isGuardianState" in mixin, (
        "the renderer needs one shared guardian predicate")
    assert "decision.usesGuardianModel()" in mixin, (
        "the visual-bound route must be able to pick the guardian model")
    # The guardian branch has to be reachable in the non-flag path.
    select = body(CLIENT / "mixin/LivingEntityRendererMixin.java",
                  "private void copimine$selectWaveModel")
    else_branch = select[select.index("} else {"):]
    assert "usesGuardianModel()" in else_branch, (
        "a visual-bound Guardian must not fall through to the event Enderman")


def test_the_guardian_visual_selects_the_guardian_texture():
    mixin = (CLIENT / "mixin/EndermanEntityRendererMixin.java").read_text(encoding="utf-8")
    assert "END_RIFT_GUARDIAN_V1" in mixin, (
        "the texture mixin must recognise the guardian visual id, not only the flag")
    assert "guardian)" in mixin, (
        "the texture branch has to be driven by one guardian decision")


def test_the_guardian_predicate_accepts_both_routes():
    mixin = (CLIENT / "mixin/LivingEntityRendererMixin.java").read_text(encoding="utf-8")
    helper = mixin[mixin.index("copimine$isGuardianState(EndRiftRenderStateAccess bound)"):]
    helper = helper[:helper.index("    }")]
    assert "copimine$endBoss()" in helper, "the flag route must still work"
    assert "END_RIFT_GUARDIAN_V1" in helper, "the visual id route must also work"
