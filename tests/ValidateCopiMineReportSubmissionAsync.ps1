$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

function Get-Block([string] $signature, [string] $nextSignature) {
    $match = [regex]::Match($source, "(?s)$signature.*?(?=\r?\n    private $nextSignature)")
    if (-not $match.Success) {
        throw "Could not locate $signature"
    }
    return $match.Value
}

$report = Get-Block 'void handleReport\(CommandSender sender,String\[\] args\)' 'boolean handleReportaCommand'
if ($report -notmatch 'dbAsyncLoad\("report submission"') {
    throw 'Regular /report submission must perform database work asynchronously.'
}

$bugReport = Get-Block 'boolean submitBugReport\(Player p,PendingBugReport pending,String playerNote\)' 'boolean handleAuditCommand'
if ($bugReport -notmatch 'dbAsyncLoad\("bug report submission"') {
    throw 'Technical /reporta submission must perform database work asynchronously.'
}

$asyncStart = $bugReport.IndexOf('dbAsyncLoad("bug report submission"')
$pendingRemoval = $bugReport.IndexOf('pendingBugReports.remove(')
if ($pendingRemoval -lt 0 -or $pendingRemoval -lt $asyncStart) {
    throw 'Technical report context must be cleared only after a successful save.'
}

$asyncHelper = Get-Block '<T> void dbAsyncLoad\(String label,SqlSupplier<T> body,Consumer<T> onSuccess,Consumer<Exception> onError\)' 'CachedElectionRole cachedElectionRole'
if ($asyncHelper -match 'else work\.run\(\)') {
    throw 'The async database helper must never fall back to running database work in the game thread.'
}

Write-Host 'Report and technical report async persistence contract OK'
