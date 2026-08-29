from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tests/RunEndRiftSpellMatrixLive.ps1"


def test_local_spell_matrix_exercises_every_boss_and_miniboss_spell() -> None:
    text = SCRIPT.read_text(encoding="utf-8")
    for spell in (
        "void_blast", "rift_projectile", "rift_arrows", "void_mark",
        "summon_servants", "arena_inferno", "will_distortion",
        "rift_step", "void_snare", "echo_pulse", "arrow_salvo",
    ):
        assert spell in text, spell
    for marker in (
        "BOSS_SPELL_TELEGRAPH", "BOSS_SPELL_FLIGHT", "BOSS_SPELL_CAST",
        "SPELL_IMPACT_VISUAL", "MINIBOSS_SPELL_FLIGHT",
        "LIVE_SPELL_MATRIX_PASS", "cmend wave clear",
        "matrixLogDelta", "generated an exception",
    ):
        assert marker in text, marker


def test_local_spell_matrix_exercises_all_phase_music_without_starting_automatic_music() -> None:
    text = SCRIPT.read_text(encoding="utf-8")
    for key in (
        "wave-1", "wave-2", "wave-3", "wave-4", "wave-5",
        "intermission-1", "intermission-2", "intermission-3", "intermission-4",
        "boss-cinematic", "final-drain", "final-ritual", "final-wave", "boss-finish",
    ):
        assert key in text, key
    assert "automatic_outside_event=0" in text
    assert "environment: local" in text
    assert "never to the production address" in text
