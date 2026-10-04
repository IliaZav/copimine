"""Guard the End Rift test gate against masking an earlier pytest failure."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "RunEndRiftEventChecks.ps1"


def test_full_build_stages_the_actual_client_before_artifact_validation() -> None:
    source = RUNNER.read_text(encoding="utf-8")
    assert "scripts\\thirdparty\\build_modpack.ps1" in source
    assert "-SyncBuiltClient" in source
    assert source.index("-SyncBuiltClient") < source.index("Invoke-GateStep 'Current Python contract'")


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
    assert current_gate.count("& python -m pytest") == 5
    first_pytest = current_gate.index("& python -m pytest")
    second_pytest = current_gate.index("& python -m pytest", first_pytest + 1)
    first_exit_check = current_gate.index("if ($LASTEXITCODE -ne 0)", first_pytest)
    second_exit_check = current_gate.index("if ($LASTEXITCODE -ne 0)", second_pytest)
    assert first_pytest < first_exit_check < second_pytest
    assert second_pytest < second_exit_check
    third_pytest = current_gate.index("& python -m pytest", second_pytest + 1)
    third_exit_check = current_gate.index("if ($LASTEXITCODE -ne 0)", third_pytest)
    assert second_exit_check < third_pytest < third_exit_check
    fourth_pytest = current_gate.index("& python -m pytest", third_pytest + 1)
    fourth_exit_check = current_gate.index("if ($LASTEXITCODE -ne 0)", fourth_pytest)
    assert third_exit_check < fourth_pytest < fourth_exit_check
    fifth_pytest = current_gate.index("& python -m pytest", fourth_pytest + 1)
    fifth_exit_check = current_gate.index("if ($LASTEXITCODE -ne 0)", fifth_pytest)
    assert fourth_exit_check < fifth_pytest < fifth_exit_check
    assert restart_gate.count("& python -m pytest") == 1
