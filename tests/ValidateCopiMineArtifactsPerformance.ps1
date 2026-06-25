$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('Executors.newFixedThreadPool','runAsync(','tickPendingHints','cleanupExpiredSessions','SESSION_TTL_SECONDS','catalogById = new ConcurrentHashMap','shopsByLocation = new ConcurrentHashMap','sessions = new ConcurrentHashMap','PgPool','runTaskTimer(this, this::tickPendingHints, 20L * 60L, 20L * 60L)')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing performance marker: $marker") }
}
if ($text -match 'runTaskTimer\(this,.*1L, 1L') { $errors.Add('Found suspicious every-tick task.') }
function Window-After([string]$needle, [int]$length) {
  $idx = $text.IndexOf($needle)
  if ($idx -lt 0) { return '' }
  return $text.Substring($idx, [Math]::Min($length, $text.Length - $idx))
}
if ((Window-After 'onInventoryClick(' 1800) -match 'withConn\(') { $errors.Add('Inventory click handler must not perform direct DB work on the event thread.') }
if ((Window-After 'onEntityDamage(' 1800) -match 'withConn\(') { $errors.Add('Combat/effect handler must not perform direct DB work on the event thread.') }
if ($errors.Count -gt 0) { throw ("Artifacts performance validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts performance validation passed.'
