$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($runtime -notmatch 'clientBridge\.visuals\(\)\.sendVisualStart' -or $runtime -notmatch 'CLIENT_MOD_VISUAL') { throw 'Visual runtime does not use client bridge when available.' }
Write-Host 'Narcotics client bridge usage validation passed.'