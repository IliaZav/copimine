from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_boss_visual_cue_entry_points_exist() -> None:
    for signature in (
        "private void playBossVisualCue(LivingEntity boss, String cueId, Location origin, boolean forced)",
        "private void renderBossVisualCue(Player viewer, LivingEntity boss, BossVisualCuePolicy.Cue cue, Location origin, int elapsedTicks)",
        "private void resetBossVisualCue(LivingEntity boss, long callbackGeneration)",
    ):
        assert signature in MAIN


def test_boss_spell_lifecycle_routes_telegraph_release_and_impact_through_one_cue_path() -> None:
    telegraph = _body("private void telegraphBossSpell", "private void executeBossSpell")
    execute = _body("private void executeBossSpell", "private void voidBlast")

    assert "playBossVisualCue(boss" in telegraph
    assert "BossVisualCuePolicy.CueStage.TELEGRAPH" in telegraph
    assert "playBossVisualCue(boss" in execute
    assert "BossVisualCuePolicy.CueStage.RELEASE" in execute
    assert "BossVisualCuePolicy.CueStage.IMPACT" in execute
    assert "renderSpellImpactVisual(boss, impactOrigin, spell.id())" in execute


def test_boss_spell_reset_token_is_captured_after_impact_cue() -> None:
    execute = _body("private void executeBossSpell", "private void voidBlast")
    impact = execute.index("BossVisualCuePolicy.CueStage.IMPACT")
    accepted_token = execute.index("resetTokenForAcceptedCue(acceptedImpactCue, false)")
    reset = execute.index("scheduleBossAnimationReset(boss, callbackGeneration", impact)

    assert impact < accepted_token < reset
    assert "resetCueSequence" not in execute


def test_phase_final_and_defeat_cues_are_wired_at_existing_lifecycle_hooks() -> None:
    synchronize = _body("private void synchronizeBossStage", "private void updateBossBar")
    absorption = _body("private void startAbsorptionChannel", "private void startJudgment")
    judgment = _body("private void startJudgment", "private void spawnJudgmentSafeZoneVisual")
    final_phase = _body("private void triggerFinalPhase", "private void applyFinalDrain")
    defeat = _body("private void commitOfficialBossDefeat", "private void triggerFinalPhase")

    assert "PHASE_SHIFT" in MAIN
    assert "FINAL_AWAKENING" in MAIN
    assert "DEFEAT_COLLAPSE" in MAIN
    assert "playBossVisualCue(boss" in synchronize
    assert "playBossVisualCue(boss" in absorption
    assert "playBossVisualCue(boss" in judgment
    assert "FINAL_AWAKENING" in final_phase
    assert "playBossVisualCue(boss" in final_phase
    assert "DEFEAT_COLLAPSE" in defeat
    assert "playBossVisualCue(boss" in defeat


def test_boss_visual_cue_path_guards_generation_owner_and_duplicate_deadline() -> None:
    cue = _body(
        "private void playBossVisualCue(LivingEntity boss, String cueId, Location origin, boolean forced)",
        "private void renderBossVisualCue",
    )

    for marker in (
        "ownedBySession(boss, eventId, generation)",
        "isLiveOwnedEntity(boss.getUniqueId())",
        "bossVisualCueGeneration",
        "bossVisualCueSequence",
        "lastBossVisualCueId",
        "lastBossVisualCueDeadlineTick",
        "BossVisualCuePolicy.shouldSuppressDuplicate",
        "cooldownTicks",
    ):
        assert marker in cue


def test_boss_visual_cue_audio_and_particles_are_viewer_scoped() -> None:
    cue = _body(
        "private void playBossVisualCue(LivingEntity boss, String cueId, Location origin, boolean forced)",
        "private void renderBossVisualCue",
    )
    render = _body(
        "private void renderBossVisualCue(Player viewer, LivingEntity boss, BossVisualCuePolicy.Cue cue, Location origin, int elapsedTicks)",
        "private void resetBossVisualCue",
    )

    assert "for (Player viewer : eventAudience())" in cue
    assert "viewer.playSound" in cue
    assert "boss.getWorld().playSound" not in cue
    assert "viewer.spawnParticle" in render
    assert "world.spawnParticle" not in render
    assert "cue.primaryParticle()" in render
    assert "cue.accentParticle()" in render


def test_boss_visual_segment_supplies_sculk_charge_payload() -> None:
    segment = _body(
        "private void spawnPatternSegment",
        "private boolean isSpellFlightAllowed",
    )

    assert "if (particle == Particle.SCULK_CHARGE)" in segment
    assert "Float.valueOf(0.0F)" in segment


def test_boss_visual_cue_keeps_reset_aliases_and_bounded_scheduler_path() -> None:
    reset = _body("private void resetBossVisualCue", "private void sendControlStart")
    schedule = _body("private void scheduleBossAnimationReset", "private void sendControlStart")

    for marker in (
        "SPELL_",
        "ABSORPTION_CHANNEL",
        "JUDGMENT_CAST",
        "EXHAUSTED",
    ):
        assert marker in MAIN
    assert 'sendBossAnimationVisualUpdate(boss, "IDLE")' in reset
    assert "callbackCueSequence" in schedule
    assert "callbackCueId" in schedule
    assert "resetBossVisualCue(boss, callbackGeneration, callbackCueSequence, callbackCueId)" in schedule
    assert "BossVisualCuePolicy.canReset" in reset
