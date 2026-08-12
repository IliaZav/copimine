"""Regression contract for the default world-access state."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORLD_CORE_CONFIG = ROOT / "copimine-world-core" / "config.yml"


class WorldAccessDefaultContractTest(unittest.TestCase):
    def test_nether_is_open_in_the_shipped_default_config(self) -> None:
        config = WORLD_CORE_CONFIG.read_text(encoding="utf-8")
        match = re.search(
            r"world_access:\s*\n\s+nether:\s*\n(?P<body>.*?)(?=\n\s{2}\w|\Z)",
            config,
            re.DOTALL,
        )
        self.assertIsNotNone(match, "world_access.nether must be present")
        self.assertRegex(match.group("body"), r"(?m)^\s+enabled:\s+true\s*$")


if __name__ == "__main__":
    unittest.main()
