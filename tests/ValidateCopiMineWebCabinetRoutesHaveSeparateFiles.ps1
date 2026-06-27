. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$routesFile = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\shared\app-routes.js')
$matches = [regex]::Matches($routesFile, '/cabinet/([a-z0-9\-]+)\.html')
if ($matches.Count -eq 0) {
  $errors.Add('APP_ROUTE_FILES must map cabinet routes to dedicated HTML files.')
}

$seen = @{}
foreach ($match in $matches) {
  $name = $match.Groups[1].Value
  if ($seen.ContainsKey($name)) { continue }
  $seen[$name] = $true
  $path = Join-Path $root ("admin-web\frontend\cabinet\{0}.html" -f $name)
  if (-not (Test-Path -LiteralPath $path)) {
    $errors.Add("Missing dedicated cabinet page file for route '$name': $path")
    continue
  }
  $html = Read-Utf8 $path
  Require-Contains $html 'data-page-kind="cabinet"' "Cabinet page '$name' must keep the authenticated page marker."
  Require-Contains $html 'id="app" class="app"' "Cabinet page '$name' must keep the authenticated app shell mount."
}

Throw-IfErrors 'ValidateCopiMineWebCabinetRoutesHaveSeparateFiles'
