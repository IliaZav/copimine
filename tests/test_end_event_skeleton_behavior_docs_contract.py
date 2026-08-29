from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/end-rift-skeleton-ai.md"


def test_skeleton_ai_behavior_is_documented_for_every_variant_and_wave() -> None:
    assert DOC.is_file(), f"Missing skeleton AI behavior contract: {DOC}"
    text = DOC.read_text(encoding="utf-8")
    for marker in (
        "Стрелок Разлома",
        "Костяной стрелок Разлома",
        "PLAYER_ONLY",
        "bone_tracer",
        "rift_salvo",
        "Волна I",
        "Волна II",
        "Волна III",
        "Волна IV",
        "Волна V",
        "Финальная волна",
        "bone_line",
        "marked_hunt",
        "portal_guard",
        "tower_artillery",
        "storm_kite",
        "final_volley",
    ):
        assert marker in text, f"Skeleton AI document is missing: {marker}"


def test_skeleton_ai_document_preserves_the_non_aggro_and_bounds_invariants() -> None:
    assert DOC.is_file()
    text = DOC.read_text(encoding="utf-8")
    for marker in (
        "PLAYER_ONLY",
        "20",
        "3",
        "стрел",
        "частиц",
        "очища",
    ):
        assert marker.lower() in text.lower(), f"Missing invariant in skeleton AI document: {marker}"
