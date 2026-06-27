. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$publicData = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-data.js')

Require-Contains $mainPy '@app.get("/api/public/president-budget")' 'Backend must expose the public president budget endpoint.'
Require-Contains $mainPy 'def public_treasury_overview_sync(limit: int = 30) -> dict[str, Any]:' 'Public budget endpoint must resolve data through the treasury overview helper.'
Require-Contains $publicData 'fetchJson("/api/public/president-budget"' 'Public homepage data loader must read the president budget endpoint.'

Throw-IfErrors 'ValidateCopiMinePresidentBudgetPublicEndpoint'
