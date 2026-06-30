$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$economy = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'

if (-not (Test-Path -LiteralPath $economy)) {
  throw "Missing source: $economy"
}

$java = Get-Content -Raw -Encoding UTF8 $economy
$errors = New-Object System.Collections.Generic.List[string]

foreach ($marker in @(
  'cmv4_pending_ar_settlements',
  'processPendingArSettlements',
  'queuePendingArSettlement',
  'reservePendingArSettlements',
  'markPendingArSettlementsDelivered',
  'releasePendingArSettlements',
  'PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY',
  'PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE',
  'if (!player.isOnline()) {',
  'queuePendingArSettlement(player.getUniqueId(), player.getName(), session.amount(), PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY',
  'queuePendingArSettlement(player.getUniqueId(), player.getName(), amount, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE',
  'queuePendingArSettlement(player.getUniqueId(), player.getName(), available, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE'
)) {
  if (-not $java.Contains($marker)) {
    $errors.Add("Missing ATM offline recovery marker: $marker")
  }
}

if ($errors.Count -gt 0) {
  throw ("ATM offline recovery validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineEconomyAtmOfflineSettlementRecovery passed.'
