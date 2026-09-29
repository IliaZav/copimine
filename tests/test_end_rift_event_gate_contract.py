"""Guard the End Rift test gate against masking an earlier pytest failure."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "RunEndRiftEventChecks.ps1"


def test_each_python_contract_suite_has_its_own_checked_gate_step() -> None:
    source = RUNNER.read_text(encoding="utf-8")
    current_marker = "Invoke-GateStep 'Current Python contract' {"
    restart_marker = "Invoke-GateStep 'Ritual restart contract' {"
    test_build_marker = "$testBuild = Join-Path $root 'tests\\build\\end-event-current'"

    assert current_marker in source
    assert restart_marker in source
    current_start = source.index(current_marker) + len(current_marker)
    restart_start = source.index(restart_marker)
    build_start = source.index(test_build_marker, restart_start)

    current_gate = source[current_start:restart_start]
    restart_gate = source[restart_start:build_start]
    assert current_gate.count("& python -m pytest") == 1
    assert restart_gate.count("& python -m pytest") == 1
