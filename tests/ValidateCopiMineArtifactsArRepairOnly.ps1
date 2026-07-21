. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'private boolean isArCatalogItem(String itemId)' 'Artifacts must classify AR items explicitly before repair.'
Require-Regex $artifacts 'isArCatalogItem\([^;]*(var3|catalog)\.itemId\(\)\)' 'Artifacts repair-open flow must reject non-AR items.'
Require-Regex $artifacts 'isArCatalogItem\([^;]*(var5|catalog)\.itemId\(\)\)' 'Artifacts repair execution flow must reject non-AR items.'
Require-Contains $artifacts 'PrepareAnvilEvent' 'Artifacts must guard anvil repair bypass.'
Require-Contains $artifacts 'PrepareGrindstoneEvent' 'Artifacts must guard grindstone repair bypass.'
Require-Contains $artifacts 'PrepareSmithingEvent' 'Artifacts must guard smithing repair bypass.'
Require-Contains $artifacts 'PlayerItemMendEvent' 'Artifacts must guard Mending repair bypass.'

Throw-IfErrors 'ValidateCopiMineArtifactsArRepairOnly'
