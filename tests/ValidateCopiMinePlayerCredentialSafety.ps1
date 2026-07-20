$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$main = Get-Content (Join-Path $root 'admin-web/backend/main.py') -Raw
$runtime = Get-Content (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js') -Raw

foreach ($needle in @(
  'class AdminAuthMePasswordResetIn',
  '@app.post("/api/players/{player}/authme-password/reset")',
  'authme changepassword',
  'minecraft_identity_in_whitelist_file',
  'auto_whitelist_fresh_identity',
  'add_player_to_whitelist_sync(minecraft_uuid, minecraft_name)'
)) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Missing backend credential/whitelist guard: $needle" }
}
foreach ($needle in @(
  'playerUpdateSiteAccount',
  'playerResetAuthMePassword',
  'admin-player-credentials-card'
)) {
  if ($runtime -notmatch [regex]::Escape($needle)) { throw "Missing frontend safe credential control: $needle" }
}
Write-Host 'ValidateCopiMinePlayerCredentialSafety: PASS'
