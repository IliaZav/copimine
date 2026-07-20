$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java')
if ($source -notmatch 'atm:delete:') { throw 'Admin ATM list has no delete action.' }
if ($source -notmatch 'UPDATE ar_atms SET active=0') { throw 'ATM deletion does not archive the database record.' }
if ($source -notmatch 'cleanupProtectedBlockVisuals\("ATM"') { throw 'ATM deletion does not remove its world visual.' }
Write-Host 'Admin ATM removal validation passed.'
