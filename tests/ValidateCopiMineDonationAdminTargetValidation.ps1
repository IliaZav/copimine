$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\admin-web\backend\main.py'))
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @(
  'def normalize_donation_player_target(',
  'valid_minecraft_name(raw_name)',
  'str(uuid.UUID(raw_uuid))',
  'player_uuid, player_name = normalize_donation_player_target(player_uuid, player_name)'
)) {
  if ($text -notmatch [regex]::Escape($marker)) {
    $errors.Add("Missing donation admin target validation marker: $marker")
  }
}

if ($errors.Count -gt 0) {
  throw ("Donation admin target validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Donation admin target validation passed.'
