$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginRoot = Join-Path $root 'copimine-artifacts'
$main = Join-Path $pluginRoot 'src\me\copimine\artifacts\CopiMineArtifacts.java'
$pluginYml = Join-Path $pluginRoot 'plugin.yml'
$migration = Join-Path $root 'db\migrations\20260611_002_copimine_artifacts.sql'
$jar = Join-Path $pluginRoot 'CopiMineArtifacts.jar'
$activeJar = Join-Path $root 'minecraft\server\plugins\CopiMineArtifacts.jar'
$errors = [System.Collections.Generic.List[string]]::new()
if (-not (Test-Path $main)) { $errors.Add('Missing separate CopiMineArtifacts source file.') }
if (-not (Test-Path $pluginYml)) { $errors.Add('Missing separate CopiMineArtifacts plugin.yml.') }
if (-not (Test-Path $migration)) { $errors.Add('Missing CopiMineArtifacts migration file.') }
if (-not (Test-Path (Join-Path $pluginRoot 'items.yml'))) { $errors.Add('Missing artifact items config.') }
if (-not (Test-Path (Join-Path $pluginRoot 'config.yml'))) { $errors.Add('Missing artifact default config.yml.') }
if (-not (Test-Path $jar)) { $errors.Add('Missing built CopiMineArtifacts.jar.') }
if (-not (Test-Path $activeJar)) { $errors.Add('Missing active CopiMineArtifacts.jar in minecraft/server/plugins.') }
if ((Get-Content -Raw $pluginYml) -notmatch 'name:\s*CopiMineArtifacts') { $errors.Add('plugin.yml name mismatch.') }
if ((Get-Content -Raw $pluginYml) -notmatch 'main:\s*me\.copimine\.artifacts\.CopiMineArtifacts') { $errors.Add('plugin.yml main class mismatch.') }
$pluginText = Get-Content -Raw $pluginYml
if ($pluginText -notmatch "(?ms)^depend:\s*(\r?\n)+\s*-\s*CopiMineEconomyCore") { $errors.Add('CopiMineArtifacts must hard-depend on CopiMineEconomyCore.') }
if ($pluginText -notmatch "(?ms)^softdepend:\s*(\r?\n)+\s*-\s*CopiMineUltimateAdminPlus") { $errors.Add('CopiMineArtifacts should keep AdminPlus only as an optional hub/delegator integration.') }
if (Test-Path $activeJar) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($activeJar)
  try {
    foreach ($entry in @('plugin.yml','config.yml','items.yml')) {
      if (-not ($zip.Entries | Where-Object { $_.FullName -eq $entry })) {
        $errors.Add("Active CopiMineArtifacts.jar must embed $entry.")
      }
    }
  } finally {
    $zip.Dispose()
  }
}
if ($errors.Count -gt 0) { throw ("Artifacts structure validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts structure validation passed.'
