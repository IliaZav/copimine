$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$java = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('handleReport(CommandSender', 'handleReportaCommand', 'reportCooldowns', 'REPORT_COOLDOWN_MS', 'admin_requests', "status='OPEN'", 'clipped(text')) {
  if ($java -notmatch [regex]::Escape($marker)) { $errors.Add("Complaint flow marker is missing: $marker") }
}
if ($java -notmatch 'createdAt\(\).*BUG_REPORT_CONTEXT_TTL_MS|BUG_REPORT_CONTEXT_TTL_MS.*createdAt\(\)') { $errors.Add('Pending bug context must expire after a bounded TTL.') }
if ($errors.Count) { throw ("Complaint command validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineComplaintCommands passed.'
