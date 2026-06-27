. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$server = Read-Utf8 $Paths.FrontendServer
$homepage = Read-Utf8 $Paths.FrontendHomepage
$publicRender = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-render.js')
$publicData = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-data.js')

Require-Contains $server 'id="publicTreasuryHistory"' 'Server page must expose the public treasury history mount.'
Require-Contains $publicRender 'renderHistory(items = [])' 'Public renderer must render treasury history cards.'
Require-Contains $publicData '/api/public/president-budget/history?limit=${Number(limit) || 6}' 'Public treasury page must load treasury history from the public history endpoint.'
Require-Contains $server 'id="treasuryHistorySection"' 'Server page must expose a dedicated treasury history section.'
Require-NotRegex $publicRender '(internal[_-]?id|ballot_id|player_uuid|voter_uuid)' 'Public treasury history renderer must not expose private or internal identifiers.'

Throw-IfErrors 'ValidateCopiMinePresidentBudgetPublicHistory'
