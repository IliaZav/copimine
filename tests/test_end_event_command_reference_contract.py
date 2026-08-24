from __future__ import annotations

from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "docs" / "plugin-guides" / "CopiMineEndEvent_Command_Reference.docx"


def _docx_text() -> str:
    assert REFERENCE.is_file(), REFERENCE
    with ZipFile(REFERENCE) as archive:
        document = ElementTree.fromstring(archive.read("word/document.xml"))
    return "\n".join(document.itertext())


def test_command_reference_contains_the_local_safety_and_runtime_workflow() -> None:
    text = _docx_text()
    for marker in (
        "environment: local",
        "launcher, сайт, боевой мир и данные игроков не затрагиваются",
        "copimine.endevent.admin",
        "/cmend core set <N>",
        "/cmend core remove confirm",
        "/cmend resources add <MATERIAL>",
        "/cmend gate preview",
        "/cmend gate open [ticks-per-layer]",
        "/cmend core remove confirm",
        "/cmend test run creative",
        "UNCONFIGURED",
        "END_EVENT_OWNED_CLEANUP",
        "CREATIVE_TEST_COMPLETE",
    ):
        assert marker in text, marker


def test_command_reference_records_russian_spell_names_and_music_choices() -> None:
    text = _docx_text()
    for marker in (
        "Взрыв Бездны",
        "Снаряд Разлома",
        "Клеймо Пустоты",
        "Призыв слуг Разлома",
        "Искажение воли",
        "Рывок Разлома",
        "Кандалы Пустоты",
        "Импульс Эха",
        "Battle Theme A",
        "Boss Battle Music",
        "Boss Battle 2: Symphonic Metal",
        "Dramatic Boss Encounter",
        "Victory Theme for RPG",
        "20.00 s",
    ):
        assert marker in text, marker
