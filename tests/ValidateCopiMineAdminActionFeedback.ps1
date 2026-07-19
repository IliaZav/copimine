$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js')
$commerce = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/admin/commerce-pages.js')
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/backend/main.py')

foreach ($marker in @(
  'pendingDangerConfirm',
  'operation-status',
  'operationState',
  'event.preventDefault'
)) {
  if ($runtime -notmatch [regex]::Escape($marker) -and $commerce -notmatch [regex]::Escape($marker)) {
    throw "Admin action feedback marker missing: $marker"
  }
}

foreach ($marker in @('strictWholeAmount', 'idempotency_key', 'Test purchases are real pending deliveries', 'conn.commit()')) {
  if ($commerce -notmatch [regex]::Escape($marker) -and $backend -notmatch [regex]::Escape($marker)) {
    throw "Action reliability marker missing: $marker"
  }
}

Write-Host 'Admin action feedback validation passed.'
