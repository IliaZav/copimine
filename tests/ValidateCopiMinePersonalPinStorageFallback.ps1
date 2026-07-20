$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java')

if ($source -notmatch 'bank_account_pins') { throw 'Economy core does not know the current account PIN table.' }
if ($source -notmatch '(?s)UNION ALL.*bank_account_pins') {
    throw 'Personal PIN verification does not fall back to bank_account_pins.'
}
if ($source -notmatch 'synchronizePersonalPinHash') {
    throw 'Successful fallback PIN verification is not migrated to the canonical tables.'
}
if ($source -notmatch 'base64:|hex:') {
    throw 'PIN hash verifier does not document/handle encoded salts.'
}
Write-Host 'Personal PIN storage fallback validation passed.'
