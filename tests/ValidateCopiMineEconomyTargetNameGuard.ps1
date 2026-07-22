$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$economy = Get-Content (Join-Path $root 'copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java') -Raw
$admin = Get-Content (Join-Path $root 'copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java') -Raw

foreach ($source in @($economy, $admin)) {
    if ($source -notmatch 'UUID\.fromString\(targetUuid\)') {
        throw 'Expected target UUID lookup is missing.'
    }
    if ($source -notmatch '(?s)catch\s*\(IllegalArgumentException\s+ignored\).*?return\s+fallback') {
        throw 'Target name lookup must fail closed for malformed UUIDs.'
    }
}

Write-Output 'CopiMine economy target-name UUID guard: OK'
