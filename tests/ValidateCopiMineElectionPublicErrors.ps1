$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

$publicError = [regex]::Match($source, '(?s)private String publicErrorMessage\(Throwable error\) \{.*?(?=\r?\n\s*private String first)')
if (-not $publicError.Success -or $publicError.Value -match 'safeError\(' -or $publicError.Value -notmatch 'ELECTION_OPERATION_FAILED') {
    throw 'Election player-facing errors must be generic and must not expose exception text.'
}

$presidentSay = [regex]::Match($source, '(?s)private boolean handlePresidentSayCommand\(CommandSender sender, String\[\] args\) \{.*?(?=\r?\n\s*private String sanitizePresidentBroadcastText)')
if (-not $presidentSay.Success -or $presidentSay.Value -match 'player\.sendMessage\(color\([^\r\n]*safeError\(error\)' -or $presidentSay.Value -notmatch 'publicErrorMessage\(error\)') {
    throw 'President broadcast failures must not send internal exception text to a player.'
}

Write-Host 'Election public error redaction contract OK'
