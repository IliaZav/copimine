from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = (ROOT / "docs" / "END_RIFT_EVENT_GUIDE_RU.md").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event" / "config.yml").read_text(encoding="utf-8")
MAIN = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")


def test_guide_covers_admin_command_surface():
    required = [
        "/cmend core set <количество игроков>",
        "/cmend core setat <x> <y> <z> <количество игроков>",
        "/cmend arena pos1",
        "/cmend arena border 10",
        "/cmend gate open [ticks-per-layer]",
        "/cmend gate close [ticks-per-layer]",
        "/cmend gate delete confirm",
        "/cmend resources reset confirm",
        "/cmend ritual cancel confirm",
        "/cmend wave spawn <1|2|3|4|5|final>",
        "/cmend boss spawn official confirm",
        "/cmend boss kill simulate-victory confirm",
        "/cmend debug ai",
        "/cmend test run creative",
    ]
    for command in required:
        assert command in DOC


def test_guide_matches_current_core_values():
    for value in [
        "countdown-seconds: 60",
        "intermission-seconds: 20",
        "radius: 20.0",
        "vertical-radius: 3.0",
        "health: 5000.0",
        "elite-health: 40.0",
        "arena-inferno:",
        "duration-ticks: 100",
        "wave-5:",
        "NETHERITE_SCRAP",
    ]:
        assert value in CONFIG or value in DOC


def test_guide_documents_the_source_guardrails():
    doc = DOC.lower()
    for marker in [
        "pointAtTargetBlock",
        '"CLOSING"',
        "isVictoryGateLockedOpen",
        "LEGACY_CORE_REMOVAL_TEXT",
        "VICTORY_COMPLETE",
    ]:
        assert marker in MAIN
    for phrase in [
        "именно блок под прицелом",
        "закрытие, восстановление и удаление двери запрещены",
        "старый плавающий текст",
        "Ядро и исходные полы рун сохраняются",
    ]:
        assert phrase.lower() in doc
