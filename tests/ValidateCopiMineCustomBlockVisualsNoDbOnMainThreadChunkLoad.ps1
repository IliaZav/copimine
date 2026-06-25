. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts
$election = Read-Utf8 $Paths.Election
Require-Contains $admin 'runTaskAsynchronously(this,work)' 'AdminPlus chunk repair must fetch ATM visuals asynchronously.'
Require-Contains $artifacts 'runAsync(() -> {' 'Artifacts chunk repair must fetch shop visuals asynchronously.'
Require-Contains $election 'runTaskAsynchronously(this, work)' 'ElectionCore chunk repair must fetch visuals asynchronously.'
Require-Regex $admin 'public void onChunkLoad\(ChunkLoadEvent e\)\s*\{\s*if\(!customBlockVisualsEnabled\(\)\)return;\s*try \{ repairProtectedBlockVisuals\(e\.getWorld\(\)\.getName\(\), e\.getChunk\(\)\.getX\(\), e\.getChunk\(\)\.getZ\(\)\); \}' 'AdminPlus chunk load must call visual repair directly without sync DB wrappers.'
Require-Regex $artifacts 'public void onChunkLoad\(ChunkLoadEvent event\)\s*\{\s*if \(!customBlockVisualsEnabled\(\)\) return;\s*try \{\s*repairProtectedBlockVisuals\(event\.getWorld\(\)\.getName\(\), event\.getChunk\(\)\.getX\(\), event\.getChunk\(\)\.getZ\(\)\);' 'Artifacts chunk load must call visual repair directly without sync DB wrappers.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsNoDbOnMainThreadChunkLoad'
