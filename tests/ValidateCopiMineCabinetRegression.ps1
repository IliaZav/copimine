$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
$legacy = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\legacy\app-legacy.js')
$faviconSvg = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\favicon.svg')
$favicon = Join-Path $root 'admin-web\frontend\assets\favicon.png'

if ($backend -notmatch '(?s)class AdminArtifactLimitResetIn\(BaseModel\):.*?item_id: str = Field\(min_length=1,') {
  throw 'The all-limit reset request must accept the wildcard item id.'
}
if ($backend -notmatch 'decrees, petitions = \[\], \[\]') {
  throw 'The live cabinet must not read retired election decree/petition tables.'
}
if ($backend -match 'SELECT \* FROM election_(decrees|petitions)' -and $backend -notmatch 'list_election_documents_sync') {
  throw 'Retired election documents must only be exposed through their explicit historical endpoints.'
}
if ($runtime -notmatch 'playerResetAuthMePassword') {
  throw 'Modern player card must expose AuthMe password reset.'
}
if ($legacy -notmatch 'playerResetAuthMePassword') {
  throw 'Legacy-compatible player card must expose the same AuthMe password reset.'
}
if (-not (Test-Path -LiteralPath $favicon)) {
  throw 'The requested PNG favicon is missing.'
}
if ($faviconSvg -notmatch 'favicon\.png') {
  throw 'The SVG favicon compatibility wrapper must point at the requested PNG.'
}
Write-Host 'Cabinet regression contract passed.'
