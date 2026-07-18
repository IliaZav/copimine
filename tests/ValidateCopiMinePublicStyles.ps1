$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$publicPages = @('shops.html', 'index.html', 'server.html', 'mods.html', 'cart.html', 'signin.html', 'register.html', 'error.html', '404.html', 'preview-player.html', 'preview-admin.html')
$publicSources = foreach ($page in $publicPages) {
  $path = Join-Path $root "admin-web\frontend\$page"
  if (Test-Path -LiteralPath $path) { Get-Content -Raw -Encoding UTF8 $path }
}
$releaseUi = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\css\release-ui.css')
$previewUi = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\css\preview.css')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($href in @('/assets/css/tokens.css', '/assets/css/themes.css', '/assets/css/release-ui.css')) {
  foreach ($index in 0..($publicSources.Count - 1)) {
    if ($publicSources[$index] -notmatch [regex]::Escape($href)) { $errors.Add("Public page index $index must link directly to $href") }
  }
}
foreach ($marker in @('.public-brand-logo', 'max-width:', 'max-height:', 'object-fit: contain')) {
  if ($releaseUi -notmatch [regex]::Escape($marker)) { $errors.Add("Release UI logo guard missing: $marker") }
}
foreach ($marker in @('.preview-pill', 'color: var(--ink) !important', 'background: var(--surface-soft) !important')) {
  if ($previewUi -notmatch [regex]::Escape($marker)) { $errors.Add("Preview quick-action contrast rule missing: $marker") }
}

if ($errors.Count) { throw ("Public styles validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'CopiMine public styles contract OK'
