. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$appPath = Resolve-Path $Paths.FrontendApp
$bootstrapPath = Resolve-Path (Join-Path $Paths.FrontendAssetsJs 'bootstrap.js')
$appLines = (Get-Content -Encoding UTF8 $appPath).Count
$bootstrapLines = (Get-Content -Encoding UTF8 $bootstrapPath).Count

if ($appLines -gt 12) {
  $script:errors.Add("assets/app.js is too large ($appLines lines). It should stay a tiny module wrapper.")
}
if ($bootstrapLines -gt 140) {
  $script:errors.Add("bootstrap.js is too large ($bootstrapLines lines). Split more logic into dedicated modules instead of regressing to a monolith.")
}

Throw-IfErrors 'ValidateCopiMineWebNoHugeAppJs'
