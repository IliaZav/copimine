$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$economy = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('CopiMineEconomyCore.ArtifactsBridge','resolveBridge()','charge(','refund(','pinStatusAsync(','BridgeHealthSnapshot','bridge.health','isAvailable()','INSUFFICIENT_AR','BankService bridge is unavailable','firstEmpty() < 0','DonationBalanceService','DonationPaymentService','DonationPurchaseService')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing bridge marker: $marker") }
}
foreach ($marker in @('public interface ArtifactsBridge','CompletableFuture<PinStatus> pinStatusAsync','TxnResult charge(','TxnResult refund(','TxnResult credit(','Health health(UUID playerUuid, String context)')) {
  if ($economy -notmatch [regex]::Escape($marker)) { $errors.Add("EconomyCore missing bridge marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts economy bridge validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts economy bridge validation passed.'
