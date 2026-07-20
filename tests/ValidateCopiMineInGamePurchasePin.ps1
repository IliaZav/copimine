$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
if ($source -notmatch 'var5x\.pinBuffer\s*=\s*"";\s*this\.openPin\(var1, var15\);') { throw 'Purchase flow does not always open the PIN pad.' }
if ($source -notmatch 'var4\.matches\("\\\\d\{4,8\}"\)') { throw 'Purchase execution has no server-side PIN format guard.' }
if ($source -match 'var3x\s*!=\s*null\s*&&\s*var3x\.configured\(\).*?executePurchase\([^;]+,""\);') { throw 'Purchase flow still has an empty-PIN bypass.' }
Write-Host 'In-game purchase PIN validation passed.'
