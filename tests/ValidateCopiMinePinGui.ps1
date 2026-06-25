$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$artifacts = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$narcotics = Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($admin, $artifacts, $narcotics)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing source: $path") }
}
if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  foreach ($marker in @('bankpin:digit:','for(int i=0;i<9;i++)','bankpin:clear','bankpin:back','bankpin:confirm','atmPinSessions')) {
    if (-not $java.Contains($marker)) { $errors.Add("AdminPlus PIN GUI missing marker: $marker") }
  }
  if ($java -match 'ATM PIN|PIN:\s*"\s*\+\s*masked|sendMessage\(.*pin') { $errors.Add('AdminPlus PIN must not be visible in title/chat.') }
}
if (Test-Path $artifacts) {
  $java = Get-Content -Raw -Encoding UTF8 $artifacts
  foreach ($marker in @('digit:','for (int i = 0; i <= 9; i++)','pin:clear','pin:backspace','pin:submit','pinBuffer')) {
    if (-not $java.Contains($marker)) { $errors.Add("Artifacts PIN GUI missing marker: $marker") }
  }
}
if (Test-Path $narcotics) {
  $java = Get-Content -Raw -Encoding UTF8 $narcotics
  foreach ($marker in @('digit:','for (int i = 1; i <= 9; i++)','clear','confirm','pinBuffers','maskedPin(player)')) {
    if (-not $java.Contains($marker)) { $errors.Add("Narcotics PIN GUI missing marker: $marker") }
  }
  if ($java -match 'PIN:\s*"\s*\+\s*maskedPin') { $errors.Add('Narcotics PIN GUI title contains PIN mask.') }
}
if ($errors.Count -gt 0) { throw ("PIN GUI validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'PIN GUI validation passed.'
