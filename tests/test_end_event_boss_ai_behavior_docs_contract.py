from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/end-rift-boss-ai.md"


def test_boss_ai_document_covers_all_stages_and_bounded_maneuvers() -> None:
    text = DOC.read_text(encoding="utf-8")
    for marker in (
        "Пробуждение", "Охота", "Искажение", "Поглощение", "Катастрофа",
        "2500 HP", "PHANTOM_FEINT", "8 секунд", "20 блоков", "telegraph",
        "particle flight", "impact", "REVERSE_PORTAL", "END_ROD",
    ):
        assert marker in text, marker


def test_boss_ai_document_preserves_cleanup_and_player_target_invariants() -> None:
    text = " ".join(DOC.read_text(encoding="utf-8").lower().split())
    for marker in (
        "живым участникам", "не переключается на предметы или мобов",
        "поколение события", "удалении ядра", "рестарте", "очищаются",
        "ванильные частицы", "без изменения ванильных текстур",
    ):
        assert marker in text, marker
