$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = @(
  'admin-web/frontend/assets/js/cabinet-runtime.js',
  'admin-web/frontend/assets/js/legacy/app-legacy.js',
  'admin-web/frontend/assets/js/admin/commerce-pages.js',
  'admin-web/frontend/assets/js/admin/plugin-registry-pages.js',
  'admin-web/frontend/assets/js/admin/cms-pages.js',
  'admin-web/frontend/assets/js/admin/narcotics-recipe-pages.js'
)

$total = 0
$unguarded = @()
foreach ($relative in $files) {
  $source = Get-Content -Raw -Encoding UTF8 (Join-Path $root $relative)
  $assignments = [regex]::Matches(
    $source,
    'const\s+headers\s*=\s*[^;]{0,180}?await\s+dangerConfirm\(',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
  )
  $total += $assignments.Count
  foreach ($assignment in $assignments) {
    $window = $source.Substring($assignment.Index, [Math]::Min(650, $source.Length - $assignment.Index))
    if ($window -notmatch 'if\s*\(!headers\)\s*return;') {
      $line = ($source.Substring(0, $assignment.Index) -split "`n").Count
      $unguarded += "${relative}:$line"
    }
  }
}

if ($total -eq 0) {
  throw 'No dangerConfirm assignments were found in the active frontend.'
}
if ($unguarded.Count -gt 0) {
  throw "Every dangerConfirm result must stop on cancel: $($unguarded -join ', ')"
}

Write-Host "ValidateCopiMineDangerConfirmCancelGuards passed: guarded=$total."
