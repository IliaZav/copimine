. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.FrontendServer
$publicRender = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-render.js')
$publicData = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-data.js')

Require-Contains $server 'id="presidentBudgetShowcase"' 'Server page must expose a dedicated public treasury showcase section.'
Require-Contains $server 'id="presidentBudgetOwner"' 'Server page must expose a dedicated treasury owner label.'
Require-Contains $publicRender 'balance_ar: treasury.balance' 'Public status payload must map treasury balance separately from player bank data.'
Require-Contains $publicData '/api/public/president-budget' 'Public treasury widgets must use the public treasury endpoint.'
Require-NotRegex $publicData '/api/player/bank|/api/player/treasury' 'Public treasury widgets must not call player bank or private treasury endpoints.'

Throw-IfErrors 'ValidateCopiMinePresidentBudgetNotPlayerBalance'
