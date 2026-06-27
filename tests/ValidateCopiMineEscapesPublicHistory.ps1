. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$siteRender = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-render.js')
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $siteRender 'function renderHistory(items = []) {' 'Public site renderer must expose history rendering.'
Require-Contains $siteRender 'makeElement("p", "", String(row.comment || row.item_name || row.public_actor_name || row.actor ||' 'Public treasury history must render text through textContent-safe DOM nodes.'
Require-NotContains $siteRender 'historyMount.innerHTML' 'Public treasury history must not use innerHTML.'
Require-Contains $legacy 'const treasuryNotes = makeElement("div", "top-note-list");' 'Legacy public board must build treasury notes through safe DOM helpers.'

Throw-IfErrors 'ValidateCopiMineEscapesPublicHistory'
