. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $mainPy 'def ar_catalog_snapshot_sync() -> dict[str, Any]:' 'Backend must resolve AR shop data through the shared AR catalog snapshot.'
Require-Contains $mainPy '@app.get("/api/player/shop/ar-items")' 'Backend must expose the player AR catalog endpoint.'
Require-Contains $mainPy '@app.get("/api/admin/shop/ar-items")' 'Backend must expose the admin AR catalog endpoint.'
Require-Contains $legacy '/api/player/shop/ar-items' 'Frontend must load AR shop prices from the backend AR catalog endpoint.'

Throw-IfErrors 'ValidateCopiMineArPricesFromBackendCatalog'
