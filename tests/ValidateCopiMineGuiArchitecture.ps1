$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$admin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$artifacts = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$narcotics = Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($admin, $artifacts, $narcotics)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing source: $path") }
}
if (Test-Path $admin) {
  $java = Get-Content -Raw -Encoding UTF8 $admin
  foreach ($marker in @('static final class Menu implements InventoryHolder','new Menu(','InventoryClickEvent','handle(Player p, ClickType click','atmPinSessions')) {
    if (-not $java.Contains($marker)) { $errors.Add("AdminPlus missing GUI architecture marker: $marker") }
  }
  foreach ($bad in @('static Inventory','Bukkit.createInventory(null','runTaskTimer(this,()->exec','runTaskTimer(this,()->query')) {
    if ($java.Contains($bad)) { $errors.Add("AdminPlus contains risky GUI/DB pattern: $bad") }
  }
}
if (Test-Path $artifacts) {
  $java = Get-Content -Raw -Encoding UTF8 $artifacts
  foreach ($marker in @('sessions','SessionState','MenuHolder','runAsync','dbExecutor')) {
    if (-not $java.Contains($marker)) { $errors.Add("Artifacts missing per-player/async GUI marker: $marker") }
  }
  foreach ($bad in @('static Inventory','runTaskTimer(this, this::tickPendingHints, 1L','runTaskTimer(this, this::cleanupExpiredSessions, 1L')) {
    if ($java.Contains($bad)) { $errors.Add("Artifacts contains risky GUI/DB pattern: $bad") }
  }
}
if (Test-Path $narcotics) {
  $java = Get-Content -Raw -Encoding UTF8 $narcotics
  foreach ($marker in @('pendingPurchases','pinBuffers','InventoryClickEvent','runTaskAsynchronously')) {
    if (-not $java.Contains($marker)) { $errors.Add("Narcotics missing GUI/session/performance marker: $marker") }
  }
  foreach ($bad in @('static Inventory','runTaskTimer','DriverManager','while (true)')) {
    if ($java.Contains($bad)) { $errors.Add("Narcotics contains risky GUI/DB pattern: $bad") }
  }
}
if ($errors.Count -gt 0) { throw ("GUI architecture validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'GUI architecture validation passed.'
