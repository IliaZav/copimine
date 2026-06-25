. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$files = @(
  $Paths.Election,
  $Paths.Economy,
  $Paths.Artifacts,
  (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java'),
  (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')
) | Where-Object { Test-Path $_ }

$combined = ($files | ForEach-Object { Get-Content -Raw -Encoding UTF8 $_ }) -join "`n"

Require-NotRegex $combined 'Bukkit\.getWorlds\(\)[\s\S]{0,800}getEntitiesByClass\(ItemDisplay\.class\)' 'Active plugins must not scan every world and every ItemDisplay for routine visual repair/cleanup.'
Require-NotRegex $combined 'for\s*\(\s*World\s+\w+\s*:\s*Bukkit\.getWorlds\(\)\s*\)[\s\S]{0,900}getNearbyEntities\(' 'Visual cleanup/repair must not combine full-world loops with entity scans.'

Throw-IfErrors 'ValidateCopiMinePluginTasksNoFullWorldScan'
