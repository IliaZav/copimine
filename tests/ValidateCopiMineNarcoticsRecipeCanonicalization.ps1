$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

if ($source -notmatch '"POTION:" \+ canonicalEffectId' -or $source -notmatch 'Set<String> recipeSignatures' -or $source -notmatch 'validateRecipeSignature\(') {
    throw 'Narcotics reload must canonicalize legacy potion recipe aliases and reject duplicate recipe signatures.'
}

Write-Host 'Narcotics recipe canonicalization contract OK'
