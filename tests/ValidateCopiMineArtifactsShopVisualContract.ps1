. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'spawnOrReplaceProtectedBlockVisual(target.getLocation(), "ARTIFACT_SHOP", shop.shopId(), Material.PAPER, MODEL_ARTIFACT_SHOP_MARKER, "artifact_shop_marker")' 'Artifact shop creation must spawn a protected visual.'
Require-Contains $artifacts 'cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId())' 'Artifact shop removal must clean its protected visual.'
Require-Contains $artifacts 'repairProtectedBlockVisual(shop, visuals.getOrDefault(shop.shopId(), Map.of()))' 'Artifact shop repair must backfill visuals for existing shops.'

Throw-IfErrors 'ValidateCopiMineArtifactsShopVisualContract'
