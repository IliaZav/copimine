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
  'createWithdrawalDeliveryRow',
  'reserveWithdrawalDelivery',
  'markPendingArSettlementsDelivered',
  'releasePendingArSettlements',
  'PENDING_AR_SETTLEMENT_STATUS_DEBITED',
  'PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY',
  'PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE',
  'if (!player.isOnline()) {',
  'queuePendingArSettlement(player.getUniqueId(), player.getName(), amount, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE',
  'queuePendingArSettlement(player.getUniqueId(), player.getName(), available, PENDING_AR_SETTLEMENT_TYPE_DEPOSIT_RESTORE'
)) {
  if (-not $java.Contains($marker)) {
    $errors.Add("Missing ATM offline recovery marker: $marker")
  }
}

# A successful ATM withdrawal must create its delivery intent in the same
# database transaction as the debit.  Re-queueing from the player callback is
# intentionally forbidden because it can duplicate a settled withdrawal.
if ($java.Contains('queuePendingArSettlement(player.getUniqueId(), player.getName(), session.amount(), PENDING_AR_SETTLEMENT_TYPE_WITHDRAW_DELIVERY')) {
  $errors.Add('ATM withdrawal must not create a second best-effort settlement from the logout callback.')
}
if (-not $java.Contains('createWithdrawalDeliveryRow(connection,')) {
  $errors.Add('ATM withdrawal durable delivery row is missing from the debit transaction.')
}
if (-not $java.Contains('PENDING_AR_SETTLEMENT_STATUS_DEBITED')) {
  $errors.Add('ATM withdrawal durable delivery status DEBITED is missing.')
}

if ($errors.Count -gt 0) {
  throw ("ATM offline recovery validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineEconomyAtmOfflineSettlementRecovery passed.'
