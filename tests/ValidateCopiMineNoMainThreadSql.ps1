$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$narcoticsMain = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$narcoticsDb = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')

if ($narcoticsMain -match 'DriverManager|getConnection\(|PreparedStatement|executeQuery|executeUpdate') { $errors.Add('CopiMineNarcotics main thread runtime must not do direct SQL.') }
foreach ($marker in @('CompletableFuture','runAsync','supplyAsync')) {
  if ($narcoticsDb -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics DB async marker missing: $marker") }
}
if ($narcoticsDb -notmatch 'Executors\.newFixedThreadPool|ThreadPoolExecutor|LinkedBlockingQueue') {
  $errors.Add('Narcotics DB executor must remain explicitly asynchronous and bounded.')
}
foreach ($marker in @('runAsync(() ->','loadCatalogFromConfig()','loadShopsFromPostgres()','shopsByLocation','catalogById')) {
  if ($artifacts -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts async/cache marker missing: $marker") }
}
if ($artifacts -notmatch '20L \* 60L, 20L \* 60L') { $errors.Add('Artifacts scheduled tasks must stay at one minute or slower.') }
if ($artifacts -match 'runTaskTimer\(this,\s*this::tickPendingHints,\s*1L|runTaskTimer\(this,\s*this::cleanupExpiredSessions,\s*1L') { $errors.Add('Artifacts contains a one-tick repeating task.') }
if ($narcoticsMain -match 'runTaskTimer|scheduleSyncRepeatingTask') { $errors.Add('Narcotics must not schedule repeating scans/tasks.') }

if ($errors.Count -gt 0) { throw ("No main-thread SQL validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'No main-thread SQL validation passed.'
