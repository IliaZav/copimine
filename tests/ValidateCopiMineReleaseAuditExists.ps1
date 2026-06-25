$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$audit = Join-Path $root 'tests\manual\COPIMINE_RELEASE_AUDIT.md'
$smoke = Join-Path $root 'tests\manual\RELEASE_10_10_SMOKE_CHECKLIST.md'

if (-not (Test-Path -LiteralPath $audit)) {
    throw 'Release audit file is missing: tests/manual/COPIMINE_RELEASE_AUDIT.md'
}
if (-not (Test-Path -LiteralPath $smoke)) {
    throw 'Release smoke checklist is missing: tests/manual/RELEASE_10_10_SMOKE_CHECKLIST.md'
}

$auditText = Get-Content -LiteralPath $audit -Raw -Encoding UTF8
$smokeText = Get-Content -LiteralPath $smoke -Raw -Encoding UTF8

$requiredAudit = @(
    'Краткий аудит',
    'Модули и ownership',
    'GUI map',
    'Legacy remnants',
    'Критичные риски'
)
foreach ($needle in $requiredAudit) {
    if ($auditText -notmatch [regex]::Escape($needle)) {
        throw "Release audit must contain section: $needle"
    }
}

$requiredSmoke = @(
    'Startup',
    'GUI',
    'Election full flow',
    'Economy',
    'Website',
    'Discord',
    'Visuals'
)
foreach ($needle in $requiredSmoke) {
    if ($smokeText -notmatch [regex]::Escape($needle)) {
        throw "Release smoke checklist must contain section: $needle"
    }
}

Write-Host 'Release audit validation passed.'
