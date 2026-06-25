$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java')
foreach ($marker in @('copimine_item_type','narcotic_id','narcotic_version','official','RP_NARCOTIC')) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "PDC marker missing: $marker" }
}
Write-Host 'PDC requirement validation passed.'
