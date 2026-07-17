$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errorPage = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/error.html')
$notFoundPage = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/404.html')
$runtimePath = Join-Path $root 'admin-web/frontend/assets/js/public/error-page.js'
$runtime = if (Test-Path $runtimePath) { Get-Content -Raw -Encoding UTF8 $runtimePath } else { '' }

foreach ($entry in @(
  @{ Name = 'error.html'; Source = $errorPage },
  @{ Name = '404.html'; Source = $notFoundPage }
)) {
  if ($entry.Source -notmatch 'error-page\.js\?v=20260718r1') {
    throw "$($entry.Name) must load the shared error-page runtime."
  }
  if ($entry.Source -match '<script>') {
    throw "$($entry.Name) must not use an inline script under the strict CSP."
  }
}

if ($runtime -notmatch 'initThemeToggle\(\)') {
  throw 'Error-page runtime must initialize the shared theme switch.'
}
if ($runtime -notmatch 'initPublicNav\(\)') {
  throw 'Error-page runtime must initialize the responsive public navigation.'
}
if ($runtime -notmatch 'window\.location\.reload\(\)') {
  throw 'Error-page runtime must implement the visible reload action.'
}

Write-Host 'ValidateCopiMineErrorPagesInteractive passed.'
