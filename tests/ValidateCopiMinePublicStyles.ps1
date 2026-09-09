$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$publicPages = @('shops.html', 'index.html', 'server.html', 'launcher.html', 'news.html', 'cart.html', 'signin.html', 'register.html', 'error.html', '404.html', 'preview-player.html', 'preview-admin.html')
$publicSources = foreach ($page in $publicPages) {
  $path = Join-Path $root "admin-web\frontend\$page"
  if (Test-Path -LiteralPath $path) { Get-Content -Raw -Encoding UTF8 $path }
}
$style = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\style.css')
$releaseUi = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\css\release-ui.css')
$previewUi = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\css\preview.css')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($index in 0..($publicSources.Count - 1)) {
  if ($publicSources[$index] -notmatch '/assets/style\.css(?:\?[^" ]+)?') {
    $errors.Add("Public page index $index must link to the shared style entrypoint")
  }
}
foreach ($import in @('./css/tokens.css', './css/themes.css', './css/release-ui.css')) {
  $expectedImport = '@import url("' + $import + '")'
  if ($style -notmatch [regex]::Escape($expectedImport)) {
    $errors.Add("Shared style entrypoint must import $import")
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
