$ErrorActionPreference = 'Stop'

$source = Join-Path $PSScriptRoot '..\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 $source

$buttonMatches = [regex]::Matches($text, '\{\s*\d+\s*,\s*Material\.([A-Z0-9_]+)\s*,\s*"([^"]+)"\s*,\s*"([a-z0-9-]+)"\s*\}')
$actions = @()
foreach ($match in $buttonMatches) {
  $actions += [pscustomobject]@{
    Material = $match.Groups[1].Value
    Label = $match.Groups[2].Value
    Action = $match.Groups[3].Value
  }
}

$uniqueActions = @($actions | Select-Object -ExpandProperty Action -Unique)
$requiredActions = @(
  'pumpkin','scare','lightning','explosion','launch','dark','glow','spin','levitate',
  'chickens','shufflehotbar','fakeop','anvil','taxpaper','bee','warden','nausea',
  'fireworkfake','potato','swap','fakeban','horn','confetti','snowball','cobweb',
  'slime','sparkle','note','amethyst','chorus','powdersnow','wind','squid',
  'phantom','fortune','rainbow','totem','clock','spy'
)

if ($uniqueActions.Count -lt 36) {
  throw "Expected at least 36 prank actions, found $($uniqueActions.Count): $($uniqueActions -join ', ')"
}

$missingRequired = @($requiredActions | Where-Object { $_ -notin $uniqueActions })
if ($missingRequired.Count -gt 0) {
  throw "Missing required prank actions: $($missingRequired -join ', ')"
}

$missingHandlers = @()
foreach ($action in $uniqueActions) {
  if ($text -notmatch ('case\s+"' + [regex]::Escape($action) + '"\s*->')) {
    $missingHandlers += $action
  }
}

if ($missingHandlers.Count -gt 0) {
  throw "Menu actions without switch handlers: $($missingHandlers -join ', ')"
}

Write-Host "Prank validation passed: $($uniqueActions.Count) actions with handlers."
