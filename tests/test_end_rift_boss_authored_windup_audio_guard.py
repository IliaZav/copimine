"""Guard the authored windup audio of every Guardian spell.

Binding source:
- The 2026-11-22 objective prompt, section 6: every phase has a signature spell
  with a "distinctive start cue" and a readable warning.
- Section 9A.01.5 "Audio choreography": a finished spell has a distinctive start
  cue and a swelling charge -- a warning a player cannot hear is not a tell.
- Section 7: the centre-core chest beam is the benchmark ultimate; its charge
  must be audible from the first telegraph tick.

The regression this probe exists to catch
-----------------------------------------
``playBossVisualCue`` resolved the legacy ten-spell ``BossVisualCuePolicy.cue``
table and returned immediately when the row was missing::

    BossVisualCuePolicy.Cue cue = BossVisualCuePolicy.cue(base, stage);
    if (cue == null) {
        return;
    }

Every authored spell that outgrew that table -- the contact and Kagune kit
(``melee_swipe``, ``chest_strike``, ``ground_slam``, ``ambush_grab``,
``small_kagune``, ``kagune_stab``, ``kagune_eruption``), ``boss_fireball`` and
the section 7 ``chest_beam`` -- therefore charged in complete silence, even
though ``BossSpellVisualCompositionPolicy.composition(...).telegraphSound()``
was authored for each of them. The fix lets an authored spell through even with
no cue row, plays the authored windup sound, and keeps the pose the spell
pipeline already sent (never falling through to ``bossCueAnimationId``, which
resolves ``UNKNOWN`` and the client draws as the idle breath).

This is a static source scan on purpose: the cue path needs a live Bukkit
scheduler and network viewers, so the behavioural proof lives in the pure Java
``BossVisualCuePolicyTest`` (``cooldownForStage``) and the composition test
(``telegraphSound`` is authored). This scan fails loudly if the runtime goes
back to a silent authored telegraph.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CUE_POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossVisualCuePolicy.java"


def _method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    # The next method at the same indentation marks the end of this body.
    tail = source[start + len(signature):]
    end = tail.find("\n    private ")
    assert end != -1, f"could not bound the body of {signature!r}"
    return tail[:end]


def test_cue_does_not_silence_an_authored_telegraph():
    source = EVENT.read_text(encoding="utf-8")
    body = _method_body(source, "private void playBossVisualCue(LivingEntity boss")
    # The blanket early return was the bug: it dropped every authored spell that
    # has no legacy cue row before its own telegraph sound could be read.
    assert not (
        "BossVisualCuePolicy.Cue cue = BossVisualCuePolicy.cue(" in body
        and "if (cue == null) {\n            return;\n        }" in body
    ), (
        "playBossVisualCue must not return on a missing legacy cue; authored "
        "spells own a windup sound without a cue row"
    )
    # The authored windup sound must still be resolved from the composition.
    assert "signature.telegraphSound()" in body, (
        "the authored windup sound must be played on the telegraph stage"
    )
    # It must only be skipped when there is neither a cue nor an authored spell.
    assert "cue == null && !authoredSpell" in body, (
        "an unauthored spell with no cue should still be the only silent case"
    )


def test_a_cue_row_still_owns_its_own_audio():
    source = EVENT.read_text(encoding="utf-8")
    body = _method_body(source, "private void playBossVisualCue(LivingEntity boss")
    # The authored signature and the legacy cue bank disagree for rift_arrows
    # (trident vs arrow) and phase_shift (end portal vs portal ambience), so a
    # spell that has a cue row must keep the cue row's sound. Preferring the
    # composition unconditionally would silently re-voice those two legacy
    # spells while claiming the ten cue spells are untouched.
    assert "cue == null\n                ? (stage" in body, (
        "the authored windup sound may only apply when there is no cue row"
    )
    assert "cue.soundId()" in body, (
        "a spell with a cue row must keep the cue row's authored audio"
    )


def test_authored_telegraph_never_sends_an_idle_pose():
    source = EVENT.read_text(encoding="utf-8")
    body = _method_body(source, "private void playBossVisualCue(LivingEntity boss")
    # A second animation send resolves bossCueAnimationId -> UNKNOWN for a
    # spell outside the cue table, and the client draws UNKNOWN as idle breath.
    assert "if (cue != null && !isSpellDrivenBossCue(cueId))" in body, (
        "the second cue animation must be gated on a real cue so an authored "
        "spell keeps the pose its pipeline already sent"
    )
    # The shared particle renderer needs a cue; an authored spell draws itself.
    assert "if (cue == null || authoredSpell)" in body, (
        "the legacy cue renderer must be skipped when there is no cue or the "
        "spell draws itself"
    )


def test_per_stage_cooldown_has_a_single_source_of_truth():
    policy = CUE_POLICY.read_text(encoding="utf-8")
    assert "public static int cooldownForStage(CueStage stage)" in policy, (
        "authored spells without a cue row need the same per-stage cooldown"
    )
    # The cue record must delegate rather than duplicate the number triple.
    body = policy.split("int cooldownTicks(CueStage stage)", 1)[1]
    body = body.split("}", 1)[0]
    assert "cooldownForStage(stage)" in body, (
        "the cue record must delegate its cooldown to the shared helper"
    )
