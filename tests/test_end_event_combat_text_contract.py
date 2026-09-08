from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def test_combat_has_no_technical_actionbar_messages() -> None:
    banned = (
        "Ритуал:",
        "Мини-босс готовит:",
        "Эйфория Пустоты:",
        "Пульс ядра:",
        "Вас отметил Разлом",
        "Метка Разлома:",
        "Порталы Разлома:",
        "Защита ядра:",
        "Шторм Разлома:",
        "Разлом раскрывается...",
        "Приговор Разлома:",
        "Финальный удар:",
        "Осколок:",
        "Хранитель готовит:",
    )
    for text in banned:
        assert text not in MAIN, text


def test_objectives_keep_worldspace_visual_and_audio_feedback() -> None:
    for marker in (
        "renderWaveOneArenaZones",
        "renderPortalObjective",
        "renderRiftStormField",
        "renderRiftStormSafeZoneVisuals",
        "playSound",
        "spawnEventParticle",
    ):
        assert marker in MAIN
