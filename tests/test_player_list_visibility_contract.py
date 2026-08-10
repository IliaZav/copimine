from pathlib import Path
import unittest


class PlayerListVisibilityContractTest(unittest.TestCase):
    def test_player_roster_is_scoped_to_whitelist_and_linked_accounts(self) -> None:
        repo = Path(__file__).resolve().parents[1]
        source = (repo / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")
        start = source.index("def list_players_sync(")
        end = source.index("def open_sqlite_readonly(", start)
        function = source[start:end]
        self.assertIn(
            "uuids = set(whitelist)",
            function,
            "the player roster must start from the current whitelist",
        )
        self.assertNotIn(
            "uuids |= {p.stem for p in pdata_dir.glob(\"*.dat\")}",
            function,
            "historical world playerdata must not repopulate the admin roster",
        )


if __name__ == "__main__":
    unittest.main()
