[CmdletBinding()]
param(
  [ValidateRange(900, 3600)]
  [int]$BotDurationSeconds = 1800,
  [ValidateRange(600, 3500)]
  [int]$TimeoutSeconds = 1700
)

# Ten real disposable protocol clients run the official V2 state machine on
# the isolated local Paper instance.  No production address, launcher, world
# reset, or production database is involved.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$driver = Join-Path $root 'tests\RunEndRiftOfficialTwoPlayerLive.ps1'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Ten-player run refused Git branch '$branch'."
}
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Ten-player run refused a non-local End Rift configuration.'
}
if (-not (Test-Path -LiteralPath $driver -PathType Leaf)) {
  throw "Shared official driver is missing: $driver"
}

$names = @('EndRiftTenA', 'EndRiftTenB', 'EndRiftTenC', 'EndRiftTenD', 'EndRiftTenE',
  'EndRiftTenF', 'EndRiftTenG', 'EndRiftTenH', 'EndRiftTenI', 'EndRiftTenJ')
& $driver -FirstBotName $names[0] -SecondBotName $names[1] `
  -AdditionalBotNames $names[2], $names[3], $names[4], $names[5], $names[6], $names[7], $names[8], $names[9] `
  -BotDurationSeconds $BotDurationSeconds -TimeoutSeconds $TimeoutSeconds
if ($LASTEXITCODE -ne 0) {
  throw "Ten-player official End Rift run failed with exit code $LASTEXITCODE."
}
