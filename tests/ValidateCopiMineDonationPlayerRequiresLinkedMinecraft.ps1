$ErrorActionPreference = "Stop"

$path = "D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py"
$text = Get-Content -Raw -Encoding UTF8 $path

$required = @(
  'if not player_uuid:',
  'raise HTTPException(status_code=409, detail="Minecraft account is not linked")',
  '"reason": "missing_player_uuid"',
  'raise HTTPException(status_code=409, detail='
)

$missing = @()
foreach ($needle in $required) {
  if (-not $text.Contains($needle)) {
    $missing += $needle
  }
}

if ($missing.Count -gt 0) {
  throw ("ValidateCopiMineDonationPlayerRequiresLinkedMinecraft failed:`n - Missing marker: " + ($missing -join "`n - Missing marker: "))
}

Write-Host "ValidateCopiMineDonationPlayerRequiresLinkedMinecraft passed."
