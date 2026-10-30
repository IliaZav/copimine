"""Scan candidate bytes before atomically replacing an existing uploaded asset."""
import asyncio
from types import SimpleNamespace

import pytest
from fastapi import HTTPException
from backend import antivirus


@pytest.mark.parametrize("verdict,status", [("infected", 422), ("unavailable", 503)])
def test_rejected_candidate_preserves_previous_file(tmp_path, monkeypatch, verdict, status):
    destination = tmp_path / "asset.png"
    destination.write_bytes(b"previous-approved-bytes")
    scanned = []
    audit = []
    def scan(candidate):
        scanned.append(candidate)
        return verdict, "test scanner result"
    monkeypatch.setattr(antivirus, "scan", scan)
    database = SimpleNamespace(audit=lambda *args: audit.append(args))
    with pytest.raises(HTTPException) as caught:
        asyncio.run(antivirus.store(destination, b"new-unapproved-bytes", database, {"id": "test", "name": "Test"}, "asset"))
    assert caught.value.status_code == status
    assert destination.read_bytes() == b"previous-approved-bytes"
    assert scanned and scanned[0] != destination
    assert set(tmp_path.iterdir()) == {destination}, "Temporary scanner input must be removed"
    assert bool(audit) is (verdict == "infected")


def test_clean_candidate_is_not_published_until_scan_completes(tmp_path, monkeypatch):
    destination = tmp_path / "asset.webp"
    destination.write_bytes(b"old-clean")
    def scan(candidate):
        assert candidate != destination
        assert destination.read_bytes() == b"old-clean"
        assert candidate.read_bytes() == b"new-clean"
        return "clean", "verified"
    monkeypatch.setattr(antivirus, "scan", scan)
    result = asyncio.run(antivirus.store(destination, b"new-clean", SimpleNamespace(audit=lambda *a: None), {}, "asset"))
    assert result == {"scan": "clean", "detail": "verified"}
    assert destination.read_bytes() == b"new-clean"
    assert set(tmp_path.iterdir()) == {destination}


def test_scanner_failure_cannot_publish_unscanned_payload(tmp_path, monkeypatch):
    destination = tmp_path / "asset.webp"
    destination.write_bytes(b"old-clean")
    def scan(candidate):
        raise OSError("synthetic scanner failure")
    monkeypatch.setattr(antivirus, "scan", scan)
    with pytest.raises(HTTPException) as caught:
        asyncio.run(antivirus.store(destination, b"new-payload", SimpleNamespace(audit=lambda *a: None), {}, "asset"))
    assert caught.value.status_code == 503
    assert destination.read_bytes() == b"old-clean"
    assert set(tmp_path.iterdir()) == {destination}
