$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sources = @(
  (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'),
  (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java'),
  (Join-Path $root 'resourcepacks\build-resourcepack.py')
)
$combined = ($sources | ForEach-Object { Get-Content -Raw -Encoding UTF8 $_ }) -join "`n"
foreach ($marker in @('requests.','urllib','Invoke-WebRequest','HttpClient','new URL(')) {
  if ($combined -match [regex]::Escape($marker)) { throw "Runtime external download marker found: $marker" }
}
Write-Host 'Narcotics no-runtime-external-downloads validation passed.'
