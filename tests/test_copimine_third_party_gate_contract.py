from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "run_copimine_third_party_gate_matrix.ps1"


def test_disposable_third_party_gate_matrix_is_path_guarded_and_exercises_direct_java() -> None:
    source = SCRIPT.read_text(encoding="utf-8")

    assert "resolvedInstanceRoot.StartsWith($resolvedValidationRoot" in source
    assert "CommandLineToArgvW" in source
    assert "CLIENT_GATE_ACCEPT" in source
    assert "CLIENT_GATE_REJECT" in source
    assert "validation-quarantine" in source
    assert "CLIENT_GATE_MATRIX_DIRECT=PASS" in source
