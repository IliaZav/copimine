from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_gate_matrix_runners_return_success_after_green_matrix() -> None:
    for relative_path in (
        "scripts/run_copimine_client_gate_matrix.ps1",
        "scripts/run_copimine_third_party_gate_matrix.ps1",
    ):
        script = (ROOT / relative_path).read_text(encoding="utf-8")
        assert "CLIENT_GATE_MATRIX=PASS" in script or "CLIENT_GATE_MATRIX_DIRECT=PASS" in script
        assert script.rstrip().endswith("exit 0")
