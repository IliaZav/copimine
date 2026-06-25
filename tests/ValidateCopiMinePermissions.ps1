$ErrorActionPreference = 'Stop'

$plugins = Resolve-Path (Join-Path $PSScriptRoot '..\minecraft\server\plugins')
$jars = Get-ChildItem -Path $plugins -File -Filter 'CopiMine*.jar'
$seen = @{}

Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($jar in $jars) {
  $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
  try {
    $entry = $zip.GetEntry('plugin.yml')
    if ($null -eq $entry) {
      continue
    }

    $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8, $true)
    $pluginYml = $reader.ReadToEnd()
    $reader.Dispose()

    $inPermissions = $false
    foreach ($line in ($pluginYml -split "`r?`n")) {
      if ($line -match '^permissions\s*:') {
        $inPermissions = $true
        continue
      }

      if ($inPermissions -and $line -match '^\S') {
        $inPermissions = $false
      }

      if ($inPermissions -and $line -match '^\s{2}([A-Za-z0-9_.-]+)\s*:') {
        $permission = $Matches[1]
        if (-not $seen.ContainsKey($permission)) {
          $seen[$permission] = New-Object System.Collections.Generic.List[string]
        }
        [void]$seen[$permission].Add($jar.Name)
      }
    }
  } finally {
    $zip.Dispose()
  }
}

$duplicates = @()
foreach ($permission in $seen.Keys | Sort-Object) {
  if ($seen[$permission].Count -gt 1) {
    $duplicates += "$permission => $($seen[$permission] -join ', ')"
  }
}

if ($duplicates.Count -gt 0) {
  throw "Duplicate CopiMine permission declarations found:`n$($duplicates -join "`n")"
}

$requiredPermissions = @(
  'copimine.admin',
  'copimine.election.admin',
  'copimine.election.cik',
  'copimine.election.president',
  'copimine.election.debug',
  'copimine.economy.admin',
  'copimine.bank.admin',
  'copimine.diagnostics',
  'copimine.players.admin',
  'copimine.rpguard.bypass'
)

$forbiddenPermissions = @(
  'copimine.elections.admin',
  'copimine.elections.curator',
  'copimine.ar.admin',
  'copimine.president',
  'group.president'
)

$missingRequired = @($requiredPermissions | Where-Object { -not $seen.ContainsKey($_) })
if ($missingRequired.Count -gt 0) {
  throw "Missing required CopiMine permissions: $($missingRequired -join ', ')"
}

$oldPermissions = @($forbiddenPermissions | Where-Object { $seen.ContainsKey($_) })
if ($oldPermissions.Count -gt 0) {
  throw "Forbidden legacy CopiMine permissions found: $($oldPermissions -join ', ')"
}

Write-Host "Permission validation passed: $($seen.Count) target CopiMine permissions."
