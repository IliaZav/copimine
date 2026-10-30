"""Player-facing failure messages explain the result without internal storage jargon."""
from pathlib import Path
from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

SOURCE = Path(__file__).resolve().parents[1] / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_core_deposit_messages_do_not_expose_internal_terms():
    body = extract_method(SOURCE.read_text(encoding="utf-8"), "public void onCoreInteract(")
    for obsolete in ('"Этот custom item', '"Внесение не записано durable', '"Состояние Core'):
        assert obsolete not in body
    assert "Этот особый предмет нельзя пожертвовать" in body
    assert "Не удалось сохранить взнос. Предмет остался у вас." in body
    assert "Не удалось сохранить состояние ядра. Ресурс возвращён." in body
    assert "Не удалось сохранить состояние ядра. Освободите слот для возврата ресурса." in body


def test_prisoner_drop_message_is_short_and_plain():
    body = extract_method(SOURCE.read_text(encoding="utf-8"), "public void onRitualPrisonerDrop(")
    assert '"В сфере нельзя выбрасывать предметы."' in body
    assert "event.setCancelled(true)" in body
