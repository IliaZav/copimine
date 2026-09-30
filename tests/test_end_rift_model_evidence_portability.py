"""Portable, self-contained contracts for the generated model evidence."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "artifacts" / "end-rift-v3-evidence"
MANIFEST = EVIDENCE / "end-rift-mob-model-preview-manifest.json"
PREVIEW_ROOT = "artifacts/end-rift-v3-evidence/model-previews/"


def test_model_preview_manifest_contains_portable_existing_paths() -> None:
    entries = json.loads(MANIFEST.read_text(encoding="utf-8"))
    username = os.environ.get("USERNAME") or os.environ.get("USER") or ""

    assert entries
    for entry in entries:
        preview = entry["preview"]
        first_component = preview.split("/", 1)[0]

        assert not Path(preview).is_absolute(), preview
        assert "\\" not in preview, preview
        assert not preview.startswith("/"), preview
        assert ":" not in first_component, preview
        assert preview.startswith(PREVIEW_ROOT), preview
        assert (ROOT / preview).is_file(), preview
        for forbidden in ("C:\\Users", "D:\\Desktop", ".worktrees", username):
            if forbidden:
                assert forbidden not in preview, preview


def test_model_preview_board_hash_is_recorded_in_verification() -> None:
    board = EVIDENCE / "end-rift-mob-model-board-20260916.png"
    verification = EVIDENCE / "end-rift-mob-model-verification-20260916.md"
    digest = hashlib.sha256(board.read_bytes()).hexdigest()

    assert digest in verification.read_text(encoding="utf-8")
