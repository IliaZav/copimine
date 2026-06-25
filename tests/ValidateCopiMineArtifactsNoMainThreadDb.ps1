. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$chunkLoad = Method-Body $artifacts 'public void onChunkLoad(ChunkLoadEvent event)'

Require-Contains $artifacts 'runAsync(() -> {' 'Artifacts visual repair must perform DB work asynchronously.'
Require-NotContains $chunkLoad 'runSync(' 'Artifacts chunk load must not schedule DB repair through a sync callback.'

Throw-IfErrors 'ValidateCopiMineArtifactsNoMainThreadDb'
