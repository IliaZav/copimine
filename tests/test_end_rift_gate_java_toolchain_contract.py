"""Regression contract for the End Rift gate's pinned JDK selection.

The hosted java-plugins job installs the pinned Temurin 25.0.4.1 archive through
actions/setup-java, so it is not present at the developer-only
``local-runtime/toolchains`` path.  The gate must accept an explicit JDK path,
validate the exact pinned java/javac versions, and keep the local path only as a
fallback.  ``RunJavaPluginCi.ps1`` must forward the JDK it already verified.
"""

from __future__ import annotations

import shutil
import subprocess
import textwrap
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "tests" / "RunEndRiftEventChecks.ps1"
RUNNER = ROOT / "scripts" / "minecraft" / "RunJavaPluginCi.ps1"
PINNED_JDK = ROOT / "local-runtime" / "toolchains" / "temurin-25.0.4.1" / "jdk-25.0.4.1+1"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_gate_exposes_explicit_java_home_and_keeps_local_fallback() -> None:
    gate = _read(GATE)
    assert "[string]$JavaHome" in gate
    assert "Resolve-PinnedEndRiftJavaHome" in gate
    assert "local-runtime\\toolchains\\temurin-25.0.4.1\\jdk-25.0.4.1+1" in gate
    assert "Resolve-PinnedEndRiftJavaHome -Requested $JavaHome -RepositoryRoot $root" in gate


def test_shared_runner_forwards_the_verified_migration_jdk_to_the_child_gate() -> None:
    runner = _read(RUNNER)
    invocation = runner[runner.index("$eventRunnerPath =") :]
    assert "'-JavaHome', $eventJavaHomeArgument" in invocation
    assert "$eventJavaHomeArgument = '\"{0}\"' -f $migrationJdk" in invocation
    assert "Start-Process -FilePath 'powershell.exe' -ArgumentList $eventRunnerArguments" in invocation


def test_gate_requires_the_exact_pinned_java_and_javac_versions() -> None:
    gate = _read(GATE)
    assert "openjdk version \"25\\.0\\.4\\.1" in gate
    assert "javac 25\\.0\\.4\\.1" in gate


@pytest.mark.skipif(shutil.which("powershell") is None and shutil.which("pwsh") is None,
                    reason="PowerShell is required for the End Rift gate contract")
def test_pinned_jdk_resolver_accepts_hosted_explicit_path_and_local_fallback(tmp_path: Path) -> None:
    shell = shutil.which("pwsh") or shutil.which("powershell")
    if not PINNED_JDK.is_dir():
        pytest.skip("The local pinned Temurin 25.0.4.1 toolchain is not installed")

    spaced_jdk = tmp_path / "hosted jdk with spaces"
    junction = subprocess.run(
        ["cmd", "/c", "mklink", "/J", str(spaced_jdk), str(PINNED_JDK)],
        capture_output=True,
        text=True,
    )
    if junction.returncode != 0:
        pytest.skip(f"Directory junction creation is unavailable: {junction.stdout}{junction.stderr}")

    empty_root = tmp_path / "repo without toolchain"
    empty_root.mkdir()
    empty_jdk = tmp_path / "empty jdk"
    empty_jdk.mkdir()
    system_java_21 = Path(shutil.which("java") or "")
    if not system_java_21.name.lower() == "java.exe":
        java_21_home = ""
    else:
        java_21_home = str(system_java_21.parent.parent)

    gate_path = str(GATE).replace("'", "''")
    repository = str(ROOT).replace("'", "''")
    spaced = str(spaced_jdk).replace("'", "''")
    empty_root_path = str(empty_root).replace("'", "''")
    empty_jdk_path = str(empty_jdk).replace("'", "''")
    java_21_path = java_21_home.replace("'", "''")
    missing_path = str(tmp_path / "missing jdk").replace("'", "''")
    probe = tmp_path / "probe-end-rift-jdk-resolver.ps1"
    probe.write_text(
        textwrap.dedent(
            f"""\
            $ErrorActionPreference = 'Stop'
            $scriptPath = '{gate_path}'
            $tokens = $null
            $errors = $null
            $ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
            if ($errors) {{ throw ($errors | Out-String) }}
            $functions = @($ast.FindAll({{ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Resolve-PinnedEndRiftJavaHome' }}, $true))
            if ($functions.Count -ne 1) {{ throw 'Expected exactly one Resolve-PinnedEndRiftJavaHome function.' }}
            . ([scriptblock]::Create($functions[0].Extent.Text))

            $explicit = Resolve-PinnedEndRiftJavaHome -Requested '{spaced}' -RepositoryRoot '{repository}'
            if ($explicit.JavaHome -ne (Resolve-Path -LiteralPath '{spaced}').Path) {{ throw 'Explicit JDK path was not preserved.' }}
            if ($explicit.Source -ne 'explicit -JavaHome') {{ throw 'Explicit JDK source label is wrong.' }}
            if (-not (Test-Path -LiteralPath $explicit.JavacExecutable -PathType Leaf)) {{ throw 'Explicit javac was not resolved.' }}

            $fallback = Resolve-PinnedEndRiftJavaHome -Requested '' -RepositoryRoot '{repository}'
            if ($fallback.Source -ne 'local-runtime fallback') {{ throw 'Local fallback source label is wrong.' }}
            if (-not (Test-Path -LiteralPath $fallback.JavaExecutable -PathType Leaf)) {{ throw 'Local fallback java was not resolved.' }}

            $failed = $false
            try {{ Resolve-PinnedEndRiftJavaHome -Requested '' -RepositoryRoot '{empty_root_path}' | Out-Null }}
            catch {{ $failed = $_.Exception.Message -match 'toolchain; local-runtime fallback path does not exist' }}
            if (-not $failed) {{ throw 'A missing local-runtime toolchain was accepted.' }}

            $failed = $false
            try {{ Resolve-PinnedEndRiftJavaHome -Requested '{missing_path}' -RepositoryRoot '{repository}' | Out-Null }}
            catch {{ $failed = $_.Exception.Message -match 'explicit -JavaHome path does not exist' }}
            if (-not $failed) {{ throw 'A missing explicit JDK path was accepted.' }}

            $failed = $false
            try {{ Resolve-PinnedEndRiftJavaHome -Requested '{empty_jdk_path}' -RepositoryRoot '{repository}' | Out-Null }}
            catch {{ $failed = $_.Exception.Message -match 'path is incomplete' }}
            if (-not $failed) {{ throw 'An incomplete explicit JDK path was accepted.' }}

            if ('{java_21_path}' -ne '') {{
                $failed = $false
                try {{ Resolve-PinnedEndRiftJavaHome -Requested '{java_21_path}' -RepositoryRoot '{repository}' | Out-Null }}
                catch {{ $failed = $_.Exception.Message -match 'unexpected runtime' }}
                if (-not $failed) {{ throw 'A non-pinned Java runtime was accepted.' }}
            }}
            Write-Host 'pinned JDK resolver regression passed'
            """
        ),
        encoding="utf-8",
    )
    try:
        result = subprocess.run(
            [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(probe)],
            capture_output=True,
            text=True,
            timeout=120,
        )
        assert result.returncode == 0, result.stdout + result.stderr
    finally:
        subprocess.run(["cmd", "/c", "rmdir", str(spaced_jdk)], capture_output=True, text=True)