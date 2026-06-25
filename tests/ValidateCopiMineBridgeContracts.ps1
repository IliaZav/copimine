$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

foreach ($marker in @('public ArtifactsBridge artifactsBridge()','BridgeHealth','BridgeTxnResult','charge(UUID playerUuid','refund(UUID playerUuid','idempotencyKey')) {
  if ($admin -notmatch [regex]::Escape($marker)) { $errors.Add("AdminPlus bridge contract missing: $marker") }
}
foreach ($marker in @('main.artifactsBridge()','bridge.charge(player','bridge.refund(player','BridgeHealthSnapshot','safeBridgeCode')) {
  if ($artifacts -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts bridge usage missing: $marker") }
}
foreach ($marker in @('CopiMineUltimateAdminPlus.ArtifactsBridge','main.artifactsBridge()','bridge.charge(')) {
  if ($narcotics -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics bridge usage missing: $marker") }
}

if ($errors.Count -gt 0) { throw ("Bridge contracts validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Bridge contracts validation passed.'
