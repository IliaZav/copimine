$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$match = [regex]::Match($source, '(?s)private void restoreOfficialItems\(Player player\) \{.*?(?=\r?\n\s*private )')
if (-not $match.Success) { throw 'restoreOfficialItems method was not found.' }
$body = $match.Value
if ($body -notmatch 'runTaskAsynchronously\(this, \(\) ->') {
    throw 'Official item restoration must perform database lookups asynchronously.'
}
$prefix = $body.Substring(0, $body.IndexOf('runTaskAsynchronously'))
if ($prefix -match '\b(queryList|queryOne|scalarLong|scalarString|tx|openConnection)\s*\(') {
    throw 'restoreOfficialItems still performs a blocking database call before scheduling async work.'
}
if ($body -notmatch 'runTask\(this, \(\) ->') {
    throw 'Official item restoration must apply inventory changes back on the server thread.'
}
if ($body -notmatch 'president_terms WHERE president_uuid=\?') {
    throw 'President mandate restoration must use the active-term query.'
}
if ($body -notmatch "cik_seals WHERE player_uuid=\? AND status='ACTIVE'") {
    throw 'Seal restoration must only restore active seals.'
}

Write-Host 'Election official-item restore async contract OK'
