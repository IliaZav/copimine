. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$legacy = Read-Utf8 $Paths.FrontendLegacy
$publicRender = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-render.js')

Require-NotContains $legacy '$("nav").innerHTML =' 'Legacy nav must use safe DOM rendering instead of innerHTML.'
Require-NotContains $legacy '$("miniHealth").innerHTML =' 'Mini health summary must use safe DOM rendering instead of innerHTML.'
Require-NotContains $legacy '$("modalRoot").innerHTML = "";' 'Modal cleanup must use safe DOM replacement instead of raw innerHTML reset.'
Require-NotContains $legacy 'panel.innerHTML =' 'Public feature panel must use safe DOM rendering instead of innerHTML.'
Require-NotContains $legacy 'statusGrid.innerHTML =' 'Public status grid must use safe DOM rendering instead of innerHTML.'
Require-NotContains $legacy 'onlineBoard.innerHTML =' 'Public online board must use safe DOM rendering instead of innerHTML.'
Require-NotContains $legacy '.innerHTML' 'Legacy frontend runtime must not assign HTML strings directly into the DOM.'
Require-Contains $legacy 'replaceChildrenSafe(navRoot, groups);' 'Legacy nav must replace children safely.'
Require-Contains $legacy 'if (content instanceof Node)' 'Legacy setView must accept DOM nodes for safer modular pages.'
Require-Contains $legacy 'replaceChildrenSafe($("modalRoot"), []);' 'Modal cleanup must use replaceChildrenSafe.'
Require-Contains $legacy 'replaceChildrenSafe(statusGrid, [' 'Public status grid must replace children safely.'
Require-Contains $legacy 'replaceChildrenSafe(onlineBoard, [' 'Public online board must replace children safely.'
Require-Contains $legacy 'setMiniHealthSummary(' 'Legacy runtime must centralize mini health DOM rendering.'
Require-NotContains $publicRender '.innerHTML' 'Public homepage renderer must not rely on innerHTML.'

Throw-IfErrors 'ValidateCopiMineNoUnsafeInnerHtml'
