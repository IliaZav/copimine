$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$java = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$body = [regex]::Match($java, 'private void notifyPlayerBug\([\s\S]*?\n\s*}\s*private PgSettings loadPgSettings', 'Singleline').Value
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('Error code:', '/reporta', 'pendingBugReports.put')) {
  if ($body -notmatch [regex]::Escape($marker)) { $errors.Add("Bug-report player message is missing: $marker") }
}
if ($body -match 'Type:\s*&f\"\+exceptionClass|warn\(player,[^;]*exceptionClass') { $errors.Add('Bug-report player message leaks the internal exception class.') }
if ($body -match 'Type:') { $errors.Add('Bug-report player message must not expose an internal Type field.') }
if ($errors.Count) { throw ("Bug-report player message validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineBugReportPlayerMessage passed.'
