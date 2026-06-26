$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$javaPath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
if (-not (Test-Path -LiteralPath $javaPath)) {
  throw "Missing artifacts source: $javaPath"
}

$java = Get-Content -Raw -Encoding UTF8 $javaPath
$match = [regex]::Match($java, 'synchronized Connection acquire\(\) throws SQLException \{(?<body>.*?)\n\s*synchronized void release', 'Singleline')
if (-not $match.Success) {
  throw 'Could not isolate PgPool.acquire() body for validation.'
}

$body = $match.Groups['body'].Value
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @('long deadline = System.currentTimeMillis() + 1500L;','while (true)','wait(Math.min(remaining, 250L))','throw new SQLException("Artifact PostgreSQL pool timed out while waiting for a free connection.")')) {
  if (-not $body.Contains($marker)) { $errors.Add("PgPool.acquire() missing marker: $marker") }
}

foreach ($forbidden in @('return acquire();','acquire();')) {
  if ($body.Contains($forbidden)) { $errors.Add("PgPool.acquire() still contains recursive call marker: $forbidden") }
}

if ($errors.Count -gt 0) {
  throw ("PgPool recursion validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'PgPool recursion validation passed: acquire() uses bounded waiting and no recursive self-call.'
