from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EndRiftAiPolicy.java").read_text(
    encoding="utf-8"
)
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def test_boss_spells_have_player_facing_russian_names() -> None:
    expected_names = {
        "VOID_BLAST": "Взрыв Бездны",
        "RIFT_PROJECTILE": "Снаряд Разлома",
        "VOID_MARK": "Клеймо Пустоты",
        "SUMMON_SERVANTS": "Призыв слуг Разлома",
        "WILL_DISTORTION": "Искажение воли",
        "ARENA_INFERNO": "Пламя Разлома",
    }

    for spell, display_name in expected_names.items():
        assert f'{spell}("' in POLICY
        assert display_name in POLICY


def test_boss_telegraph_uses_display_name_instead_of_internal_spell_id() -> None:
    telegraph = MAIN[MAIN.index("private void telegraphBossSpell"):]
    telegraph = telegraph[:telegraph.index("private void executeBossSpell")]

    assert '"Хранитель готовит: " + spell.displayName()' in telegraph
    assert '"Хранитель готовит: " + spell.id()' not in telegraph
