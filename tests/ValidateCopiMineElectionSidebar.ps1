$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$errors = New-Object System.Collections.Generic.List[string]

function Require([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}
function Reject([string]$needle, [string]$message) {
  if ($text.Contains($needle)) { $script:errors.Add($message) }
}

foreach ($needle in @('SidebarSnapshot','refreshSidebarSnapshotAsync','updateSidebar','SIDEBAR_OBJECTIVE','tryBlankNumbersWith')) {
  Require $needle "Sidebar marker missing: $needle"
}

foreach ($bad in @('new String(bytes','ISO_8859_1','windows-1251','Cp1251')) {
  Reject $bad "Sidebar/source contains encoding-risk marker: $bad"
}

Require 'ELECTION_SIDEBAR_RENDER_FAILED' 'Sidebar render error code is missing.'
Require 'show_live_results' 'Sidebar must respect live-result visibility.'

if ($errors.Count -gt 0) {
  throw ("Election sidebar validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election sidebar validation passed.'
