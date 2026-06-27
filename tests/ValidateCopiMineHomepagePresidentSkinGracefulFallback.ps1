. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$render = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'public\site-render.js')

Require-Contains $render 'skinImage.addEventListener("error", () => {' 'Homepage president skin renderer must handle failed image loads.'
Require-Contains $render 'skinShell?.classList.add("hidden");' 'Skin fallback must hide the shell when the image fails.'
Require-Contains $render 'skinImage.removeAttribute("src");' 'Skin fallback must clear the broken image src.'
Require-Contains $render '/api/public/president/skin/body?uuid=${encodeURIComponent(uuid)}' 'Skin renderer must load the president skin through the public API route.'

Throw-IfErrors 'ValidateCopiMineHomepagePresidentSkinGracefulFallback'
