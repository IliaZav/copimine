$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)private Map<String, Object> grantTaxClockExemptionNow\(UUID playerUuid, String playerName, String artifactInstanceId\) throws Exception \{.*?(?=\r?\n\s*private Map<String, Object> activeTax)')
if (-not $method.Success) { throw 'Tax-clock grant method was not found.' }
$body = $method.Value
if ($body -notmatch 'existingActive' -or $body -notmatch 'artifactInstanceId\.equals\(string\(existing\.get\("artifact_instance_id"\)\)\)') {
    throw 'Only a retry of the same artifact instance may be treated as an idempotent subscription reuse.'
}
if ($body -notmatch "president_tax_exemptions WHERE player_uuid=\? AND source=\?") {
    throw 'Tax-clock activation must not overwrite an exemption from another source.'
}
if ($body -notmatch 'subscriptionBase' -or $body -notmatch 'Math\.max\(issuedAt, longValue\(existing\.get\("expires_at"\)\)\)') {
    throw 'A new subscription purchase must extend from the current expiration instead of resetting or reusing it.'
}
if ($source -notmatch "president_tax_exemptions WHERE player_uuid=\? AND status='ACTIVE' AND source=\?") {
    throw 'ATM subscription status must only trust TAX_CLOCK exemption records.'
}
if ($source -notmatch "president_tax_exemptions WHERE status='ACTIVE' AND source=\? AND expires_at>\?") {
    throw 'The president tax roster must only count TAX_CLOCK exemptions as subscriptions.'
}

Write-Host 'Donation subscription renewal contract OK'
