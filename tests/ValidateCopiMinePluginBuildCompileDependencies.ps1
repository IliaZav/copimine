$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$scripts = @(
  'copimine-economy-core\build-plugin.ps1',
  'copimine-election-core\build-plugin.ps1',
  'copimine-admin-plugin\build-plugin.ps1',
  'copimine-artifacts\build-plugin.ps1',
  'copimine-narcotics\build-plugin.ps1',
  'copimine-world-core\build-plugin.ps1'
)
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($relativePath in $scripts) {
  $text = Get-Content -LiteralPath (Join-Path $root $relativePath) -Raw -Encoding UTF8
  foreach ($marker in @('$mavenRepo', 'net\kyori', 'net\md-5', 'org\joml', 'PAPER_COMPILE_DEPS')) {
    if (-not $text.Contains($marker)) {
      $errors.Add("$relativePath must include Paper compile dependency marker: $marker")
    }
  }
}

$adminBuild = Get-Content -LiteralPath (Join-Path $root 'copimine-admin-plugin\build-plugin.ps1') -Raw -Encoding UTF8
foreach ($marker in @('me\clip\placeholderapi', 'thirdparty\client-mods', "'voicechat-*.jar'")) {
  if (-not $adminBuild.Contains($marker)) {
    $errors.Add("copimine-admin-plugin\\build-plugin.ps1 must provide a clean-checkout fallback for $marker")
  }
}

if ($errors.Count -gt 0) {
  throw ("Plugin build dependency validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Plugin build dependency validation passed.'
