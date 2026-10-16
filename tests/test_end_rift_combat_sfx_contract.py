from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain"
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
SOUNDS = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "sounds.json"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_boss_hit_feedback_uses_built_in_server_audio_contract() -> None:
    policy = read(DOMAIN / "BossHitFeedbackPolicy.java")
    event = read(EVENT)
    sounds = read(SOUNDS)

    for outcome in (
        "ACCEPTED_MELEE",
        "ACCEPTED_PROJECTILE",
        "SHIELD_BLOCKED_MELEE",
        "SHIELD_BLOCKED_PROJECTILE",
        "PHASE_IMMUNE",
        "CINEMATIC_IMMUNE",
    ):
        assert outcome in policy
    for cue in ("ENTITY_PLAYER_ATTACK_STRONG", "ENTITY_ARROW_HIT", "BLOCK_ANVIL_HIT"):
        assert cue in policy
        assert cue in event
    assert "attackerLocalCue" in policy
    assert "applyBossHitFeedbackAt" in event
    assert "attacker.playSound" in event

    # The selected implementation deliberately auditions vanilla sounds first;
    # no unlicensed custom combat audio is added to the resource pack.
    assert "boss_hit" not in sounds
    assert "shield_block" not in sounds


def test_boss_feedback_diagnostics_and_animation_priority_are_wired() -> None:
    event = read(EVENT)
    priority = read(DOMAIN / "BossAnimationPriorityPolicy.java")
    assert 'emitDiagnostic("BOSS_HIT_FEEDBACK"' in event
    assert "applyBossHitFeedback" in event
    assert "BossAnimationPriorityPolicy.canInterruptWithHurt" in event
    for animation in ("DYING", "FINAL_STRIKE", "PHASE_TRANSITION", "GROUND_SLAM", "CHEST_STRIKE"):
        assert animation in priority
