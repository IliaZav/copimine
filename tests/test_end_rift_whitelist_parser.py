"""Exercise the official probe's whitelist reader without starting Paper."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]

PROBE = r"""
param([string]$RepositoryRoot, [string]$RuntimeRoot)
$ErrorActionPreference = 'Stop'
. (Join-Path $RepositoryRoot 'tests/EndRiftLocalAuthMode.ps1')

$tokens = $null
$errors = $null
$source = Join-Path $RepositoryRoot 'tests/RunEndRiftOfficialTwoPlayerLive.ps1'
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors) { throw 'Could not parse the official live probe.' }
$functions = @($ast.FindAll({
  param($node)
  $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -ceq 'Get-LocalWhitelistNames'
}, $true))
if ($functions.Count -ne 1) { throw 'Expected one official whitelist reader.' }
. ([scriptblock]::Create($functions[0].Extent.Text))

$runtimeRoot = $RuntimeRoot
$serverDir = $RuntimeRoot
$path = Join-Path $RuntimeRoot 'whitelist.json'
$fixtures = @(
  @{ Label = 'empty'; Json = '[]'; Names = '' },
  @{ Label = 'one'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001","name":"Alpha"}]'; Names = 'Alpha' },
  @{ Label = 'two'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001","name":"Alpha"},{"uuid":"00000000-0000-4000-8000-000000000002","name":"Beta_2"}]'; Names = 'Alpha,Beta_2' },
  @{ Label = 'many and additional fields'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001","name":"A","extra":"allowed"},{"uuid":"00000000-0000-4000-8000-000000000002","name":"B"},{"uuid":"00000000-0000-4000-8000-000000000003","name":"C"},{"uuid":"00000000-0000-4000-8000-000000000004","name":"D"},{"uuid":"00000000-0000-4000-8000-000000000005","name":"E"}]'; Names = 'A,B,C,D,E' },
  @{ Label = 'malformed JSON'; Json = '[{"name":'; Reject = $true },
  @{ Label = 'object root'; Json = '{"uuid":"00000000-0000-4000-8000-000000000001","name":"Alpha"}'; Reject = $true },
  @{ Label = 'null root'; Json = 'null'; Reject = $true },
  @{ Label = 'string entry'; Json = '["Alpha"]'; Reject = $true },
  @{ Label = 'null entry'; Json = '[null]'; Reject = $true },
  @{ Label = 'nested array'; Json = '[[{"uuid":"00000000-0000-4000-8000-000000000001","name":"Alpha"}]]'; Reject = $true },
  @{ Label = 'missing name'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001"}]'; Reject = $true },
  @{ Label = 'numeric name'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001","name":123}]'; Reject = $true },
  @{ Label = 'blank name'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001","name":"  "}]'; Reject = $true },
  @{ Label = 'missing uuid'; Json = '[{"name":"Alpha"}]'; Reject = $true },
  @{ Label = 'invalid uuid'; Json = '[{"uuid":"not-a-uuid","name":"Alpha"}]'; Reject = $true },
  @{ Label = 'mixed valid and invalid entries'; Json = '[{"uuid":"00000000-0000-4000-8000-000000000001","name":"Alpha"},null]'; Reject = $true }
)
$failures = [Collections.Generic.List[string]]::new()
foreach ($fixture in $fixtures) {
  [IO.File]::WriteAllText($path, $fixture.Json, [Text.UTF8Encoding]::new($false))
  $problem = $null
  $actual = @()
  try { $actual = @(Get-LocalWhitelistNames) } catch { $problem = $_ }
  if ($fixture.Reject) {
    if ($null -eq $problem) { $failures.Add("$($fixture.Label): accepted invalid input") }
  } elseif ($null -ne $problem) {
    $failures.Add("$($fixture.Label): unexpected error $problem")
  } elseif (($actual -join ',') -cne $fixture.Names) {
    $failures.Add("$($fixture.Label): wrong names $($actual -join ',')")
  }
}
if ($failures.Count -gt 0) { throw ($failures -join '; ') }
Write-Output ("WHITELIST_PARSER_PASS PowerShell=$($PSVersionTable.PSVersion) fixtures=$($fixtures.Count)")
"""


@pytest.mark.parametrize("shell_name", ["powershell.exe", "pwsh.exe"])
def test_official_probe_rejects_malformed_whitelist_and_reads_valid_arrays(
    shell_name: str, tmp_path: Path
) -> None:
    shell = shutil.which(shell_name)
    if shell is None:
        pytest.skip(f"{shell_name} is unavailable")
    script = tmp_path / "probe-whitelist.ps1"
    script.write_text(PROBE, encoding="utf-8")
    result = subprocess.run(
        [shell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
         "-File", str(script), str(ROOT), str(tmp_path)],
        capture_output=True,
        text=True,
        timeout=60,
        cwd=ROOT,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert "WHITELIST_PARSER_PASS" in result.stdout
