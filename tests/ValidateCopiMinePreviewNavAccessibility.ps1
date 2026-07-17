$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/preview-nav.js')
$preview = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/preview-admin.html')
$backdropLabel = 'backdrop.setAttribute("aria-label", "\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e \u043f\u043e \u0444\u043e\u043d\u0443");'
$scriptReference = '<script type="module" src="/assets/js/preview-nav.js?v=20260718r2"></script>'

if ($source -notmatch [regex]::Escape($backdropLabel)) {
  throw 'The mobile navigation backdrop must have a distinct accessible name.'
}

if ($preview -notmatch [regex]::Escape($scriptReference)) {
  throw 'The preview page must reference the updated navigation asset version.'
}

Write-Host 'ValidateCopiMinePreviewNavAccessibility passed.'
