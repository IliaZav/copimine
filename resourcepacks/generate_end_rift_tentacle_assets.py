"""Compatibility entry point for the source-driven Kagune asset importer."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
IMPORTER_DIR = ROOT.parent / "CopiMineClient" / "tools"
sys.path.insert(0, str(IMPORTER_DIR))

from import_kagune_model import import_kagune_assets  # noqa: E402


def main() -> None:
    import_kagune_assets()


if __name__ == "__main__":
    main()
