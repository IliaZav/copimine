$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$errors = New-Object System.Collections.Generic.List[string]
if (-not (Test-Path -LiteralPath $admin)) { $errors.Add('Missing AdminPlus source.') }
if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  $badPrefixes = @(
    ([string][char]0x0420 + [string][char]0x0459), # mojibake prefix often visible as R-like Cyrillic sequence
    ([string][char]0x00D0),
    ([string][char]0x00D1),
    'CP1251',
    'ISO_8859_1',
    'new String(.getBytes'
  )
  foreach ($bad in $badPrefixes) {
    if ($java.Contains($bad)) { $errors.Add("Sidebar/source contains forbidden encoding marker: $bad") }
  }
  foreach ($marker in @('tryBlankNumbers','NumberFormat','renderSidebarObjective','sidebarLines','LIVE_SIDEBAR_ALWAYS_ON_V3')) {
    if (-not $java.Contains($marker)) { $errors.Add("Missing sidebar marker: $marker") }
  }
}
if ($errors.Count -gt 0) { throw ("Sidebar encoding validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Sidebar encoding validation passed.'
