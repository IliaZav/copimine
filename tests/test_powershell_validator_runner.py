import os
from pathlib import Path
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("shell_name", ["pwsh", "powershell"])
def test_validator_runner_counts_a_nonzero_child_exit_as_failure(tmp_path, shell_name):
    shell = shutil.which(shell_name)
    if shell is None:
        pytest.skip(f"{shell_name} is unavailable on this host")

    runner = tmp_path / "RunCopiMineValidators.ps1"
    shutil.copyfile(ROOT / "tests/RunCopiMineValidators.ps1", runner)
    (tmp_path / "ValidateRunnerExitProbe.ps1").write_text(
        "Write-Output 'probe validator failed'; exit 23\n", encoding="utf-8"
    )

    result = subprocess.run(
        [shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
         "-File", str(runner), "-Pattern", "ValidateRunnerExitProbe.ps1"],
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=False,
    )

    output = result.stdout + result.stderr
    assert result.returncode != 0, output
    assert "VALIDATOR_SUMMARY total=1 passed=0 failed=1 skipped=0" in output
    assert "ValidateRunnerExitProbe.ps1" in output
    assert "23" in output


@pytest.mark.parametrize("shell_name", ["pwsh", "powershell"])
def test_validator_runner_routes_migration_inputs_and_keeps_generic_validators_zero_arg(
    tmp_path, shell_name
):
    shell = shutil.which(shell_name)
    if shell is None:
        pytest.skip(f"{shell_name} is unavailable on this host")

    runner = tmp_path / "RunCopiMineValidators.ps1"
    shutil.copyfile(ROOT / "tests/RunCopiMineValidators.ps1", runner)
    result_log = tmp_path / "validator-arguments.txt"

    (tmp_path / "ValidateCopiMinePaper263CompileDependencies.ps1").write_text(
        "param([Parameter(Mandatory)][string]$JavaHome, "
        "[Parameter(Mandatory)][string]$MavenRepository, "
        "[Parameter(Mandatory)][string]$PaperApiJar)\n"
        "[IO.File]::AppendAllText($env:VALIDATOR_ARGUMENT_LOG, "
        "('paper|' + $JavaHome + '|' + $MavenRepository + '|' + $PaperApiJar + [Environment]::NewLine))\n",
        encoding="utf-8",
    )
    (tmp_path / "ValidateClientBridgeV4FailClosed.ps1").write_text(
        "param([Parameter(Mandatory)][string]$JavaHome, "
        "[Parameter(Mandatory)][string]$GradleExecutable)\n"
        "[IO.File]::AppendAllText($env:VALIDATOR_ARGUMENT_LOG, "
        "('bridge|' + $JavaHome + '|' + $GradleExecutable + [Environment]::NewLine))\n",
        encoding="utf-8",
    )
    (tmp_path / "ValidateRunnerZeroArgProbe.ps1").write_text(
        "if ($args.Count -ne 0) { throw 'generic validator received arguments' }\n"
        "[IO.File]::AppendAllText($env:VALIDATOR_ARGUMENT_LOG, 'generic|zero-args' + [Environment]::NewLine)\n",
        encoding="utf-8",
    )

    migration_java = str(tmp_path / "jdk-25")
    migration_gradle = str(tmp_path / "gradle-9.8.1" / "bin" / "gradle.bat")
    maven_repository = str(tmp_path / "m2-repository")
    paper_api = str(tmp_path / "m2-repository" / "io" / "papermc" / "paper-api-26.3.jar")
    environment = os.environ.copy()
    environment["VALIDATOR_ARGUMENT_LOG"] = str(result_log)
    result = subprocess.run(
        [
            shell,
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(runner),
            "-Pattern",
            "Validate*.ps1",
            "-MigrationJavaHome",
            migration_java,
            "-MigrationGradleExecutable",
            migration_gradle,
            "-MavenRepository",
            maven_repository,
            "-PaperApiJar",
            paper_api,
        ],
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=False,
        env=environment,
    )

    output = result.stdout + result.stderr
    assert result.returncode == 0, output
    assert "VALIDATOR_SUMMARY total=3 passed=3 failed=0 skipped=0" in output
    assert set(result_log.read_text(encoding="utf-8").splitlines()) == {
        f"paper|{migration_java}|{maven_repository}|{paper_api}",
        f"bridge|{migration_java}|{migration_gradle}",
        "generic|zero-args",
    }


def test_java_plugin_gate_supplies_migration_runtime_and_synchronized_resource_pack():
    java_plugin_gate = (ROOT / "scripts/minecraft/RunJavaPluginCi.ps1").read_text(encoding="utf-8")
    workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")

    validator_call = java_plugin_gate.index("tests/RunCopiMineValidators.ps1")
    validator_setup = java_plugin_gate[max(0, validator_call - 600) : validator_call + 250]
    assert "-MigrationJavaHome $migrationJdk" in validator_setup
    assert "-MigrationGradleExecutable $MigrationGradleExecutable" in validator_setup
    assert "-MavenRepository $mavenRepository" in validator_setup
    assert "-PaperApiJar $paper263ApiJar" in validator_setup

    pack_build = java_plugin_gate.index("resourcepacks/build-resourcepack.ps1")
    pack_build_call = java_plugin_gate[pack_build - 180 : pack_build + 180]
    assert "-SkipServerProperties" not in pack_build_call
    assert pack_build < validator_call
    assert "'minecraft/server/server.properties'" in java_plugin_gate

    java_job = workflow.split("\n  java-plugins:\n", 1)[1]
    java21_record = java_job.index("COPIMINE_JAVA_21_HOME=$env:JAVA_HOME")
    java25_setup = java_job.index("Set up Java 25 for Java plugin validators")
    gate_invocation = java_job.index("Run shared Java plugin release gate")
    assert java21_record < java25_setup < gate_invocation
    java25_section = java_job[java25_setup:gate_invocation]
    assert "25.0.4.1" in java25_section
    assert "COPIMINE_JAVA_21_HOME" in java_job[gate_invocation : gate_invocation + 1000]
    assert "-MigrationJavaHome $env:JAVA_HOME" in java_job[gate_invocation : gate_invocation + 1000]
    assert (
        "-MigrationGradleExecutable $env:COPIMINE_MIGRATION_GRADLE_EXECUTABLE"
        in java_job[gate_invocation : gate_invocation + 1000]
    )


@pytest.mark.parametrize("shell_name", ["pwsh", "powershell"])
def test_validator_runner_fails_closed_when_required_migration_inputs_are_missing(
    tmp_path, shell_name
):
    shell = shutil.which(shell_name)
    if shell is None:
        pytest.skip(f"{shell_name} is unavailable on this host")

    runner = tmp_path / "RunCopiMineValidators.ps1"
    shutil.copyfile(ROOT / "tests/RunCopiMineValidators.ps1", runner)
    marker = tmp_path / "validator-was-run.txt"
    (tmp_path / "ValidateCopiMinePaper263CompileDependencies.ps1").write_text(
        "[IO.File]::WriteAllText($env:VALIDATOR_WAS_RUN, 'unexpected')\n",
        encoding="utf-8",
    )
    environment = os.environ.copy()
    environment["VALIDATOR_WAS_RUN"] = str(marker)

    result = subprocess.run(
        [
            shell,
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(runner),
            "-Pattern",
            "ValidateCopiMinePaper263CompileDependencies.ps1",
        ],
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=False,
        env=environment,
    )

    output = result.stdout + result.stderr
    assert result.returncode != 0, output
    assert "VALIDATOR_SUMMARY total=1 passed=0 failed=1 skipped=0" in output
    assert "requires MigrationJavaHome, MavenRepository, and PaperApiJar" in output
    assert not marker.exists()
