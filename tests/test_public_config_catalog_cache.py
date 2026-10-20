import sys
import tempfile
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "admin-web"))

from backend.public_config_cache import FileMtimeMemo  # noqa: E402


class PublicConfigCatalogCacheTest(unittest.TestCase):
    def test_concurrent_reads_share_one_load_and_reload_after_file_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            catalog = Path(directory) / "items.yml"
            catalog.write_text("enabled", encoding="utf-8")
            calls = 0

            def loader() -> bool:
                nonlocal calls
                calls += 1
                time.sleep(0.01)
                return catalog.read_text(encoding="utf-8") == "enabled"

            memo = FileMtimeMemo()
            with ThreadPoolExecutor(max_workers=16) as executor:
                values = list(executor.map(lambda _: memo.get(catalog, loader), range(32)))

            self.assertEqual(values, [True] * 32)
            self.assertEqual(calls, 1)

            catalog.write_text("disabled", encoding="utf-8")
            self.assertFalse(memo.get(catalog, loader))
            self.assertEqual(calls, 2)


if __name__ == "__main__":
    unittest.main()
