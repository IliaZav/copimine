"""Pins for the two canonical engineering specifications.

The canonical files live outside the repository (``D:\\Downloads\\``) and are not
vendored, so nothing in CI would notice when one of them changes. This file
records the exact revision the current implementation was audited against and
hashes the file whenever it is present.

A hash mismatch is not a test bug: it means the specification moved, so every
requirement that cites it has to be re-read before the next change. The test
skips on hosts where the files are absent (for example a CI runner), which keeps
the suite honest instead of silently asserting nothing.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
PIN_DOC = ROOT / "docs" / "end-rift" / "CANONICAL_SPEC_PINS.md"

PINNED = {
    "COPIMINE_END_RIFT_WAVES_CANONICAL.md": {
        "size": 47_126,
        "sha256": "6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf",
    },
    "COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md": {
        "size": 51_711,
        "sha256": "0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50",
    },
}


def canonical_roots():
    """Places a canonical file may live, most specific first."""
    roots = []
    override = os.environ.get("COPIMINE_CANONICAL_DIR")
    if override:
        roots.append(Path(override))
    # The documented location on the authoring workstation.
    roots.append(Path(r"D:\Downloads"))
    return roots


def find_canonical(name: str):
    for root in canonical_roots():
        candidate = root / name
        if candidate.is_file():
            return candidate
    return None


def test_the_pin_document_records_both_hashes():
    assert PIN_DOC.is_file(), (
        "docs/end-rift/CANONICAL_SPEC_PINS.md is the in-repo record of the "
        "canonical revisions; without it nothing in CI knows what was audited"
    )
    text = PIN_DOC.read_text(encoding="utf-8")
    for name, pin in PINNED.items():
        assert name in text, f"{name} is not named in the pin document"
        assert pin["sha256"] in text, f"{name} hash is not recorded in the pin document"
        # The size is written with a non-breaking-space-free thousands separator.
        assert f"{pin['size']:,}".replace(",", " ") in text, (
            f"{name} size is not recorded in the pin document"
        )


@pytest.mark.parametrize("name", sorted(PINNED))
def test_the_canonical_file_matches_its_pin_when_present(name):
    path = find_canonical(name)
    if path is None:
        pytest.skip(
            f"{name} is not on this host (set COPIMINE_CANONICAL_DIR to its "
            "directory to enforce the pin)"
        )
    payload = path.read_bytes()
    pin = PINNED[name]
    assert len(payload) == pin["size"], (
        f"{name} changed size: spec revision moved, re-read it before changing code"
    )
    assert hashlib.sha256(payload).hexdigest() == pin["sha256"], (
        f"{name} changed hash: spec revision moved, re-read it before changing code"
    )