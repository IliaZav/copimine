$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$authEffects = Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java'

if (-not (Test-Path -LiteralPath $authEffects)) {
  throw "Missing source: $authEffects"
}

$java = Get-Content -Raw -Encoding UTF8 $authEffects
$errors = New-Object System.Collections.Generic.List[string]

foreach ($marker in @(
  'AUTH_EVENT_CLASSES',
  'registerOptionalAuthEvents()',
  'Bukkit.getPluginManager().registerEvent(',
  'Class.forName(className, false, loader)',
  'EventPriority.MONITOR',
  'handleAuthEvent(event)',
  'Optional auth hooks not present:'
)) {
  if (-not $java.Contains($marker)) {
    $errors.Add("Missing auth hook marker: $marker")
  }
}

if ($java.Contains('public void onAnyAuthEvent(')) {
  $errors.Add('Generic @EventHandler fallback still exists; optional hook registration must be the live path.')
}

if ($errors.Count -gt 0) {
  throw ("Auth hook validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineAuthOptionalHookRegistration passed.'
