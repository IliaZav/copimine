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
    test_build_marker = "$testBuild = Join-Path ([IO.Path]::GetTempPath())"

    assert current_marker in source
    assert restart_marker in source
    current_start = source.index(current_marker) + len(current_marker)
    restart_start = source.index(restart_marker)
    build_start = source.index(test_build_marker, restart_start)

    current_gate = source[current_start:restart_start]
    restart_gate = source[restart_start:build_start]
    assert current_gate.count("& python -m pytest") == 7
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
    sixth_pytest = current_gate.index("& python -m pytest", fifth_pytest + 1)
    sixth_exit_check = current_gate.index("if ($LASTEXITCODE -ne 0)", sixth_pytest)
    assert fifth_exit_check < sixth_pytest < sixth_exit_check
    assert "test_end_rift_wave2_cleanup_epoch_contract.py" in current_gate
    assert "test_wave2_unlaunchable_start_contract.py" in current_gate
    assert restart_gate.count("& python -m pytest") == 1
    assert "CopiMine-EndRiftEventChecks-" in source
    assert "tests\\build\\end-event-current" not in source


def test_temporary_java_build_directory_is_removed_after_gate_completion_or_failure() -> None:
    source = RUNNER.read_text(encoding="utf-8")
    create_directory = source.index("New-Item -ItemType Directory -Path $testBuild | Out-Null")
    ownership_marker = source.index("$testBuildCreated = $true", create_directory)
    build_try = source.index("try {", ownership_marker)
    evidence_gate = source.index("Invoke-GateStep 'Acceptance evidence matrices and plugin inventory'")
    artifact_hashes = source.index("Write-Host '== Current artifact hashes =='")
    cleanup = source.index("Remove-Item -LiteralPath $testBuild -Recurse -Force")
    cleanup_finally = source.rfind("finally {", artifact_hashes, cleanup)

    assert create_directory < ownership_marker < build_try < evidence_gate < artifact_hashes < cleanup_finally < cleanup, (
        "The isolated Java build output must be removed from finally after success or any gate failure, including the evidence matrix step"
    )


def test_gate_pins_jdk25_for_nested_plugin_compilers() -> None:
    source = RUNNER.read_text(encoding="utf-8")
    java_version_check = source.index("$javaVersion -notmatch 'openjdk version \"25\\.0\\.4\\.1'")
    build_start = source.index("if (-not $SkipBuilds)")

    java_home_pin = source.index("$env:JAVA_HOME = $javaHome", java_version_check)
    path_pin = source.index('$env:PATH = \"$javaHome\\bin;$env:PATH\"', java_version_check)

    assert java_version_check < java_home_pin < build_start
    assert java_version_check < path_pin < build_start
