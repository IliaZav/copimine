$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($runtime -notmatch [regex]::Escape('clientBridge.visuals().clearVisuals')) { throw 'Visual runtime no longer clears stale client visual sessions.' }
if ($runtime -match 'sendVisualStart|sendVisualRefresh') { throw 'Retired narcotics shaders must not be started or refreshed through the client bridge.' }
if ($runtime -notmatch [regex]::Escape('SERVER_PARTICLE_FALLBACK')) { throw 'Particle fallback route missing.' }
Write-Host 'Narcotics client bridge cleanup validation passed.'