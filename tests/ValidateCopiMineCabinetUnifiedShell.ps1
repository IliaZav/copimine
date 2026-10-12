$ErrorActionPreference = "Stop"

$root = Join-Path $PSScriptRoot "..\admin-web\frontend\cabinet"
$pages = Get-ChildItem -LiteralPath $root -Filter "*.html" | Sort-Object Name
if (-not $pages) {
  throw "Cabinet route pages not found."
}

$errors = New-Object System.Collections.Generic.List[string]
# Windows PowerShell 5.1 decodes UTF-8 scripts without a BOM using the system
# ANSI code page, so keep Russian fixture expectations encoded as UTF-8 bytes.
$demotedPlayerMessage = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('0JLRiyDQsdGL0LvQuCDRgNCw0LfQttCw0LvQvtCy0LDQvdGLINC00L4g0LjQs9GA0L7QutCwLg=='))
$adminContactMessage = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('0J7QsdGA0LDRgtC40YLQtdGB0Ywg0Log0LDQtNC80LjQvdC40YHRgtGA0LDRhtC40Lgu'))

function Require-Contains {
  param(
    [string]$content,
    [string]$needle,
    [string]$label
  )
  if ($content -notmatch [regex]::Escape($needle)) {
    $errors.Add("$label is missing required fragment: $needle")
  }
}

foreach ($page in $pages) {
  $content = Get-Content -Raw -Encoding UTF8 $page.FullName
  $label = $page.Name
  if ($page.Name -eq 'demoted.html') {
    Require-Contains $content '<body class="auth-screen">' $label
    Require-Contains $content $demotedPlayerMessage $label
    Require-Contains $content $adminContactMessage $label
    continue
  }
  Require-Contains $content 'data-page-kind="cabinet"' $label
  Require-Contains $content 'data-boot-state="loading"' $label
  Require-Contains $content '<header class="public-nav public-nav-auth">' $label
  Require-Contains $content 'id="bootStage"' $label
  Require-Contains $content '<div id="app" class="app">' $label
  Require-Contains $content 'id="publicCabinetBtn"' $label
  Require-Contains $content 'id="publicLogoutBtn"' $label
  Require-Contains $content 'id="guestPagesBtn"' $label
  Require-Contains $content '/assets/js/cabinet-polish.js' $label
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineCabinetUnifiedShell failed:`n - " + ($errors -join "`n - "))
}

Write-Host "ValidateCopiMineCabinetUnifiedShell passed."
