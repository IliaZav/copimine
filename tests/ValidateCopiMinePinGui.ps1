$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$economy = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'

if (-not (Test-Path -LiteralPath $economy)) {
  throw "Missing source: $economy"
}

$java = Get-Content -Raw -Encoding UTF8 $economy
$errors = New-Object System.Collections.Generic.List[string]

foreach ($marker in @(
  'Map<Integer, String> keypad = Map.of(',
  '10, "1"',
  '11, "2"',
  '12, "3"',
  '19, "4"',
  '20, "5"',
  '21, "6"',
  '28, "7"',
  '29, "8"',
  '30, "9"',
  '38, "0"',
  'pin:digit:',
  'pin:back',
  'pin:clear',
  'pin:confirm',
  'atmPinSessions'
)) {
  if (-not $java.Contains($marker)) {
    $errors.Add("Economy PIN GUI missing marker: $marker")
  }
}

if ($java -match 'PIN:\s*"\s*\+\s*masked') {
  $errors.Add('PIN GUI title must not expose the PIN mask.')
}

if ($errors.Count -gt 0) {
  throw ("PIN GUI validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'PIN GUI validation passed.'
