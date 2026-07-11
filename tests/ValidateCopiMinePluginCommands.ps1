$ErrorActionPreference = 'Stop'

$pluginDir = Join-Path $PSScriptRoot '..\minecraft\server\plugins'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$commands = @{}
$aliases = @{}
Get-ChildItem -LiteralPath $pluginDir -File -Filter 'CopiMine*.jar' | ForEach-Object {
  $jar = $_
  $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
  try {
    $entry = $zip.GetEntry('plugin.yml')
    if ($null -eq $entry) { return }
    $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
    $yaml = $reader.ReadToEnd()
    $reader.Dispose()

    $inCommands = $false
    $currentCommand = ''
    foreach ($line in ($yaml -split "`r?`n")) {
      if ($line -match '^commands\s*:') {
        $inCommands = $true
        continue
      }

      if ($inCommands -and $line -match '^\S') {
        $inCommands = $false
        $currentCommand = ''
      }

      if (-not $inCommands) {
        continue
      }

      if ($line -match '^\s{2}([A-Za-z0-9_-]+)\s*:') {
        $currentCommand = $Matches[1].ToLowerInvariant()
        if (-not $commands.ContainsKey($currentCommand)) {
          $commands[$currentCommand] = @()
        }
        $commands[$currentCommand] += $jar.Name
        continue
      }

      if ($currentCommand -and $line -match '^\s{4}aliases\s*:\s*\[(.*)\]\s*$') {
        foreach ($rawAlias in ($Matches[1] -split ',')) {
          $alias = $rawAlias.Trim().Trim("'`"").ToLowerInvariant()
          if ($alias) {
            if (-not $aliases.ContainsKey($alias)) {
              $aliases[$alias] = @()
            }
            $aliases[$alias] += "$($jar.Name):$currentCommand"
          }
        }
      }
    }
  } finally {
    $zip.Dispose()
  }
}

$duplicates = @(
  $commands.GetEnumerator() |
    Where-Object { $_.Value.Count -gt 1 } |
    ForEach-Object { "$($_.Key): $($_.Value -join ', ')" }
)

if ($duplicates.Count -gt 0) {
  throw "Duplicate CopiMine commands found: $($duplicates -join '; ')"
}

$allowedCommands = @(
  'cmultra',
  'cadm',
  'ar',
  'cmbank',
  'appeal',
  'report',
  'reporta',
  'oldvoteoff',
  'rpguard',
  'cmsealdrop',
  'cmartifacts',
  'hidelive',
  'presidentsay',
  'cmworld',
  'cmclient',
  'cmnarcotics',
  'setprice'
)

$forbiddenCommands = @(
  'cmeflow',
  'cmpres',
  'cmreports',
  'cmstations',
  'cmseal',
  'cmballotadmin',
  'cmvote',
  'vote',
  'votes',
  'election',
  'president',
  'candidate',
  'ballot',
  'voteadmin',
  'electionflow',
  'presidentadmin',
  'presadmin',
  'cikstations',
  'stationadmin',
  'cikseal',
  'sealadmin',
  'ballotadmin',
  'ballotsadmin',
  'выборы'
)

$missingRequired = @($allowedCommands | Where-Object { -not $commands.ContainsKey($_) })
if ($missingRequired.Count -gt 0) {
  throw "Missing required CopiMine commands: $($missingRequired -join ', ')"
}

$unexpectedCommands = @($commands.Keys | Where-Object { $_ -notin $allowedCommands })
if ($unexpectedCommands.Count -gt 0) {
  throw "Unexpected CopiMine command roots found: $($unexpectedCommands -join ', ')"
}

$forbiddenRoots = @($forbiddenCommands | Where-Object { $commands.ContainsKey($_) })
if ($forbiddenRoots.Count -gt 0) {
  throw "Forbidden legacy election command roots found: $($forbiddenRoots -join ', ')"
}

$forbiddenAliases = @($forbiddenCommands | Where-Object { $aliases.ContainsKey($_) })
if ($forbiddenAliases.Count -gt 0) {
  throw "Forbidden legacy election aliases found: $($forbiddenAliases -join ', ')"
}

Write-Host "Plugin command validation passed: $($commands.Count) allowed CopiMine commands and no legacy election aliases."
