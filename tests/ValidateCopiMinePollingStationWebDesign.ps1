$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$bootstrap = Join-Path $root 'admin-web\frontend\assets\app.js'
$legacyFrontend = Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js'
$style = Join-Path $root 'admin-web\frontend\assets\style.css'
$legacyStyle = Join-Path $root 'admin-web\frontend\assets\css\legacy.css'
$js = @(
  Get-Content -Raw -Encoding UTF8 $bootstrap
  Get-Content -Raw -Encoding UTF8 $legacyFrontend
) -join "`n"
$css = @(
  Get-Content -Raw -Encoding UTF8 $style
  Get-Content -Raw -Encoding UTF8 $legacyStyle
) -join "`n"
$errors = New-Object System.Collections.Generic.List[string]

function Require([string]$name, [string]$text, [string]$pattern) {
  if ($text -notmatch $pattern) { $script:errors.Add("$name missing: $pattern") }
}

Require 'station card helper' $js 'function stationCardsHtml'
Require 'station cards use deposits' $js 'depositByStation'
Require 'election page station cards' $js 'stationCardsHtml\(pollingStations,\s*voteDeposits\)'
Require 'station cards visual class' $css '\.station-card-grid'
Require 'station card active state' $css '\.station-card\.active'
Require 'station coordinate chip' $css '\.station-card-coords'

if ($errors.Count -gt 0) {
  throw ("Polling station web design validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Polling station web design validation passed: station cards are rendered with visual CSS and deposit counts.'
