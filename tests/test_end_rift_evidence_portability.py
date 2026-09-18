"""Exact-name portability and visual-boundary contracts for End Rift evidence."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "artifacts" / "end-rift-v3-evidence"
MANIFEST = EVIDENCE / "end-rift-mob-model-preview-manifest.json"
REPORT = EVIDENCE / "end-rift-mob-model-verification-20260916.md"
PREVIEW_ROOT = "artifacts/end-rift-v3-evidence/model-previews/"


def test_static_model_manifest_is_portable_and_explicitly_not_native() -> None:
    entries = json.loads(MANIFEST.read_text(encoding="utf-8"))
    username = os.environ.get("USERNAME") or os.environ.get("USER") or ""

    assert isinstance(entries, list) and entries
    for entry in entries:
        preview = entry["preview"]
        first_component = preview.split("/", 1)[0]

        assert entry["STATIC_ASSEMBLED_PREVIEW_ONLY"] is True
        assert entry["NATIVE_MINECRAFT_RENDER_VERIFIED"] is False
        assert not Path(preview).is_absolute(), preview
        assert "\\" not in preview, preview
        assert not preview.startswith("/"), preview
        assert ":" not in first_component, preview
        assert preview.startswith(PREVIEW_ROOT), preview
        assert (ROOT / preview).is_file(), preview
        for forbidden in ("C:\\Users", "D:\\Desktop", ".worktrees", username):
            if forbidden:
                assert forbidden not in preview, preview


def test_static_model_report_contains_exact_visual_boundary_labels() -> None:
    text = REPORT.read_text(encoding="utf-8")
    assert "STATIC_ASSEMBLED_PREVIEW_ONLY=true" in text
    assert "NATIVE_MINECRAFT_RENDER_VERIFIED=false" in text


def test_static_model_board_hash_matches_the_recorded_report_digest() -> None:
    board = EVIDENCE / "end-rift-mob-model-board-20260916.png"
    digest = hashlib.sha256(board.read_bytes()).hexdigest()

    assert digest in REPORT.read_text(encoding="utf-8")
