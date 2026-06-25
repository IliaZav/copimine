$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$code = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\CopiMineClient.java')
foreach ($marker in @('copimineclient','status','protocol','visual','debug')) {
  if ($code -notmatch [regex]::Escape($marker)) { throw "Client command marker missing: $marker" }
}
Write-Host 'CopiMineClient command validation passed.'
