$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$syncScript = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/scripts/sync_item_visuals.py')
$preview = Join-Path $root 'admin-web/frontend/assets/item-textures/ne_segodnya_suka_shield.png'

if ($syncScript -notmatch [regex]::Escape('def shield_preview(')) {
  throw 'Website visual sync must build a dedicated shield preview instead of copying the shield UV texture.'
}

if (-not (Test-Path -LiteralPath $preview)) {
  throw 'Shield preview asset is missing.'
}

$bytes = [System.IO.File]::ReadAllBytes($preview)
$signature = [string]::Join(',', @($bytes[0..7]))
if ($bytes.Length -lt 24 -or $signature -ne '137,80,78,71,13,10,26,10') {
  throw 'Shield preview is not a PNG file.'
}

$width = ([int]$bytes[16] -shl 24) -bor ([int]$bytes[17] -shl 16) -bor ([int]$bytes[18] -shl 8) -bor [int]$bytes[19]
$height = ([int]$bytes[20] -shl 24) -bor ([int]$bytes[21] -shl 16) -bor ([int]$bytes[22] -shl 8) -bor [int]$bytes[23]
if ($width -ne 32 -or $height -ne 32) {
  throw "Shield preview must be a 32x32 item icon, got ${width}x${height}."
}

Write-Host 'ValidateCopiMineShieldPreview passed.'
