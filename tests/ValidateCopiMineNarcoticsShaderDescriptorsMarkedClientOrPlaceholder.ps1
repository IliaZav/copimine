$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Get-ChildItem (Join-Path $root 'resourcepacks\src\assets\copimine\shaders\narcotics') -Filter '*.json' | ForEach-Object {
  $content = Get-Content -Raw -Encoding UTF8 $_.FullName
  foreach ($marker in @('placeholder_only','server_forceable','CLIENT_MOD_OR_DOCUMENTATION_ONLY')) {
    if ($content -notmatch [regex]::Escape($marker)) { throw "Shader descriptor marker missing in $($_.Name): $marker" }
  }
}
Write-Host 'Shader descriptor placeholder validation passed.'