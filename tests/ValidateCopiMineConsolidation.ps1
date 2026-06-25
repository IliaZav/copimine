$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$plugins = Join-Path $root 'minecraft\server\plugins'
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'
$text = Get-Content -Raw -Encoding UTF8 $source
$pluginText = Get-Content -Raw -Encoding UTF8 $pluginYml

$activeAdminPrankPatch = Join-Path $plugins 'CopiMineAdminPrankPatch.jar'
if (Test-Path -LiteralPath $activeAdminPrankPatch) {
  throw 'CopiMineAdminPrankPatch.jar is still active; its player prank functions must live in CopiMineUltimateAdminPlus.'
}

$inactiveCopiMineDataDirs = @('CopiMineElections', 'CopiMineEconomyGuard')
foreach ($dirName in $inactiveCopiMineDataDirs) {
  $dataDir = Join-Path $plugins $dirName
  $jar = Join-Path $plugins ($dirName + '.jar')
  if ((Test-Path -LiteralPath $dataDir) -and -not (Test-Path -LiteralPath $jar)) {
    throw "Inactive legacy data directory is still in active plugins folder: $dirName"
  }
}

$mergedPranks = @('freeze', 'jumpscare', 'blind', 'bees', 'fsb')
foreach ($action in $mergedPranks) {
  if ($text -notmatch ('"' + [regex]::Escape($action) + '"')) {
    throw "Merged AdminPrankPatch action is missing from AdminPlus menu: $action"
  }
  if ($text -notmatch ('case\s+"' + [regex]::Escape($action) + '"\s*->')) {
    throw "Merged AdminPrankPatch action is missing a handler: $action"
  }
}

if ($text -match 'case\s+"old"\s*->' -or $text -match 'performCommand\("cadm"\)') {
  throw 'AdminPlus still exposes a bridge back to the old cadm admin.'
}

$mergedGuardJars = @{
  'CopiMineRpCommandGuard.jar' = 'isBlockedRpCommand'
  'CopiMineSealDropFix.jar' = 'isElectionSeal'
  'CopiMineOfficialItemGuard.jar' = 'isProtectedOfficialItem'
}

foreach ($jarName in $mergedGuardJars.Keys) {
  $jar = Join-Path $plugins $jarName
  if (Test-Path -LiteralPath $jar) {
    throw "$jarName is still active; its guard behavior must live in CopiMineUltimateAdminPlus."
  }

  $method = $mergedGuardJars[$jarName]
  if ($text -notmatch ('private .* ' + [regex]::Escape($method) + '\(')) {
    throw "Merged guard method is missing from AdminPlus: $method"
  }
}

if ($pluginText -notmatch '(?m)^  rpguard:\s*$') {
  throw 'AdminPlus plugin.yml must own the rpguard diagnostic command after merging RpCommandGuard.'
}

if ($pluginText -notmatch '(?m)^  cmsealdrop:\s*$') {
  throw 'AdminPlus plugin.yml must own the cmsealdrop command after merging SealDropFix.'
}

$activeCoreShim = Join-Path $plugins 'CopiMineUltimateAdmin.jar'
if (Test-Path -LiteralPath $activeCoreShim) {
  throw 'CopiMineUltimateAdmin.jar is still active; cadm/report/appeal/oldvoteoff/PlaceholderAPI shim must live in CopiMineUltimateAdminPlus.'
}

foreach ($command in @('cadm', 'ar', 'appeal', 'report', 'oldvoteoff')) {
  if ($pluginText -notmatch ('(?m)^  ' + [regex]::Escape($command) + ':\s*$')) {
    throw "AdminPlus plugin.yml must own merged core-shim command: $command"
  }
}

foreach ($method in @('handleReport', 'handleOldVoteOff', 'removeOldElectionSidebar', 'latestWinnerName', 'arTotal')) {
  if ($text -notmatch ('private .* ' + [regex]::Escape($method) + '\(')) {
    throw "Core-shim method is missing from AdminPlus: $method"
  }
}

if ($text -notmatch 'extends PlaceholderExpansion') {
  throw 'PlaceholderAPI expansion shim is not merged into AdminPlus.'
}

$legacyBuildDirs = @(
  Get-ChildItem -LiteralPath $root -Directory |
    Where-Object { $_.Name -match '^(ultimate-|ultra-v)' } |
    Select-Object -ExpandProperty Name
)
if ($legacyBuildDirs.Count -gt 0) {
  throw "Legacy ultimate/ultra build folders are still in active root: $($legacyBuildDirs -join ', ')"
}

Write-Host 'Consolidation validation passed: pranks, guards, core shim, and legacy ultimate build folders are merged/archived.'
