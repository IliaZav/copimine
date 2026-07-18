$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$helper = [regex]::Match($source, '(?s)private String shortText\(String text, int maximumLength\) \{.*?(?=\r?\n\s*private )')

if (-not $helper.Success -or $helper.Value -notmatch 'maximumLength' -or $helper.Value -notmatch 'substring') {
    throw 'The shop detail GUI requires a bounded title helper so a long shop name cannot break the inventory title.'
}

Write-Host 'Artifact shop title truncation contract OK'
