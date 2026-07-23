$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
if ($source -notmatch 'exemption_expires_at') {
    throw 'President tax roster must carry the active exemption expiration.'
}
if ($source -notmatch 'subscriptionExpiresAt' -or $source -notmatch 'formatTs\(subscriptionExpiresAt\)') {
    throw 'President tax roster GUI must display the subscription expiration.'
}

Write-Host 'President tax subscription display contract OK'
