$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$resolve = [regex]::Match($source, '(?s)public NarcoticDefinition resolveOfficial\(ItemStack stack\) \{.*?(?=\r?\n\s*public boolean isOfficialFinishedItem)')
$flag = [regex]::Match($source, '(?s)private boolean hasOfficialFlag\(ItemMeta meta\) \{.*?(?=\r?\n\s*private String color)')

if (-not $resolve.Success -or $resolve.Value -notmatch 'stack\.getType\(\) != definition\.material\(\)' -or $resolve.Value -notmatch 'TextureMode\.CUSTOM') {
    throw 'Official narcotics must match their configured material and required custom-model state.'
}

if (-not $flag.Success -or $flag.Value -notmatch 'return false;') {
    throw 'An item without the explicit official marker must never be accepted as an official narcotic.'
}

Write-Host 'Narcotics official item strictness contract OK'
