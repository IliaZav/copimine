[CmdletBinding()]
param(
  [ValidateRange(900, 3600)]
  [int]$BotDurationSeconds = 1800,
  [ValidateRange(600, 3500)]
  [int]$TimeoutSeconds = 1700
)

# Five disposable Mineflayer clients run the real official End Rift state
# machine. The delegated driver only touches the isolated local Paper scene;
# it never connects to a production address or resets the world/database.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$driver = Join-Path $root 'tests\RunEndRiftOfficialTwoPlayerLive.ps1'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Five-player run refused Git branch '$branch'."
}
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Five-player run refused a non-local End Rift configuration.'
}
if (-not (Test-Path -LiteralPath $driver -PathType Leaf)) {
  throw "Shared official driver is missing: $driver"
}

$names = @('EndRiftFiveA', 'EndRiftFiveB', 'EndRiftFiveC', 'EndRiftFiveD', 'EndRiftFiveE')
& $driver -FirstBotName $names[0] -SecondBotName $names[1] `
  -AdditionalBotNames $names[2], $names[3], $names[4] `
  -BotDurationSeconds $BotDurationSeconds -TimeoutSeconds $TimeoutSeconds
if ($LASTEXITCODE -ne 0) {
  throw "Five-player official End Rift run failed with exit code $LASTEXITCODE."
}
