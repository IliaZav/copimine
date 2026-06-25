$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$code = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java')
if ($code -notmatch 'clamp\(visual\.intensity\(\)\)') { throw 'Visual intensity must be clamped and read during rendering.' }
if ($code -notmatch 'alphaFactor') { throw 'Visual intensity must affect alpha.' }
if ($code -notmatch 'motionFactor') { throw 'Visual intensity must affect motion or jitter.' }
Write-Host 'CopiMineClient intensity rendering validation passed.'
