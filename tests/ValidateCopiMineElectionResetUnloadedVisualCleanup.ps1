$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -LiteralPath (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java') -Raw -Encoding UTF8
$migrationPath = Join-Path $root 'db\migrations\20260718_012_election_visual_cleanup_queue.sql'
$reset = [regex]::Match($source, '(?s)private void resetElections\(String actor\)( throws Exception)? \{.*?(?=\r?\n\s*private void expirePresidentTermsSafe)')
$chunk = [regex]::Match($source, '(?s)public void onChunkLoad\(ChunkLoadEvent event\) \{.*?(?=\r?\n\s*@EventHandler)').Value

if (-not $reset.Success -or $reset.Value -notmatch 'queueElectionVisualCleanup\(connection\);') {
    throw 'Election reset must queue labels and block visuals before deleting their database links.'
}
if ($chunk -notmatch 'cleanupQueuedElectionVisuals\(') {
    throw 'Chunk loading must process queued cleanup for visuals that were in unloaded chunks during reset.'
}
foreach ($marker in @('election_visual_cleanup_queue', 'runTaskAsynchronously(this, work)', 'removeQueuedElectionVisual', 'deleteQueuedElectionVisualCleanup')) {
    if ($source -notmatch [regex]::Escape($marker)) {
        throw "Unloaded visual cleanup contract missing: $marker"
    }
}
if (-not (Test-Path -LiteralPath $migrationPath) -or (Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8) -notmatch 'CREATE TABLE IF NOT EXISTS election_visual_cleanup_queue') {
    throw 'Deployment migration for the election visual cleanup queue is missing.'
}

Write-Host 'Election unloaded visual cleanup contract OK'
