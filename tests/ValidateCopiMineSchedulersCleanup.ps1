$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$auth = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java')

foreach ($pair in @(
  @($admin,'sidebarTask.cancel()'),
  @($admin,'inventorySnapshotTask.cancel()'),
  @($admin,'nameplateTask.cancel()'),
  @($artifacts,'deliveryTask.cancel()'),
  @($artifacts,'sessionCleanupTask.cancel()'),
  @($narcotics,'Bukkit.getScheduler().cancelTasks(this)'),
  @($auth,'Bukkit.getScheduler().cancelTasks(this)')
)) {
  if ($pair[0] -notmatch [regex]::Escape($pair[1])) { $errors.Add("Missing scheduler cleanup marker: $($pair[1])") }
}
if ($artifacts -notmatch '20L \* 60L, 20L \* 60L') { $errors.Add('Artifacts repeating tasks must be one minute or slower.') }
if ($narcotics -match 'runTaskTimer|scheduleSyncRepeatingTask') { $errors.Add('Narcotics should not add repeating scheduler tasks.') }

if ($errors.Count -gt 0) { throw ("Schedulers cleanup validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Schedulers cleanup validation passed.'
