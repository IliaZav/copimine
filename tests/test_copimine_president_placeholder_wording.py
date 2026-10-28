from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADMIN_PLUGIN = (
    ROOT
    / "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
)


def _latest_winner_name_body() -> str:
    source = ADMIN_PLUGIN.read_text(encoding="utf-8")
    start = source.index("private String latestWinnerName()")
    end = source.index("\n    }", start)
    return source[start:end]


def test_president_placeholder_uses_the_reference_wording():
    assert ADMIN_PLUGIN.is_file()

    method = _latest_winner_name_body()

    # The TAB header renders %copimine_president% as "Президент: <value>", so a bare "нет"
    # reads as a broken sentence and the reference screenshot uses the longer wording.
    assert '"не выбран"' in method
    assert '"нет"' not in method
