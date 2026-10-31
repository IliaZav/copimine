"""TAB header pluralises "player" without changing what %online% means."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
ADMIN_PLUGIN = ROOT / "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
TAB_CONFIG = ROOT / "minecraft/server/plugins/TAB/config.yml"


def test_online_word_placeholder_is_registered():
    source = ADMIN_PLUGIN.read_text(encoding="utf-8")

    assert 'case "online_word" ->' in source
    assert "russianPlayersWord(" in source


def test_online_placeholder_itself_stays_a_bare_number():
    # Other plugins and server text still read %online% as a number, so only the word changes.
    config = TAB_CONFIG.read_text(encoding="utf-8")

    assert "%online%" in config
    assert "%copimine_online_word%" in config


def _run_plural(probe_dir: Path) -> str:
    source = ADMIN_PLUGIN.read_text(encoding="utf-8")
    method = extract(source, "static String russianPlayersWord(int online)")
    java = probe_dir / "OnlineWordProbe.java"
    java.write_text(
        "public class OnlineWordProbe {\n"
        + method
        + "\n"
        + r'''
    static void check(String expected, int online) {
        String actual = russianPlayersWord(online);
        if (!expected.equals(actual)) {
            throw new AssertionError(online + " -> " + actual + ", expected " + expected);
        }
    }
    public static void main(String[] args) {
        check("игрок", 1);
        check("игрока", 2);
        check("игрока", 4);
        check("игроков", 5);
        check("игроков", 0);
        check("игроков", 11);
        check("игроков", 12);
        check("игроков", 14);
        check("игроков", 15);
        check("игрок", 21);
        check("игрока", 22);
        check("игроков", 25);
        check("игроков", 111);
        check("игрок", 101);
    }
}
''',
        encoding="utf-8",
    )
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(probe_dir), str(java)],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(
        ["java", "-cp", str(probe_dir), "OnlineWordProbe"], capture_output=True, text=True
    )
    return run.stdout + run.stderr


def test_plural_follows_russian_rules(tmp_path):
    output = _run_plural(tmp_path)

    assert output.strip() == "", output