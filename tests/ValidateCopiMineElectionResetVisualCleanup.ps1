$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$reset = [regex]::Match($source, '(?s)private void resetElections\(String actor\)( throws Exception)? \{.*?(?=\r?\n\s*private void expirePresidentTermsSafe)')
$cleanup = [regex]::Match($source, '(?s)private void removeElectionManagedVisuals\(\) \{.*?(?=\r?\n\s*private )')

if (-not $reset.Success -or $reset.Value -notmatch 'removeElectionManagedVisuals\(\)') {
    throw 'A full election reset must remove existing station and tax-office visual entities before their links are deleted.'
}

if (-not $cleanup.Success -or $cleanup.Value -notmatch 'STATION_LABEL' -or $cleanup.Value -notmatch 'TAX_LABEL' -or $cleanup.Value -notmatch 'PROTECTED_BLOCK_VISUAL') {
    throw 'Election reset visual cleanup must target only managed station and tax-office labels and block visuals.'
}

Write-Host 'Election reset visual cleanup contract OK'
