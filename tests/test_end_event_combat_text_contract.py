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


def test_official_boss_hud_does_not_expose_phase_or_exact_health_text() -> None:
    start = MAIN.index("private void updateV2BossBar")
    end = MAIN.index("private void startAbsorptionChannel", start)
    hud = MAIN[start:end]
    assert 'String renderedTitle = "Страж Разлома"' in hud
    assert "v2BossStage.title()" not in hud
    assert '" HP"' not in hud


def test_official_phase_shift_message_uses_atmosphere_not_a_phase_identifier() -> None:
    start = MAIN.index("private void synchronizeV2BossStage")
    end = MAIN.index("private void startV2LastSealVisuals", start)
    phase_shift = MAIN[start:end]
    assert 'announceEventTitle("§5СТРАЖ РАЗЛОМА"' in phase_shift
    assert "v2BossStage.title()" not in phase_shift
