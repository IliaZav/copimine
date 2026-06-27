$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$expectedJars = @(
  'CopiMineEconomyCore.jar',
  'CopiMineElectionCore.jar',
  'CopiMineUltimateAdminPlus.jar',
  'CopiMineArtifacts.jar',
  'CopiMineNarcotics.jar',
  'CopiMineWorldCore.jar'
)
$pluginDir = Join-Path $root 'minecraft\server\plugins'
foreach ($jar in $expectedJars) {
  $path = Join-Path $pluginDir $jar
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing active plugin jar: $jar") }
  elseif ((Get-Item -LiteralPath $path).Length -le 0) { $errors.Add("Empty active plugin jar: $jar") }
}

$copimineJars = Get-ChildItem -LiteralPath $pluginDir -Filter 'CopiMine*.jar' | Select-Object -ExpandProperty Name
$unexpected = $copimineJars | Where-Object { $_ -notin $expectedJars }
if ($unexpected) { $errors.Add("Unexpected active CopiMine jar(s): $($unexpected -join ', ')") }
if ($copimineJars.Count -ne $expectedJars.Count) { $errors.Add("Expected exactly $($expectedJars.Count) active CopiMine jars, found $($copimineJars.Count).") }

$sourceFiles = @(
  'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java',
  'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java',
  'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java',
  'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java',
  'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java',
  'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java',
  'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java'
)
foreach ($rel in $sourceFiles) {
  if (-not (Test-Path -LiteralPath (Join-Path $root $rel))) { $errors.Add("Missing audited source: $rel") }
}

if ($errors.Count -gt 0) { throw ("All plugins audit failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'All plugins audit passed: active jars and audited sources are present.'
