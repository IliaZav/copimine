$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$css = Join-Path $root 'admin-web\frontend\assets\css\preview.css'
$player = Join-Path $root 'admin-web\frontend\preview-player.html'
$admin = Join-Path $root 'admin-web\frontend\preview-admin.html'

$text = Get-Content -LiteralPath $css -Raw -Encoding UTF8
if ($text -notmatch '(?s)\.preview-nav-toggle\s*\{[^}]*z-index\s*:\s*52') {
  throw 'Mobile menu toggle must stay above the backdrop so the open menu can be closed again.'
}
if ($text -notmatch '(?s)\.preview-nav-backdrop\s*\{[^}]*z-index\s*:\s*50') {
  throw 'Mobile menu backdrop must keep its bounded stacking layer.'
}
foreach ($page in @($player, $admin)) {
  $html = Get-Content -LiteralPath $page -Raw -Encoding UTF8
  if ($html -notmatch 'preview\.css\?v=20260718r3') { throw "Preview stylesheet cache-buster missing: $page" }
}

Write-Host 'CopiMine preview mobile menu contract OK'
