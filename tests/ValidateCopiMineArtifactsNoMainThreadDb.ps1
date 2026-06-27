. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$chunkLoad = [regex]::Match($artifacts, 'public void onChunkLoad\(ChunkLoadEvent event\)\s*\{(?<body>[\s\S]*?)\n\s*\}', 'Singleline').Groups['body'].Value

Require-Contains $artifacts 'private void drainProtectedBlockVisualRepairs()' 'Artifacts visual repair queue must exist.'
Require-Contains $artifacts 'this.runAsync(() -> {' 'Artifacts visual repair must perform DB work asynchronously.'
Require-Contains $chunkLoad 'repairProtectedBlockVisuals(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());' 'Artifacts chunk load must route through chunk-scoped visual repair.'
Require-NotContains $chunkLoad 'scalarLong(' 'Artifacts chunk load must not query PostgreSQL directly on the main thread.'
Require-NotContains $chunkLoad 'queryList(' 'Artifacts chunk load must not query PostgreSQL directly on the main thread.'
Require-NotContains $chunkLoad 'pgPool.acquire()' 'Artifacts chunk load must not acquire PostgreSQL connections on the main thread.'

Throw-IfErrors 'ValidateCopiMineArtifactsNoMainThreadDb'
