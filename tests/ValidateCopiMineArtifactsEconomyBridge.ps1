$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$admin = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('CopiMineUltimateAdminPlus.ArtifactsBridge','artifactsBridge','charge(','refund(','pinStatus(','BridgeHealthSnapshot','bridge.health','isAvailable()','INSUFFICIENT_AR','BankService bridge is unavailable','firstEmpty() < 0')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing bridge marker: $marker") }
}
foreach ($marker in @('public interface ArtifactsBridge','BridgeHealth health','ARTIFACTS_BRIDGE_HEALTH','ARTIFACTS_BRIDGE_HEALTH_FAILED','ARTIFACTS_CHARGE','ARTIFACTS_REFUND')) {
  if ($admin -notmatch [regex]::Escape($marker)) { $errors.Add("AdminPlus missing bridge marker: $marker") }
}
if ($text -match 'getClass\(\)\.getMethod|java\.lang\.reflect|Method\s+') { $errors.Add('Artifacts bridge must use typed AdminPlus API, not reflection.') }
if ($errors.Count -gt 0) { throw ("Artifacts economy bridge validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts economy bridge validation passed.'
