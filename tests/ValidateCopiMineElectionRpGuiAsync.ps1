$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Get-MethodBody([string]$name) {
  $pattern = '(?s)private\s+void\s+' + [regex]::Escape($name) + '\s*\([^)]*\)\s*(?:throws\s+[^\{]+)?\{'
  $match = [regex]::Match($source, $pattern)
  if (-not $match.Success) { throw "Method not found: $name" }
  $start = $match.Index + $match.Length
  $depth = 1
  for ($index = $start; $index -lt $source.Length; $index++) {
    if ($source[$index] -eq '{') { $depth++ }
    elseif ($source[$index] -eq '}') {
      $depth--
      if ($depth -eq 0) { return $source.Substring($start, $index - $start) }
    }
  }
  throw "Unclosed method: $name"
}

$dbCalls = '\b(queryList|queryOne|scalarLong|scalarString|tx|openConnection)\s*\('
foreach ($method in @(
  'openRpApplicationDetail',
  'openRpResultsMenu',
  'openRpPresidentMenu',
  'openApplicationBook',
  'createRpVotingBlockFromTargetAsync',
  'disableRpVotingBlockAsync',
  'confirmDirectVote'
)) {
  $body = Get-MethodBody $method
  if ($body -notmatch 'runAsync\s*\(') {
    throw "$method must schedule work on the SQL worker."
  }
  $outer = $body.Substring(0, $body.IndexOf('runAsync', [System.StringComparison]::Ordinal))
  if ($outer -match $dbCalls) {
    throw "$method performs a database call before entering runAsync."
  }
}

$actionBody = Get-MethodBody 'runRpDatabaseAction'
if ($actionBody -notmatch 'runAsync\s*\(' -or $actionBody -notmatch 'runSync\s*\(') {
  throw 'runRpDatabaseAction must return user-facing work to the Bukkit thread.'
}

if ($source -notmatch 'createRpVotingBlockFromTargetAsync\(player\);') {
  throw 'The RP block creation action still uses a synchronous target/SQL path.'
}
if ($source -notmatch 'disableRpVotingBlockAsync\(player,') {
  throw 'The RP block disable action still uses a synchronous SQL path.'
}
if ($source -notmatch 'action\.startsWith\("stations:"\)') {
  throw 'Retired station actions are not blocked from the old CIK menu.'
}

Write-Output 'Election RP GUI async contract: PASS'
