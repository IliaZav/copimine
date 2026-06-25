. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin

Require-Contains $admin 'spawnOrReplaceProtectedBlockVisual(b.getLocation(),"ATM",id,Material.PAPER,MODEL_ATM_TERMINAL,"atm_terminal")' 'ATM creation must spawn a protected visual.'
Require-Contains $admin "LEFT JOIN protected_block_visuals pbv ON pbv.kind='ATM' AND pbv.linked_id=a.id AND pbv.active=1" 'ATM repair must backfill from protected_block_visuals.'
Require-Contains $admin 'applyProtectedBlockVisualRepairs(worldName,chunkX,chunkZ,rows);' 'ATM chunk repair must reapply visuals for existing ATMs.'

Throw-IfErrors 'ValidateCopiMineAtmVisualBackfill'
