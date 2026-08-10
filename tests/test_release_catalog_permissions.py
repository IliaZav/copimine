from pathlib import Path
import unittest


class ReleaseCatalogPermissionsTest(unittest.TestCase):
    def test_canonical_artifacts_catalog_is_in_runtime_writable_allowlist(self) -> None:
        repo = Path(__file__).resolve().parents[1]
        common = (repo / "deploy" / "shared" / "common.sh").read_text(encoding="utf-8")
        start = common.index("copimine_harden_release_ownership()")
        end = common.index("copimine_write_runtime_metadata()", start)
        ownership = common[start:end]
        self.assertIn(
            '"$COPIMINE_ROOT/copimine-artifacts"',
            ownership,
            "atomic shop price updates require a writable canonical catalog directory",
        )


if __name__ == "__main__":
    unittest.main()
