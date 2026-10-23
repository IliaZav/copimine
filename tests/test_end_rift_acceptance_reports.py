import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs" / "end-rift" / "final-26.3"


def read_json(name: str) -> dict:
    return json.loads((DOCS / name).read_text(encoding="utf-8"))


def test_bot_acceptance_is_blocked_not_faked() -> None:
    report = read_json("BOT_ACCEPTANCE.json")
    assert report["authentication"]["v4Signer"] == "unavailable"
    assert report["authentication"]["disablingAttempted"] is False
    assert report["authentication"]["randomKeypairGenerated"] is False
    assert report["server"]["running"] is False
    assert report["attempts"] == []
    assert [row["participants"] for row in report["matrix"]] == [2, 5, 10, 11, 20]
    for row in report["matrix"]:
        assert row["status"] == "BLOCKED"
        assert row["acceptedDamageReceipts"] == 0
        assert row["wins"] == 0
        assert row["losses"] == 0
    assert report["nativeVisualVerified"] is False
    assert report["nativeAudioVerified"] is False
    assert all(value is False for value in report["claims"].values())


def test_performance_report_claims_no_unmeasured_pass() -> None:
    text = (DOCS / "PERFORMANCE_REPORT.md").read_text(encoding="utf-8")
    assert "BLOCKED / NOT MEASURED" in text
    assert "TPS at 10-player combat | 20.0 sustained | NOT MEASURED" in text
    assert "p95 MSPT at 10-player combat | ≤ 50 ms | NOT MEASURED" in text
    assert "No PASS is claimed" in text


def test_current_handoff_tracks_installed_hashes_and_blocks() -> None:
    text = (DOCS / "CURRENT_HANDOFF.md").read_text(encoding="utf-8")
    for value in (
        "d44c8f51be00f3b6d911135f7722a155cbf20c13",
        "152704c4b62488f2bf71ed67395a2de8fd906175ea7479dd6544769434846340",
        "75547398ec77fdc083c37de5a5d6449b166efb92c8af942ab0bddc5d675cf109",
        "d7e46fcc2bcfb8f31216cb1ea51c53bf3f492d29bc3b8c227540cb38e8ced7e2",
        "COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE",
    ):
        assert value in text
    assert "Do not mark Waves 1–7 fully accepted" in text


def test_old_evidence_snapshot_is_marked_historical() -> None:
    text = (DOCS / "WAVES_1_7_CURRENT_EVIDENCE_2026-10-22.md").read_text(encoding="utf-8")
    assert "Исторический снимок" in text
    assert "CURRENT_HANDOFF.md" in text
    assert "7026719d47ec3c7acd66bf3b3e77357782f6f71d54fe5684279b86c58d5e8ea5" in text
    assert "aa8f9ea94ee471cd28efc4f574a16460412a2886e749ca4956bb337372561a42" not in text
