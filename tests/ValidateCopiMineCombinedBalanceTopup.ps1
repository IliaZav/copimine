$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/backend/main.py')
$commerce = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/admin/commerce-pages.js')
foreach ($marker in @(
  'class AdminBalanceTopupIn',
  'def admin_topup_balances_sync',
  '/api/admin/economy/balances/topup',
  'admin-balance-topup:',
  'admin-ar-combined-',
  'don-combined-',
  'conn.commit()',
  '/api/admin/economy/balances/topup',
  'strictWholeAmount'
)) {
  if ($backend -notmatch [regex]::Escape($marker) -and $commerce -notmatch [regex]::Escape($marker)) {
    throw "Combined balance marker missing: $marker"
  }
}
if ($commerce -match '/api/admin/economy/ar/add-balance[\s\S]{0,1200}/api/admin/donation/add-balance') {
  throw 'Combined UI must use the atomic topup endpoint instead of two independent requests.'
}
Write-Host 'Combined balance topup validation passed.'
