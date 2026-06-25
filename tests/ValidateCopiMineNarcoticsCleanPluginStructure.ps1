$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$required = @(
  'copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java',
  'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java',
  'copimine-narcotics\src\me\copimine\narcotics\recipe\NarcoticsRecipeService.java',
  'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java',
  'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java',
  'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java',
  'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java'
)
$missing = @($required | Where-Object { -not (Test-Path (Join-Path $root $_)) })
if ($missing.Count -gt 0) { throw ("Missing clean plugin structure files:`n - " + ($missing -join "`n - ")) }
Write-Host 'Narcotics clean plugin structure validation passed.'
