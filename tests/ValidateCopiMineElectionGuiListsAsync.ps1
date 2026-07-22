$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Get-MethodBody([string]$name) {
  $pattern = '(?s)private\s+void\s+' + [regex]::Escape($name) + '\s*\([^)]*\)\s*(?:throws\s+[^{]+)?\{'
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

$methods = @(
  'openCikMenu',
  'openCikChairManagementMenu',
  'openChairRemovalRequestsMenu',
  'openChairApplicationsMenu',
  'openChairBallotsMenu'
)
$dbCalls = '\b(queryList|queryOne|scalarLong|scalarString|tx|openConnection)\s*\('

foreach ($method in $methods) {
  $body = Get-MethodBody $method
  if ($body -notmatch 'runAsync\s*\(' -and $body -notmatch 'runTaskAsynchronously\s*\(') {
    throw "$method must load database rows asynchronously."
  }
  $asyncIndex = $body.IndexOf('runAsync', [System.StringComparison]::Ordinal)
  $outerBody = if ($asyncIndex -ge 0) { $body.Substring(0, $asyncIndex) } else { $body }
  if ($outerBody -match $dbCalls) {
    throw "$method still performs a database call in its outer GUI method."
  }
}

foreach ($query in @(
  'FROM cik_chairs WHERE active=1 ORDER BY assigned_at DESC LIMIT 500',
  'FROM polling_stations WHERE active=1 ORDER BY created_at DESC LIMIT 500',
  'FROM cik_chair_removal_requests ORDER BY requested_at DESC, id DESC LIMIT 500',
  'FROM candidate_applications WHERE station_id=? ORDER BY submitted_at DESC,issued_at DESC LIMIT 500'
)) {
  if ($source -notmatch [regex]::Escape($query)) {
    throw "Election GUI list query is not bounded: $query"
  }
}

Write-Output 'Election GUI list async contract: PASS'
