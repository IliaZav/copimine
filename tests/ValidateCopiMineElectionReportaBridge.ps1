$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$election = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java')
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')

$errors = New-Object System.Collections.Generic.List[string]
function Require([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

Require $admin 'public void capturePluginError' 'AdminPlus must expose a narrow plugin-error bridge for reporta context capture.'
Require $admin 'capturePluginError' 'AdminPlus reporta bridge method is missing.'
Require $election 'capturePluginError' 'ElectionCore must forward handled errors to AdminPlus.'
Require $election 'getPlugin("CopiMineUltimateAdminPlus")' 'ElectionCore must resolve AdminPlus without a compile-time dependency.'
Require $election 'publicOperationDetail(error)' 'ElectionCore must retain a safe fallback when the reporta bridge is unavailable.'

if ($errors.Count -gt 0) {
  throw ("Election reporta bridge validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election reporta bridge validation passed.'
