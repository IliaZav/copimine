$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$adminSource = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$electionSource = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$artifactsSource = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$text = (Get-Content -Raw -Encoding UTF8 $adminSource) + "`n" + (Get-Content -Raw -Encoding UTF8 $electionSource)
$artifacts = Get-Content -Raw -Encoding UTF8 $artifactsSource
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-OrderedInMethod([string]$method, [string[]]$needles, [string]$message) {
  $start = $text.IndexOf("private void $method")
  if ($start -lt 0) {
    $script:errors.Add($message)
    return
  }
  $next = $text.IndexOf("`n    private ", $start + 1)
  if ($next -lt 0) { $next = $text.Length }
  $segment = $text.Substring($start, $next - $start)
  $pos = 0
  foreach ($needle in $needles) {
    $found = $segment.IndexOf($needle, $pos)
    if ($found -lt 0) {
      $script:errors.Add($message)
      return
    }
    $pos = $found + $needle.Length
  }
}

Require-Contains 'openElectionCoreHub' 'Admin hub must delegate elections to CopiMineElectionCore.'
Require-Contains 'openElectionRoot' 'ElectionCore root menu is missing.'
Require-Contains 'openManagementMenu' 'ElectionCore management menu is missing.'
Require-Contains 'openStationsMenu' 'ElectionCore station menu is missing.'
Require-Contains 'openCikMenu' 'ElectionCore CIK menu is missing.'
Require-Contains 'openApplicationsMenu' 'ElectionCore application menu is missing.'
Require-Contains 'openResultsMenu' 'ElectionCore results menu is missing.'
Require-Contains 'openPresidentMandateMenu' 'ElectionCore president menu is missing.'
Require-Contains 'openLiveMenu' 'ElectionCore live menu is missing.'
Require-Contains 'renderPinPad' 'Unified terminal PIN panel is missing.'

foreach ($fragment in @(
  'blackmarket:open',
  'cmnarcotics market',
  'sidebar:sound'
)) {
  if ($text.Contains($fragment) -or $artifacts.Contains($fragment)) {
    $script:errors.Add("Legacy GUI fragment remains: $fragment")
  }
}

Require-OrderedInMethod 'openHub' @('open:elections','open:economy','open:players') 'Root admin GUI must expose exactly the three target sections.'

if ($errors.Count -gt 0) {
  throw ("Election GUI polish/integrity validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election GUI polish/integrity validation passed for ElectionCore, three-button admin hub, and removed black market.'
