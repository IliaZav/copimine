$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('sessions = new ConcurrentHashMap','purchaseInFlightId','idempotency_key','UUID.randomUUID().toString()','session(Player player)','Inventory inv = Bukkit.createInventory')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing concurrency marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts concurrent shop validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts concurrent shop validation passed.'
