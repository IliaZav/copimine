from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RECOVERY = ROOT / "tests" / "RunEndRiftRecoverySmoke.ps1"


def test_recovery_smoke_reads_rotated_paper_logs() -> None:
    text = RECOVERY.read_text(encoding="utf-8")

    assert "Get-ChildItem" in text
    assert "*.gz" in text
    assert "GzipStream" in text
    assert "persistent phase=" in text
    assert "END_EVENT_STATE forced=" in text
