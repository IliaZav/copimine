$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$Paths = @{
  Election = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
  Admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
  AdminConfig = Join-Path $root 'copimine-admin-plugin\config.yml'
  AdminPluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'
  Artifacts = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
  ArtifactsConfig = Join-Path $root 'copimine-artifacts\config.yml'
  Economy = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'
  EconomyPluginYml = Join-Path $root 'copimine-economy-core\plugin.yml'
  ElectionPluginYml = Join-Path $root 'copimine-election-core\plugin.yml'
  ArtifactsPluginYml = Join-Path $root 'copimine-artifacts\plugin.yml'
  MainPy = Join-Path $root 'admin-web\backend\main.py'
  Discord = Join-Path $root 'admin-web\backend\discord_bot.py'
  FrontendApp = Join-Path $root 'admin-web\frontend\assets\app.js'
  FrontendLegacy = Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js'
  FrontendHomepage = Join-Path $root 'admin-web\frontend\assets\js\public\homepage.js'
  ServerProperties = Join-Path $root 'minecraft\server\server.properties'
  ResourcePackZip = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
  Migration007 = Join-Path $root 'db\migrations\20260621_007_copimine_election_core_rebuild.sql'
  Migration008 = Join-Path $root 'db\migrations\20260623_008_copimine_election_core_phase1_stability.sql'
}

function Read-Utf8([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) {
    throw "Missing file: $path"
  }
  Get-Content -Raw -Encoding UTF8 $path
}

function New-ErrorList {
  New-Object System.Collections.Generic.List[string]
}

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) {
    $script:errors.Add($message)
  }
}

function Require-NotContains([string]$text, [string]$needle, [string]$message) {
  if ($text.Contains($needle)) {
    $script:errors.Add($message)
  }
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, 'Singleline')) {
    $script:errors.Add($message)
  }
}

function Require-NotRegex([string]$text, [string]$pattern, [string]$message) {
  if ([regex]::IsMatch($text, $pattern, 'Singleline')) {
    $script:errors.Add($message)
  }
}

function Method-Body([string]$text, [string]$signature) {
  $start = $text.IndexOf($signature)
  if ($start -lt 0) {
    return $null
  }
  $nextPrivate = $text.IndexOf("`n    private ", $start + $signature.Length)
  $nextRecord = $text.IndexOf("`n    private record ", $start + $signature.Length)
  $nextEnum = $text.IndexOf("`n    private enum ", $start + $signature.Length)
  $next = @($nextPrivate, $nextRecord, $nextEnum) | Where-Object { $_ -ge 0 } | Sort-Object | Select-Object -First 1
  if ($null -eq $next) {
    $next = $text.Length
  }
  return $text.Substring($start, $next - $start)
}

function Throw-IfErrors([string]$name) {
  if ($errors.Count -gt 0) {
    throw ($name + " failed:`n - " + ($errors -join "`n - "))
  }
  Write-Host ($name + ' passed.')
}
