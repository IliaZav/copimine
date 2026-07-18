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

$menuError = [regex]::Match($source, '(?s)private void sendUserError\(Player player, Exception error, String fallback\) \{.*?(?=\r?\n\s*private boolean hasActiveSeal)')
if (-not $menuError.Success -or $menuError.Value -match 'safeError\(error\)' -or $menuError.Value -notmatch 'player\.sendMessage\(color\(fallback\)\)') {
    throw 'Election GUI failures must show the generic fallback instead of internal exception text.'
}

Write-Host 'Election public error redaction contract OK'
